<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">操作日志</h1>
      <p class="page-desc">记录所有管理员关键操作，支持追溯和审计</p>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="searchKeyword" placeholder="搜索操作人/对象" clearable style="width: 240px;" prefix-icon="Search" />
        <el-select v-model="typeFilter" placeholder="操作类型" clearable style="width: 140px;">
          <el-option label="登录" value="登录" />
          <el-option label="新建" value="新建" />
          <el-option label="编辑" value="编辑" />
          <el-option label="删除" value="删除" />
          <el-option label="查看" value="查看" />
        </el-select>
        <div class="date-picker-wrap" style="width: 240px;">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 100%;" />
        </div>
        <button class="btn btn-primary btn-sm" @click="loadData"><i class="fas fa-search"></i> 查询</button>
        <button class="btn btn-outline btn-sm" @click="resetFilters"><i class="fas fa-rotate-left"></i> 重置</button>
        <button class="btn btn-outline btn-sm export-button" style="margin-left: auto;" :disabled="exporting" @click="handleExport">
          <i :class="['fas', exporting ? 'fa-spinner fa-spin' : 'fa-download']"></i> {{ exporting ? '导出中...' : '导出' }}
        </button>
      </div>

      <el-table :data="logs" stripe :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
        <el-table-column prop="id" label="日志ID" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" show-overflow-tooltip />
        <el-table-column prop="type" label="操作类型" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag effect="light">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="操作对象" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP地址" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="结果" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="['status-badge', row.result === '成功' ? 'success' : 'default']">{{ row.result }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
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

    <!-- 日志详情弹窗 -->
    <el-dialog v-model="detailVisible" title="日志详情" width="560px" :close-on-click-modal="false">
      <div v-if="currentLog" class="log-detail">
        <div class="log-detail-row">
          <span class="log-detail-label">日志ID：</span>
          <span class="log-detail-value" style="font-family:monospace; color:var(--primary);">{{ currentLog.id }}</span>
        </div>
        <div class="log-detail-row">
          <span class="log-detail-label">操作人：</span>
          <span class="log-detail-value">
            <span class="avatar-sm">{{ (currentLog.operator || '-').charAt(0) }}</span>
            {{ currentLog.operator || '-' }}
          </span>
        </div>
        <div class="log-detail-row">
          <span class="log-detail-label">操作类型：</span>
          <span class="log-detail-value">
            <el-tag effect="light">{{ currentLog.type || '-' }}</el-tag>
          </span>
        </div>
        <div class="log-detail-row">
          <span class="log-detail-label">操作对象：</span>
          <span class="log-detail-value">{{ currentLog.target || '-' }}</span>
        </div>
        <div class="log-detail-row">
          <span class="log-detail-label">详情描述：</span>
          <span class="log-detail-value">{{ detailDescription(currentLog) }}</span>
        </div>
        <div class="log-detail-row">
          <span class="log-detail-label">IP地址：</span>
          <span class="log-detail-value" style="font-family:monospace;">{{ currentLog.ip || '-' }}</span>
        </div>
        <div class="log-detail-row">
          <span class="log-detail-label">操作时间：</span>
          <span class="log-detail-value">{{ formatTime(currentLog.createTime) }}</span>
        </div>
        <div class="log-detail-row">
          <span class="log-detail-label">操作结果：</span>
          <span class="log-detail-value">
            <span :class="['status-badge', currentLog.result === '成功' ? 'success' : 'default']">{{ currentLog.result || '-' }}</span>
          </span>
        </div>
        <div class="log-request-block">
          <div class="log-request-title">请求参数</div>
          <pre class="log-detail-json">{{ requestParams(currentLog) }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listLogs } from '@/api/system'

const logs = ref([])
const total = ref(0)
const searchKeyword = ref('')
const typeFilter = ref('')
const dateRange = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const detailVisible = ref(false)
const currentLog = ref(null)
const exporting = ref(false)

const formatTime = (t) => {
  if (!t) return '-'
  const s = String(t).replace('T', ' ')
  return s.length > 16 ? s.substring(0, 16) : s
}

const showDetail = (row) => {
  currentLog.value = row
  detailVisible.value = true
}

