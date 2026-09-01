import { mockKnowledge } from '../mocks/data'
import type { DownloadGrant, FaqDetail, KnowledgeItem, KnowledgeKind } from '../types/content'
import type { ApiEnvelope, PageData } from '../types/problem'
import { apiMode, http } from './http'

export async function listKnowledge(kind: KnowledgeKind, keyword = ''): Promise<KnowledgeItem[]> {
  if (apiMode === 'remote') {
    const path = kind === 'FAQ' ? '/faq' : '/manuals'
    const response = await http.get<ApiEnvelope<PageData<KnowledgeItem>>>(path, { params: { keyword, page: 1, pageSize: 20 } })
    return response.data.data.items
  }

  const normalized = keyword.trim().toLowerCase()
  return structuredClone(mockKnowledge).filter((item) =>
    item.kind === kind && (!normalized || `${item.title}${item.summary}${item.category}`.toLowerCase().includes(normalized)),
  )
}

export async function getManualDownloadUrl(manualId: string): Promise<DownloadGrant> {
  const response = await http.get<ApiEnvelope<DownloadGrant>>(`/manuals/${manualId}/download-url`)
  return response.data.data
}

export async function getFaqDetail(faqId: string): Promise<FaqDetail> {
  if (apiMode === 'remote') {
    const response = await http.get<ApiEnvelope<FaqDetail>>(`/faq/${faqId}`)
    return response.data.data
  }
  const item = mockKnowledge.find((value) => value.kind === 'FAQ' && value.id === faqId)
  if (!item) throw new Error('FAQ 不存在')
  return {
    id: item.id,
    categoryId: item.category,
    categoryName: item.category,
    title: item.title,
    question: item.question || item.title,
    answer: item.summary,
    summary: item.summary,
    hotCount: item.hotCount || 0,
    updatedAt: item.updatedAt,
  }
}
