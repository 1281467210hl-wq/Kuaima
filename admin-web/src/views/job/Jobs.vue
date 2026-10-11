<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">招工管理</h1>
      <p class="page-desc">管理平台所有招工信息、状态、数据</p>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="searchKeyword" placeholder="工种/雇主名称" clearable style="width: 200px;" prefix-icon="Search" />
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 120px;">
          <el-option label="招聘中" value="招工中" />
          <el-option label="已招满" value="招工结束" />
          <el-option label="已关闭" value="取消招工" />
          <el-option label="待审核" value="待审核" />
          <el-option label="待结算" value="待结算" />
          <el-option label="已完成" value="已完成" />
        </el-select>
        <el-select v-model="typeFilter" placeholder="工种类型" clearable style="width: 130px;">
          <el-option label="日结" value="daily" />
          <el-option label="压薪日结" value="heldBack" />
          <el-option label="月结" value="month" />
        </el-select>
        <div class="date-picker-wrap" style="width: 240px;">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 100%;" />
        </div>
        <button class="btn btn-primary btn-sm" @click="handleSearch"><i class="fas fa-search"></i> 查询</button>
        <button class="btn btn-outline btn-sm" @click="handleReset"><i class="fas fa-rotate-left"></i> 重置</button>
        <button class="btn btn-outline btn-sm" style="margin-left: auto;"><i class="fas fa-download"></i> 导出</button>
      </div>

      <el-table :data="jobsData" stripe :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
        <el-table-column label="招工ID" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="job-id">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工种" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="job-info-cell">
              <span class="job-name">{{ row.postion || row.orderTitle || '-' }}</span>
              <span :class="['job-type-tag', typeClass(row.type)]">{{ formatType(row.type) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="雇主" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="boss-cell">
              <span class="boss-logo" :style="{ background: bossColor(row.employerName) }">{{ (row.employerName || '?').charAt(0) }}</span>
              <span class="boss-name-text">{{ row.employerName || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="需求人数/已招" width="140">
          <template #default="{ row }">
            <div class="demand-cell">
              <span class="demand-text">{{ row.orderNum || 0 }} / {{ row.currentApply || 0 }}</span>
              <div class="demand-progress">
                <div class="demand-progress-fill" :style="{ width: progressPct(row) + '%' }"></div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="工价" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="wage-cell">{{ row.salary ? '¥' + row.salary + '/天' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工作地点" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="location-cell"><i class="fas fa-map-marker-alt"></i>{{ row.address || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.timestamp) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span :class="['status-badge', statusClass(row.orderStatus)]">{{ formatStatus(row.orderStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button link type="primary" size="small" @click="showDetail(row)">查看</el-button>
              <router-link v-permission="'permJob.jobs.applicants'" :to="`/admin/jobs/applicants/${row.id}`">
                <el-button link type="primary" size="small"><i class="fas fa-users"></i> 报名人员</el-button>
              </router-link>
              <router-link v-permission="'permJob.jobs.edit'" :to="`/admin/jobs/edit/${row.id}`">
                <el-button link type="primary" size="small">编辑</el-button>
              </router-link>
              <el-button v-if="row.orderStatus === '招工中'" v-permission="'permJob.jobs.toggle'" link type="danger" size="small" @click="handleClose(row)">关闭</el-button>
              <el-button v-else-if="row.orderStatus === '取消招工'" v-permission="'permJob.jobs.toggle'" link type="success" size="small" @click="handleOpen(row)">开启</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

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

    <!-- 招工详情弹窗 -->
    <el-dialog v-model="detailVisible" title="招工详情" width="600px" :close-on-click-modal="false">
      <div v-if="currentJob" class="cert-detail">
        <div class="detail-section">
          <div class="detail-section-title">基本信息</div>
          <div class="detail-grid">
            <div class="detail-item"><span class="label">招工ID</span><span class="value" style="color:var(--primary);font-family:monospace;">{{ currentJob.id }}</span></div>
            <div class="detail-item"><span class="label">工种</span><span class="value">{{ currentJob.postion || currentJob.orderTitle || '-' }}</span></div>
            <div class="detail-item"><span class="label">工种类型</span><span class="value">{{ formatType(currentJob.type) }}</span></div>
            <div class="detail-item"><span class="label">工价</span><span class="value" style="color:var(--primary);font-weight:600;">{{ currentJob.salary ? '¥' + currentJob.salary + '/天' : '-' }}</span></div>
            <div class="detail-item"><span class="label">工作地点</span><span class="value">{{ currentJob.address || '-' }}</span></div>
            <div class="detail-item"><span class="label">发布时间</span><span class="value">{{ formatTime(currentJob.timestamp) }}</span></div>
            <div class="detail-item"><span class="label">状态</span><span class="value"><span :class="['status-badge', statusClass(currentJob.orderStatus)]">{{ formatStatus(currentJob.orderStatus) }}</span></span></div>
            <div class="detail-item"><span class="label">需求人数</span><span class="value">{{ currentJob.orderNum || 0 }} 人</span></div>
          </div>
        </div>
        <div class="detail-section">
          <div class="detail-section-title">雇主信息</div>
          <div class="detail-grid">
            <div class="detail-item"><span class="label">雇主名称</span><span class="value">{{ currentJob.employerName || '-' }}</span></div>
            <div class="detail-item"><span class="label">雇主类型</span><span class="value">企业</span></div>
          </div>
        </div>
        <div class="detail-section">
          <div class="detail-section-title">招聘进度</div>
          <div class="detail-grid">
            <div class="detail-item"><span class="label">已报名</span><span class="value" style="color:var(--primary);font-weight:600;">{{ currentJob.currentApply || 0 }} 人</span></div>
            <div class="detail-item"><span class="label">需求人数</span><span class="value">{{ currentJob.orderNum || 0 }} 人</span></div>
            <div class="detail-item detail-item-full">
              <span class="label">保险信息</span>
              <span class="value insurance-value" :class="{ insured: hasInsurance(currentJob) }">
                <i class="fas fa-shield-halved"></i>{{ insuranceText(currentJob) }}
              </span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="detailVisible = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getJob, listJobs, closeJob, openJob } from '@/api/job'

const searchKeyword = ref('')
const statusFilter = ref('')
const typeFilter = ref('')
const dateRange = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const jobsData = ref([])
const detailVisible = ref(false)
const currentJob = ref(null)

const formatTime = (t) => {
  if (!t) return '-'
  const s = String(t).replace('T', ' ')
  return s.length > 16 ? s.substring(0, 16) : s
}

const formatType = (t) => {
  if (t === 'daily') return '日结'
  if (t === 'heldBack') return '压薪日结'
  if (t === 'month') return '月结'
  return t || '-'
}

const typeClass = (t) => {
  if (t === 'daily') return 'type-electronics'
  if (t === 'heldBack') return 'type-manufacture'
  if (t === 'month') return 'type-logistics'
  return 'type-service'
}

const formatStatus = (s) => {
  if (s === '招工中') return '招聘中'
  if (s === '招工结束') return '已招满'
  if (s === '取消招工') return '已关闭'
  return s || '未知'
}

const statusClass = (s) => {
  if (s === '招工中') return 'success'
  if (s === '招工结束') return 'info'
  if (s === '取消招工') return 'default'
  if (s === '待审核') return 'warning'
  if (s === '已完成') return 'success'
  if (s === '待结算') return 'warning'
  if (s === '审核拒绝') return 'danger'
  return 'default'
}

const progressPct = (row) => {
  const demand = row.orderNum || 0
  const hired = row.currentApply || 0
  if (demand === 0) return 0
  return Math.min(100, Math.round((hired / demand) * 100))
}

const bossColors = ['#3B82F6', '#06B6D4', '#F59E0B', '#10B981', '#EC4899', '#8B5CF6', '#6366F1', '#F97316', '#0EA5E9']
const bossColor = (name) => {
  if (!name) return bossColors[0]
  return bossColors[name.charCodeAt(0) % bossColors.length]
}

const loadJobs = async () => {
  try {
    const res = await listJobs({
      type: typeFilter.value || undefined,
      status: statusFilter.value || undefined,
      title: searchKeyword.value || undefined,
      page: currentPage.value - 1,
      size: pageSize.value,
    })
    const d = res.data
    const list = Array.isArray(d) ? d : (d?.content || d?.list || [])
    total.value = res.total ?? d?.totalElements ?? d?.total ?? list.length
    jobsData.value = list
  } catch (err) {
    console.warn('[Jobs] API 加载失败:', err.message)
    jobsData.value = []
    total.value = 0
    ElMessage.error('加载招工列表失败')
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadJobs()
}

const handleReset = () => {
  searchKeyword.value = ''
  typeFilter.value = ''
  statusFilter.value = ''
  dateRange.value = []
  handleSearch()
}

const onSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadJobs()
}

const onPageChange = (page) => {
  currentPage.value = page
  loadJobs()
}

const showDetail = async (row) => {
  currentJob.value = row
  detailVisible.value = true
  try {
    const res = await getJob(row.id)
    currentJob.value = { ...row, ...(res?.data || {}) }
  } catch (e) {
    console.warn('[Jobs] 加载招工详情失败:', e)
  }
}

const hasInsurance = (job) => !!job?.insurance
const insuranceText = (job) => {
  const insurance = job?.insurance
  if (!insurance) return '未选择保险'
  const type = insurance.type || '意外险'
  const amount = Number(insurance.amount || 0)
  if (amount <= 0) return type
  const wan = amount / 10000
  const wanText = Number.isInteger(wan) ? wan : wan.toFixed(1)
  const count = Number(insurance.count || 1)
  return `${type} · ${wanText}万意外保障${count > 1 ? `（${count} 份保单）` : ''}`
}

const handleClose = async (row) => {
  try {
    await ElMessageBox.confirm(`确定关闭「${row.postion || row.orderTitle || row.id}」的招工吗？`, '关闭招工', { type: 'warning' })
    await closeJob(row.id)
    ElMessage.success('已关闭')
    loadJobs()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

const handleOpen = async (row) => {
  try {
    await ElMessageBox.confirm(`确定重新开启「${row.postion || row.orderTitle || row.id}」的招工吗？`, '开启招工', { type: 'warning' })
    await openJob(row.id)
    ElMessage.success('已开启')
    loadJobs()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

onMounted(loadJobs)
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

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

/* 招工ID */
.job-id { font-family: monospace; color: var(--primary); }

/* 工种单元格 */
.job-info-cell { display: flex; flex-direction: column; gap: 4px; }
.job-name { font-weight: 500; color: var(--text-primary); }
.job-type-tag {
  display: inline-block; padding: 2px 8px; border-radius: 3px;
  font-size: 11px; font-weight: 500; width: fit-content;
}
.type-electronics { background: #EFF6FF; color: #2563EB; }
.type-manufacture { background: #FEF3C7; color: #D97706; }
.type-logistics { background: #D1FAE5; color: #059669; }
.type-warehouse { background: #DBEAFE; color: #0369A1; }
.type-catering { background: #FCE7F3; color: #BE185D; }
.type-auto { background: #E0E7FF; color: #4338CA; }
.type-service { background: #F3E8FF; color: #7C3AED; }

/* 雇主单元格 */
.boss-cell { display: flex; align-items: center; gap: 8px; }
.boss-logo {
  width: 32px; height: 32px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 600; color: #fff; flex-shrink: 0;
}
.boss-name-text { font-size: 13px; }

/* 需求人数/已招 */
.demand-cell { display: flex; flex-direction: column; gap: 4px; }
.demand-text { font-size: 13px; font-weight: 500; }
.demand-progress {
  width: 100px; height: 4px; background: var(--border, #E5E7EB);
  border-radius: 2px; overflow: hidden;
}
.demand-progress-fill { height: 100%; background: var(--primary); border-radius: 2px; }

/* 工价 */
.wage-cell { color: var(--primary); font-weight: 600; }

/* 工作地点 */
.location-cell { font-size: 13px; }
.location-cell i { color: var(--text-muted, #9CA3AF); margin-right: 4px; }

/* 操作按钮 */
.action-btns { display: flex; gap: 4px; flex-wrap: nowrap; white-space: nowrap; }

/* 详情弹窗 */
.cert-detail { display: flex; flex-direction: column; gap: 16px; }
.detail-section-title {
  font-size: 13px; font-weight: 600; color: var(--text-secondary);
  margin-bottom: 10px; padding-bottom: 8px; border-bottom: 1px solid var(--border);
}
.detail-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px 20px; }
.detail-item { display: flex; flex-direction: column; gap: 2px; }
.detail-item-full { grid-column: 1 / -1; }
.detail-item .label { font-size: 12px; color: var(--text-muted); }
.detail-item .value { font-size: 14px; color: var(--text-primary); font-weight: 500; }
.insurance-value { display: inline-flex; align-items: center; color: var(--text-muted); }
.insurance-value i { margin-right: 6px; font-size: 13px; }
.insurance-value.insured { color: #16A34A; font-weight: 600; }
</style>
