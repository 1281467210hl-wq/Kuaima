<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">老板管理</h1>
      <p class="page-desc">管理雇主信息、企业认证、经营数据</p>
    </div>

    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">总雇主</span>
          <div class="stat-card-icon"><i class="fas fa-building"></i></div>
        </div>
        <div class="stat-card-value">{{ formatStat(stats.total) }}</div>
        <div class="stat-card-change"><span class="text-muted">全部雇主</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">已认证</span>
          <div class="stat-card-icon green"><i class="fas fa-check-circle"></i></div>
        </div>
        <div class="stat-card-value">{{ formatStat(stats.certified) }}</div>
        <div class="stat-card-change"><span class="text-muted">认证率 {{ certifiedRate }}</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">本月新增</span>
          <div class="stat-card-icon blue"><i class="fas fa-user-plus"></i></div>
        </div>
        <div class="stat-card-value">{{ formatStat(stats.monthAdded) }}</div>
        <div class="stat-card-change"><span class="text-muted">按注册时间统计</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-card-header">
          <span class="stat-card-title">待审核</span>
          <div class="stat-card-icon yellow"><i class="fas fa-clock"></i></div>
        </div>
        <div class="stat-card-value">{{ formatStat(stats.pendingReview) }}</div>
        <div class="stat-card-change"><span class="text-muted">待审核中</span></div>
      </div>
    </div>

    <div class="card">
      <div class="filter-bar">
        <div class="filter-item"><span>关键词</span><el-input
          v-model="searchKeyword"
          placeholder="企业名称/联系人/ID"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        ><template #prefix><i class="fas fa-search"></i></template></el-input></div>
        <div class="filter-item"><span>认证状态</span><el-select v-model="certFilter" placeholder="全部" clearable style="width: 120px">
          <el-option label="已认证" value="APPROVED" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="未认证" value="UNVERIFIED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select></div>
        <div class="filter-item"><span>企业类型</span><el-select v-model="typeFilter" filterable allow-create default-first-option placeholder="全部" clearable style="width: 140px">
          <el-option v-for="item in industryOptions" :key="item" :label="item" :value="item" />
        </el-select></div>
        <button class="btn btn-primary btn-sm" @click="handleSearch"><i class="fas fa-search"></i> 查询</button>
        <button class="btn btn-outline btn-sm" @click="handleReset"><i class="fas fa-rotate-left"></i> 重置</button>
        <div class="filter-space"></div>
        <button class="btn btn-outline btn-sm export-button" :disabled="exporting" @click="handleExport"><i class="fas" :class="exporting ? 'fa-spinner fa-spin' : 'fa-download'"></i> {{ exporting ? '导出中...' : '导出数据' }}</button>
      </div>

      <el-table
        :data="tableData"
        stripe
        class="boss-table"
        :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }"
      >
        <el-table-column label="雇主ID" min-width="100" fixed="left">
          <template #default="{ row }">
            <span class="employer-id">{{ employerId(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="头像+名称" min-width="175" fixed="left">
          <template #default="{ row }">
            <div class="avatar-cell">
              <span class="mini-avatar" :style="{ background: avatarBackground(row) }">{{ avatarText(row) }}</span>
              <div class="boss-info">
                <span class="boss-name">{{ row.displayName }}</span>
                <span class="boss-id">ID: {{ employerId(row) }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="企业名称" min-width="195" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="company-name">{{ row.companyName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="行业类型" min-width="130">
          <template #default="{ row }">
            <span class="industry-tag" :class="industryClass(row.industry)">{{ row.industry || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="联系人" min-width="105">
          <template #default="{ row }">
            <div class="contact-info">
              <span class="contact-name">{{ row.contact || row.nickname || '-' }}</span>
              <span class="contact-phone">{{ row.contactPosition || row.contactPhone || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="手机号" min-width="115">
          <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
        </el-table-column>
        <el-table-column label="招工数" min-width="85" align="center">
          <template #default="{ row }">
            <span class="job-count">{{ formatNumber(row.jobsCount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="信用分" min-width="105" align="center">
          <template #default="{ row }">
            <span class="credit-tag" :class="creditClass(row.creditScore)">{{ creditText(row.creditScore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="余额" min-width="110" align="right">
          <template #default="{ row }">
            <span class="asset-num">{{ formatMoney(row.balance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="奖励金余额" min-width="110" align="right">
          <template #default="{ row }">
            <span class="asset-num">{{ formatMoney(row.rewardAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="积分余额" min-width="90" align="right">
          <template #default="{ row }">
            <span class="asset-num points">{{ formatNumber(row.points) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="认证状态" min-width="105">
          <template #default="{ row }">
            <span class="status-badge" :class="certClass(row)">
              <i :class="certIcon(row)"></i> {{ formatCertStatus(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="190" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
              <el-button v-if="isPending(row)" link type="primary" size="small" @click="handleAudit(row)">审核</el-button>
              <el-button v-if="isNormal(row.status)" v-permission="'permUser.bosses.freeze'" link type="danger" size="small" @click="handleFreeze(row)">冻结</el-button>
              <el-button v-else v-permission="'permUser.bosses.unfreeze'" link type="success" size="small" @click="handleUnfreeze(row)">解冻</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <div class="pagination-info">共 {{ formatNumber(total) }} 条记录</div>
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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { enterprisePass, enterpriseReject, freezeUser, listBosses, unfreezeUser } from '@/api/user'

const router = useRouter()

const industryOptions = ['电子厂', '物流', '餐饮', '仓储', '制造业', '汽车', '服务业', '农业']
const searchKeyword = ref('')
const certFilter = ref('')
const typeFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref([])
const exporting = ref(false)
const stats = ref({ total: 0, certified: 0, monthAdded: 0, pendingReview: 0 })

const certifiedRate = computed(() => {
  const totalNumber = Number(stats.value.total || 0)
  if (!totalNumber) return '0%'
  return `${((Number(stats.value.certified || 0) / totalNumber) * 100).toFixed(1)}%`
})

const rows = (result) => {
  const data = result?.data
  return Array.isArray(data) ? data : (data?.content || data?.list || [])
}

const totalOf = (result) => Number(result?.total ?? result?.data?.totalElements ?? result?.data?.total ?? 0)

const normalizeBoss = (item) => ({
  ...item,
  displayName: item.companyName || item.nickname || item.username || '未知企业'
})

const loadStats = async () => {
  try {
    const [allRes, approvedRes, pendingRes, monthRes] = await Promise.all([
      listBosses({ page: 0, size: 1 }),
      listBosses({ enterpriseStatus: 'APPROVED', page: 0, size: 1 }),
      listBosses({ enterpriseStatus: 'PENDING', page: 0, size: 1 }),
      listBosses({ page: 0, size: 1000 })
    ])
    const now = new Date()
    const monthList = rows(monthRes).filter((item) => {
      const created = new Date(item.timestamp || item.createdAt || 0)
      return created.getFullYear() === now.getFullYear() && created.getMonth() === now.getMonth()
    })
    stats.value = {
      total: totalOf(allRes),
      certified: totalOf(approvedRes),
      pendingReview: totalOf(pendingRes),
      monthAdded: monthList.length
    }
  } catch (e) {
    console.warn('[Bosses] 加载统计失败:', e)
  }
}

const loadData = async () => {
  try {
    const result = await listBosses({
      keyword: searchKeyword.value || undefined,
      enterpriseStatus: certFilter.value || undefined,
      industry: typeFilter.value || undefined,
      page: currentPage.value - 1,
      size: pageSize.value
    })
    tableData.value = rows(result).map(normalizeBoss)
    total.value = totalOf(result)
  } catch (e) {
    console.warn('[Bosses] 加载列表失败:', e)
    tableData.value = []
    total.value = 0
  }
}

const employerId = (row) => row?.companyCode || `B${String(row?.id ?? 0).padStart(7, '0')}`
const avatarText = (row) => String(row?.displayName || row?.companyName || '老').slice(0, 1)
const avatarBackground = (row) => {
  const palette = [
    'linear-gradient(135deg,#3B82F6,#2563EB)',
    'linear-gradient(135deg,#F59E0B,#D97706)',
    'linear-gradient(135deg,#EC4899,#BE185D)',
    'linear-gradient(135deg,#06B6D4,#0891B2)',
    'linear-gradient(135deg,#8B5CF6,#7C3AED)',
    'linear-gradient(135deg,#22C55E,#16A34A)'
  ]
  return palette[Number(row?.id || 0) % palette.length]
}
const industryClass = (industry) => {
  const mapping = {
    电子厂: 'industry-elec',
    物流: 'industry-logistics',
    餐饮: 'industry-catering',
    仓储: 'industry-warehouse',
    制造业: 'industry-manufacture',
    汽车: 'industry-auto',
    服务业: 'industry-service',
    农业: 'industry-agriculture'
  }
  return mapping[industry] || 'industry-manufacture'
}
const creditValue = (value) => Number(value ?? 0)
const creditClass = (value) => {
  const score = creditValue(value)
  if (score >= 90) return 'lv-excellent'
  if (score >= 75) return 'lv-good'
  if (score >= 60) return 'lv-normal'
  return 'lv-low'
}
const creditText = (value) => {
  const score = creditValue(value)
  const label = score >= 90 ? '优秀' : score >= 75 ? '良好' : score >= 60 ? '一般' : '较低'
  return `${score} ${label}`
}
const formatNumber = (value) => Number(value ?? 0).toLocaleString('zh-CN')
const formatStat = (value) => (value === '-' ? '-' : formatNumber(value))
const formatMoney = (value) => `¥${(Number(value ?? 0) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const maskPhone = (phone) => {
  const value = String(phone || '')
  return /^(\d{3})\d{4}(\d{4})$/.test(value) ? value.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : value || '-'
}
const formatDate = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleDateString('zh-CN')
}
const isApproved = (row) => row?.enterpriseStatus === 'APPROVED' || row?.certStatus === '已通过' || row?.certStatus === '已认证'
const isPending = (row) => row?.enterpriseStatus === 'PENDING' || row?.certStatus === '待审核'
const formatCertStatus = (row) => {
  if (isApproved(row)) return '已认证'
  if (isPending(row)) return '待审核'
  if (row?.enterpriseStatus === 'REJECTED' || row?.certStatus === '已拒绝') return '已拒绝'
  return '未认证'
}
const certClass = (row) => {
  if (isApproved(row)) return 'success'
  if (isPending(row)) return 'warning'
  if (row?.enterpriseStatus === 'REJECTED') return 'danger'
  return 'default'
}
const certIcon = (row) => {
  if (isApproved(row)) return 'fas fa-check-circle'
  if (isPending(row)) return 'fas fa-clock'
  return 'fas fa-minus-circle'
}
const formatStatus = (status) => {
  if (status === '正常' || status === 'NORMAL') return '正常'
  if (status === '冻结' || status === 'FROZEN') return '冻结'
  return status || '-'
}
const isNormal = (status) => status === '正常' || status === 'NORMAL' || status === 1

const openDetail = (row) => {
  router.push(`/admin/bosses/detail/${row.id}`)
}

const handleAudit = async (row) => {
  try {
    await ElMessageBox.confirm(`确定通过「${row.companyName || row.displayName}」的企业认证吗？`, '企业认证审核', {
      distinguishCancelAndClose: true,
      confirmButtonText: '通过',
      cancelButtonText: '拒绝',
      type: 'warning'
    })
    await enterprisePass(row.id)
    ElMessage.success('企业认证已通过')
  } catch (e) {
    if (e === 'cancel') {
      await enterpriseReject(row.id)
      ElMessage.success('企业认证已拒绝')
    } else if (e !== 'close') {
      console.warn('[Bosses] 审核失败:', e)
    }
  } finally {
    loadData()
    loadStats()
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadData()
}
const handleReset = () => {
  searchKeyword.value = ''
  certFilter.value = ''
  typeFilter.value = ''
  currentPage.value = 1
  loadData()
}

const csvCell = (value) => {
  if (value == null || value === '') return '""'
  let text = String(value)
  // 防止 Excel 将普通文本识别为公式。
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

const handleExport = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    const batchSize = 500
    const params = {
      keyword: searchKeyword.value || undefined,
      enterpriseStatus: certFilter.value || undefined,
      industry: typeFilter.value || undefined
    }
    const first = await listBosses({ ...params, page: 0, size: batchSize })
    const allBosses = [...rows(first)]
    const pageCount = Math.ceil(totalOf(first) / batchSize)
    for (let page = 1; page < pageCount; page += 1) {
      const next = await listBosses({ ...params, page, size: batchSize })
      allBosses.push(...rows(next))
    }

    const exportRows = [[
      '雇主ID', '名称', '企业名称', '联系人', '手机号', '行业类型', '招工数', '信用分',
      '余额（元）', '奖励金余额（元）', '积分余额', '认证状态'
    ]]
    allBosses.map(normalizeBoss).forEach(row => exportRows.push([
      employerId(row),
      row.displayName || '',
      row.companyName || '',
      row.contact || row.nickname || '',
      maskPhone(row.phone),
      row.industry || '',
      row.jobsCount ?? 0,
      row.creditScore ?? 0,
      (Number(row.balance ?? 0) / 100).toFixed(2),
      (Number(row.rewardAmount ?? 0) / 100).toFixed(2),
      row.points ?? 0,
      formatCertStatus(row)
    ]))

    const today = new Date().toLocaleDateString('zh-CN', { timeZone: 'Asia/Shanghai' }).replaceAll('/', '-')
    downloadCsv(exportRows, `老板数据_${today}.csv`)
    ElMessage.success(`已导出 ${allBosses.length} 条老板数据`)
  } catch (e) {
    console.warn('[Bosses] 导出失败:', e)
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

const handleFreeze = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要冻结雇主 ${row.displayName || row.id} 吗？`, '提示', { type: 'warning' })
    await freezeUser(row.id)
    ElMessage.success('冻结成功')
  } catch (e) {
    if (e !== 'cancel') console.warn('[Bosses] 冻结失败:', e)
  } finally {
    loadData()
    loadStats()
  }
}
const handleUnfreeze = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要解冻雇主 ${row.displayName || row.id} 吗？`, '提示', { type: 'warning' })
    await unfreezeUser(row.id)
    ElMessage.success('解冻成功')
  } catch (e) {
    if (e !== 'cancel') console.warn('[Bosses] 解冻失败:', e)
  } finally {
    loadData()
    loadStats()
  }
}

onMounted(() => {
  loadData()
  loadStats()
})
</script>

<style scoped>
.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-item { display: flex; align-items: center; gap: 6px; }
.filter-item > span { color: var(--text-secondary); font-size: 13px; white-space: nowrap; }
.filter-space { flex: 1; }
.boss-table :deep(.el-table__row) { height: 56px; }
.employer-id { font-family: monospace; color: var(--primary); }
.avatar-cell { display: flex; align-items: center; gap: 10px; }
.boss-info { display: flex; flex-direction: column; min-width: 0; }
.boss-name { overflow: hidden; font-weight: 500; font-size: 14px; color: var(--text-primary); text-overflow: ellipsis; white-space: nowrap; }
.boss-id { font-size: 12px; color: var(--text-muted); }
.mini-avatar { width: 36px; height: 36px; flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center; border-radius: 8px; color: #fff; font-size: 14px; font-weight: 600; }
.industry-tag { display: inline-block; padding: 3px 10px; border-radius: 6px; font-size: 12px; white-space: nowrap; }
.industry-elec { background: #EFF6FF; color: #2563EB; }
.industry-logistics { background: #FFFBEB; color: #F59E0B; }
.industry-catering { background: #FEF2F2; color: #EF4444; }
.industry-warehouse { background: #ECFDF5; color: #10B981; }
.industry-manufacture { background: #F3F4F6; color: #6B7280; }
.industry-auto { background: #F0F9FF; color: #0EA5E9; }
.industry-service { background: #FAF5FF; color: #8B5CF6; }
.industry-agriculture { background: #F0FDF4; color: #22C55E; }
.company-name { display: block; max-width: 180px; overflow: hidden; color: var(--text-secondary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.contact-info { display: flex; flex-direction: column; }
.contact-name { font-weight: 500; font-size: 13px; color: var(--text-primary); }
.contact-phone { font-size: 12px; color: var(--text-muted); }
.job-count { font-weight: 600; color: var(--text-primary); font-variant-numeric: tabular-nums; }
.asset-num { font-weight: 500; white-space: nowrap; color: var(--text-primary); font-variant-numeric: tabular-nums; }
.asset-num.points { color: var(--primary); }
.credit-tag { display: inline-block; padding: 3px 10px; border-radius: 12px; font-size: 11px; font-weight: 600; white-space: nowrap; }
.credit-tag.lv-excellent { background: #ECFDF5; color: #059669; }
.credit-tag.lv-good { background: #EFF6FF; color: #2563EB; }
.credit-tag.lv-normal { background: #FFFBEB; color: #D97706; }
.credit-tag.lv-low { background: #FEF2F2; color: #DC2626; }
.action-btns { display: flex; gap: 2px; white-space: nowrap; }
.pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; }
.pagination-info { color: var(--text-secondary); font-size: 13px; }

.status-badge { display: inline-flex; align-items: center; gap: 4px; }
.status-badge.success { color: #059669; }
.status-badge.warning { color: #D97706; }
.status-badge.danger { color: #DC2626; }
.status-badge.default { color: #6B7280; }
</style>
