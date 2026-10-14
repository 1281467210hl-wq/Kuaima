import request from './request'

/* ==================== 项目本体 ==================== */

// 项目列表：keyword 匹配项目名/用工企业，status=active|archived，page 从 0 开始
export function listProjects({ keyword, status, enterpriseId, page = 0, size = 20 } = {}) {
  return request.get('/admin/projects', {
    params: { keyword, status, enterpriseId, page, size }
  })
}

// 项目统计（active / archived / total）
export function projectStats() {
  return request.get('/admin/projects/stats')
}

// 项目详情（含 stats / members / checkins / onsite / onboard 聚合，date 控制签到记录日期）
export function getProject(id, date) {
  return request.get(`/admin/projects/${id}`, { params: { date } })
}

// 创建项目（body 为 Project 实体字段：name/companyName/leaderName/leaderPhone/establishDate/location…）
export function createProject(payload) {
  return request.post('/admin/projects', payload)
}

// 更新项目（仅传实体支持字段）
export function updateProject(id, payload) {
  return request.put(`/admin/projects/${id}`, payload)
}

// 归档项目
export function archiveProject(id) {
  return request.post(`/admin/projects/${id}/archive`)
}

// 删除项目（软删除）
export function deleteProject(id) {
  return request.delete(`/admin/projects/${id}`)
}

/* ==================== 子资源：成员 ==================== */

// 成员列表（status 可选：active/temp/left）
export function listProjectMembers(id, status) {
  return request.get(`/admin/projects/${id}/members`, { params: { status } })
}

// 新增成员（body: name/phone/role/userId/status/employmentType/joinDate）
export function addProjectMember(id, payload) {
  return request.post(`/admin/projects/${id}/members`, payload)
}

// 编辑成员状态（status 支持中文「在职/临时/离职」或 active/temp/left；
// leaveDate 可选 yyyy-MM-dd，后端仅离职时写入，未传按当日；恢复在职不传即清空）
export function updateProjectMemberStatus(id, memberId, status, leaveDate) {
  const body = { status }
  if (leaveDate) body.leaveDate = leaveDate
  return request.put(`/admin/projects/${id}/members/${memberId}/status`, body)
}

// 移除成员
export function removeProjectMember(id, memberId) {
  return request.delete(`/admin/projects/${id}/members/${memberId}`)
}

/* ==================== 子资源：驻场 ==================== */

// 驻场人员列表
export function listProjectOnsite(id) {
  return request.get(`/admin/projects/${id}/onsite`)
}

// 新建驻场人员（body: name/phone/company/onsiteRole/position/startDate/endDate）
export function addProjectOnsite(id, payload) {
  return request.post(`/admin/projects/${id}/onsite`, payload)
}

// 移除驻场人员
export function removeProjectOnsite(id, staffId) {
  return request.delete(`/admin/projects/${id}/onsite/${staffId}`)
}

/* ==================== 子资源：考勤 / 入职 / 签到码 ==================== */

// 考勤打卡列表（返回 { records, stats }，date 缺省为今日）
export function getProjectAttendance(id, date) {
  return request.get(`/admin/projects/${id}/attendance`, { params: { date } })
}

// 入职申请列表（status 可选：pending/passed/rejected）
export function listProjectOnboard(id, status) {
  return request.get(`/admin/projects/${id}/onboard`, { params: { status } })
}

// 项目签到码（返回 { code, qrImage(dataURL), expireMinutes, expireTime … }；refresh=true 重新生成）
export function getProjectAttendanceCode(id, refresh = false) {
  return request.get(`/admin/projects/${id}/attendance-code`, { params: { refresh } })
}
