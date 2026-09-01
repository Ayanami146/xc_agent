export type ChatRequestStatus =
  | 'ACCEPTED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'INTERRUPTED'

export type ChatMessageStatus = 'COMPLETED' | 'STREAMING' | 'INTERRUPTED' | 'FAILED'
export type ChatRole = 'user' | 'assistant'
export type ChatStage = 'queued' | 'safety' | 'intent' | 'retrieval' | 'generation' | 'validation'

export interface Citation {
  title: string
  sourceId?: number
  snippet: string
  sourceLocator: string
  page?: number
}

export interface ChatMessage {
  id: number
  requestId?: number
  role: ChatRole
  content: string
  status: ChatMessageStatus
  createdAt: string
  stage?: ChatStage
  citations?: Citation[]
  feedback?: 'up' | 'down'
}

export interface ChatSession {
  id: number
  title: string
  preview: string
  updatedAt: string
}

interface EventBase<TEvent extends string, TPayload> {
  event: TEvent
  requestId: number
  sequence: number
  occurredAt: string
  payload: TPayload
}

export type ChatStreamEvent =
  | EventBase<'meta', { sessionId: number; userMessageId: number; assistantMessageId: number }>
  | EventBase<'status', { stage: ChatStage; message: string }>
  | EventBase<'delta', { content: string }>
  | EventBase<'citation', { sources: Citation[] }>
  | EventBase<'usage', { model: string; promptTokens: number; completionTokens: number; totalTokens: number; estimatedCost: number }>
  | EventBase<'heartbeat', Record<string, never>>
  | EventBase<'done', { finishReason: string; messageId: number }>
  | EventBase<'error', { code: string; message: string; retryable: boolean }>

export interface ChatStreamRequest {
  sessionId?: number
  message: string
}

export interface ChatRequestResult {
  status: ChatRequestStatus
  sessionId: number
  assistantMessageId?: number
  answer?: string
  citations: Citation[]
  error: { code: string; message: string } | null
  startedAt?: string
  finishedAt?: string
}
