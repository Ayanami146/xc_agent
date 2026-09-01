import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiEnvelope, ProblemDetail } from '../types/problem'

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1'
let accessToken = ''
let refreshPromise: Promise<string> | null = null

export const AUTH_EXPIRED_EVENT = 'xinchuang:auth-expired'

export const http = axios.create({
  baseURL: API_BASE,
  timeout: 15_000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: { Accept: 'application/json' },
})

export function setAccessToken(token: string) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

function requestId() {
  return globalThis.crypto?.randomUUID?.() ?? `web_${Date.now()}_${Math.random().toString(16).slice(2)}`
}

http.interceptors.request.use((config) => {
  config.headers.set('X-Request-Id', requestId())
  if (accessToken) config.headers.set('Authorization', `Bearer ${accessToken}`)
  return config
})

export async function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = axios
      .post<ApiEnvelope<{ accessToken: string }>>(`${API_BASE}/auth/refresh`, {}, {
        withCredentials: true,
        xsrfCookieName: 'XSRF-TOKEN',
        xsrfHeaderName: 'X-XSRF-TOKEN',
      })
      .then((response) => {
        setAccessToken(response.data.data.accessToken)
        return response.data.data.accessToken
      })
      .finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

http.interceptors.response.use(undefined, async (error: AxiosError<ProblemDetail>) => {
  const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
  if (error.response?.status === 401 && original && !original._retried && !original.url?.includes('/auth/')) {
    original._retried = true
    try {
      const token = await refreshAccessToken()
      original.headers.set('Authorization', `Bearer ${token}`)
      return http.request(original)
    } catch {
      setAccessToken('')
      globalThis.dispatchEvent?.(new Event(AUTH_EXPIRED_EVENT))
    }
  }

  const problem: ProblemDetail = error.response?.data ?? {
    title: '网络请求失败', status: error.response?.status ?? 0, code: 'NETWORK_ERROR', detail: error.message,
  }
  return Promise.reject(problem)
})

export const apiMode = import.meta.env.VITE_API_MODE === 'mock' ? 'mock' : 'remote'
export const newIdempotencyKey = () => globalThis.crypto?.randomUUID?.() ?? `idem_${Date.now()}`

export function problemMessage(error: unknown, fallback = '操作失败，请稍后重试') {
  const problem = error as Partial<ProblemDetail> | undefined
  return problem?.detail || problem?.title || fallback
}
