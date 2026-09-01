import type { ChatMessage, ChatSession } from '../types/chat'
import type { KnowledgeItem } from '../types/content'
import type { Ticket } from '../types/ticket'

export const mockSessions: ChatSession[] = [
  { id: 1, title: '统信 UOS 打印机驱动', preview: '驱动安装后仍无法识别设备', updatedAt: '今天 10:29' },
  { id: 2, title: '银河麒麟软件兼容', preview: '如何确认应用兼容版本？', updatedAt: '今天 10:22' },
  { id: 3, title: '保修政策咨询', preview: '整机保修期限如何计算？', updatedAt: '昨天 16:20' },
  { id: 4, title: '企业网络连接异常', preview: '系统升级后无法接入办公网络', updatedAt: '08-22 14:08' },
]

export const mockMessages: Record<number, ChatMessage[]> = {
  1: [
    { id: 1, role: 'assistant', status: 'COMPLETED', content: '您好，我是信创智能客服助手。请告诉我设备型号、系统版本和具体现象，我会结合维修手册为您排查。', createdAt: '10:28' },
    { id: 2, requestId: 1, role: 'user', status: 'COMPLETED', content: '统信 UOS 打印机驱动安装失败怎么办？', createdAt: '10:29' },
    {
      id: 3, requestId: 1, role: 'assistant', status: 'COMPLETED', createdAt: '10:29',
      content: '建议先确认打印机型号与系统架构，再检查驱动包是否与当前 UOS 版本一致。安装前可删除旧驱动并重新连接设备；如果设备管理器仍显示异常标识，请提交日志和设备型号以便进一步定位。',
      citations: [{ title: '统信 UOS 外设驱动安装手册', sourceId: 1, snippet: '安装驱动前应确认 CPU 架构、系统版本和设备硬件 ID。', sourceLocator: '/manuals/1', page: 12 }],
    },
  ],
  2: [
    { id: 4, role: 'assistant', status: 'COMPLETED', content: '您可以提供软件名称和银河麒麟版本，我会帮助确认兼容情况。', createdAt: '10:21' },
  ],
  3: [],
  4: [],
}

export const mockKnowledge: KnowledgeItem[] = [
  { id: 'faq_1', kind: 'FAQ', title: '驱动安装失败', question: '统信 UOS 驱动安装失败怎么办？', summary: '安装后设备管理器仍有异常标识', category: '驱动', updatedAt: '20:29', hotCount: 18 },
  { id: 'faq_2', kind: 'FAQ', title: '网络连接异常', question: '银河麒麟系统升级后无法连接网络怎么办？', summary: '系统升级后无法连接企业网络', category: '网络', updatedAt: '20:24' },
  { id: 'faq_3', kind: 'FAQ', title: '保修政策咨询', question: '国产电脑整机保修期限如何计算？', summary: '查询设备保修范围和服务期限', category: '保修', updatedAt: '20:23', hotCount: 9 },
  { id: 'faq_4', kind: 'FAQ', title: '系统升级卡顿', question: '系统升级进度长时间不动怎么办？', summary: '升级进度长时间停留在同一页面', category: '系统', updatedAt: '20:20' },
  { id: 'manual_1', kind: 'MANUAL', title: '统信 UOS 外设驱动安装手册', summary: '打印机、扫描仪等外设驱动安装与排障', category: '驱动手册', updatedAt: '08-20' },
  { id: 'manual_2', kind: 'MANUAL', title: '银河麒麟网络配置指南', summary: '有线、无线和企业认证网络配置说明', category: '网络手册', updatedAt: '08-18' },
  { id: 'manual_3', kind: 'MANUAL', title: '国产电脑售后服务手册', summary: '保修、送修和配件更换服务规范', category: '服务手册', updatedAt: '08-12' },
]

export const mockTickets: Ticket[] = [
  {
    id: 'WO202608240018', title: '统信 UOS 打印机驱动安装失败', category: '驱动问题', deviceBrand: '长城', deviceModel: '世恒 TD120A2',
    description: '安装官方驱动后打印机仍无法识别，设备管理器显示未知 USB 设备。', contact: '138****3800', status: 'PROCESSING', assignee: '王工',
    createdAt: '2026-08-24 09:18', updatedAt: '2026-08-24 10:42', attachments: [
      { id: 'att_demo_1', fileName: '设备管理器截图.png', size: 245760, contentType: 'image/png' },
      { id: 'att_demo_2', fileName: 'driver-install.log', size: 18240, contentType: 'text/plain' },
    ],
    replies: [{ id: 'reply_1', sender: 'admin', senderName: '王工', content: '您好，已收到问题。请确认打印机完整型号，并补充系统版本信息。', createdAt: '2026-08-24 10:42' }],
    timeline: [
      { id: 'tl_1', title: '工单已提交', description: '系统已生成留言工单', status: 'PENDING', createdAt: '2026-08-24 09:18' },
      { id: 'tl_2', title: '客服已受理', description: '王工正在处理您的问题', status: 'PROCESSING', createdAt: '2026-08-24 10:42' },
    ],
  },
  { id: 'WO202608230009', title: '银河麒麟无法连接企业 Wi-Fi', category: '网络问题', deviceBrand: '浪潮', deviceModel: '英政 CE520F', description: '更新系统后无法通过 802.1X 认证。', contact: '138****3800', status: 'WAITING_USER', assignee: '李工', createdAt: '2026-08-23 14:06', updatedAt: '2026-08-24 08:55', attachments: [], replies: [{ id: 'reply_2', sender: 'admin', senderName: '李工', content: '请补充网络认证失败截图和系统版本。', createdAt: '2026-08-24 08:55' }], timeline: [{ id: 'tl_3', title: '等待补充信息', description: '请上传认证失败截图', status: 'WAITING_USER', createdAt: '2026-08-24 08:55' }] },
  { id: 'WO202608210026', title: '整机保修期限确认', category: '保修咨询', deviceBrand: '联想开天', deviceModel: 'M90h G1t', description: '希望确认主机和显示器的保修期限。', contact: '138****3800', status: 'RESOLVED', assignee: '陈工', createdAt: '2026-08-21 11:30', updatedAt: '2026-08-22 16:20', attachments: [], replies: [{ id: 'reply_3', sender: 'admin', senderName: '陈工', content: '根据设备服务编码，主机保修至 2028 年 6 月，显示器保修至 2027 年 6 月。', createdAt: '2026-08-22 16:20' }], timeline: [{ id: 'tl_4', title: '问题已解决', description: '已回复保修信息', status: 'RESOLVED', createdAt: '2026-08-22 16:20' }] },
  { id: 'WO202608180013', title: '软件安装提示架构不兼容', category: '软件兼容', deviceBrand: '华为擎云', deviceModel: 'W585x', description: '安装 x86 版本软件时提示不兼容。', contact: '138****3800', status: 'CLOSED', assignee: '刘工', createdAt: '2026-08-18 09:10', updatedAt: '2026-08-19 13:11', attachments: [], replies: [], timeline: [{ id: 'tl_5', title: '工单已关闭', description: '已更换为 ARM64 安装包', status: 'CLOSED', createdAt: '2026-08-19 13:11' }] },
]
