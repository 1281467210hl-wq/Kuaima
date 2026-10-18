<template>
  <div class="settings-page">
    <div class="page-header">
      <h1 class="page-title">系统设置</h1>
      <p class="page-desc">平台参数、账号权限、规则配置</p>
    </div>

    <div class="card">
      <el-tabs v-model="activeTab" class="settings-tabs">

        <!-- ============ Tab 1: 基础设置 ============ -->
        <el-tab-pane name="basic">
          <template #label>
            <span class="tab-label">
              <i class="fas fa-cog"></i> 基础设置
            </span>
          </template>

          <!-- 平台基础信息 -->
          <div class="settings-section-title">
            <i class="fas fa-info-circle"></i> 平台基础信息
          </div>
          <div class="settings-form-grid">
            <div class="form-group full-width">
              <label class="form-label">平台名称</label>
              <el-input v-model="platformForm.name" placeholder="请输入平台名称" />
            </div>
            <div class="form-group">
              <label class="form-label">平台Logo</label>
              <el-upload
                class="logo-upload"
                :show-file-list="false"
                :before-upload="beforeLogoUpload"
                :http-request="handleLogoUpload"
                accept="image/png,image/jpeg,image/svg+xml"
              >
                <img v-if="logoPreviewUrl" :src="logoPreviewUrl" class="logo-preview" alt="logo" />
                <div v-else class="logo-upload-placeholder">
                  <i class="fas fa-cloud-upload-alt"></i>
                  <span>上传Logo</span>
                </div>
                <div v-if="logoUploading" class="logo-upload-mask">
                  <i class="fas fa-spinner fa-spin"></i>
                  <span>上传中...</span>
                </div>
              </el-upload>
              <div class="logo-upload-tip">建议尺寸 200x60px，支持 PNG/JPG/SVG，大小不超过 2MB</div>
            </div>
            <div class="form-group">
              <label class="form-label">客服电话</label>
              <el-input v-model="platformForm.phone" placeholder="请输入客服电话" />
            </div>
            <div class="form-group">
              <label class="form-label">营业时间</label>
              <div class="time-range">
                <el-time-picker
                  v-model="platformForm.workStart"
                  format="HH:mm"
                  value-format="HH:mm"
                  placeholder="开始时间"
                  style="flex:1;"
                />
                <span class="time-sep">至</span>
                <el-time-picker
                  v-model="platformForm.workEnd"
                  format="HH:mm"
                  value-format="HH:mm"
                  placeholder="结束时间"
                  style="flex:1;"
                />
              </div>
            </div>
            <div class="form-group">
              <label class="form-label">版本号</label>
              <el-input v-model="platformForm.version" readonly class="readonly-input" />
            </div>
            <div class="form-group full-width">
              <label class="form-label">平台简介</label>
              <el-input
                v-model="platformForm.intro"
                type="textarea"
                :rows="4"
                placeholder="请输入平台简介..."
              />
            </div>
          </div>

          <!-- 平台服务费收取账号 -->
          <div class="settings-section-title" style="margin-top:28px;">
            <i class="fas fa-university"></i> 平台服务费收取账号
          </div>
          <div class="account-desc">老板结算时在零工工资之外支付的平台服务费，收取至以下指定账户</div>

          <div class="account-tabs">
            <div
              :class="['account-tab', { active: accountTab === 'bank' }]"
              @click="accountTab = 'bank'"
            >
              <i class="fas fa-credit-card"></i> 银行账户
            </div>
            <div
              :class="['account-tab', { active: accountTab === 'wallet' }]"
              @click="accountTab = 'wallet'"
            >
              <i class="fas fa-wallet"></i> 电子钱包
            </div>
          </div>

          <!-- Bank panel -->
          <div v-show="accountTab === 'bank'" class="account-panel">
            <div class="bank-card">
              <div class="bank-card-header">
                <div>
                  <div class="bank-card-title">收款银行</div>
                  <div class="bank-card-bank">{{ bankInfo.bankName }}</div>
                </div>
                <div class="bank-card-logo"><i class="fas fa-building"></i></div>
              </div>
              <div class="bank-card-number">{{ bankInfo.cardNumber }}</div>
              <div class="bank-card-footer">
                <div class="bank-card-holder">
                  账户名
                  <span>{{ bankInfo.holder }}</span>
                </div>
                <div class="bank-card-status">
                  <i class="fas fa-check-circle"></i> 已认证
                </div>
              </div>
            </div>

            <div class="account-list">
              <div class="account-item">
                <div class="account-item-header">
                  <div class="account-item-title">
                    <i class="fas fa-info-circle" style="color:var(--primary);"></i> 账户详细信息
                  </div>
                  <button class="account-edit-btn" @click="openBankEdit">
                    <i class="fas fa-edit"></i> 编辑
                  </button>
                </div>
                <div class="account-item-body">
                  <div class="account-field">
                    <span class="account-field-label">开户支行</span>
                    <span class="account-field-value">{{ bankInfo.branch }}</span>
                  </div>
                  <div class="account-field">
                    <span class="account-field-label">SWIFT代码</span>
                    <span class="account-field-value">{{ bankInfo.swiftCode }}</span>
                  </div>
                  <div class="account-field">
                    <span class="account-field-label">联行号</span>
                    <span class="account-field-value">{{ bankInfo.bankCode }}</span>
                  </div>
                  <div class="account-field">
                    <span class="account-field-label">账户类型</span>
                    <span class="account-field-value">{{ bankInfo.accountType }}</span>
                  </div>
                </div>
              </div>

              <div class="account-item">
                <div class="account-item-header">
                  <div class="account-item-title">
                    <i class="fas fa-shield-alt" style="color:var(--primary);"></i> 账户状态
                  </div>
                </div>
                <div class="account-item-body">
                  <div class="account-field">
                    <span class="account-field-label">启用状态</span>
                    <span class="account-field-value" style="color:var(--success);">
                      <i class="fas fa-check-circle"></i> 已启用
                    </span>
                  </div>
                  <div class="account-field">
                    <span class="account-field-label">验证时间</span>
                    <span class="account-field-value">2024-01-15</span>
                  </div>
                  <div class="account-field">
                    <span class="account-field-label">累计收取服务费</span>
                    <span class="account-field-value">¥ 1,258,960.00</span>
                  </div>
                  <div class="account-field">
                    <span class="account-field-label">本月收取</span>
                    <span class="account-field-value">¥ 86,420.00</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Wallet panel -->
          <div v-show="accountTab === 'wallet'" class="account-panel">
            <div class="wallet-card">
              <div class="wallet-card-icon alipay">
                <i class="fab fa-alipay"></i>
              </div>
              <div class="wallet-card-info">
                <div class="wallet-card-name">
                  支付宝收款账户
                  <span v-if="walletInfo.alipay.isDefault" class="wallet-default-tag">默认</span>
                </div>
                <div class="wallet-card-account">{{ maskWalletAccount('alipay', walletInfo.alipay.account) }}</div>
              </div>
              <div class="wallet-card-actions">
                <button class="account-edit-btn" @click="openWalletEdit('alipay')">
                  <i class="fas fa-edit"></i> 编辑
                </button>
                <button
                  v-if="!walletInfo.alipay.isDefault"
                  class="account-edit-btn account-edit-primary"
                  @click="setWalletDefault('alipay')"
                >
                  <i class="fas fa-star"></i> 默认
                </button>
              </div>
            </div>

            <div class="wallet-card">
              <div class="wallet-card-icon wechat">
                <i class="fab fa-weixin"></i>
              </div>
              <div class="wallet-card-info">
                <div class="wallet-card-name">
                  微信收款账户
                  <span v-if="walletInfo.wechat.isDefault" class="wallet-default-tag">默认</span>
                </div>
                <div class="wallet-card-account">{{ maskWalletAccount('wechat', walletInfo.wechat.account) }}</div>
              </div>
              <div class="wallet-card-actions">
                <button class="account-edit-btn" @click="openWalletEdit('wechat')">
                  <i class="fas fa-edit"></i> 编辑
                </button>
                <button
                  v-if="!walletInfo.wechat.isDefault"
                  class="account-edit-btn account-edit-primary"
                  @click="setWalletDefault('wechat')"
                >
                  <i class="fas fa-star"></i> 默认
                </button>
              </div>
            </div>

            <div class="account-item" style="margin-top:12px;">
              <div class="account-item-header">
                <div class="account-item-title">
                  <i class="fas fa-exclamation-triangle" style="color:var(--warning);"></i> 注意事项
                </div>
              </div>
              <div class="account-notice">
                1. 平台服务费将在订单结算时自动扣除至指定账户<br>
                2. 请确保收款账户信息准确，避免资金转错<br>
                3. 如需更换收款账户，请提前通知财务部门并完成验证<br>
                4. 服务费收取记录可在"结算管理"中查看详细明细
              </div>
            </div>
          </div>

          <div class="settings-actions">
            <button class="btn btn-primary" @click="savePlatform">
              <i class="fas fa-save"></i> 保存设置
            </button>
            <button class="btn btn-outline" @click="resetPlatform">重置</button>
          </div>
        </el-tab-pane>

        <!-- ============ Tab 2: 权限管理 ============ -->
        <el-tab-pane name="permission">
          <template #label>
            <span class="tab-label">
              <i class="fas fa-user-shield"></i> 权限管理
            </span>
          </template>

          <div class="settings-section-title">
            <i class="fas fa-users-cog"></i> 账号列表
            <button class="btn btn-outline btn-sm" style="margin-left:auto;" @click="router.push('/admin/password')">
              <i class="fas fa-key"></i> 修改我的密码
            </button>
            <button class="btn btn-primary btn-sm" style="margin-left:8px;" @click="goAddAdmin">
              新建账号
            </button>
          </div>

          <div class="table-container">
            <table class="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>姓名</th>
                  <th>手机号</th>
                  <th>角色</th>
                  <th>权限组</th>
                  <th>最近登录</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="u in adminUsers" :key="u.id">
                  <td class="mono-cell">{{ formatId(u.id) }}</td>
                  <td>
                    <div class="user-cell">
                      <span class="avatar-circle" :style="{ background: avatarBg(u) }">
                        {{ (u.name || 'A').charAt(0) }}
                      </span>
                      <span>{{ u.name || '-' }}</span>
                    </div>
                  </td>
                  <td>{{ u.phone || '-' }}</td>
                  <td>
                    <span :class="['role-tag', roleClass(u.role)]">{{ roleLabel(u.role) }}</span>
                  </td>
                  <td>{{ getPermGroup(u) }}</td>
                  <td>{{ formatTime(u.lastLoginTime || u.lastLogin) }}</td>
                  <td>
                    <span :class="['status-badge', u.status === '禁用' ? 'default' : 'success']">
                      {{ u.status === '禁用' ? '禁用' : '启用' }}
                    </span>
                  </td>
                  <td>
                    <a class="card-action" @click="goEditAdmin(u)">编辑</a>
                    <a class="card-action" style="margin-left:8px; color:#F59E0B;" @click="handleResetPassword(u)">重置密码</a>
                    <a v-if="canToggleStatus(u)" class="card-action" :style="u.status === '禁用' ? 'margin-left:8px; color:#10B981;' : 'margin-left:8px; color:#EF4444;'" @click="handleToggleStatus(u)">{{ u.status === '禁用' ? '启用' : '禁用' }}</a>
                  </td>
                </tr>
                <tr v-if="adminUsers.length === 0">
                  <td colspan="8" class="empty-cell">暂无管理员数据</td>
                </tr>
              </tbody>
            </table>
          </div>
        </el-tab-pane>

        <!-- ============ Tab 3: 规则配置 ============ -->
        <el-tab-pane name="rules">
          <template #label>
            <span class="tab-label">
              <i class="fas fa-sliders-h"></i> 规则配置
            </span>
          </template>

          <!-- 招工规则 -->
          <div class="settings-section-title">
            <i class="fas fa-briefcase"></i> 招工规则
          </div>
          <div class="settings-form-grid">
            <div class="toggle-row full-width">
              <div class="toggle-label">
                <span class="toggle-label-title">招工审核</span>
                <span class="toggle-label-desc">雇主发布招工信息需经平台审核后方可展示</span>
              </div>
              <el-switch v-model="rulesForm.jobAudit" />
            </div>
            <div class="toggle-row full-width">
              <div class="toggle-label">
                <span class="toggle-label-title">实名认证要求</span>
                <span class="toggle-label-desc">零工必须完成实名认证方可接单</span>
              </div>
              <el-switch v-model="rulesForm.realNameRequired" />
            </div>
            <div class="form-group">
              <label class="form-label">最低日薪标准（元）</label>
              <el-input-number v-model="rulesForm.minDailyWage" :min="0" :step="10" controls-position="right" style="width:100%;" />
            </div>
            <div class="form-group">
              <label class="form-label">最长招工有效期（天）</label>
              <el-input-number v-model="rulesForm.maxJobValidity" :min="1" :step="1" controls-position="right" style="width:100%;" />
            </div>
          </div>

          <!-- 结算规则 -->
          <div class="settings-section-title" style="margin-top:28px;">
            <i class="fas fa-coins"></i> 结算规则
          </div>
          <div class="settings-form-grid">
            <div class="toggle-row full-width">
              <div class="toggle-label">
                <span class="toggle-label-title">平台服务费</span>
                <span class="toggle-label-desc">按零工工资的一定比例，向老板加收平台服务费；零工工资全额到账</span>
              </div>
              <el-switch v-model="rulesForm.platformFeeEnabled" />
            </div>
            <div class="form-group">
              <label class="form-label">服务费比例（%）</label>
              <el-input-number v-model="rulesForm.feeRate" :min="0" :max="100" :step="0.5" controls-position="right" style="width:100%;" />
            </div>
            <div class="form-group full-width">
              <label class="form-label">结算规则公式</label>
              <div class="rule-formula">
                <span class="comment">// 老板支付金额计算公式（零工工资全额到账）</span><br>
                <span class="highlight">老板支付</span> = <span class="highlight">零工工资</span> × (1 + <span class="highlight">服务费率</span>)<br>
                <span class="comment">// 示例：零工工资280元，服务费率{{ formulaExample.rateText }}%</span><br>
                <span class="highlight">老板支付</span> = 280 + {{ formulaExample.fee }} = {{ formulaExample.total }} 元，零工到账 280 元
              </div>
            </div>
          </div>

          <div class="settings-actions">
            <button class="btn btn-primary" @click="saveRules">
              <i class="fas fa-save"></i> 保存规则配置
            </button>
            <button class="btn btn-outline" @click="resetRules">恢复默认</button>
          </div>
        </el-tab-pane>

      </el-tabs>
    </div>

    <!-- 银行账户编辑弹窗 -->
    <el-dialog
      v-model="showBankEditDialog"
      title="编辑银行账户"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form :model="bankEditForm" label-width="100px">
        <el-form-item label="收款银行">
          <el-input v-model="bankEditForm.bankName" placeholder="请输入收款银行" />
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="bankEditForm.cardNumber" placeholder="请输入银行卡号" />
        </el-form-item>
        <el-form-item label="账户名">
          <el-input v-model="bankEditForm.holder" placeholder="请输入账户名" />
        </el-form-item>
        <el-form-item label="开户支行">
          <el-input v-model="bankEditForm.branch" placeholder="请输入开户支行" />
        </el-form-item>
        <el-form-item label="SWIFT代码">
          <el-input v-model="bankEditForm.swiftCode" placeholder="请输入SWIFT代码" />
        </el-form-item>
        <el-form-item label="联行号">
          <el-input v-model="bankEditForm.bankCode" placeholder="请输入联行号" />
        </el-form-item>
        <el-form-item label="账户类型">
          <el-select v-model="bankEditForm.accountType" style="width:100%">
            <el-option label="对公账户" value="对公账户" />
            <el-option label="个人账户" value="个人账户" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <button class="btn btn-outline" @click="showBankEditDialog = false">取消</button>
          <button class="btn btn-primary" @click="saveBankEdit">保存</button>
        </div>
      </template>
    </el-dialog>

    <!-- 钱包账户编辑弹窗 -->
    <el-dialog
      v-model="showWalletEditDialog"
      :title="walletMeta[walletEditType].title"
      width="480px"
      :close-on-click-modal="false"
    >
      <!-- 账户预览 -->
      <div class="wallet-edit-preview">
        <div :class="['wallet-edit-icon', walletMeta[walletEditType].iconClass]">
          <i :class="walletMeta[walletEditType].icon"></i>
        </div>
        <div style="min-width:0; flex:1;">
          <div style="font-size:14px; font-weight:600; color:var(--text-primary);">
            {{ walletMeta[walletEditType].name }}
          </div>
          <div style="font-size:13px; color:var(--text-secondary); margin-top:2px;">
            {{ maskWalletAccount(walletEditType, walletEditForm.account) }}
          </div>
        </div>
        <span class="tag tag-green" style="flex-shrink:0;">已认证</span>
      </div>

      <el-form :model="walletEditForm" label-width="120px" style="margin-top:16px;">
        <el-form-item :label="walletMeta[walletEditType].accountLabel">
          <el-input
            v-model="walletEditForm.account"
            :placeholder="walletMeta[walletEditType].placeholder"
          />
          <div style="font-size:12px; color:var(--text-secondary); margin-top:4px;">
            {{ walletMeta[walletEditType].hint }}
          </div>
        </el-form-item>
        <el-form-item :label="walletMeta[walletEditType].holderLabel">
          <el-input v-model="walletEditForm.holder" placeholder="请输入实名认证姓名或企业主体名称" />
          <div style="font-size:12px; color:var(--text-secondary); margin-top:4px;">
            须与账户实名认证信息完全一致，否则将导致收款失败。
          </div>
        </el-form-item>
        <el-form-item label="设为默认收款账户">
          <el-switch v-model="walletEditForm.isDefault" />
          <span style="font-size:13px; color:var(--text-secondary); margin-left:8px;">
            默认账户将优先用于服务费扣款
          </span>
        </el-form-item>
      </el-form>

      <!-- 安全提示 -->
      <div class="wallet-edit-tips">
        <div class="wallet-edit-tip"><i class="fas fa-check-circle"></i> 修改收款账户后将进入 1 个工作日的审核期。</div>
        <div class="wallet-edit-tip"><i class="fas fa-check-circle"></i> 审核期间结算款项仍打入原账户，审核通过后自动切换至新账户。</div>
        <div class="wallet-edit-tip"><i class="fas fa-check-circle"></i> 每次修改账户信息均会记录操作日志，可在「系统设置 - 操作日志」中查看。</div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <button class="btn btn-outline" @click="showWalletEditDialog = false">取消</button>
          <button class="btn btn-primary" @click="saveWalletEdit">保存</button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSettingsByCategory, saveSetting, getBankAccount, saveBankAccount, getWalletAccount, saveWalletAccount, uploadFile, resetAdminPassword, adminPasswordProblem, updateAdminUser } from '@/api/system'
