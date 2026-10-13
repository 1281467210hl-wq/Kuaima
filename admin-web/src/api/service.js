/**
 * 客服管理相关 API
 */
import request from './request'

// 统计
export function getServiceStats() { return request.get('/admin/service/stats') }

// 会话管理
export function getServiceSessions(params) { return request.get('/admin/service/sessions', { params }) }
export function getServiceSession(id) { return request.get(`/admin/service/sessions/${id}`) }
export function getSessionMessages(id, params) { return request.get(`/admin/service/sessions/${id}/messages`, { params }) }
export function sendAgentMessage(id, data) { return request.post(`/admin/service/sessions/${id}/messages`, data) }
export function closeServiceSession(id) { return request.put(`/admin/service/sessions/${id}/close`) }
export function claimServiceSession(id) { return request.put(`/admin/service/sessions/${id}/claim`) }
export function updateServiceSessionType(id, type) { return request.put(`/admin/service/sessions/${id}/type`, { type }) }

// 快捷回复
export function getQuickReplies() { return request.get('/admin/service/quick-replies') }
export function createQuickReply(data) { return request.post('/admin/service/quick-replies', data) }
export function updateQuickReply(id, data) { return request.put(`/admin/service/quick-replies/${id}`, data) }
export function deleteQuickReply(id) { return request.delete(`/admin/service/quick-replies/${id}`) }

// FAQ 管理
export function getFaqs() { return request.get('/admin/service/faqs') }
export function createFaq(data) { return request.post('/admin/service/faqs', data) }
export function updateFaq(id, data) { return request.put(`/admin/service/faqs/${id}`, data) }
export function deleteFaq(id) { return request.delete(`/admin/service/faqs/${id}`) }

// 用户端会话
export function startChatSession(userId) { return request.post('/chat/sessions', { userId }) }
export function getUserSessions(userId) { return request.get('/chat/sessions', { params: { userId } }) }
export function sendUserMessage(sessionId, data) { return request.post(`/chat/sessions/${sessionId}/messages`, data) }
