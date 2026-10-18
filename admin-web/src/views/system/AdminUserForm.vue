<template>
  <div>
    <!-- 页面头部：与原型 admin-user.html 一致 -->
    <div class="af-page-header">
      <div style="display:flex; align-items:center; gap:12px;">
        <button class="back-btn" @click="goBack">
          <i class="fas fa-arrow-left"></i> 返回
        </button>
        <div>
          <h1 class="page-title">{{ isEdit ? '编辑账号' : '新建账号' }}</h1>
          <p class="page-desc">{{ isEdit ? '修改信息和权限配置' : '创建新的账号并分配权限' }}</p>
        </div>
      </div>
    </div>

    <div class="edit-layout">
      <div style="display:flex; flex-direction:column; gap:20px;">
        <!-- 分区 1：基本信息 -->
        <div class="form-section">
          <div class="form-section-title">
            <span class="section-num">1</span> 基本信息
          </div>
          <div class="form-group">
            <div>
              <label class="form-label">账号姓名<span class="required">*</span></label>
              <input type="text" class="form-input" v-model.trim="form.name" maxlength="50" placeholder="请输入真实姓名">
            </div>
            <div>
              <label class="form-label">登录账号<span class="required">*</span></label>
              <input type="text" class="form-input" v-model.trim="form.username" :disabled="isEdit" maxlength="50" placeholder="字母+数字组合，3-16位">
            </div>
            <div>
              <label class="form-label">手机号码<span class="required">*</span></label>
              <input type="text" class="form-input" v-model.trim="form.phone" maxlength="20" placeholder="请输入11位手机号">
            </div>
            <div>
              <label class="form-label">电子邮箱</label>
              <input type="email" class="form-input" v-model.trim="form.email" maxlength="100" placeholder="请输入邮箱地址">
            </div>
            <div v-if="!isEdit">
              <label class="form-label">登录密码<span class="required">*</span></label>
              <input type="password" class="form-input" v-model="form.password" maxlength="50" placeholder="至少8位，包含字母和数字">
            </div>
            <div v-if="!isEdit">
              <label class="form-label">确认密码<span class="required">*</span></label>
              <input type="password" class="form-input" v-model="form.password2" maxlength="50" placeholder="请再次输入密码">
            </div>
            <div class="full-width">
              <label class="form-label">备注说明</label>
              <textarea class="form-textarea" v-model.trim="form.remark" maxlength="255" placeholder="可填写该管理员的职责说明等信息"></textarea>
            </div>
          </div>
        </div>

        <!-- 分区 2：角色与权限 -->
        <div class="form-section">
          <div class="form-section-title">
            <span class="section-num">2</span> 角色与权限
          </div>
          <div style="margin-bottom:16px;">
            <label class="form-label">选择角色<span class="required">*</span></label>
            <select class="form-input" v-model="roleValue" @change="onRoleChange(roleValue)" :disabled="roleSelectDisabled">
              <option v-for="r in visibleRoleOptions" :key="r.v" :value="r.v">{{ r.label }}</option>
            </select>
            <div v-if="roleValue === 'custom'" style="margin-top:10px;">
              <label class="form-label">自定义角色名称<span class="required">*</span></label>
              <input type="text" class="form-input" v-model.trim="customRoleName" maxlength="20" :disabled="roleSelectDisabled" placeholder="请输入自定义角色名称，如：财务、客服主管">
            </div>
            <div v-if="isSuperAdminEdit" class="super-admin-notice">
              <i class="fas fa-shield-alt"></i> 超级管理员角色和权限受系统保护，不可修改
            </div>
            <div v-else-if="isEdit && !canOperate" class="super-admin-notice">
              <i class="fas fa-lock"></i> 该账号由其他管理员创建，仅可编辑基本信息，角色/权限/状态不可修改
            </div>
          </div>

          <div style="margin-top:20px;">
            <label class="form-label">权限分配<span class="required">*</span></label>
            <div class="permission-tree">
              <div v-for="m in modules" :key="m.key" class="perm-group" :class="{ open: m.open }">
                <div class="perm-row perm-group-row" @click="m.open = !m.open">
                  <i class="fas fa-caret-down perm-arrow"></i>
                  <input type="checkbox"
                         :checked="groupAllChecked(m)"
                         :indeterminate.prop="groupIndeterminate(m)"
                         :disabled="permDisabled"
                         @click.stop
                         @change="setGroupChecked(m, $event.target.checked)">
                  <span>{{ m.name }}</span>
                </div>
                <div class="perm-children">
                  <div v-for="pg in m.pages" :key="pg.key" class="perm-page" :class="{ open: pg.open }">
                    <div class="perm-row perm-page-row" @click="pg.open = !pg.open">
                      <i class="fas fa-caret-down perm-arrow" :class="{ ghost: !pg.btns.length }"></i>
                      <input type="checkbox"
                             :checked="pageAllChecked(pg)"
                             :indeterminate.prop="pageIndeterminate(pg)"
                             :disabled="permDisabled"
                             @click.stop
                             @change="setPageChecked(pg, $event.target.checked)">
                      <span>{{ pg.label }}</span>
                    </div>
                    <div v-if="pg.btns.length" class="perm-btns">
                      <label v-for="btn in pg.btns" :key="btn.key" class="perm-row perm-btn-row">
                        <input type="checkbox" v-model="btn.checked" :disabled="permDisabled">
                        <span>{{ btn.label }}</span>
                      </label>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧信息卡 -->
      <div>
        <div class="sidebar-card">
          <div class="avatar-upload">
            <el-upload
              class="avatar-uploader"
              :show-file-list="false"
              :before-upload="beforeAvatarUpload"
              :http-request="handleAvatarUpload"
              accept="image/png,image/jpeg,image/gif"
            >
              <img v-if="form.avatar" :src="form.avatar" class="avatar-img" alt="头像" />
              <div v-else class="avatar-preview">{{ avatarLetter }}</div>
              <div class="avatar-overlay">
                <i class="fas fa-camera"></i>
                <span>上传头像</span>
              </div>
            </el-upload>
            <div class="avatar-tip">支持 JPG/PNG/GIF，大小不超过 2MB</div>
            <button v-if="form.avatar" class="avatar-remove-btn" @click="form.avatar = ''">
              <i class="fas fa-times"></i> 移除头像
            </button>
          </div>

          <div style="margin-top:20px; padding-top:16px; border-top:1px solid var(--border);">
            <div class="status-row">
              <span class="status-row-label">账号状态</span>
              <div class="toggle-switch" :class="{ active: form.status === '启用', disabled: isSuperAdminEdit || !canOperate }" @click="toggleStatus"></div>
            </div>
            <div class="status-row">
              <span class="status-row-label">允许登录</span>
              <div class="toggle-switch" :class="{ active: allowLogin, disabled: isSuperAdminEdit }" @click="toggleAllowLogin"></div>
            </div>
            <div class="status-row">
              <span class="status-row-label">接收通知</span>
              <div class="toggle-switch" :class="{ active: receiveNoti }" @click="receiveNoti = !receiveNoti"></div>
            </div>
            <div class="status-row">
              <span class="status-row-label">最后登录</span>
              <span class="status-row-value" style="color:var(--text-muted);">{{ form.lastLoginTime ? fmtTime(form.lastLoginTime) : '--' }}</span>
            </div>
            <div class="status-row">
              <span class="status-row-label">创建时间</span>
              <span class="status-row-value" style="color:var(--text-muted);">{{ fmtTime(form.createTime) || '--' }}</span>
            </div>
          </div>

          <div class="action-buttons">
            <button class="btn btn-primary" :disabled="saving" @click="handleSave">
              <i class="fas fa-save"></i> {{ saving ? '保存中...' : '保存' }}
            </button>
            <button class="btn btn-outline" @click="goBack">取消</button>
          </div>
          <div style="margin-top:10px;" v-if="isEdit">
            <button class="btn btn-danger" style="width:100%;" @click="handleDelete">
              <i class="fas fa-trash-alt"></i> 删除
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminUser, createAdminUser, updateAdminUser, deleteAdminUser, adminPasswordProblem } from '@/api/system'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => route.query.mode === 'edit' || !!route.query.id)
const saving = ref(false)
const allowLogin = ref(true)
const receiveNoti = ref(true)

