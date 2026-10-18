<template>
  <div>
    <div v-loading="loading">
      <!-- Hero 卡：返回 + 标题 + 元信息 + 金额 -->
      <div class="pd-hero" v-if="order">
        <div class="back-btn" @click="goBack" title="返回发薪管理">
          <i class="fas fa-arrow-left"></i>
        </div>
        <div class="pd-hero-info">
          <div class="pd-hero-title">
            <span>{{ order.title }}</span>
            <span :class="['status-badge', statusClass(order.status)]">{{ statusText(order.status) }}</span>
          </div>
          <div class="pd-hero-meta">
            <div class="item"><b>发薪单号</b><span>{{ order.orderNo || '—' }}</span></div>
            <div class="item"><b>所属项目</b><span>{{ order.projectName || '—' }}</span></div>
            <div class="item"><b>制单人</b><span>{{ order.creator || '—' }}</span></div>
            <div class="item"><b>提交时间</b><span>{{ order.submitTime || '—' }}</span></div>
          </div>
        </div>
        <div class="pd-hero-total">
          <div class="label">应发总金额</div>
          <div class="value">¥{{ fmtMoney(order.amount) }}</div>
          <div class="sub"><span>{{ peopleNum }}</span> 人 · 已通过 <span>{{ successNum }}</span></div>
        </div>
      </div>

      <!-- 发薪明细 -->
      <div class="pd-detail-card" v-if="order">
        <div class="pd-detail-head">
          <div class="pd-detail-title"><i class="fas fa-list"></i> 发薪明细</div>
          <div class="pd-head-actions">
            <template v-if="order.status === 'pending'">
              <button class="btn btn-outline btn-sm" @click="confirmReject"><i class="fas fa-times"></i> 驳回</button>
              <button class="btn btn-primary btn-sm" @click="confirmApprove"><i class="fas fa-check"></i> 通过</button>
            </template>
            <template v-else>
              <button class="btn btn-outline btn-sm" @click="exportStub('PDF')"><i class="fas fa-file-pdf"></i> 导出明细</button>
              <button class="btn btn-outline btn-sm" @click="exportStub('Excel')"><i class="fas fa-file-excel"></i> 下载Excel</button>
            </template>
          </div>
        </div>
        <div>
          <div class="pd-row hdr">
            <div></div>
            <div>姓名</div>
            <div>岗位</div>
            <div>手机号</div>
            <div>出勤</div>
            <div>日薪</div>
            <div style="text-align:right;">应发金额（元）</div>
            <div>状态</div>
          </div>
          <div class="pd-row" v-for="(m, i) in details" :key="i">
            <div><div class="avatar" :style="{ background: avatarColor(i) }">{{ (m.name || '?').charAt(0) }}</div></div>
            <div class="pd-name">{{ m.name }}</div>
            <div><span class="role-tag">{{ m.job || '—' }}</span></div>
            <div class="pd-phone">{{ m.phone || '—' }}</div>
            <div>{{ m.attendDays ?? 0 }}天</div>
            <div>¥{{ fmtMoney(m.dailyWage) }}</div>
            <div class="amount">¥{{ fmtMoney(m.amount) }}</div>
            <div><span :class="['status-badge', detailStatusClass(m.status)]">{{ detailStatusText(m.status) }}</span></div>
          </div>
          <div v-if="!details.length" class="pd-empty">暂无人员明细</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPayroll, approvePayroll, rejectPayroll } from '@/api/payroll'

const route = useRoute()
const router = useRouter()
const id = route.params.id

const loading = ref(false)
const order = ref(null)
const details = ref([])

const fen2yuan = (fen) => {
  if (fen == null || fen === '') return '0.00'
  const n = Number(fen)
  if (isNaN(n)) return '0.00'
  return (n / 100).toFixed(2)
}

/** 分 → 金额文本（原型风格：整数不带小数，最多两位小数） */
const fmtMoney = (fen) => {
  const yuan = Number(fen || 0) / 100
  return yuan.toLocaleString('zh-CN', { minimumFractionDigits: yuan % 1 === 0 ? 0 : 2, maximumFractionDigits: 2 })
}

const formatTime = (t) => { if (!t) return '—'; const s = String(t).replace('T', ' '); return s.length > 16 ? s.substring(0, 16) : s }

const statusText = (s) => ({ draft: '待提交审核', pending: '待审批', approved: '审批通过', rejected: '已驳回', withdrawn: '已撤回' }[s] || s || '—')
const statusClass = (s) => ({ draft: 'info', pending: 'warning', approved: 'success', rejected: 'danger', withdrawn: 'default' }[s] || 'default')
const detailStatusText = (s) => ({ pending: '待转账', success: '已到账', failed: '转账失败' }[s] || s || '—')
const detailStatusClass = (s) => ({ pending: 'warning', success: 'success', failed: 'danger' }[s] || 'default')

