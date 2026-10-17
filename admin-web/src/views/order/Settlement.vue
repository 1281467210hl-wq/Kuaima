<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">结算管理</h1>
      <p class="page-desc">订单结算、财务核对、支付记录</p>
    </div>

    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">待结算订单</span>
          <div class="stat-card-icon yellow"><i class="fas fa-hourglass-half"></i></div>
        </div>
        <div class="stat-card-value">{{ formatNumber(stats.pendingCount) }}</div>
        <div class="stat-card-change down"><i class="fas fa-clock"></i><span>需要处理</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">结算金额</span>
          <div class="stat-card-icon"><i class="fas fa-yen-sign"></i></div>
        </div>
        <div class="stat-card-value">¥{{ formatNumber(stats.settledAmount) }}</div>
        <div class="stat-card-change up"><i class="fas fa-arrow-up"></i><span>今日结算总额</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">已结算</span>
          <div class="stat-card-icon green"><i class="fas fa-check-double"></i></div>
        </div>
        <div class="stat-card-value">{{ formatNumber(stats.settledCount) }}</div>
        <div class="stat-card-change up"><i class="fas fa-arrow-up"></i><span>结算笔数</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">结算成功率</span>
          <div class="stat-card-icon blue"><i class="fas fa-percentage"></i></div>
        </div>
        <div class="stat-card-value">{{ stats.successRate }}%</div>
        <div class="stat-card-change up"><i class="fas fa-arrow-up"></i><span>本周平均</span></div>
      </div>
    </div>

    <div class="batch-action-bar">
      <div class="batch-action-info">
        共 <strong>{{ formatNumber(stats.totalCount) }}</strong> 条结算记录，其中
        <strong class="warning-text">{{ formatNumber(stats.pendingCount) }}</strong> 条待结算；
        已勾选 <strong>{{ selectedRows.length }}</strong> 条<template v-if="selectedRows.length">，其中待结算 <strong>{{ selectedPendingRows.length }}</strong> 条</template>
      </div>
      <div class="batch-actions">
        <el-button v-permission="'permOrder.settlement.settle'" type="primary" :disabled="!selectedPendingRows.length" @click="handleBatchSettle">
          <i class="fas fa-coins"></i> 批量结算{{ selectedPendingRows.length ? `（${selectedPendingRows.length}）` : '' }}
        </el-button>
      </div>
    </div>

    <div class="card">
      <div class="filter-bar">
        <div class="filter-item"><span>结算周期</span><el-select v-model="cycleFilter" placeholder="全部" clearable style="width: 110px">
          <el-option label="全部" value="" />
          <el-option label="今日" value="today" />
          <el-option label="本周" value="week" />
          <el-option label="本月" value="month" />
        </el-select></div>
        <div class="filter-item"><span>结算状态</span><el-select v-model="statusFilter" placeholder="全部" clearable style="width: 110px">
          <el-option label="全部" value="" />
          <el-option label="待结算" value="待结算" />
          <el-option label="结算中" value="结算中" />
          <el-option label="已结算" value="已结算" />
          <el-option label="已失败" value="结算失败" />
        </el-select></div>
        <div class="filter-item"><span>支付方式</span><el-select v-model="methodFilter" placeholder="全部" clearable style="width: 110px">
          <el-option label="微信支付" value="微信支付" />
          <el-option label="支付宝" value="支付宝" />
          <el-option label="银行卡" value="银行卡" />
          <el-option label="现金" value="现金" />
        </el-select></div>
        <div class="filter-item"><span>日期范围</span><el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 250px"
          value-format="YYYY-MM-DD"
        /></div>
        <button class="btn btn-primary btn-sm" @click="handleSearch"><i class="fas fa-search"></i> 查询</button>
        <button class="btn btn-outline btn-sm" @click="handleReset"><i class="fas fa-rotate-left"></i> 重置</button>
        <div class="filter-space"></div>
        <button class="btn btn-outline btn-sm" :disabled="exporting" :title="selectedRows.length ? '导出已勾选的结算记录' : '未勾选时导出当前筛选条件下的全部结算记录'" @click="handleExport">
          <i :class="exporting ? 'fas fa-spinner fa-spin' : 'fas fa-download'"></i> {{ exporting ? '导出中...' : (selectedRows.length ? `导出所选（${selectedRows.length}）` : '导出报表') }}
        </button>
      </div>

      <el-table
        ref="settlementTable"
        :data="settlementData"
        stripe
        class="settlement-table"
        :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column label="结算单号" min-width="110" fixed="left">
          <template #default="{ row }"><span class="settle-id-cell">{{ settlementNo(row) }}</span></template>
        </el-table-column>
        <el-table-column label="订单号" min-width="110">
          <template #default="{ row }"><span class="settle-id-cell">{{ orderNo(row) }}</span></template>
        </el-table-column>
        <el-table-column label="雇主" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="employer-cell">
              <span class="employer-logo" :style="{ background: avatarBackground(row.id, row.employerName) }">{{ avatarText(row.employerName) }}</span>
              <span class="employer-name-text">{{ row.employerName || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="零工" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="worker-cell">
              <span class="worker-avatar" :style="{ background: avatarBackground(row.workerId, row.workerName) }">{{ avatarText(row.workerName) }}</span>
              <span class="worker-name-text">{{ row.workerName || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结算金额" min-width="110" align="right">
          <template #default="{ row }"><span class="money-cell primary">¥{{ formatMoney(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="平台费" min-width="100" align="right">
          <template #default="{ row }"><span class="money-cell danger">¥{{ formatMoney(row.platformFee) }}</span></template>
        </el-table-column>
        <el-table-column label="优惠券抵扣金额" min-width="140" align="right">
          <template #default="{ row }">
            <div class="coupon-cell">
              <span class="money-cell success">-¥{{ formatMoney(row.couponAmount) }}</span>
              <span v-if="row.couponName" class="coupon-name">{{ row.couponName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="实付金额" min-width="110" align="right">
          <template #default="{ row }"><span class="money-cell success">¥{{ formatMoney(row.actualAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="结算状态" min-width="100">
          <template #default="{ row }"><span class="status-badge" :class="row.statusClass">{{ row.status }}</span></template>
        </el-table-column>
        <el-table-column label="结算时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.time) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="140" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.status === '待结算'" v-permission="'permOrder.settlement.settle'" link type="success" size="small" @click="openDetail(row)">结算</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <div class="pagination-info">共 {{ formatNumber(total) }} 条结算记录</div>
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

    <el-dialog v-model="detailVisible" width="760px" class="settlement-detail-dialog" :show-close="false" :close-on-click-modal="false">
      <template #header>
        <div class="detail-dialog-header"><div><span class="detail-dialog-title">结算详情</span><span :class="['status-badge', detailStatusClass]">{{ detailRow.status || '-' }}</span></div><button class="detail-close" type="button" @click="detailVisible = false"><i class="fas fa-times"></i></button></div>
      </template>
      <div class="settle-summary">
        <div class="settle-summary-item"><div class="label">结算金额</div><div class="value primary">¥{{ formatMoney(detailRow.amount) }}</div></div>
        <div class="settle-summary-item"><div class="label">平台服务费</div><div class="value danger">-¥{{ formatMoney(detailRow.platformFee) }}</div></div>
        <div class="settle-summary-item"><div class="label">实付金额</div><div class="value success">¥{{ formatMoney(detailRow.actualAmount) }}</div></div>
      </div>

      <div class="detail-section-title">结算信息</div>
      <div class="detail-info-grid">
        <div class="detail-info-item"><span class="label">结算单号</span><span class="value">{{ settlementNo(detailRow) }}</span></div>
        <div class="detail-info-item"><span class="label">关联订单号</span><span class="value">{{ orderNo(detailRow) }}</span></div>
        <div class="detail-info-item"><span class="label">结算周期</span><span class="value">{{ cycleText(detailRow) }}</span></div>
        <div class="detail-info-item"><span class="label">支付方式</span><span class="value">{{ detailRow.payMethod || '-' }}</span></div>
        <div class="detail-info-item"><span class="label">结算时间</span><span class="value">{{ formatDateTime(detailRow.time) }}</span></div>
        <div class="detail-info-item"><span class="label">支付流水号</span><span class="value">{{ detailRow.payNo || '-' }}</span></div>
      </div>

      <div class="detail-section-title">雇主信息</div>
      <div class="detail-info-grid">
        <div class="detail-info-item"><span class="label">雇主名称</span><span class="value">{{ detailRow.employerName || '-' }}</span></div>
        <div class="detail-info-item"><span class="label">联系电话</span><span class="value">{{ detailRow.employerPhone || '-' }}</span></div>
      </div>

      <div class="detail-section-title">零工信息</div>
      <div class="detail-info-grid">
        <div class="detail-info-item"><span class="label">零工姓名</span><span class="value">{{ detailRow.workerName || '-' }}</span></div>
        <div class="detail-info-item"><span class="label">收款账户</span><span class="value">{{ detailRow.workerAccount || '—' }}</span></div>
        <div class="detail-info-item"><span class="label">账户实名</span><span class="value">{{ detailRow.workerRealname || detailRow.workerName || '—' }}</span></div>
        <div class="detail-info-item"><span class="label">实名认证</span><span class="value success-text">{{ detailRow.workerVerified === false ? '未认证' : '已认证' }}</span></div>
      </div>

      <div class="detail-section-title">费用明细</div>
      <div class="fee-box">
        <div class="fee-row"><span>订单金额</span><strong>¥{{ formatMoney(detailRow.amount) }}</strong></div>
        <div class="fee-row"><span>平台服务费</span><strong class="danger">-¥{{ formatMoney(detailRow.platformFee) }}</strong></div>
        <div class="fee-row total"><span>实付金额</span><strong class="success">¥{{ formatMoney(detailRow.actualAmount) }}</strong></div>
      </div>

      <div class="detail-section-title">服务费收款账户</div>
      <div class="detail-info-grid service-account-grid">
        <div class="detail-info-item"><span class="label">收款账户</span><span class="value">快马日结平台对公账户</span></div>
        <div class="detail-info-item"><span class="label">收款银行</span><span class="value">工商银行上海分行</span></div>
        <div class="detail-info-item"><span class="label">银行账号</span><span class="value">6222 **** **** 8888</span></div>
        <div class="detail-info-item"><span class="label">账户名</span><span class="value">快马日结科技有限公司</span></div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button v-if="detailRow.status === '待结算'" class="reminder-button" :loading="reminding" @click="sendReminder"><i v-if="!reminding" class="fas fa-comment-dots"></i> 发消息提醒</el-button>
          <el-button v-if="detailRow.status === '待结算'" v-permission="'permOrder.settlement.settle'" type="primary" @click="openConfirm(detailRow)"><i class="fas fa-check-circle"></i> 财务确认结算</el-button>
        </div>
      </template>
    </el-dialog>

    <div v-if="confirmVisible" class="settlement-confirm-overlay" @click.self="confirmVisible = false">
      <div class="settlement-confirm-card" role="dialog" aria-modal="true" aria-labelledby="settlement-confirm-title">
        <div class="confirm-content">
          <div class="confirm-icon"><i class="fas fa-check-circle"></i></div>
          <div id="settlement-confirm-title" class="confirm-title">财务确认结算</div>
          <div class="confirm-desc">确认后将标记该笔订单为已结算，<br>此操作不可撤销。</div>
          <div class="confirm-actions"><button class="confirm-cancel" type="button" @click="confirmVisible = false">取消</button><button class="confirm-submit" type="button" @click="confirmSettlement"><i class="fas fa-check"></i> 确认结算</button></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { batchSettlePay, getSettlementDetail, getSettlementStats, listSettlements, remindSettlement, settlePay } from '@/api/settlement'

const cycleFilter = ref('')
const statusFilter = ref('')
const methodFilter = ref('')
const dateRange = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const settlementData = ref([])
const stats = ref({ pendingCount: 0, settledAmount: 0, settledCount: 0, successRate: 0, totalCount: 0 })
const detailVisible = ref(false)
const detailRow = ref({})
const confirmVisible = ref(false)
const pendingSettleRow = ref(null)
const settlementTable = ref()
const selectedRows = ref([])

const selectedPendingRows = computed(() => selectedRows.value.filter(isSettleable))
const exporting = ref(false)
const detailStatusClass = computed(() => detailRow.value.statusClass || statusClassMap[detailRow.value.status] || 'default')

const statusClassMap = {
  '待结算': 'warning',
  '结算中': 'info',
  '已结算': 'success',
  '结算失败': 'danger'
}

const formatNumber = value => Number(value ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
const formatMoney = value => Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const formatDateTime = value => {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN', { hour12: false })
}
const settlementNo = row => `JS${String(row?.id ?? 0).padStart(6, '0')}`
const orderNo = row => `ORD${String(row?.orderId ?? 0).padStart(6, '0')}`
const cycleText = row => {
  if (!row?.time) return '-'
  const time = new Date(row.time)
  if (Number.isNaN(time.getTime())) return '-'
  return `${time.getFullYear()}年第${Math.ceil((time.getMonth() + 1) / 3)}季度`
}
const avatarText = value => String(value || '快').slice(0, 1)
const avatarBackground = (id, name) => {
  const palette = [
    'linear-gradient(135deg,#3B82F6,#2563EB)',
    'linear-gradient(135deg,#F59E0B,#D97706)',
    'linear-gradient(135deg,#10B981,#059669)',
    'linear-gradient(135deg,#8B5CF6,#7C3AED)',
    'linear-gradient(135deg,#EC4899,#BE185D)'
  ]
  const key = String(name || '') + String(id || '')
  let hash = 0
  for (const char of key) hash = (hash * 31 + char.charCodeAt()) % palette.length
  return palette[hash]
}

const normalizeSettlement = item => {
  const amount = Number(item.totalAmount ?? item.amount ?? 0)
  const platformFee = Number(item.serviceFee ?? item.platformFee ?? 0)
  return {
    ...item,
    employerName: item.employerName || '-',
    workerName: item.workerName || '-',
    amount,
    platformFee,
    couponAmount: Number(item.couponAmount ?? 0),
    actualAmount: amount - platformFee,
    status: item.status || '-',
    statusClass: statusClassMap[item.status] || 'default'
  }
}

const rows = result => {
  const data = result?.data
  return Array.isArray(data) ? data : (data?.content || data?.list || [])
}

const totalOf = result => Number(result?.total ?? result?.data?.totalElements ?? result?.data?.total ?? 0)

const loadStats = async () => {
  try {
    const result = await getSettlementStats()
    stats.value = { ...stats.value, ...(result?.data || {}) }
  } catch (error) {
    console.warn('[Settlement] 加载统计失败:', error)
  }
}

const loadSettlements = async () => {
  try {
    const result = await listSettlements(filterParams(currentPage.value - 1, pageSize.value))
    settlementData.value = normalizeRows(result)
    total.value = totalOf(result)
    // 换页/筛选后清空勾选，批量结算只处理用户当前明确勾选的记录
    selectedRows.value = []
  } catch (error) {
    console.warn('[Settlement] API 加载失败:', error)
    settlementData.value = []
    total.value = 0
    ElMessage.error('加载结算列表失败')
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadSettlements()
}

const handleReset = () => {
  cycleFilter.value = ''
  statusFilter.value = ''
  methodFilter.value = ''
  dateRange.value = []
  handleSearch()
}

const isSettleable = row => row.status === '待结算'
const handleSelectionChange = rows => { selectedRows.value = rows }
const filterParams = (page, size) => {
  const [start, end] = dateRange.value || []
  return {
    status: statusFilter.value || undefined,
    cycle: cycleFilter.value || undefined,
    startDate: start || undefined,
    endDate: end || undefined,
    page,
    size
  }
}

// 支付方式后端不支持筛选，列表与导出统一在前端过滤
const normalizeRows = result => rows(result)
  .map(normalizeSettlement)
  .filter(item => !methodFilter.value || item.payMethod === methodFilter.value)

const onSizeChange = size => {
  pageSize.value = size
  currentPage.value = 1
  loadSettlements()
}

const onPageChange = page => {
  currentPage.value = page
  loadSettlements()
}

const openDetail = async row => {
  detailRow.value = normalizeSettlement(row)
  detailVisible.value = true
  try {
    const result = await getSettlementDetail(row.id)
    detailRow.value = normalizeSettlement({ ...row, ...(result?.data || {}) })
  } catch (error) {
    console.warn('[Settlement] 加载详情失败:', error)
  }
}

const payOne = async row => {
  await settlePay(row.id)
}

const openConfirm = row => {
  pendingSettleRow.value = row
  detailVisible.value = false
  confirmVisible.value = true
}

const confirmSettlement = async () => {
  const row = pendingSettleRow.value
  if (!row) return
  try {
    await payOne(row)
    ElMessage.success('结算成功')
    confirmVisible.value = false
    detailVisible.value = false
    pendingSettleRow.value = null
    await Promise.all([loadSettlements(), loadStats()])
  } catch (error) {
    console.warn('[Settlement] settlePay API 调用失败:', error)
    ElMessage.error('结算失败，请重试')
  }
}

const reminding = ref(false)
const sendReminder = async () => {
  if (reminding.value || !detailRow.value?.id) return
  reminding.value = true
  try {
    await remindSettlement(detailRow.value.id)
    ElMessage.success(`已向雇主${detailRow.value.employerName ? `「${detailRow.value.employerName}」` : ''}发送结算提醒`)
  } catch (error) {
    // 错误提示由 request 拦截器统一弹出
    console.warn('[Settlement] 发送结算提醒失败:', error)
  } finally {
    reminding.value = false
  }
}

const EXPORT_PAGE_SIZE = 500

const fetchAllForExport = async () => {
  const all = []
  for (let page = 0; ; page += 1) {
    const result = await listSettlements(filterParams(page, EXPORT_PAGE_SIZE))
    const pageRows = rows(result)
    all.push(...normalizeRows(result))
    if (pageRows.length < EXPORT_PAGE_SIZE || (page + 1) * EXPORT_PAGE_SIZE >= totalOf(result)) return all
  }
}

const handleExport = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    // 有勾选导出勾选记录，未勾选导出当前筛选条件下的全部记录
    const data = selectedRows.value.length ? [...selectedRows.value] : await fetchAllForExport()
    if (!data.length) return ElMessage.info('当前筛选条件下没有可导出的结算记录')
    const headers = ['结算单号', '订单号', '雇主', '零工', '结算金额', '平台服务费', '优惠券抵扣金额', '实付金额', '结算状态', '结算时间']
    const lines = data.map(row => [
      settlementNo(row), orderNo(row), row.employerName, row.workerName, row.amount,
      row.platformFee, row.couponAmount, row.actualAmount, row.status, formatDateTime(row.time)
    ].map(value => `"${String(value ?? '').replaceAll('"', '""')}"`).join(','))
    const blob = new Blob([`\ufeff${headers.join(',')}\n${lines.join('\n')}`], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '结算记录.csv'
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${data.length} 条结算记录`)
  } catch (error) {
    console.warn('[Settlement] 导出失败:', error)
    ElMessage.error('导出失败，请重试')
  } finally {
    exporting.value = false
  }
}

const handleBatchSettle = async () => {
  const pending = selectedPendingRows.value
  if (!pending.length) return ElMessage.info('请先勾选需要结算的待结算记录')
  try {
    const skipped = selectedRows.value.length - pending.length
    await ElMessageBox.confirm(`将确认结算已勾选的 ${pending.length} 笔待结算订单${skipped ? `（另有 ${skipped} 笔非待结算记录将跳过）` : ''}，是否继续？`, '批量结算', {
      confirmButtonText: '确认结算',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    const result = await batchSettlePay(pending.map(row => row.id))
    const data = result?.data || {}
    const failedCount = Number(data.failedCount || 0)
    ElMessage.success(`批量结算完成，成功 ${Number(data.successCount || 0)} 笔${failedCount ? `，失败 ${failedCount} 笔` : ''}`)
  } catch (error) {
    console.warn('[Settlement] 批量结算 API 调用失败:', error)
    ElMessage.error('批量结算失败，请重试')
  } finally {
    await Promise.all([loadSettlements(), loadStats()])
  }
}

onMounted(() => {
  loadSettlements()
  loadStats()
})
</script>

<style scoped>
.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-item { display: flex; align-items: center; gap: 8px; }
.filter-item > span { color: var(--text-secondary,#4B5563); font-size: 13px; white-space: nowrap; }
.filter-space { flex: 1; }
.batch-action-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; padding: 12px 16px; border-radius: 10px; background: #FFF8E6; }
.batch-action-info { color: var(--text-secondary,#4B5563); font-size: 13px; }
.warning-text { color: var(--warning,#D97706); }
.batch-actions { display: flex; gap: 10px; }
.settlement-table :deep(.el-table__row) { height: 56px; }
.settlement-table :deep(.el-table__header th) { height: 44px; }
.settle-id-cell { color: var(--primary); font-family: monospace; font-weight: 500; }
.employer-cell { display: flex; align-items: center; gap: 8px; }
.employer-logo { width: 32px; height: 32px; flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center; border-radius: 8px; color: #fff; font-size: 13px; font-weight: 600; }
.employer-name-text { overflow: hidden; max-width: 140px; color: var(--text-primary,#111827); font-weight: 500; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.worker-cell { display: flex; align-items: center; gap: 8px; }
.worker-avatar { width: 32px; height: 32px; flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; color: #fff; font-size: 13px; font-weight: 600; }
.worker-name-text { color: var(--text-primary,#111827); font-weight: 500; font-size: 13px; }
.money-cell { color: var(--text-primary,#111827); font-weight: 600; white-space: nowrap; font-variant-numeric: tabular-nums; }
.money-cell.primary { color: var(--primary); }
.money-cell.success { color: var(--success,#059669); }
.money-cell.danger { color: var(--danger,#DC2626); }
.coupon-cell { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.coupon-name { overflow: hidden; max-width: 120px; color: var(--text-muted,#9CA3AF); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.action-btns { display: flex; gap: 2px; white-space: nowrap; }
.pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; }
.pagination-info { color: var(--text-secondary,#4B5563); font-size: 13px; }
.settle-summary { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 16px; margin-bottom: 20px; }
.settle-summary-item { padding: 14px 16px; border-radius: 10px; background: #F9FAFB; }
.settle-summary-item .label { margin-bottom: 4px; color: var(--text-secondary,#4B5563); font-size: 12px; }
.settle-summary-item .value { color: #111827; font-size: 18px; font-weight: 700; font-variant-numeric: tabular-nums; }
.settle-summary-item .primary { color: var(--primary); }
.settle-summary-item .success { color: var(--success,#059669); }
.settle-summary-item .danger { color: var(--danger,#DC2626); }
.detail-section-title { margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid #E5E7EB; color: #111827; font-size: 14px; font-weight: 700; }
.detail-info-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16px; margin-bottom: 20px; }
.detail-info-item { display: flex; flex-direction: column; gap: 4px; }
.detail-info-item .label { color: var(--text-muted,#9CA3AF); font-size: 12px; }
.detail-info-item .value { overflow: hidden; color: #111827; font-size: 14px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.fee-box { padding: 16px; border-radius: 10px; background: #F9FAFB; }
.fee-row { display: flex; align-items: center; justify-content: space-between; padding: 8px 0; border-bottom: 1px dashed #E5E7EB; color: var(--text-secondary,#4B5563); font-size: 13px; }
.fee-row strong { color: #111827; font-variant-numeric: tabular-nums; }
.fee-row .danger { color: var(--danger,#DC2626); }
.fee-row .success { color: var(--success,#059669); }
.fee-row.total { margin-top: 4px; padding-bottom: 0; border-bottom: 0; color: #111827; font-weight: 700; }
.fee-row.total strong { font-size: 16px; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 12px; }
.success-text { color: var(--success,#059669) !important; }
.service-account-grid { margin-bottom: 0; }
.reminder-button { border-color: var(--primary); color: var(--primary); }
.stat-card-icon { width: 40px; height: 40px; border-radius: 10px; background: linear-gradient(135deg,#FFF0EB 0%,#FFE8DC 100%); color: var(--primary); display: flex; align-items: center; justify-content: center; font-size: 16px; }
.stat-card-icon.blue { background: linear-gradient(135deg,#EFF6FF 0%,#DBEAFE 100%); color: var(--secondary); }
.stat-card-icon.green { background: linear-gradient(135deg,#ECFDF5 0%,#D1FAE5 100%); color: var(--success); }
.stat-card-icon.yellow { background: linear-gradient(135deg,#FFFBEB 0%,#FEF3C7 100%); color: var(--warning); }
.stat-card-change { display: flex; align-items: center; gap: 5px; font-size: 12px; }
.stat-card-change.up { color: var(--success); }
.stat-card-change.down { color: var(--warning); }
.reminder-button:hover { border-color: var(--primary); background: #FFF0EB; color: var(--primary); }
.detail-dialog-header { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.detail-dialog-title { color: var(--text-primary,#111827); font-size: 18px; font-weight: 600; }
.detail-dialog-header .status-badge { margin-left: 10px; }
.detail-close { display: flex; align-items: center; justify-content: center; width: 28px; height: 28px; padding: 0; border: 0; border-radius: 6px; background: transparent; color: var(--text-muted,#9CA3AF); cursor: pointer; }
.detail-close:hover { background: #F3F4F6; color: #4B5563; }
:deep(.settlement-detail-dialog) { overflow: hidden; border-radius: 16px; }
:deep(.settlement-detail-dialog.el-dialog) { width: 700px !important; max-width: 90vw; max-height: 92vh; margin: 4vh auto !important; border-radius: 16px; }
:deep(.settlement-detail-dialog .el-dialog__header) { display: flex; margin: 0; padding: 20px 24px; border-bottom: 1px solid #E5E7EB; }
:deep(.settlement-detail-dialog .el-dialog__body) { overflow-y: auto; max-height: 65vh; padding: 24px; }
:deep(.settlement-detail-dialog .el-dialog__footer) { padding: 16px 24px; border-top: 1px solid #E5E7EB; }
.settlement-confirm-overlay { position: fixed; inset: 0; z-index: 3000; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(0,0,0,.5); box-sizing: border-box; }
.settlement-confirm-card { width: 90%; max-width: 440px; overflow: hidden; border-radius: 16px; background: #fff; box-sizing: border-box; }
.confirm-content { text-align: center; }
.confirm-content { padding: 24px; }
.confirm-icon { display: flex; align-items: center; justify-content: center; width: 60px; height: 60px; margin: 0 auto 16px; border-radius: 50%; background: #ECFDF5; color: var(--success,#059669); font-size: 24px; }
.confirm-title { margin-bottom: 8px; color: var(--text-primary,#111827); font-size: 18px; font-weight: 600; }
.confirm-desc { margin-bottom: 20px; color: var(--text-secondary,#4B5563); font-size: 14px; line-height: 1.7; }
.confirm-actions { display: flex; justify-content: center; gap: 12px; }
.confirm-cancel,.confirm-submit { min-width: 88px; height: 36px; padding: 0 16px; border-radius: 6px; font-size: 14px; cursor: pointer; }
.confirm-cancel { border: 1px solid var(--border,#E5E7EB); background: #fff; color: var(--text-secondary,#4B5563); }
.confirm-cancel:hover { border-color: var(--primary,#FF6B35); color: var(--primary,#FF6B35); }
.confirm-submit { border: 1px solid var(--primary,#FF6B35); background: var(--primary,#FF6B35); color: #fff; }
.confirm-submit:hover { border-color: var(--primary-dark,#E55A2B); background: var(--primary-dark,#E55A2B); }
@media (max-width: 800px) { .settle-summary,.detail-info-grid { grid-template-columns: 1fr; } }
</style>
