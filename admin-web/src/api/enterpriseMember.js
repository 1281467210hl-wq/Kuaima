import request from './request'

/* =====================================================================
   企业成员（admin-web EnterpriseMember.vue）接口层。
   后端契约：
     - AdminEnterpriseMemberController  /admin/enterprises/{enterpriseId}/members
     - AdminEnterpriseController        /admin/enterprises、/admin/enterprises/{id}/invite-qr
     - EmployeeRoleController           /admin/employee-roles
     - JoinApplyController              /admin/join-applies/{id}/approve|reject
                                        （入企申请 enterprise_join_apply 的审批写回入口）
   响应拦截器返回完整 Result body：{ code, data, page, total }；列表函数在这里统一
   整理成 { rows, total }，并完成「后端字段 ⇄ 本页 DEMO 字段」的适配。
   ===================================================================== */

const memberBase = (enterpriseId) => `/admin/enterprises/${enterpriseId}/members`

/* ==================== 权限码归一化 ==================== */

/** 前端模块 key / 中文名 / 后端历史中文权限 → 后端统一大写 code。 */
const TOKEN_TO_CODE = {
  resume_pool: 'RESUME_VIEW',
  简历库: 'RESUME_VIEW',
  job_manage: 'POSITION_PUBLISH',
  岗位管理: 'POSITION_PUBLISH',
  岗位发布: 'POSITION_PUBLISH',
  岗位发布与编辑: 'POSITION_PUBLISH',
  live_showcase: 'LIVE_SHOWCASE',
  直播橱窗: 'LIVE_SHOWCASE',
  candidate: 'CANDIDATE_MANAGE',
  候选人: 'CANDIDATE_MANAGE',
  batch_payroll: 'PAYROLL_CREATE',
  批量发薪: 'PAYROLL_CREATE',
  制单发薪: 'PAYROLL_CREATE',
  payroll_approve: 'PAYROLL_APPROVE',
  待我审批: 'PAYROLL_APPROVE',
  发薪审批: 'PAYROLL_APPROVE',
  transfer_record: 'TRANSFER_VIEW',
  转账记录: 'TRANSFER_VIEW',
  balance_view: 'BALANCE_VIEW',
  余额查询: 'BALANCE_VIEW',
  member_list: 'MEMBER_MANAGE',
  企业成员: 'MEMBER_MANAGE',
  员工列表: 'MEMBER_MANAGE',
  成员管理: 'MEMBER_MANAGE',
  成员管理与邀请: 'MEMBER_MANAGE',
  org_member: 'MEMBER_MANAGE',
  attend_check: 'ATTENDANCE_STATS',
  考勤打卡: 'ATTENDANCE_STATS',
  考勤统计: 'ATTENDANCE_STATS',
  项目管理: 'PROJECT_MANAGE',
  project_manage: 'PROJECT_MANAGE',
  org_project: 'PROJECT_MANAGE',
}

/** 后端 code → 本页角色权限树（ROLE_PERMISSIONS）的 key。 */
const CODE_TO_PERM_KEY = {
  RESUME_VIEW: 'resume_pool',
  POSITION_PUBLISH: 'job_manage',
  LIVE_SHOWCASE: 'live_showcase',
  CANDIDATE_MANAGE: 'candidate',
  PAYROLL_CREATE: 'batch_payroll',
  PAYROLL_APPROVE: 'payroll_approve',
  TRANSFER_VIEW: 'transfer_record',
  BALANCE_VIEW: 'balance_view',
  MEMBER_MANAGE: 'member_list',
  ATTENDANCE_STATS: 'attend_check',
}

/** 后端 code → 工作台开关树（WB_PERM_TREE）的中文名（一个 code 可能对应两个开关项）。 */
const CODE_TO_WB_NAMES = {
  RESUME_VIEW: ['简历库'],
  POSITION_PUBLISH: ['岗位管理'],
  LIVE_SHOWCASE: ['直播橱窗'],
  CANDIDATE_MANAGE: ['候选人'],
  PAYROLL_CREATE: ['批量发薪'],
  PAYROLL_APPROVE: ['待我审批'],
  TRANSFER_VIEW: ['转账记录'],
  BALANCE_VIEW: ['余额查询'],
  MEMBER_MANAGE: ['企业成员', '员工列表'],
  ATTENDANCE_STATS: ['考勤打卡'],
  PROJECT_MANAGE: ['项目管理'],
}

