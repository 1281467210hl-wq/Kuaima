<template>
  <div v-if="detail" v-loading="loading">
    <div class="page-header detail-page-header">
      <div>
        <h1 class="page-title">项目详情</h1>
        <p class="page-desc">项目管理 / {{ detail.name }}</p>
      </div>
      <button class="btn btn-outline" @click="goBack"><i class="fas fa-arrow-left"></i> 返回列表</button>
    </div>

    <!-- 基础信息 -->
    <div class="section-card">
      <div class="section-card-header">
        <div class="section-card-title"><i class="fas fa-circle-info"></i>基础信息</div>
      </div>
      <div class="section-card-body">
        <div class="detail-hero-meta">
          <div class="detail-hero-meta-item"><div class="label">项目名称</div><div class="value">{{ detail.name }}</div></div>
          <div class="detail-hero-meta-item"><div class="label">用工企业</div><div class="value">{{ detail.client }}</div></div>
          <div class="detail-hero-meta-item"><div class="label">项目负责人</div><div class="value">{{ detail.manager }}</div></div>
          <div class="detail-hero-meta-item"><div class="label">立项日期</div><div class="value">{{ detail.date }}</div></div>
          <div class="detail-hero-meta-item"><div class="label">项目人数</div><div class="value">{{ detail.stats.total }}人</div></div>
        </div>
      </div>
    </div>

    <!-- 项目成员 / 签到记录 / 驻场管理 -->
    <div class="section-card" style="margin-top:32px;">
      <div class="section-card-header">
        <div class="card-title title-switch-group">
          <span class="title-switch" :class="{ active: tab === 'member' }" @click="tab = 'member'">项目成员</span>
          <span class="title-switch-divider"></span>
          <span class="title-switch" :class="{ active: tab === 'checkin' }" @click="tab = 'checkin'">签到记录</span>
          <span class="title-switch-divider"></span>
          <span class="title-switch" :class="{ active: tab === 'onsite' }" @click="tab = 'onsite'">驻场管理</span>
        </div>
        <div style="display:flex;align-items:center;gap:12px;">
          <span style="font-size:12px;color:#9CA3AF;">总人数 {{ detail.stats.total }} 人</span>
          <button class="btn btn-primary btn-sm" @click="openAddMemberModal"><i class="fas fa-plus"></i> 新增项目成员</button>
        </div>
      </div>
      <div class="section-card-body">
        <!-- 项目成员 -->
        <el-table v-show="tab === 'member'" :data="detail.members"
          :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="name" label="姓名" min-width="100" />
          <el-table-column prop="job" label="岗位" min-width="110" />
          <el-table-column label="日薪" min-width="110">
            <template #default="{ row }">{{ row.salary || '—' }}</template>
          </el-table-column>
          <el-table-column prop="type" label="用工类型" min-width="100" />
          <el-table-column prop="joinTime" label="入职时间" min-width="160" />
          <el-table-column label="离职时间" min-width="160">
            <template #default="{ row }">{{ row.status === '离职' && row.leaveTime ? row.leaveTime : '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <span :class="['status-badge', memberStatusClass(row.status)]">{{ row.status }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row, $index }">
              <button class="btn btn-sm btn-outline" @click.stop="openMemberEdit($index)">编辑</button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 签到记录 -->
        <div v-show="tab === 'checkin'">
          <div class="checkin-filter">
            <span class="checkin-filter-label">状态：</span>
            <el-select v-model="checkinStatus" style="width: 120px;">
              <el-option label="全部" value="" />
              <el-option label="出勤" value="出勤" />
              <el-option label="迟到" value="迟到" />
              <el-option label="缺卡" value="缺卡" />
            </el-select>
          </div>
          <el-table :data="filteredCheckins"
            :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
            <el-table-column type="index" label="序号" width="60" />
            <el-table-column prop="worker" label="姓名" min-width="100" />
            <el-table-column prop="job" label="岗位" min-width="110" />
            <el-table-column prop="time" label="签到时间" min-width="150" />
            <el-table-column prop="loc" label="签到地点" min-width="180" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span :class="['status-badge', checkinStatusClass(row.status)]">{{ row.status }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 驻场管理 -->
        <div v-show="tab === 'onsite'">
          <div class="filter-bar">
            <div class="filter-item">
              <span class="filter-label">姓名</span>
              <el-input v-model="onsiteFilters.name" placeholder="请输入姓名" style="width: 140px;" />
            </div>
            <div class="filter-item">
              <span class="filter-label">岗位</span>
              <el-input v-model="onsiteFilters.job" placeholder="请输入岗位" style="width: 140px;" />
            </div>
            <div class="filter-item">
              <span class="filter-label">驻场开始</span>
              <el-date-picker v-model="onsiteFilters.start" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 150px;" />
            </div>
            <div class="filter-item">
              <span class="filter-label">驻场结束</span>
              <el-date-picker v-model="onsiteFilters.end" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 150px;" />
            </div>
            <button class="btn btn-primary btn-sm" @click="applyOnsiteFilters"><i class="fas fa-search"></i> 查询</button>
            <button class="btn btn-outline btn-sm" @click="resetOnsiteFilters"><i class="fas fa-arrow-rotate-left"></i> 重置</button>
          </div>
          <div style="margin-bottom:12px;text-align:right;">
            <button class="btn btn-primary" @click="openOnsiteModal"><i class="fas fa-plus"></i> 新建驻场人员</button>
          </div>
          <el-table :data="filteredOnsite"
            :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
            <el-table-column type="index" label="序号" width="60" />
            <el-table-column prop="name" label="姓名" min-width="90" />
            <el-table-column prop="job" label="岗位" min-width="100" />
            <el-table-column prop="phone" label="联系电话" min-width="120" />
            <el-table-column prop="startDate" label="驻场开始" min-width="110" />
            <el-table-column prop="endDate" label="驻场结束" min-width="110" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span :class="['status-badge', onsiteStatusClass(row.status)]">{{ row.status }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ $index }">
                <button class="btn btn-sm btn-danger-outline" @click.stop="deleteOnsite($index)">移除</button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 新建驻场人员弹窗 -->
    <el-dialog v-model="onsiteVisible" title="新建驻场人员" width="520px">
      <div class="form-group">
        <label class="form-label"><span style="color:#EF4444;">*</span> 姓名</label>
        <el-input v-model="onsiteForm.name" placeholder="请输入姓名" />
      </div>
      <div class="form-group">
        <label class="form-label"><span style="color:#EF4444;">*</span> 岗位</label>
        <el-input v-model="onsiteForm.job" placeholder="请输入岗位，如：驻场经理" />
      </div>
      <div class="form-group">
        <label class="form-label"><span style="color:#EF4444;">*</span> 联系电话</label>
        <el-input v-model="onsiteForm.phone" placeholder="请输入联系电话" />
      </div>
      <div class="form-grid-2col">
        <div class="form-group">
          <label class="form-label"><span style="color:#EF4444;">*</span> 驻场开始日期</label>
          <el-date-picker v-model="onsiteForm.startDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%;" />
        </div>
        <div class="form-group">
          <label class="form-label">驻场结束日期</label>
          <el-date-picker v-model="onsiteForm.endDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%;" />
        </div>
      </div>
      <template #footer>
        <button class="btn btn-secondary" @click="onsiteVisible = false">取消</button>
        <button class="btn btn-primary" :disabled="onsiteSaving" @click="saveOnsite">确认创建</button>
      </template>
    </el-dialog>

    <!-- 新增项目成员弹窗（从候选人选择） -->
    <el-dialog v-model="addMemberVisible" title="从候选人列表选择" width="560px">
      <div style="margin-bottom:12px;">
        <el-input v-model="memberKeyword" placeholder="搜索候选人姓名/岗位" prefix-icon="Search" clearable />
      </div>
      <div class="candidate-list">
        <label v-for="c in filteredCandidates" :key="c.name" class="candidate-row" :class="{ disabled: c.disabled }">
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

    <!-- 编辑成员状态弹窗 -->
    <el-dialog v-model="memberEditVisible" title="编辑成员状态" width="460px">
      <div class="form-group">
        <label class="form-label">姓名</label>
        <el-input v-model="memberEditForm.name" disabled />
      </div>
      <div class="form-group">
        <label class="form-label">岗位</label>
        <el-input v-model="memberEditForm.job" disabled />
      </div>
      <div class="form-group">
        <label class="form-label"><span style="color:#EF4444;">*</span> 状态</label>
        <el-select v-model="memberEditForm.status" style="width:100%;">
          <el-option label="在职" value="在职" />
          <el-option label="离职" value="离职" />
          <el-option label="临时" value="临时" />
        </el-select>
      </div>
      <div class="form-group" v-if="memberEditForm.status === '离职'">
        <label class="form-label">离职时间</label>
        <el-date-picker v-model="memberEditForm.leaveTime" type="datetime" placeholder="选择离职时间"
          format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm" style="width:100%;" />
      </div>
      <template #footer>
        <button class="btn btn-secondary" @click="memberEditVisible = false">取消</button>
        <button class="btn btn-primary" :disabled="memberEditSaving" @click="saveMemberEdit">保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getProject,
  getProjectAttendance,
  addProjectMember,
  updateProjectMemberStatus,
  addProjectOnsite,
  removeProjectOnsite,
} from '@/api/project'

