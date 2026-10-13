import request from './request'

// ============ Settings ============
export function listSettings() { return request.get('/admin/settings') }
export function getSettingsByCategory(cat) { return request.get(`/admin/settings/category/${cat}`) }
export function saveSetting(key, value, description) {
  return request.put(`/admin/settings/${key}`, { settingValue: value, description })
}
// 银行账户设置
export function getBankAccount() { return request.get('/admin/settings/bank-account') }
export function saveBankAccount(data) {
  return request.put('/admin/settings/bank-account', JSON.stringify(data), {
    headers: { 'Content-Type': 'application/json' }
  })
}
// 钱包账户设置
export function getWalletAccount() { return request.get('/admin/settings/wallet-account') }
export function saveWalletAccount(data) {
  return request.put('/admin/settings/wallet-account', JSON.stringify(data), {
    headers: { 'Content-Type': 'application/json' }
  })
}
// 模板测试发送
export function testSendTemplate(data) {
  return request.post('/admin/settings/test-send', JSON.stringify(data), {
    headers: { 'Content-Type': 'application/json' }
  })
}
// 文件上传
export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/admin/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ============ Admin Users ============
export function listAdminUsers({ page = 0, size = 10 } = {}) {
  return request.get('/admin/admin-users', { params: { page, size } })
}
export function getAdminUser(id) { return request.get(`/admin/admin-users/${id}`) }
export function createAdminUser(data) { return request.post('/admin/admin-users', data) }
export function updateAdminUser(id, data) { return request.put(`/admin/admin-users/${id}`, data) }
export function resetAdminPassword(id, newPassword = '123456') {
  return request.put(`/admin/admin-users/${id}/reset-password`, { newPassword })
}
export function deleteAdminUser(id) { return request.delete(`/admin/admin-users/${id}`) }

// ============ Logs ============
export function listLogs({ type, keyword, startDate, endDate, page = 0, size = 20 } = {}) {
  return request.get('/admin/logs', { params: { type, keyword, startDate, endDate, page, size } })
}
