import { mockTickets } from '../mocks/data'
import type { Ticket, TicketAttachment, TicketDraft, TicketReply, TicketStatus } from '../types/ticket'
import type { ApiEnvelope, PageData } from '../types/problem'
import { apiMode, http, newIdempotencyKey } from './http'

let tickets = structuredClone(mockTickets)
const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

export interface TicketFilters { keyword?: string; status?: TicketStatus | 'ALL' }

export async function listTickets(filters: TicketFilters = {}): Promise<Ticket[]> {
  if (apiMode === 'remote') {
    const response = await http.get<ApiEnvelope<PageData<Ticket>>>('/tickets', { params: { ...filters, page: 1, pageSize: 100 } })
    return response.data.data.items
  }
  await wait(180)
  const keyword = filters.keyword?.trim().toLowerCase() ?? ''
  return structuredClone(tickets.filter((ticket) =>
    (!filters.status || filters.status === 'ALL' || ticket.status === filters.status)
    && (!keyword || `${ticket.id}${ticket.title}${ticket.deviceBrand}${ticket.deviceModel}`.toLowerCase().includes(keyword)),
  ))
}

export async function getTicket(id: string): Promise<Ticket> {
  if (apiMode === 'remote') {
    const response = await http.get<ApiEnvelope<Ticket>>(`/tickets/${id}`)
    return response.data.data
  }
  await wait(140)
  const ticket = tickets.find((item) => item.id === id)
  if (!ticket) throw new Error('TICKET_NOT_FOUND')
  return structuredClone(ticket)
}

async function uploadAttachment(file: File): Promise<TicketAttachment> {
  const form = new FormData()
  form.append('file', file, file.name)
  const response = await http.post<ApiEnvelope<TicketAttachment>>('/ticket-attachments', form, {
    headers: { 'Idempotency-Key': newIdempotencyKey() },
    timeout: 60_000,
  })
  return response.data.data
}

export async function createTicket(draft: TicketDraft, files: File[] = []): Promise<Ticket> {
  if (apiMode === 'remote') {
    const attachments = await Promise.all(files.map(uploadAttachment))
    const response = await http.post<ApiEnvelope<Ticket>>('/tickets', { ...draft, attachmentIds: attachments.map((item) => item.id) }, { headers: { 'Idempotency-Key': newIdempotencyKey() } })
    return response.data.data
  }
  await wait(520)
  const now = new Date().toLocaleString('zh-CN', { hour12: false })
  const ticket: Ticket = {
    ...draft, attachments: files.map((file, index) => ({ id: `att_${Date.now()}_${index}`, fileName: file.name, size: file.size, contentType: file.type || 'application/octet-stream' })),
    id: `WO${new Date().toISOString().slice(0, 10).replaceAll('-', '')}${String(tickets.length + 31).padStart(4, '0')}`,
    status: 'PENDING', createdAt: now, updatedAt: now, replies: [],
    timeline: [{ id: `tl_${Date.now()}`, title: '工单已提交', description: '系统已生成留言工单，等待客服受理', status: 'PENDING', createdAt: now }],
  }
  tickets = [ticket, ...tickets]
  return structuredClone(ticket)
}

export async function replyTicket(id: string, content: string): Promise<TicketReply> {
  if (apiMode === 'remote') {
    const response = await http.post<ApiEnvelope<TicketReply>>(`/tickets/${id}/replies`, { content }, { headers: { 'Idempotency-Key': newIdempotencyKey() } })
    return response.data.data
  }
  await wait(280)
  const ticket = tickets.find((item) => item.id === id)
  if (!ticket) throw new Error('TICKET_NOT_FOUND')
  const reply: TicketReply = { id: `reply_${Date.now()}`, sender: 'user', senderName: '我', content, createdAt: new Date().toLocaleString('zh-CN', { hour12: false }) }
  ticket.replies.push(reply)
  ticket.updatedAt = reply.createdAt
  if (ticket.status === 'WAITING_USER') ticket.status = 'PROCESSING'
  return structuredClone(reply)
}

export async function closeTicket(id: string) {
  if (apiMode === 'remote') await http.post(`/tickets/${id}/close`, {}, { headers: { 'Idempotency-Key': newIdempotencyKey() } })
  const ticket = tickets.find((item) => item.id === id)
  if (ticket) { ticket.status = 'CLOSED'; ticket.updatedAt = new Date().toLocaleString('zh-CN', { hour12: false }) }
}

export async function reopenTicket(id: string, reason: string) {
  if (apiMode === 'remote') await http.post(`/tickets/${id}/reopen`, { reason }, { headers: { 'Idempotency-Key': newIdempotencyKey() } })
  const ticket = tickets.find((item) => item.id === id)
  if (ticket?.status === 'RESOLVED') { ticket.status = 'PROCESSING'; ticket.updatedAt = new Date().toLocaleString('zh-CN', { hour12: false }) }
}

export async function getAttachmentDownloadUrl(ticketId: string, attachmentId: string) {
  if (apiMode === 'remote') {
    const response = await http.get<ApiEnvelope<{ url: string; expiresAt: string }>>(`/tickets/${ticketId}/attachments/${attachmentId}/download-url`)
    return response.data.data.url
  }
  return ''
}
