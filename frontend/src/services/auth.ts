import type { AuthSession, LoginPayload } from '../types/auth'
import type { ApiEnvelope } from '../types/problem'
import { apiMode, http, newIdempotencyKey, setAccessToken } from './http'

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

export async function login(payload: LoginPayload): Promise<AuthSession> {
  if (apiMode === 'remote') {
    const response = await http.post<ApiEnvelope<AuthSession>>('/auth/login', payload)
    setAccessToken(response.data.data.accessToken)
    return response.data.data
  }

  await wait(650)
  const session: AuthSession = {
    accessToken: `mock_${Date.now()}`,
    expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
    user: { id: 'user_demo', name: '知识辅助', phone: '138****3800', avatarText: '知' },
  }
  setAccessToken(session.accessToken)
  return session
}

export async function sendSmsCode(phone: string) {
  if (apiMode === 'remote') {
    await http.post('/auth/sms-codes', { phone }, { headers: { 'Idempotency-Key': newIdempotencyKey() } })
    return
  }
  await wait(420)
}

export async function refreshSession(): Promise<AuthSession> {
  if (apiMode === 'remote') {
    const response = await http.post<ApiEnvelope<AuthSession>>('/auth/refresh')
    setAccessToken(response.data.data.accessToken)
    return response.data.data
  }
  throw new Error('MOCK_SESSION_NOT_PERSISTED')
}

export async function logout() {
  if (apiMode === 'remote') {
    await http.post('/auth/logout', {}, { headers: { 'Idempotency-Key': newIdempotencyKey() } })
  } else {
    await wait(160)
  }
  setAccessToken('')
}
