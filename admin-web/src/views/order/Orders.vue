<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">用工订单</h1>
      <p class="page-desc">管理所有日结订单、状态追踪、异常处理</p>
    </div>

    <div class="stat-cards">
      <div v-for="card in statCards" :key="card.title" class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">{{ card.title }}</span>
          <div :class="['stat-card-icon', card.color]"><i :class="['fas', card.icon]"></i></div>
        </div>
        <div class="stat-card-value">{{ card.value }}</div>
        <div :class="['stat-card-change', card.trend]"><i :class="['fas', card.trend === 'down' ? 'fa-arrow-down' : 'fa-arrow-up']"></i><span>{{ card.note }}</span></div>
      </div>
    </div>

    <div class="card">
      <div class="filter-bar">
        <div class="filter-item"><span>订单号</span><el-input v-model="searchKeyword" placeholder="输入订单号搜索" clearable style="width: 200px;" prefix-icon="Search" @keyup.enter="handleSearch" /></div>
        <div class="filter-item"><span>订单状态</span><el-select v-model="statusFilter" placeholder="全部" clearable style="width: 110px;">
          <el-option label="全部" value="" />
          <el-option v-for="status in orderStatuses" :key="status.value" :label="status.label" :value="status.value" />
        </el-select></div>
        <div class="filter-item"><span>企业类型</span>
          <el-cascader
            v-model="typeFilter"
            :options="industryTypeOptions"
            :props="{ value: 'id', label: 'name', children: 'enterpriseTypes', emitPath: false }"
            placeholder="行业 / 企业类型"
            clearable
            filterable
            style="width: 220px"
          /></div>
        <div class="filter-item"><span>日期范围</span><el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 250px;" /></div>
        <button class="btn btn-primary btn-sm" @click="handleSearch"><i class="fas fa-search"></i> 查询</button>
        <button class="btn btn-outline btn-sm" @click="handleReset"><i class="fas fa-rotate-left"></i> 重置</button>
        <div class="filter-space"></div>
        <button class="btn btn-outline btn-sm" @click="handleExport"><i class="fas fa-download"></i> 导出数据</button>
      </div>

      <div class="quick-filter-bar">
        <button v-for="item in quickFilters" :key="item.value" class="quick-filter" :class="{ active: statusFilter === item.value }" @click="quickFilter(item.value)">{{ item.label }}</button>
      </div>

      <el-table class="orders-table" :data="ordersData" stripe :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
        <el-table-column label="订单号" width="130" fixed="left">
          <template #default="{ row }"><span class="order-id-cell">{{ row.id }}</span></template>
        </el-table-column>
        <el-table-column label="雇主" width="190" show-overflow-tooltip>
          <template #default="{ row }"><div class="employer-cell"><span class="employer-logo" :style="{ background: avatarBackground(row.id, row.employer) }">{{ avatarText(row.employer) }}</span><span class="employer-name-text">{{ row.employer }}</span></div></template>
        </el-table-column>
        <el-table-column label="零工" width="150" show-overflow-tooltip>
          <template #default="{ row }"><div class="worker-cell"><span class="worker-avatar" :style="{ background: avatarBackground(row.workerId, row.worker) }">{{ avatarText(row.worker) }}</span><span class="worker-name-text">{{ row.worker }}</span></div></template>
        </el-table-column>
        <el-table-column label="工种" width="170" show-overflow-tooltip>
          <template #default="{ row }"><div class="job-cell"><span class="job-name">{{ row.job }}</span><span :class="['job-type-tag', jobTypeClass(row.type)]">{{ row.type || '其他' }}</span></div></template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }"><span class="amount-cell">¥{{ formatMoney(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="开始/结束时间" min-width="170">
          <template #default="{ row }"><div class="time-range"><span><i class="fas fa-arrow-up start-icon"></i>{{ row.startTime }}</span><span><i class="fas fa-arrow-down end-icon"></i>{{ row.endTime }}</span></div></template>
        </el-table-column>
        <el-table-column label="订单状态" width="100">
          <template #default="{ row }"><span :class="['status-badge', row.statusClass]">{{ statusLabel(row.status) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }"><button class="btn btn-outline btn-xs" @click="showDetail(row)">查看详情</button></template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <div class="pagination-info">共 {{ total }} 条订单记录，当前显示 {{ pageStart }}-{{ pageEnd }} 条</div>
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
    <el-dialog v-model="detailVisible" width="760px" class="order-detail-dialog" :show-close="false">
      <template #header><div class="detail-dialog-header"><div><span class="detail-dialog-title">订单详情</span><span v-if="selectedOrder" :class="['status-badge', selectedOrder.statusClass]">{{ statusLabel(selectedOrder.status) }}</span></div><button class="detail-close" @click="detailVisible = false"><i class="fas fa-times"></i></button></div></template>
      <template v-if="selectedOrder">
        <div class="detail-section-title">基本信息</div>
        <div class="detail-info-grid">
          <div class="detail-info-item"><span>订单号</span><strong>{{ selectedOrder.id }}</strong></div><div class="detail-info-item"><span>订单金额</span><strong class="primary-text">¥{{ formatMoney(selectedOrder.amount) }}</strong></div>
          <div class="detail-info-item"><span>工种</span><strong>{{ selectedOrder.job }}</strong></div><div class="detail-info-item"><span>工价</span><strong>¥{{ formatMoney(selectedOrder.amount) }}/天</strong></div>
          <div class="detail-info-item"><span>开始时间</span><strong>{{ selectedOrder.startTime }}</strong></div><div class="detail-info-item"><span>结束时间</span><strong>{{ selectedOrder.endTime }}</strong></div>
          <div class="detail-info-item"><span>工作地点</span><strong>{{ selectedOrder.location }}</strong></div><div class="detail-info-item"><span>结算方式</span><strong>日结</strong></div>
        </div>
        <div class="detail-section-title">雇主信息</div>
        <div class="detail-info-grid"><div class="detail-info-item"><span>雇主名称</span><strong>{{ selectedOrder.employer }}</strong></div><div class="detail-info-item"><span>联系人</span><strong>{{ selectedOrder.contact }}</strong></div><div class="detail-info-item"><span>联系电话</span><strong>{{ selectedOrder.employerPhone }}</strong></div><div class="detail-info-item"><span>雇主评分</span><strong class="warning-text">{{ selectedOrder.employerRating }}</strong></div></div>
        <div class="detail-section-title">零工信息</div>
        <div class="detail-info-grid"><div class="detail-info-item"><span>姓名</span><strong>{{ selectedOrder.worker }}</strong></div><div class="detail-info-item"><span>联系电话</span><strong>{{ selectedOrder.workerPhone }}</strong></div><div class="detail-info-item"><span>实名认证</span><strong class="success-text">已认证</strong></div><div class="detail-info-item"><span>信用评分</span><strong class="primary-text">{{ selectedOrder.workerRating }}</strong></div></div>
        <div class="detail-section-title">订单时间线</div>
        <div class="order-timeline"><div v-for="(stage, index) in orderTimeline" :key="stage.title" class="timeline-item"><div :class="['timeline-dot', stage.state]"><i :class="['fas', stage.icon]"></i></div><div class="timeline-content"><div class="timeline-title">{{ stage.title }}<span v-if="stage.current">（{{ statusLabel(selectedOrder.status) }}）</span></div><div class="timeline-time">{{ stage.time }}</div></div></div></div>
        <div class="detail-section-title review-title">零工对老板的评价</div>
        <div v-if="selectedOrder.workerReview" class="review-card"><div class="review-head"><span class="review-avatar worker-review-avatar">{{ avatarText(selectedOrder.worker) }}</span><div class="review-meta"><strong>{{ selectedOrder.worker }} 的评价</strong><small>{{ formatReviewTime(selectedOrder.workerReview.time) }}</small></div><span class="review-score">{{ Number(selectedOrder.workerReview.score).toFixed(1) }}</span></div><div class="review-dims"><span v-for="dim in selectedOrder.workerReview.dimensions" :key="dim.label">{{ dim.label }} <i v-for="n in 5" :key="n" :class="['fas', n <= dim.score ? 'fa-star' : 'fa-star empty-star']"></i></span></div><div class="review-content">{{ selectedOrder.workerReview.content || '未填写评价内容' }}</div></div><div v-else class="review-empty"><i class="far fa-comment-dots"></i>订单完成后，零工可对老板进行评价<br>当前订单暂无评价</div>
        <div class="detail-section-title review-title boss-review-title">老板对零工的评价</div>
        <div v-if="selectedOrder.bossReview" class="review-card boss-review-card"><div class="review-head"><span class="review-avatar employer-review-avatar">{{ avatarText(selectedOrder.employer) }}</span><div class="review-meta"><strong>{{ selectedOrder.employer }} 对 {{ selectedOrder.worker }} 的评价</strong><small>{{ formatReviewTime(selectedOrder.bossReview.time) }}</small></div><span class="review-score">{{ Number(selectedOrder.bossReview.score).toFixed(1) }}</span></div><div class="review-dims"><span v-for="dim in selectedOrder.bossReview.dimensions" :key="dim.label">{{ dim.label }} <i v-for="n in 5" :key="n" :class="['fas', n <= dim.score ? 'fa-star' : 'fa-star empty-star']"></i></span></div><div class="review-content">{{ selectedOrder.bossReview.content || '未填写评价内容' }}</div></div><div v-else class="review-empty"><i class="far fa-comment-dots"></i>订单完成后，老板可对零工进行评价<br>当前订单暂无评价</div>
      </template>
      <template #footer><div class="dialog-footer"><el-button @click="detailVisible = false">关闭</el-button><el-button v-if="selectedOrder?.status === '纠纷'" type="warning" @click="handleDispute(selectedOrder)">处理纠纷</el-button><el-button v-if="selectedOrder && !isCanceled(selectedOrder) && !['已完成','已结算'].includes(selectedOrder.status)" type="danger" plain @click="handleCancel(selectedOrder)">取消订单</el-button></div></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, getJobCategoryTree, getOrderStats, listOrders } from '@/api/order'

