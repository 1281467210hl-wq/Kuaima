import request from './request'

// ============================================================
// 后台-闪电发薪「转账记录 / 商户账户余额」接口层
// 后端契约（字段已按源码确认）：
//   GET  /admin/transfers?direction=out|in|all&status=success|processing|failed
//        &keyword&bossId&bossKeyword&projectId&type&payMethod&startDate&endDate&page(0起)&size
//        -> data: { records, stats, direction, range, page, size, total }
//        记录行字段：id/direction/no/title/company/bossName/projectName/typeText/payAccount/
//                   payee/recvAccount/amountFen/amountYuan/peopleCount/status/statusText/payTime/remark
//   GET  /admin/transfers/{id}/details  （id 为出账 payroll_order.id；入账充值单无人员明细）
//        -> data: { order:{orderNo,title,company,amountFen,amountYuan,payTime,status,...},
//                   details:[{name,job,phone,attendDays,dailyWageYuan,amountYuan,status,...}], 计数 }
//   POST /admin/transfers/export -> text/csv 文本流（含 UTF-8 BOM），请求体同列表筛选条件
//   GET  /admin/merchant-accounts?keyword&bossId&bossKeyword&status&isDefault&page&size
//        -> data: { records:[{accountName,subjectName,merchantNo,balanceFen,balanceYuan,
//                   frozenYuan,availableYuan,bankName,isDefault,status,...}], total, 余额统计 }
//   POST/PUT/DELETE /admin/merchant-accounts[/{id}]  入参 SaveRequest：
//        bossId/accountName(简称)/subjectName(全称)/merchantNo/bankName/balanceFen|Yuan 等
//   GET  /admin/merchant-accounts/recharge-orders?accountId&status&payMethod&...&page&size
//        -> data: { records:[{orderNo,amountYuan,payMethodText,statusText,payTime,...}], total }
// 金额单位：出账/账户余额后端以「分」存储（附 xxxYuan 字符串），充值单以「元」存储。
// ============================================================

const num = (v) => Number(v || 0)

/** 转账列表筛选参数清洗：空串/all 转 undefined，避免污染查询串。 */
function transferParams(params = {}) {
  const { direction, status, keyword, bossId, bossKeyword, projectId, type, payMethod, startDate, endDate, page = 0, size = 10 } = params
  const clean = (v) => (v === undefined || v === null || v === '' || v === 'all' ? undefined : v)
  return {
    direction: clean(direction),
    status: clean(status),
    keyword: clean(keyword),
    bossId: clean(bossId),
    bossKeyword: clean(bossKeyword),
    projectId: clean(projectId),
    type: clean(type),
    payMethod: clean(payMethod),
    startDate: clean(startDate),
    endDate: clean(endDate),
    page,
    size,
  }
}

/** 后端记录行 → 表格模板字段（amount 用「分」，与 fmtMoney 一致；time 交回视图格式化）。 */
function adaptTransferRow(o) {
  return {
    id: o.id,
    direction: o.direction || 'out',
    no: o.no || '—',
    title: o.title || '',
    company: o.company || '—',
    payAccount: o.payAccount || '—',
    amount: num(o.amountFen),
    amountYuan: o.amountYuan || '0.00',
    payee: o.payee || '—',
    recvAccount: o.recvAccount || '—',
    time: o.payTime || o.reviewTime || null,
    status: o.status || 'processing',
    statusText: o.statusText || '',
    peopleCount: o.peopleCount || 0,
  }
}

/** data.stats → 统计卡扁平字段。 */
function adaptTransferStats(s) {
  const src = s || {}
  const out = src.out || {}
  const inStat = src.in || {}
  return {
    count: num(src.count),
    amountYuan: src.amountYuan || '0.00',
    peopleCount: num(src.peopleCount),
    outCount: num(out.count),
    outAmountYuan: out.amountYuan || '0.00',
    inCount: num(inStat.count),
    inAmountYuan: inStat.amountYuan || '0.00',
  }
}

/** 转账记录列表（含筛选维度统计）：GET /admin/transfers */
export async function listTransfers(params = {}) {
  const res = await request.get('/admin/transfers', { params: transferParams(params) })
  const d = (res && res.data) || {}
  const records = (d.records || []).map(adaptTransferRow)
  const total = typeof res.total === 'number' ? res.total : num(d.total || records.length)
  return { records, stats: adaptTransferStats(d.stats), total }
}