import request from '@/api/request'

const router = useRouter()
const route = useRoute()

const activeTab = ref('basic')
const accountTab = ref('bank')

// 银行账户信息（响应式，可编辑）
const bankInfo = reactive({
  bankName: '中国工商银行',
  cardNumber: '6222 **** **** 8888',
  holder: '快马日结科技有限公司',
  branch: '北京海淀支行',
  swiftCode: 'ICBKCNBJ',
  bankCode: '102100000458',
  accountType: '对公账户'
})
// 银行账户编辑弹窗
const showBankEditDialog = ref(false)
const bankEditForm = reactive({ ...bankInfo })
const openBankEdit = () => {
  Object.assign(bankEditForm, bankInfo)
  showBankEditDialog.value = true
}
const loadBankInfo = async () => {
  try {
    const res = await getBankAccount()
    const data = res.data || res
    if (data) {
      const parsed = typeof data === 'string' ? JSON.parse(data) : data
      Object.assign(bankInfo, parsed)
    }
  } catch (e) {
    console.warn('[Settings] 加载银行账户信息失败，使用默认值:', e)
  }
}
const saveBankEdit = async () => {
  try {
    await saveBankAccount({ ...bankEditForm })
    Object.assign(bankInfo, bankEditForm)
    showBankEditDialog.value = false
    ElMessage.success('银行账户信息已更新')
  } catch (e) {
    ElMessage.error('保存失败，请重试')
    console.error('[Settings] 保存银行账户信息失败:', e)
  }
}