// 原型 AVATAR_COLORS：按行序取色
const AVATAR_COLORS = ['#FF6B35', '#FF8C5A', '#FFB84D', '#F09A3E', '#2563EB', '#10B981', '#8B5CF6', '#EF4444']
const avatarColor = (i) => AVATAR_COLORS[i % AVATAR_COLORS.length]

const peopleNum = computed(() => (details.value.length ? details.value.length : order.value?.peopleCount ?? 0))
const successNum = computed(() => details.value.filter((m) => m.status === 'success').length)

const goBack = () => router.push('/admin/project-salary')

// 从对象中按优先级取第一个非空字段
const pick = (obj, keys, fallback = null) => {
  for (const k of keys) {
    if (obj && obj[k] != null && obj[k] !== '') return obj[k]
  }
  return fallback
}

// 时间字段兼容：与列表页 ProjectSalary.vue 保持一致的回退链
const pickSubmitTime = (o) => pick(o, ['submitTime', 'submit_time', 'commitTime', 'commit_time', 'date', 'createdAt', 'createTime', 'create_time', 'created_at', 'timestamp'])
const pickReviewTime = (o) => pick(o, ['reviewTime', 'review_time', 'approvedAt', 'approved_at'])
const pickReviewBy = (o) => pick(o, ['reviewBy', 'review_by', 'reviewer', 'approvedBy', 'approved_by'], '—')
const pickCreator = (o) => pick(o, ['creator', 'createdBy', 'created_by', 'creatorName'], '—')

const normalizeOrder = (raw) => {
  if (!raw) return null
  return {
    id: pick(raw, ['id']),
    orderNo: pick(raw, ['orderNo', 'order_no', 'no']),
    company: pick(raw, ['company'], '—'),
    title: pick(raw, ['title'], '—'),
    projectName: pick(raw, ['projectName', 'project_name'], '—'),
    type: pick(raw, ['type'], 'wage'),
    peopleCount: pick(raw, ['peopleCount', 'people_count', 'memberCount', 'workerCount'], 0),
    amount: pick(raw, ['amount'], 0),
    creator: pickCreator(raw),
    submitTime: formatTime(pickSubmitTime(raw)),
    reviewBy: pickReviewBy(raw),
    reviewTime: formatTime(pickReviewTime(raw)),
    status: pick(raw, ['status'], 'pending'),
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getPayroll(id)
    const d = res.data || {}
    // 后端可能返回 { order, details } 或直接返回完整对象
    const rawOrder = d.order || d
    order.value = normalizeOrder(rawOrder)
    // 详情人员明细也兼容两种结构
    const rawDetails = d.details || rawOrder.details
    details.value = Array.isArray(rawDetails) ? rawDetails : []
  } catch (err) {
    console.warn('[PayrollDetail] 加载失败:', err.message)
    ElMessage.error('加载薪单详情失败')
  } finally {
    loading.value = false
  }
}

