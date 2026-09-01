import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'
import { AUTH_EXPIRED_EVENT } from './services/http'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ElementPlus)
globalThis.addEventListener(AUTH_EXPIRED_EVENT, () => {
  useAuthStore(pinia).clearSession()
  if (router.currentRoute.value.name !== 'login') void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
})
app.mount('#app')