const searchKeyword = ref('')
const statusFilter = ref('')
const typeFilter = ref('')
const dateRange = ref([])

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const ordersData = ref([])
const detailVisible = ref(false)
const selectedOrder = ref(null)
const orderStats = ref({ total: 0, byStatus: {} })

const orderStatuses = [
  { label: '已报名', value: '已报名' }, { label: '已录用', value: '已录用' }, { label: '已到岗', value: '已到岗' },
  { label: '已完工', value: '待结算' }, { label: '已结算', value: '已结算' }, { label: '已完成', value: '已完成' },
  { label: '纠纷', value: '纠纷' }, { label: '已取消', value: '取消招工' },
]
const industryTypeOptions = ref([])
const quickFilters = [
  { label: '全部订单', value: '' }, { label: '已报名', value: '已报名' }, { label: '已录用', value: '已录用' },
  { label: '已到岗', value: '已到岗' }, { label: '已完工', value: '待结算' }, { label: '已结算', value: '已结算' }, { label: '已完成', value: '已完成' },
]

const statusClassMap = {
  '已完成': 'success',
  '已报名': 'default',
  '已录用': 'purple',
  '已到岗': 'info',
  '已完工': 'warning',
  '已结算': 'teal',
  '进行中': 'info',
  '待确认': 'warning',
  '纠纷': 'danger',
  '已取消': 'default',
  '待处理': 'warning',
  '待结算': 'warning',
  '结算中': 'info',
  '结算失败': 'danger',
}