const confirmApprove = async () => {
  try {
    await ElMessageBox.confirm('确认通过该发薪单？', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  try { await approvePayroll(id); ElMessage.success('已通过'); loadData() }
  catch (err) { ElMessage.error('审批失败') }
}
const confirmReject = async () => {
  try {
    await ElMessageBox.confirm('确认驳回？', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  try { await rejectPayroll(id); ElMessage.success('已驳回'); loadData() }
  catch (err) { ElMessage.error('驳回失败') }
}

/** 把任意字符串包上引号（CSV 安全）。 */
const csvCell = (v) => {
  if (v == null || v === '') return '""'
  const s = String(v).replace(/"/g, '""')
  return `"${s}"`
}

/** 通用：二维数组 → CSV Blob 下载。 */
const downloadCsv = (rows, filename) => {
  const bom = '﻿' // 防 Excel 中文乱码
  const content = bom + rows.map(row => row.map(csvCell).join(',')).join('\r\n')
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** 导出当前薪单明细为 CSV（薪单头部 + 人员明细）。 */
const exportDetails = () => {
  if (!order.value) { ElMessage.warning('薪单数据未加载'); return }
  const o = order.value
  const now = new Date().toISOString().slice(0, 19).replace('T', '_')

  const rows = [
    // 单据头：与下方人员明细统一为横向表格（第 1 行标签、第 2 行数值）
    ['薪单标题', '发薪单号', '所属公司', '所属项目', '转账类型', '发薪人数', '应发总金额(元)', '制单人员', '提交时间', '状态', '审核人', '审核时间'],
    [
      o.title,
      o.orderNo || '',
      o.company,
      o.projectName,
      ({ wage: '工资', advance: '预支', other: '其他' })[o.type] || o.type || '',
      `${o.peopleCount ?? 0}`,
      fen2yuan(o.amount),
      o.creator,
      o.submitTime,
      statusText(o.status),
      o.reviewBy,
      o.reviewTime,
    ],
    [], // 空行分隔
    ['姓名', '手机号', '岗位', '出勤天数', '日薪(元)', '应发金额(元)', '状态'],
  ]
  for (const d of (details.value || [])) {
    rows.push([
      d.name || '',
      d.phone || '',
      d.job || '',
      `${d.attendDays ?? 0}`,
      fen2yuan(d.dailyWage),
      fen2yuan(d.amount),
      detailStatusText(d.status),
    ])
  }

  downloadCsv(rows, `发薪明细_${o.orderNo || o.id || now}.csv`)
  ElMessage.success('导出成功')
}

/** 导出明细（PDF 按钮当前以 CSV 形式导出，Excel 按钮同样输出 CSV）。 */
const exportStub = (type) => {
  exportDetails()
}

onMounted(loadData)
</script>

<style scoped>
/* --- Hero 卡：返回 + 标题 + 元信息 + 金额 --- */
.pd-hero {
  background: #fff; border-radius: 14px; padding: 18px 24px; margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  display: flex; align-items: center; gap: 18px;
}
.pd-hero .back-btn {
  width: 36px; height: 36px; flex-shrink: 0; border-radius: 8px;
  border: 1px solid #ECECEC; background: #FAFAFA;
  display: flex; align-items: center; justify-content: center;
  color: #666; font-size: 13px; cursor: pointer;
  transition: all 0.15s;
}
.pd-hero .back-btn:hover { color: #FF6B35; border-color: #FFE4CC; background: #FFF7F0; }

.pd-hero-info { flex: 1; min-width: 0; }
.pd-hero-title {
  font-size: 18px; font-weight: 700; color: #222;
  display: flex; align-items: center; gap: 10px; margin-bottom: 6px;
}
.pd-hero-meta { display: flex; flex-wrap: wrap; gap: 14px 22px; font-size: 12px; color: #666; }
.pd-hero-meta .item b { color: #999; font-weight: normal; margin-right: 4px; }
.pd-hero-meta .item span { color: #333; }

.pd-hero-total { text-align: right; flex-shrink: 0; }
.pd-hero-total .label { font-size: 12px; color: #999; }
.pd-hero-total .value { font-size: 24px; font-weight: 700; color: #FF6B35; margin-top: 2px; }
.pd-hero-total .sub { font-size: 12px; color: #999; margin-top: 2px; }

/* --- 发薪明细 --- */
.pd-detail-card {
  background: #fff; border-radius: 14px; margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04); overflow: hidden;
}
.pd-detail-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 24px; border-bottom: 1px solid #F0F0F0; gap: 12px;
}
.pd-detail-title { font-size: 14px; font-weight: 600; color: #333; display: flex; align-items: center; gap: 8px; }
.pd-detail-title i { color: #FF6B35; }
.pd-head-actions { display: flex; gap: 8px; }

.pd-row {
  display: grid; grid-template-columns: 44px 110px 100px 120px 70px 70px 135px 125px;
  gap: 14px; align-items: center; padding: 14px 24px;
  font-size: 13px; border-bottom: 1px solid #F7F7F7;
}
.pd-row:last-child { border-bottom: none; }
.pd-row.hdr {
  font-size: 12px; color: #999; padding: 10px 24px; background: #FAFAFA; font-weight: 500;
}
.pd-row.hdr > div { white-space: nowrap; }
.pd-row .avatar {
  width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 12px; font-weight: 600;
}
.pd-row .pd-name { font-weight: 500; color: #333; }
.pd-row .pd-phone { font-family: monospace; font-size: 12px; }
.pd-row .role-tag {
  display: inline-block; font-size: 12px; color: #FF6B35; background: #FFF0E8;
  padding: 3px 10px; border-radius: 12px;
}
.pd-row .amount { color: #FF6B35; font-weight: 600; text-align: right; font-variant-numeric: tabular-nums; }
.pd-row .status-badge { white-space: nowrap; }

.pd-empty { text-align: center; padding: 40px; color: #9ca3af; }
</style>
