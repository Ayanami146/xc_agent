import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import * as chatService from '../services/chat'
import type { ChatMessage, ChatSession, ChatStage, ChatStreamEvent } from '../types/chat'

const nowTime = () => new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
let temporaryId = -1
const nextTemporaryId = () => temporaryId--

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSession[]>([])
  const activeSessionId = ref<number | null>(null)
  const messages = ref<ChatMessage[]>([])
  const loading = ref(false)
  const generating = ref(false)
  const currentStage = ref<ChatStage | null>(null)
  const controller = ref<AbortController | null>(null)
  const currentRequestId = ref<number | null>(null)
  const searchKeyword = ref('')

  const filteredSessions = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase()
    return sessions.value.filter((item) => !keyword || `${item.title}${item.preview}`.toLowerCase().includes(keyword))
  })

  async function initialize() {
    if (sessions.value.length) return
    sessions.value = await chatService.listSessions()
    if (sessions.value[0]) await selectSession(sessions.value[0].id)
  }

  async function selectSession(sessionId: number) {
    if (generating.value) stopGeneration()
    activeSessionId.value = sessionId
    loading.value = true
    try { messages.value = await chatService.listMessages(sessionId) } finally { loading.value = false }
  }

  function newSession() {
    if (generating.value) stopGeneration()
    activeSessionId.value = null
    messages.value = [{ id: nextTemporaryId(), role: 'assistant', status: 'COMPLETED', content: '您好，我是信创智能客服助手。请描述设备型号、系统版本和具体现象。', createdAt: nowTime() }]
  }

  async function rename(sessionId: number, title: string) {
    const item = sessions.value.find((session) => session.id === sessionId)
    const previous = item?.title
    if (item) item.title = title
    try { await chatService.renameSession(sessionId, title) } catch (error) {
      if (item && previous) item.title = previous
      throw error
    }
  }

  async function remove(sessionId: number) {
    await chatService.deleteSession(sessionId)
    sessions.value = sessions.value.filter((session) => session.id !== sessionId)
    if (activeSessionId.value === sessionId) {
      const next = sessions.value[0]
      if (next) await selectSession(next.id)
      else newSession()
    }
  }

  function applyEvent(event: ChatStreamEvent, userMessage: ChatMessage, assistant: ChatMessage, originalContent: string) {
    assistant.requestId = event.requestId
    currentRequestId.value = event.requestId
    if (event.event === 'meta') {
      userMessage.id = event.payload.userMessageId
      assistant.id = event.payload.assistantMessageId
      activeSessionId.value = event.payload.sessionId
      const existing = sessions.value.find((item) => item.id === event.payload.sessionId)
      if (!existing) sessions.value.unshift({ id: event.payload.sessionId, title: originalContent.slice(0, 18), preview: originalContent, updatedAt: '刚刚' })
    } else if (event.event === 'status') {
      currentStage.value = event.payload.stage
      assistant.stage = event.payload.stage
    } else if (event.event === 'delta') {
      assistant.content += event.payload.content
    } else if (event.event === 'citation') {
      assistant.citations = event.payload.sources
    } else if (event.event === 'done') {
      assistant.id = event.payload.messageId
      assistant.status = 'COMPLETED'
      generating.value = false
      currentStage.value = null
      currentRequestId.value = null
    } else if (event.event === 'error') {
      assistant.status = 'FAILED'
      assistant.content = event.payload.message
      generating.value = false
      currentStage.value = null
      currentRequestId.value = null
    }
  }

  async function send(message: string) {
    const content = message.trim()
    if (!content || generating.value) return
    if (!activeSessionId.value) newSession()
    // 这两个对象会在收到 meta/status/delta/done 时被原位更新，因此必须先转成 Vue
    // 响应式对象再放入 messages。若先 push 普通对象、随后继续修改原始引用，修改会绕过
    // 数组内部的 Proxy：数据库和内存内容都已更新，但页面只能在切换会话后重新渲染。
    const userMessage = reactive<ChatMessage>({
      id: nextTemporaryId(), role: 'user', status: 'COMPLETED', content, createdAt: nowTime(),
    })
    const assistant = reactive<ChatMessage>({
      id: nextTemporaryId(), role: 'assistant', status: 'STREAMING', content: '', createdAt: nowTime(), stage: 'queued',
    })
    messages.value.push(userMessage, assistant)
    generating.value = true
    currentStage.value = 'queued'
    const abortController = new AbortController()
    controller.value = abortController
    const requestedSessionId = activeSessionId.value ?? undefined
    const session = sessions.value.find((item) => item.id === activeSessionId.value)
    if (session) {
      session.preview = content
      session.updatedAt = '刚刚'
    }

    try {
      await chatService.streamChat({ sessionId: requestedSessionId, message: content }, (event) => applyEvent(event, userMessage, assistant, content), abortController.signal)
    } catch (error) {
      if ((error as Error).name === 'AbortError') {
        assistant.status = 'INTERRUPTED'
        if (!assistant.content) assistant.content = '本次回答已停止。'
      } else {
        const requestId = assistant.requestId
        const result = requestId ? await chatService.getChatRequest(requestId).catch(() => null) : null
        if (result?.status === 'SUCCEEDED' && result.answer && result.assistantMessageId) {
          assistant.id = result.assistantMessageId
          assistant.content = result.answer
          assistant.citations = result.citations
          assistant.status = 'COMPLETED'
        } else {
          assistant.status = 'FAILED'
          assistant.content = result?.error?.message || '服务暂时不可用，请稍后重试或提交留言工单。'
        }
      }
    } finally {
      generating.value = false
      currentStage.value = null
      controller.value = null
      currentRequestId.value = null
    }
  }

  function stopGeneration() {
    const requestId = currentRequestId.value
    controller.value?.abort()
    if (requestId) void chatService.cancelChatRequest(requestId)
  }
  async function setFeedback(messageId: number, feedback: 'up' | 'down') {
    const message = messages.value.find((item) => item.id === messageId)
    if (!message) return
    const previous = message.feedback
    const next = message.feedback === feedback ? undefined : feedback
    message.feedback = next
    try { await chatService.setMessageFeedback(messageId, next ?? null) } catch (error) { message.feedback = previous; throw error }
  }

  return { sessions, activeSessionId, messages, loading, generating, currentStage, searchKeyword, filteredSessions, initialize, selectSession, newSession, rename, remove, send, stopGeneration, setFeedback }
})
