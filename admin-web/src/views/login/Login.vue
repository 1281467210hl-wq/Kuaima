<template>
  <div class="login-page">
    <!-- 品牌区 -->
    <div class="brand-side">
      <div class="brand-content">
        <div class="brand-logo">
          <div class="brand-logo-icon">
            <i class="fas fa-bolt"></i>
          </div>
          <div class="brand-logo-text">快马日结</div>
        </div>

        <div class="brand-title">
          一站式<br>
          <span>零工服务平台</span>
        </div>
        <div class="brand-desc">
          连接企业雇主与务工人员，提供从招工发布、智能匹配、在线接单到薪资结算的全链路服务，助力灵活就业新模式。
        </div>

        <div class="brand-features">
          <div class="brand-feature">
            <div class="brand-feature-icon f1">
              <i class="fas fa-briefcase"></i>
            </div>
            <div>
              <div class="brand-feature-title">智能招工</div>
              <div class="brand-feature-desc">AI推荐匹配最合适的零工</div>
            </div>
          </div>
          <div class="brand-feature">
            <div class="brand-feature-icon f2">
              <i class="fas fa-shield-alt"></i>
            </div>
            <div>
              <div class="brand-feature-title">实名认证</div>
              <div class="brand-feature-desc">确保每笔交易真实可信</div>
            </div>
          </div>
          <div class="brand-feature">
            <div class="brand-feature-icon f3">
              <i class="fas fa-coins"></i>
            </div>
            <div>
              <div class="brand-feature-title">日结薪资</div>
              <div class="brand-feature-desc">完工即结，快速到账</div>
            </div>
          </div>
          <div class="brand-feature">
            <div class="brand-feature-icon f4">
              <i class="fas fa-chart-line"></i>
            </div>
            <div>
              <div class="brand-feature-title">数据可视</div>
              <div class="brand-feature-desc">多维度运营数据看板</div>
            </div>
          </div>
        </div>
      </div>

      <div class="brand-stats">
        <div>
          <div class="brand-stat-value">120万+</div>
          <div class="brand-stat-label">注册零工</div>
        </div>
        <div>
          <div class="brand-stat-value">3万+</div>
          <div class="brand-stat-label">入驻企业</div>
        </div>
        <div>
          <div class="brand-stat-value">98%</div>
          <div class="brand-stat-label">好评率</div>
        </div>
      </div>
    </div>

    <!-- 表单区 -->
    <div class="form-side">
      <div class="login-form-wrapper">
        <div class="form-header">
          <div class="form-title">管理员登录</div>
          <div class="form-subtitle">欢迎回到快马日结管理后台</div>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          @submit.prevent="handleLogin"
          size="large"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入管理员账号"
              prefix-icon="User"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入登录密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item prop="captcha">
            <div class="captcha-box">
              <el-input
                v-model="form.captcha"
                placeholder="请输入验证码"
                maxlength="4"
                prefix-icon="Shield"
              />
              <div class="captcha-img" @click="refreshCaptcha">
                {{ captchaCode }}
              </div>
            </div>
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="form.remember">7天内免登录</el-checkbox>
            <el-link type="primary" :underline="false" @click="showForgot">
              忘记密码？
            </el-link>
          </div>

          <el-button
            type="primary"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>

          <div class="form-footer">
            登录即表示同意 <el-link type="primary" :underline="false">《管理员服务协议》</el-link>
            和 <el-link type="primary" :underline="false">《信息保密协议》</el-link>
          </div>
        </el-form>
      </div>
    </div>

    <div class="page-footer">
      © 2024 快马日结 · 管理后台 v2.0 | 技术支持：快马科技
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const captchaCode = ref('')

const form = reactive({
  username: 'admin',
  password: 'admin123',
  captcha: '',
  remember: true
})

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }],
  captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

const refreshCaptcha = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let code = ''
  for (let i = 0; i < 4; i++) {
    code += chars[Math.floor(Math.random() * chars.length)]
  }
  captchaCode.value = code
}

