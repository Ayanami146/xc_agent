import { mockMessages, mockSessions } from '../mocks/data'
import type { ChatMessage, ChatRequestResult, ChatSession, ChatStreamEvent, ChatStreamRequest } from '../types/chat'
import type { ApiEnvelope, PageData, ProblemDetail } from '../types/problem'
import { apiMode, getAccessToken, http, newIdempotencyKey, refreshAccessToken } from './http'

const wait = (ms: number, signal?: AbortSignal) => new Promise<void>((resolve, reject) => {
  const timer = setTimeout(resolve, ms)
  signal?.addEventListener('abort', () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }, { once: true })
})

let mockPrimaryKey = 1000
const nextMockId = () => mockPrimaryKey++

export async function listSessions(): Promise<ChatSession[]> {
  if (apiMode === 'remote') {
    const response = await http.get<ApiEnvelope<PageData<ChatSession>>>('/sessions', { params: { page: 1, pageSize: 100 } })
    return response.data.data.items
  }
  await wait(120)
  return structuredClone(mockSessions)
}

export async function listMessages(sessionId: number): Promise<ChatMessage[]> {
  if (apiMode === 'remote') {
    const response = await http.get<ApiEnvelope<PageData<ChatMessage>>>(`/sessions/${sessionId}/messages`, { params: { page: 1, pageSize: 100 } })
    return response.data.data.items
  }
  await wait(100)
  return structuredClone(mockMessages[sessionId] ?? [])
}

export async function renameSession(sessionId: number, title: string) {
  if (apiMode === 'remote') await http.patch(`/sessions/${sessionId}`, { title }, { headers: { 'Idempotency-Key': newIdempotencyKey() } })
}

export async function deleteSession(sessionId: number) {
  if (apiMode === 'remote') await http.delete(`/sessions/${sessionId}`, { headers: { 'Idempotency-Key': newIdempotencyKey() } })
}

export async function setMessageFeedback(messageId: number, feedback: 'up' | 'down' | null) {
  if (apiMode === 'remote') {
    await http.put(`/messages/${messageId}/feedback`, { feedback }, { headers: { 'Idempotency-Key': newIdempotencyKey() } })
  }
}

export async function getChatRequest(requestId: number): Promise<ChatRequestResult> {
  const response = await http.get<ApiEnvelope<ChatRequestResult>>(`/chat/requests/${requestId}`)
  return response.data.data
}

export async function cancelChatRequest(requestId: number) {
  if (apiMode === 'remote') {
    await http.post(`/chat/requests/${requestId}/cancel`, {}, { headers: { 'Idempotency-Key': newIdempotencyKey() } })
  }
}

export interface SseParser {
  push(chunk: string): void
  finish(): void
}

export function createSseParser(onEvent: (event: ChatStreamEvent) => void): SseParser {
  let buffer = ''
  let lastSequence = 0
  let terminalSeen = false

  const consume = (block: string) => {
    if (!block.trim()) return
    let eventName = ''
    const dataLines: string[] = []
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
    }
    if (!eventName || !dataLines.length) return
    const event = JSON.parse(dataLines.join('\n')) as ChatStreamEvent
    if (event.event !== eventName) throw new Error('SSE_EVENT_NAME_MISMATCH')
    if (event.sequence <= lastSequence) throw new Error('SSE_SEQUENCE_INVALID')
    if (terminalSeen) throw new Error('SSE_EVENT_AFTER_TERMINAL')
    lastSequence = event.sequence
    terminalSeen = event.event === 'done' || event.event === 'error'
    onEvent(event)
  }

  return {
    push(chunk) {
      buffer += chunk.replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        consume(buffer.slice(0, boundary))
        buffer = buffer.slice(boundary + 2)
        boundary = buffer.indexOf('\n\n')
      }
    },
    finish() {
      if (buffer.trim()) consume(buffer)
      if (!terminalSeen) throw new Error('SSE_TERMINAL_EVENT_MISSING')
    },
  }
}

