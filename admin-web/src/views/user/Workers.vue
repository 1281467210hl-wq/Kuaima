<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">零工管理</h1>
      <p class="page-desc">管理平台零工信息、状态、操作</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">总人数</span>
          <div class="stat-card-icon"><i class="fas fa-user-friends"></i></div>
        </div>
        <div class="stat-card-value">{{ stats.total }}</div>
        <div :class="['stat-card-change', changeClass('total')]"><i :class="['fas', changeIcon('total')]"></i><span>{{ changeText('total') }}</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">本月新增</span>
          <div class="stat-card-icon blue"><i class="fas fa-user-plus"></i></div>
        </div>
        <div class="stat-card-value">{{ stats.monthNew }}</div>
        <div :class="['stat-card-change', changeClass('monthNew')]"><i :class="['fas', changeIcon('monthNew')]"></i><span>{{ changeText('monthNew') }}</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">在线人数</span>
          <div class="stat-card-icon green"><i class="fas fa-signal"></i></div>
        </div>
        <div class="stat-card-value">{{ stats.online }}</div>
        <div :class="['stat-card-change', changeClass('online')]"><i :class="['fas', changeIcon('online')]"></i><span>{{ changeText('online') }}</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">已冻结</span>
          <div class="stat-card-icon red"><i class="fas fa-user-lock"></i></div>
        </div>
        <div class="stat-card-value">{{ stats.frozen }}</div>
        <div :class="['stat-card-change', changeClass('frozen')]"><i :class="['fas', changeIcon('frozen')]"></i><span>{{ changeText('frozen') }}</span></div>
      </div>
    </div>

    <!-- 筛选和表格 -->
    <div class="card">
      <div class="filter-bar">
        <div class="filter-item"><span>关键词</span><el-input v-model="searchKeyword" placeholder="姓名/手机号/ID" clearable style="width: 220px;" prefix-icon="Search" /></div>
        <div class="filter-item"><span>状态</span><el-select v-model="statusFilter" placeholder="全部" clearable style="width: 120px;">
          <el-option label="全部" value="" />
          <el-option label="在线" value="在线" />
          <el-option label="离线" value="离线" />
          <el-option label="冻结" value="冻结" />
        </el-select></div>
        <div class="filter-item"><span>注册日期</span><div class="date-picker-wrap"><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 280px;" /></div></div>
        <button class="btn btn-primary btn-sm" @click="handleSearch"><i class="fas fa-search"></i> 查询</button>
        <button class="btn btn-outline btn-sm" @click="handleReset"><i class="fas fa-rotate-left"></i> 重置</button>
        <button class="btn btn-outline btn-sm export-button" :disabled="exporting" @click="handleExport"><i class="fas" :class="exporting ? 'fa-spinner fa-spin' : 'fa-download'"></i> {{ exporting ? '导出中...' : '导出数据' }}</button>
      </div>

      <div class="table-scroll">
      <el-table class="workers-table" :data="tableData" stripe :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
        <el-table-column prop="id" label="零工ID" min-width="110" show-overflow-tooltip />
        <el-table-column label="头像+姓名" min-width="185" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="user-cell">
              <span class="mini-avatar" :style="{ background: row.avatarColor || getAvatarColor(row.name) }">{{ getAvatarLetter(row.name) }}</span>
              <div class="worker-info">
                <div class="worker-name">{{ row.name }}</div>
                <div class="worker-id">ID: {{ row.id }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="certStatus" label="实名状态" min-width="105" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="['realname-status', isPhoneVerified(row) ? 'verified' : 'unverified']"><i :class="['fas', isPhoneVerified(row) ? 'fa-check-circle' : 'fa-clock']"></i>{{ isPhoneVerified(row) ? '已认证' : '未认证' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="技能标签" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="skill-tags"><span v-for="(skill, idx) in normalizeSkills(row.skills)" :key="idx" class="worker-tag" :class="`worker-tag-${idx % 4}`">{{ skill }}</span><span v-if="!normalizeSkills(row.skills).length" class="text-muted">-</span></div>
          </template>
        </el-table-column>
        <el-table-column label="信用分" min-width="95" show-overflow-tooltip>
          <template #default="{ row }"><span v-if="row.creditScore != null" :class="['credit-tag', creditLevel(row.creditScore)]">{{ row.creditScore }} {{ creditLabel(row.creditScore) }}</span><span v-else>-</span></template>
        </el-table-column>
        <el-table-column label="奖励金余额" min-width="110" show-overflow-tooltip>
          <template #default="{ row }"><span class="asset-num">{{ formatMoney(row.rewardBalance ?? row.rewardAmount ?? row.reward ?? null) }}</span></template>
        </el-table-column>
        <el-table-column label="积分余额" min-width="90" show-overflow-tooltip>
          <template #default="{ row }"><span class="asset-num points">{{ formatNumber(row.pointsBalance ?? row.pointBalance ?? row.points ?? null) }}</span></template>
        </el-table-column>
        <el-table-column prop="orders" label="完成订单" min-width="95" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.completedOrders ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="registerTime" label="注册时间" min-width="115" show-overflow-tooltip>
          <template #default="{ row }">{{ formatDateTime(row.registerTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="195">
          <template #default="{ row }">
            <div class="action-cell">
              <button class="btn btn-outline btn-sm" @click="handleDetail(row)">查看</button>
              <button v-permission="'permUser.workers.freeze'" class="btn btn-danger btn-sm" v-if="isNormal(row.status)" @click="handleFreeze(row)">冻结</button>
              <button v-permission="'permUser.workers.unfreeze'" class="btn btn-primary btn-sm" v-else @click="handleUnfreeze(row)">解冻</button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="pagination">
        <div class="pagination-info">共 {{ total }} 条记录</div>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next, jumper"
          background
          @size-change="onSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listWorkers, getWorkerStats, freezeUser, unfreezeUser } from '@/api/user'

const router = useRouter()

const searchKeyword = ref('')
const statusFilter = ref('')
const dateRange = ref([])

// 分页
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref([])
const exporting = ref(false)

// 统计数据
const stats = ref({ total: '-', monthNew: '-', online: '-', frozen: '-', changes: {} })

const changeValue = (key) => stats.value.changes?.[key]
const changeClass = (key) => {
  const value = changeValue(key)?.value
  return typeof value === 'number' ? (value >= 0 ? 'up' : 'down') : 'muted'
}
const changeIcon = (key) => {
  const value = changeValue(key)?.value
  return typeof value === 'number' ? (value >= 0 ? 'fa-arrow-up' : 'fa-arrow-down') : 'fa-minus'
}
const changeText = (key) => {
  const change = changeValue(key)
  if (!change || typeof change.value !== 'number') return '暂无对比数据'
  const sign = change.value > 0 ? '+' : ''
  return `${change.label || '较上期'} ${sign}${change.value}%`
}

// 加载统计
const loadStats = async () => {
  try {
    const result = await getWorkerStats()
    stats.value = { ...stats.value, ...(result.data || {}) }
  } catch (e) {
    console.warn('[Workers] 加载统计失败:', e)
  }
}

// 后端 Worker 真实字段
const normalizeWorker = (item) => {
  const realName = String(item.realName || '').trim()
  const nickname = String(item.nickname || '').trim()
  const phone = String(item.phone || '').trim()
  const username = String(item.username || '').trim()
  const isWechatIdentifier = /^(wx[_-]|openid[_-]?)/i.test(username)
  const displayName = realName || nickname || (phone && phone !== username ? phone : '')
  return {
    ...item,
    name: displayName || (!isWechatIdentifier && username ? username : `零工${item.id || ''}`),
    nickname: item.nickname || '',
    registerTime: item.date || item.createdAt || item.timestamp,
  }
}

const formatDateTime = (t) => {
  if (!t) return '-'
  if (typeof t === 'number') {
    const d = new Date(t)
    return isNaN(d.getTime()) ? '-' : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }
  const s = String(t)
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s
  return s.replace('T', ' ').substring(0, 10)
}

const formatDateTimeFull = (t) => {
  if (!t || t === 0) return '-'
  if (typeof t === 'number') {
    const d = new Date(t)
    if (isNaN(d.getTime())) return '-'
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const h = String(d.getHours()).padStart(2, '0')
    const min = String(d.getMinutes()).padStart(2, '0')
    return `${y}-${m}-${day} ${h}:${min}`
  }
  const s = String(t).replace('T', ' ')
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s
  return s.length > 16 ? s.substring(0, 16) : s
}

const loadData = async () => {
  try {
    const result = await listWorkers({
      keyword: searchKeyword.value || undefined,
      status: statusFilter.value || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined,
      page: currentPage.value - 1,
      size: pageSize.value
    })
    const d = result.data
    tableData.value = (Array.isArray(d) ? d : (d?.content || d?.list || [])).map(normalizeWorker)
    total.value = result.total ?? d?.totalElements ?? d?.total ?? 0
  } catch (e) {
    console.warn('[Workers] 加载列表失败:', e)
    tableData.value = []
    total.value = 0
  }
}

const getCreditColor = (score) => {
  if (!score) return 'inherit'
  if (score >= 80) return '#10B981'
  if (score >= 60) return '#F59E0B'
  return '#EF4444'
}
const creditLevel = (score) => {
  if (score >= 80) return 'lv-excellent'
  if (score >= 70) return 'lv-good'
  if (score >= 60) return 'lv-normal'
  return 'lv-low'
}
const creditLabel = (score) => score >= 80 ? '优秀' : score >= 70 ? '良好' : score >= 60 ? '一般' : '较低'
const formatNumber = (value) => value == null || value === '' ? '-' : Number(value).toLocaleString('zh-CN')
const formatMoney = (value) => value == null || value === '' ? '-' : `¥${(Number(value) / 100).toFixed(2)}`
const formatMoneyForExport = (row) => {
  const value = row.rewardBalance ?? row.rewardAmount ?? row.reward
  return value == null || value === '' ? '' : (Number(value) / 100).toFixed(2)
}
const formatPercent = (value) => value == null || value === '' ? '-' : `${Number(value).toFixed(1).replace('.0', '')}%`

const getAvatarColor = (name) => {
  const palette = ['#FF6B35', '#2563EB', '#10B981', '#8B5CF6', '#F59E0B', '#EC4899', '#06B6D4', '#64748B']
  const idx = (name || '').length % palette.length
  return palette[idx]
}
const getAvatarLetter = (name) => (name || '').charAt(0) || '?'
const normalizeSkills = (skills) => {
  if (!skills) return []
  if (Array.isArray(skills)) return skills
  if (typeof skills === 'string') return skills.split(',').filter(Boolean)
  return []
}
const isPhoneVerified = (row) => {
  return row.certStatus === true
    || row.certStatus === 1
    || row.certStatus === '已通过'
    || row.certStatus === '已认证'
    || row.certStatus === 'VERIFIED'
    || row.certStatus === 'APPROVED'
}
const isNormal = (s) => s === '正常' || s === 'NORMAL' || s === 1

const formatStatus = (s) => {
  if (s === 'NORMAL' || s === 1 || s === '正常') return '正常'
  if (s === 'FROZEN' || s === 0 || s === '冻结') return '冻结'
  return s || '-'
}
const formatRealnameStatus = (s) => {
  if (s === 'APPROVED') return '已认证'
  if (s === 'PENDING') return '待审核'
  if (s === 'REJECTED') return '已拒绝'
  return '未认证'
}
const formatGender = (g) => {
  if (g === '男' || g === 'male' || g === 'M') return '男'
  if (g === '女' || g === 'female' || g === 'F') return '女'
  return g || '-'
}

const handleSearch = () => {
  currentPage.value = 1
  loadData()
}
const handleReset = () => {
  searchKeyword.value = ''
  statusFilter.value = ''
  dateRange.value = []
  currentPage.value = 1
  loadData()
}

const csvCell = (value) => {
  if (value == null || value === '') return '""'
  let text = String(value)
  // 防止 Excel 把普通文本识别为公式。
  if (/^[=+\-@]/.test(text)) text = `'${text}`
  return `"${text.replace(/"/g, '""')}"`
}

const downloadCsv = (rows, filename) => {
  const content = `\uFEFF${rows.map(row => row.map(csvCell).join(',')).join('\r\n')}`
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const extractWorkerPage = (result) => {
  const data = result?.data
  const rows = Array.isArray(data) ? data : (data?.content || data?.list || [])
  const count = Number(result?.total ?? data?.totalElements ?? data?.total ?? rows.length)
  return { rows, count }
}

const handleExport = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    const batchSize = 500
    const params = {
      keyword: searchKeyword.value || undefined,
      status: statusFilter.value || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined
    }
    const first = extractWorkerPage(await listWorkers({ ...params, page: 0, size: batchSize }))
    const allWorkers = [...first.rows]
    const pageCount = Math.ceil(first.count / batchSize)
    for (let page = 1; page < pageCount; page += 1) {
      const next = extractWorkerPage(await listWorkers({ ...params, page, size: batchSize }))
      allWorkers.push(...next.rows)
    }

    const rows = [[
      '零工ID', '姓名', '手机号', '实名状态', '性别', '技能标签', '信用分',
      '奖励金余额（元）', '积分余额', '完成订单', '注册时间', '账户状态'
    ]]
    allWorkers.map(normalizeWorker).forEach(row => rows.push([
      row.id,
      row.name,
      row.phone,
      isPhoneVerified(row) ? '已认证' : formatRealnameStatus(row.realnameStatus || row.certStatus),
      formatGender(row.gender),
      normalizeSkills(row.skills).join('、'),
      row.creditScore ?? '',
      formatMoneyForExport(row),
      row.pointsBalance ?? row.pointBalance ?? row.points ?? '',
      row.completedOrders ?? 0,
      formatDateTimeFull(row.registerTime),
      formatStatus(row.status)
    ]))

    const today = new Date().toLocaleDateString('zh-CN', { timeZone: 'Asia/Shanghai' }).replaceAll('/', '-')
    downloadCsv(rows, `零工数据_${today}.csv`)
    ElMessage.success(`已导出 ${allWorkers.length} 条零工数据`)
  } catch (e) {
    console.warn('[Workers] 导出失败:', e)
    ElMessage.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

const onSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadData()
}

const onPageChange = (page) => {
  currentPage.value = page
  loadData()
}

// 详情弹窗
const handleDetail = (row) => router.push({ name: 'WorkerDetail', params: { id: row.id } })

// 冻结 / 解冻
const handleFreeze = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要冻结用户 ${row.name || row.nickname || row.id} 吗？`, '提示', { type: 'warning' })
    await freezeUser(row.id)
    ElMessage.success('冻结成功')
    loadData()
    loadStats()
  } catch (e) {
    if (e !== 'cancel') console.warn('[Workers] 冻结失败:', e)
  }
}
const handleUnfreeze = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要解冻用户 ${row.name || row.nickname || row.id} 吗？`, '提示', { type: 'warning' })
    await unfreezeUser(row.id)
    ElMessage.success('解冻成功')
    loadData()
    loadStats()
  } catch (e) {
    if (e !== 'cancel') console.warn('[Workers] 解冻失败:', e)
  }
}

