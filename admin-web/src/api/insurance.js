import request from './request'

export function listInsurances({ keyword, status, page = 0, size = 10 } = {}) {
  return request.get('/admin/insurances', { params: { keyword, status, page, size } })
}

export function getInsurance(id) {
  return request.get(`/admin/insurances/${id}`)
}

export function getInsuranceNoticeUrl(id) {
  return request.get(`/admin/insurances/${id}/notice-url`)
}

export function createInsurance(data) {
  return request.post('/admin/insurances', data)
}

export function updateInsurance(id, data) {
  return request.put(`/admin/insurances/${id}`, data)
}

export function toggleInsurance(id) {
  return request.put(`/admin/insurances/${id}/toggle`)
}

export function uploadInsuranceNotice(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/admin/insurances/upload-notice', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
