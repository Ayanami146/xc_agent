export type AdminRole = 'ADMIN' | 'SUPPORT'
export type Status = 'ACTIVE' | 'DISABLED' | 'LOCKED'
export interface Envelope<T> { requestId: string; data: T }
export interface Page<T> { items: T[]; total: number; page: number; pageSize: number }
export interface AdminSession { accessToken: string; expiresAt: string; adminId: string; displayName: string; role: AdminRole; permissions: string[] }
export interface Dashboard { metrics: Record<string, number>; ticketStatus: Array<{name:string;value:number}>; ticketTrend: Array<{day:string;value:number}> }
export interface Category { id:string;name:string;sortOrder:number;status:string;version:number;updatedAt:string }
export interface AdminItem { id:string;account:string;name:string;role:AdminRole;status:Status;failedCount:number;lockedUntil?:string;version:number;updatedAt:string }
export interface TicketSummary { id:string;title:string;category:string;customerName:string;status:string;assigneeId?:string;assigneeName:string;version:number;createdAt:string;updatedAt:string }
export interface TicketDetail extends TicketSummary { deviceBrand:string;deviceModel:string;description:string;contact:string;resolution?:string;replies:Array<{id:string;senderType:string;senderName:string;content:string;createdAt:string}>;attachments:Array<{id:string;fileName:string;contentType:string;fileSize:number;scanStatus:string}>;timeline:Array<{id:string;fromStatus?:string;toStatus:string;title:string;reason:string;createdAt:string}> }
export interface Faq { id:string;categoryId:string;title:string;question:string;answer:string;summary:string;keywords:string;status:string;top:boolean;hotCount:number;version:number;publishedAt?:string;updatedAt:string }
export interface Manual { id:string;categoryId:string;title:string;summary:string;fileName:string;contentType:string;fileSize:number;scanStatus:string;status:string;versionNo:number;version:number;publishedAt?:string;updatedAt:string }
export interface Audit { requestId:string;actorId:string;action:string;resourceType:string;resourceId:string;result:string;detailJson:string;ipAddress:string;createdAt:string }