const route = useRoute()
const router = useRouter()

/* ============ 项目 id（路由参数：/admin/project-detail?id=123 或 :id） ============ */
const projectId = computed(() => route.params.id || route.query.id || '')

const pad = (n) => (n < 10 ? '0' + n : '' + n)
const todayISO = () => {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/* ============ 详情数据 ============ */
const detail = ref(null)
const loading = ref(false)
const tab = ref('member')
// 签到/考勤查询日期：后端 ?date 缺省即今日
const queryDate = ref(todayISO())

const goBack = () => router.push('/admin/project-mgmt')

const normalizeDetail = (d) => ({
  ...d,
  // 后端实体无经纬度列，coord 恒为 null，模板降级展示
  stats: { total: 0, active: 0, left: 0, temp: 0, ...(d.stats || {}) },
  members: Array.isArray(d.members) ? d.members : [],
  checkins: Array.isArray(d.checkins) ? d.checkins : [],
  onsite: Array.isArray(d.onsite) ? d.onsite : [],
  onboard: Array.isArray(d.onboard) ? d.onboard : [],
})

const loadDetail = async () => {
  const id = projectId.value
  if (!id) {
    ElMessage.error('缺少项目 id，无法加载项目详情')
    detail.value = null
    return
  }
  loading.value = true
  try {
    const res = await getProject(id, queryDate.value)
    detail.value = res?.data ? normalizeDetail(res.data) : null
  } catch (err) {
    console.warn('[ProjectDetail] 详情加载失败:', err.message)
    detail.value = null
  } finally {
    loading.value = false
  }
}

/** 考勤打卡按日期查询：GET /admin/projects/{id}/attendance?date= */
const loadCheckins = async () => {
  const id = projectId.value
  if (!id || !detail.value) return
  try {
    const res = await getProjectAttendance(id, queryDate.value)
    const d = res?.data || {}
    detail.value.checkins = Array.isArray(d.records) ? d.records : []
    detail.value.attendanceStats = d.stats || null
  } catch (err) {
    console.warn('[ProjectDetail] 签到记录加载失败:', err.message)
    detail.value.checkins = []
  }
}

// 切到签到记录 tab 时按当前日期重新拉取考勤
watch(tab, (t) => { if (t === 'checkin') loadCheckins() })

/* ============ 状态 badge 映射（与原型逐字一致） ============ */
const memberStatusClass = (s) => (s === '在职' ? 'success' : s === '临时' ? 'warning' : 'default')
const checkinStatusClass = (s) => (s === '出勤' ? 'success' : s === '迟到' ? 'warning' : 'danger')
const onsiteStatusClass = (s) => (s === '驻场中' ? 'success' : 'default')

/* ============ 签到记录筛选 ============ */
const checkinStatus = ref('')
const filteredCheckins = computed(() => {
  const list = detail.value?.checkins || []
  return checkinStatus.value ? list.filter((c) => c.status === checkinStatus.value) : list
})

/* ============ 驻场管理筛选（查询 / 重置，与原型一致点击生效） ============ */
const onsiteFilters = reactive({ name: '', job: '', start: '', end: '' })
const onsiteApplied = ref({ name: '', job: '', start: '', end: '' })
const filteredOnsite = computed(() => {
  const list = detail.value?.onsite || []
  const f = onsiteApplied.value
  return list.filter((o) => {
    if (f.name && !(o.name || '').includes(f.name)) return false
    if (f.job && !(o.job || '').includes(f.job)) return false
    if (f.start && (o.startDate || '') < f.start) return false
    if (f.end && (o.endDate || '') > f.end) return false
    return true
  })
})
const applyOnsiteFilters = () => {
  onsiteApplied.value = {
    name: (onsiteFilters.name || '').trim(),
    job: (onsiteFilters.job || '').trim(),
    start: onsiteFilters.start || '',
    end: onsiteFilters.end || '',
  }
  tab.value = 'onsite'
}
const resetOnsiteFilters = () => {
  onsiteFilters.name = ''; onsiteFilters.job = ''; onsiteFilters.start = ''; onsiteFilters.end = ''
  onsiteApplied.value = { name: '', job: '', start: '', end: '' }
}

/* ============ 新建 / 移除驻场人员（ProjectOnsiteStaff 实体字段） ============ */
const onsiteVisible = ref(false)
const onsiteSaving = ref(false)
const onsiteForm = reactive({ name: '', job: '', phone: '', company: '', startDate: '', endDate: '' })
const openOnsiteModal = () => {
  onsiteForm.name = ''
  onsiteForm.job = ''
  onsiteForm.phone = ''
  onsiteForm.company = ''
  onsiteForm.startDate = ''
  onsiteForm.endDate = ''
  onsiteVisible.value = true
}
const saveOnsite = async () => {
  const name = onsiteForm.name.trim()
  const job = onsiteForm.job.trim()
  const phone = onsiteForm.phone.trim()
  if (!name) { ElMessage.warning('请输入姓名'); return }
  if (!job) { ElMessage.warning('请输入岗位'); return }
  if (!phone) { ElMessage.warning('请输入联系电话'); return }
  if (!onsiteForm.startDate) { ElMessage.warning('请选择驻场开始日期'); return }
  const id = projectId.value
  if (!id) { ElMessage.error('缺少项目 id'); return }
  onsiteSaving.value = true
  try {
    // 弹窗岗位 → 实体 position；驻场角色后端仅 leader/assistant，按岗位文案推断
    const onsiteRole = /(经理|主管|负责人|总)/.test(job) ? 'leader' : 'assistant'
    await addProjectOnsite(id, {
      name,
      phone,
      company: onsiteForm.company.trim(),
      onsiteRole,
      position: job,
      startDate: onsiteForm.startDate,
      endDate: onsiteForm.endDate || null,
      onsiteStatus: 'ongoing',
    })
    onsiteVisible.value = false
    ElMessage.success('驻场人员创建成功')
    await loadDetail()
    tab.value = 'onsite'
  } catch (err) {
    console.warn('[ProjectDetail] 新建驻场人员失败:', err.message)
  } finally {
    onsiteSaving.value = false
  }
}
const deleteOnsite = async (index) => {
  // index 为筛选后表格行号，须回到 filteredOnsite 取真实记录 id
  const row = filteredOnsite.value[index]
  if (!row) return
  const id = projectId.value
  if (!row.id || !id) { ElMessage.error('缺少驻场人员 id，无法移除'); return }
  try {
    await removeProjectOnsite(id, row.id)
    ElMessage.success('已移除该驻场人员')
    await loadDetail()
    tab.value = 'onsite'
  } catch (err) {
    console.warn('[ProjectDetail] 移除驻场人员失败:', err.message)
  }
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
const candidateRows = ref([])

const filteredCandidates = computed(() => {
  const kw = (memberKeyword.value || '').trim()
  if (!kw) return candidateRows.value
  return candidateRows.value.filter((c) => c.name.includes(kw) || c.job.includes(kw))
})
const openAddMemberModal = () => {
  memberKeyword.value = ''
  const existing = (detail.value?.members || []).map((m) => m.name)
  candidateRows.value = CANDIDATES.map((c) => ({ ...c, checked: false, disabled: existing.includes(c.name) }))
  addMemberVisible.value = true
}
const confirmAddMember = async () => {
  const picked = candidateRows.value.filter((c) => c.checked && !c.disabled)
  if (!picked.length) { ElMessage.warning('请至少选择一位候选人'); return }
  const id = projectId.value
  if (!id) { ElMessage.error('缺少项目 id，无法新增成员'); return }
  addingMember.value = true
  try {
    const joinDate = todayISO()
    const names = []
    for (const c of picked) {
      // ProjectMember 实体字段：name / role（岗位）/ status / employmentType / joinDate
      await addProjectMember(id, {
        name: c.name,
        role: c.job,
        status: 'active',
        employmentType: 'fulltime',
        joinDate,
      })
      names.push(c.name)
    }
    addMemberVisible.value = false
    ElMessage.success('已添加项目成员：' + names.join('、'))
    await loadDetail()
    tab.value = 'member'
  } catch (err) {
    console.warn('[ProjectDetail] 新增项目成员失败:', err.message)
  } finally {
    addingMember.value = false
  }
}

/* ============ 编辑成员状态（PUT /members/{memberId}/status） ============ */
const memberEditVisible = ref(false)
const memberEditIndex = ref(-1)
const memberEditSaving = ref(false)
const memberEditForm = reactive({ name: '', job: '', status: '在职', leaveTime: '' })
const openMemberEdit = (index) => {
  const m = detail.value?.members?.[index]
  if (!m) return
  memberEditIndex.value = index
  memberEditForm.name = m.name
  memberEditForm.job = m.job
  memberEditForm.status = m.status
  memberEditForm.leaveTime = m.leaveTime ? m.leaveTime.substring(0, 16) : ''
  memberEditVisible.value = true
}
const saveMemberEdit = async () => {
  const m = detail.value?.members?.[memberEditIndex.value]
  if (!m) return
  const id = projectId.value
  if (!m.id || !id) { ElMessage.error('缺少成员 id，无法保存'); return }
  memberEditSaving.value = true
  try {
    // 后端落库 status（中文状态由接口归一化为 active/temp/left）；
    // 选「离职」时附弹窗所选离职时间 leaveDate（yyyy-MM-dd，未选则后端按当日）；
    // 恢复在职不传，后端清空离职日期。
    const leaveDate = memberEditForm.status === '离职' && memberEditForm.leaveTime
      ? memberEditForm.leaveTime.substring(0, 10)
      : ''
    await updateProjectMemberStatus(id, m.id, memberEditForm.status, leaveDate)
    memberEditVisible.value = false
    ElMessage.success('成员状态已更新')
    await loadDetail()
    tab.value = 'member'
  } catch (err) {
    console.warn('[ProjectDetail] 成员状态更新失败:', err.message)
  } finally {
    memberEditSaving.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.detail-page-header { display: flex; justify-content: space-between; align-items: flex-start; }

.section-card { background: #fff; border: 1px solid var(--border); border-radius: 10px; }
.section-card-header {
  padding: 16px 20px; border-bottom: 1px solid var(--border);
  display: flex; align-items: center; justify-content: space-between;
}
.section-card-title { font-size: 15px; font-weight: 600; color: #1F2937; }
.section-card-title i { color: var(--primary); margin-right: 8px; font-size: 14px; }
.section-card-body { padding: 16px 20px; }

.detail-hero-meta { display: grid; grid-template-columns: repeat(5, 1fr); gap: 32px; }
.detail-hero-meta-item .label { font-size: 12px; color: #9CA3AF; }
.detail-hero-meta-item .value { font-size: 14px; font-weight: 600; color: #1F2937; margin-top: 4px; }

.title-switch-group { display: flex; align-items: center; gap: 28px; }
.title-switch { font-size: 16px; font-weight: 600; color: var(--text-muted); cursor: pointer; padding: 6px 0; }
.title-switch.active { color: var(--text-primary); }
.title-switch-divider { width: 1px; height: 14px; background: var(--border); }

.checkin-filter { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.checkin-filter-label { font-size: 13px; color: #666; }

.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.filter-item { display: flex; align-items: center; gap: 6px; }
.filter-label { font-size: 13px; font-weight: 500; color: var(--text-secondary); white-space: nowrap; }

.btn-danger-outline {
  background: #fff; border: 1px solid var(--danger); color: var(--danger);
  border-radius: 8px; cursor: pointer; transition: background 0.15s;
}
.btn-danger-outline:hover { background: #FFF1F1; }

.form-group { margin-bottom: 16px; }
.form-label { display: block; font-size: 13px; color: var(--text-secondary); margin-bottom: 6px; }
.form-grid-2col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }

/* 候选人列表 */
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