const form = reactive({
  name: '',
  username: '',
  phone: '',
  email: '',
  password: '',
  password2: '',
  remark: '',
  avatar: '',
  status: '启用',
  lastLoginTime: '',
  createTime: '',
  createdBy: null // 该账号的创建者管理员 ID
})

// 当前登录管理员 ID（来自 user store / localStorage）
const currentAdminId = computed(() => Number(localStorage.getItem('admin_id') || 0))
// 当前登录账号是否为超级管理员（能看到所有账号就能操作所有账号）
const currentIsSuperAdmin = computed(() => {
  const role = localStorage.getItem('admin_role') || ''
  return role === 'SUPER_ADMIN' || role === '超级管理员'
})
// 是否为当前管理员自己创建的账号
const isOwnCreated = computed(() => isEdit.value && form.createdBy != null && Number(form.createdBy) === currentAdminId.value)
// 是否可操作角色/权限/状态/密码：当前为超级管理员，或该账号是自己创建的
const canOperate = computed(() => !isEdit.value || currentIsSuperAdmin.value || isOwnCreated.value)

// ====== 角色 ======
// 原型下拉框 4 种角色
const roleOptions = [
  { v: 'super', label: '超级管理员' },
  { v: 'admin', label: '管理员' },
  { v: 'audit', label: '审核员' },
  { v: 'custom', label: '自定义' }
]
const BACKEND_ROLE = { super: 'SUPER_ADMIN', admin: 'ADMIN', audit: 'EDITOR', viewer: 'VIEWER' }
const ROLE_FROM_BACKEND = Object.fromEntries(Object.entries(BACKEND_ROLE).map(([k, v]) => [v, k]))
const roleValue = ref('admin')
// 自定义角色名称（roleValue === 'custom' 时手动输入，直接作为后端 role 存储）
const customRoleName = ref('')
// 超级管理员保护：编辑超级管理员时角色和权限不可修改
const isSuperAdminEdit = computed(() => isEdit.value && roleValue.value === 'super')
// 新建时下拉框不展示"超级管理员"选项（后端禁止新建超级管理员）
const visibleRoleOptions = computed(() => isEdit.value ? roleOptions : roleOptions.filter(r => r.v !== 'super'))
// 角色 select 是否禁用：编辑超级管理员 / 无权操作 时禁用
const roleSelectDisabled = computed(() => isSuperAdminEdit.value || !canOperate.value)
// 权限树是否禁用：同上
const permDisabled = computed(() => isSuperAdminEdit.value || !canOperate.value)

