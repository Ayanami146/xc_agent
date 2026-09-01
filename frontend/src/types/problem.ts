export interface ProblemDetail {
  type?: string
  title: string
  status: number
  code: string
  detail?: string
  requestId?: string
  errors?: Array<{ field: string; message: string }>
}

export interface ApiEnvelope<T> {
  requestId: string
  data: T
}

export interface PageData<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}
