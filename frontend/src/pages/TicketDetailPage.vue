<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ChatLineRound, CircleCheck, Close, Document, Paperclip, Promotion, RefreshRight, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppHeader from '../components/AppHeader.vue'
import TicketStatus from '../components/TicketStatus.vue'
import { useTicketStore } from '../stores/tickets'
import { problemMessage } from '../services/http'

const route = useRoute()
const router = useRouter()
const store = useTicketStore()
const replyText = ref('')
const sending = ref(false)
const id = computed(() => String(route.params.id))

async function load() { await store.loadDetail(id.value).catch(() => router.replace('/tickets')) }
async function submitReply() {
  const content = replyText.value.trim()
  if (!content) return
  sending.value = true
  try { await store.reply(content); replyText.value = ''; ElMessage.success('补充信息已提交') } catch (error) { ElMessage.error(problemMessage(error)) } finally { sending.value = false }
}
async function closeTicket() {
  const ok = await ElMessageBox.confirm('关闭后工单将变为只读，是否继续？', '关闭工单', { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消' }).catch(() => false)
  if (ok) { try { await store.close(); ElMessage.success('工单已关闭') } catch (error) { ElMessage.error(problemMessage(error)) } }
}
async function reopen() {
  const result = await ElMessageBox.prompt('请说明重新打开的原因', '重新打开工单', {
    inputPattern: /^.{1,500}$/,
    inputErrorMessage: '请输入 1～500 个字符',
    confirmButtonText: '确认重开',
    cancelButtonText: '取消',
  }).catch(() => null)
  if (!result?.value) return
  try { await store.reopen(result.value); ElMessage.success('工单已重新进入处理流程') } catch (error) { ElMessage.error(problemMessage(error)) }
}
async function downloadAttachment(attachmentId: string) { try { await store.downloadAttachment(attachmentId) } catch (error) { ElMessage.error(problemMessage(error, '附件下载失败')) } }

watch(id, load)
onMounted(load)
</script>

<template>
  <div class="app-shell detail-page">
    <AppHeader />
    <main v-loading="store.loading" class="detail-container">
      <button class="back-button" @click="router.push('/tickets')"><el-icon><ArrowLeft /></el-icon> 返回工单列表</button>
      <template v-if="store.current">
        <section class="detail-head">
          <div><div class="detail-kicker"><span>{{ store.current.id }}</span><TicketStatus :status="store.current.status" /></div><h1>{{ store.current.title }}</h1><p>创建于 {{ store.current.createdAt }} · 最后更新 {{ store.current.updatedAt }}</p></div>
          <div class="detail-actions"><el-button v-if="store.current.status === 'RESOLVED'" :icon="RefreshRight" @click="reopen">重新打开</el-button><el-button v-if="!['CLOSED','RESOLVED'].includes(store.current.status)" :icon="Close" @click="closeTicket">关闭工单</el-button></div>
        </section>

        <div class="detail-grid">
          <div class="detail-main">
            <section class="detail-card issue-card">
              <div class="card-title"><span><el-icon><Document /></el-icon></span><div><h2>问题信息</h2><p>您提交的设备和问题详情</p></div></div>
              <dl class="issue-meta"><div><dt>问题分类</dt><dd>{{ store.current.category }}</dd></div><div><dt>设备品牌</dt><dd>{{ store.current.deviceBrand }}</dd></div><div><dt>设备型号</dt><dd>{{ store.current.deviceModel }}</dd></div><div><dt>联系方式</dt><dd>{{ store.current.contact }}</dd></div></dl>
              <div class="description-block"><span>问题描述</span><p>{{ store.current.description }}</p></div>
              <div v-if="store.current.attachments.length" class="attachment-list"><span>相关附件</span><button v-for="file in store.current.attachments" :key="file.id" @click="downloadAttachment(file.id)"><el-icon><Paperclip /></el-icon>{{ file.fileName }}</button></div>
            </section>

            <section class="detail-card conversation-card">
              <div class="card-title"><span><el-icon><ChatLineRound /></el-icon></span><div><h2>沟通记录</h2><p>客服回复与您的补充信息</p></div></div>
              <div class="reply-list">
                <article v-for="reply in store.current.replies" :key="reply.id" class="reply-item" :class="reply.sender">
                  <span class="reply-avatar"><el-icon><User /></el-icon></span>
                  <div><header><strong>{{ reply.senderName }}</strong><time>{{ reply.createdAt }}</time></header><p>{{ reply.content }}</p></div>
                </article>
                <div v-if="!store.current.replies.length" class="no-replies">客服受理后，沟通记录会显示在这里</div>
              </div>
              <div v-if="store.current.status !== 'CLOSED'" class="reply-composer">
                <el-input v-model="replyText" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="补充设备信息、错误提示或处理结果" />
                <div><span>请勿填写密码、密钥等敏感信息</span><el-button type="primary" :icon="Promotion" :loading="sending" :disabled="!replyText.trim()" @click="submitReply">提交补充</el-button></div>
              </div>
              <div v-else class="closed-note"><el-icon><CircleCheck /></el-icon>工单已关闭，沟通记录仅供查看</div>
            </section>
          </div>

          <aside class="detail-side">
            <section class="detail-card progress-card">
              <div class="card-title compact"><span><el-icon><CircleCheck /></el-icon></span><div><h2>处理进度</h2><p>{{ store.current.assignee ? `当前负责人：${store.current.assignee}` : '等待客服受理' }}</p></div></div>
              <div class="timeline">
                <article v-for="(item, index) in store.current.timeline" :key="item.id" :class="{ latest: index === store.current.timeline.length - 1 }"><i /><div><strong>{{ item.title }}</strong><p>{{ item.description }}</p><time>{{ item.createdAt }}</time></div></article>
              </div>
            </section>
            <section class="help-card"><span>需要更快获得帮助？</span><h3>返回智能客服继续排查</h3><p>您可以将客服建议带回对话，继续查询相关维修手册。</p><el-button plain @click="router.push('/chat')">前往智能客服</el-button></section>
          </aside>
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.detail-page { min-height: 100vh; padding-bottom: 50px; }.detail-container { width: min(1320px, calc(100% - 48px)); min-height: 500px; margin: 0 auto; }.back-button { margin: 28px 0 18px; padding: 0; border: 0; display: flex; align-items: center; gap: 7px; color: #62758e; background: transparent; cursor: pointer; }.back-button:hover { color: var(--primary); }.detail-head { padding: 0 2px 25px; display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; }.detail-kicker { display: flex; align-items: center; gap: 12px; }.detail-kicker > span { color: var(--primary); font-size: 12px; font-weight: 800; letter-spacing: .08em; }.detail-head h1 { margin: 12px 0 8px; font-size: 31px; letter-spacing: -.04em; }.detail-head p { margin: 0; color: #8a99aa; font-size: 13px; }.detail-actions { display: flex; gap: 8px; }
.detail-grid { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 18px; align-items: start; }.detail-main, .detail-side { display: flex; flex-direction: column; gap: 18px; }.detail-card { border: 1px solid #dfe7f0; border-radius: 18px; background: #fff; box-shadow: 0 12px 32px rgba(35,82,124,.07); overflow: hidden; }.issue-card, .conversation-card, .progress-card { padding: 22px; }.card-title { margin-bottom: 22px; display: flex; align-items: center; gap: 12px; }.card-title > span { width: 42px; height: 42px; border-radius: 13px; display: grid; place-items: center; color: var(--primary); background: #eaf3ff; font-size: 20px; }.card-title h2 { margin: 0; font-size: 18px; }.card-title p { margin: 5px 0 0; color: #98a5b6; font-size: 12px; }.card-title.compact { margin-bottom: 26px; }
.issue-meta { margin: 0; padding: 15px 18px; border-radius: 14px; display: grid; grid-template-columns: repeat(4, 1fr); gap: 15px; background: #f6f9fc; }.issue-meta div { min-width: 0; }.issue-meta dt { color: #96a4b4; font-size: 11px; }.issue-meta dd { margin: 6px 0 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #354963; font-weight: 700; font-size: 13px; }.description-block { margin-top: 20px; }.description-block > span, .attachment-list > span { color: #78899f; font-size: 12px; font-weight: 700; }.description-block p { margin: 8px 0 0; padding: 16px; border: 1px solid #e7edf4; border-radius: 12px; line-height: 1.75; color: #40536c; background: #fcfdfe; }.attachment-list { margin-top: 18px; display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }.attachment-list button { padding: 8px 10px; border: 1px solid #dce6f2; border-radius: 9px; display: flex; align-items: center; gap: 6px; color: #557397; background: #f7faff; cursor: pointer; }
.reply-list { display: flex; flex-direction: column; gap: 18px; }.reply-item { display: flex; gap: 11px; }.reply-item.user { flex-direction: row-reverse; }.reply-avatar { width: 36px; height: 36px; flex: 0 0 auto; border-radius: 11px; display: grid; place-items: center; color: var(--primary); background: #eaf3ff; }.reply-item.user .reply-avatar { color: #fff; background: var(--primary); }.reply-item > div { max-width: 78%; }.reply-item header { margin-bottom: 6px; display: flex; align-items: center; gap: 10px; }.reply-item header time { color: #a2adba; font-size: 10px; }.reply-item p { margin: 0; padding: 12px 14px; border-radius: 5px 14px 14px 14px; color: #40536b; background: #f3f7fb; line-height: 1.65; }.reply-item.user p { color: #fff; background: #2979e8; border-radius: 14px 5px 14px 14px; }.no-replies { padding: 26px; text-align: center; color: #9aa7b6; }.reply-composer { margin-top: 22px; padding-top: 20px; border-top: 1px solid #e8edf3; }.reply-composer > div { margin-top: 10px; display: flex; justify-content: space-between; align-items: center; gap: 12px; }.reply-composer span { color: #a1adbb; font-size: 11px; }.closed-note { margin-top: 20px; padding: 14px; border-radius: 12px; display: flex; justify-content: center; align-items: center; gap: 8px; color: #738398; background: #f4f6f8; }
.timeline { position: relative; }.timeline::before { content: ''; position: absolute; left: 6px; top: 8px; bottom: 10px; width: 1px; background: #dce7f4; }.timeline article { position: relative; padding: 0 0 24px 26px; }.timeline article > i { position: absolute; left: 0; top: 4px; width: 13px; height: 13px; border: 3px solid #b8cce4; border-radius: 50%; background: #fff; }.timeline article.latest > i { border-color: var(--primary); box-shadow: 0 0 0 4px #e9f3ff; }.timeline strong { font-size: 13px; }.timeline p { margin: 6px 0; color: #77899e; line-height: 1.5; font-size: 12px; }.timeline time { color: #a2aebb; font-size: 10px; }.help-card { padding: 24px; border-radius: 18px; color: #fff; background: radial-gradient(circle at 90% 10%, rgba(65,216,255,.28), transparent 38%), linear-gradient(145deg, #0b4ca9, #187ecf); box-shadow: 0 14px 34px rgba(12,76,169,.18); }.help-card > span { color: #86dcf5; font-size: 11px; font-weight: 800; letter-spacing: .08em; }.help-card h3 { margin: 9px 0; }.help-card p { margin: 0 0 18px; color: rgba(255,255,255,.72); line-height: 1.65; font-size: 12px; }.help-card :deep(.el-button) { color: #fff; border-color: rgba(255,255,255,.45); background: rgba(255,255,255,.08); }
@media (max-width: 960px) { .detail-grid { grid-template-columns: 1fr; }.detail-side { display: grid; grid-template-columns: 1fr 1fr; }.issue-meta { grid-template-columns: repeat(2,1fr); } }
@media (max-width: 680px) { .detail-page { padding-bottom: 86px; }.detail-container { width: calc(100% - 24px); }.detail-head { align-items: flex-start; flex-direction: column; }.detail-head h1 { font-size: 25px; }.detail-actions { width: 100%; }.detail-actions :deep(.el-button) { flex: 1; }.detail-side { display: flex; }.issue-card, .conversation-card, .progress-card { padding: 17px; }.issue-meta { grid-template-columns: 1fr; }.reply-item > div { max-width: 86%; }.reply-composer > div { align-items: stretch; flex-direction: column; }.reply-composer :deep(.el-button) { width: 100%; } }
</style>