const handleLogin = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  
  if (form.captcha.toUpperCase() !== captchaCode.value) {
    ElMessage.error('验证码错误')
    refreshCaptcha()
    return
  }
  
  loading.value = true
  try {
    const res = await userStore.loginAction(form.username, form.password)
    if (res?.success) {
      ElMessage.success('登录成功，正在跳转...')
      router.push('/admin/dashboard')
    } else {
      ElMessage.error(res?.message || '登录失败，请检查账号密码')
    }
  } catch (error) {
    // 业务错误和 HTTP 错误已由 request 拦截器展示后端返回的具体原因
    if (error?.message?.includes('Network Error')) {
      ElMessage.error('无法连接服务器，请检查后端服务是否启动')
    }
  } finally {
    loading.value = false
  }
}

const showForgot = () => {
  ElMessage.info('请联系系统管理员重置密码')
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  background: #fff;
}

.brand-side {
  flex: 1;
  background: linear-gradient(135deg, #1E293B 0%, #334155 40%, #475569 100%);
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 60px;
  color: #fff;
  
  &::before {
    content: '';
    position: absolute;
    top: -20%;
    right: -10%;
    width: 500px;
    height: 500px;
    background: radial-gradient(circle, rgba(255,107,53,0.25) 0%, transparent 70%);
    pointer-events: none;
  }
  
  &::after {
    content: '';
    position: absolute;
    bottom: -15%;
    left: -10%;
    width: 400px;
    height: 400px;
    background: radial-gradient(circle, rgba(37,99,235,0.2) 0%, transparent 70%);
    pointer-events: none;
  }
}

.brand-content {
  position: relative;
  z-index: 1;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 48px;
}

.brand-logo-icon {
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, #FF6B35, #FF8C5A);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.brand-logo-text {
  font-size: 18px;
  font-weight: 700;
}

.brand-title {
  font-size: 38px;
  font-weight: 700;
  line-height: 1.3;
  margin-bottom: 16px;
  
  span {
    background: linear-gradient(135deg, #FF8C42, #FFB800);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
}

.brand-desc {
  font-size: 16px;
  color: rgba(255,255,255,0.7);
  line-height: 1.8;
  max-width: 480px;
  margin-bottom: 48px;
}

.brand-features {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  max-width: 520px;
}

.brand-feature {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.brand-feature-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  
  &.f1 { background: rgba(255,107,53,0.15); color: #FF8C42; }
  &.f2 { background: rgba(37,99,235,0.15); color: #60A5FA; }
  &.f3 { background: rgba(16,185,129,0.15); color: #34D399; }
  &.f4 { background: rgba(245,158,11,0.15); color: #FBBF24; }
}

.brand-feature-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}

.brand-feature-desc {
  font-size: 12px;
  color: rgba(255,255,255,0.6);
  line-height: 1.5;
}

.brand-stats {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 40px;
  padding-top: 32px;
  border-top: 1px solid rgba(255,255,255,0.1);
}

.brand-stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #FF8C42;
}

.brand-stat-label {
  font-size: 13px;
  color: rgba(255,255,255,0.6);
}

.form-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 60px;
  background: #fff;
}

.login-form-wrapper {
  width: 100%;
  max-width: 420px;
}

.form-header {
  margin-bottom: 36px;
}

.form-title {
  font-size: 28px;
  font-weight: 700;
  color: #1F2937;
  margin-bottom: 8px;
}

.form-subtitle {
  font-size: 14px;
  color: #6B7280;
}

.captcha-box {
  display: flex;
  gap: 12px;
  width: 100%;
  
  .el-input {
    flex: 1;
  }
}

.captcha-img {
  width: 120px;
  height: 40px;
  border-radius: 8px;
  background: linear-gradient(135deg, #FEF3C7 0%, #FDE68A 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 5px;
  color: #B45309;
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
  font-size: 13px;
}

.login-btn {
  width: 100%;
  height: 50px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 4px;
  background: linear-gradient(135deg, #FF6B35 0%, #FF8C5A 100%);
  border: none;
  box-shadow: 0 4px 14px rgba(255,107,53,0.3);
  
  &:hover {
    background: linear-gradient(135deg, #E55A2B 0%, #FF6B35 100%);
  }
}

.form-footer {
  margin-top: 32px;
  text-align: center;
  font-size: 13px;
  color: #9CA3AF;
}

.page-footer {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  color: #9CA3AF;
  font-size: 12px;
  text-align: center;
  z-index: 10;
}

@media (max-width: 900px) {
  .brand-side {
    display: none;
  }
  .form-side {
    padding: 40px 24px;
  }
}
</style>
