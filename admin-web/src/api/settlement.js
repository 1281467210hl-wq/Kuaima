import request from './request'

export function listSettlements({
  status,
  cycle,
  startDate,
  endDate,
  page = 0,
  size = 10
} = {}) {
  return request.get('/admin/settlements', { params: { status, cycle, startDate, endDate, page, size } })
}

export function getSettlementStats() {
  return request.get('/admin/settlements/stats')
}

export function getSettlementDetail(id) {
  return request.get(`/admin/settlements/${id}`)
}

export function settlePay(id) {
  return request.post(`/admin/settlements/${id}/pay`)
}

export function batchSettlePay(ids) {
  return request.post('/admin/settlements/batch-pay', { ids })
}

/** 站内消息提醒老板支付待结算工资 */
export function remindSettlement(id) {
  return request.post(`/admin/settlements/${id}/remind`)
}