/** 任意来源的权限项（key / 中文名 / 大写 code）→ 后端统一 code 列表。 */
export function permTokensToCodes(tokens) {
  const list = Array.isArray(tokens) ? tokens : [tokens]
  const codes = []
  list.forEach((raw) => {
    if (raw == null) return
    const value = String(raw).trim()
    if (!value) return
    const code = TOKEN_TO_CODE[value] || TOKEN_TO_CODE[value.toLowerCase?.() || value]
      || (/^[A-Z0-9_]{2,60}$/.test(value) ? value : '')
    if (code && !codes.includes(code)) codes.push(code)
  })
  return codes
}

/** code 列表 → 本页角色权限 key 列表（无对应 UI 项的 code 丢弃）。 */
export function permCodesToKeys(codes) {
  return permTokensToCodes(codes)
    .map((code) => CODE_TO_PERM_KEY[code] || '')
    .filter(Boolean)
}

/** code 列表 → 工作台开关树的中文名列表。 */
export function permCodesToWbNames(codes) {
  const names = []
  permTokensToCodes(codes).forEach((code) => {
    ;(CODE_TO_WB_NAMES[code] || []).forEach((n) => {
      if (!names.includes(n)) names.push(n)
    })
  })
  return names
}

/* ==================== 通用适配 ==================== */

const dash = (value, fallback = '—') => (value == null || value === '' ? fallback : value)

const toList = (res) => {
  const d = res && res.data
  if (Array.isArray(d)) return d
  if (Array.isArray(d && d.content)) return d.content
  if (Array.isArray(d && d.list)) return d.list
  return []
}

const normalizeProject = (p) => ({
  id: p.projectId != null ? p.projectId : p.id,
  name: dash(p.name),
  role: dash(p.role),
  joined: dash(p.joined),
  status: dash(p.status, '进行中'),
  statusCode: p.statusCode || '',
})

/** 成员行：后端 rowView 已按本页字段命名，这里仅补空态与权限派生字段。 */
export function normalizeMember(row) {
  const codes = permTokensToCodes(row.permissions || [])
  return {
    ...row,
    memberId: row.memberId != null ? row.memberId : row.id,
    name: dash(row.name),
    phone: dash(row.phone),
    // 性别(male/female) / 身份证号(后端已脱敏) / 年龄(后端按身份证计算或存值) 原样透传，
    // 缺失时置空串/null，展示层按 '—' 兜底；编辑弹窗据此回填。
    gender: row.gender || '',
    idCard: row.idCard || '',
    age: row.age != null ? row.age : null,
    dept: dash(row.dept),
    position: dash(row.position),
    avatar: row.avatar || 'orange',
    role: row.role || 'staff',
    roleName: dash(row.roleName, '普通成员'),
    joinTime: dash(row.joinTime),
    status: row.status === 'resigned' ? 'resigned' : 'active',
    statusName: dash(row.statusName, '在职'),
    proj: dash(row.proj, '全部项目'),
    permissions: codes,
    wbPermNames: permCodesToWbNames(codes),
    permKeys: permCodesToKeys(codes),
    wbPerms: Array.isArray(row.wbPerms) ? row.wbPerms : [],
    projects: (Array.isArray(row.projects) ? row.projects : []).map(normalizeProject),
  }
}

/** 入企申请行：enterprise_join_apply 无「意向项目」列，统一空态显示「—」。 */
const normalizeApply = (a) => ({
  ...a,
  name: dash(a.name),
  phone: dash(a.phone),
  avatar: a.avatar || 'orange',
  job: dash(a.job || a.roleName),
  roleName: dash(a.roleName, '员工'),
  proj: dash(a.proj),
  time: dash(a.time),
  status: a.status || 'pending',
  statusName: dash(a.statusName),
})

/* ==================== 企业 / 邀请二维码 ==================== */

// 所属公司筛选来源（admin 是平台视角，必须先选企业再取成员）
export function listEnterprises() {
  return request.get('/admin/enterprises')
}

