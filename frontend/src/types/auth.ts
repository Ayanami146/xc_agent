export type LoginMode = 'password' | 'sms'

export interface AuthUser {
  id: string
  name: string
  phone: string
  avatarText: string
}

export interface PasswordLoginPayload {
  mode: 'password'
  account: string
  password: string
  rememberDevice: boolean
}

export interface SmsLoginPayload {
  mode: 'sms'
  phone: string
  code: string
  rememberDevice: boolean
}

export type LoginPayload = PasswordLoginPayload | SmsLoginPayload

export interface AuthSession {
  accessToken: string
  expiresAt: string
  user: AuthUser
}
