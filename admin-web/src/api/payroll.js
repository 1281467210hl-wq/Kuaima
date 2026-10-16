import request from './request'

// 发薪单列表：tab=submitted(已提交) / reviewed(已审核)
export function listPayrolls({ tab = 'submitted', status, projectId, keyword, page = 0, size = 10 } = {}) {
  return request.get('/admin/payrolls', {
    params: { tab, status, projectId, keyword, page, size }
  })
}

// 发薪管理统计
export function payrollStats() {
  return request.get('/admin/payrolls/stats')
}

// 发薪单详情（含人员明细）
export function getPayroll(id) {
  return request.get(`/admin/payrolls/${id}`)
}

// 新建发薪单（无明细 → 待提交审核草稿；带明细 → 直接待审批）
export function createPayroll(payload) {
  return request.post('/admin/payrolls', payload)
}

// 草稿-添加/替换发薪人员：PUT /admin/payrolls/{id}/members
// body { members: [{ userId, name, phone, job, dailyWage?, attendDays? }] }
export function updatePayrollMembers(id, payload) {
  return request.put(`/admin/payrolls/${id}/members`, payload)
}

// 草稿-设置薪资：PUT /admin/payrolls/{id}/salary
// body { salaries: [{ userId, phone, dailyWage(分), attendDays }] }
export function updatePayrollSalary(id, payload) {
  return request.put(`/admin/payrolls/${id}/salary`, payload)
}

// 提交发薪单（草稿→待审批）
export function submitPayroll(id) {
  return request.post(`/admin/payrolls/${id}/submit`)
}

// 审批通过
export function approvePayroll(id) {
  return request.post(`/admin/payrolls/${id}/approve`)
}

// 审批驳回
export function rejectPayroll(id) {
  return request.post(`/admin/payrolls/${id}/reject`)
}

// 撤回发薪单
export function withdrawPayroll(id) {
  return request.post(`/admin/payrolls/${id}/withdraw`)
}
