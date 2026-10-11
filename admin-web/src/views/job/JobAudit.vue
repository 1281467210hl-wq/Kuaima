<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">招工审核</h1>
      <p class="page-desc">审核雇主发布的招工信息，确保内容真实合规</p>
    </div>

    <!-- 审核列表 -->
    <div class="card">
      <div class="filter-bar">
        <el-input v-model="searchKeyword" placeholder="搜索招工名称/雇主" clearable style="width: 240px;" prefix-icon="Search" />
        <el-select v-model="statusFilter" placeholder="审核状态" clearable style="width: 120px;">
          <el-option label="全部" value="ALL" />
          <el-option label="待审核" value="待审核" />
          <el-option label="已通过" value="招工中" />
          <el-option label="已拒绝" value="审核拒绝" />
        </el-select>
        <div class="filter-date"><span>提交日期</span><el-date-picker v-model="submitDateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 280px;" /></div>
        <button class="btn btn-primary btn-sm" @click="handleSearch"><i class="fas fa-search"></i> 查询</button>
        <button class="btn btn-outline btn-sm" @click="handleReset"><i class="fas fa-rotate-left"></i> 重置</button>
        <button class="btn btn-outline btn-sm export-button" @click="handleExport"><i class="fas fa-download"></i> 导出数据</button>
      </div>

      <el-table :data="auditData" stripe :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
        <el-table-column prop="id" label="审核ID" show-overflow-tooltip />
        <el-table-column label="行业 / 工种" show-overflow-tooltip>
          <template #default="{ row }">{{ row.jobCategoryName || row.postion || row.type || '-' }}</template>
        </el-table-column>
        <el-table-column label="雇主" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="employer-cell">
              <span class="employer-avatar" :style="{ background: employerColor(row.employer) }">{{ employerInitial(row.employer) }}</span>
              <span class="employer-name">{{ row.employer || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="time" label="提交时间" show-overflow-tooltip />
        <el-table-column label="审核状态" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="['status-badge', row.statusClass]">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="auditBy" label="审核人" show-overflow-tooltip>
          <template #default="{ row }">{{ row.auditBy || '-' }}</template>
        </el-table-column>
        <el-table-column prop="auditTime" label="审核时间" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.auditTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'permJob.audit.detail'" link type="primary" size="small" @click="handleDetail(row)">查看详情</el-button>
            <template v-if="isAuditable(row)">
              <el-button v-permission="row.status === '审核拒绝' ? 'permJob.audit.reaudit' : 'permJob.audit.approve'" link type="success" size="small" @click="handleApprove(row)">{{ row.status === '审核拒绝' ? '重新审核' : '通过' }}</el-button>
              <el-button v-permission="'permJob.audit.reject'" link type="danger" size="small" @click="handleReject(row)">拒绝</el-button>
            </template>
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

    <!-- 审核详情弹窗 -->
    <el-dialog v-model="detailVisible" width="780px" class="audit-detail-dialog" :show-close="true">
      <template #header><div class="audit-dialog-title">招工审核详情 <span v-if="currentItem" :class="['status-badge', currentItem.statusClass]">● {{ currentItem.status }}</span></div></template>
      <div v-if="currentItem">
        <div v-if="isAuditable(currentItem)" class="audit-action-bar">
          <div><div class="action-title">{{ currentItem.status === '审核拒绝' ? '重新审核' : '招工审核' }} <span class="risk-tag">低风险</span></div><div class="action-desc">请仔细审核以下招工信息，确认无误后通过审核</div></div>
          <div class="action-buttons"><button v-permission="'permJob.audit.approve'" class="btn btn-success" @click="confirmApprove"><i class="fas fa-check-circle"></i> 一键通过</button><button v-permission="'permJob.audit.reject'" class="btn btn-danger" @click="confirmReject"><i class="fas fa-times-circle"></i> 拒绝</button></div>
        </div>
        <div class="detail-section-title">招工信息预览</div>
        <div class="audit-preview-grid">
        <div class="audit-preview-box"><div class="audit-preview-title"><i class="fas fa-file-alt"></i> 招工详情</div><el-descriptions :column="1">
          <el-descriptions-item label="工种名称">{{ currentItem.postion || currentItem.orderTitle || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工种类型">{{ currentItem.industryName || currentItem.jobCategoryName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工价">{{ formatWage(currentItem) }}</el-descriptions-item>
          <el-descriptions-item label="需求人数">{{ currentItem.count }} 人</el-descriptions-item>
          <el-descriptions-item label="地点">{{ currentItem.location }}</el-descriptions-item>
          <el-descriptions-item label="工作时间">{{ formatTime(currentItem.startTime) }} - {{ formatTime(currentItem.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="结算方式">{{ settlementText(currentItem.type) }}</el-descriptions-item>
          <el-descriptions-item label="性别要求">{{ genderText(currentItem.gender) }}</el-descriptions-item>
        </el-descriptions></div>
        <div class="audit-preview-box"><div class="audit-preview-title"><i class="fas fa-building"></i> 雇主信息</div><el-descriptions :column="1">
          <el-descriptions-item label="雇主名称">{{ currentItem.employer }}</el-descriptions-item><el-descriptions-item label="联系人">{{ currentItem.contact || '-' }}</el-descriptions-item><el-descriptions-item label="联系电话">{{ currentItem.contactPhone || '-' }}</el-descriptions-item><el-descriptions-item label="企业认证">{{ certificationText(currentItem.enterpriseStatus) }}</el-descriptions-item><el-descriptions-item label="信用分">{{ currentItem.employerCreditScore ?? '-' }}</el-descriptions-item><el-descriptions-item label="历史招工">{{ currentItem.historyJobs ?? '-' }} 条</el-descriptions-item><el-descriptions-item label="投诉次数">{{ currentItem.complaintCount ?? 0 }} 次</el-descriptions-item><el-descriptions-item label="通过率">{{ currentItem.passRate == null ? '-' : `${currentItem.passRate}%` }}</el-descriptions-item>
        </el-descriptions></div></div>
        <div class="detail-section-title">招工描述</div><div class="audit-desc-box">{{ currentItem.description || '-' }}</div>
        <div class="detail-section-title">福利待遇</div><div class="benefit-list"><span v-for="tag in benefitTags(currentItem)" :key="tag"><i class="fas fa-check-circle"></i> {{ tag }}</span><em v-if="!benefitTags(currentItem).length">暂无福利待遇</em></div>
        <div class="detail-section-title">提交材料</div>
        <div v-if="materialItems(currentItem).length" class="materials-list">
          <div v-for="material in materialItems(currentItem)" :key="material.key" class="material-item">
            <span class="material-icon"><i :class="material.icon"></i></span>
            <span class="material-info"><strong>{{ material.name }}</strong><small v-if="material.time">上传时间：{{ formatTime(material.time) }}</small></span>
            <a v-if="material.url" class="btn btn-outline btn-sm" :href="material.url" target="_blank" rel="noopener"><i class="fas fa-eye"></i> 查看</a>
          </div>
        </div>
        <div v-else class="materials-box">暂无提交材料</div>
        <el-descriptions class="audit-extra" :column="2" border><el-descriptions-item label="提交时间">{{ formatTime(currentItem.time) }}</el-descriptions-item><el-descriptions-item label="审核人">{{ currentItem.auditBy || '-' }}</el-descriptions-item><el-descriptions-item label="审核时间">{{ formatTime(currentItem.auditTime) }}</el-descriptions-item></el-descriptions>

        <div style="margin-bottom: 16px;">
          <div style="font-weight: 600; margin-bottom: 8px;">审核备注</div>
          <el-input type="textarea" v-model="auditRemark" placeholder="请输入审核备注（可选）" :rows="3" />
        </div>
        <div style="margin-bottom: 4px;">
          <div style="font-weight: 600; margin-bottom: 8px;">驳回原因（拒绝时必填）</div>
          <el-input type="textarea" v-model="rejectDesc" placeholder="若拒绝该招工信息，请填写驳回原因" :rows="2" />
        </div>
      </div>
      
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <template v-if="isAuditable(currentItem)">
          <el-button v-permission="'permJob.audit.reject'" type="danger" @click="confirmReject">拒绝</el-button>
          <el-button v-permission="'permJob.audit.approve'" type="primary" @click="confirmApprove">通过审核</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 拒绝原因弹窗 -->
    <el-dialog v-model="rejectVisible" title="拒绝原因" width="450px">
      <el-form>
        <el-form-item label="拒绝原因">
          <el-select v-model="rejectReason" placeholder="请选择拒绝原因" style="width: 100%;">
            <el-option label="信息不完整" value="incomplete" />
            <el-option label="价格严重偏低" value="low_price" />
            <el-option label="内容违规" value="violation" />
            <el-option label="疑似虚假招工" value="fake" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="补充说明">
          <el-input type="textarea" v-model="rejectDesc" :rows="3" placeholder="请输入补充说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmRejectSubmit">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listJobs, exportJobs, getJob, auditJobPass, auditJobReject } from '@/api/job'
import { getUser } from '@/api/user'

const searchKeyword = ref('')
const statusFilter = ref('ALL')
const submitDateRange = ref([])
const auditRemark = ref('')
const rejectReason = ref('')
const rejectDesc = ref('')
const detailVisible = ref(false)
const rejectVisible = ref(false)
const currentItem = ref(null)

const auditData = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const onSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadAuditData()
}

const onPageChange = (page) => {
  currentPage.value = page
  loadAuditData()
}

const statusClassMap = {
  '待审核': 'warning',
  '已通过': 'success',
  '已拒绝': 'danger',
  '招工中': 'info',
  '进行中': 'info',
  '已完成': 'success',
  '已取消': 'default',
  '审核拒绝': 'danger',
}

const isAuditable = (item) => item?.status === '待审核' || item?.status === '审核拒绝'

const formatTime = (t) => {
  if (!t) return '-'
  // fastjson2 序列化 Date 为毫秒时间戳（数字）
  if (typeof t === 'number') {
    const d = new Date(t)
    if (isNaN(d.getTime())) return '-'
    const Y = d.getFullYear()
    const M = String(d.getMonth() + 1).padStart(2, '0')
    const D = String(d.getDate()).padStart(2, '0')
    const h = String(d.getHours()).padStart(2, '0')
    const m = String(d.getMinutes()).padStart(2, '0')
    return `${Y}-${M}-${D} ${h}:${m}`
  }
  const s = String(t).replace('T', ' ')
  return s.length > 16 ? s.substring(0, 16) : s
}

const genderText = (value) => ({
  male: '男', M: '男', MAN: '男', 男: '男',
  female: '女', F: '女', WOMAN: '女', 女: '女',
  all: '不限', ALL: '不限', none: '不限', unlimited: '不限', 不限: '不限',
}[value] || value || '不限')

const settlementText = (value) => ({
  daily: '日结', DAILY: '日结',
  heldBack: '压薪日结', HELD_BACK: '压薪日结', heldback: '压薪日结',
  month: '月结', MONTH: '月结', monthly: '月结',
  day: '日结', week: '周结', weekly: '周结',
  日结: '日结', 压薪日结: '压薪日结', 月结: '月结',
}[value] || value || '-')

const formatWage = (item) => {
  if (item?.price == null || item.price === '') return '-'
  const unit = settlementText(item.type) === '月结' ? '元/月' : '元/天'
  return `${item.price} ${unit}`
}

const ageText = (value) => {
  if (!value) return '不限'
  const text = String(value)
  if (/\d+\s*[~至-]\s*\d+/.test(text) || /\d+岁/.test(text)) return text
  return ({ none: '不限', all: '不限', unlimited: '不限', NO_REQUIREMENT: '不限', 不限: '不限' }[text] || text)
}

const certificationText = (value) => ({
  APPROVED: '已认证', PENDING: '认证中', REJECTED: '认证未通过', UNVERIFIED: '未认证',
  已通过: '已认证', 已认证: '已认证',
}[value] || value || '-')

const benefitNames = ['包吃住', '包餐', '包工作餐', '中午包饭', '晚上包饭', '交通补贴', '全勤奖', '保险', '加班补助', '提供饮水', '免费培训', '工作轻松', '环境舒适', '有空调', '有风扇', '室内工作', '当日结清', '月结']
const benefitTags = (item) => {
  const explicit = item?.benefits
  const source = explicit || item?.orderRemark
  const values = String(source || '').split(/[,，、;；]/).map(v => v.trim()).filter(Boolean)
  const matched = values.filter(value => benefitNames.some(name => value === name || value.includes(name)))
  if (matched.length) return [...new Set(matched)]
  return explicit ? values : []
}

const materialItems = (item) => {
  const raw = item?.submittedMaterials ?? item?.materials
  if (!raw) return []
  let values = raw
  if (typeof raw === 'string') {
    try { values = JSON.parse(raw) } catch { values = raw.split(/[,，、\n]/).map(v => v.trim()).filter(Boolean) }
  }
  if (!Array.isArray(values)) values = [values]
  return values.map((value, index) => {
    const entry = typeof value === 'object' && value !== null ? value : { name: String(value) }
    const name = entry.name || entry.fileName || entry.title || entry.url || `材料${index + 1}`
    const ext = String(name).split('.').pop().toLowerCase()
    const icon = ['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(ext) ? 'fas fa-image' : (ext === 'pdf' ? 'fas fa-file-pdf' : 'fas fa-file-alt')
    return { key: `${name}-${index}`, name, url: entry.url || entry.fileUrl || entry.filePath, time: entry.time || entry.uploadTime || entry.createdAt, icon }
  })
}

const employerInitial = (value) => String(value || '?').trim().charAt(0) || '?'
const employerColor = (value) => {
  const palette = ['#3B82F6', '#46A6C6', '#E8942B', '#4CAF83', '#8B5CF6', '#EC4899']
  const text = String(value || '')
  return palette[text.length % palette.length]
}

const normalizeAudit = (item) => {
  const statusVal = item.orderStatus ?? item.status ?? '未知'
  return {
    id: item.id,
    type: item.type ?? item.postion,
    postion: item.postion,
    jobCategoryName: item.jobCategoryName,
    industryName: item.industryName,
    employer: item.employerName ?? item.employer ?? '未知雇主',
    price: item.salary ?? item.price,
    count: item.orderNum ?? item.count,
    location: item.address ?? item.location,
    time: item.timestamp ?? item.time,
    startTime: item.startTime,
    endTime: item.endTime,
    status: statusVal,
    statusClass: statusClassMap[statusVal] ?? 'default',
    auditBy: item.auditBy,
    auditTime: item.auditTime,
    createBy: item.createBy,
    gender: item.gender,
    experience: item.experience,
    description: item.orderContent ?? item.description,
    contact: item.contact,
    contactPhone: item.contactPhone,
    enterpriseStatus: item.enterpriseStatus,
  }
}

const loadAuditData = async () => {
  try {
    const res = await listJobs({
      type: undefined,
      status: statusFilter.value === 'ALL' ? undefined : (statusFilter.value || undefined),
      title: searchKeyword.value || undefined,
      startDate: submitDateRange.value?.[0] || undefined,
      endDate: submitDateRange.value?.[1] || undefined,
      page: currentPage.value - 1,
      size: pageSize.value,
    })
    const d = res.data
    const list = Array.isArray(d) ? d : (d?.content || d?.list || [])
    auditData.value = list.map(normalizeAudit)
    total.value = res.total ?? d?.total ?? list.length
  } catch (err) {
    console.warn('[JobAudit] API 加载失败:', err.message)
    auditData.value = []
    total.value = 0
    ElMessage.error('加载审核数据失败')
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadAuditData()
}

const handleExport = async () => {
  try {
    const blob = await exportJobs({
      status: statusFilter.value === 'ALL' ? undefined : statusFilter.value || undefined,
      title: searchKeyword.value || undefined,
      startDate: submitDateRange.value?.[0] || undefined,
      endDate: submitDateRange.value?.[1] || undefined
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `招工审核_${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

const handleReset = () => {
  searchKeyword.value = ''
  statusFilter.value = 'ALL'
  submitDateRange.value = []
  currentPage.value = 1
  loadAuditData()
}

const loadDetail = async (row) => {
  const detailRes = await getJob(row.id)
  const detail = detailRes.data || {}
  let employer = {}
  if (detail.createBy) {
    try {
      const employerRes = await getUser(detail.createBy)
      employer = employerRes.data || {}
    } catch (err) {
      console.warn('[JobAudit] 雇主信息加载失败:', err.message)
    }
  }
  return {
    ...row,
    ...normalizeAudit({ ...row, ...detail }),
    ...detail,
    employer: employer.companyName || row.employer,
    contact: employer.contact || employer.realName || employer.nickname || employer.username,
    contactPhone: employer.contactPhone || employer.phone,
    enterpriseStatus: employer.enterpriseStatus,
  }
}

const openDetail = async (row) => {
  currentItem.value = row
  auditRemark.value = ''
  rejectDesc.value = ''
  rejectReason.value = ''
  detailVisible.value = true
  try {
    currentItem.value = await loadDetail(row)
  } catch (err) {
    console.warn('[JobAudit] 详情加载失败:', err.message)
    ElMessage.error('加载招工详情失败')
  }
}

const handleDetail = openDetail

const handleApprove = openDetail

const handleReject = (row) => {
  currentItem.value = row
  rejectReason.value = ''
  rejectDesc.value = ''
  rejectVisible.value = true
}

const confirmApprove = async () => {
  if (!currentItem.value) return
  try {
    await auditJobPass(currentItem.value.id)
    detailVisible.value = false
    ElMessage.success('审核通过')
    loadAuditData()
  } catch (err) {
    console.warn('[JobAudit] 审核通过 API 调用失败:', err.message)
    ElMessage.error('审核操作失败')
  }
}

const confirmReject = () => {
  detailVisible.value = false
  if (rejectDesc.value) rejectReason.value = 'other'
  rejectVisible.value = true
}

const confirmRejectSubmit = async () => {
  if (!rejectReason.value) {
    ElMessage.warning('请选择拒绝原因')
    return
  }
  if (!currentItem.value) return

  const reasonLabel = {
    incomplete: '信息不完整',
    low_price: '价格严重偏低',
    violation: '内容违规',
    fake: '疑似虚假招工',
    other: '其他',
  }[rejectReason.value] || rejectReason.value
  const fullReason = rejectDesc.value
    ? `${reasonLabel}：${rejectDesc.value}`
    : reasonLabel

  try {
    await auditJobReject(currentItem.value.id, fullReason)
    rejectVisible.value = false
    ElMessage.success('已拒绝')
    loadAuditData()
  } catch (err) {
    console.warn('[JobAudit] 审核拒绝 API 调用失败:', err.message)
    ElMessage.error('拒绝操作失败')
  }
}

onMounted(loadAuditData)
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-date { display: flex; align-items: center; gap: 6px; color: var(--text-secondary); font-size: 13px; white-space: nowrap; }
.export-button { margin-left: auto; }
.employer-cell { display: flex; align-items: center; gap: 10px; min-width: 0; }
.employer-avatar { width: 36px; height: 36px; flex: 0 0 36px; display: inline-flex; align-items: center; justify-content: center; border-radius: 9px; color: #fff; font-size: 16px; font-weight: 700; }
.employer-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}
.audit-detail-dialog :deep(.el-dialog__header) { padding: 22px 24px; border-bottom: 1px solid var(--border); }
.audit-detail-dialog :deep(.el-dialog__body) { max-height: 72vh; overflow-y: auto; padding: 24px; }
.audit-dialog-title { display: flex; align-items: center; gap: 14px; color: var(--text-primary); font-size: 20px; font-weight: 700; }
.audit-dialog-title .status-badge { font-size: 13px; font-weight: 600; }
.audit-action-bar { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 20px 24px; margin-bottom: 24px; border-radius: 14px; background: #FFF8E6; }
.action-title { color: var(--text-primary); font-size: 18px; font-weight: 700; }.action-desc { margin-top: 5px; color: var(--text-secondary); }.risk-tag { display: inline-block; margin-left: 8px; padding: 4px 10px; border-radius: 6px; color: #059669; background: #ECFDF5; font-size: 12px; }.action-buttons { display: flex; gap: 12px; flex-shrink: 0; }.action-buttons .btn { padding: 10px 20px; font-size: 15px; }.action-buttons .btn-success { color: #111827; background: #F3F4F6; border-color: #F3F4F6; }
.detail-section-title { margin: 20px 0 12px; padding-bottom: 10px; border-bottom: 1px solid var(--border); color: var(--text-primary); font-size: 18px; font-weight: 700; }
.audit-preview-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }.audit-preview-box { padding: 16px; border: 1px solid #E5E7EB; border-radius: 14px; background: #FAFAFA; }.audit-preview-title { margin-bottom: 10px; color: var(--text-primary); font-size: 16px; font-weight: 700; }.audit-preview-title i { margin-right: 8px; color: var(--primary); }.audit-desc-box,.materials-box { padding: 14px; border-radius: 8px; background: var(--bg-page); color: var(--text-secondary); line-height: 1.7; }.materials-list { display: flex; flex-direction: column; gap: 8px; }.material-item { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border: 1px solid #E5E7EB; border-radius: 8px; background: #fff; }.material-icon { width: 32px; color: var(--primary); font-size: 20px; text-align: center; }.material-info { display: flex; flex: 1; flex-direction: column; gap: 3px; min-width: 0; }.material-info strong { overflow: hidden; color: var(--text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.material-info small { color: var(--text-muted); font-size: 11px; }.benefit-list { display: flex; flex-wrap: wrap; gap: 8px; }.benefit-list span { padding: 4px 10px; border-radius: 12px; color: var(--success); background: #ECFDF5; font-size: 12px; }.benefit-list em { color: var(--text-muted); font-style: normal; font-size: 13px; }.audit-extra { margin-top: 20px; }
@media (max-width: 800px) { .audit-preview-grid { grid-template-columns: 1fr; }.audit-action-bar { align-items: flex-start; flex-direction: column; }.action-buttons { width: 100%; }.action-buttons .btn { flex: 1; } }
</style>
