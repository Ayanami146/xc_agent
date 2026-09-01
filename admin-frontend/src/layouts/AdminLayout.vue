<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
const auth=useAuthStore(),route=useRoute(),router=useRouter()
const menus=computed(()=>[
  {to:'/dashboard',icon:'◫',label:'工作台'}, {to:'/tickets',icon:'▤',label:'工单管理'},
  {to:'/faqs',icon:'?',label:'FAQ 管理'}, {to:'/manuals',icon:'▣',label:'维修手册'},
  ...(auth.isAdmin?[{to:'/admins',icon:'♙',label:'管理员'},{to:'/audits',icon:'⌁',label:'操作审计'}]:[]),
])
async function signOut(){await auth.logout();router.replace('/login')}
const expired=()=>{auth.session=null;router.replace('/login')}
onMounted(()=>globalThis.addEventListener('xc-admin:expired',expired));onUnmounted(()=>globalThis.removeEventListener('xc-admin:expired',expired))
</script>
<template><div class="admin-layout"><aside class="sidebar"><div class="admin-brand"><div class="brand-mark">信</div><div class="brand-copy">智能客服<small>ADMIN CONSOLE</small></div></div><nav class="side-nav"><router-link v-for="m in menus" :key="m.to" :to="m.to"><b>{{m.icon}}</b><span>{{m.label}}</span></router-link></nav><div class="side-footer">信创终端服务平台<br>管理端 · v1.0</div></aside><main class="admin-main"><header class="admin-header"><div class="header-title"><h1>{{String(route.meta.title||'管理端')}}</h1><small>管理业务数据与知识内容</small></div><el-dropdown><div class="admin-profile"><div class="profile-avatar">{{auth.session?.displayName?.slice(0,1)}}</div><div class="profile-copy"><b>{{auth.session?.displayName}}</b><small>{{auth.session?.role}}</small></div></div><template #dropdown><el-dropdown-menu><el-dropdown-item @click="signOut">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></header><section class="content"><router-view/></section></main></div></template>