// ====== 权限树 ======
const makeBtn = (key, label) => ({ key, label, checked: false })
const makePage = (key, label, btns = []) => ({ key, label, checked: false, open: true, btns })
const makeGroup = (key, name, pages) => ({ key, name, open: true, pages })

// 与原型一致：一级模块 → 二级页面 → 三级按钮，默认全部展开
const modules = reactive([
  makeGroup('permStats', '数据统计', [
    makePage('dashboard', '数据概览')
  ]),
  makeGroup('permUser', '用户管理', [
    makePage('workers', '零工管理', [makeBtn('view', '查看'), makeBtn('freeze', '冻结'), makeBtn('unfreeze', '解冻')]),
    makePage('bosses', '老板管理', [makeBtn('view', '查看'), makeBtn('freeze', '冻结'), makeBtn('unfreeze', '解冻')]),
    makePage('credits', '信用分管理', [makeBtn('detail', '明细'), makeBtn('adjust', '调整')]),
    makePage('cert', '企业认证', [makeBtn('detail', '详情'), makeBtn('approve', '通过'), makeBtn('reject', '拒绝'), makeBtn('unfreeze', '解冻')]),
    makePage('insurance', '保险管理', [makeBtn('detail', '详情'), makeBtn('edit', '编辑')])
  ]),
  makeGroup('permJob', '招工管理', [
    makePage('jobs', '招工管理', [makeBtn('view', '查看'), makeBtn('applicants', '报名人员'), makeBtn('edit', '编辑'), makeBtn('toggle', '开启')]),
    makePage('audit', '招工审核', [makeBtn('detail', '查看详情'), makeBtn('approve', '通过'), makeBtn('reject', '拒绝'), makeBtn('reaudit', '重新审核')])
  ]),
  makeGroup('permOrder', '订单结算', [
    makePage('orders', '用工订单'),
    makePage('settlement', '结算管理', [makeBtn('view', '查看'), makeBtn('settle', '结算')])
  ]),
  makeGroup('permMarketing', '营销管理', [
    makePage('points', '积分管理', [makeBtn('detail', '详情'), makeBtn('pay', '支付')]),
    makePage('coupons', '优惠券管理', [makeBtn('edit', '编辑'), makeBtn('disable', '停用'), makeBtn('delete', '删除')]),
    makePage('rewards', '奖励金管理')
  ]),
  makeGroup('permContent', '内容管理', [
    makePage('academy', '学堂管理', [makeBtn('create', '新增课程'), makeBtn('edit', '编辑课程'), makeBtn('delete', '删除课程')]),
    makePage('notices', '公告管理'),
    makePage('rules', '规则管理'),
    makePage('agreements', '协议管理')
  ]),
  makeGroup('permService', '消息客服', [
    makePage('messages', '消息管理'),
    makePage('service', '客服管理')
  ]),
  makeGroup('permSystem', '系统管理', [
    makePage('settings', '系统设置'),
    makePage('logs', '操作日志')
  ]),
  makeGroup('permEnterprise', '企业工作台', [
    makePage('employees', '员工管理'),
    makePage('payroll', '发薪管理')
  ])
])

