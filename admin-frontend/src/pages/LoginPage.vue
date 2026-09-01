<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
const form=reactive({account:'',password:''}),loading=ref(false),auth=useAuthStore(),router=useRouter(),route=useRoute()
async function submit(){if(!form.account||!form.password){ElMessage.warning('请输入账号和密码');return}loading.value=true;try{await auth.login(form.account,form.password);ElMessage.success('登录成功');router.replace(String(route.query.redirect||'/dashboard'))}catch(e){ElMessage.error((e as Error).message)}finally{loading.value=false}}
</script>
<template><div class="login-page"><section class="login-visual"><div class="admin-brand"><div class="brand-mark">信</div><div class="brand-copy">信创智能客服<small>MANAGEMENT CONSOLE</small></div></div><div class="login-copy"><span>SECURE · EFFICIENT · TRACEABLE</span><h1>让每一次服务<br>都有据可循</h1><p>统一处理终端工单、维护知识内容并审查关键操作，为信创终端服务提供清晰可靠的管理入口。</p><div class="login-points"><div>工单全流程处理与状态追踪</div><div>FAQ 与维修手册统一维护</div><div>管理员操作全程留痕</div></div></div></section><section class="login-form-side"><div class="login-card"><span style="color:var(--primary);font-size:12px;font-weight:800">管理员入口</span><h2>欢迎回来</h2><p>请输入管理员账号和密码</p><el-form @submit.prevent="submit"><el-form-item><el-input v-model="form.account" size="large" placeholder="管理员账号" autocomplete="username"/></el-form-item><el-form-item><el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" autocomplete="current-password" @keyup.enter="submit"/></el-form-item><el-button class="login-button" type="primary" :loading="loading" @click="submit">进入管理端</el-button></el-form><div class="login-tip">管理端不提供注册或找回密码功能。</div></div></section></div></template>
