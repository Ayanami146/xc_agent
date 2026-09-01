<script setup lang="ts">
import { onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCheck, Clock, DocumentAdd, EditPen, Plus, Search, Service, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AppHeader from '../components/AppHeader.vue'
import NewTicketDrawer from '../components/NewTicketDrawer.vue'
import TicketStatus from '../components/TicketStatus.vue'
import { useTicketStore } from '../stores/tickets'
import type { TicketDraft, TicketStatus as TicketStatusType } from '../types/ticket'
import { problemMessage } from '../services/http'

const router = useRouter()
const store = useTicketStore()
const drawerOpen = ref(false)
const submitting = ref(false)

const statusCards: Array<{ status: TicketStatusType; label: string; description: string; icon: Component }> = [
  { status: 'PENDING', label: '待处理', description: '等待客服受理', icon: Clock },
  { status: 'PROCESSING', label: '处理中', description: '客服正在跟进', icon: Service },
  { status: 'WAITING_USER', label: '待补充', description: '需要补充信息', icon: EditPen },
  { status: 'RESOLVED', label: '已解决', description: '已有处理结论', icon: CircleCheck },
]

async function createTicket(draft: TicketDraft, files: File[]) {
  submitting.value = true
  try {
    const ticket = await store.create(draft, files)
    drawerOpen.value = false
    ElMessage.success(`工单 ${ticket.id} 已提交`)
    await router.push(`/tickets/${ticket.id}`)
  } catch (error) { ElMessage.error(problemMessage(error, '工单提交失败')) } finally { submitting.value = false }
}

onMounted(async () => {
  try { await store.load() } catch (error) { ElMessage.error(problemMessage(error, '工单加载失败')) }
})
</script>

<template>
  <div class="app-shell tickets-page">
    <AppHeader />
    <main class="page-container">
      <section class="page-hero">
        <div><span class="eyebrow">SERVICE TICKETS</span><h1>留言工单</h1><p>复杂问题交由专业客服持续跟进，您可以随时查看处理进度。</p></div>
        <el-button type="primary" size="large" :icon="Plus" @click="drawerOpen = true">提交新工单</el-button>
      </section>

      <section class="ticket-stats" aria-label="工单状态统计">
        <button v-for="card in statusCards" :key="card.status" :class="{ active: store.statusFilter === card.status }" @click="store.statusFilter = store.statusFilter === card.status ? 'ALL' : card.status">
          <span class="stat-icon"><el-icon><component :is="card.icon" /></el-icon></span>
          <span class="stat-copy"><strong>{{ store.counts[card.status] }}</strong><span>{{ card.label }}</span><small>{{ card.description }}</small></span>
        </button>
        <div class="response-card"><span>平均响应</span><strong>28<small>分钟</small></strong><p>工作时间内首次响应</p></div>
      </section>

      <section class="ticket-list-card">
        <div class="list-toolbar">
          <div><h2>我的工单</h2><p>共 {{ store.filteredTickets.length }} 条记录</p></div>
          <div class="filters">
            <el-input v-model="store.keyword" :prefix-icon="Search" clearable placeholder="搜索编号、问题或设备" />
            <el-select v-model="store.statusFilter" aria-label="按状态筛选"><el-option label="全部状态" value="ALL" /><el-option v-for="item in statusCards" :key="item.status" :label="item.label" :value="item.status" /><el-option label="已关闭" value="CLOSED" /></el-select>
          </div>
        </div>

        <div v-loading="store.loading" class="ticket-table">
          <button v-for="ticket in store.filteredTickets" :key="ticket.id" class="ticket-row" @click="router.push(`/tickets/${ticket.id}`)">
            <span class="ticket-leading"><span class="ticket-type-icon"><el-icon><DocumentAdd /></el-icon></span><span><strong>{{ ticket.title }}</strong><small>{{ ticket.id }} · {{ ticket.category }}</small></span></span>
            <span class="ticket-device"><small>设备</small><strong>{{ ticket.deviceBrand }} {{ ticket.deviceModel }}</strong></span>
            <TicketStatus :status="ticket.status" />
            <span class="ticket-update"><small>最后更新</small><strong>{{ ticket.updatedAt }}</strong></span>
            <el-icon class="row-arrow"><View /></el-icon>
          </button>
          <el-empty v-if="!store.loading && !store.filteredTickets.length" description="没有符合条件的工单"><el-button type="primary" @click="drawerOpen = true">提交工单</el-button></el-empty>
        </div>
      </section>

      <section class="service-promise">
        <div><span>01</span><p><strong>提交问题</strong><small>填写设备与异常信息</small></p></div>
        <i />
        <div><span>02</span><p><strong>专业受理</strong><small>客服分析并持续沟通</small></p></div>
        <i />
        <div><span>03</span><p><strong>问题解决</strong><small>形成明确处理结论</small></p></div>
      </section>
    </main>
    <NewTicketDrawer v-model="drawerOpen" :submitting="submitting" @submit="createTicket" />
  </div>
</template>

<style scoped>
.tickets-page { min-height: 100vh; padding-bottom: 42px; }.page-container { width: min(1460px, calc(100% - 48px)); margin: 0 auto; }.page-hero { padding: 44px 4px 28px; display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; }.page-hero h1 { margin: 7px 0 9px; font-size: 36px; letter-spacing: -.04em; }.page-hero p { margin: 0; color: #718198; }.page-hero :deep(.el-button) { height: 46px; border-radius: 12px; padding-inline: 22px; box-shadow: 0 10px 24px rgba(23,105,223,.2); }
.ticket-stats { display: grid; grid-template-columns: repeat(4, 1fr) .9fr; gap: 14px; }.ticket-stats > button, .response-card { min-height: 120px; padding: 18px; border: 1px solid #dfe7f0; border-radius: 17px; display: flex; align-items: center; gap: 14px; text-align: left; background: #fff; box-shadow: 0 10px 28px rgba(35,82,124,.06); }.ticket-stats > button { cursor: pointer; transition: transform .2s, border-color .2s; }.ticket-stats > button:hover, .ticket-stats > button.active { transform: translateY(-2px); border-color: #80b2f1; box-shadow: 0 13px 32px rgba(23,105,223,.11); }.stat-icon { width: 44px; height: 44px; border-radius: 14px; display: grid; place-items: center; color: var(--primary); background: #edf5ff; font-size: 21px; }.stat-copy { display: grid; grid-template-columns: auto 1fr; align-items: baseline; gap: 0 8px; }.stat-copy strong { font-size: 27px; }.stat-copy > span { font-weight: 750; }.stat-copy small { grid-column: 1 / -1; margin-top: 5px; color: #98a5b6; }.response-card { flex-direction: column; align-items: flex-start; justify-content: center; color: #fff; background: linear-gradient(135deg, #0c4da9, #188bdc); border: 0; }.response-card > span, .response-card p { color: rgba(255,255,255,.72); }.response-card strong { font-size: 28px; }.response-card strong small { margin-left: 3px; font-size: 12px; }.response-card p { margin: 0; font-size: 11px; }
.ticket-list-card { margin-top: 18px; border: 1px solid #dfe7f0; border-radius: 18px; background: #fff; box-shadow: var(--shadow); overflow: hidden; }.list-toolbar { padding: 22px 24px; display: flex; align-items: center; justify-content: space-between; gap: 20px; border-bottom: 1px solid #e8edf3; }.list-toolbar h2 { margin: 0; font-size: 20px; }.list-toolbar p { margin: 5px 0 0; color: #98a5b6; font-size: 12px; }.filters { width: min(100%, 500px); display: grid; grid-template-columns: 1fr 150px; gap: 10px; }.ticket-table { min-height: 290px; }.ticket-row { width: 100%; min-height: 92px; padding: 16px 24px; border: 0; border-bottom: 1px solid #edf1f5; display: grid; grid-template-columns: minmax(280px, 1.4fr) minmax(180px, .8fr) 92px minmax(160px, .6fr) 24px; align-items: center; gap: 20px; text-align: left; background: #fff; cursor: pointer; transition: background .18s; }.ticket-row:hover { background: #f8fbff; }.ticket-leading { min-width: 0; display: flex; align-items: center; gap: 12px; }.ticket-type-icon { width: 42px; height: 42px; flex: 0 0 auto; border-radius: 13px; display: grid; place-items: center; color: var(--primary); background: #edf5ff; }.ticket-leading > span:last-child, .ticket-device, .ticket-update { min-width: 0; display: flex; flex-direction: column; gap: 5px; }.ticket-leading strong, .ticket-device strong, .ticket-update strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.ticket-leading small, .ticket-device small, .ticket-update small { color: #97a5b6; font-size: 11px; }.ticket-device strong, .ticket-update strong { color: #51647e; font-size: 12px; }.row-arrow { color: #a5b1bf; }
.service-promise { margin: 22px 0; padding: 22px 28px; border: 1px solid #dfe7f0; border-radius: 18px; display: grid; grid-template-columns: 1fr 80px 1fr 80px 1fr; align-items: center; background: rgba(255,255,255,.75); }.service-promise > div { display: flex; align-items: center; gap: 13px; }.service-promise > div > span { width: 38px; height: 38px; border-radius: 50%; display: grid; place-items: center; color: var(--primary); background: #eaf3ff; font-size: 11px; font-weight: 850; }.service-promise p { display: flex; flex-direction: column; gap: 5px; }.service-promise p strong { font-size: 13px; }.service-promise p small { color: #96a4b5; }.service-promise i { height: 1px; background: linear-gradient(90deg, #bdd7f5, #e1ebf6); }
@media (max-width: 1120px) { .ticket-stats { grid-template-columns: repeat(2, 1fr); }.response-card { display: none; }.ticket-row { grid-template-columns: minmax(260px,1fr) 90px minmax(145px,.6fr) 24px; }.ticket-device { display: none; } }
@media (max-width: 760px) { .tickets-page { padding-bottom: 86px; }.page-container { width: calc(100% - 24px); }.page-hero { padding-top: 28px; align-items: flex-start; }.page-hero h1 { font-size: 29px; }.page-hero p { max-width: 260px; line-height: 1.55; }.page-hero :deep(.el-button span) { font-size: 0; }.page-hero :deep(.el-button span)::after { content: '新建'; font-size: 14px; }.ticket-stats { grid-template-columns: repeat(2, 1fr); gap: 9px; }.ticket-stats > button { min-height: 96px; padding: 13px; }.stat-icon { width: 36px; height: 36px; }.stat-copy strong { font-size: 22px; }.stat-copy small { display: none; }.list-toolbar { align-items: stretch; flex-direction: column; }.filters { width: 100%; grid-template-columns: 1fr 120px; }.ticket-row { min-height: 106px; padding: 14px 16px; grid-template-columns: 1fr auto; gap: 10px; }.ticket-leading { grid-column: 1 / -1; }.ticket-update, .row-arrow { display: none; }.service-promise { grid-template-columns: 1fr; gap: 12px; }.service-promise i { display: none; } }
</style>
