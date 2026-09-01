<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import type { Dashboard } from '../types'
const data=ref<Dashboard>({metrics:{},ticketStatus:[],ticketTrend:[]}),loading=ref(false)
const cards=computed(()=>[{k:'userCount',t:'用户总数'},{k:'sessionCount',t:'会话总数'},{k:'openTicketCount',t:'待处理工单'},{k:'publishedFaqCount',t:'已发布 FAQ'},{k:'publishedManualCount',t:'已发布手册'}])
const maxStatus=computed(()=>Math.max(1,...data.value.ticketStatus.map(x=>Number(x.value)))),maxTrend=computed(()=>Math.max(1,...data.value.ticketTrend.map(x=>Number(x.value))))
onMounted(async()=>{loading.value=true;try{data.value=await api.get('/dashboard/overview')}catch(e){ElMessage.error((e as Error).message)}finally{loading.value=false}})
</script>
<template><div v-loading="loading"><div class="page-heading"><div><h2>管理工作台</h2><p>快速了解用户服务与知识内容运行情况</p></div></div><div class="metric-grid"><div v-for="c in cards" :key="c.k" class="panel metric-card"><small>{{c.t}}</small><strong>{{data.metrics[c.k]||0}}</strong></div></div><div class="dashboard-grid"><div class="panel chart-card"><h3>工单状态分布</h3><div class="status-bars"><div v-for="s in data.ticketStatus" :key="s.name" class="status-row"><span>{{s.name}}</span><div class="bar-track"><i :style="{width:`${Number(s.value)/maxStatus*100}%`}"/></div><b>{{s.value}}</b></div></div></div><div class="panel chart-card"><h3>最近 7 天工单趋势</h3><div class="trend"><div v-for="d in data.ticketTrend" :key="d.day" class="trend-col"><i :style="{height:`${Math.max(4,Number(d.value)/maxTrend*165)}px`}"/><span>{{String(d.day).slice(5)}}</span><b style="display:block;color:#345">{{d.value}}</b></div></div></div></div></div></template>
