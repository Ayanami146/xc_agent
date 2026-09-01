<script setup lang="ts">
import { Bell, ChatDotRound, Document, Fold, Operation, SwitchButton, Tickets } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import BrandLogo from './BrandLogo.vue'
import { useAuthStore } from '../stores/auth'

defineProps<{ showPanelControls?: boolean }>()
const emit = defineEmits<{ toggleSessions: []; toggleKnowledge: [] }>()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

async function logout() {
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <header class="topbar app-header">
    <div class="header-start">
      <button v-if="showPanelControls" class="icon-button mobile-control" aria-label="打开会话列表" @click="emit('toggleSessions')"><el-icon><Fold /></el-icon></button>
      <BrandLogo />
    </div>
    <nav class="main-nav" aria-label="主导航">
      <router-link to="/chat" :class="{ active: route.path.startsWith('/chat') }"><el-icon><ChatDotRound /></el-icon><span>智能客服</span></router-link>
      <router-link to="/tickets" :class="{ active: route.path.startsWith('/tickets') }"><el-icon><Tickets /></el-icon><span>工单中心</span></router-link>
    </nav>
    <div class="topbar-actions">
      <button v-if="showPanelControls" class="icon-button tablet-control" aria-label="打开知识中心" @click="emit('toggleKnowledge')"><el-icon><Operation /></el-icon></button>
      <button class="icon-button" aria-label="通知"><el-icon><Bell /></el-icon><i /></button>
      <el-dropdown trigger="click">
        <button class="user-chip user-button" aria-label="打开用户菜单">
          <span class="avatar">{{ auth.user?.avatarText ?? '知' }}</span><span class="user-name">{{ auth.user?.name ?? '知识辅助' }}</span><span class="chevron">⌄</span>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :icon="Document">个人信息</el-dropdown-item>
            <el-dropdown-item divided :icon="SwitchButton" @click="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
.app-header { gap: 20px; }
.header-start { display: flex; align-items: center; gap: 8px; min-width: 245px; }
.main-nav { align-self: stretch; display: flex; align-items: center; justify-content: center; gap: 8px; }
.main-nav a { height: 42px; padding: 0 16px; border-radius: 12px; display: flex; align-items: center; gap: 7px; color: #687b94; text-decoration: none; font-weight: 700; font-size: 14px; }
.main-nav a:hover, .main-nav a.active { color: var(--primary); background: #eff6ff; }
.user-button { border: 0; background: transparent; cursor: pointer; }
.mobile-control, .tablet-control { display: none; }
@media (max-width: 1240px) { .tablet-control { display: grid; place-items: center; } }
@media (max-width: 820px) {
  .header-start { min-width: 0; }
  .mobile-control { display: grid; place-items: center; }
  .main-nav { position: fixed; z-index: 30; left: 50%; bottom: 10px; transform: translateX(-50%); height: 58px; padding: 6px; border: 1px solid #dbe5f1; border-radius: 18px; background: rgba(255,255,255,.96); box-shadow: 0 15px 40px rgba(23,50,80,.18); backdrop-filter: blur(15px); }
  .main-nav a { height: 46px; padding: 0 14px; }
  .main-nav span { font-size: 12px; }
  .user-name, .chevron { display: none; }
  .topbar-actions { gap: 6px; }
}
</style>
