import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { AdminSession, Envelope } from './types'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1/admin'
let token = ''
let refreshPromise: Promise<AdminSession> | null = null

export const http = axios.create({ baseURL, timeout: 20_000, withCredentials: true, headers: { Accept: 'application/json' } })
export const setToken = (value: string) => { token = value }

http.interceptors.request.use((config) => {
  config.headers.set('X-Request-Id', globalThis.crypto?.randomUUID?.() || `admin_${Date.now()}`)
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  return config
})

export async function login(account: string, password: string) {
  const response = await http.post<Envelope<AdminSession>>('/auth/login', { account, password })
  setToken(response.data.data.accessToken)
  return response.data.data
}
export async function refresh() {
  if (!refreshPromise) refreshPromise = axios.post<Envelope<AdminSession>>(`${baseURL}/auth/refresh`, {}, { withCredentials: true })
    .then(r => { setToken(r.data.data.accessToken); return r.data.data }).finally(() => { refreshPromise = null })
  return refreshPromise
}
export async function logout() { await http.post('/auth/logout'); setToken('') }

http.interceptors.response.use(undefined, async (error: AxiosError) => {
  const request = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
  if (error.response?.status === 401 && request && !request._retried && !request.url?.includes('/auth/')) {
    request._retried = true
    try { const session = await refresh(); request.headers.set('Authorization', `Bearer ${session.accessToken}`); return http.request(request) }
    catch { setToken(''); globalThis.dispatchEvent(new Event('xc-admin:expired')) }
  }
  const body = error.response?.data as { detail?: string; title?: string } | undefined
  return Promise.reject(new Error(body?.detail || body?.title || error.message || '请求失败'))
})

export const api = {
  get: <T>(url:string, params?:unknown) => http.get<Envelope<T>>(url,{params}).then(r=>r.data.data),
  post: <T>(url:string, data?:unknown, version?:number) => http.post<Envelope<T>>(url,data,{headers:version===undefined?{}:{'If-Match':String(version)}}).then(r=>r.data.data),
  patch: <T>(url:string, data:unknown, version:number) => http.patch<Envelope<T>>(url,data,{headers:{'If-Match':String(version)}}).then(r=>r.data.data),
  delete: (url:string, version:number) => http.delete(url,{headers:{'If-Match':String(version)}}),
}
