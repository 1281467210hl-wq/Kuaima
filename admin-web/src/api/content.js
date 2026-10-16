import request from './request'

// ============ Banner ============
export function listBanners() { return request.get('/admin/banners') }
export function createBanner(data) { return request.post('/admin/banners', data) }
export function updateBanner(id, data) { return request.put(`/admin/banners/${id}`, data) }
export function deleteBanner(id) { return request.delete(`/admin/banners/${id}`) }

// ============ Notice ============
export function listNotices({ status, page = 0, size = 10 } = {}) {
  return request.get('/admin/notices', { params: { status, page, size } })
}
export function createNotice(data) { return request.post('/admin/notices', data) }
export function updateNotice(id, data) { return request.put(`/admin/notices/${id}`, data) }
export function deleteNotice(id) { return request.delete(`/admin/notices/${id}`) }

// 未读公告（登录后浮层）
export function listUnreadNotices() { return request.get('/admin/notices/unread') }
// 标记已读
export function markNoticeRead(id) { return request.post(`/admin/notices/${id}/read`) }

// ============ Rules ============
export function listRules({ page = 0, size = 10 } = {}) {
  return request.get('/admin/rules', { params: { page, size } })
}
export function createRules(data) { return request.post('/admin/rules', data) }
export function updateRules(id, data) { return request.put(`/admin/rules/${id}`, data) }
export function deleteRules(id) { return request.delete(`/admin/rules/${id}`) }

// ============ Agreements ============
export function listAgreements({ type, keyword, page = 0, size = 10 } = {}) {
  return request.get('/admin/agreements', { params: { type, keyword, page, size } })
}
export function getAgreement(id) { return request.get(`/admin/agreements/${id}`) }
export function createAgreement(data) { return request.post('/admin/agreements', data) }
export function updateAgreement(id, data) { return request.put(`/admin/agreements/${id}`, data) }
export function toggleAgreement(id) { return request.put(`/admin/agreements/${id}/toggle`) }
export function getAgreementDownloadUrl(id) { return request.get(`/admin/agreements/${id}/download-url`) }
export function uploadAgreementFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/admin/agreements/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ============ Certification ============
export function listCertifications({ type, status, page = 0, size = 10 } = {}) {
  return request.get('/admin/certifications', { params: { type, status, page, size } })
}
export function auditCertPass(id) { return request.put(`/admin/certifications/${id}/pass`) }
export function auditCertReject(id, reason) {
  return request.put(`/admin/certifications/${id}/reject`, null, { params: { reason } })
}

// ============ Message Template ============
export function listMessageTemplates({ status, event, page = 0, size = 10 } = {}) {
  return request.get('/admin/message-templates', { params: { status, event, page, size } })
}
export function getMessageTemplate(id) { return request.get(`/admin/message-templates/${id}`) }
export function createMessageTemplate(data) { return request.post('/admin/message-templates', data) }
export function updateMessageTemplate(id, data) { return request.put(`/admin/message-templates/${id}`, data) }
export function toggleMessageTemplate(id) { return request.put(`/admin/message-templates/${id}/toggle`) }
export function deleteMessageTemplate(id) { return request.delete(`/admin/message-templates/${id}`) }
