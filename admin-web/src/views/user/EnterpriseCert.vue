<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">企业认证</h1>
      <p class="page-desc">企业资质认证审核管理</p>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-select v-model="statusFilter" placeholder="审核状态" style="width: 120px;">
          <el-option label="全部" value="" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
        <span class="filter-label">提交时间</span>
        <el-date-picker v-model="dateFrom" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width: 135px;" />
        <span class="date-separator">至</span>
        <el-date-picker v-model="dateTo" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" style="width: 135px;" />
        <el-input v-model="searchKeyword" placeholder="企业名称 / 信用代码搜索" clearable style="width: 240px;" prefix-icon="Search" />
        <button class="btn btn-primary btn-sm" @click="handleSearch"><i class="fas fa-search"></i> 查询</button>
        <button class="btn btn-outline btn-sm" @click="handleReset"><i class="fas fa-rotate-left"></i> 重置</button>
      </div>

      <el-table :data="tableData" stripe :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
        <el-table-column prop="id" label="用户ID" width="100" show-overflow-tooltip />
        <el-table-column label="企业名称" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="ent-cell">
              <span class="ent-avatar">{{ (row.companyName || row.username || '?').charAt(0) }}</span>
              <div>
                <div class="ent-name">{{ row.companyName || '-' }}</div>
                <div class="ent-code">{{ row.licenseNo || '-' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="法定代表人" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="legal-cell">
              <div class="legal-name">{{ row.legalRep || '-' }}</div>
              <div class="legal-phone">{{ row.contactPhone || row.phone || '-' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.certificationApplyTime || row.applyTime) }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.enterpriseStatus)" effect="light">{{ formatStatus(row.enterpriseStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-cell">
              <el-button v-permission="'permUser.cert.detail'" link type="primary" size="small" @click="showDetail(row)">详情</el-button>
              <template v-if="row.enterpriseStatus === 'PENDING' || row.enterpriseStatus === '待审核'">
                <el-button v-permission="'permUser.cert.approve'" link type="success" size="small" @click="handlePass(row)">通过</el-button>
                <el-button v-permission="'permUser.cert.reject'" link type="danger" size="small" @click="handleReject(row)">拒绝</el-button>
              </template>
              <el-button v-if="row.status === '正常' || row.status === 'NORMAL' || row.status === 1" v-permission="'permUser.cert.unfreeze'" link type="warning" size="small" @click="handleFreeze(row)">冻结</el-button>
              <el-button v-else v-permission="'permUser.cert.unfreeze'" link type="success" size="small" @click="handleUnfreeze(row)">解冻</el-button>
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

    <!-- 企业认证详情弹窗 -->
    <el-dialog v-model="detailVisible" width="720px" :close-on-click-modal="false">
      <template #header><span><i class="fas fa-id-card"></i> 企业认证详情</span></template>
      <div v-if="currentRow" class="cert-detail">
        <div class="panel-status" :class="statusBgClass(currentRow.enterpriseStatus)">
          <div class="status-icon" :class="statusIconClass(currentRow.enterpriseStatus)">
            <i :class="['fas', statusIconFa(currentRow.enterpriseStatus)]"></i>
          </div>
          <div class="status-text">{{ formatStatus(currentRow.enterpriseStatus) }}</div>
          <div class="status-hint">企业认证 · 提交于 {{ formatTime(currentRow.certificationApplyTime || currentRow.applyTime) }}</div>
        </div>

        <div class="detail-section-title">企业基本信息</div>
        <div class="detail-info-grid">
          <div class="detail-info-item full">
            <span class="detail-info-label">企业名称</span>
            <span class="detail-info-value">{{ currentRow.companyName || '-' }}</span>
          </div>
          <div class="detail-info-item">
            <span class="detail-info-label">统一社会信用代码</span>
            <span class="detail-info-value mono">{{ currentRow.licenseNo || '-' }}</span>
          </div>
          <div class="detail-info-item">
            <span class="detail-info-label">法定代表人</span>
            <span class="detail-info-value">{{ currentRow.legalRep || '-' }}</span>
          </div>
          <div class="detail-info-item">
            <span class="detail-info-label">联系电话</span>
            <span class="detail-info-value">{{ currentRow.contactPhone || currentRow.phone || '-' }}</span>
          </div>
          <div class="detail-info-item">
            <span class="detail-info-label">成立日期</span>
            <span class="detail-info-value">{{ formatDateOnly(currentRow.foundingDate) }}</span>
          </div>
          <div class="detail-info-item full">
            <span class="detail-info-label">经营范围</span>
            <span class="detail-info-value">{{ currentRow.businessScope || '-' }}</span>
          </div>
          <div class="detail-info-item">
            <span class="detail-info-label">法定联系人</span>
            <span class="detail-info-value">{{ currentRow.contact || currentRow.legalRep || '-' }}</span>
          </div>
        </div>

        <div class="detail-section-title">证件预览</div>
        <div class="document-preview" v-if="currentRow.licenseImageUrl">
          <div class="document-preview-header"><span><i class="fas fa-file-image"></i> 营业执照</span><a :href="currentRow.licenseImageUrl" target="_blank" rel="noreferrer"><i class="fas fa-eye"></i> 查看大图</a></div>
          <img class="license-preview" :src="currentRow.licenseImageUrl" alt="营业执照" />
        </div>
        <div v-else class="empty-document">暂无证件图片</div>

        <div class="action-buttons" v-if="currentRow.enterpriseStatus === 'PENDING' || currentRow.enterpriseStatus === '待审核'">
          <el-button v-permission="'permUser.cert.approve'" type="success" style="width:100%;" @click="handlePass(currentRow)">
            <i class="fas fa-check-circle"></i> 通过审核
          </el-button>
          <el-button v-permission="'permUser.cert.reject'" type="danger" style="width:100%;" @click="handleReject(currentRow)">
            <i class="fas fa-times-circle"></i> 拒绝审核
          </el-button>
        </div>
      </div>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listEnterpriseCertifications, enterprisePass, enterpriseReject, freezeUser, unfreezeUser } from '@/api/user'