const formatTime = (t) => {
  if (!t) return '-'
  const date = new Date(t)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleTimeString('zh-CN', { hour12: false })
}
const formatDateTime = (t) => {
  if (!t) return '-'
  const date = new Date(t)
  if (Number.isNaN(date.getTime())) return '-'
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
const formatTimelineTime = (value) => {
  if (!value) return '暂无记录'
  const raw = String(value)
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '暂无记录'
  // 旧数据只有日期精度，不能格式化为浏览器本地的 00:00:00。
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) return raw
  return formatDateTime(value)
}
const formatReviewTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '暂无时间'

const formatMoney = value => {
  const amount = Number(value ?? 0)
  return Number.isFinite(amount) ? amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '0.00'
}
const avatarText = value => String(value || '快').slice(0, 1)
const avatarBackground = (id, name) => {
  const palette = ['linear-gradient(135deg,#3B82F6,#2563EB)', 'linear-gradient(135deg,#F59E0B,#D97706)', 'linear-gradient(135deg,#10B981,#059669)', 'linear-gradient(135deg,#8B5CF6,#7C3AED)', 'linear-gradient(135deg,#EC4899,#BE185D)']
  const key = `${name || ''}${id || ''}`
  let hash = 0
  for (const char of key) hash = (hash * 31 + char.charCodeAt(0)) % palette.length
  return palette[hash]
}
const jobTypeClass = () => 'type-manufacture'
const statusLabel = status => ({ 待结算: '已完工', 取消招工: '已取消', 取消报名: '已取消' }[status] || status)

