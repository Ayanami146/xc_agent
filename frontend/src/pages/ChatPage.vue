<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Close, Microphone, Promotion } from '@element-plus/icons-vue'
import AppHeader from '../components/AppHeader.vue'
import ChatMessage from '../components/ChatMessage.vue'
import KnowledgeSidebar from '../components/KnowledgeSidebar.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { useChatStore } from '../stores/chat'
import { ElMessage } from 'element-plus'
import { problemMessage } from '../services/http'

const chat = useChatStore()
const composer = ref('')
const messageViewport = ref<HTMLDivElement>()
const sessionDrawer = ref(false)
const knowledgeDrawer = ref(false)

const stageText = computed(() => ({ queued: '正在排队', safety: '正在进行安全检查', intent: '正在识别问题类型', retrieval: '正在检索知识库', generation: '正在生成回答', validation: '正在校验引用' }[chat.currentStage ?? 'queued']))

function submit() {
  const value = composer.value
  if (!value.trim() || chat.generating) return
  composer.value = ''
  chat.send(value)
}

function ask(question: string) {
  composer.value = question
  nextTick(() => submit())
}

async function setFeedback(messageId: number, value: 'up' | 'down') {
  try { await chat.setFeedback(messageId, value) } catch (error) { ElMessage.error(problemMessage(error, '反馈提交失败')) }
}

watch(() => chat.messages.map((item) => `${item.id}:${item.content.length}`).join('|'), async () => {
  await nextTick()
  messageViewport.value?.scrollTo({ top: messageViewport.value.scrollHeight, behavior: 'smooth' })
})

onMounted(async () => {
  try { await chat.initialize() } catch (error) { ElMessage.error(problemMessage(error, '会话加载失败')) }
})
</script>

<template>
  <div class="app-shell chat-page">
    <AppHeader show-panel-controls @toggle-sessions="sessionDrawer = true" @toggle-knowledge="knowledgeDrawer = true" />
    <main class="workspace">
      <SessionSidebar @selected="sessionDrawer = false" />

      <section class="panel chat-panel">
        <div class="chat-heading">
          <div><span class="eyebrow">AI SERVICE DESK</span><h1>您好，需要什么帮助？</h1></div>
          <span class="online-badge"><i /> 服务在线</span>
        </div>

        <div ref="messageViewport" v-loading="chat.loading" class="welcome-area message-viewport">
          <div v-if="!chat.messages.length && !chat.loading" class="empty-chat">
            <span class="empty-chat-mark">AI</span>
            <span class="eyebrow">WELCOME</span>
            <h2>从一个具体问题开始</h2>
            <p>描述设备型号、系统版本和异常现象，我会结合维修手册给出可追溯的建议。</p>
            <div class="quick-prompts empty-prompts">
              <button @click="ask('统信 UOS 打印机驱动安装失败怎么办？')">统信 UOS 驱动安装失败</button>
              <button @click="ask('银河麒麟系统升级后无法连接网络怎么办？')">银河麒麟网络连接异常</button>
              <button @click="ask('国产电脑整机保修期限如何计算？')">查询设备保修政策</button>
            </div>
          </div>
          <template v-else>
            <ChatMessage v-for="message in chat.messages" :key="message.id" :message="message" @feedback="setFeedback" />
          </template>
        </div>

        <div class="composer-wrap">
          <div v-if="chat.generating" class="generation-status"><span class="status-wave"><i /><i /><i /></span><span>{{ stageText }}</span></div>
          <div class="composer">
            <textarea v-model="composer" rows="3" maxlength="8000" placeholder="请描述您的问题，建议说明设备型号、系统版本和具体现象" @keydown.enter.exact.prevent="submit" />
            <div class="composer-footer">
              <button class="voice-button" aria-label="语音输入"><el-icon><Microphone /></el-icon></button>
              <span>{{ composer.length }}/8000 · Enter 发送 · Shift + Enter 换行</span>
              <button v-if="chat.generating" class="stop-button" @click="chat.stopGeneration"><el-icon><Close /></el-icon><span>停止</span></button>
              <button v-else class="send-button" :disabled="!composer.trim()" @click="submit"><span>发送</span><el-icon><Promotion /></el-icon></button>
            </div>
          </div>
          <small class="ai-note">AI 回答仅供参考，高风险操作请提交工单确认</small>
        </div>
      </section>

      <KnowledgeSidebar @ask="ask" @selected="knowledgeDrawer = false" />
    </main>

    <el-drawer v-model="sessionDrawer" title="历史会话" direction="ltr" size="min(340px, 90vw)" class="mobile-drawer"><SessionSidebar @selected="sessionDrawer = false" /></el-drawer>
    <el-drawer v-model="knowledgeDrawer" title="知识中心" direction="rtl" size="min(380px, 92vw)" class="mobile-drawer"><KnowledgeSidebar @ask="ask" @selected="knowledgeDrawer = false" /></el-drawer>
  </div>
</template>

<style scoped>
.chat-page { height: 100vh; overflow: hidden; }
.message-viewport { min-height: 0; scroll-behavior: smooth; }
.empty-chat { min-height: 100%; padding: 26px; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; }
.empty-chat-mark { width: 68px; height: 68px; margin-bottom: 18px; border-radius: 23px; display: grid; place-items: center; color: #fff; background: linear-gradient(145deg, #1769df, #0bb8d4); box-shadow: 0 18px 34px rgba(23,105,223,.22); font-weight: 850; }
.empty-chat h2 { margin: 8px 0; font-size: 28px; }.empty-chat p { max-width: 540px; margin: 0; color: #74859b; line-height: 1.7; }.empty-prompts { margin: 24px 0 0; justify-content: center; }
.generation-status { margin: 0 2px 9px; display: flex; align-items: center; gap: 8px; color: #56769e; font-size: 12px; }.status-wave { display: flex; gap: 3px; }.status-wave i { width: 4px; height: 4px; border-radius: 50%; background: var(--primary); animation: blink 1s infinite alternate; }.status-wave i:nth-child(2) { animation-delay: .2s; }.status-wave i:nth-child(3) { animation-delay: .4s; }
.stop-button { margin-left: auto; height: 38px; padding: 0 15px; border: 1px solid #f0b6b6; border-radius: 11px; display: flex; align-items: center; gap: 7px; color: #bf4d4d; background: #fff5f5; cursor: pointer; font-weight: 700; }.send-button:disabled { opacity: .45; cursor: not-allowed; box-shadow: none; }
:deep(.mobile-drawer .el-drawer__body) { padding: 0; background: #f4f8fc; }:deep(.mobile-drawer .session-panel), :deep(.mobile-drawer .knowledge-panel) { display: flex; min-height: calc(100vh - 76px); border: 0; border-radius: 0; box-shadow: none; }
@keyframes blink { to { opacity: .25; transform: translateY(-2px); } }
@media (max-width: 820px) { .message-viewport { padding-bottom: 35px; }.empty-chat { padding: 10px; }.empty-chat h2 { font-size: 24px; } }
</style>