const searchKeyword = ref('')
const statusFilter = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref([])
const detailVisible = ref(false)
const currentRow = ref(null)

const formatDateOnly = value => (value ? String(value).slice(0, 10) : '-')
const formatTime = (t) => {
  if (!t) return '-'
  const s = String(t).replace('T', ' ')
  return s.length > 16 ? s.substring(0, 16) : s
}

const formatStatus = (s) => {
  if (s === 'APPROVED' || s === '已通过') return '已通过'
  if (s === 'PENDING' || s === '待审核') return '待审核'
  if (s === 'REJECTED' || s === '已拒绝') return '已拒绝'
  return '未认证'
}

const statusTagType = (s) => {
  if (s === 'APPROVED' || s === '已通过') return 'success'
  if (s === 'PENDING' || s === '待审核') return 'warning'
  if (s === 'REJECTED' || s === '已拒绝') return 'danger'
  return 'info'
}

const statusBgClass = (s) => {
  if (s === 'APPROVED' || s === '已通过') return 'bg-success'
  if (s === 'PENDING' || s === '待审核') return 'bg-warning'
  if (s === 'REJECTED' || s === '已拒绝') return 'bg-danger'
  return 'bg-muted'
}

const statusIconClass = (s) => {
  if (s === 'APPROVED' || s === '已通过') return 'icon-success'
  if (s === 'PENDING' || s === '待审核') return 'icon-warning'
  if (s === 'REJECTED' || s === '已拒绝') return 'icon-danger'
  return 'icon-muted'
}

const statusIconFa = (s) => {
  if (s === 'APPROVED' || s === '已通过') return 'fa-check'
  if (s === 'PENDING' || s === '待审核') return 'fa-clock'
  if (s === 'REJECTED' || s === '已拒绝') return 'fa-times'
  return 'fa-question'
}