const statusCount = status => Number(orderStats.value.byStatus?.[status] || 0)
const completedCount = computed(() => statusCount('已完成'))
const statCards = computed(() => [
  { title: '今日订单', value: Number(orderStats.value.total || 0).toLocaleString('zh-CN'), note: '符合当前筛选条件的订单', icon: 'fa-clipboard-list', color: '', trend: 'up' },
  { title: '已到岗', value: statusCount('已到岗').toLocaleString('zh-CN'), note: '在岗中订单', icon: 'fa-location-dot', color: 'blue', trend: 'up' },
  { title: '已完成', value: completedCount.value.toLocaleString('zh-CN'), note: `完成率 ${ordersData.value.length ? ((completedCount.value / ordersData.value.length) * 100).toFixed(1) : '0.0'}%`, icon: 'fa-check-circle', color: 'green', trend: 'up' },
  { title: '已报名', value: statusCount('已报名').toLocaleString('zh-CN'), note: `待录用 ${statusCount('已录用')} 单`, icon: 'fa-user-plus', color: 'red', trend: 'down' },
])
const pageStart = computed(() => total.value ? (currentPage.value - 1) * pageSize.value + 1 : 0)
const pageEnd = computed(() => Math.min(currentPage.value * pageSize.value, total.value))
const orderTimeline = computed(() => {
  const order = selectedOrder.value
  if (!order) return []
  const current = ['已报名', '已录用', '已到岗', '待结算', '已完成'].indexOf(order.status)
  const stages = [
    { title: '零工已报名', icon: 'fa-user-plus', time: formatTimelineTime(order.applyAt) },
    { title: '雇主已录用', icon: 'fa-user-check', time: formatTimelineTime(order.hireAt) },
    { title: `已到岗${order.location && order.location !== '-' ? ` - ${order.location}` : ''}`, icon: 'fa-location-dot', time: formatTimelineTime(order.workAt) },
    { title: '已完工', icon: 'fa-flag-checkered', time: formatTimelineTime(order.finishAt || order.finishDate) },
    { title: '工资已结算', icon: 'fa-yen-sign', time: formatTimelineTime(order.settlementPayTime) },
    { title: '订单已完成', icon: 'fa-circle-check', time: formatTimelineTime(order.completedAt) },
  ]
  const normalizedCurrent = order.status === '待结算' ? 3 : order.status === '已完成' ? 5 : current
  return stages.map((stage, index) => ({ ...stage, state: index < normalizedCurrent ? 'done' : index === normalizedCurrent ? 'current' : 'pending', current: index === normalizedCurrent }))
})