// 钱包账户信息（响应式，可编辑）
const walletInfo = reactive({
  alipay: { account: 'kuaima@163.com', holder: '快马日结科技有限公司', isDefault: true },
  wechat: { account: 'k_m_riji001', holder: '快马日结科技有限公司', isDefault: false }
})
const showWalletEditDialog = ref(false)
const walletEditType = ref('alipay')
const walletEditForm = reactive({ account: '', holder: '', isDefault: false })

// 钱包类型元数据
const walletMeta = {
  alipay: {
    title: '编辑支付宝账户',
    badge: '支付宝', badgeClass: 'tag-blue',
    icon: 'fab fa-alipay', iconClass: 'alipay',
    name: '支付宝收款账户',
    accountLabel: '收款支付宝账号',
    holderLabel: '认证姓名 / 企业主体',
    placeholder: '请输入支付宝账号（邮箱或手机号）',
    hint: '用于接收平台服务费的支付宝账号，请确保账号准确。'
  },
  wechat: {
    title: '编辑微信账户',
    badge: '微信', badgeClass: 'tag-green',
    icon: 'fab fa-weixin', iconClass: 'wechat',
    name: '微信收款账户',
    accountLabel: '收款微信号',
    holderLabel: '认证姓名 / 企业主体',
    placeholder: '请输入收款微信号或微信商户号',
    hint: '用于接收平台服务费的微信账户，请确保账号准确。'
  }
}