const loadData = async () => {
  try {
    const result = await listEnterpriseCertifications({
      status: statusFilter.value || undefined,
      keyword: searchKeyword.value || undefined,
      dateFrom: dateFrom.value || undefined,
      dateTo: dateTo.value || undefined,
      page: currentPage.value - 1,
      size: pageSize.value
    })
    const d = result.data
    tableData.value = Array.isArray(d) ? d : (d?.content || d?.list || [])
    total.value = result.total ?? d?.totalElements ?? d?.total ?? 0
  } catch (e) {
    console.warn('[EnterpriseCert] 加载失败:', e)
    tableData.value = []
    total.value = 0
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadData()
}

const handleReset = () => {
  searchKeyword.value = ''
  statusFilter.value = ''
  dateFrom.value = ''
  dateTo.value = ''
  currentPage.value = 1
  loadData()
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

const showDetail = (row) => {
  currentRow.value = row
  detailVisible.value = true
}

const handlePass = async (row) => {
  try {
    await ElMessageBox.confirm(`确定通过「${row.companyName || row.id}」的企业认证吗？`, '企业审核', { type: 'warning' })
    await enterprisePass(row.id)
    ElMessage.success('审核通过')
    detailVisible.value = false
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

const handleReject = async (row) => {
  try {
    await ElMessageBox.confirm(`确定拒绝「${row.companyName || row.id}」的企业认证吗？`, '企业审核', { type: 'warning' })
    await enterpriseReject(row.id)
    ElMessage.success('已拒绝')
    detailVisible.value = false
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

const handleFreeze = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要冻结「${row.companyName || row.id}」吗？`, '提示', { type: 'warning' })
    await freezeUser(row.id)
    ElMessage.success('冻结成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

const handleUnfreeze = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要解冻「${row.companyName || row.id}」吗？`, '提示', { type: 'warning' })
    await unfreezeUser(row.id)
    ElMessage.success('解冻成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

onMounted(loadData)
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-label, .date-separator { font-size: 13px; color: var(--text-secondary); }

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

/* 企业名称单元格 */
.ent-cell { display: flex; align-items: center; gap: 8px; }
.ent-avatar {
  width: 32px; height: 32px; border-radius: 8px;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600; color: #fff;
  background: linear-gradient(135deg, #10B981, #059669);
  flex-shrink: 0;
}
.ent-name { font-weight: 500; font-size: 13px; }
.ent-code { font-size: 12px; color: var(--text-muted); font-family: monospace; }

/* 法定代表人单元格 */
.legal-cell { line-height: 1.5; }
.legal-name { font-size: 13px; font-weight: 500; }
.legal-phone { font-size: 12px; color: var(--text-muted); }

/* 详情弹窗 */
.cert-detail { display: flex; flex-direction: column; gap: 16px; }
.panel-status {
  text-align: center; padding: 16px; border-radius: 12px;
}
.panel-status .status-icon {
  width: 48px; height: 48px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  margin: 0 auto 8px; font-size: 20px;
}
.panel-status .status-text { font-size: 16px; font-weight: 600; }
.panel-status .status-hint { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
.bg-success { background: #ECFDF5; }
.bg-warning { background: #FFFBEB; }
.bg-danger { background: #FEF2F2; }
.bg-muted { background: #F3F4F6; }
.icon-success { background: #D1FAE5; color: var(--success); }
.icon-warning { background: #FEF3C7; color: var(--warning); }
.icon-danger { background: #FECACA; color: var(--danger); }
.icon-muted { background: #E5E7EB; color: var(--text-muted); }

.detail-section-title {
  font-size: 14px; font-weight: 600;
  padding-bottom: 8px; border-bottom: 1px solid var(--border);
}
.detail-info-grid {
  display: grid; grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.detail-info-item { display: flex; flex-direction: column; gap: 4px; }
.detail-info-item.full { grid-column: 1 / -1; }
.detail-info-label { font-size: 12px; color: var(--text-muted); }
.detail-info-value { font-size: 14px; font-weight: 500; }
.detail-info-value.mono { font-family: monospace; }
.document-preview { border: 1px solid var(--border); border-radius: 8px; padding: 12px; }
.document-preview-header { display: flex; justify-content: space-between; margin-bottom: 10px; font-size: 13px; }
.document-preview-header a { color: var(--primary); text-decoration: none; }
.license-preview { display: block; max-width: 100%; max-height: 260px; margin: 0 auto; object-fit: contain; }
.empty-document { color: var(--text-muted); font-size: 13px; }

.action-buttons {
  display: flex; flex-direction: column; gap: 10px;
  padding-top: 16px; border-top: 1px solid var(--border);
}

.action-cell {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
}
.action-cell .el-button {
  margin-left: 0;
  margin-right: 8px;
  padding: 0;
}
.action-cell .el-button:last-child {
  margin-right: 0;
}
</style>