const normalizeOrder = (item) => ({
  id: item.orderNumber || item.parentOrderId || item.orderId || item.id,
  itemId: item.id,
  orderId: item.orderId,
  employer: item.employerName || '-',
  worker: item.workerName || '-',
  workerId: item.userId,
  job: item.jobCategoryName || item.postion || item.jobTitle || '-',
  type: item.enterpriseTypeName || '-',
  amount: item.amount ?? item.salary ?? '-',
  status: item.status || '-',
  statusClass: statusClassMap[item.status] ?? 'default',
  startTime: formatDateTime(item.startTime),
  endTime: formatDateTime(item.endTime),
  applyAt: item.applyAt || item.applyDate || null,
  hireAt: item.hireAt || item.hireDate || null,
  workAt: item.workAt || item.workDate || null,
  finishDate: item.finishDate,
  finishAt: item.finishAt,
  settlementPayTime: item.settlementPayTime,
  completedAt: item.completedAt || (item.status === '已完成' ? item.settlementPayTime : null),
  workerReview: item.bossReview || null,
  bossReview: item.workerReview || null,
  location: item.address || '-',
  contact: item.contact || item.employerName || '-',
  employerPhone: item.employerPhone || item.phone || '-',
  workerPhone: item.workerPhone || item.userPhone || item.phone || '-',
  employerRating: item.employerRating ? `${item.employerRating} / 5.0` : '-',
  workerRating: item.workerRating ? `${item.workerRating} / 5.0` : '-',
})

const loadOrders = async () => {
  try {
    const params = {
      status: statusFilter.value || undefined,
      keyword: searchKeyword.value || undefined,
      type: undefined,
      enterpriseTypeId: typeFilter.value || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined,
      page: currentPage.value - 1,
      size: pageSize.value,
    }
    const [res, statsRes] = await Promise.all([listOrders(params), getOrderStats(params)])
    const d = res.data
    const list = Array.isArray(d) ? d : (d?.content || d?.list || [])
    total.value = res.total ?? d?.totalElements ?? d?.total ?? list.length
    ordersData.value = list.map(normalizeOrder)
    orderStats.value = statsRes.data || { total: 0, byStatus: {} }
  } catch (err) {
    console.warn('[Orders] API 加载失败:', err.message)
    ordersData.value = []
    total.value = 0
    orderStats.value = { total: 0, byStatus: {} }
    ElMessage.error('加载订单列表失败')
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadOrders()
}

const handleReset = () => {
  searchKeyword.value = ''
  statusFilter.value = ''
  typeFilter.value = ''
  dateRange.value = []
  handleSearch()
}

const quickFilter = status => {
  statusFilter.value = status
  handleSearch()
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadOrders()
}

const onSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadOrders()
}

const onPageChange = (page) => {
  currentPage.value = page
  loadOrders()
}

const showDetail = (row) => { selectedOrder.value = row; detailVisible.value = true }
const isCanceled = (row) => ['已取消', '取消招工', '取消报名'].includes(row.status)

const handleCancel = async (row) => {
  if (isCanceled(row)) return
  try {
    const result = await ElMessageBox.prompt('请输入取消原因（可选）', '取消订单', { inputPlaceholder: '例如：后台管理员取消', confirmButtonText: '确认取消', cancelButtonText: '返回' })
    await cancelOrder(row.itemId, result.value)
    ElMessage.success('订单已取消')
    await loadOrders()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '取消订单失败')
  }
}

const handleDispute = (row) => {
  selectedOrder.value = row
  ElMessageBox.alert('请先核实雇主与零工双方凭证，再通过订单详情处理后续结算。', `处理纠纷 · ${row.id}`, { confirmButtonText: '知道了', type: 'warning' })
}