function envelope<T extends ChatStreamEvent['event']>(event: T, requestId: number, sequence: number, payload: Extract<ChatStreamEvent, { event: T }>['payload']): Extract<ChatStreamEvent, { event: T }> {
  return { event, requestId, sequence, occurredAt: new Date().toISOString(), payload } as Extract<ChatStreamEvent, { event: T }>
}

async function mockStream(request: ChatStreamRequest, onEvent: (event: ChatStreamEvent) => void, signal: AbortSignal) {
  const requestId = nextMockId()
  const sessionId = request.sessionId ?? nextMockId()
  let sequence = 1
  onEvent(envelope('meta', requestId, sequence++, { sessionId, userMessageId: nextMockId(), assistantMessageId: nextMockId() }))
  await wait(180, signal)
  onEvent(envelope('status', requestId, sequence++, { stage: 'safety', message: '已完成安全检查' }))
  await wait(220, signal)
  onEvent(envelope('status', requestId, sequence++, { stage: 'retrieval', message: '正在检索维修手册与常见问题' }))
  await wait(340, signal)

  if (request.message.includes('模拟错误')) {
    onEvent(envelope('error', requestId, sequence++, { code: 'MODEL_UNAVAILABLE', message: '模型服务暂时不可用，请稍后重试或提交工单。', retryable: true }))
    return
  }

  onEvent(envelope('status', requestId, sequence++, { stage: 'generation', message: '正在组织答案' }))
  const answer = request.message.includes('保修')
    ? '整机、显示器和配件可能采用不同保修期限。建议准备设备服务编码，在保修查询页面核验；如果无法查询，可提交工单由客服确认。'
    : '建议先确认设备完整型号、当前系统版本和 CPU 架构，再下载对应版本的官方驱动。安装前请移除旧驱动并重新连接设备；如果仍无法识别，请保留安装日志并提交工单。'
  const chunks = answer.match(/.{1,9}/g) ?? [answer]
  for (const content of chunks) {
    await wait(55, signal)
    onEvent(envelope('delta', requestId, sequence++, { content }))
  }
  onEvent(envelope('citation', requestId, sequence++, { sources: [{ title: '统信 UOS 外设驱动安装手册', sourceId: 1, snippet: '安装前确认系统版本、CPU 架构和设备硬件 ID。', sourceLocator: '/manuals/uos-driver', page: 12 }] }))
  onEvent(envelope('usage', requestId, sequence++, { model: 'mock-customer-service', promptTokens: 286, completionTokens: 96, totalTokens: 382, estimatedCost: 0.0018 }))
  onEvent(envelope('done', requestId, sequence++, { finishReason: 'stop', messageId: nextMockId() }))
}

async function remoteStream(request: ChatStreamRequest, onEvent: (event: ChatStreamEvent) => void, signal: AbortSignal) {
  const url = `${import.meta.env.VITE_API_BASE_URL || '/api/v1'}/chat/stream`
  const idempotencyKey = newIdempotencyKey()
  const execute = () => fetch(url, {
    method: 'POST', signal, credentials: 'include',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', Authorization: `Bearer ${getAccessToken()}`, 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(request),
  })
  let response = await execute()
  if (response.status === 401) { await refreshAccessToken(); response = await execute() }
  if (!response.ok || !response.body) {
    const problem = await response.json().catch(() => null) as ProblemDetail | null
    throw problem ?? new Error(`CHAT_STREAM_HTTP_${response.status}`)
  }
  const parser = createSseParser(onEvent)
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    parser.push(decoder.decode(value, { stream: true }))
  }
  parser.push(decoder.decode())
  parser.finish()
}

export async function streamChat(request: ChatStreamRequest, onEvent: (event: ChatStreamEvent) => void, signal: AbortSignal) {
  return apiMode === 'remote' ? remoteStream(request, onEvent, signal) : mockStream(request, onEvent, signal)
}