// 邀请二维码：{ inviteCode, link, enterpriseId, enterpriseName, qrImage(base64 data URL) }
export function getInviteQr(enterpriseId) {
  return request.get(`/admin/enterprises/${enterpriseId}/invite-qr`)
}

/* ==================== 成员 ==================== */

// 成员列表（后端 page 从 0 开始；本页表格沿用前端切片分页，一次拉取窗口数据）
export async function listMembers({
  enterpriseId, keyword, status = 'all', role = 'all', company, page = 0, size = 200,
} = {}) {
  const res = await request.get(memberBase(enterpriseId), {
    params: { keyword, status, role, company, page, size },
  })
  const rows = toList(res).map(normalizeMember)
  return { rows, total: res && typeof res.total === 'number' ? res.total : rows.length }
}

// 成员详情（含邀请人、参与项目、工作台权限明细）
export async function getMember(enterpriseId, memberId) {
  const res = await request.get(`${memberBase(enterpriseId)}/${memberId}`)
  return { data: res && res.data ? normalizeMember(res.data) : null }
}

// 成员参与项目
export function listMemberProjects(enterpriseId, memberId) {
  return request.get(`${memberBase(enterpriseId)}/${memberId}/projects`)
}

// 编辑角色：role = super/admin/staff（后端归一化为 OWNER/ADMIN/STAFF），title = 职位，
// roleId 传 null/0 可解除与 employee_role 的绑定。
// gender = male/female，idCard = 完整 18 位身份证（后端据此计算年龄），age = 手动覆盖值；
// 三者在有值时并入同一 PUT body（idCard 仅传完整值，脱敏串由调用方过滤后为 undefined）。
export function updateMemberRole(
  enterpriseId,
  memberId,
  { role, roleId = undefined, title, gender = undefined, idCard = undefined, age = undefined } = {},
) {
  const body = {}
  if (role != null) body.role = role
  if (roleId !== undefined) body.roleId = roleId
  if (title != null) body.title = title
  if (gender != null && gender !== '') body.gender = gender
  if (idCard != null && idCard !== '') body.idCard = idCard
  if (age != null && age !== '') body.age = age
  return request.put(`${memberBase(enterpriseId)}/${memberId}/role`, body)
}

// 编辑工作台权限（permissions 支持 code / 中文名 / 前端 key，后端统一归一化）
export function updateMemberPermissions(enterpriseId, memberId, permissions) {
  return request.put(`${memberBase(enterpriseId)}/${memberId}/permissions`, {
    permissions: permTokensToCodes(permissions),
  })
}

// 批量改权限
export function batchUpdateMemberPermissions(enterpriseId, memberIds, permissions) {
  return request.put(`${memberBase(enterpriseId)}/permissions/batch`, {
    memberIds,
    permissions: permTokensToCodes(permissions),
  })
}

// 企业工作台访问开关（本页暂无对应 UI）
export function updateMemberPortal(enterpriseId, memberId, enabled) {
  return request.put(`${memberBase(enterpriseId)}/${memberId}/portal`, { enabled })
}

/** 在职状态归一化：后端 PUT status 仅认 active/left（在职/离职亦可），
 *  本页行 status 为 active/resigned（编辑弹窗历史值 inactive 一并兼容）。 */
export function normalizeMemberStatus(status) {
  const value = String(status ?? '').trim()
  if (value === '在职' || value.toLowerCase() === 'active') return 'active'
  if (value === '离职' || ['left', 'resigned', 'inactive'].includes(value.toLowerCase())) return 'left'
  return 'active'
}

// 成员在职状态（OWNER 置离职会被后端拒绝并弹错，调用方 catch 保留原状即可）
export function updateMemberStatus(enterpriseId, memberId, status) {
  return request.put(`${memberBase(enterpriseId)}/${memberId}/status`, {
    status: normalizeMemberStatus(status),
  })
}

// 移除成员（软删 status=LEFT；超级管理员不可移除）
export function removeMember(enterpriseId, memberId) {
  return request.delete(`${memberBase(enterpriseId)}/${memberId}`)
}

/* ==================== 入企申请 ==================== */

