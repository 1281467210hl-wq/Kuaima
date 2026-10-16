import request from './request'

/* ============================================================
 * 智能招聘（后台）接口封装
 *   岗位      GET  /admin/positions        /stats  /{id}
 *             POST /admin/positions?bossId  PUT /admin/positions/{id}
 *             DELETE /admin/positions/{id}  PUT /admin/positions/{id}/status
 *   简历库    GET  /admin/resumes          /{resumeId}  DELETE /{resumeId}
 *             POST /admin/resumes/import   GET  /admin/resumes/stats
 *   候选人    GET  /admin/candidates       /{id}  PUT /{id}/status
 *             POST /admin/candidates       DELETE /admin/candidates/{id}
 *             POST /admin/candidates/from-resume（简历库「加入候选人」）
 *   直播橱窗  GET/POST/PUT/DELETE /admin/live-showcases  PUT /{id}/status
 *   归属老板  GET  /admin/users/bosses（兜底 GET /admin/enterprises）
 * 字段与后端 AdminRecruitService 行映射保持一致，差异在本层适配，页面模板只关心 UI 字段。
 * ============================================================ */

/** 分页体 { code, data: [...], page, total }（后端 page 从 0 开始）。 */
export function unwrapPage(res) {
  const raw = res?.data
  let list = []
  if (Array.isArray(raw)) list = raw
  else if (Array.isArray(raw?.content)) list = raw.content
  else if (Array.isArray(raw?.list)) list = raw.list
  const total = Number(res?.total ?? raw?.totalElements ?? raw?.total ?? list.length)
  return { list, total: Number.isFinite(total) ? total : list.length }
}

/** java.sql.Timestamp#toString() 带 .0 毫秒尾巴，列表统一裁掉。 */
const trimMillis = (value) => (typeof value === 'string' ? value.replace(/\.0$/, '') : value)

const text = (value, fallback = '—') => {
  if (value === null || value === undefined || value === '') return fallback
  return value
}

/* ==================== 岗位 ==================== */

export function listPositions({ keyword, status, bossId, page = 0, size = 10 } = {}) {
  return request.get('/admin/positions', {
    params: { keyword: keyword || undefined, status: status || undefined, bossId, page, size },
  })
}

export function positionStats() {
  return request.get('/admin/positions/stats')
}

export function getPosition(id) {
  return request.get(`/admin/positions/${id}`)
}

// 发布岗位：POST /admin/positions?bossId=（query 为归属老板 user id，建议必传否则 bossName 为空）
// form 字段对齐 BossPosition：name(必填)/code(缺省后端自动生成 POS+时间戳)/category/department/
// location/hireCount/salaryRange/experience/education/description；空串统一归 null，避免写入空字符串。
export function createPosition(form, bossId) {
  return request.post('/admin/positions', positionPayload(form), {
    params: { bossId: bossId === undefined || bossId === null || bossId === '' ? undefined : bossId },
  })
}

// 编辑岗位：PUT /admin/positions/{id}，后端逐字段整体覆盖，name/code 必须随 body 提交（code 非空列）；status 传 on/off。
export function updatePosition(id, form) {
  return request.put(`/admin/positions/${id}`, positionPayload(form))
}

// 删除岗位：DELETE /admin/positions/{id}（物理删除）
export function deletePosition(id) {
  return request.delete(`/admin/positions/${id}`)
}

// 岗位状态切换：PUT /admin/positions/{id}/status（on/off 互换，后端无 body）
export function togglePositionStatus(id) {
  return request.put(`/admin/positions/${id}/status`)
}

function positionPayload(form = {}) {
  const str = (v) => {
    const s = v === null || v === undefined ? '' : String(v).trim()
    return s === '' ? null : s
  }
  const payload = {
    name: str(form.name),
    code: str(form.code),
    category: str(form.category),
    department: str(form.department),
    location: str(form.location),
    hireCount: Number.isFinite(Number(form.hireCount)) ? Math.max(Math.trunc(Number(form.hireCount)), 0) : 0,
    salaryRange: str(form.salaryRange),
    experience: str(form.experience),
    education: str(form.education),
    description: str(form.description),
  }
  if (form.status != null && form.status !== '') payload.status = String(form.status).trim()
  return payload
}

export function mapPositionDetail(row) {
  return {
    ...mapPosition(row),
    description: row.description,
    applicationCount: row.applicationCount ?? 0,
    recentApplications: Array.isArray(row.recentApplications) ? row.recentApplications : [],
  }
}

export function mapPosition(row) {
  return {
    id: row.id,
    code: text(row.code, String(row.id ?? '')),
    name: text(row.name, '未命名岗位'),
    type: text(row.type, ''),
    department: row.department,
    location: row.location,
    salaryRange: row.salaryRange,
    headcount: row.headcount ?? 0,
    applied: row.applied ?? 0,
    interviewed: row.interviewed ?? 0,
    hired: row.hired ?? 0,
    status: row.status,
    rawStatus: row.rawStatus,
    statusName: text(row.statusName),
    statusClass: row.statusClass || 'default',
    publishTime: trimMillis(row.publishTime) || '—',
    bossId: row.bossId,
    bossName: text(row.bossName),
  }
}