const pageAllChecked = (p) => p.btns.length ? p.btns.every(b => b.checked) : p.checked
const pageIndeterminate = (p) => p.btns.length > 0 && !pageAllChecked(p) && p.btns.some(b => b.checked)
const groupAllChecked = (m) => m.pages.every(pageAllChecked)
const groupIndeterminate = (m) => !groupAllChecked(m) && m.pages.some(p => pageAllChecked(p) || pageIndeterminate(p))
const setPageChecked = (p, checked) => {
  p.checked = checked
  p.btns.forEach(b => { b.checked = checked })
}
const setGroupChecked = (m, checked) => m.pages.forEach(p => setPageChecked(p, checked))

const onRoleChange = (v) => {
  roleValue.value = v
  if (v !== 'custom') customRoleName.value = ''
  if (v === 'super') {
    // 原型：超级管理员自动全选并展开所有模块
    modules.forEach(m => {
      setGroupChecked(m, true)
      m.open = true
      m.pages.forEach(p => { p.open = true })
    })
  }
}

// ====== 头像 ======
const avatarLetter = computed(() => (form.name || '').charAt(0) || '管')
const avatarUploading = ref(false)

const beforeAvatarUpload = (file) => {
  const validTypes = ['image/png', 'image/jpeg', 'image/gif']
  if (!validTypes.includes(file.type)) {
    ElMessage.error('仅支持 JPG/PNG/GIF 格式')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('头像大小不能超过 2MB')
    return false
  }
  return true
}

const handleAvatarUpload = (option) => {
  avatarUploading.value = true
  const reader = new FileReader()
  reader.onload = (e) => {
    form.avatar = e.target.result
    avatarUploading.value = false
    ElMessage.success('头像上传成功')
  }
  reader.onerror = () => {
    avatarUploading.value = false
    ElMessage.error('头像读取失败')
  }
  reader.readAsDataURL(option.file)
}

// ====== 工具 ======
const fmtTime = (v) => {
  if (!v) return ''
  const s = String(v)
  if (s.includes('T')) return s.replace('T', ' ').slice(0, 16)
  return s.length > 16 ? s.slice(0, s.lastIndexOf(':')) : s
}
// 统一返回策略：始终返回系统设置的权限管理标签页
const navigateBack = () => {
  router.push('/admin/settings?tab=permission')
}
const goBack = () => {
  navigateBack()
}
const toggleStatus = () => {
  if (isSuperAdminEdit.value) {
    ElMessage.warning('超级管理员账号不可禁用')
    return
  }
  if (!canOperate.value) {
    ElMessage.warning('仅可修改自己创建的账号的状态')
    return
  }
  form.status = form.status === '启用' ? '禁用' : '启用'
}

