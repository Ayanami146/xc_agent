import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/login', name: 'login', component: () => import('../pages/LoginPage.vue'), meta: { public: true, title: '登录' } },
    { path: '/chat', name: 'chat', component: () => import('../pages/ChatPage.vue'), meta: { requiresAuth: true, title: '智能客服' } },
    { path: '/tickets', name: 'tickets', component: () => import('../pages/TicketsPage.vue'), meta: { requiresAuth: true, title: '工单中心' } },
    { path: '/tickets/:id', name: 'ticket-detail', component: () => import('../pages/TicketDetailPage.vue'), meta: { requiresAuth: true, title: '工单详情' } },
    { path: '/:pathMatch(.*)*', redirect: '/chat' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.initialize()
  if (to.meta.requiresAuth && !auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.name === 'login' && auth.isAuthenticated) return { name: 'chat' }
  document.title = `${String(to.meta.title ?? '首页')} · 信创智能客服`
})

export default router
