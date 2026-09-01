<script setup lang="ts">
import { ChatDotRound, Delete, EditPen, MoreFilled, Plus, Search, Tickets } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useChatStore } from '../stores/chat'
import { problemMessage } from '../services/http'

const emit = defineEmits<{ selected: [] }>()
const router = useRouter()
const chat = useChatStore()

async function renameSession(id: number, current: string) {
  const result = await ElMessageBox.prompt('请输入新的会话名称', '重命名会话', { inputValue: current, inputPattern: /^.{1,30}$/, inputErrorMessage: '请输入 1～30 个字符' }).catch(() => null)
  if (result?.value) try { await chat.rename(id, result.value) } catch (error) { ElMessage.error(problemMessage(error, '会话重命名失败')) }
}

async function deleteSession(id: number) {
  const confirmed = await ElMessageBox.confirm('删除后会话将从当前列表移除，是否继续？', '删除会话', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }).catch(() => false)
  if (confirmed) try { await chat.remove(id) } catch (error) { ElMessage.error(problemMessage(error, '会话删除失败')) }
}
</script>

<template>
  <aside class="panel session-panel">
    <button class="new-chat" @click="chat.newSession(); emit('selected')"><el-icon><Plus /></el-icon><span>开启新对话</span></button>
    <label class="search-field"><el-icon><Search /></el-icon><input v-model="chat.searchKeyword" placeholder="搜索历史对话" /></label>
    <div class="panel-section-title"><span>最近对话</span><span>{{ chat.sessions.length }}</span></div>
    <div class="conversation-list">
      <article v-for="conversation in chat.filteredSessions" :key="conversation.id" class="conversation-item" :class="{ active: chat.activeSessionId === conversation.id }">
        <button class="conversation-main" @click="chat.selectSession(conversation.id); emit('selected')">
          <span class="conversation-icon"><el-icon><ChatDotRound /></el-icon></span>
          <span class="conversation-copy"><strong>{{ conversation.title }}</strong><small>{{ conversation.preview }}</small></span>
          <time>{{ conversation.updatedAt.replace('今天 ', '') }}</time>
        </button>
        <el-dropdown trigger="click" class="conversation-menu">
          <button aria-label="会话操作"><el-icon><MoreFilled /></el-icon></button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item :icon="EditPen" @click="renameSession(conversation.id, conversation.title)">重命名</el-dropdown-item><el-dropdown-item :icon="Delete" divided @click="deleteSession(conversation.id)">删除</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </article>
      <div v-if="!chat.filteredSessions.length" class="sidebar-empty">未找到相关对话</div>
    </div>
    <div class="ticket-entry">
      <div><el-icon><Tickets /></el-icon><strong>留言工单</strong></div><p>复杂问题可提交工单，由专业客服跟进处理</p><button @click="router.push('/tickets')">进入工单中心 <span>→</span></button>
    </div>
  </aside>
</template>

<style scoped>
.conversation-item { position: relative; display: block; padding: 0; }
.conversation-main { width: 100%; min-height: 66px; padding: 10px 30px 10px 10px; border: 0; background: transparent; display: grid; grid-template-columns: 34px 1fr auto; gap: 9px; text-align: left; cursor: pointer; }
.conversation-menu { position: absolute; right: 6px; bottom: 4px; opacity: 0; transition: opacity .2s; }
.conversation-item:hover .conversation-menu, .conversation-item.active .conversation-menu { opacity: 1; }
.conversation-menu button { width: 26px; height: 26px; border: 0; border-radius: 7px; background: transparent; color: #7990aa; cursor: pointer; }
.sidebar-empty { padding: 30px 0; text-align: center; color: #9aa8b9; font-size: 13px; }
</style>