onMounted(() => {
  loadData()
  loadStats()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 13px;
  white-space: nowrap;
}
.filter-item :deep(.el-input__wrapper),
.filter-item :deep(.el-select__wrapper),
.date-picker-wrap :deep(.el-input__wrapper) {
  min-height: 36px;
  border-radius: 6px;
  box-shadow: 0 0 0 1px #E5E7EB inset;
}
.filter-item :deep(.el-input__wrapper:hover),
.filter-item :deep(.el-input__wrapper.is-focus),
.filter-item :deep(.el-select__wrapper:hover),
.date-picker-wrap :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--primary) inset;
}
.date-picker-wrap { display: flex; align-items: center; }
.export-button { margin-left: auto; }
.stat-card-change.muted { color: var(--text-muted); }

/* 表格始终填满卡片；窗口较窄时才允许横向滚动，避免宽屏右侧出现大片空白。 */
.workers-table { width: 100% !important; min-width: 980px; }
.table-scroll { width: 100%; overflow-x: auto; }

.workers-table :deep(.el-table__header th) {
  height: 48px;
  background: #F9FAFB !important;
  color: #6B7280;
  font-size: 13px;
  font-weight: 600;
}
.workers-table :deep(.el-table__row td) {
  height: 64px;
  color: var(--text-primary);
  font-size: 13px;
}
.workers-table :deep(.el-table__body td:first-child .cell) {
  color: var(--primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.workers-table :deep(.el-table__body tr:hover > td) { background: #FAFAFA; }
.workers-table :deep(.el-table__inner-wrapper::before) { display: none; }

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.worker-info { display:flex; flex-direction:column; min-width:0; }
.worker-name { overflow:hidden; color:var(--text-primary); font-size:14px; font-weight:500; text-overflow:ellipsis; white-space:nowrap; }
.worker-id { margin-top:2px; color:var(--text-muted); font-family:monospace; font-size:12px; }
.skill-tags { display:flex; flex-wrap:wrap; gap:4px; }
.worker-tag, .credit-tag { display:inline-flex; align-items:center; white-space:nowrap; }
.worker-tag { padding:2px 7px; border-radius:4px; color:#C2410C; background:#FFF7ED; font-size:11px; }
.worker-tag-1 { color:#2563EB; background:#EFF6FF; }
.worker-tag-2 { color:#059669; background:#ECFDF5; }
.worker-tag-3 { color:#DC2626; background:#FEF2F2; }
.realname-status { display:inline-flex; align-items:center; gap:4px; font-size:12px; white-space:nowrap; }
.realname-status.verified { color:var(--success); }
.realname-status.unverified { color:var(--text-muted); }
.credit-tag { padding:3px 9px; border-radius:12px; font-size:11px; font-weight:600; }
.credit-tag.lv-excellent { color:#059669; background:#ECFDF5; }
.credit-tag.lv-good { color:#2563EB; background:#EFF6FF; }
.credit-tag.lv-normal { color:#D97706; background:#FFFBEB; }
.credit-tag.lv-low { color:#DC2626; background:#FEF2F2; }
.asset-num { font-weight:500; white-space:nowrap; }
.asset-num.points { color:var(--primary); }

/* 操作按钮强制一行排列 */
.action-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
  white-space: nowrap;
}
.action-cell .el-button {
  margin-left: 0;
  margin-right: 8px;
  padding: 0;
}
.action-cell .btn {
  min-width: 46px;
  padding: 4px 10px;
  font-size: 12px;
  border-radius: 6px;
}
.action-cell .el-button:last-child {
  margin-right: 0;
}

.mini-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.pagination-info {
  font-size: 13px;
  color: var(--text-secondary);
}

/* ====== 详情弹窗样式 ====== */
.worker-detail-dialog :deep(.el-dialog__header) {
  display: none;
}
.worker-detail-dialog :deep(.el-dialog__body) {
  padding: 0;
}
.worker-detail-dialog :deep(.el-dialog__footer) {
  display: none;
}

.detail-dialog-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border, #E5E7EB);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.detail-dialog-title {
  font-size: 18px;
  font-weight: 600;
}
.detail-dialog-close {
  cursor: pointer;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: var(--text-muted, #9CA3AF);
}
.detail-dialog-close:hover {
  background: #F3F4F6;
  color: var(--text-primary);
}

.detail-dialog-body {
  padding: 24px;
  overflow-y: auto;
  max-height: 60vh;
}

.detail-avatar-section {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
}
.detail-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}
.detail-basic-info h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 6px 0;
}
.detail-basic-info p {
  margin: 3px 0;
  font-size: 13px;
  color: var(--text-secondary, #6B7280);
  display: flex;
  align-items: center;
  gap: 6px;
}
.detail-basic-info p i {
  color: var(--text-muted, #9CA3AF);
  width: 16px;
  text-align: center;
}

.detail-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary, #1F2937);
  margin-bottom: 12px;
  padding-left: 8px;
  border-left: 3px solid var(--primary, #FF6B35);
}

.detail-info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0;
}
.detail-info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border, #E5E7EB);
}
.detail-info-item:nth-child(odd) {
  padding-right: 24px;
}
.detail-info-item:nth-child(even) {
  padding-left: 24px;
  border-left: 1px solid var(--border, #E5E7EB);
}
.detail-info-label {
  font-size: 12px;
  color: var(--text-muted, #9CA3AF);
}
.detail-info-value {
  font-size: 14px;
  color: var(--text-primary, #1F2937);
  font-weight: 500;
}
.detail-info-value.positive { color:var(--success); }

.text-muted {
  color: var(--text-muted, #9CA3AF);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}
.status-badge.success {
  background: #ECFDF5;
  color: #059669;
}
.status-badge.danger {
  background: #FEF2F2;
  color: #DC2626;
}

.detail-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  background: #F3F4F6;
  color: #6B7280;
}
.detail-tag-blue {
  background: #DBEAFE;
  color: #2563EB;
}

.detail-dialog-footer {
  padding: 16px 24px;
  border-top: 1px solid var(--border, #E5E7EB);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn-success {
  background: #10B981;
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 8px 16px;
  font-size: 14px;
  cursor: pointer;
}
.btn-success:hover {
  opacity: 0.9;
}
</style>
