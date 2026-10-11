<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">保险管理</h1>
      <p class="page-desc">管理平台与保险公司合作的意外险产品配置</p>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="searchKeyword" placeholder="搜索保险公司 / 产品编码" clearable style="width: 220px;" prefix-icon="Search" @keyup.enter="handleSearch" />
        <div class="filter-item"><span>状态</span>
          <el-select v-model="statusFilter" placeholder="全部" style="width: 110px;">
            <el-option label="全部" value="" />
            <el-option label="已启用" value="已启用" />
            <el-option label="已停用" value="已停用" />
          </el-select>
        </div>
        <button class="btn btn-primary btn-sm" @click="handleSearch"><i class="fas fa-search"></i> 查询</button>
        <button class="btn btn-outline btn-sm" @click="handleReset"><i class="fas fa-rotate-left"></i> 重置</button>
        <div class="filter-space"></div>
        <button v-permission="'permUser.insurance.edit'" class="btn btn-primary btn-sm" @click="openCreate"><i class="fas fa-plus"></i> 新建保险</button>
      </div>

      <el-table :data="tableData" stripe :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="保险公司" min-width="150">
          <template #default="{ row }">
            <div class="carrier-cell">
              <span class="carrier-logo" :class="carrierClass(row.carrier)">{{ (row.carrier || '?').charAt(0) }}</span>
              <span class="carrier-name">{{ row.carrier || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="产品编码" min-width="120">
          <template #default="{ row }"><span class="code-cell">{{ row.code || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="保险档数" width="110" align="center">
          <template #default="{ row }"><span class="tier-count">{{ row.tierCount || 0 }}<span class="unit">档</span></span></template>
        </el-table-column>
        <el-table-column label="创建人" min-width="110">
          <template #default="{ row }"><span class="muted">{{ row.creatorName || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="150">
          <template #default="{ row }"><span class="muted">{{ formatTime(row.createTime) }}</span></template>
        </el-table-column>
        <el-table-column label="启用状态" width="100" align="center">
          <template #default="{ row }">
            <div v-permission="'permUser.insurance.edit'" class="switch" :class="{ on: !!row.enabled }" @click="handleToggle(row)"></div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <button v-permission="'permUser.insurance.detail'" class="btn btn-outline btn-sm" @click="openDetail(row)"><i class="fas fa-eye"></i> 详情</button>
              <button v-permission="'permUser.insurance.edit'" class="btn btn-outline btn-sm" @click="openEdit(row)"><i class="fas fa-edit"></i> 编辑</button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <div class="pagination-info">共 {{ total }} 条记录</div>
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="sizes, prev, pager, next, jumper" background @size-change="onSizeChange" @current-change="onPageChange" />
      </div>
    </div>


  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listInsurances, toggleInsurance } from '@/api/insurance'

const searchKeyword = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref([])
const router = useRouter()

const formatTime = (value) => {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return text.length > 16 ? text.substring(0, 16) : text
}

const carrierClass = (carrier) => ({
  '众安保险': 'zongan', '平安保险': 'pingan', '太平洋保险': 'taipingyang', '中国人保': 'renbao'
}[carrier] || 'default')

const maxAmount = (row) => Math.max(0, ...(row?.tiers || []).map(tier => Number(tier.amount || 0)))

const loadData = async () => {
  try {
    const result = await listInsurances({
      keyword: searchKeyword.value || undefined,
      status: statusFilter.value || undefined,
      page: currentPage.value - 1,
      size: pageSize.value
    })
    const data = result?.data
    tableData.value = Array.isArray(data) ? data : (data?.content || data?.list || [])
    total.value = Number(result?.total ?? data?.totalElements ?? data?.total ?? 0)
  } catch (error) {
    console.warn('[InsuranceManage] 加载失败:', error)
    tableData.value = []
    total.value = 0
  }
}

const handleSearch = () => { currentPage.value = 1; loadData() }
const handleReset = () => {
  searchKeyword.value = ''
  statusFilter.value = ''
  handleSearch()
}
const onSizeChange = (size) => { pageSize.value = size; currentPage.value = 1; loadData() }
const onPageChange = (page) => { currentPage.value = page; loadData() }

const openDetail = (row) => router.push(`/admin/insurance/detail?id=${row.id}`)

const handleToggle = async (row) => {
  try {
    const result = await toggleInsurance(row.id)
    row.enabled = result?.data?.enabled
    ElMessage.success(row.enabled ? '已启用' : '已停用')
  } catch (error) {
    console.warn('[InsuranceManage] 切换状态失败:', error)
  }
}

const openCreate = () => router.push('/admin/insurance/create')

const openEdit = (row) => router.push(`/admin/insurance/edit?id=${row.id}`)

onMounted(loadData)
</script>

<style scoped>
.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-item { display: flex; align-items: center; gap: 8px; }
.filter-item > span { font-size: 13px; color: var(--text-secondary); white-space: nowrap; }
.filter-space { flex: 1; }
.pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; }
.pagination-info { font-size: 13px; color: var(--text-secondary); }
.muted { font-size: 12px; color: var(--text-muted); }
.carrier-cell { display: flex; align-items: center; gap: 8px; }
.carrier-logo { width: 30px; height: 30px; border-radius: 8px; display: inline-flex; align-items: center; justify-content: center; color: #fff; font-size: 13px; font-weight: 600; flex-shrink: 0; }
.zongan { background: linear-gradient(135deg, #FF6B35, #FF8C5A); }
.pingan { background: linear-gradient(135deg, #1677FF, #4096FF); }
.taipingyang { background: linear-gradient(135deg, #16A34A, #52C41A); }
.renbao { background: linear-gradient(135deg, #9333EA, #C084FC); }
.default { background: linear-gradient(135deg, #6B7280, #9CA3AF); }
.hero-logo { width: 52px; height: 52px; border-radius: 12px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 20px; font-weight: 600; }
.carrier-name { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.code-cell { font-family: monospace; font-size: 12px; color: var(--text-secondary); }
.tier-count { font-size: 18px; font-weight: 700; color: #FF6B35; }
.tier-count .unit { font-size: 12px; font-weight: 400; color: var(--text-muted); margin-left: 2px; }
.switch { display: inline-block; position: relative; width: 46px; height: 24px; border-radius: 12px; background: #D1D5DB; cursor: pointer; transition: background .25s ease; flex-shrink: 0; }
.switch::after { content: ''; position: absolute; top: 2px; left: 2px; width: 20px; height: 20px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,.18); transition: left .25s cubic-bezier(.4,.8,.4,1); }
.switch.on { background: #FF6B35; }
.switch.on::after { left: 24px; }
.action-btns { display: flex; gap: 6px; white-space: nowrap; align-items: center; }
.required { color: #EF4444; font-style: normal; margin-right: 2px; }
</style>