const handleExport = () => {
  if (!ordersData.value.length) return ElMessage.info('当前没有可导出的订单')
  const headers = ['订单号', '雇主', '零工', '工种', '金额', '开始时间', '结束时间', '状态']
  const lines = ordersData.value.map(row => [row.id, row.employer, row.worker, row.job, row.amount, row.startTime, row.endTime, row.status].map(value => `"${String(value ?? '').replaceAll('"', '""')}"`).join(','))
  const blob = new Blob([`\ufeff${headers.join(',')}\n${lines.join('\n')}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = '用工订单.csv'; link.click(); URL.revokeObjectURL(url)
}

const loadJobTypes = async () => {
  try {
    const result = await getJobCategoryTree()
    // 与小程序 all-jobs 保持一致：行业 → 企业类型 两级结构，过滤无企业类型的行业。
    industryTypeOptions.value = (Array.isArray(result.data) ? result.data : [])
      .filter(industry => Array.isArray(industry.enterpriseTypes) && industry.enterpriseTypes.length > 0)
      .map(industry => ({
        id: `industry-${industry.id}`,
        name: industry.name,
        enterpriseTypes: industry.enterpriseTypes.map(item => ({ id: item.id, name: item.name })),
      }))
  } catch (error) {
    console.warn('[Orders] 企业类型加载失败:', error.message)
    industryTypeOptions.value = []
  }
}

onMounted(async () => {
  await loadJobTypes()
  await loadOrders()
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
.filter-item { display: flex; align-items: center; gap: 8px; }
.filter-item > span { color: var(--text-secondary); font-size: 13px; white-space: nowrap; }
.filter-space { flex: 1; }
.quick-filter-bar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.quick-filter { display: inline-flex; align-items: center; padding: 5px 12px; border: 1px solid var(--border,#E5E7EB); border-radius: 16px; background: #fff; color: var(--text-secondary); font-size: 13px; cursor: pointer; transition: all .2s; }
.quick-filter:hover { border-color: var(--primary); color: var(--primary); }
.quick-filter.active { border-color: var(--primary); background: var(--primary); color: #fff; }
.stat-card-icon { width: 40px; height: 40px; border-radius: 10px; background: linear-gradient(135deg,#FFF0EB,#FFE8DC); color: var(--primary); display: flex; align-items: center; justify-content: center; font-size: 16px; }
.stat-card-icon.blue { background: linear-gradient(135deg,#EFF6FF,#DBEAFE); color: var(--secondary); }
.stat-card-icon.green { background: linear-gradient(135deg,#ECFDF5,#D1FAE5); color: var(--success); }
.stat-card-icon.red { background: linear-gradient(135deg,#FEF2F2,#FEE2E2); color: var(--danger); }
.stat-card-change { display: flex; align-items: center; gap: 5px; font-size: 12px; }
.stat-card-change.up { color: var(--success); }
.stat-card-change.down { color: var(--danger); }
.orders-table :deep(.el-table__row) { height: 62px; }
.orders-table :deep(.el-table__header th) { height: 44px; }
.order-id-cell { color: var(--primary); font-family: monospace; font-weight: 500; }
.employer-cell,.worker-cell { display: flex; align-items: center; gap: 8px; }
.employer-logo,.worker-avatar { width: 32px; height: 32px; flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center; color: #fff; font-size: 13px; font-weight: 600; }
.employer-logo { border-radius: 8px; }
.worker-avatar { border-radius: 50%; }
.employer-name-text { overflow: hidden; max-width: 140px; color: var(--text-primary); font-size: 13px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.worker-name-text { color: var(--text-primary); font-size: 13px; font-weight: 500; }
.job-cell { display: flex; flex-direction: column; align-items: flex-start; gap: 3px; }
.job-name { overflow: hidden; max-width: 150px; color: var(--text-primary); font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.job-type-tag { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 500; }
.type-elec { background: #EFF6FF; color: #2563EB; }.type-logistics { background: #FFFBEB; color: #F59E0B; }.type-catering { background: #FEF2F2; color: #EF4444; }.type-warehouse { background: #ECFDF5; color: #10B981; }.type-manufacture { background: #F3F4F6; color: #6B7280; }.type-service { background: #FAF5FF; color: #8B5CF6; }.type-auto { background: #F0F9FF; color: #0EA5E9; }.type-agriculture { background: #F0FDF4; color: #22C55E; }
.amount-cell { color: var(--text-primary); font-weight: 600; font-variant-numeric: tabular-nums; }
.time-range { display: flex; flex-direction: column; gap: 4px; color: var(--text-secondary); font-size: 13px; }
.time-range i { width: 15px; margin-right: 3px; font-size: 10px; }.start-icon { color: var(--success); }.end-icon { color: var(--danger); }
.btn-xs { padding: 4px 10px; font-size: 12px; }
.status-badge.purple { background: #F5F3FF; color: #7C3AED; }.status-badge.teal { background: #F0FDFA; color: #0D9488; }
.detail-dialog-header { display: flex; align-items: center; justify-content: space-between; width: 100%; }.detail-dialog-title { color: var(--text-primary); font-size: 18px; font-weight: 600; }.detail-dialog-header .status-badge { margin-left: 10px; }
.detail-close { display: flex; align-items: center; justify-content: center; width: 28px; height: 28px; padding: 0; border: 0; border-radius: 6px; background: transparent; color: var(--text-muted); cursor: pointer; }.detail-close:hover { background: #F3F4F6; }
.detail-section-title { margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid var(--border); color: var(--text-primary); font-size: 14px; font-weight: 600; }
.detail-info-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16px; margin-bottom: 20px; }.detail-grid-last { margin-bottom: 0; }
.detail-info-item { display: flex; flex-direction: column; gap: 4px; }.detail-info-item span { color: var(--text-muted); font-size: 12px; }.detail-info-item strong { overflow: hidden; color: var(--text-primary); font-size: 14px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.primary-text { color: var(--primary) !important; font-size: 16px !important; }.success-text { color: var(--success) !important; }.dialog-footer { display: flex; justify-content: flex-end; gap: 12px; }
.warning-text { color: var(--warning) !important; }.order-timeline { margin: 0 0 4px; }.timeline-item { position: relative; display: flex; gap: 12px; padding: 10px 0; }.timeline-item:not(:last-child)::after { position: absolute; top: 34px; bottom: -6px; left: 11px; width: 2px; background: var(--border); content: ''; }.timeline-dot { z-index: 1; display: flex; flex: 0 0 24px; align-items: center; justify-content: center; width: 24px; height: 24px; border-radius: 50%; color: #fff; font-size: 11px; }.timeline-dot.done { background: var(--success); }.timeline-dot.current { background: var(--primary); }.timeline-dot.pending { background: #D1D5DB; }.timeline-content { flex: 1; min-width: 0; }.timeline-title { color: var(--text-primary); font-size: 13px; font-weight: 500; }.timeline-time { margin-top: 2px; color: var(--text-muted); font-size: 12px; }.review-title { margin-top: 24px; }.boss-review-title { margin-top: 20px; }.review-empty { padding: 26px 0; color: var(--text-muted); font-size: 13px; line-height: 1.9; text-align: center; }.review-empty i { display: block; margin-bottom: 8px; color: #E5E7EB; font-size: 30px; }
.review-card { padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: #FAFAFA; }.boss-review-card { border-color: rgba(255,107,53,.25); background: #FFF8F4; }.review-head { display: flex; align-items: center; gap: 10px; }.review-avatar { display: inline-flex; flex: 0 0 38px; align-items: center; justify-content: center; width: 38px; height: 38px; border-radius: 50%; color: #fff; font-size: 14px; font-weight: 600; }.worker-review-avatar { background: linear-gradient(135deg,#10B981,#059669); }.employer-review-avatar { background: linear-gradient(135deg,#F59E0B,#D97706); }.review-meta { display: flex; flex: 1; flex-direction: column; gap: 3px; min-width: 0; }.review-meta strong { overflow: hidden; color: var(--text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.review-meta small { color: var(--text-muted); font-size: 12px; }.review-score { color: var(--warning); font-size: 17px; font-weight: 700; }.review-dims { display: flex; flex-wrap: wrap; gap: 14px; margin: 12px 0; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; background: #fff; color: var(--text-secondary); font-size: 12px; }.review-dims i { margin-left: 3px; color: var(--warning); font-size: 11px; }.review-dims .empty-star { color: #E5E7EB; }.review-content { padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; background: #fff; color: var(--text-primary); font-size: 13px; line-height: 1.75; }
:deep(.order-detail-dialog.el-dialog) { max-width: 90vw; max-height: 92vh; margin: 4vh auto !important; overflow: hidden; border-radius: 16px; }:deep(.order-detail-dialog .el-dialog__header) { display: flex; margin: 0; padding: 20px 24px; border-bottom: 1px solid var(--border); }:deep(.order-detail-dialog .el-dialog__body) { overflow-y: auto; max-height: 65vh; padding: 24px; }:deep(.order-detail-dialog .el-dialog__footer) { padding: 16px 24px; border-top: 1px solid var(--border); }

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  
  .pagination-info {
    font-size: 13px;
    color: var(--text-secondary);
  }
}
@media (max-width: 800px) { .detail-info-grid { grid-template-columns: 1fr; } }
</style>
