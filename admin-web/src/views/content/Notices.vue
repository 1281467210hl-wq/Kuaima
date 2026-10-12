<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">公告管理</h1>
        <p class="page-desc">管理员主动创建并发布平台公告、活动通知（用户端公告栏展示）</p>
      </div>
      <button class="btn btn-primary" @click="openCreateModal">
        <i class="fas fa-plus"></i> 新建公告
      </button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <div class="filter-item">
          <span>状态</span>
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 120px;">
            <el-option label="已发布" value="已发布" />
            <el-option label="草稿" value="草稿" />
            <el-option label="已下架" value="已下架" />
          </el-select>
        </div>
        <div class="filter-item">
          <span>发布时间</span>
          <el-date-picker
            v-model="publishTimeRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px;"
          />
        </div>
        <button class="btn btn-primary btn-sm" @click="loadData">
          <i class="fas fa-search"></i> 查询
        </button>
        <button class="btn btn-outline btn-sm" @click="resetFilters">
          <i class="fas fa-rotate-left"></i> 重置
        </button>
      </div>

      <el-table :data="notices" stripe :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
        <el-table-column label="公告ID" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono-cell">{{ formatId(row.id) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag :type="row.typeClass === 'info' ? 'primary' : row.typeClass === 'warning' ? 'warning' : 'success'" effect="light">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="公告标题" show-overflow-tooltip />
        <el-table-column prop="scope" label="发布范围" show-overflow-tooltip />
        <el-table-column prop="publisher" label="发布人" show-overflow-tooltip>
          <template #default="{ row }">{{ row.publisher || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="['status-badge', row.statusClass]">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.publishTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <button class="table-action" @click="handleEdit(row)">编辑</button>
            <button v-if="row.status === '草稿'" class="table-action table-action-success" @click="handlePublish(row)">立即发布</button>
            <button v-else-if="row.status === '已发布'" class="table-action table-action-warning" @click="handleUnpublish(row)">下架</button>
            <button class="table-action table-action-danger" @click="handleDelete(row)">删除</button>
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

    <!-- 新建/编辑公告弹窗 -->
    <el-dialog
      v-model="showModal"
      :title="editingId ? '编辑公告' : '新建公告'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top" class="notice-form">
        <div class="form-section-title">公告标题</div>
        <el-form-item required>
          <el-input v-model="form.title" placeholder="请输入公告标题" maxlength="100" show-word-limit />
        </el-form-item>
        <div class="form-section-title">公告类型</div>
        <el-form-item>
          <el-select v-model="form.type" style="width: 100%;">
            <el-option label="系统" value="系统" />
            <el-option label="活动" value="活动" />
            <el-option label="政策" value="政策" />
          </el-select>
        </el-form-item>
        <div class="form-section-title">发布范围</div>
        <el-form-item>
          <el-select v-model="form.scope" style="width: 100%;">
            <el-option label="全部用户" value="全部" />
            <el-option label="仅零工" value="零工" />
            <el-option label="仅老板" value="雇主" />
            <el-option label="已实名用户" value="已实名" />
          </el-select>
        </el-form-item>
        <div class="form-section-title">公告内容</div>
        <el-form-item>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            placeholder="请输入公告详细内容..."
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <button class="btn btn-outline" @click="closeModal">取消</button>
          <button class="btn btn-outline" @click="saveNotice('草稿')">存为草稿</button>
          <button class="btn btn-primary" @click="saveNotice('已发布')">
            <i class="fas fa-paper-plane"></i> 立即发布
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listNotices, createNotice, updateNotice, deleteNotice } from '@/api/content'

const notices = ref([])
const total = ref(0)
const statusFilter = ref('')
const publishTimeRange = ref([])
const currentPage = ref(1)
const pageSize = ref(10)

const formatId = (id) => 'N' + String(id || 0).padStart(3, '0')

