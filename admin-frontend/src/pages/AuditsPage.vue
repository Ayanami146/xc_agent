<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import type { Audit, Page } from '../types'
const rows=ref<Audit[]>([]),total=ref(0),loading=ref(false),query=reactive({operatorId:'',resourceType:'',action:'',requestId:'',from:'',to:'',page:1,pageSize:20})
async function load(){loading.value=true;try{const p=await api.get<Page<Audit>>('/audits',query);rows.value=p.items;total.value=p.total}catch(e){ElMessage.error((e as Error).message)}finally{loading.value=false}}
onMounted(load)
</script>
<template><div><div class="page-heading"><div><h2>操作审计</h2><p>查看管理端关键写操作及请求追踪信息</p></div></div><div class="panel toolbar"><el-input v-model="query.operatorId" clearable placeholder="操作人 ID"/><el-input v-model="query.resourceType" clearable placeholder="资源类型"/><el-input v-model="query.requestId" clearable placeholder="Request ID"/><el-date-picker v-model="query.from" value-format="YYYY-MM-DD" type="date" placeholder="开始日期"/><el-date-picker v-model="query.to" value-format="YYYY-MM-DD" type="date" placeholder="结束日期"/><el-button type="primary" @click="load">查询</el-button></div><div class="panel table-panel"><el-table v-loading="loading" :data="rows" stripe><el-table-column prop="createdAt" label="时间" width="190"/><el-table-column prop="actorId" label="操作人" width="160"/><el-table-column prop="action" label="动作" width="180"/><el-table-column prop="resourceType" label="资源" width="140"/><el-table-column prop="resourceId" label="资源 ID" min-width="180"/><el-table-column prop="requestId" label="Request ID" min-width="210"/><el-table-column prop="ipAddress" label="IP" width="140"/><el-table-column prop="detailJson" label="安全摘要" min-width="220" show-overflow-tooltip/></el-table><div class="pagination"><el-pagination v-model:current-page="query.page" layout="total,prev,pager,next" :total="total" @change="load"/></div></div></div></template>