const requestParams = (log) => {
  const detail = String(log?.detail || '')
  const marker = ' | body='
  const bodyIndex = detail.indexOf(marker)
  if (bodyIndex < 0) return '{}'
  let body = detail.slice(bodyIndex + marker.length)
  const exceptionIndex = body.indexOf(' | ex=')
  if (exceptionIndex >= 0) body = body.slice(0, exceptionIndex)
  body = body.trim()
  if (!body) return '{}'
  try {
    return JSON.stringify(JSON.parse(body), null, 2)
  } catch (_) {
    return body
  }
}

const detailDescription = (log) => {
  if (log?.type !== '登录') return log?.detail || '-'
  const operator = log.operator || log.operatorAccount || '用户'
  return `${operator}登录${log.result || '成功'}`
}

// 切换筛选条件时，自动回到第一页并重新加载
watch([searchKeyword, typeFilter, dateRange], () => {
  currentPage.value = 1
  loadData()
})

const onSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadData()
}

const onPageChange = (page) => {
  currentPage.value = page
  loadData()
}

const loadData = async () => {
  try {
    const res = await listLogs({ ...currentFilters(), page: currentPage.value - 1, size: pageSize.value })
    const d = res.data
    const list = Array.isArray(d) ? d : (d?.content || d?.list || [])
    logs.value = list
    total.value = d?.totalElements ?? d?.total ?? res.total ?? list.length
  } catch (e) {
    console.warn('[Logs] 加载失败:', e)
    logs.value = []
    total.value = 0
  }
}

const currentFilters = () => ({
  type: typeFilter.value || undefined,
  keyword: searchKeyword.value.trim() || undefined,
  startDate: dateRange.value?.[0] || undefined,
  endDate: dateRange.value?.[1] || undefined,
})

const handleExport = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    const pageSize = 1000
    let page = 0
    let total = Infinity
    const rows = []
    while (page * pageSize < total) {
      const res = await listLogs({ ...currentFilters(), page, size: pageSize })
      const data = res.data
      const list = Array.isArray(data) ? data : (data?.content || data?.list || [])
      total = Number(data?.totalElements ?? data?.total ?? res.total ?? list.length) || 0
      rows.push(...list)
      if (!list.length) break
      page += 1
    }
    if (!rows.length) {
      ElMessage.info('当前筛选条件下没有可导出的日志')
      return
    }
    const headers = ['日志ID', '操作人', '登录账号', '操作类型', '操作对象', 'IP地址', '操作时间', '操作结果', '详情']
    const lines = rows.map(row => [
      row.id, row.operator, row.operatorAccount, row.type, row.target, row.ip,
      formatTime(row.createTime), row.result, row.detail,
    ].map(value => `"${String(value ?? '').replaceAll('"', '""')}"`).join(','))
    const blob = new Blob([`\ufeff${headers.join(',')}\n${lines.join('\n')}`], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `操作日志-${new Date().toISOString().slice(0, 19).replaceAll(':', '')}.csv`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${rows.length} 条日志`)
  } finally {
    exporting.value = false
  }
}

const resetFilters = () => {
  searchKeyword.value = ''
  typeFilter.value = ''
  dateRange.value = []
  currentPage.value = 1
  loadData()
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

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

/* 日志详情 */
.log-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.log-detail-row {
  display: flex;
  align-items: flex-start;
  font-size: 14px;
  line-height: 1.6;
}
.log-detail-label {
  width: 84px;
  flex-shrink: 0;
  color: var(--text-muted, #9CA3AF);
}
.log-detail-value {
  flex: 1;
  color: var(--text-primary, #1F2937);
  word-break: break-all;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.avatar-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #FF6B35, #FF8C42);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  margin-right: 4px;
}

.log-request-block {
  margin-top: 14px;
}
.log-request-title {
  margin-bottom: 8px;
  color: var(--text-primary, #1F2937);
  font-size: 13px;
  font-weight: 600;
}
.log-detail-json {
  max-height: 200px;
  margin: 0;
  padding: 12px;
  overflow-y: auto;
  border-radius: 6px;
  background: #F5F6F8;
  color: var(--text-primary, #1F2937);
  font-family: 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