/** 格式化时间：2026-09-07T14:30:00 -> 2026-09-07 14:30 */
const formatTime = (t) => {
  if (!t) return '-'
  const s = String(t).replace('T', ' ')
  return s.length > 16 ? s.substring(0, 16) : s
}

const showModal = ref(false)
const editingId = ref(null)
const form = ref({
  title: '',
  type: '系统',
  scope: '全部',
  content: ''
})

// 切换筛选条件时，自动回到第一页并重新加载
watch([statusFilter, publishTimeRange], () => {
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
    const res = await listNotices({
      status: statusFilter.value || undefined,
      page: currentPage.value - 1,
      size: pageSize.value,
    })
    const d = res.data
    const list = Array.isArray(d) ? d : (Array.isArray(res) ? res : [])
    // 前端按发布时间范围过滤（后端未实现该筛选条件）
    const [start, end] = publishTimeRange.value || []
    notices.value = list.filter(n => {
      const pt = n.publishTime ? String(n.publishTime).slice(0, 10) : ''
      if (start && pt && pt < start) return false
      if (end && pt && pt > end) return false
      return true
    })
    total.value = res.total ?? d?.total ?? list.length
  } catch (e) {
    console.warn('[Notices] 加载失败:', e)
    notices.value = []
    total.value = 0
  }
}

const resetFilters = () => {
  statusFilter.value = ''
  publishTimeRange.value = []
  currentPage.value = 1
  loadData()
}

const openCreateModal = () => {
  editingId.value = null
  form.value = { title: '', type: '系统', scope: '全部', content: '' }
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
  editingId.value = null
}

const saveNotice = async (status) => {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  try {
    if (editingId.value) {
      await updateNotice(editingId.value, { ...form.value, status })
      ElMessage.success('修改成功')
    } else {
      await createNotice({ ...form.value, status })
      ElMessage.success(status === '已发布' ? '公告已发布，系统消息已推送' : '已存为草稿')
    }
    closeModal()
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleEdit = (notice) => {
  editingId.value = notice.id
  form.value = {
    title: notice.title || '',
    type: notice.type || '系统',
    scope: notice.scope || '全部',
    content: notice.content || ''
  }
  showModal.value = true
}

const handlePublish = async (notice) => {
  const now = new Date().toISOString().slice(0, 10)
  try {
    await updateNotice(notice.id, { ...notice, status: '已发布', statusClass: 'success', publishTime: now })
    ElMessage.success('已发布，系统消息已推送')
    await loadData()
  } catch (e) {
    ElMessage.error('发布失败')
  }
}

const handleUnpublish = async (notice) => {
  try {
    await updateNotice(notice.id, { ...notice, status: '已下架', statusClass: 'warning' })
    ElMessage.success('已下架')
    await loadData()
  } catch (e) {
    ElMessage.error('下架失败')
  }
}

const handleDelete = async (notice) => {
  try {
    await ElMessageBox.confirm('确定删除该公告？', '提示', { type: 'warning' })
    await deleteNotice(notice.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(loadData)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 20px;
}

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
  font-size: 13px;
  color: var(--text-secondary, #6B7280);
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.dialog-footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

/* 表格操作按钮 — 链接风格 */
.table-action {
  background: none;
  border: none;
  padding: 4px 6px;
  color: var(--primary);
  font-size: 13px;
  cursor: pointer;
}
.table-action:hover { color: var(--primary-dark); }
.table-action-success { color: #10b981; }
.table-action-success:hover { color: #059669; }
.table-action-warning { color: #f59e0b; }
.table-action-warning:hover { color: #d97706; }
.table-action-danger { color: #ef4444; }
.table-action-danger:hover { color: #dc2626; }
.notice-form :deep(.el-form-item) { margin-bottom: 18px; }
.notice-form :deep(.el-form-item__label) { padding-bottom: 4px; }
.form-section-title {
  margin: 0 0 6px;
  padding-left: 8px;
  border-left: 3px solid var(--primary);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}
</style>
