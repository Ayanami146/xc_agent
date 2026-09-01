import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as ticketService from '../services/tickets'
import type { Ticket, TicketDraft, TicketStatus } from '../types/ticket'

export const useTicketStore = defineStore('tickets', () => {
  const tickets = ref<Ticket[]>([])
  const current = ref<Ticket | null>(null)
  const loading = ref(false)
  const statusFilter = ref<TicketStatus | 'ALL'>('ALL')
  const keyword = ref('')

  const counts = computed(() => {
    const base: Record<TicketStatus, number> = { PENDING: 0, PROCESSING: 0, WAITING_USER: 0, RESOLVED: 0, CLOSED: 0 }
    tickets.value.forEach((ticket) => base[ticket.status]++)
    return base
  })

  const filteredTickets = computed(() => {
    const normalized = keyword.value.trim().toLowerCase()
    return tickets.value.filter((ticket) =>
      (statusFilter.value === 'ALL' || ticket.status === statusFilter.value)
      && (!normalized || `${ticket.id}${ticket.title}${ticket.deviceBrand}${ticket.deviceModel}`.toLowerCase().includes(normalized)),
    )
  })

  async function load() {
    loading.value = true
    try { tickets.value = await ticketService.listTickets() } finally { loading.value = false }
  }

  async function loadDetail(id: string) {
    loading.value = true
    try { current.value = await ticketService.getTicket(id) } finally { loading.value = false }
  }

  async function create(draft: TicketDraft, files: File[]) {
    const ticket = await ticketService.createTicket(draft, files)
    tickets.value.unshift(ticket)
    return ticket
  }

  async function reply(content: string) {
    if (!current.value) return
    const reply = await ticketService.replyTicket(current.value.id, content)
    current.value.replies.push(reply)
    current.value.updatedAt = reply.createdAt
    if (current.value.status === 'WAITING_USER') current.value.status = 'PROCESSING'
  }

  async function close() {
    if (!current.value) return
    await ticketService.closeTicket(current.value.id)
    current.value.status = 'CLOSED'
  }

  async function reopen(reason: string) {
    if (!current.value || current.value.status !== 'RESOLVED') return
    await ticketService.reopenTicket(current.value.id, reason)
    current.value.status = 'PROCESSING'
  }

  async function downloadAttachment(attachmentId: string) {
    if (!current.value) return
    const url = await ticketService.getAttachmentDownloadUrl(current.value.id, attachmentId)
    if (url) window.open(url, '_blank', 'noopener,noreferrer')
  }

  return { tickets, current, loading, statusFilter, keyword, counts, filteredTickets, load, loadDetail, create, reply, close, reopen, downloadAttachment }
})