const toggleAllowLogin = () => {
  if (isSuperAdminEdit.value) {
    ElMessage.warning('超级管理员账号不可禁止登录')
    return
  }
  allowLogin.value = !allowLogin.value
}

// 旧版两层权限 key → 新版「模块/页面/按钮」的兼容映射，保证历史账号权限不丢失
const LEGACY_PERM_MAP = {
  'permUser.u1': { group: 'permUser', page: 'workers' },
  'permUser.u2': { group: 'permUser', page: 'bosses' },
  'permJob.j1': { group: 'permJob', page: 'jobs' },
  'permJob.j2': { group: 'permJob', page: 'audit' },
  'permJob.j3': { group: 'permJob', page: 'jobs', btn: 'applicants' },
  'permJob.j4': { group: 'permJob', page: 'jobs', btn: 'toggle' },
  'permOrder.o1': { group: 'permOrder', page: 'orders' },
  'permOrder.o3': { group: 'permOrder', page: 'settlement' },
  'permContent.c1': { group: 'permUser', page: 'cert' },
  'permContent.c3': { group: 'permContent', page: 'notices' },
  'permContent.c4': { group: 'permContent', page: 'rules' },
  'permService.s1': { group: 'permService', page: 'messages' },
  'permService.s2': { group: 'permService', page: 'service' },
  'permSystem.sys1': { group: 'permMarketing', page: 'points' },
  'permSystem.sys3': { group: 'permSystem', page: 'settings' },
  'permSystem.sys4': { group: 'permSystem', page: 'logs' }
}

/** 回显权限：新格式 {moduleKey:{pageKey:true|{btnKey:true}}}，兼容旧格式 {moduleKey:{itemKey:true}} */
const applyPermissions = (permData) => {
  if (!permData || typeof permData !== 'object') return
  modules.forEach(m => {
    const modPerm = permData[m.key]
    if (!modPerm || typeof modPerm !== 'object') return
    m.pages.forEach(pg => {
      const value = modPerm[pg.key]
      if (value === true) {
        setPageChecked(pg, true)
      } else if (value && typeof value === 'object') {
        pg.btns.forEach(b => { b.checked = value[b.key] === true })
        pg.checked = pg.btns.length ? pg.btns.every(b => b.checked) : Object.values(value).some(Boolean)
      }
    })
  })
  Object.entries(LEGACY_PERM_MAP).forEach(([legacyKey, target]) => {
    const [groupKey, itemKey] = legacyKey.split('.')
    if (!permData[groupKey]?.[itemKey]) return
    const group = modules.find(m => m.key === target.group)
    const page = group?.pages.find(pg => pg.key === target.page)
    if (!page) return
    if (target.btn) {
      const btn = page.btns.find(b => b.key === target.btn)
      if (btn) btn.checked = true
    } else {
      setPageChecked(page, true)
    }
  })
}

// ====== 数据加载（编辑模式） ======
const loadDetail = async () => {
  try {
    const res = await getAdminUser(route.query.id)
    const d = res?.data || {}
    form.name = d.name || ''
    form.username = d.username || ''
    form.phone = d.phone || ''
    form.email = d.email || ''
    form.remark = d.remark || ''
    form.avatar = d.avatar || ''
    form.status = d.status || '启用'
    form.lastLoginTime = d.lastLoginTime || ''
    form.createTime = d.createTime || ''
    form.createdBy = d.createdBy ?? null
    allowLogin.value = d.allowLogin !== false
    receiveNoti.value = d.receiveNotifications !== false
    const mappedRole = ROLE_FROM_BACKEND[d.role]
    if (mappedRole && roleOptions.some(r => r.v === mappedRole)) {
      roleValue.value = mappedRole
    } else {
      roleValue.value = 'custom'
      customRoleName.value = d.role || ''
    }
    // 回显权限树（新三层格式 + 旧两层格式兼容）
    if (d.permissions) {
      try {
        const permData = typeof d.permissions === 'string' ? JSON.parse(d.permissions) : d.permissions
        applyPermissions(permData)
      } catch (e) {
        console.warn('[AdminUserForm] 解析权限数据失败:', e)
      }
    }
  } catch (e) {
    console.warn('[AdminUserForm] 加载详情失败:', e)
  }
}

