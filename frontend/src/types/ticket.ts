export type TicketStatus = 'PENDING' | 'PROCESSING' | 'WAITING_USER' | 'RESOLVED' | 'CLOSED'

export interface TicketReply {
  id: string
  sender: 'user' | 'admin'
  senderName: string
  content: string
  createdAt: string
}

export interface TicketTimelineItem {
  id: string
  title: string
  description: string
  status: TicketStatus
  createdAt: string
}

export interface TicketAttachment {
  id: string
  fileName: string
  size: number
  contentType: string
}

export interface Ticket {
  id: string
  title: string
  category: string
  deviceBrand: string
  deviceModel: string
  description: string
  contact: string
  status: TicketStatus
  createdAt: string
  updatedAt: string
  assignee?: string
  attachments: TicketAttachment[]
  replies: TicketReply[]
  timeline: TicketTimelineItem[]
}

export interface TicketDraft {
  title: string
  category: string
  deviceBrand: string
  deviceModel: string
  description: string
  contact: string
}