/** 脱敏钱包账号（移植原型 maskWalletAccount） */
const maskWalletAccount = (type, val) => {
  val = (val || '').trim()
  if (!val) return '—'
  if (type === 'alipay') {
    if (val.indexOf('@') > -1) {
      const parts = val.split('@')
      const u = parts[0], d = parts[1] || ''
      if (u.length <= 3) return u + '****@' + d
      return u.slice(0, 1) + '****' + u.slice(-2) + '@' + d
    }
    if (/^\d{7,}$/.test(val)) return val.replace(/^(\d{3})\d+(\d{4})$/, '$1****$2')
    return val.length > 4 ? val.slice(0, 2) + '****' + val.slice(-2) : val
  }
  if (val.length <= 6) return val
  return val.slice(0, 4) + '****' + val.slice(-3)
}

const openWalletEdit = (type) => {
  walletEditType.value = type
  const d = walletInfo[type]
  walletEditForm.account = d.account
  walletEditForm.holder = d.holder
  walletEditForm.isDefault = d.isDefault
  showWalletEditDialog.value = true
}
const setWalletDefault = async (type) => {
  const other = type === 'alipay' ? 'wechat' : 'alipay'
  walletInfo[type].isDefault = true
  walletInfo[other].isDefault = false
  try {
    await saveWalletAccount({ alipay: walletInfo.alipay, wechat: walletInfo.wechat })
    ElMessage.success(`已将${walletMeta[type].name}设为默认`)
  } catch (e) {
    ElMessage.error('设置失败，请重试')
  }
}
const loadWalletInfo = async () => {
  try {
    const res = await getWalletAccount()
    const data = res.data || res
    if (data) {
      const parsed = typeof data === 'string' ? JSON.parse(data) : data
      if (parsed.alipay) Object.assign(walletInfo.alipay, parsed.alipay)
      if (parsed.wechat) Object.assign(walletInfo.wechat, parsed.wechat)
    }
  } catch (e) {
    console.warn('[Settings] 加载钱包账户信息失败，使用默认值:', e)
  }
}
const saveWalletEdit = async () => {
  try {
    const type = walletEditType.value
    const other = type === 'alipay' ? 'wechat' : 'alipay'
    // 若设为默认，取消另一个的默认
    if (walletEditForm.isDefault) {
      walletInfo[other].isDefault = false
    }
    walletInfo[type].account = walletEditForm.account
    walletInfo[type].holder = walletEditForm.holder
    walletInfo[type].isDefault = walletEditForm.isDefault
    // 确保至少有一个默认账户
    if (!walletInfo.alipay.isDefault && !walletInfo.wechat.isDefault) {
      walletInfo[other].isDefault = true
    }
    await saveWalletAccount({ alipay: walletInfo.alipay, wechat: walletInfo.wechat })
    showWalletEditDialog.value = false
    ElMessage.success(`${walletMeta[type].name}已更新`)
  } catch (e) {
    ElMessage.error('保存失败，请重试')
    console.error('[Settings] 保存钱包账户信息失败:', e)
  }
}

const platformForm = reactive({
  name: '快马日结',
  logo: '',
  phone: '400-888-6666',
  workStart: '08:00',
  workEnd: '22:00',
  version: 'v2.0.3',
  intro: '快马日结是一款专注于零工经济的日结平台，为雇主和零工提供高效、便捷、安全的撮合服务。'
})

const logoUploading = ref(false)
const logoPreviewUrl = ref("")

const beforeLogoUpload = (file) => {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) {
    ElMessage.error('仅支持图片格式上传')
    return false
  }
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

const handleLogoUpload = async (options) => {
  logoUploading.value = true
  try {
    const res = await uploadFile(options.file)
    const data = res?.data || res || {}
    if (data.objectKey && data.url) {
      platformForm.logo = data.objectKey
      logoPreviewUrl.value = data.url
      ElMessage.success('Logo 上传成功，记得点击保存设置')
    } else {
      ElMessage.error('上传失败：未获取到图片地址')
    }
  } catch (e) {
    ElMessage.error('Logo 上传失败，请重试')
  } finally {
    logoUploading.value = false
  }
}

const rulesForm = reactive({
  jobAudit: true,
  realNameRequired: true,
  minDailyWage: 150,
  maxJobValidity: 30,
  platformFeeEnabled: true,
  feeRate: 5
})

// 与后端 SettlementService 一致：服务费 = 工资 × 费率，四舍五入到分，由老板额外支付
const formulaExample = computed(() => {
  const rate = rulesForm.platformFeeEnabled ? Math.max(0, Number(rulesForm.feeRate) || 0) : 0
  const fee = Math.round(280 * rate) / 100
  return { rateText: rate, fee: fee.toFixed(2), total: (280 + fee).toFixed(2) }
})

const adminUsers = ref([])

// ====== Helpers ======
const roleClass = (role) => {
  if (role === 'SUPER_ADMIN') return 'role-super'
  if (role === 'ADMIN') return 'role-admin'
  if (role === 'EDITOR') return 'role-editor'
  return 'role-viewer'
}

const roleLabel = (role) => {
  const map = {
    SUPER_ADMIN: '超级管理员',
    ADMIN: '管理员',
    EDITOR: '审核员',
    VIEWER: '查看员'
  }
  return map[role] || role || '查看员'
}

const formatId = (id) => 'A' + String(id || 0).padStart(3, '0')

