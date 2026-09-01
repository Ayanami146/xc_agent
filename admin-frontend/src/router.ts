import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from './stores/auth'

const router = createRouter({ history:createWebHistory(import.meta.env.BASE_URL), routes:[
  {path:'/login',name:'login',component:()=>import('./pages/LoginPage.vue'),meta:{public:true,title:'管理员登录'}},
  {path:'/',component:()=>import('./layouts/AdminLayout.vue'),meta:{auth:true},children:[
    {path:'',redirect:'/dashboard'},
    {path:'dashboard',name:'dashboard',component:()=>import('./pages/DashboardPage.vue'),meta:{title:'工作台'}},
    {path:'tickets',name:'tickets',component:()=>import('./pages/TicketsPage.vue'),meta:{title:'工单管理'}},
    {path:'faqs',name:'faqs',component:()=>import('./pages/FaqPage.vue'),meta:{title:'FAQ 管理'}},
    {path:'manuals',name:'manuals',component:()=>import('./pages/ManualPage.vue'),meta:{title:'维修手册'}},
    {path:'admins',name:'admins',component:()=>import('./pages/AdminsPage.vue'),meta:{title:'管理员',admin:true}},
    {path:'audits',name:'audits',component:()=>import('./pages/AuditsPage.vue'),meta:{title:'操作审计',admin:true}},
  ]},
  {path:'/:pathMatch(.*)*',redirect:'/dashboard'},
]})
router.beforeEach(async to=>{const auth=useAuthStore();await auth.initialize();if(to.meta.auth&&!auth.authenticated)return {name:'login',query:{redirect:to.fullPath}};if(to.meta.admin&&!auth.isAdmin)return {name:'dashboard'};if(to.name==='login'&&auth.authenticated)return {name:'dashboard'};document.title=`${String(to.meta.title||'管理端')} · 信创智能客服`})
export default router