/** 收集权限树勾选状态为 JSON 对象 */
const collectPermissions = () => {
  const result = {}
  modules.forEach(m => {
    const pages = {}
    m.pages.forEach(pg => {
      pages[pg.key] = pg.btns.length
        ? Object.fromEntries(pg.btns.filter(b => b.checked).map(b => [b.key, true]))
        : pg.checked
    })
    result[m.key] = pages
  })
  return result
}

// ====== 提交 ======
const validate = () => {
  if (!form.name) return '请输入管理员姓名'
  if (!form.username) return '请输入登录账号'
  if (!/^[A-Za-z0-9]{3,16}$/.test(form.username)) return '登录账号需为 3-16 位字母或数字组合'
  if (!form.phone) return '请输入手机号码'
  if (!/^1\d{10}$/.test(form.phone)) return '请输入正确的11位手机号'
  if (form.email && !/^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(form.email)) return '请输入正确的邮箱地址'
  // 自定义角色：校验手动输入的角色名（oper 为原型预留角色，后端映射为 ADMIN）
  if (!isSuperAdminEdit.value && canOperate.value && roleValue.value === 'custom') {
    if (!customRoleName.value) return '请输入自定义角色名称'
    if (['SUPER_ADMIN', '超级管理员'].includes(customRoleName.value)) return '自定义角色名称不能为超级管理员'
  }
  if (!isEdit.value) {
    if (!form.password) return '请设置登录密码'
    const passwordProblem = adminPasswordProblem(form.password)
    if (passwordProblem) return passwordProblem
    if (form.password !== form.password2) return '两次输入的密码不一致'
  }
  return ''
}

const handleSave = async () => {
  const msg = validate()
  if (msg) {
    ElMessage.warning(msg)
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name,
      phone: form.phone,
      email: form.email || null,
      remark: form.remark || null,
      avatar: form.avatar || null
    }
    // 超级管理员（被编辑对象）/ 无权操作：不提交 role/permissions/status（后端会拒绝，前端先过滤）
    if (!isSuperAdminEdit.value && canOperate.value) {
      // 自定义角色直接提交手动输入的角色名；其余角色走预设映射（oper 暂无映射，兜底 ADMIN）
      payload.role = roleValue.value === 'custom'
        ? customRoleName.value
        : (BACKEND_ROLE[roleValue.value] || 'ADMIN')
      payload.permissions = JSON.stringify(collectPermissions())
      payload.status = form.status
      payload.allowLogin = allowLogin.value
    }
    if (canOperate.value) {
      payload.receiveNotifications = receiveNoti.value
    }
    if (isEdit.value) {
      if (form.password && canOperate.value) payload.password = form.password
      await updateAdminUser(route.query.id, payload)
      ElMessage.success('管理员信息保存成功！')
    } else {
      payload.username = form.username
      payload.password = form.password
      await createAdminUser(payload)
      ElMessage.success('管理员创建成功！')
    }
    navigateBack()
  } catch (e) {
    console.warn('[AdminUserForm] 保存失败:', e)
    // 具体错误信息已由请求拦截器提示
  } finally {
    saving.value = false
  }
}

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm('确定要删除该管理员吗？此操作不可恢复。', '删除确认', { type: 'warning' })
    await deleteAdminUser(route.query.id)
    ElMessage.success('管理员已删除')
    navigateBack()
  } catch (e) {
    if (e !== 'cancel') console.warn('[AdminUserForm] 删除失败:', e)
  }
}

onMounted(() => {
  if (isEdit.value) {
    loadDetail()
  }
})
</script>

