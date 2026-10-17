/**
 * 客服聊天相关 API（用户端）
 */
import { request } from './http'

// 发起会话
export function startSession(userId) {
  return request({ url: '/chat/sessions', method: 'POST', data: { userId } })
}

// 获取会话消息
export function getMessages(sessionId, page = 0, size = 50) {
  return request({ url: `/chat/sessions/${sessionId}/messages`, method: 'GET', data: { page, size } })
}

// 发送消息（HTTP 降级）
export function sendMessage(sessionId, fromId, content) {
  return request({
    url: `/chat/sessions/${sessionId}/messages`,
    method: 'POST',
    data: { fromId, content }
  })
}

// 关闭会话
export function closeSession(sessionId) {
  return request({ url: `/chat/sessions/${sessionId}/close`, method: 'PUT' })
}

// 获取 FAQ 列表
export function getFaqs(category) {
  return request({
    url: '/service/faqs',
    method: 'GET',
    // 未传分类时不能携带 undefined，避免小程序序列化为 category=undefined。
    data: category ? { category } : undefined,
  })
}
