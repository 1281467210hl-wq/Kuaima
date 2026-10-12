import request from './request'

export function listPointPurchaseOrders(params = {}) {
  return request.get('/admin/point-purchase/orders', { params })
}

export function getPointPurchaseStats(params = {}) {
  return request.get('/admin/point-purchase/stats', { params })
}

export function createPointPurchaseOrder(data) {
  return request.post('/admin/point-purchase/orders', data)
}

export function confirmPointPurchasePayment(orderNo, data = {}) {
  return request.post(`/admin/point-purchase/orders/${encodeURIComponent(orderNo)}/pay`, data)
}

export function getPointPurchaseOrder(orderNo) {
  return request.get(`/admin/point-purchase/orders/${encodeURIComponent(orderNo)}`)
}

export function createPointPurchasePayUrl(orderNo, payMethod = '微信支付') {
  return request.get(`/admin/point-purchase/orders/${encodeURIComponent(orderNo)}/pay-url`, { params: { payMethod } })
}

export function exportPointPurchaseOrders(params = {}) {
  return request.get('/admin/point-purchase/orders/export', { params, responseType: 'blob' })
}

export function getPointSettingsStats() {
  return request.get('/admin/point-purchase/settings/stats')
}

export function listPointPackages() {
  return request.get('/admin/point-purchase/settings/packages')
}

export function savePointPackage(data) {
  return request.post('/admin/point-purchase/settings/packages', data)
}

export function togglePointPackage(id) {
  return request.put(`/admin/point-purchase/settings/packages/${id}/toggle`)
}

export function listPointExchangeRules() {
  return request.get('/admin/point-purchase/settings/exchange-rules')
}

export function savePointExchangeRule(data) {
  return request.post('/admin/point-purchase/settings/exchange-rules', data)
}

export function togglePointExchangeRule(id) {
  return request.put(`/admin/point-purchase/settings/exchange-rules/${id}/toggle`)
}

export function listPointEarnRules() {
  return request.get('/admin/point-purchase/settings/earn-rules')
}

export function savePointEarnRule(data) {
  return request.post('/admin/point-purchase/settings/earn-rules', data)
}

export function togglePointEarnRule(id) {
  return request.put(`/admin/point-purchase/settings/earn-rules/${id}/toggle`)
}