<style scoped>
.af-page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}
.back-btn:hover {
  color: var(--primary);
  border-color: var(--primary);
}
.edit-layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 20px;
  align-items: start;
}
.form-section {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.form-section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  gap: 8px;
}
.form-section-title .section-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.form-group {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px 24px;
}
.form-group .full-width {
  grid-column: 1 / -1;
}
.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.form-label .required {
  color: var(--danger);
  margin-left: 2px;
}
.form-input,
.form-select,
.form-textarea {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 14px;
  color: var(--text-primary);
  background: #fff;
  transition: border-color 0.2s, box-shadow 0.2s;
  font-family: inherit;
}
.form-input:disabled {
  background: var(--bg-page);
  color: var(--text-muted);
  cursor: not-allowed;
}
.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(255, 107, 53, 0.1);
}
.form-textarea {
  resize: vertical;
  min-height: 80px;
}
.permission-tree {
  border: 1px solid var(--border);
  border-radius: 6px;
  background: #fff;
  max-height: 560px;
  overflow-y: auto;
}
.perm-group {
  border-bottom: 1px solid var(--border);
}
.perm-group:last-child {
  border-bottom: none;
}
.perm-row {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}
.perm-row:hover {
  background: var(--bg-page);
}
.perm-group-row {
  padding: 10px 14px;
  background: var(--bg-page);
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}
.perm-arrow {
  width: 14px;
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
  flex-shrink: 0;
  transition: transform 0.15s;
}
.perm-arrow.ghost {
  visibility: hidden;
}
.perm-group:not(.open) > .perm-group-row .perm-arrow,
.perm-page:not(.open) > .perm-page-row .perm-arrow {
  transform: rotate(-90deg);
}
.perm-children {
  display: none;
}
.perm-group.open > .perm-children {
  display: block;
  padding: 4px 0;
}
.perm-page-row {
  padding: 7px 14px 7px 26px;
  font-size: 13px;
  color: var(--text-primary);
}
.perm-btns {
  display: none;
}
.perm-page.open > .perm-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  padding: 2px 14px 8px 48px;
}
.perm-btn-row {
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
}
.perm-row input[type='checkbox'] {
  accent-color: var(--primary);
  flex-shrink: 0;
  cursor: pointer;
}
.sidebar-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  position: sticky;
  top: 20px;
}
.avatar-upload {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.avatar-uploader {
  position: relative;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  cursor: pointer;
  overflow: hidden;
}
.avatar-uploader :deep(.el-upload) {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.avatar-img {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  object-fit: cover;
}
.avatar-preview {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff6b35, #ff8c42);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 32px;
  font-weight: 600;
}
.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.2s;
}
.avatar-uploader:hover .avatar-overlay {
  opacity: 1;
}
.avatar-tip {
  font-size: 11px;
  color: var(--text-muted);
  text-align: center;
}
.avatar-remove-btn {
  padding: 4px 10px;
  font-size: 12px;
  color: var(--danger);
  border: 1px solid var(--danger);
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}
.avatar-remove-btn:hover {
  background: #fef2f2;
}
.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}
.status-row:last-child {
  border-bottom: none;
}
.status-row-label {
  font-size: 13px;
  color: var(--text-secondary);
}
.status-row-value {
  font-size: 13px;
  font-weight: 500;
}
.toggle-switch {
  position: relative;
  width: 44px;
  height: 24px;
  background: #e5e7eb;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s;
  flex-shrink: 0;
}
.toggle-switch.active {
  background: var(--primary);
}
.toggle-switch::after {
  content: '';
  position: absolute;
  width: 20px;
  height: 20px;
  background: #fff;
  border-radius: 50%;
  top: 2px;
  left: 2px;
  transition: left 0.2s;
}
.toggle-switch.active::after {
  left: 22px;
}
.toggle-switch.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.action-buttons {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}
/* 全局 .btn 样式已在 index.scss 中定义，此处仅保留表单底部按钮布局需求 */
.btn-primary {
  flex: 1;
}
.btn-primary:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.btn-danger {
  background: #fff;
  color: var(--danger);
  border-color: var(--danger);
}
.btn-danger:hover {
  background: var(--danger);
  color: #fff;
}

@media (max-width: 1200px) {
  .edit-layout {
    grid-template-columns: 1fr;
  }
  .sidebar-card {
    position: static;
  }
}
.super-admin-notice {
  margin-top: 8px;
  padding: 8px 12px;
  background: #FEF3C7;
  color: #D97706;
  border-radius: 8px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
