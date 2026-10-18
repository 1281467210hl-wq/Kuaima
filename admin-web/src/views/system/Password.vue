<template>
  <div class="password-page">
    <div class="page-header">
      <h1 class="page-title">修改密码</h1>
      <p class="page-desc">定期修改密码有助于保障账户安全</p>
    </div>

    <div class="password-container">
      <!-- 左侧：修改密码表单 -->
      <div class="password-card">
        <!-- 安全提示 -->
        <div class="password-tips">
          <div class="password-tips-icon">
            <i class="fas fa-shield-alt"></i>
          </div>
          <div class="password-tips-content">
            <div class="password-tips-title">密码安全建议</div>
            <div class="password-tips-list">
              · 密码长度 8-50 位，须同时包含字母和数字<br />
              · 建议混合大小写字母和符号，安全性更高<br />
              · 避免使用生日、手机号等个人信息<br />
              · 不要与其他网站使用相同密码
            </div>
          </div>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="当前密码" prop="oldPassword">
            <div class="password-input-wrap">
              <i class="fas fa-lock input-icon"></i>
              <input
                :type="showOld ? 'text' : 'password'"
                v-model="form.oldPassword"
                class="password-input"
                placeholder="请输入当前登录密码"
                autocomplete="current-password"
              />
              <button type="button" class="toggle-pwd" @click="showOld = !showOld">
                <i :class="showOld ? 'fas fa-eye-slash' : 'fas fa-eye'"></i>
              </button>
            </div>
          </el-form-item>

          <el-form-item label="新密码" prop="newPassword">
            <div class="password-input-wrap">
              <i class="fas fa-key input-icon"></i>
              <input
                :type="showNew ? 'text' : 'password'"
                v-model="form.newPassword"
                class="password-input"
                placeholder="请输入新密码"
                autocomplete="new-password"
                @input="onStrengthChange"
              />
              <button type="button" class="toggle-pwd" @click="showNew = !showNew">
                <i :class="showNew ? 'fas fa-eye-slash' : 'fas fa-eye'"></i>
              </button>
            </div>
            <div class="password-strength">
              <div class="strength-bar" :class="strengthClass">
                <span></span><span></span><span></span>
              </div>
              <div class="strength-text" :class="strengthClass">
                <span>密码强度</span>
                <strong>{{ strengthLabel }}</strong>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="确认新密码" prop="confirmPassword">
            <div class="password-input-wrap">
              <i class="fas fa-check-double input-icon"></i>
              <input
                :type="showConfirm ? 'text' : 'password'"
                v-model="form.confirmPassword"
                class="password-input"
                placeholder="请再次输入新密码"
                autocomplete="new-password"
                @input="onConfirmChange"
              />
              <button type="button" class="toggle-pwd" @click="showConfirm = !showConfirm">
                <i :class="showConfirm ? 'fas fa-eye-slash' : 'fas fa-eye'"></i>
              </button>
            </div>
            <div v-if="matchVisible" class="password-confirm">
              <i class="fas fa-check-circle"></i>
              <span>两次输入的密码一致</span>
            </div>
          </el-form-item>

          <div class="password-actions">
            <el-button type="primary" :loading="submitting" @click="submitChange">
              <i class="fas fa-check" style="margin-right:4px;"></i> 确认修改
            </el-button>
            <el-button @click="resetForm">重置</el-button>
          </div>
        </el-form>
      </div>

      <!-- 右侧：最近登录记录 -->
      <div class="login-history">
        <div class="login-history-title">
          <i class="fas fa-history"></i> 最近登录记录
        </div>
        <div v-if="loginHistory.length === 0" class="empty-tip">暂无登录记录</div>
        <div v-for="(item, idx) in loginHistory" :key="idx" class="login-item">
          <div class="login-info">
            <div :class="['login-icon', isSuccess(item) ? 'success' : 'danger']">
              <i :class="isSuccess(item) ? 'fas fa-sign-in-alt' : 'fas fa-exclamation-triangle'"></i>
            </div>
            <div class="login-info-text">
              <div class="login-loc">{{ item.target || '未知位置' }}</div>
              <div class="login-detail">
                IP：{{ item.ip || '-' }} · {{ formatTime(item.createTime) }}
              </div>
            </div>
          </div>
          <div :class="['login-status', isSuccess(item) ? 'success' : 'danger']">
            {{ isSuccess(item) ? '成功' : (item.result || '失败') }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { adminPasswordProblem, changeOwnPassword, listLogs } from '@/api/system'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const submitting = ref(false)

const showOld = ref(false)
const showNew = ref(false)
const showConfirm = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const matchVisible = ref(false)

const strengthClass = ref('')
const strengthLabel = ref('请输入密码')

const loginHistory = ref([])

const validateConfirm = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请确认新密码'))
    return
  }
  if (value !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        const problem = adminPasswordProblem(value)
        problem ? callback(new Error(problem)) : callback()
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

const calcStrength = (pwd) => {
  if (!pwd) return { cls: '', label: '请输入密码' }
  let score = 0
  if (pwd.length >= 8) score++
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++
  if (score <= 1) return { cls: 'weak', label: '弱' }
  if (score === 2) return { cls: 'medium', label: '中等' }
  if (score === 3) return { cls: 'medium', label: '较强' }
  return { cls: 'strong', label: '强' }
}

const onStrengthChange = () => {
  const r = calcStrength(form.newPassword)
  strengthClass.value = r.cls
  strengthLabel.value = r.label
  onConfirmChange()
}

const onConfirmChange = () => {
  if (form.newPassword && form.confirmPassword && form.newPassword === form.confirmPassword) {
    matchVisible.value = true
  } else {
    matchVisible.value = false
  }
}

const isSuccess = (item) => {
  const r = (item.result || '').trim()
  return r === '成功' || r === 'SUCCESS'
}

const formatTime = (t) => {
  if (!t) return '-'
  return String(t).replace('T', ' ').substring(0, 16)
}

const loadLoginHistory = async () => {
  try {
    const res = await listLogs({ page: 0, size: 50 })
    const d = res?.data
    const list = Array.isArray(d) ? d : (d?.content || d?.list || [])
    // 过滤登录类型的日志
    loginHistory.value = (list.filter(l => (l.type || '').indexOf('登录') >= 0)).slice(0, 8)
  } catch (e) {
    console.warn('[Password] 加载登录记录失败:', e)
    loginHistory.value = []
  }
}

const submitChange = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (form.oldPassword === form.newPassword) {
    ElMessage.error('新密码不能与原密码相同')
    return
  }

  submitting.value = true
  try {
    // 后端校验原密码；原来调用 reset-password 不校验原密码，且普通管理员无权重置自己
    await changeOwnPassword(form.oldPassword, form.newPassword)
    ElMessage.success('密码修改成功！下次登录请使用新密码')
    resetForm()
    // 2 秒后跳转登录
    setTimeout(() => {
      userStore.logout()
      router.push('/login')
    }, 2000)
  } catch (e) {
    // 错误原因（如原密码不正确）由 request 拦截器统一提示
    console.warn('[Password] 修改密码失败:', e)
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  matchVisible.value = false
  strengthClass.value = ''
  strengthLabel.value = '请输入密码'
  formRef.value?.clearValidate()
}

onMounted(() => {
  loadLoginHistory()
})

</script>

<style scoped>
.password-page {
  width: 100%;
}

.password-container {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 20px;
  align-items: start;
}

.password-card {
  background: #fff;
  border-radius: 16px;
  padding: 36px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.password-tips {
  background: linear-gradient(135deg, #FFF8E6 0%, #FFF3D6 100%);
  border: 1px solid #FDE68A;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 28px;
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.password-tips-icon {
  width: 36px;
  height: 36px;
  background: rgba(245, 158, 11, 0.15);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #F59E0B;
  flex-shrink: 0;
  font-size: 16px;
}

.password-tips-content {
  flex: 1;
}

.password-tips-title {
  font-size: 14px;
  font-weight: 600;
  color: #92400E;
  margin-bottom: 6px;
}

.password-tips-list {
  font-size: 13px;
  color: #B45309;
  line-height: 1.8;
}

.password-input-wrap {
  position: relative;
  width: 100%;
}

.password-input-wrap .input-icon {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  color: #9CA3AF;
  font-size: 15px;
  pointer-events: none;
  z-index: 1;
}

.password-input {
  width: 100%;
  height: 48px;
  padding: 0 48px 0 46px;
  border: 1px solid #E5E7EB;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
  background: #F9FAFB;
  box-sizing: border-box;
  font-family: inherit;
}

.password-input:focus {
  border-color: #FF6B35;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(255, 107, 53, 0.1);
}

.password-input-wrap .toggle-pwd {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: #9CA3AF;
  font-size: 15px;
  cursor: pointer;
  background: none;
  border: none;
  padding: 4px;
  z-index: 1;
}

.password-strength {
  margin-top: 12px;
  width: 100%;
}

.strength-bar {
  height: 6px;
  background: #E5E7EB;
  border-radius: 3px;
  overflow: hidden;
  display: flex;
  gap: 3px;
}

.strength-bar span {
  flex: 1;
  border-radius: 3px;
  transition: all 0.3s;
  background: #E5E7EB;
}

.strength-bar.weak span:nth-child(1) {
  background: #EF4444;
}

.strength-bar.medium span:nth-child(1),
.strength-bar.medium span:nth-child(2) {
  background: #F59E0B;
}

.strength-bar.strong span {
  background: #10B981;
}

.strength-text {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
}

.strength-text strong {
  font-weight: 600;
}

.strength-text.weak strong {
  color: #EF4444;
}

.strength-text.medium strong {
  color: #F59E0B;
}

.strength-text.strong strong {
  color: #10B981;
}

.password-confirm {
  background: #F0FDF4;
  border: 1px solid #BBF7D0;
  border-radius: 10px;
  padding: 12px 16px;
  font-size: 13px;
  color: #166534;
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  box-sizing: border-box;
}

.password-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}

.password-actions :deep(.el-button) {
  height: 48px;
  padding: 0 32px;
}

.login-history {
  background: #fff;
  border-radius: 16px;
  padding: 28px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  position: sticky;
  top: 20px;
}

.login-history-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
}

.login-history-title i {
  color: var(--primary);
}

.login-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
}

.login-item:last-child {
  border-bottom: none;
}

.login-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.login-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}

.login-icon.success {
  background: #ECFDF5;
  color: #10B981;
}

.login-icon.danger {
  background: #FEF2F2;
  color: #EF4444;
}

.login-info-text {
  min-width: 0;
  flex: 1;
}

.login-loc {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.login-detail {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.login-status {
  font-size: 12px;
  flex-shrink: 0;
  margin-left: 8px;
}

.login-status.success {
  color: #10B981;
}

.login-status.danger {
  color: #EF4444;
}

.empty-tip {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}

/* Element Plus 表单标签样式微调 */
.password-card :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--text-primary);
  padding-bottom: 6px;
}

.password-card :deep(.el-form-item) {
  margin-bottom: 22px;
}

@media (max-width: 1024px) {
  .password-container {
    grid-template-columns: 1fr;
  }

  .login-history {
    position: static;
  }
}
</style>
