/**
 * 聊天 API
 */
import request from '@/utils/request'

export interface ChatMsg {
  id?: number
  helpId?: number
  orderId?: number
  senderId: number
  receiverId: number
  content: string
  isRead?: number
  createTime?: string
}

/** 获取订单聊天记录 */
export function getChatMessages(orderId: number): Promise<{ data: ChatMsg[] }> {
  return request.get(`/chat/order/${orderId}`)
}

/** 获取求助私信记录 */
export function getHelpChatMessages(helpId: number): Promise<{ data: ChatMsg[] }> {
  return request.get(`/chat/help/${helpId}`)
}

/** 发送消息（订单聊天） */
export function sendChatMessage(orderId: number, content: string): Promise<{ data: ChatMsg }> {
  return request.post('/chat/send', { orderId, content })
}

/** 发送私信（求助私信） */
export function sendHelpMessage(helpId: number, content: string): Promise<{ data: ChatMsg }> {
  return request.post('/chat/send', { helpId, content })
}

/** 获取未读私信数（后台轮询，静默，不弹错误） */
export function getChatUnread(): Promise<{ data: number }> {
  return request.get('/chat/unread', { silent: true } as any)
}

/** 获取私信会话列表 */
export function getConversations(): Promise<{ data: any[] }> {
  return request.get('/chat/conversations')
}

/** 获取求助聊天头部信息 */
export function getHelpChatInfo(helpId: number): Promise<{ data: any }> {
  return request.get(`/chat/help/${helpId}/info`)
}
