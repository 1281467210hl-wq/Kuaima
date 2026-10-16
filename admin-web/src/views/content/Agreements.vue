<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">协议管理</h1>
        <p class="page-desc">平台各类协议文本的上传、查看与维护</p>
      </div>
      <button class="btn btn-primary" @click="createAgreement">
        <i class="fas fa-plus"></i> 新建协议
      </button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <div class="filter-item">
          <span>协议类型</span>
          <el-select v-model="typeFilter" placeholder="全部" clearable style="width: 210px;">
            <el-option v-for="type in agreementTypes" :key="type" :label="type" :value="type" />
          </el-select>
        </div>
        <div class="filter-item">
          <el-input
            v-model="keyword"
            placeholder="协议名称搜索"
            prefix-icon="Search"
            clearable
            style="width: 220px;"
            @keyup.enter="search"
          />
        </div>
        <button class="btn btn-primary btn-sm" @click="search">
          <i class="fas fa-search"></i> 查询
        </button>
        <button class="btn btn-outline btn-sm" @click="resetFilter">
          <i class="fas fa-rotate-left"></i> 重置
        </button>
      </div>

      <el-table
        :data="agreements"
        stripe
        :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }"
      >
        <el-table-column label="协议ID" width="100">
          <template #default="{ row }">
            <span class="agreement-no">{{ row.agreementNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="协议名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="type" label="协议类型" width="190" show-overflow-tooltip />
        <el-table-column label="上传时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="上传账户" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="agreement-account">{{ row.uploadAccount || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最新更新时间" width="150">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <button :class="['toggle', { on: row.enabled }]" @click="toggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="right">
          <template #default="{ row }">
            <button class="table-action" @click="showDetail(row)">
              <i class="fas fa-eye"></i> 详情
            </button>
            <button class="table-action" @click="editAgreement(row)">
              <i class="fas fa-edit"></i> 编辑
            </button>
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

    <el-dialog v-model="detailVisible" title="协议详情" width="620px">
      <div class="detail-list">
        <div v-for="item in detailRows" :key="item.label" class="detail-row">
          <span class="detail-label">{{ item.label }}</span>
          <span class="detail-value">{{ item.value }}</span>
        </div>
      </div>
      <div class="detail-content">
        <div class="content-title">协议内容</div>
        <div v-if="detail.contentMode === 'FILE'" class="file-box">
          <div class="file-icon"><i class="fas fa-file-arrow-down"></i></div>
          <div class="file-info">
            <div class="file-name">{{ detail.fileName }}</div>
            <div class="file-size">文件大小：{{ formatFileSize(detail.fileSize) }}</div>
          </div>
          <button class="btn btn-outline btn-sm" :disabled="downloading" @click="downloadFile(detail)">
            <i class="fas fa-download"></i> 下载
          </button>
        </div>
        <div v-else class="content-body">{{ detail.content }}</div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <button class="btn btn-outline" @click="detailVisible = false">关闭</button>
          <button class="btn btn-primary" @click="editAgreement(detail)">
            <i class="fas fa-edit"></i> 编辑
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAgreementDownloadUrl, listAgreements, toggleAgreement } from '@/api/content'

const router = useRouter()
const agreementTypes = [
  '用户服务协议',
  '隐私协议',
  '知识产权协议',
  '实名认证&人脸验证服务协议',
  '信息发布协议'
]
const agreements = ref([])
const typeFilter = ref('')
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const detailVisible = ref(false)
const detail = ref({})
const downloading = ref(false)

const detailRows = computed(() => [
  { label: '协议ID', value: detail.value.agreementNo || '-' },
  { label: '协议名称', value: detail.value.name || '-' },
  { label: '协议类型', value: detail.value.type || '-' },
  { label: '上传时间', value: formatTime(detail.value.createdAt) },
  { label: '上传账户', value: detail.value.uploadAccount || '-' },
  { label: '最新更新时间', value: formatTime(detail.value.updatedAt) },
  { label: '内容形式', value: detail.value.contentMode === 'FILE' ? '上传文件' : '手动输入' }
])

const loadAgreements = async () => {
  try {
    const res = await listAgreements({
      type: typeFilter.value || undefined,
      keyword: keyword.value || undefined,
      page: currentPage.value - 1,
      size: pageSize.value
    })
    const data = res.data || {}
    agreements.value = Array.isArray(data.records) ? data.records : []
    total.value = Number(data.total ?? res.total ?? 0)
  } catch (error) {
    agreements.value = []
    total.value = 0
  }
}

const search = () => {
  currentPage.value = 1
  loadAgreements()
}

const resetFilter = () => {
  typeFilter.value = ''
  keyword.value = ''
  currentPage.value = 1
  loadAgreements()
}

const onSizeChange = () => {
  currentPage.value = 1
  loadAgreements()
}

const onPageChange = () => loadAgreements()

const createAgreement = () => router.push('/admin/agreements/edit')
const editAgreement = (row) => {
  detailVisible.value = false
  router.push(`/admin/agreements/edit/${row.id}`)
}

const showDetail = (row) => {
  detail.value = row
  detailVisible.value = true
}

const toggleStatus = async (row) => {
  try {
    const res = await toggleAgreement(row.id)
    row.enabled = res.data?.enabled
    row.updatedAt = res.data?.updatedAt || row.updatedAt
    ElMessage.success(row.enabled ? '协议已启用' : '协议已停用')
  } catch (error) {
    await loadAgreements()
  }
}

const downloadFile = async (row) => {
  if (downloading.value) return
  downloading.value = true
  try {
    const res = await getAgreementDownloadUrl(row.id)
    window.open(res.data?.url, '_blank')
  } finally {
    downloading.value = false
  }
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatFileSize(value) {
  const size = Number(value || 0)
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(2)} MB`
  if (size >= 1024) return `${Math.max(1, Math.round(size / 1024))} KB`
  return `${size} B`
}

onMounted(loadAgreements)
</script>

<style scoped>
.page-header { display:flex; align-items:flex-end; justify-content:space-between; margin-bottom:20px; }
.filter-bar { display:flex; align-items:center; flex-wrap:wrap; gap:12px; margin-bottom:16px; }
.filter-item { display:flex; align-items:center; gap:6px; color:var(--text-secondary,#6B7280); font-size:13px; }
.pagination { display:flex; align-items:center; justify-content:space-between; margin-top:16px; }
.agreement-no { color:var(--primary,#FF6B35); font-family:ui-monospace,monospace; font-size:12px; font-weight:600; }
.agreement-account { color:var(--text-secondary,#6B7280); font-family:ui-monospace,monospace; font-size:12px; }
.toggle { position:relative; width:38px; height:21px; margin:0; padding:0; border:0; border-radius:11px; background:#D1D5DB; cursor:pointer; transition:background .2s; }
.toggle::after { position:absolute; top:2px; left:2px; width:17px; height:17px; border-radius:50%; background:#fff; box-shadow:0 1px 3px rgba(0,0,0,.2); transition:left .2s; content:''; }
.toggle.on { background:var(--primary,#FF6B35); }
.toggle.on::after { left:19px; }
.table-action { margin:0 3px; padding:4px 2px; border:0; background:none; color:var(--primary,#FF6B35); font-size:13px; cursor:pointer; }
.table-action:hover { opacity:.8; }
.detail-list { border:1px solid #F3F4F6; border-radius:8px; padding:2px 14px; }
.detail-row { display:flex; padding:10px 0; border-bottom:1px solid #F3F4F6; font-size:13px; }
.detail-row:last-child { border-bottom:0; }
.detail-label { flex:0 0 90px; color:#9CA3AF; }
.detail-value { flex:1; color:#1F2937; font-weight:500; }
.detail-content { margin-top:14px; padding-top:14px; border-top:1px dashed var(--border,#E5E7EB); }
.content-title { margin-bottom:10px; color:#374151; font-size:13px; font-weight:600; }
.content-body { overflow:auto; max-height:300px; padding:16px; border-radius:8px; background:var(--bg-page,#F9FAFB); color:var(--text-secondary,#6B7280); font-size:13px; line-height:1.8; white-space:pre-wrap; }
.file-box { display:flex; align-items:center; gap:12px; padding:14px; border:1px solid var(--border,#E5E7EB); border-radius:8px; }
.file-icon { display:flex; align-items:center; justify-content:center; flex-shrink:0; width:40px; height:40px; border-radius:8px; background:linear-gradient(135deg,#FFE8D6,#FFD6B0); color:var(--primary,#FF6B35); font-size:18px; }
.file-info { flex:1; min-width:0; }
.file-name { overflow:hidden; color:var(--text-primary,#1F2937); font-size:13px; font-weight:500; text-overflow:ellipsis; white-space:nowrap; }
.file-size { margin-top:2px; color:var(--text-muted,#9CA3AF); font-size:12px; }
.dialog-footer { display:flex; justify-content:flex-end; gap:10px; }
</style>