/* ==================== 简历库 ==================== */

// tab: all/pending/viewed/sent/fav/new/import/apply（后端 searchGlobal）
export function listResumes({ tab = 'all', keyword, status, bossId, page = 0, size = 10 } = {}) {
  return request.get('/admin/resumes', {
    params: { tab: tab || 'all', keyword: keyword || undefined, status: status || undefined, bossId, page, size },
  })
}

export function resumeStats() {
  return request.get('/admin/resumes/stats')
}

export function getResume(resumeId) {
  return request.get(`/admin/resumes/${resumeId}`)
}

export function deleteResume(resumeId) {
  return request.delete(`/admin/resumes/${resumeId}`)
}

// 导入：multipart/form-data（request.js 默认头是 application/json，需显式覆盖）
// file 走 form-data 字段，bossId 走 query（后端 @RequestParam 必填）。
export function importResume(file, bossId, fileName) {
  const form = new FormData()
  form.append('file', file, file.name)
  if (fileName) form.append('fileName', fileName)
  return request.post('/admin/resumes/import', form, {
    params: { bossId },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

export function mapResume(row) {
  return {
    id: row.id,
    name: text(row.name, '未命名'),
    job: text(row.job, ''),
    exp: text(row.exp, ''),
    edu: text(Array.isArray(row.edu) ? '' : row.edu, ''),
    uploadTime: trimMillis(row.uploadTime) || '—',
    gender: row.gender,
    age: row.age,
    phone: row.phone,
    city: row.city,
    jobCategory: row.jobCategory,
    expectedSalary: row.expectedSalary,
    status: row.status,
    favorite: row.favorite,
    source: row.source,
    bossId: row.bossId,
    bossName: text(row.bossName),
  }
}

const mapExperience = (e) => ({
  id: e.id,
  title: text(e.title, ''),
  org: text(e.org, ''),
  period: [e.startDate, e.endDate].filter(Boolean).join(' 至 ') || '—',
  description: text(e.description, ''),
  tags: e.tags,
})

/**
 * 简历详情映射。
 * 后端 detail 用 edu/work/project 三个数组覆盖了行里的 edu（学历）字符串，
 * 这里拆成 eduExperiences/workExperiences/projectExperiences，学历文本回退列表行。
 */
export function mapResumeDetail(row, fallbackRow) {
  const eduExperiences = Array.isArray(row.edu) ? row.edu.map(mapExperience) : []
  const workExperiences = Array.isArray(row.work) ? row.work.map(mapExperience) : []
  const projectExperiences = Array.isArray(row.project) ? row.project.map(mapExperience) : []
  const eduText = row.resume?.edu || (!Array.isArray(row.edu) ? row.edu : undefined) || fallbackRow?.edu
  const base = mapResume({ ...row, edu: eduText })
  return {
    ...base,
    eduExperiences,
    workExperiences,
    projectExperiences,
    fileUrl: row.fileUrl,
    previewUrl: row.previewUrl,
    filePreviewUrl: row.filePreviewUrl,
    filePreviewIsImage: !!row.filePreviewIsImage,
  }
}

/* ==================== 候选人（岗位投递） ==================== */

// 后端仅 4 态：PENDING/INTERVIEWING/HIRED/REJECTED（无「待面试」）
export const CANDIDATE_STATUS = {
  PENDING: '待筛选',
  INTERVIEWING: '面试中',
  HIRED: '已录用',
  REJECTED: '已拒绝',
}

export function candidateStatusName(status) {
  return CANDIDATE_STATUS[status] || status || '—'
}

/** 兼容 UI 中文状态与后端枚举（后端仅接受 PENDING/INTERVIEWING/HIRED/REJECTED，无「待面试」）。 */
export function normalizeCandidateStatus(status) {
  if (!status || status === 'all') return undefined
  const upper = String(status).trim().toUpperCase()
  if (CANDIDATE_STATUS[upper]) return upper
  const hit = Object.entries(CANDIDATE_STATUS).find(([, name]) => name === String(status).trim())
  return hit ? hit[0] : upper
}

export function listCandidates({ status, keyword, bossId, positionId, startDate, endDate, page = 0, size = 10 } = {}) {
  return request.get('/admin/candidates', {
    params: {
      status: normalizeCandidateStatus(status),
      keyword: keyword || undefined,
      bossId,
      positionId,
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      page,
      size,
    },
  })
}

export function getCandidate(id) {
  return request.get(`/admin/candidates/${id}`)
}

export function updateCandidateStatus(id, status, extra = {}) {
  return request.put(`/admin/candidates/${id}/status`, { status: normalizeCandidateStatus(status), ...extra })
}

export function createCandidate({ positionId, resumeId, remark }) {
  return request.post('/admin/candidates', { positionId, resumeId, remark })
}

// 简历库「加入候选人」：POST /admin/candidates/from-resume（body: {resumeId}）。
// 后端按 简历意向岗位 ↔ 岗位名称 匹配岗位，命中则建 source=MANUAL 的 PENDING 投递；
// 匹配不到岗位时返回 400，调用方需回退到岗位下拉手动选择（走 createCandidate）。
// 成功响应 data.created=false 表示该(position,resume)投递已存在，即「已在候选人列表中」。
export function addCandidateFromResume(resumeId) {
  return request.post('/admin/candidates/from-resume', { resumeId })
}

export function deleteCandidate(id) {
  return request.delete(`/admin/candidates/${id}`)
}

export function mapCandidate(row) {
  return {
    id: row.id,
    positionId: row.positionId,
    resumeId: row.resumeId,
    userId: row.userId,
    name: text(row.name, '未知候选人'),
    job: text(row.job, ''),
    applyTime: trimMillis(row.applyTime) || '—',
    interviewTime: trimMillis(row.interviewTime) || '—',
    status: row.status,
    statusName: candidateStatusName(row.status),
    remark: row.remark,
    gender: row.gender,
    age: row.age,
    phone: row.phone,
    city: row.city,
    expLabel: row.expLabel,
    edu: Array.isArray(row.edu) ? row.resume?.edu : row.edu,
    school: row.school,
    major: row.major,
    schoolYears: row.schoolYears,
    schoolTag: row.schoolTag,
    bossId: row.bossId,
    bossName: text(row.bossName),
  }
}

export function mapCandidateDetail(row) {
  const detail = mapCandidate(row)
  detail.positionName = text(row.positionName, detail.job)
  detail.decisionTime = trimMillis(row.decisionTime)
  detail.eduExperiences = Array.isArray(row.edu) ? row.edu.map(mapExperience) : []
  detail.workExperiences = Array.isArray(row.work) ? row.work.map(mapExperience) : []
  detail.projectExperiences = Array.isArray(row.project) ? row.project.map(mapExperience) : []
  detail.resume = row.resume ? mapResume(row.resume) : null
  if (detail.resume) detail.edu = detail.resume.edu
  return detail
}

/* ==================== 直播橱窗 ==================== */

export const SHOWCASE_STATUS = { PENDING: '待开播', LIVE: '直播中', ENDED: '已结束' }

export function listShowcases({ keyword, status, bossId, page = 0, size = 10 } = {}) {
  return request.get('/admin/live-showcases', {
    params: { keyword: keyword || undefined, status: status || undefined, bossId, page, size },
  })
}

export function createShowcase({ title, anchorName, douyinAccount, bossId, liveTime, status }) {
  return request.post('/admin/live-showcases', { title, anchorName, douyinAccount, bossId, liveTime, status })
}

export function updateShowcase(id, payload) {
  return request.put(`/admin/live-showcases/${id}`, payload)
}

export function updateShowcaseStatus(id, status) {
  return request.put(`/admin/live-showcases/${id}/status`, { status })
}

export function deleteShowcase(id) {
  return request.delete(`/admin/live-showcases/${id}`)
}

export function mapShowcase(row) {
  return {
    id: row.id,
    title: text(row.title, '未命名橱窗'),
    host: text(row.host, ''),
    account: text(row.account, ''),
    time: trimMillis(row.time) || '—',
    status: row.status,
    statusName: SHOWCASE_STATUS[row.status] || row.statusName || '—',
    bossId: row.bossId,
    bossName: text(row.bossName),
  }
}

/* ==================== 归属老板选项（简历导入用） ==================== */

/** bossId 必须是 user 表主键（后端 userRepository.existsById 校验），优先取雇主列表。 */
export async function listBossOptions(keyword) {
  const res = await request.get('/admin/users/bosses', {
    params: { keyword: keyword || undefined, page: 0, size: 100 },
  })
  const { list } = unwrapPage(res)
  return list
    .map((u) => ({
      id: u.id,
      name: text(u.companyName || u.realName || u.nickname || u.username, `老板 #${u.id}`),
      companyCode: u.companyCode,
    }))
    .filter((b) => b.id != null)
}

/** 兜底：雇主列表为空/异常时用企业列表（企业 id 与老板 user id 不同源，仅作可选项展示）。 */
export async function listEnterpriseOptions() {
  const res = await request.get('/admin/enterprises')
  const { list } = unwrapPage(res)
  return list
    .map((e) => ({ id: e.id, name: text(e.companyName, `企业 #${e.id}`), companyCode: e.companyCode }))
    .filter((e) => e.id != null)
}