/** 单笔转账人员明细：GET /admin/transfers/{id}/details（仅出账记录有明细）。 */
export async function getTransferDetails(id) {
  const res = await request.get(`/admin/transfers/${id}/details`)
  const d = (res && res.data) || {}
  const head = d.order || {}
  return {
    head: {
      no: head.orderNo || '—',
      title: head.title || '',
      company: head.company || '—',
      amountFen: num(head.amountFen),
      payTime: head.payTime || null,
      status: head.status || '',
      peopleCount: head.peopleCount || 0,
      payAccount: head.payAccount || '',
    },
    // daily/amount 已换算为「元」数值，与弹窗模板 fmtYuan 对齐
    details: (d.details || []).map((t) => ({
      name: t.name || '—',
      job: t.job || '—',
      phone: t.phone || '—',
      days: t.attendDays || 0,
      daily: num(t.dailyWageYuan),
      amount: num(t.amountYuan),
      status: t.status || 'processing',
      statusText: t.statusText || '',
    })),
    successCount: num(d.successCount),
    processingCount: num(d.processingCount),
    failedCount: num(d.failedCount),
  }
}

/** 导出转账记录：POST /admin/transfers/export 返回 text/csv 文本流（Blob），请求体同列表筛选。 */
export function exportTransfers(query = {}) {
  const { direction, status, keyword, bossId, bossKeyword, projectId, type, payMethod, startDate, endDate } =
    transferParams(query)
  return request.post(
    '/admin/transfers/export',
    { direction, status, keyword, bossId, bossKeyword, projectId, type, payMethod, startDate, endDate },
    { responseType: 'blob' }
  )
}

/** 商户账户行 → 卡片模板字段（balanceYuan 为后端给的元字符串，直接展示）。 */
function adaptAccount(o) {
  return {
    id: o.id,
    bossId: o.bossId,
    bossName: o.bossName || '',
    short: o.accountName || o.companyShort || '—',
    company: o.subjectName || '—',
    merchant: o.merchantNo || '—',
    bank: o.bankName || '待完善',
    balanceFen: num(o.balanceFen),
    balanceYuan: o.balanceYuan || '0.00',
    frozenYuan: o.frozenYuan || '0.00',
    availableYuan: o.availableYuan || '0.00',
    isDefault: !!o.isDefault,
    status: o.status || 'active',
  }
}

/** 商户账户列表（跨老板）：GET /admin/merchant-accounts */
export async function listMerchantAccounts(params = {}) {
  const { keyword, bossId, bossKeyword, status, isDefault, page = 0, size = 10 } = params
  const res = await request.get('/admin/merchant-accounts', {
    params: { keyword, bossId, bossKeyword, status, isDefault, page, size },
  })
  const d = (res && res.data) || {}
  return {
    records: (d.records || []).map(adaptAccount),
    total: typeof res.total === 'number' ? res.total : num(d.total),
    pageBalanceYuan: d.pageBalanceYuan || '0.00',
    globalBalanceYuan: d.globalBalanceYuan || '0.00',
  }
}

/** 表单字段 → 后端 SaveRequest（short→accountName、company→subjectName、merchant→merchantNo、bank→bankName）。 */
function toSaveRequest(form = {}) {
  return {
    bossId: form.bossId == null || form.bossId === '' ? undefined : Number(form.bossId),
    accountName: form.short,
    subjectName: form.company,
    merchantNo: form.merchant,
    bankName: form.bank || undefined,
  }
}

/** 新增商户账户：POST /admin/merchant-accounts（bossId/accountName/subjectName/merchantNo 必填）。 */
export function createMerchantAccount(form) {
  return request.post('/admin/merchant-accounts', toSaveRequest(form))
}

/** 编辑商户账户：PUT /admin/merchant-accounts/{id}（null 字段后端不覆盖；bossId 仅随详情回填不参与修改）。 */
export function updateMerchantAccount(id, form) {
  const body = toSaveRequest(form)
  delete body.bossId
  return request.put(`/admin/merchant-accounts/${id}`, body)
}

/** 删除商户账户（软删除）：DELETE /admin/merchant-accounts/{id} */
export function deleteMerchantAccount(id) {
  return request.delete(`/admin/merchant-accounts/${id}`)
}

/** 充值订单记录：GET /admin/merchant-accounts/recharge-orders（amount 单位为元）。 */
export async function listRechargeOrders(params = {}) {
  const { keyword, bossId, accountId, bossKeyword, status, payMethod, orderNo, startDate, endDate, page = 0, size = 10 } = params
  const res = await request.get('/admin/merchant-accounts/recharge-orders', {
    params: { keyword, bossId, accountId, bossKeyword, status, payMethod, orderNo, startDate, endDate, page, size },
  })
  const d = (res && res.data) || {}
  return {
    records: (d.records || []).map((o) => ({
      id: o.id,
      orderNo: o.orderNo || '—',
      amountText: num(o.amountYuan).toFixed(2),
      payMethodText: o.payMethodText || o.payMethod || '—',
      status: o.status || '',
      statusText: o.statusText || '',
      time: o.payTime || o.createdAt || '—',
      accountName: o.accountName || '',
    })),
    total: typeof res.total === 'number' ? res.total : num(d.total),
  }
}
