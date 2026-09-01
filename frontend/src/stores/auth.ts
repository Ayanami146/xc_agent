import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authService from '../services/auth'
import type { AuthSession, LoginPayload } from '../types/auth'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<AuthSession | null>(null)
  const loading = ref(false)
  const initialized = ref(false)
  const isAuthenticated = computed(() => Boolean(session.value))
  const user = computed(() => session.value?.user ?? null)

  async function login(payload: LoginPayload) {
    loading.value = true
    try {
      session.value = await authService.login(payload)
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try { await authService.logout() } finally {
      session.value = null
    }
  }

  async function initialize() {
    if (initialized.value) return
    try { session.value = await authService.refreshSession() } catch { session.value = null } finally { initialized.value = true }
  }

  function clearSession() { session.value = null }

  return { session, loading, initialized, isAuthenticated, user, login, logout, initialize, clearSession }
})
