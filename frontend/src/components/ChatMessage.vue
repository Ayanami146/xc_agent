<script setup lang="ts">
import { CopyDocument, Document, Loading, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { ChatMessage } from '../types/chat'

const props = defineProps<{ message: ChatMessage }>()
const emit = defineEmits<{ feedback: [messageId: number, value: 'up' | 'down'] }>()
async function copy() { await navigator.clipboard?.writeText(props.message.content); ElMessage.success('回答已复制') }
</script>

<template>
  <div class="message-row" :class="message.role">
    <span v-if="message.role === 'assistant'" class="assistant-avatar">AI</span>
    <div class="message-stack">
      <div class="message-bubble" :class="[message.role, message.status.toLowerCase()]">
        <div v-if="message.status === 'STREAMING' && !message.content" class="thinking"><el-icon class="is-loading"><Loading /></el-icon><span>正在{{ message.stage === 'retrieval' ? '检索知识库' : '分析问题' }}…</span></div>
        <p v-else>{{ message.content }}</p>
        <div v-if="message.status === 'INTERRUPTED'" class="message-warning"><el-icon><WarningFilled /></el-icon> 回答已中断，未完成内容不作为后续上下文</div>
        <div v-if="message.citations?.length" class="citation-list">
          <button v-for="citation in message.citations" :key="citation.sourceId ?? citation.sourceLocator"><el-icon><Document /></el-icon><span><strong>{{ citation.title }}</strong><small>{{ citation.snippet }}<template v-if="citation.page"> · 第 {{ citation.page }} 页</template></small></span></button>
        </div>
      </div>
      <div v-if="message.role === 'assistant' && message.status === 'COMPLETED'" class="message-actions">
        <button aria-label="有帮助" :class="{ active: message.feedback === 'up' }" @click="emit('feedback', message.id, 'up')">赞</button>
        <button aria-label="无帮助" :class="{ active: message.feedback === 'down' }" @click="emit('feedback', message.id, 'down')">踩</button>
        <button aria-label="复制回答" @click="copy"><el-icon><CopyDocument /></el-icon> 复制</button>
      </div>
      <time>{{ message.createdAt }}</time>
    </div>
  </div>
</template>

<style scoped>
.message-row { min-width: 0; display: flex; gap: 12px; align-items: flex-start; margin-bottom: 22px; }
.message-row.user { justify-content: flex-end; }
.message-stack { min-width: 0; max-width: min(76%, 760px); display: flex; flex-direction: column; gap: 7px; }
.message-row.user .message-stack { align-items: flex-end; }
.message-bubble { padding: 14px 17px; border: 1px solid #e0e8f2; border-radius: 7px 18px 18px 18px; background: #fff; box-shadow: 0 8px 24px rgba(43,76,109,.05); }
.message-bubble.user { border: 0; border-radius: 18px 7px 18px 18px; color: #fff; background: linear-gradient(135deg, #1769df, #347ff0); box-shadow: 0 9px 20px rgba(23,105,223,.16); }
.message-bubble.failed { border-color: #ffd7d7; background: #fff8f8; }
.message-bubble p { margin: 0; line-height: 1.75; white-space: pre-wrap; overflow-wrap: anywhere; }
.thinking { min-width: 180px; display: flex; align-items: center; gap: 9px; color: #4d6b91; }
.message-warning { margin-top: 10px; display: flex; align-items: center; gap: 5px; color: #a56b15; font-size: 12px; }
.citation-list { margin-top: 12px; display: flex; flex-direction: column; gap: 7px; }
.citation-list button { padding: 9px 10px; border: 0; border-radius: 9px; display: flex; gap: 8px; text-align: left; color: #58708f; background: #f4f8fc; cursor: pointer; }
.citation-list span { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.citation-list strong { font-size: 12px; color: #3e5f87; }
.citation-list small { line-height: 1.45; overflow-wrap: anywhere; }
.message-actions { display: flex; gap: 5px; }
.message-actions button { padding: 4px 7px; border: 0; border-radius: 7px; color: #8291a5; background: transparent; cursor: pointer; font-size: 11px; }
.message-actions button:hover, .message-actions button.active { color: var(--primary); background: #eef5ff; }
.message-stack > time { color: #a3afbd; font-size: 10px; }
@media (max-width: 620px) { .message-stack { max-width: 88%; } }
</style>
