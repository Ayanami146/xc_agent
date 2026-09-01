<script setup lang="ts">
import type { TicketStatus } from '../types/ticket'
defineProps<{ status: TicketStatus }>()
const labels: Record<TicketStatus, string> = { PENDING: '待处理', PROCESSING: '处理中', WAITING_USER: '待补充', RESOLVED: '已解决', CLOSED: '已关闭' }
</script>
<template><span class="ticket-status" :class="status.toLowerCase()"><i />{{ labels[status] }}</span></template>
<style scoped>
.ticket-status { width: fit-content; padding: 5px 9px; border-radius: 999px; display: inline-flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 700; background: #f1f4f8; color: #67778c; }
.ticket-status i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.pending { color: #d37814; background: #fff5e8; }.processing { color: #1769df; background: #eaf3ff; }.waiting_user { color: #a866d0; background: #f7edff; }.resolved { color: #26865b; background: #eaf9f1; }.closed { color: #778496; background: #f0f3f6; }
</style>