const formatTime = (t) => {
  if (!t) return '-'
  const s = String(t).replace('T', ' ')
  return s.length > 16 ? s.substring(0, 16) : s
}

/** 权限模块短名映射（与 AdminUserForm 权限树模块 key 对应） */
const PERM_MODULE_LABELS = {
  permUser: '用户',
  permJob: '招工',
  permOrder: '订单',
  permContent: '内容',
  permService: '消息',
  permSystem: '系统'
}

/**
 * 根据账号的 permissions 权限树生成「权限组」摘要（与原型一致）
 * - 超级管理员：全部权限
 * - 所有模块都至少有一个勾选：全部权限
 * - 否则：用 "/" 连接已勾选的模块短名，如「用户/订单/招工」
 * - 无权限：显示 "无"
 */
const getPermGroup = (u) => {
  if (u.role === 'SUPER_ADMIN') return '全部权限'
  let perms = u.permissions
  if (!perms) return '无'
  if (typeof perms === 'string') {
    try { perms = JSON.parse(perms) } catch { return '无' }
  }
  if (!perms || typeof perms !== 'object') return '无'
  const active = []
  for (const key of Object.keys(PERM_MODULE_LABELS)) {
    const mod = perms[key]
    if (mod && typeof mod === 'object' && Object.values(mod).some(v => v === true)) {
      active.push(PERM_MODULE_LABELS[key])
    }
  }
  if (active.length === Object.keys(PERM_MODULE_LABELS).length) return '全部权限'
  return active.length ? active.join('/') : '无'
}

const avatarBg = (u) => {
  const map = {
    SUPER_ADMIN: 'linear-gradient(135deg,#FF6B35,#FF8C42)',
    ADMIN: 'linear-gradient(135deg,#2563EB,#3B82F6)',
    EDITOR: 'linear-gradient(135deg,#10B981,#059669)',
    VIEWER: 'linear-gradient(135deg,#8B5CF6,#6D28D9)'
  }
  return map[u.role] || 'linear-gradient(135deg,#F59E0B,#D97706)'
}

// ====== Load ======
const applyPlatformSettings = (list) => {
  const map = {}
  ;(list || []).forEach(item => {
    map[item.settingKey] = item.settingValue
  })
  if (map['platform.name']) platformForm.name = map['platform.name']
  const logoItem = (list || []).find(item => item.settingKey === 'platform.logo')
  if (logoItem?.settingValue) {
    platformForm.logo = ossObjectKey(logoItem.settingValue)
    logoPreviewUrl.value = logoItem.displayValue || logoItem.settingValue
  }
  if (map['platform.phone']) platformForm.phone = map['platform.phone']
  if (map['platform.workStart']) platformForm.workStart = map['platform.workStart']
  if (map['platform.workEnd']) platformForm.workEnd = map['platform.workEnd']
  if (map['platform.version']) platformForm.version = map['platform.version']
  if (map['platform.intro']) platformForm.intro = map['platform.intro']
}

