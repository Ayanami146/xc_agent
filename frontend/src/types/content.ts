export type KnowledgeKind = 'FAQ' | 'MANUAL'

export interface KnowledgeItem {
  id: string
  kind: KnowledgeKind
  title: string
  summary: string
  category: string
  updatedAt: string
  hotCount?: number
  question?: string
}

export interface FaqDetail {
  id: string
  categoryId: string
  categoryName: string
  title: string
  question: string
  answer: string
  summary: string
  hotCount: number
  updatedAt: string
}

export interface DownloadGrant {
  url: string
  expiresAt: string
}