export async function listApplies({
  enterpriseId, status = 'all', page = 0, size = 200,
} = {}) {
  const res = await request.get(`${memberBase(enterpriseId)}/applies`, {
    params: { status, page, size },
  })
  const rows = toList(res).map(normalizeApply)
  return { rows, total: res && typeof res.total === 'number' ? res.total : rows.length }
}

// 审批：入企申请落在 enterprise_join_apply，审批需回传 source 让后端写回该表。
const APPLY_SOURCE = 'enterprise_join_apply'

export function approveApply(applyId) {
  return request.post(`/admin/join-applies/${applyId}/approve`, { source: APPLY_SOURCE })
}

export function rejectApply(applyId) {
  return request.post(`/admin/join-applies/${applyId}/reject`, { source: APPLY_SOURCE })
}

// result: 'approved' | 'rejected'
export function reviewApply(applyId, result) {
  return result === 'approved' ? approveApply(applyId) : rejectApply(applyId)
}

// 手机号邀请成员
export function inviteMember(enterpriseId, { phone, role = 'staff' } = {}) {
  return request.post(`${memberBase(enterpriseId)}/invite`, { phone, role })
}

/* ==================== 角色（/admin/employee-roles） ==================== */

const ROLE_PALETTE = ['#8B5CF6', '#EC4899', '#14B8A6', '#F59E0B', '#EF4444', '#3B82F6']
const ROLE_ICONS = ['fa-user-tie', 'fa-user-gear', 'fa-user-shield', 'fa-briefcase', 'fa-users']

/** employee_role.permissions 兼容三种存量写法：code 数组 / 嵌套对象 / 逗号串。 */
export function parseRolePermissions(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return permTokensToCodes(raw)
  const text = String(raw).trim()
  if (!text) return []
  if (text.startsWith('[')) {
    try { return permTokensToCodes(JSON.parse(text)) } catch { return [] }
  }
  if (text.startsWith('{')) {
    let obj
    try { obj = JSON.parse(text) } catch { return [] }
    const tokens = []
    Object.keys(obj).forEach((k) => {
      const v = obj[k]
      if (v && typeof v === 'object') {
        Object.keys(v).forEach((sk) => { if (v[sk] === true || v[sk] === 1) tokens.push(sk) })
      } else if (v === true || v === 1) {
        tokens.push(k)
      }
    })
    return permTokensToCodes(tokens)
  }
  return permTokensToCodes(text.split(','))
}

const hashIndex = (text, len) => {
  const s = String(text || '')
  let sum = 0
  for (let i = 0; i < s.length; i += 1) sum += s.charCodeAt(i)
  return sum % len
}

/** EmployeeRole 实体 → 本页角色卡字段（desc←description，权限 JSON→前端 key 列表）。 */
const normalizeRole = (r, idx) => {
  const codes = parseRolePermissions(r.permissions)
  return {
    ...r,
    id: r.id,
    name: dash(r.name, '未命名角色'),
    desc: r.description != null ? r.description : (r.desc || ''),
    system: r.system === true || r.system === 'true',
    color: r.color || ROLE_PALETTE[hashIndex(r.name, ROLE_PALETTE.length)] || ROLE_PALETTE[idx % ROLE_PALETTE.length],
    icon: r.icon || ROLE_ICONS[idx % ROLE_ICONS.length],
    permissions: codes,
    keys: permCodesToKeys(codes),
  }
}

export async function listEmployeeRoles() {
  const res = await request.get('/admin/employee-roles')
  const rows = toList(res).map(normalizeRole)
  return { rows, total: rows.length }
}

const rolePayload = (payload = {}) => {
  const name = payload.name || ''
  return {
    name,
    description: payload.desc != null ? payload.desc : (payload.description || ''),
    color: payload.color || ROLE_PALETTE[hashIndex(name, ROLE_PALETTE.length)],
    icon: payload.icon || ROLE_ICONS[hashIndex(name, ROLE_ICONS.length)],
    permissions: JSON.stringify(permTokensToCodes(payload.permissions || [])),
  }
}

export function createEmployeeRole(payload) {
  return request.post('/admin/employee-roles', rolePayload(payload))
}

export function updateEmployeeRole(id, payload) {
  return request.put(`/admin/employee-roles/${id}`, rolePayload(payload))
}

export function deleteEmployeeRole(id) {
  return request.delete(`/admin/employee-roles/${id}`)
}