function ossObjectKey(value) {
  const text = String(value || '')
  if (!text.startsWith('http')) return text
  try {
    const url = new URL(text)
    return decodeURIComponent(url.pathname.replace(/^\//, ''))
  } catch (_) {
    return text
  }
}

const applyRulesSettings = (list) => {
  const map = {}
  ;(list || []).forEach(item => {
    map[item.settingKey] = item.settingValue
  })
  if ('rules.jobAudit' in map) rulesForm.jobAudit = map['rules.jobAudit'] === 'true' || map['rules.jobAudit'] === true
  if ('rules.realNameRequired' in map) rulesForm.realNameRequired = map['rules.realNameRequired'] === 'true' || map['rules.realNameRequired'] === true
  if ('rules.minDailyWage' in map) rulesForm.minDailyWage = Number(map['rules.minDailyWage']) || rulesForm.minDailyWage
  if ('rules.maxJobValidity' in map) rulesForm.maxJobValidity = Number(map['rules.maxJobValidity']) || rulesForm.maxJobValidity
  if ('rules.platformFeeEnabled' in map) rulesForm.platformFeeEnabled = map['rules.platformFeeEnabled'] === 'true' || map['rules.platformFeeEnabled'] === true
  if ('rules.feeRate' in map) rulesForm.feeRate = Number(map['rules.feeRate']) || rulesForm.feeRate
}

const loadPlatformSettings = async () => {
  try {
    const res = await getSettingsByCategory('platform')
    const d = res?.data
    const list = Array.isArray(d) ? d : (d?.content || d?.list || [])
    if (Array.isArray(list)) applyPlatformSettings(list)
  } catch (e) {
    console.warn('[Settings] 加载平台设置失败:', e)
  }
}

const loadRulesSettings = async () => {
  // security/points 为历史分类；saveRules 写入的是 rules.*，放在最后读取，以最新保存的值为准
  for (const [category, label] of [['security', '规则'], ['points', '积分'], ['rules', '规则']]) {
    try {
      const res = await getSettingsByCategory(category)
      const d = res?.data
      const list = Array.isArray(d) ? d : (d?.content || d?.list || [])
      if (Array.isArray(list)) applyRulesSettings(list)
    } catch (e) {
      console.warn(`[Settings] 加载${label}设置失败:`, e)
    }
  }
}

const loadAdminUsers = async () => {
  try {
    const res = await request.get('/admin/admin-users', { params: { page: 0, size: 20 } })
    const d = res?.data
    const list = Array.isArray(d) ? d : (d?.content || d?.list || [])
    adminUsers.value = list || []
  } catch (e) {
    console.warn('[Settings] 加载管理员列表失败:', e)
    adminUsers.value = []
  }
}

// ====== Save ======
const savePlatform = async () => {
  try {
    await saveSetting('platform.name', platformForm.name, '平台名称')
    await saveSetting('platform.logo', platformForm.logo || '', '平台Logo')
    await saveSetting('platform.phone', platformForm.phone, '客服电话')
    await saveSetting('platform.workStart', platformForm.workStart, '营业开始时间')
    await saveSetting('platform.workEnd', platformForm.workEnd, '营业结束时间')
    await saveSetting('platform.intro', platformForm.intro, '平台简介')
    ElMessage.success('保存成功')
  } catch (e) {
    console.warn('[Settings] 保存平台设置失败:', e)
    ElMessage.error('保存失败')
  }
}

const resetPlatform = () => {
  platformForm.name = '快马日结'
  platformForm.phone = '400-888-6666'
  platformForm.workStart = '08:00'
  platformForm.workEnd = '22:00'
  platformForm.intro = '快马日结是一款专注于零工经济的日结平台，为雇主和零工提供高效、便捷、安全的撮合服务。'
  ElMessage.info('已重置为默认设置')
}

const saveRules = async () => {
  try {
    await saveSetting('rules.jobAudit', String(rulesForm.jobAudit), '招工审核')
    await saveSetting('rules.realNameRequired', String(rulesForm.realNameRequired), '实名认证要求')
    await saveSetting('rules.minDailyWage', String(rulesForm.minDailyWage), '最低日薪标准')
    await saveSetting('rules.maxJobValidity', String(rulesForm.maxJobValidity), '最长招工有效期')
    await saveSetting('rules.platformFeeEnabled', String(rulesForm.platformFeeEnabled), '平台服务费')
    await saveSetting('rules.feeRate', String(rulesForm.feeRate), '服务费比例')
    ElMessage.success('规则配置保存成功')
  } catch (e) {
    console.warn('[Settings] 保存规则失败:', e)
    ElMessage.error('保存失败')
  }
}

const resetRules = () => {
  rulesForm.jobAudit = true
  rulesForm.realNameRequired = true
  rulesForm.minDailyWage = 150
  rulesForm.maxJobValidity = 30
  rulesForm.platformFeeEnabled = true
  rulesForm.feeRate = 5
  ElMessage.info('已恢复为默认设置')
}

// ====== Actions ======
const goAddAdmin = () => {
  router.push('/admin/admin-user/form')
}

const goEditAdmin = (row) => {
  router.push(`/admin/admin-user/form?mode=edit&id=${row.id}`)
}

/** 重置密码：由操作人输入新密码（超级管理员可重置所有账号，普通管理员仅限自己创建的账号） */
const handleResetPassword = async (row) => {
  let newPassword
  try {
    const { value } = await ElMessageBox.prompt(
      `请为账号「${row.name || row.username}」设置新密码，重置后请告知对方尽快自行修改。`,
      '重置密码',
      {
        confirmButtonText: '重置',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPlaceholder: '8-50 位，同时包含字母和数字',
        inputValidator: value => adminPasswordProblem(value) || true
      }
    )
    newPassword = value
  } catch {
    return
  }
  try {
    await resetAdminPassword(row.id, newPassword)
    ElMessage.success('密码已重置')
  } catch (e) {
    console.warn('[Settings] 重置密码失败:', e)
  }
}

/** 当前登录账号是否为超级管理员 */
const currentIsSuperAdmin = () => {
  const role = localStorage.getItem('admin_role') || ''
  return role === 'SUPER_ADMIN' || role === '超级管理员'
}

/** 是否可切换状态：超级管理员可操作所有账号；普通管理员仅限自己创建的账号；超级管理员账号不可禁用 */
const canToggleStatus = (u) => {
  if (u.role === 'SUPER_ADMIN') return false
  if (currentIsSuperAdmin()) return true
  const currentId = Number(localStorage.getItem('admin_id') || 0)
  return u.createdBy != null && Number(u.createdBy) === currentId
}

/** 启用/禁用账号（仅限自己创建的账号） */
const handleToggleStatus = async (u) => {
  const target = u.status === '禁用' ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定要${target}账号「${u.name || u.username}」吗？${target === '禁用' ? '禁用后该账号将无法登录后台。' : ''}`,
      target === '禁用' ? '禁用账号' : '启用账号',
      { type: 'warning', confirmButtonText: target, cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await updateAdminUser(u.id, { status: target })
    ElMessage.success(`账号已${target}`)
    loadAdminUsers()
  } catch (e) {
    console.warn('[Settings] 切换账号状态失败:', e)
  }
}

onMounted(() => {
  if (route.query.tab) {
    activeTab.value = String(route.query.tab)
  }
  loadPlatformSettings()
  loadRulesSettings()
  loadBankInfo()
  loadWalletInfo()
  loadAdminUsers()
})

// 从管理员编辑页返回时刷新对应列表
watch(() => route.query.tab, (newTab) => {
  if (newTab === 'permission') {
    activeTab.value = 'permission'
    loadAdminUsers()
  }
})
</script>

<style scoped>
.settings-page {
  width: 100%;
}

/* ============ Tab navigation ============ */
.settings-tabs :deep(.el-tabs__header) {
  margin-bottom: 24px;
  border-bottom: 2px solid var(--border);
}

.settings-tabs :deep(.el-tabs__nav-wrap) {
  border: none;
}

.settings-tabs :deep(.el-tabs__item) {
  padding: 14px 24px;
  height: auto;
  line-height: 1.2;
  font-size: 15px;
  color: var(--text-secondary);
  border: none;
  transition: color 0.2s;
}

.settings-tabs :deep(.el-tabs__item:hover) {
  color: var(--primary);
}

.settings-tabs :deep(.el-tabs__item.is-active) {
  color: var(--primary);
  font-weight: 500;
}

.settings-tabs :deep(.el-tabs__active-bar) {
  background-color: var(--primary);
  height: 2px;
}

.settings-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* ============ Section title ============ */
.settings-section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
}

.settings-section-title i {
  color: var(--primary);
}

/* ============ Form grid ============ */
.settings-form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px 24px;
}

.settings-form-grid .full-width {
  grid-column: 1 / -1;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.time-range {
  display: flex;
  gap: 10px;
  align-items: center;
}

.time-sep {
  flex-shrink: 0;
  color: var(--text-secondary);
  font-size: 13px;
}

/* readonly version input */
.readonly-input :deep(.el-input__wrapper) {
  background: var(--bg-page);
  box-shadow: 0 0 0 1px var(--border) inset;
}

/* ============ Logo upload ============ */
.logo-upload {
  width: 120px;
  height: 120px;
  display: inline-block;
}
.logo-upload :deep(.el-upload) {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2px dashed var(--border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--text-muted);
  gap: 8px;
  position: relative;
  overflow: hidden;
}
.logo-upload :deep(.el-upload:hover) {
  border-color: var(--primary);
  color: var(--primary);
}
.logo-upload i {
  font-size: 28px;
}
.logo-upload span {
  font-size: 13px;
}
.logo-preview {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 10px;
}
.logo-upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.logo-upload-mask {
  position: absolute;
  inset: 0;
  background: rgba(255,255,255,0.85);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--primary);
  font-size: 12px;
}
.logo-upload-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

/* ============ Account description ============ */
.account-desc {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-muted);
}

/* ============ Account sub-tabs ============ */
.account-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--border);
}

.account-tab {
  padding: 12px 24px;
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 8px;
}

.account-tab:hover {
  color: var(--primary);
}

.account-tab.active {
  color: var(--primary);
  border-bottom-color: var(--primary);
  font-weight: 500;
}

/* ============ Bank card ============ */
.bank-card {
  background: linear-gradient(135deg, #1E3A8A 0%, #4338CA 100%);
  border-radius: 16px;
  padding: 28px;
  color: white;
  margin-bottom: 16px;
  position: relative;
  overflow: hidden;
}

.bank-card::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -20%;
  width: 200px;
  height: 200px;
  background: rgba(255,255,255,0.1);
  border-radius: 50%;
}

.bank-card::after {
  content: '';
  position: absolute;
  bottom: -30%;
  left: -10%;
  width: 150px;
  height: 150px;
  background: rgba(255,255,255,0.08);
  border-radius: 50%;
}

.bank-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  position: relative;
  z-index: 1;
}

.bank-card-title {
  font-size: 12px;
  opacity: 0.8;
}

.bank-card-bank {
  font-size: 18px;
  font-weight: 600;
  margin-top: 4px;
}

.bank-card-logo {
  width: 44px;
  height: 44px;
  background: rgba(255,255,255,0.15);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.bank-card-number {
  font-size: 22px;
  font-family: 'Courier New', monospace;
  letter-spacing: 2px;
  margin: 20px 0;
  position: relative;
  z-index: 1;
}

.bank-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  z-index: 1;
}

.bank-card-holder {
  font-size: 13px;
  opacity: 0.8;
}

.bank-card-holder span {
  display: block;
  font-size: 16px;
  font-weight: 500;
  margin-top: 2px;
  opacity: 1;
}

.bank-card-status {
  font-size: 12px;
  padding: 4px 10px;
  background: rgba(255,255,255,0.2);
  border-radius: 12px;
}

/* ============ Account items ============ */
.account-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.account-item {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 12px;
  margin-bottom: 12px;
  transition: border-color 0.2s;
}

.account-item:hover {
  border-color: var(--primary);
}

.account-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}

.account-item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.account-item-body {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px 24px;
  padding: 16px 20px;
}

.account-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 0;
}

.account-field-label {
  font-size: 13px;
  color: var(--text-muted);
}

.account-field-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.account-notice {
  padding: 16px 20px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.8;
}

/* ============ Wallet cards ============ */
.wallet-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 12px;
  margin-bottom: 12px;
  transition: all 0.2s;
}

.wallet-card:hover {
  border-color: var(--primary);
  box-shadow: 0 4px 12px rgba(255,107,53,0.1);
}

.wallet-card-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.wallet-card-icon.alipay {
  background: #EFF6FF;
  color: #3B82F6;
}

.wallet-card-icon.wechat {
  background: #F0FDF4;
  color: #10B981;
}

.wallet-card-info {
  flex: 1;
}

.wallet-card-name {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.wallet-card-account {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.wallet-card-actions {
  display: flex;
  gap: 8px;
}

.account-edit-btn {
  padding: 6px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.account-edit-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.account-edit-primary {
  color: var(--primary);
  border-color: var(--primary);
}

/* ============ Toggle row ============ */
.toggle-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid var(--border);
}

.toggle-row:last-child {
  border-bottom: none;
}

.toggle-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.toggle-label-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.toggle-label-desc {
  font-size: 12px;
  color: var(--text-muted);
}

/* ============ Rule formula ============ */
.rule-formula {
  background: #F9FAFB;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 20px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 2;
  color: var(--text-primary);
}

.rule-formula .comment {
  color: var(--text-muted);
}

.rule-formula .highlight {
  color: var(--primary);
  font-weight: 600;
}

/* ============ Settings actions ============ */
.settings-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}

/* ============ Dialog footer ============ */
.dialog-footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

/* ============ Template cards（对齐原型紧凑风格） ============ */
.template-card {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 12px;
  transition: border-color 0.2s;
}

.template-card:hover {
  border-color: var(--primary);
}

.template-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.template-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.tag {
  display: inline-block;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 12px;
  font-weight: 500;
}

.tag-blue {
  background: #EFF6FF;
  color: #2563EB;
}

.tag-green {
  background: #F0FDF4;
  color: #10B981;
}

.template-content {
  font-size: 13px;
  color: var(--text-secondary);
  background: var(--bg-page);
  padding: 10px 12px;
  border-radius: 6px;
  line-height: 1.6;
}

.template-actions {
  display: flex;
  gap: 6px;
  margin-top: 10px;
}

/* ============ Data table ============ */
.table-container {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th {
  padding: 12px 16px;
  text-align: left;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-muted);
  border-bottom: 2px solid var(--border);
  background: #F9FAFB;
}

.data-table td {
  padding: 12px 16px;
  font-size: 14px;
  border-bottom: 1px solid var(--border);
  color: var(--text-primary);
}

.data-table tbody tr:last-child td {
  border-bottom: none;
}

.data-table tbody tr:hover {
  background: #F9FAFB;
}

.mono-cell {
  font-family: 'Courier New', monospace;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar-circle {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}

.role-tag {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
}

.role-super {
  background: #FFF3E6;
  color: #FF6B35;
}

.role-admin {
  background: #EFF6FF;
  color: #3B82F6;
}

.role-editor {
  background: #FFF8E6;
  color: #F59E0B;
}

.role-viewer {
  background: #F3F4F6;
  color: #6B7280;
}

.status-badge {
  display: inline-block;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.status-badge.success {
  background: #F0FDF4;
  color: #10B981;
}

.status-badge.default {
  background: #F3F4F6;
  color: #6B7280;
}

/* ============ Button (与原型 admin.css 一致) ============ */
.empty-cell {
  text-align: center;
  color: var(--text-muted);
  padding: 24px !important;
  font-size: 13px;
}

.card-action {
  font-size: 13px;
  color: var(--primary);
  cursor: pointer;
}

.card-action:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .settings-form-grid,
  .account-item-body {
    grid-template-columns: 1fr;
  }
}

/* ============ Wallet default tag ============ */
.wallet-default-tag {
  display: inline-block;
  background: var(--primary, #FF6B35);
  color: #fff;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  margin-left: 6px;
  font-weight: 500;
  vertical-align: middle;
}

/* ============ Wallet edit dialog ============ */
.wallet-edit-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--bg-secondary, #f5f5f5);
  border-radius: 8px;
}
.wallet-edit-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.wallet-edit-icon.alipay {
  background: #1677ff;
  color: #fff;
}
.wallet-edit-icon.wechat {
  background: #07c160;
  color: #fff;
}
.wallet-edit-tips {
  margin-top: 8px;
  padding: 12px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 8px;
}
.wallet-edit-tip {
  font-size: 12px;
  color: #8c6d1f;
  line-height: 1.8;
  display: flex;
  align-items: flex-start;
  gap: 6px;
}
.wallet-edit-tip i {
  color: #52c41a;
  margin-top: 3px;
  flex-shrink: 0;
}

/* ============ 抽屉通用样式（对齐原型 .preview-drawer） ============ */
.preview-drawer-wrapper :deep(.el-drawer) {
  background: #F3F4F6;
}
.preview-drawer-wrapper :deep(.el-drawer__body) {
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
/* 发送测试抽屉叠加在预览抽屉之上 */
.test-drawer-wrapper :deep(.el-drawer) {
  z-index: 2100;
}
.test-drawer-wrapper :deep(.el-overlay) {
  z-index: 2099;
}

/* 抽屉头部 */
.pd-header {
  flex-shrink: 0;
  background: #fff;
  padding: 18px 20px;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.pd-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.pd-title i { color: var(--primary); }
.pd-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}
.pd-close {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: #F3F4F6;
  color: var(--text-secondary);
  font-size: 15px;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}
.pd-close:hover { background: #E5E7EB; color: var(--text-primary); }

/* 抽屉主体 */
.pd-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
.pd-section {
  background: #fff;
  border-radius: 10px;
  padding: 18px;
  margin-bottom: 16px;
}
.pd-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.pd-section-title i { color: var(--primary); }
.pd-section-title .pd-hint {
  font-weight: 400;
  font-size: 12px;
  color: var(--text-muted);
  margin-left: 2px;
}
.pd-section-phone {
  background: linear-gradient(180deg, #FAFBFC 0%, #F3F4F6 100%);
}

/* ============ iPhone 模型（对齐原型 .pd-phone） ============ */
.pd-phone {
  --phone-w: 300px;
  width: var(--phone-w);
  margin: 4px auto 0;
  position: relative;
  background: linear-gradient(145deg, #2b313c 0%, #12151b 100%);
  border-radius: 44px;
  padding: 11px;
  box-shadow:
    0 18px 44px rgba(17, 24, 39, 0.28),
    0 4px 12px rgba(17, 24, 39, 0.18),
    inset 0 0 0 2px rgba(255, 255, 255, 0.07);
}
/* 侧边按键：电源键（右）+ 音量键（左） */
.pd-phone::before {
  content: '';
  position: absolute;
  right: -2.5px;
  top: 116px;
  width: 3px;
  height: 64px;
  background: #1a1e26;
  border-radius: 0 3px 3px 0;
}
.pd-phone::after {
  content: '';
  position: absolute;
  left: -2.5px;
  top: 96px;
  width: 3px;
  height: 48px;
  background: #1a1e26;
  border-radius: 3px 0 0 3px;
  box-shadow: 0 56px 0 #1a1e26;
}
.pd-phone-screen {
  position: relative;
  background: #fff;
  border-radius: 34px;
  overflow: hidden;
}
/* 灵动岛 */
.pd-phone-island {
  position: absolute;
  top: 9px;
  left: 50%;
  transform: translateX(-50%);
  width: 84px;
  height: 22px;
  background: #0a0a0c;
  border-radius: 12px;
  z-index: 20;
}
.pd-phone-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 13px 24px 2px;
  font-size: 12px;
  font-weight: 600;
  color: #111827;
  position: relative;
  z-index: 10;
}
.pd-phone-bar i { margin-left: 4px; font-size: 11px; }
.pd-phone-head {
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  padding: 6px 0 10px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}
.pd-phone-body {
  background: #F5F6F8;
  padding: 16px 12px 20px;
  min-height: 300px;
}
/* Home 指示条 */
.pd-phone-home {
  height: 18px;
  background: #F5F6F8;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 6px;
}
.pd-phone-home::after {
  content: '';
  width: 104px;
  height: 4px;
  border-radius: 2px;
  background: #111827;
  opacity: 0.85;
}

/* 手机内短信气泡 */
.pd-sms { display: flex; gap: 9px; }
.pd-sms-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  background: linear-gradient(135deg, #FF6B35, #FF8C5A);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  box-shadow: 0 4px 10px rgba(255, 107, 53, 0.3);
}
.pd-sms-main { flex: 1; min-width: 0; }
.pd-sms-top { display: flex; align-items: baseline; gap: 8px; margin-bottom: 5px; }
.pd-sms-name { font-size: 12px; font-weight: 600; color: #1f2937; }
.pd-sms-time { font-size: 10px; color: #9ca3af; }
.pd-sms-bubble {
  background: #fff;
  border-radius: 4px 12px 12px 12px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.8;
  color: #374151;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  word-break: break-all;
}

/* 手机内站内信通知卡片 */
.pd-notice {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.pd-notice-head { display: flex; align-items: center; gap: 9px; margin-bottom: 8px; }
.pd-notice-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  flex-shrink: 0;
  background: linear-gradient(135deg, #FF6B35, #FF8C5A);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}
.pd-notice-app { font-size: 12px; font-weight: 600; color: #1f2937; }
.pd-notice-now { font-size: 10px; color: #9ca3af; margin-top: 2px; }
.pd-notice-title { font-size: 13px; font-weight: 600; color: #1f2937; margin-bottom: 5px; }
.pd-notice-text { font-size: 12px; line-height: 1.8; color: #374151; word-break: break-all; }
.pd-notice-foot {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f3f4f6;
  font-size: 11px;
  color: var(--primary);
  text-align: center;
}
.pd-hl { color: var(--primary); font-weight: 600; }

/* 原始模板 */
.pd-raw {
  background: #F9FAFB;
  border: 1px dashed var(--border);
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 12px;
  line-height: 1.9;
  color: var(--text-secondary);
  word-break: break-all;
}
.pd-raw code {
  color: var(--primary);
  background: rgba(255, 107, 53, 0.08);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 11px;
}

/* 变量示例 */
.pd-var-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;
  border-bottom: 1px solid #f7f7f7;
}
.pd-var-row:last-child { border-bottom: none; }
.pd-var-code {
  color: var(--primary);
  background: rgba(255, 107, 53, 0.08);
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}
.pd-var-val { color: var(--text-secondary); margin-left: auto; text-align: right; word-break: break-all; }

/* 抽屉底部 */
.pd-footer {
  flex-shrink: 0;
  background: #fff;
  border-top: 1px solid var(--border);
  padding: 14px 20px;
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
.pd-footer .btn { flex-shrink: 0; }

/* ============ 发送测试抽屉 ============ */
.td-field { margin-bottom: 16px; }
.td-field:last-child { margin-bottom: 0; }
.td-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 8px;
}
.td-label .required { color: #ef4444; }
.td-el-input :deep(.el-input__wrapper) {
  height: 40px;
  border-radius: 8px;
}
.td-hint { font-size: 12px; color: var(--text-muted); margin-top: 7px; line-height: 1.6; }
.td-content-preview {
  background: #FFF7F2;
  border: 1px solid #FFE2D1;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 12px;
  line-height: 1.9;
  color: var(--text-primary);
  word-break: break-all;
}
.td-content-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 9px;
}
.td-tip {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin-bottom: 10px;
}
.td-tip:last-child { margin-bottom: 0; }
.td-tip i { color: var(--primary); margin-top: 4px; font-size: 11px; flex-shrink: 0; }

/* 发送成功态 */
.td-success {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 40px 24px;
  text-align: center;
}
.td-success-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #ecfdf5;
  color: #10b981;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  margin: 0 auto 18px;
}
.td-success-title { font-size: 17px; font-weight: 600; color: var(--text-primary); margin-bottom: 10px; }
.td-success-desc { font-size: 13px; color: var(--text-muted); line-height: 1.9; margin-bottom: 26px; }
.td-success-desc b { color: var(--text-primary); }
</style>
