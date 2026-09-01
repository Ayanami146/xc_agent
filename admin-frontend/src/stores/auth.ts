import { defineStore } from 'pinia'
import type { AdminSession } from '../types'
import * as authApi from '../api'

export const useAuthStore = defineStore('admin-auth', {
  state: () => ({ session: null as AdminSession | null, initialized: false }),
  getters: { authenticated: s => !!s.session, isAdmin: s => s.session?.role === 'ADMIN' },
  actions: {
    async initialize() { if (this.initialized) return; try { this.session = await authApi.refresh() } catch { this.session = null } finally { this.initialized = true } },
    async login(account:string,password:string) { this.session = await authApi.login(account,password); this.initialized = true },
    async logout() { try { await authApi.logout() } finally { this.session=null; this.initialized=true } },
  },
})
