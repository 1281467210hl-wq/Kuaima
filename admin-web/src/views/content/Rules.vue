<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">规则管理</h1>
      <p class="page-desc">平台规则、信用分管理规则</p>
    </div>

    <!-- 分类标签栏 -->
    <div class="rule-tabs">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        :class="['rule-tab', { active: currentTab === tab.key }]"
        @click="switchTab(tab.key)"
      >
        <span class="tab-icon" :style="{ background: tab.gradient }">
          <i :class="['fas', tab.icon]"></i>
        </span>
        {{ tab.label }}
        <span v-if="tab.key === 'season'" class="count-badge season">已配置 {{ seasonCities.length }} 城</span>
        <span v-else class="count-badge">{{ tab.count }}</span>
      </div>
    </div>

    <!-- 淡旺季系数 Tab 内容 -->
    <div v-if="currentTab === 'season'" class="card season-card">
      <div class="season-tip">
        <i class="fas fa-circle-info"></i>
        <div>淡旺季系数用于动态调整城市用工推荐、价格指数、信用分加减基数等。系数范围 <b>0.5 ~ 4.0</b>，<b>1.0</b> 为基准值。</div>
      </div>
      <div class="season-grid">
        <div class="season-city-panel">
          <div class="season-city-header">
            <span>用工城市</span>
            <div class="season-city-actions">
              <button class="season-mini-btn primary" @click="openNewCity"><i class="fas fa-plus"></i> 新建</button>
              <button class="season-mini-btn" @click="importSeason"><i class="fas fa-file-import"></i> 导入</button>
            </div>
          </div>
          <div class="season-city-list">
            <div
              v-for="city in seasonCities"
              :key="city.name"
              :class="['season-city-item', { active: city.name === selectedSeasonCity }]"
              @click="selectedSeasonCity = city.name"
            >
              <span>{{ city.name }}</span>
              <span class="season-city-region">{{ city.region }}</span>
            </div>
          </div>
        </div>
        <div class="season-factor-panel">
          <div class="season-factor-header">
            <span>{{ selectedSeasonCity }} · {{ selectedCityData?.region }}</span>
            <span class="season-factor-hint">点击单元格可直接修改系数</span>
          </div>
          <table class="season-factor-table">
            <thead>
              <tr><th v-for="m in 12" :key="m">{{ m }}月</th></tr>
            </thead>
            <tbody>
              <tr>
                <td
                  v-for="(val, idx) in selectedCityFactors"
                  :key="idx"
                  :class="factorClass(val)"
                  @click="openEditFactor(idx, val)"
                >{{ val.toFixed(2) }}</td>
              </tr>
            </tbody>
          </table>
          <div class="season-legend">
            <span><i class="peak"></i>旺季 &gt; 1.2</span>
            <span><i class="normal"></i>平稳 1.2 ~ 3.0</span>
            <span><i class="low"></i>淡季 ≥ 3.0</span>
          </div>
        </div>
      </div>
      <div class="season-actions">
        <button class="btn btn-outline btn-sm" @click="resetSeasonFactors"><i class="fas fa-rotate-left"></i> 恢复默认</button>
        <button class="btn btn-primary btn-sm" @click="saveSeasonFactors"><i class="fas fa-check"></i> 保存系数</button>
      </div>
    </div>

    <div class="card">
      <div class="filter-bar">
        <div class="filter-item">
          <span>状态</span>
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 120px;">
            <el-option label="已发布" value="published" />
            <el-option label="草稿" value="draft" />
            <el-option label="已归档" value="archived" />
          </el-select>
        </div>
        <div class="filter-item">
          <el-input
            v-model="keyword"
            placeholder="规则名称搜索"
            style="width: 220px;"
            :prefix-icon="Search"
            clearable
          />
        </div>
        <button class="btn btn-primary btn-sm" @click="loadData">
          <i class="fas fa-search"></i> 查询
        </button>
        <button class="btn btn-outline btn-sm" @click="resetFilters">
          <i class="fas fa-rotate-left"></i> 重置
        </button>
        <div style="flex:1;"></div>
        <button class="btn btn-primary btn-sm" @click="goCreate">
          <i class="fas fa-plus"></i> 新增规则
        </button>
      </div>

      <el-table
        :data="rules"
        stripe
        :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }"
      >
        <el-table-column label="规则ID" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="rule-id">{{ row.code || row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="规则名称" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="rule-name">
              <span class="category-icon" :style="{ background: currentTabMeta.gradient }">
                <i :class="['fas', currentTabMeta.icon]"></i>
              </span>
              <span class="rule-name-text">{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" show-overflow-tooltip />
        <el-table-column label="版本号" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="rule-version">{{ row.version || 'v1.0' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="['status-badge', statusClass(row.status)]">{{ statusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建人" show-overflow-tooltip>
          <template #default="{ row }">{{ row.creator || row.createdBy || '管理员' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createTime || row.createdAt || '--' }}</template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" show-overflow-tooltip />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <button class="table-action" @click="viewRule(row)">查看</button>
              <button class="table-action" @click="handleEdit(row)">编辑</button>
              <button
                v-if="row.status === 'published'"
                class="table-action table-action-warning"
                @click="toggleStatus(row, 'draft')"
              >下线</button>
              <button
                v-else-if="row.status === 'draft'"
                class="table-action table-action-success"
                @click="toggleStatus(row, 'published')"
              >发布</button>
              <button
                v-else
                class="table-action"
                @click="toggleStatus(row, 'published')"
              >恢复</button>
              <button class="table-action table-action-danger" @click="handleDelete(row)">删除</button>
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

    <!-- 规则详情预览弹窗 -->
    <el-dialog v-model="viewVisible" title="规则详情" width="640px">
      <div v-if="currentViewRule" class="preview-meta">
        <span><i class="fas fa-tag" style="color: var(--primary);"></i> {{ currentViewRule.category }}</span>
        <span><i class="fas fa-code-branch" style="color: var(--primary);"></i> {{ currentViewRule.version || 'v1.0' }}</span>
        <span v-if="currentViewRule.updateTime"><i class="fas fa-clock"></i> {{ currentViewRule.updateTime }}</span>
        <span :class="['status-badge', statusClass(currentViewRule.status)]">{{ statusLabel(currentViewRule.status) }}</span>
      </div>
      <div class="preview-content" v-html="currentViewRule?.content"></div>
      <template #footer>
        <button class="btn btn-outline" @click="viewVisible = false">关闭</button>
        <button class="btn btn-primary" @click="editFromView">
          <i class="fas fa-edit"></i> 编辑
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { listRules, updateRules, deleteRules } from '@/api/content'

const router = useRouter()
const rules = ref([])
const allRules = ref([])
const total = ref(0)
const currentTab = ref('platform')
const statusFilter = ref('')
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

const viewVisible = ref(false)
const currentViewRule = ref(null)

const tabs = computed(() => {
  const map = { platform: '平台规则', credit: '信用分规则', season: '淡旺季系数' }
  return Object.keys(map).map(key => ({
    key,
    label: map[key],
    count: allRules.value.filter(r => key === 'credit' ? (r.type === 'credit' || r.category === '信用分规则') : (r.type !== 'credit' && r.category !== '信用分规则')).length,
    icon: tabMeta[key].icon,
    gradient: tabMeta[key].gradient
  }))
})

const tabMeta = {
  platform: { icon: 'fa-cube', gradient: 'linear-gradient(135deg,#3B82F6,#2563EB)' },
  credit: { icon: 'fa-star', gradient: 'linear-gradient(135deg,#10B981,#059669)' },
  season: { icon: 'fa-chart-line', gradient: 'linear-gradient(135deg,#F59E0B,#D97706)' },
}

const currentTabMeta = computed(() => tabMeta[currentTab.value] || tabMeta.platform)

// 切换分类、筛选条件或搜索关键字时，自动回到第一页并重新加载
watch([currentTab, statusFilter, keyword], () => {
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

const switchTab = (key) => {
  currentTab.value = key
}

// ====== 淡旺季系数 ======
const seasonCities = ref([
  { name: '上海', region: '华东', factors: [1.0, 1.15, 1.25, 1.1, 1.05, 1.0, 0.95, 0.9, 1.0, 1.1, 1.2, 1.3] },
  { name: '深圳', region: '华南', factors: [1.05, 1.0, 1.1, 1.15, 1.1, 1.0, 0.95, 0.95, 1.05, 1.1, 1.15, 1.1] },
  { name: '北京', region: '华北', factors: [0.9, 0.95, 1.1, 1.15, 1.1, 1.0, 1.05, 1.0, 1.1, 1.1, 1.0, 0.9] },
  { name: '成都', region: '西南', factors: [1.0, 1.05, 1.1, 1.0, 1.0, 0.95, 1.0, 1.0, 1.05, 1.1, 1.05, 1.0] },
  { name: '杭州', region: '华东', factors: [1.0, 1.0, 1.15, 1.1, 1.05, 1.0, 0.95, 0.95, 1.05, 1.15, 1.2, 1.25] },
  { name: '武汉', region: '华中', factors: [0.95, 1.0, 1.05, 1.0, 1.0, 0.95, 1.0, 1.0, 1.05, 1.1, 1.05, 1.0] },
])
const selectedSeasonCity = ref('上海')
const selectedCityData = computed(() => seasonCities.value.find(c => c.name === selectedSeasonCity.value))
const selectedCityFactors = computed(() => selectedCityData.value?.factors || [])
const factorClass = val => val > 1.2 ? 'peak' : val >= 3.0 ? 'low' : 'normal'
const openNewCity = () => ElMessage.info('新建城市功能开发中')
const importSeason = () => ElMessage.info('导入城市系数功能开发中')
const openEditFactor = (idx, val) => ElMessage.info(`${selectedSeasonCity.value} ${idx + 1}月当前系数 ${val}，编辑功能开发中`)
const resetSeasonFactors = () => { seasonCities.value.forEach(c => c.factors = c.factors.map(() => 1.0)); ElMessage.success('已恢复默认系数') }
const saveSeasonFactors = () => ElMessage.success('系数已保存')

const resetFilters = () => {
  statusFilter.value = ''
  keyword.value = ''
  currentPage.value = 1
  loadData()
}

const goCreate = () => {
  router.push({ name: 'RulesEdit', params: { tab: currentTab.value } })
}

const handleEdit = (rule) => {
  router.push({ name: 'RulesEdit', params: { id: rule.id, tab: rule.type || currentTab.value } })
}

const viewRule = (rule) => {
  currentViewRule.value = rule
  viewVisible.value = true
}

const editFromView = () => {
  if (currentViewRule.value) {
    viewVisible.value = false
    handleEdit(currentViewRule.value)
  }
}

const statusLabel = (status) => {
  const map = { published: '已发布', draft: '草稿', archived: '已归档' }
  return map[status] || '草稿'
}

const statusClass = (status) => {
  const map = { published: 'success', draft: 'warning', archived: 'default' }
  return map[status] || 'warning'
}

const toggleStatus = async (rule, target) => {
  const action = target === 'published' ? '发布' : target === 'draft' ? '下线' : '恢复'
  try {
    await ElMessageBox.confirm(`确定要${action}规则「${rule.title}」吗？`, '提示', { type: 'warning' })
    const now = new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
    await updateRules(rule.id, { ...rule, status: target, updateTime: now })
    ElMessage.success(`已${action}`)
    await loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(`${action}失败`)
  }
}

const handleDelete = async (rule) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除规则「${rule.title}」吗？删除后不可恢复，请谨慎操作。`,
      '确认删除',
      { type: 'warning' }
    )
    await deleteRules(rule.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

// 分类映射
const CATEGORY_MAP = { platform: '平台规则', credit: '信用分规则' }

const loadData = async () => {
  try {
    // 规则接口暂不支持分类筛选；一次取全量后在前端按 Tab 分类，确保基础规则不会因分页落在后续页而不显示。
    const res = await listRules({ page: 0, size: 200 })
    const d = res.data
    const list = Array.isArray(d) ? d : (Array.isArray(res) ? res : [])
    allRules.value = list
    const filtered = list.filter(r => {
      // 按当前选中的 tab 分类过滤
      const targetCategory = CATEGORY_MAP[currentTab.value]
      if (currentTab.value === 'credit') return r.type === 'credit' || r.category === targetCategory
      return r.type !== 'credit' && r.category !== '信用分规则'
    }).filter(r => !statusFilter.value || r.status === statusFilter.value)
      .filter(r => !keyword.value || String(r.title || '').toLowerCase().includes(keyword.value.toLowerCase()) || String(r.code || r.id).toLowerCase().includes(keyword.value.toLowerCase()))
    rules.value = filtered.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value)
    total.value = filtered.length
  } catch (e) {
    console.warn('[Rules] 加载失败:', e)
    rules.value = []
    total.value = 0
  }
}

onMounted(loadData)
</script>

<style scoped>
.season-card { padding: 14px; }
.season-tip { display: flex; gap: 8px; align-items: flex-start; background: #FFF8E6; border: 1px solid #FFE58F; color: #AD6800; padding: 10px 14px; border-radius: 6px; font-size: 12px; margin-bottom: 14px; }
.season-tip i { margin-top: 2px; }
.season-grid { display: grid; grid-template-columns: 200px 1fr; gap: 16px; align-items: start; }
.season-city-panel { border: 1px solid #F3F4F6; border-radius: 8px; overflow: hidden; }
.season-city-header { padding: 8px 10px; background: #FAFAFA; border-bottom: 1px solid #F3F4F6; display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.season-city-header > span { font-size: 12px; font-weight: 600; color: #6B7280; }
.season-city-actions { display: flex; gap: 4px; }
.season-mini-btn { padding: 4px 8px; font-size: 11px; border-radius: 5px; border: 1px solid #E5E7EB; background: #fff; color: #6B7280; cursor: pointer; }
.season-mini-btn.primary { color: #FF6B35; font-weight: 600; }
.season-city-list { max-height: 340px; overflow-y: auto; }
.season-city-item { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-bottom: 1px solid #F9FAFB; cursor: pointer; font-size: 13px; color: #374151; }
.season-city-item:hover { background: #FFF7F0; }
.season-city-item.active { background: #FFF7F0; color: #FF6B35; font-weight: 600; }
.season-city-region { font-size: 11px; color: #9CA3AF; }
.season-city-item.active .season-city-region { color: #FF6B35; }
.season-factor-panel { border: 1px solid #F3F4F6; border-radius: 8px; overflow: hidden; }
.season-factor-header { padding: 10px 14px; background: #FAFAFA; font-size: 12px; font-weight: 600; color: #6B7280; border-bottom: 1px solid #F3F4F6; display: flex; align-items: center; justify-content: space-between; }
.season-factor-hint { font-size: 11px; color: #9CA3AF; font-weight: 400; }
.season-factor-table { width: 100%; border-collapse: collapse; }
.season-factor-table th { padding: 10px 0; font-size: 12px; font-weight: 500; color: #6B7280; text-align: center; background: #FAFAFA; border-bottom: 1px solid #F3F4F6; }
.season-factor-table td { padding: 14px 0; font-size: 14px; font-weight: 600; text-align: center; cursor: pointer; }
.season-factor-table td:hover { outline: 2px solid #FF6B35; outline-offset: -2px; }
.season-factor-table td.peak { background: rgba(220,38,38,0.08); color: #DC2626; }
.season-factor-table td.normal { background: rgba(107,114,128,0.06); color: #4B5563; }
.season-factor-table td.low { background: rgba(5,150,105,0.08); color: #059669; }
.season-legend { padding: 10px 14px; border-top: 1px solid #F3F4F6; display: flex; gap: 18px; font-size: 11px; color: #6B7280; }
.season-legend span { display: flex; align-items: center; gap: 5px; }
.season-legend i { display: inline-block; width: 10px; height: 10px; border-radius: 2px; }
.season-legend .peak { background: rgba(220,38,38,0.2); }
.season-legend .normal { background: rgba(107,114,128,0.15); }
.season-legend .low { background: rgba(5,150,105,0.2); }
.season-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 14px; }
.count-badge.season { background: #FEF3C7 !important; color: #92400E !important; font-size: 11px !important; }
.rule-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 2px solid var(--border, #E5E7EB);
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.rule-tab {
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary, #6B7280);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 6px;
}
.rule-tab:hover { color: var(--primary, #FF6B35); }
.rule-tab.active {
  color: var(--primary, #FF6B35);
  border-bottom-color: var(--primary, #FF6B35);
}
.rule-tab .tab-icon {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: #fff;
}
.rule-tab .count-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 11px;
  background: var(--bg-page, #F9FAFB);
  color: var(--text-muted, #9CA3AF);
  margin-left: 4px;
}
.rule-tab.active .count-badge {
  background: rgba(255,107,53,0.1);
  color: var(--primary, #FF6B35);
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

.category-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
}
.rule-name {
  display: flex;
  align-items: center;
  gap: 10px;
}
.rule-name-text {
  font-weight: 500;
  font-size: 14px;
  color: var(--text-primary, #111827);
}
.rule-id {
  font-family: monospace;
  color: var(--primary, #FF6B35);
  font-size: 12px;
}
.rule-version {
  font-family: monospace;
  color: var(--text-secondary, #6B7280);
  font-size: 12px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}
.status-badge.success { background: #D1FAE5; color: #059669; }
.status-badge.warning { background: #FEF3C7; color: #D97706; }
.status-badge.default { background: #F3F4F6; color: #6B7280; }

.action-btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.table-action {
  background: none;
  border: none;
  padding: 4px 6px;
  color: var(--primary, #FF6B35);
  font-size: 13px;
  cursor: pointer;
}
.table-action:hover { color: var(--primary-dark, #E55A2B); }
.table-action-success { color: #10b981; }
.table-action-success:hover { color: #059669; }
.table-action-warning { color: #f59e0b; }
.table-action-warning:hover { color: #d97706; }
.table-action-danger { color: #ef4444; }
.table-action-danger:hover { color: #dc2626; }

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.preview-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-muted, #9CA3AF);
  flex-wrap: wrap;
}
.preview-content {
  background: var(--bg-page, #F9FAFB);
  border-radius: 8px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-secondary, #6B7280);
  max-height: 360px;
  overflow-y: auto;
  white-space: pre-wrap;
}
</style>
