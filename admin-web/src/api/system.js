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
export function resetAdminPassword(id, newPassword) {
  return request.put(`/admin/admin-users/${id}/reset-password`, { newPassword })
}
/** 当前登录管理员修改自己的密码（后端校验原密码） */
export function changeOwnPassword(oldPassword, newPassword) {
  return request.put('/admin/admin-users/me/password', { oldPassword, newPassword })
}
/** 与后端 AdminUserCrudController.passwordProblem 一致：8-50 位，同时包含字母和数字 */
export function adminPasswordProblem(password) {
  const value = String(password || '')
  if (value.length < 8) return '密码至少8位'
  if (value.length > 50) return '密码不能超过50位'
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) return '密码必须同时包含字母和数字'
  return ''
}
export function deleteAdminUser(id) { return request.delete(`/admin/admin-users/${id}`) }

// ============ Logs ============
export function listLogs({ type, keyword, startDate, endDate, page = 0, size = 20 } = {}) {
  return request.get('/admin/logs', { params: { type, keyword, startDate, endDate, page, size } })
}
