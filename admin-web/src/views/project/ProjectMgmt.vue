<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">项目管理</h1>
      <p class="page-desc">项目签到考勤与入离职管理，实时跟踪人员到岗状态</p>
    </div>

    <!-- 项目列表 -->
    <div class="card">
      <div class="card-header">
        <div class="card-title"><span class="title-switch active">项目列表</span></div>
      </div>

      <div class="filter-bar">
        <div class="filter-item">
          <span class="filter-label">项目名称</span>
          <el-select v-model="filters.name" style="width: 180px;">
            <el-option label="全部项目" value="all" />
            <el-option v-for="n in nameOptions" :key="n" :label="n" :value="n" />
          </el-select>
        </div>
        <div class="filter-item">
          <span class="filter-label">状态</span>
          <el-select v-model="filters.status" style="width: 160px;">
            <el-option label="全部状态" value="all" />
            <el-option label="待归档" value="processing" />
            <el-option label="已归档" value="completed" />
            <el-option label="已暂停" value="paused" />
          </el-select>
        </div>
        <div class="filter-item">
          <span class="filter-label">立项日期</span>
          <el-date-picker v-model="filters.date" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 150px;" />
        </div>
        <button class="btn btn-primary btn-sm" @click="applyFilters"><i class="fas fa-search"></i> 查询</button>
        <button class="btn btn-outline btn-sm" @click="resetFilters"><i class="fas fa-arrow-rotate-left"></i> 重置</button>
        <button class="btn btn-primary btn-sm" style="margin-left:auto;" @click="openCreateProj"><i class="fas fa-plus"></i> 新建项目</button>
      </div>

      <el-table :data="pagedProjects" v-loading="loading" @selection-change="onSelect"
        :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
        <el-table-column type="selection" width="40" />
        <el-table-column label="项目名称" min-width="180">
          <template #default="{ row }">
            <div class="project-cell">
              <span class="proj-name">{{ row.name }}</span>
              <span class="proj-code">{{ row.code }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="client" label="用工企业" min-width="110" show-overflow-tooltip />
        <el-table-column label="项目负责人" min-width="130">
          <template #default="{ row }">
            <div class="manager-cell">
              <span class="mini-avatar" :class="row.managerAvatar">{{ (row.manager || '?').charAt(0) }}</span>
              <span>{{ row.manager }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="date" label="立项日期" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span :class="['status-badge', projectStatusClass(row.status)]">{{ projectStatusText(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="项目人数" width="100">
          <template #default="{ row }"><span>{{ row.count }} 人</span></template>
        </el-table-column>
        <el-table-column label="操作" min-width="330" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <button class="btn btn-outline btn-sm" @click="openProjectDetail(row)"><i class="fas fa-eye"></i> 详情</button>
              <button class="btn btn-outline btn-sm" @click="openEditProject(row)"><i class="fas fa-edit"></i> 编辑</button>
              <button class="btn btn-outline btn-sm" @click="openSignCode(row)"><i class="fas fa-qrcode"></i> 签到码</button>
              <button class="btn btn-outline btn-sm" @click="openAddMemberModal(row)"><i class="fas fa-user-plus"></i> 新增项目成员</button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <div class="pagination-info">共 {{ total }} 条记录</div>
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="total" layout="prev, pager, next" background />
      </div>
    </div>

    <!-- 创建新项目弹窗 -->
    <el-dialog v-model="createVisible" title="创建新项目" width="480px">
      <div class="form-group">
        <label class="form-label">项目名称 <span style="color:#FF4D4F;">*</span></label>
        <el-input v-model="createForm.name" placeholder="如：菜鸟·云联日结" />
      </div>
      <div class="form-group">
        <label class="form-label">用工企业</label>
        <el-input v-model="createForm.client" placeholder="用工企业名称" />
      </div>
      <div class="form-group">
        <label class="form-label">负责人</label>
        <el-input v-model="createForm.manager" placeholder="负责人姓名" />
      </div>
      <div class="form-time-row">
        <div class="form-group" style="flex:1;">
          <label class="form-label">规定签到时间</label>
          <el-time-picker v-model="createForm.checkinTime" format="HH:mm" value-format="HH:mm" placeholder="08:00" style="width:100%;" />
        </div>
        <div class="form-group" style="flex:1;">
          <label class="form-label">规定签退时间</label>
          <el-time-picker v-model="createForm.checkoutTime" format="HH:mm" value-format="HH:mm" placeholder="18:00" style="width:100%;" />
        </div>
      </div>
      <template #footer>
        <button class="btn btn-outline" @click="createVisible = false">取消</button>
        <button class="btn btn-primary" :disabled="creating" @click="confirmCreateProj">确认创建</button>
      </template>
    </el-dialog>

    <!-- 编辑项目弹窗 -->
    <el-dialog v-model="editVisible" title="编辑项目" width="520px">
      <div class="form-group">
        <label class="form-label"><span style="color:#EF4444;">*</span> 项目名称</label>
        <el-input v-model="editForm.name" placeholder="请输入项目名称" />
      </div>
      <div class="form-group">
        <label class="form-label"><span style="color:#EF4444;">*</span> 用工企业</label>
        <el-input v-model="editForm.client" placeholder="请输入用工企业名称" />
      </div>
      <div class="form-time-row">
        <div class="form-group" style="flex:1;">
          <label class="form-label">负责人</label>
          <el-input v-model="editForm.manager" placeholder="请输入负责人姓名" />
        </div>
        <div class="form-group" style="flex:1;">
          <label class="form-label">联系电话</label>
          <el-input v-model="editForm.phone" placeholder="请输入联系电话" />
        </div>
      </div>
      <div class="form-group">
        <label class="form-label">项目地点</label>
        <el-input v-model="editForm.location" type="textarea" :rows="2" resize="vertical" placeholder="请输入项目地点" />
      </div>
      <div class="form-group">
        <label class="form-label">项目状态</label>
        <el-select v-model="editForm.statusName" style="width:100%;">
          <el-option label="待归档" value="待归档" />
          <el-option label="已暂停" value="已暂停" />
          <el-option label="已归档" value="已归档" />
        </el-select>
      </div>
      <div class="form-time-row">
        <div class="form-group" style="flex:1;">
          <label class="form-label">签到时间</label>
          <el-time-picker v-model="editForm.checkinTime" format="HH:mm" value-format="HH:mm" placeholder="08:00" style="width:100%;" />
        </div>
        <div class="form-group" style="flex:1;">
          <label class="form-label">签退时间</label>
          <el-time-picker v-model="editForm.checkoutTime" format="HH:mm" value-format="HH:mm" placeholder="18:00" style="width:100%;" />
        </div>
      </div>
      <template #footer>
        <button class="btn btn-outline" @click="editVisible = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="saveEditProject"><i class="fas fa-check"></i> 保存</button>
      </template>
    </el-dialog>

    <!-- 签到码弹窗 -->
    <el-dialog v-model="signVisible" title="项目签到码" width="420px" align-center>
      <div class="sign-body">
        <div class="sign-tab-wrap">
          <div class="sign-tab" :class="{ active: signTab === 'in' }" @click="signTab = 'in'">签到码</div>
          <div class="sign-tab" :class="{ active: signTab === 'out' }" @click="signTab = 'out'">签退码</div>
        </div>
        <div class="sign-qr-box">
          <img v-if="signQrImage" :src="signQrImage" alt="项目签到码" style="width: 180px; height: 180px; object-fit: contain; border-radius: 8px;" />
          <i v-else class="fas fa-qrcode" :class="{ out: signTab === 'out' }"></i>
        </div>
        <div class="sign-proj-name">{{ signProject?.name }}</div>
        <div class="sign-tip">使用日结项目 APP 扫描二维码完成{{ signTab === 'in' ? '签到' : '签退' }}</div>
        <div class="sign-code-value" :class="{ out: signTab === 'out' }">{{ signProject?.code }}</div>
        <div class="sign-times">
          <div>
            <div class="sign-time-label"><i class="fas fa-sign-in-alt"></i> 签到时间</div>
            <div class="sign-time-value in">{{ signProject?.checkinTime || '08:00' }}</div>
          </div>
          <div>
            <div class="sign-time-label"><i class="fas fa-sign-out-alt"></i> 签退时间</div>
            <div class="sign-time-value out">{{ signProject?.checkoutTime || '18:00' }}</div>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-outline" @click="signVisible = false">关闭</button>
        <button class="btn btn-primary" :disabled="signLoading" @click="saveSignCode"><i class="fas fa-download"></i> 保存二维码</button>
      </template>
    </el-dialog>

    <!-- 新增项目成员弹窗（从候选人选择） -->
    <el-dialog v-model="addMemberVisible" title="从候选人列表选择" width="560px">
      <div class="candidate-search">
        <el-input v-model="memberKeyword" placeholder="搜索候选人姓名/岗位" prefix-icon="Search" clearable />
      </div>
      <div class="candidate-list">
        <label v-for="(c, i) in filteredCandidates" :key="c.name" class="candidate-row" :class="{ disabled: c.disabled }">
          <el-checkbox v-model="c.checked" :disabled="c.disabled" />
          <div class="candidate-avatar">{{ c.name.charAt(0) }}</div>
          <div class="candidate-info">
            <div class="candidate-name">{{ c.name }}
              <span class="candidate-job">{{ c.job }}</span>
              <span v-if="c.disabled" class="candidate-exists">（已是项目成员）</span>
            </div>
            <div class="candidate-meta">{{ c.gender }} · {{ c.age }}岁 · {{ c.exp }}经验</div>
          </div>
        </label>
        <div v-if="!filteredCandidates.length" class="candidate-empty">暂无匹配候选人</div>
      </div>
      <template #footer>
        <button class="btn btn-secondary" @click="addMemberVisible = false">取消</button>
        <button class="btn btn-primary" :disabled="addingMember" @click="confirmAddMember">确认添加</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  listProjects,
  createProject,
  updateProject,
  archiveProject,
  addProjectMember,
  getProjectAttendanceCode,
} from '@/api/project'

const router = useRouter()

/* ============ 日期工具 ============ */
const pad = (n) => (n < 10 ? '0' + n : '' + n)
const todayISO = () => {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/* ============ 状态映射（后端实体 ↔ 原型文案） ============
   后端 Project.status：active 进行中 / archived 已归档（无「已暂停」）
   原型 PROJECT_STATUS_MAP：processing 待归档 / completed 已归档 / paused 已暂停 */
const STATUS_TO_UI = { active: 'processing', archived: 'completed' }
const STATUS_TO_API = { all: undefined, processing: 'active', completed: 'archived', paused: 'paused' }
const AVATAR_COLORS = ['orange', 'blue', 'green', 'purple']

const PROJECT_STATUS_MAP = {
  processing: { text: '待归档', cls: 'success' },
  completed: { text: '已归档', cls: 'default' },
  paused: { text: '已暂停', cls: 'warning' },
}
const projectStatusText = (s) => PROJECT_STATUS_MAP[s]?.text || s
const projectStatusClass = (s) => PROJECT_STATUS_MAP[s]?.cls || 'default'

/* ============ 列表 + 筛选 + 分页（服务端分页，page 从 0 开始） ============ */
const loading = ref(false)
const projects = ref([])
const total = ref(0)
const selectedRows = ref([])
const page = ref(1)
const pageSize = ref(10)
const filters = reactive({ name: 'all', status: 'all', date: '' })

// 项目名称下拉选项：真实接口取一批项目名（后端仅支持 keyword 精确名匹配）
const nameOptions = ref([])

/** 后端 projectView → 原型行字段 */
const normalizeProject = (p, i) => ({
  id: p.id,
  code: p.code || `PRJ-${p.id}`,
  name: p.name || '—',
  client: p.client || '—',
  manager: p.manager || '—',
  managerPhone: p.managerPhone || '',
  managerAvatar: AVATAR_COLORS[(String(p.manager || '').charCodeAt(0) || i) % AVATAR_COLORS.length],
  date: p.date || '—',
  address: p.address || '',
  enterpriseId: p.enterpriseId ?? null,
  status: STATUS_TO_UI[p.status] || p.status,
  rawStatus: p.status,
  statusText: p.statusText || '',
  count: p.count ?? 0,
  // 后端 Project 实体无「规定签到/签退时间」列，仅弹窗展示默认值
  checkinTime: p.checkinTime || '08:00',
  checkoutTime: p.checkoutTime || '18:00',
})

// 后端已完成关键字/状态筛选与分页；立项日期后端不支持，在当前页内本地过滤
const filteredProjects = computed(() => {
  if (!filters.date) return projects.value
  return projects.value.filter((p) => (p.date || '').substring(0, 10) === filters.date)
})

const pagedProjects = computed(() => filteredProjects.value)

const onSelect = (rows) => { selectedRows.value = rows }

const loadProjects = async () => {
  loading.value = true
  try {
    const res = await listProjects({
      keyword: filters.name !== 'all' ? filters.name : undefined,
      status: STATUS_TO_API[filters.status],
      page: page.value - 1,
      size: pageSize.value,
    })
    const rows = Array.isArray(res?.data) ? res.data : []
    projects.value = rows.map(normalizeProject)
    total.value = Number(res?.total ?? rows.length)
  } catch (err) {
    console.warn('[ProjectMgmt] 项目加载失败:', err.message)
    projects.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const loadNameOptions = async () => {
  try {
    const res = await listProjects({ page: 0, size: 100 })
    const rows = Array.isArray(res?.data) ? res.data : []
    nameOptions.value = [...new Set(rows.map((p) => p.name).filter(Boolean))]
  } catch (err) {
    console.warn('[ProjectMgmt] 项目名选项加载失败:', err.message)
    nameOptions.value = []
  }
}

watch([page, pageSize], loadProjects)

// 页码变化由 watch 触发重载；已在第一页时手动加载，避免重复请求
const reloadFromFirstPage = async () => {
  if (page.value !== 1) {
    page.value = 1
  } else {
    await loadProjects()
  }
}

const applyFilters = async () => {
  await reloadFromFirstPage()
}
const resetFilters = async () => {
  filters.name = 'all'
  filters.status = 'all'
  filters.date = ''
  ElMessage.success('已重置筛选条件')
  await applyFilters()
}

/* ============ 详情跳转 ============ */
const openProjectDetail = (row) => {
  router.push({ path: '/admin/project-detail', query: { id: row.id, code: row.code, name: row.name } })
}

/* ============ 创建新项目 ============ */
const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', client: '', manager: '', checkinTime: '08:00', checkoutTime: '18:00' })
const openCreateProj = () => {
  createForm.name = ''
  createForm.client = ''
  createForm.manager = ''
  createForm.checkinTime = '08:00'
  createForm.checkoutTime = '18:00'
  createVisible.value = true
}
const confirmCreateProj = async () => {
  const name = createForm.name.trim()
  if (!name) { ElMessage.warning('请输入项目名称'); return }
  creating.value = true
  try {
    await createProject({
      name,
      companyName: createForm.client.trim(),
      leaderName: createForm.manager.trim(),
      establishDate: todayISO(),
      status: 'active',
      checkinTime: createForm.checkinTime || '08:00',
      checkoutTime: createForm.checkoutTime || '18:00',
    })
    createVisible.value = false
    ElMessage.success('项目创建成功')
    await Promise.all([reloadFromFirstPage(), loadNameOptions()])
  } catch (err) {
    console.warn('[ProjectMgmt] 创建项目失败:', err.message)
  } finally {
    creating.value = false
  }
}

/* ============ 编辑项目 ============ */
const editVisible = ref(false)
const editingProject = ref(null)
const saving = ref(false)
const editForm = reactive({ name: '', client: '', manager: '', phone: '', location: '', statusName: '待归档', checkinTime: '08:00', checkoutTime: '18:00' })
const openEditProject = (row) => {
  editingProject.value = row
  editForm.name = row.name === '—' ? '' : row.name || ''
  editForm.client = row.client === '—' ? '' : row.client || ''
  editForm.manager = row.manager === '—' ? '' : row.manager || ''
  editForm.phone = row.managerPhone || ''
  editForm.location = row.address || ''
  editForm.statusName = projectStatusText(row.status)
  editForm.checkinTime = row.checkinTime || '08:00'
  editForm.checkoutTime = row.checkoutTime || '18:00'
  editVisible.value = true
}
const saveEditProject = async () => {
  const p = editingProject.value
  if (!p) return
  const name = editForm.name.trim()
  const client = editForm.client.trim()
  if (!name) { ElMessage.warning('请输入项目名称'); return }
  if (!client) { ElMessage.warning('请输入用工企业'); return }
  if (!p.id) { ElMessage.error('缺少项目 id，无法保存'); return }
  saving.value = true
  try {
    // Project 实体字段：name / companyName / leaderName / leaderPhone / location / checkinTime / checkoutTime
    await updateProject(p.id, {
      name,
      companyName: client,
      leaderName: editForm.manager.trim(),
      leaderPhone: editForm.phone.trim(),
      location: editForm.location.trim(),
      checkinTime: editForm.checkinTime || '08:00',
      checkoutTime: editForm.checkoutTime || '18:00',
    })
    // 状态：后端仅支持归档（无「已暂停」「取消归档」接口）
    const wantArchived = editForm.statusName === '已归档'
    const wasArchived = p.rawStatus === 'archived'
    if (wantArchived && !wasArchived) {
      await archiveProject(p.id)
    } else if (editForm.statusName === '已暂停') {
      ElMessage.warning('后端暂无「已暂停」状态，项目状态保持不变')
    } else if (!wantArchived && wasArchived) {
      ElMessage.warning('已归档项目暂不支持取消归档')
    }
    editVisible.value = false
    ElMessage.success('项目信息已更新')
    await loadProjects()
  } catch (err) {
    console.warn('[ProjectMgmt] 保存项目失败:', err.message)
  } finally {
    saving.value = false
  }
}

/* ============ 签到码（真实二维码） ============ */
const signVisible = ref(false)
const signTab = ref('in')
const signProject = ref(null)
const signQrImage = ref('')
const signLoading = ref(false)
const openSignCode = async (row) => {
  signProject.value = row
  signTab.value = 'in'
  signQrImage.value = ''
  signVisible.value = true
  if (!row?.id) return
  signLoading.value = true
  try {
    const res = await getProjectAttendanceCode(row.id)
    const d = res?.data || {}
    signQrImage.value = d.qrImage || ''
    // 用后端真实签到码覆盖展示编号（模板仍读 signProject.code）
    if (d.code) signProject.value = { ...row, code: d.code }
  } catch (err) {
    console.warn('[ProjectMgmt] 签到码加载失败:', err.message)
    signQrImage.value = ''
  } finally {
    signLoading.value = false
  }
}
const saveSignCode = () => {
  if (!signQrImage.value) { ElMessage.warning('二维码尚未生成，请稍后重试'); return }
  const a = document.createElement('a')
  a.href = signQrImage.value
  a.download = `${signProject.value?.code || 'project'}-${signTab.value === 'in' ? 'checkin' : 'checkout'}.png`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  ElMessage.success(`${signTab.value === 'in' ? '签到' : '签退'}码已保存`)
}

/* ============ 新增项目成员（候选人弹层为本地选择，提交走真实接口） ============ */
const CANDIDATES = [
  { name: '张伟', job: '分拣员', exp: '3年', gender: '男', age: 28 },
  { name: '李娜', job: '包装员', exp: '2年', gender: '女', age: 25 },
  { name: '王强', job: '装卸工', exp: '5年', gender: '男', age: 32 },
  { name: '赵敏', job: '质检工', exp: '4年', gender: '女', age: 27 },
  { name: '刘洋', job: '分拣员', exp: '1年', gender: '男', age: 23 },
  { name: '陈静', job: '包装员', exp: '2年', gender: '女', age: 26 },
  { name: '孙磊', job: '仓库管理员', exp: '6年', gender: '男', age: 30 },
  { name: '周颖', job: '后厨助手', exp: '2年', gender: '女', age: 24 },
]
const addMemberVisible = ref(false)
const addingMember = ref(false)
const memberKeyword = ref('')
const candidateRows = ref(CANDIDATES.map((c) => ({ ...c, checked: false, disabled: false })))
const addMemberProject = ref(null)

const filteredCandidates = computed(() => {
  const kw = (memberKeyword.value || '').trim()
  if (!kw) return candidateRows.value
  return candidateRows.value.filter((c) => c.name.includes(kw) || c.job.includes(kw))
})

const openAddMemberModal = (row) => {
  addMemberProject.value = row
  memberKeyword.value = ''
  candidateRows.value = CANDIDATES.map((c) => ({ ...c, checked: false, disabled: false }))
  addMemberVisible.value = true
}
const confirmAddMember = async () => {
  const picked = candidateRows.value.filter((c) => c.checked && !c.disabled)
  if (!picked.length) { ElMessage.warning('请至少选择一位候选人'); return }
  const id = addMemberProject.value?.id
  if (!id) { ElMessage.error('缺少项目 id，无法新增成员'); return }
  addingMember.value = true
  try {
    const joinDate = todayISO()
    for (const c of picked) {
      // ProjectMember 实体字段：name / role（岗位）/ status / employmentType / joinDate
      await addProjectMember(id, {
        name: c.name,
        role: c.job,
        status: 'active',
        employmentType: 'fulltime',
        joinDate,
      })
    }
    addMemberVisible.value = false
    ElMessage.success('已添加项目成员：' + picked.map((c) => c.name).join('、'))
    await loadProjects()
  } catch (err) {
    console.warn('[ProjectMgmt] 新增项目成员失败:', err.message)
  } finally {
    addingMember.value = false
  }
}

onMounted(() => {
  loadProjects()
  loadNameOptions()
})
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.card-title { font-size: 16px; font-weight: 600; color: var(--text-primary); }
.title-switch { font-size: 16px; font-weight: 600; color: var(--text-muted); cursor: pointer; padding: 6px 0; }
.title-switch.active { color: var(--text-primary); }

.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-item { display: flex; align-items: center; gap: 6px; }
.filter-label { font-size: 13px; font-weight: 500; color: var(--text-secondary); white-space: nowrap; }
.pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; }
.pagination-info { font-size: 13px; color: var(--text-secondary); }

.project-cell { display: flex; flex-direction: column; gap: 2px; }
.project-cell .proj-name { font-weight: 500; font-size: 14px; }
.project-cell .proj-code { font-size: 12px; color: var(--text-muted); }

.manager-cell { display: flex; align-items: center; gap: 8px; }
.mini-avatar { width: 28px; height: 28px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; color: #fff; flex-shrink: 0; }
.mini-avatar.orange { background: linear-gradient(135deg, #FF8C42 0%, #FF6B35 100%); }
.mini-avatar.blue { background: linear-gradient(135deg, #60A5FA 0%, #2563EB 100%); }
.mini-avatar.green { background: linear-gradient(135deg, #34D399 0%, #10B981 100%); }
.mini-avatar.purple { background: linear-gradient(135deg, #A78BFA 0%, #7C3AED 100%); }

.action-btns { display: flex; gap: 6px; justify-content: flex-end; }
.action-btns .btn { padding: 4px 10px; font-size: 12px; }

.form-group { margin-bottom: 16px; }
.form-label { display: block; font-size: 13px; color: var(--text-secondary); margin-bottom: 6px; }
.form-time-row { display: flex; gap: 16px; }

/* 签到码弹窗 */
.sign-body { text-align: center; }
.sign-tab-wrap { display: inline-flex; background: #F3F4F6; border-radius: 10px; padding: 4px; margin-bottom: 8px; }
.sign-tab { padding: 7px 22px; border-radius: 8px; font-size: 13px; font-weight: 600; cursor: pointer; color: #666; }
.sign-tab.active { background: var(--primary); color: #fff; }
.sign-qr-box { width: 200px; height: 200px; margin: 16px auto; border: 1px solid var(--border); border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 80px; }
.sign-qr-box .fa-qrcode { color: var(--primary); }
.sign-qr-box .fa-qrcode.out { color: #FF6B35; }
.sign-proj-name { font-size: 16px; font-weight: 600; color: #1F2937; margin-top: 10px; }
.sign-tip { font-size: 13px; color: #9CA3AF; margin-top: 6px; }
.sign-code-value { font-family: monospace; font-size: 20px; font-weight: 700; letter-spacing: 4px; margin-top: 16px; color: var(--primary); }
.sign-code-value.out { color: #FF6B35; }
.sign-times { display: flex; justify-content: center; gap: 32px; margin-top: 18px; padding-top: 16px; border-top: 1px dashed #E5E7EB; }
.sign-time-label { font-size: 12px; color: #9CA3AF; margin-bottom: 4px; }
.sign-time-value { font-size: 18px; font-weight: 700; }
.sign-time-value.in { color: #10B981; }
.sign-time-value.out { color: #FF6B35; }

/* 候选人列表 */
.candidate-search { margin-bottom: 12px; }
.candidate-list { max-height: 340px; overflow-y: auto; border: 1px solid #EEE; border-radius: 8px; }
.candidate-row { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-bottom: 1px solid #F0F0F0; cursor: pointer; }
.candidate-row:last-child { border-bottom: none; }
.candidate-row.disabled { cursor: not-allowed; opacity: 0.5; }
.candidate-avatar { width: 36px; height: 36px; border-radius: 8px; background: linear-gradient(135deg, #FF7743, #FFB84D); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 600; flex-shrink: 0; }
.candidate-info { flex: 1; }
.candidate-name { font-size: 14px; font-weight: 600; color: #333; }
.candidate-job { font-weight: 400; color: #999; font-size: 12px; }
.candidate-exists { font-size: 11px; color: #999; }
.candidate-meta { font-size: 12px; color: #999; }
.candidate-empty { text-align: center; padding: 30px; color: #BBB; }
</style>
