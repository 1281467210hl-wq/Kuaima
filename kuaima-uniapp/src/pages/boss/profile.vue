<template>
  <view class="container">
    <view class="status-spacer" :style="{ height: `${statusBarHeight}px` }" />
    <scroll-view scroll-y class="scroll-area">
      <!-- 顶部导航 -->
      <view class="top-header">
        <view class="nav-bar">
          <view class="brand-tag">快马日结</view>
        </view>

        <!-- 用户信息 -->
        <view class="user-info" @click="navigateTo('personal-info')">
          <view class="user-avatar">
            <image :src="profile.avatar || userIcon" mode="aspectFill" />
          </view>
          <view class="user-info-main">
            <text class="user-name" @click.stop="openIdentitySwitch">{{ profile.name }} | 老板</text>
            <image class="chevron-icon" :src="chevronRightIcon" mode="aspectFit" />
          </view>
          <view class="switch-btn" @click.stop="openIdentitySwitch">
            <image :src="exchangeIcon" mode="aspectFit" />
            <text>我要找工作</text>
          </view>
        </view>
      </view>

      <view class="employer-card card-shadow" @click="navigateTo('creditor-score')">
        <view class="employer-top">
          <view class="employer-title-row">
            <view class="employer-badge">
              <image :src="shieldIcon" mode="aspectFit" />
              <text>五星雇主</text>
            </view>
            <view class="employer-score">
              <text>诚意分</text>
              <text class="score-val">{{ stats.integrityScore }}</text>
              <image :src="chevronRightIcon" mode="aspectFit" />
            </view>
          </view>
          <view class="employer-stats">
            <view class="stat-col"><text class="stat-val">{{ stats.goodRate }}%</text><text>好评率</text></view>
            <view class="stat-col"><text class="stat-val">{{ stats.arrivalRate }}%</text><text>到达完成率</text></view>
            <view class="stat-col"><text class="stat-val">{{ stats.settleRate }}%</text><text>24小时结算率</text></view>
          </view>
          <view class="employer-footer">
            <view class="footer-item">
              <text>累计支付报酬 </text>
              <text class="footer-value">{{ formatMoney(stats.totalPayment) }}</text>
              <text>元</text>
            </view>
            <text class="sep">|</text>
            <view class="footer-item">
              <text>完单数 </text>
              <text class="footer-value">{{ formatCount(stats.completedOrders) }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="account-card card-shadow">
        <view class="auth-row">
          <view class="auth-info">
            <text class="auth-name">暂未授权</text>
            <text class="auth-tag">未授权员工</text>
          </view>
          <view class="auth-unbind" @click="navigateTo('authorize')">
            <text>去授权</text>
            <image :src="chevronRightOrangeIcon" mode="aspectFit" />
          </view>
        </view>
        <view class="balance-row">
          <view class="balance-info">
            <text class="balance-desc">余额可支付零工报酬与购买积分</text>
            <view class="balance-val-row">
              <text class="balance-symbol">¥</text>
              <text class="balance-num">{{ stats.balance }}</text>
            </view>
          </view>
          <view class="balance-actions">
            <button class="btn-outline" @click="navigateTo('balance')">账户明细</button>
            <button class="btn-primary-sm" @click="navigateTo('recharge')">立即充值</button>
          </view>
        </view>
      </view>
      <view class="asset-card card-shadow"><view v-for="asset in assets" :key="asset.label" class="asset-item" @click="navigateTo(asset.page)"><text class="asset-val">{{ asset.value }}</text><text>{{ asset.label }}</text><image :src="chevronRightIcon" mode="aspectFit" /></view></view>

      <!-- 企业认证Banner -->
      <view class="cert-banner card-shadow" @click="navigateTo('enterprise-cert')">
        <view class="cert-icon">
          <image :src="buildingIcon" mode="aspectFit" />
        </view>
        <view class="cert-content">
          <text class="cert-title">{{ profile.enterpriseApproved ? '企业认证已通过' : '完成企业认证 解锁权益' }}</text>
          <text class="cert-desc">{{ profile.enterpriseApproved ? '认证信息已生效' : '曝光加权·优先推荐熟练零工接单' }}</text>
        </view>
        <text class="cert-btn">{{ profile.enterpriseApproved ? '查看认证' : '立即认证' }}</text>
      </view>

      <!-- 我的服务 -->
      <text class="section-title">我的服务</text>
      <view class="service-grid card-shadow">
        <view class="service-item" @click="navigateTo('talent-list')"><view class="service-icon"><image :src="usersIcon" mode="aspectFit" /></view><text class="service-label">人才库</text></view>
        <view class="service-item" @click="navigateTo('suspend-settle')">
          <view class="service-icon">
            <image :src="banIcon" mode="aspectFit" />
          </view>
          <text class="service-label">暂不结算</text>
        </view>
        <view class="service-item" @click="navigateTo('payment-detail')">
          <view class="service-icon">
            <image :src="fileInvoiceDollarIcon" mode="aspectFit" />
          </view>
          <text class="service-label">报酬支付明细</text>
        </view>
        <view class="service-item" @click="navigateTo('expense-detail')"><view class="service-icon"><image class="expense-icon" :src="calendarIcon" mode="aspectFit" /></view><text class="service-label">费用报销明细</text></view>
      </view>

      <!-- 其他功能 -->
      <text class="section-title">其他功能</text>
      <view class="other-grid card-shadow">
        <view class="other-item" @click="navigateTo('service-chat')">
          <view class="other-icon">
            <image :src="headsetIcon" mode="aspectFit" />
          </view>
          <text class="other-label">联系客服</text>
        </view>
        <view class="other-item" @click="navigateTo('realname')">
          <view class="other-icon">
            <image :src="userShieldIcon" mode="aspectFit" />
          </view>
          <text class="other-label">实名认证</text>
        </view>
        <view class="other-item" @click="navigateTo('enterprise-cert')">
          <view class="other-icon">
            <image :src="buildingColumnsIcon" mode="aspectFit" />
          </view>
          <text class="other-label">企业认证</text>
        </view>
        <view class="other-item" @click="navigateTo('invite-inbox')">
          <view class="other-icon badge-wrap">
            <image :src="usersIcon" mode="aspectFit" />
            <view v-if="inviteCount > 0" class="other-badge">{{ inviteCount > 99 ? '99+' : inviteCount }}</view>
          </view>
          <text class="other-label">企业邀请</text>
        </view>
        <view class="other-item" @click="navigateTo('switch-account')">
          <view class="other-icon">
            <image :src="exchangeDarkIcon" mode="aspectFit" />
          </view>
          <text class="other-label">更换账号</text>
        </view>
        <view class="other-item" @click="navigateTo('user-agreement')">
          <view class="other-icon">
            <image :src="fileLinesIcon" mode="aspectFit" />
          </view>
          <text class="other-label">用户服务协议</text>
        </view>
        <view class="other-item" @click="navigateTo('privacy')">
          <view class="other-icon">
            <image :src="lockIcon" mode="aspectFit" />
          </view>
          <text class="other-label">隐私协议</text>
        </view>
        <view class="other-item" @click="navigateTo('copyright')">
          <view class="other-icon">
            <image :src="bookOpenIcon" mode="aspectFit" />
          </view>
          <text class="other-label">知识产权规则</text>
        </view>
        <view class="other-item" @click="navigateTo('rule')">
          <view class="other-icon">
            <image :src="clipboardListIcon" mode="aspectFit" />
          </view>
          <text class="other-label">平台规则</text>
        </view>
      </view>

      <!-- 底部信息 -->
      <view class="footer-info">
        <text>客服电话 400-188-6610  工作时间 9:00-18:00</text>
        <text>推荐算法举报与未成年人举报渠道同上</text>
        <text>营业执照 人力资源服务许可证</text>
        <text>ICP证书号: 合字 B2-20250310</text>
        <text>上海市人社局备案 （001）123456</text>
      </view>
    </scroll-view>

    <!-- 底部TabBar -->
    <view class="tab-bar">
      <view v-if="canAccess('HOME_VIEW')" class="tab-item" @click="switchTab('home')">
        <view class="tab-icon-wrap">
          <image :src="houseGrayIcon" mode="aspectFit" />
        </view>
        <text class="tab-label">首页</text>
      </view>
      <view v-if="canAccess('ORDER_VIEW')" class="tab-item" @click="switchTab('order')">
        <view class="tab-icon-wrap">
          <image :src="calendarCheckGrayIcon" mode="aspectFit" />
        </view>
        <text class="tab-label">招工订单</text>
      </view>
      <view v-if="canAccess('WORKBENCH_VIEW')" class="tab-item" @click="switchTab('workbench')">
        <view class="tab-icon-wrap">
          <image src="/static/icons/boss-tabbar/briefcase-gray.svg" mode="aspectFit" />
        </view>
        <text class="tab-label">工作台</text>
      </view>
      <view v-if="canAccess('MESSAGE_VIEW')" class="tab-item" @click="switchTab('message')">
        <view class="tab-icon-wrap">
          <image :src="commentDotsGrayIcon" mode="aspectFit" />
        </view>
        <text class="tab-label">消息</text>
      </view>
      <view class="tab-item active" @click="switchTab('profile')">
        <view class="tab-icon-wrap">
          <image :src="faceSmileWhiteIcon" mode="aspectFit" />
        </view>
        <text class="tab-label">我的</text>
      </view>
    </view>
  </view>
</template>

<script>
import {
  getCurrentUser,
  getUser,
  getBossProfile,
  getBossProfileAssets,
  getBossProfileStats,
  getBossBalance,
  getBossRewardOverview,
} from '@/api/backend'
import userIcon from '/static/avatars/default-boss-avatar.png'
import chevronRightIcon from '/static/icons/boss-profile/chevron-right.svg'
import exchangeIcon from '/static/icons/boss-profile/exchange.svg'
import buildingIcon from '/static/icons/boss-profile/building.svg'
import headsetIcon from '/static/icons/boss-profile/headset.svg'
import buildingColumnsIcon from '/static/icons/boss-profile/building-columns.svg'
import fileLinesIcon from '/static/icons/boss-profile/file-lines.svg'
import lockIcon from '/static/icons/boss-profile/lock.svg'
import bookOpenIcon from '/static/icons/boss-profile/book-open.svg'
import clipboardListIcon from '/static/icons/boss-profile/clipboard-list.svg'
import houseGrayIcon from '/static/icons/boss-tabbar/house-gray.svg'
import calendarCheckGrayIcon from '/static/icons/boss-tabbar/calendar-check-gray.svg'
import commentDotsGrayIcon from '/static/icons/boss-tabbar/comment-dots-gray.svg'
import faceSmileWhiteIcon from '/static/icons/boss-tabbar/face-smile-white.svg'
import shieldIcon from '/static/icons/boss-profile/shield-check.svg'
import calendarIcon from '/static/icons/boss-profile/calendar-check-solid-gray.svg'
import chevronRightOrangeIcon from '/static/icons/worker-credit/chevron-right-orange.svg'
import usersDarkIcon from '/static/icons/boss-profile/users-dark.svg'
import banIcon from '/static/icons/boss-profile/ban-dark.svg'
import fileInvoiceDollarIcon from '/static/icons/boss-profile/file-invoice-dollar-dark.svg'
import userShieldIcon from '/static/icons/boss-profile/user-shield-orange.svg'
import exchangeDarkIcon from '/static/icons/boss-profile/exchange-dark.svg'
import { hasEnterprisePermission } from '@/api/enterprise-context'
import { getMyInvites } from '@/api/enterprise'

export default {
  data() {
    return {
      statusBarHeight: uni.getSystemInfoSync().statusBarHeight || 0,
      userIcon,
      chevronRightIcon,
      exchangeIcon,
      buildingIcon,
      headsetIcon,
      buildingColumnsIcon,
      fileLinesIcon,
      lockIcon,
      bookOpenIcon,
      clipboardListIcon,
      houseGrayIcon,
      calendarCheckGrayIcon,
      commentDotsGrayIcon,
      faceSmileWhiteIcon,
      shieldIcon,
      usersIcon: usersDarkIcon,
      calendarIcon,
      banIcon,
      fileInvoiceDollarIcon,
      userShieldIcon,
      exchangeDarkIcon,
      assets: [
        { value: '320', label: '积分', page: 'points' },
        { value: '0', label: '券包(个)', page: 'voucher' },
        { value: '0.00', label: '奖励金(元)', page: 'reward' },
      ],
      stats: {
        integrityScore: 709,
        goodRate: 91,
        arrivalRate: 82,
        settleRate: 97,
        totalPayment: '10万+',
        completedOrders: 757,
        authorizedName: '王娜',
        balance: '0',
      },
      profile: {
        name: '用户',
        avatar: '',
        enterpriseApproved: false,
      },
      inviteCount: 0,
    }
  },
  onShow() {
    this.loadProfile()
  },
  methods: {
    canAccess(code) { return hasEnterprisePermission(code) },
    formatMoney(value) {
      const cents = Number(value)
      return Number.isFinite(cents) ? (cents / 100).toFixed(2) : '--'
    },
    formatCount(value) {
      return value === null || value === undefined || value === '' ? '--' : value
    },
    openIdentitySwitch() {
      uni.navigateTo({ url: '/pages/worker/switch-identity?currentRole=boss' })
    },
    async loadProfile() {
      try {
        const userId = uni.getStorageSync('userId')
        const results = await Promise.all([
          getCurrentUser().catch(() => null),
          userId ? getUser(userId).catch(() => null) : Promise.resolve(null),
          userId ? getBossProfile(userId).catch(() => null) : Promise.resolve(null),
          userId ? getBossProfileAssets(userId).catch(() => null) : Promise.resolve(null),
          userId ? getBossProfileStats(userId).catch(() => null) : Promise.resolve(null),
          getBossBalance().catch(() => null),
          getBossRewardOverview().catch(() => null),
          getMyInvites().catch(() => null),
        ])
        const [currentUser, user, bossProfile, assetSummary, profileStats, walletBalance, rewardOverview, myInvites] = results
        this.inviteCount = Array.isArray(myInvites) ? myInvites.length : 0
        const data = { ...(user || {}), ...(currentUser || {}), ...(bossProfile || {}) }
        const cached = uni.getStorageSync('userInfo') || {}
        const name = data.nickname || data.name || data.realName || cached.nickname || cached.name || '用户'
        const enterpriseStatus = String(data.enterpriseStatus || '').toUpperCase()
        this.profile = {
          name,
          avatar: data.avatar || data.avatarUrl || cached.avatar || cached.avatarUrl || '',
          enterpriseApproved: ['APPROVED', 'PASSED', '已通过', '已认证'].includes(enterpriseStatus),
        }
        if (assetSummary) {
          const balanceYuan = Number(assetSummary.balanceYuan)
          const cents = Number(assetSummary.balanceFen ?? assetSummary.balance)
          const rewardCents = Number(assetSummary.rewardAmount)
          this.stats.balance = Number.isFinite(balanceYuan)
            ? balanceYuan.toFixed(2)
            : (Number.isFinite(cents) ? (cents / 100).toFixed(2) : this.stats.balance)
          this.assets = [
            { value: assetSummary.points ?? 0, label: '积分', page: 'points' },
            { value: assetSummary.couponCount ?? 0, label: '券包(个)', page: 'voucher' },
            { value: Number.isFinite(rewardCents) ? (rewardCents / 100).toFixed(2) : '0.00', label: '奖励金(元)', page: 'reward' },
          ]
        }
        // 支付页返回时，资产汇总接口可能仍是旧快照；以钱包和奖励金专用接口的最新余额覆盖展示。
        if (walletBalance) {
          const walletYuan = Number(walletBalance.balanceYuan)
          const walletCents = Number(walletBalance.balanceFen ?? walletBalance.balance)
          if (Number.isFinite(walletYuan)) this.stats.balance = walletYuan.toFixed(2)
          else if (Number.isFinite(walletCents)) this.stats.balance = (walletCents / 100).toFixed(2)
        }
        if (rewardOverview) {
          const rewardAmount = Number(rewardOverview.balance ?? rewardOverview.amount)
          if (Number.isFinite(rewardAmount)) {
            this.assets = this.assets.map((asset) =>
              asset.page === 'reward'
                ? { ...asset, value: rewardAmount.toFixed(2) }
                : asset,
            )
          }
        }
        if (profileStats) {
          this.stats = {
            ...this.stats,
            integrityScore: profileStats.integrityScore ?? profileStats.creditScore ?? this.stats.integrityScore,
            goodRate: profileStats.goodRate ?? profileStats.positiveRate ?? this.stats.goodRate,
            arrivalRate: profileStats.arrivalRate ?? profileStats.completionRate ?? this.stats.arrivalRate,
            settleRate: profileStats.settleRate ?? profileStats.settlementRate ?? this.stats.settleRate,
            totalPayment: profileStats.totalPayment ?? profileStats.settledAmount ?? this.stats.totalPayment,
            completedOrders: profileStats.completedOrders ?? profileStats.completedCount ?? this.stats.completedOrders,
          }
        }
      } catch (_) {
        // 保留安全默认值，避免接口失败影响页面渲染。
      }
    },
    navigateTo(pageName) {
      const bossPages = [
        'boss-employer', 'boss-home', 'boss-message', 'boss-order', 'boss-profile', 
        'boss-publish', 'search-worker', 'select-job', 'publish-info', 'schedule-stats', 
        'enterprise-cert', 'enterprise-cert-form', 'creditor-score', 'talent-list', 
        'expense-detail', 'payment-detail', 'recruit-manager', 'recruit-address', 
        'sub-account', 'authorize', 'suspend-settle', 'switch-account', 'invite-code', 'blacklist',
        'all-jobs', 'boss-filter', 'settlement', 'contract', 'system-notice', 'missed-call', 
        'signup-notice', 'invite-friend', 'service-chat', 'insurance', 'realname', 'balance', 'recharge',
        'personal-info', 'points', 'voucher', 'reward', 'invite-inbox'
      ]
      const sharedPageMap = {
        'rule': '/pages/worker/rule',
        'user-agreement': '/pages/worker/user-agreement',
        'privacy': '/pages/worker/privacy',
        'copyright': '/pages/worker/copyright'
      }
      const url = bossPages.includes(pageName)
        ? `/pages/boss/${pageName}`
        : sharedPageMap[pageName]

      if (!url) {
        uni.showToast({ title: '页面暂未开放', icon: 'none' })
        return
      }

      uni.navigateTo({
        url,
        fail: () => {
          uni.showToast({ title: '页面打开失败，请重试', icon: 'none' })
        }
      })
    },
    switchTab(tab) {
      const tabPages = {
        'home': '/pages/boss/home',
        'order': '/pages/boss/order',
        'workbench': '/pages/boss/workbench',
        'message': '/pages/boss/message',
        'profile': '/pages/boss/profile'
      }
      const target = tabPages[tab]
      const currentRoute = getCurrentPages().slice(-1)[0]?.route
      if (!target || `/${currentRoute}` === target) return
      uni.redirectTo({
        url: target,
        fail: (error) => {
          console.error('Boss 主导航跳转失败', error)
          uni.reLaunch({
            url: target,
            fail: () => uni.showToast({ title: '页面跳转失败，请重试', icon: 'none' })
          })
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.container {
  width: 100%;
  height: 100vh;
  background: #F7F7F7;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.status-bar {
  height: 47px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 28px;
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.status-icons {
  display: flex;
  align-items: center;
  gap: 4px;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  background: #F7F7F7;
}

.status-spacer {
  flex-shrink: 0;
  background: #F7F7F7;
}

.top-header {
  background: #FFD96F;
  padding: 12px 16px 20px;
}

.nav-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.brand-tag {
  background: #fff;
  padding: 6px 14px;
  border-radius: 20px;
  font-weight: 700;
  font-size: 14px;
  color: #8B4513;
}

.nav-icons { display: none; }

.nav-icon-item {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-divider {
  width: 1px;
  height: 16px;
  background: #ddd;
  margin: 0 4px;
}

.user-info {
  display: flex;
  align-items: center;
  padding: 12px 0;
}

.user-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #FFB347, #FF6B35);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 14px;
  border: 3px solid rgba(255,255,255,0.5);
  overflow: hidden;
}

.user-info-main {
  display: flex;
  align-items: center;
  gap: 4px;
}

.user-name {
  font-size: 18px;
  font-weight: 700;
  color: #8B4513;
}

.switch-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: rgba(255,255,255,0.7);
  border-radius: 16px;
  font-size: 12px;
  color: #FF6B35;
  margin-left: auto;
}

.cert-banner {
  background: linear-gradient(135deg, #E3F2FD, #BBDEFB);
  margin: 0 16px 16px;
  border-radius: 16px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.employer-card { margin: -10px 16px 12px; border-radius: 16px; overflow: hidden; }
.employer-top { background: linear-gradient(135deg,#2D1B4E,#5E35B1 50%,#7B1FA2); padding:16px 18px 14px; color:#fff; }
.employer-top .employer-title-row { margin-bottom:14px; }
.employer-top .employer-badge { color:#fff; }
.employer-top .employer-score { color:#fff; }
.employer-top .employer-stats { background:rgba(255,255,255,.08); border-radius:10px; padding:12px 0; margin:0 0 12px; }
.employer-top .stat-col { border-right:1px solid rgba(255,255,255,.15); }
.employer-top .stat-col:last-child { border-right:0; }
.employer-top .stat-val { color:#fff; font-size:18px; }
.employer-top .employer-footer { color:#fff; }
.account-card,.asset-card { margin: 0 16px 12px; background: #fff; border-radius: 14px; padding: 0; overflow:hidden; }
.employer-title-row,.auth-row,.balance-row { display:flex; align-items:center; justify-content:space-between; }
.employer-badge { display:flex; align-items:center; gap:6px; color:#8B4513; font-weight:600; }
.employer-badge image,.employer-score image,.asset-item image { width:16px; height:16px; }
.employer-score { display:flex; align-items:center; gap:5px; color:#888; font-size:12px; }
.score-val { color:#FF6B35; font-size:20px; font-weight:700; }
.employer-stats { display:flex; }
.stat-col { flex:1; text-align:center; display:flex; flex-direction:column; gap:4px; color:#fff; font-size:11px; }
.stat-val { color:#333; font-size:20px; font-weight:700; }
.employer-footer { display:flex; align-items:center; gap:10px; font-size:12px; opacity:.85; }
.footer-item { display:flex; align-items:center; }
.footer-value { color:#FFD96F; font-weight:700; }
.employer-footer .sep { opacity:.4; }
.auth-row { justify-content:space-between; border-bottom:1px solid #F0E6D2; padding:12px 16px; background:#FFF8E7; }
.auth-info { display:flex; align-items:center; gap:8px; }
.auth-name { color:#333; font-size:14px; font-weight:600; }
.auth-tag { padding:2px 8px; color:#B91C1C; font-size:11px; font-weight:600; background:#FECACA; border-radius:4px; }
.auth-unbind { display:flex; align-items:center; gap:2px; color:#FF6B35; font-size:12px; font-weight:500; }
.auth-unbind image { width:10px; height:16px; }
.balance-row { justify-content:space-between; align-items:stretch; gap:12px; padding:14px 16px; }
.balance-info { flex:1; display:flex; flex-direction:column; justify-content:center; min-width:0; }
.balance-desc { display:block; color:#999; font-size:11px; }
.balance-val-row { display:flex; align-items:baseline; margin-top:4px; }
.balance-symbol { color:#333; font-size:18px; font-weight:700; margin-right:2px; }
.balance-num { color:#333; font-size:32px; font-weight:800; line-height:1; }
.balance-actions { display:flex; flex-direction:column; gap:8px; justify-content:center; flex-shrink:0; }
.btn-outline,.btn-primary-sm { display:flex; align-items:center; justify-content:center; height:32px; margin:0; padding:0 14px; border-radius:16px; font-size:12px; line-height:32px; }
.btn-outline { color:#666; background:#fff; border:1px solid #E0E0E0; }
.btn-primary-sm { color:#fff; background:linear-gradient(135deg,#FFD700,#FFA500); border:0; font-weight:600; }
.btn-outline::after,.btn-primary-sm::after { border:0; }
.asset-card { display:flex; padding:0; }
.asset-item { flex:1; position:relative; display:flex; flex-direction:column; align-items:center; gap:3px; color:#999; font-size:11px; padding:16px 0; border-right:1px solid #f0f0f0; }
.asset-item:last-child { border-right:0; }
.asset-val { color:#333; font-size:19px; font-weight:700; }
.asset-item image { position:absolute; right:8px; top:10px; width:11px; height:11px; }

.cert-icon {
  width: 40px;
  height: 40px;
  background: #1976D2;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.cert-content {
  flex: 1;
}

.cert-title {
  font-size: 14px;
  font-weight: 600;
  color: #1565C0;
}

.cert-desc {
  font-size: 12px;
  color: #1976D2;
  margin-top: 2px;
}

.cert-btn {
  padding: 6px 14px;
  background: #1976D2;
  color: white;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  padding: 0 16px 12px;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  background: white;
  margin: 0 16px;
  padding: 20px 12px 12px;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.service-item {
  text-align: center;
}

.service-icon {
  width: 44px;
  height: 44px;
  margin: 0 auto 6px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.service-label {
  font-size: 12px;
  color: #666;
}

.expense-icon {
  /* 该 SVG 原色较浅，压暗后与同组服务图标统一。 */
  filter: grayscale(1) brightness(0.45);
}

.other-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px 8px;
  background: white;
  margin: 12px 16px;
  padding: 20px 12px;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.other-item {
  text-align: center;
}

.other-icon {
  width: 44px;
  height: 44px;
  margin: 0 auto 6px;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 其他功能图标与“我的服务”统一为深灰色；部分 SVG 本身为橙色，使用滤镜跨端统一色值。 */
.other-icon image {
  filter: grayscale(1) brightness(0.45);
}

.other-label {
  font-size: 12px;
  color: #666;
}

.other-icon.badge-wrap {
  position: relative;
}

.other-badge {
  position: absolute;
  top: -6px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  box-sizing: border-box;
  border-radius: 8px;
  background: #ff4d4f;
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
}

.footer-info {
  text-align: center;
  padding: 20px 16px;
  font-size: 11px;
  color: #bbb;
  line-height: 1.8;
}

/* TabBar样式 */
.tab-bar {
  flex-shrink: 0;
  height: 83px;
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(20px);
  border-top: 0.5px solid rgba(0, 0, 0, 0.05);
  display: flex;
  padding-bottom: 20px;
  z-index: 50;
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.tab-icon-wrap {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  margin-bottom: 3px;
}

.tab-label {
  font-size: 10px;
  color: #999;
  font-weight: 500;
}

.tab-item.active .tab-label {
  color: #FF6B35;
}

.tab-item.active .tab-icon-wrap {
  background: linear-gradient(135deg, #FF6B35, #FF8C5A);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  color: white;
  margin-bottom: 2px;
  box-shadow: 0 4px 10px rgba(255, 107, 53, 0.3);
}

.status-icons image { width: 16px; height: 16px; }
.nav-icon-item image { width: 16px; height: 16px; }
.user-avatar image { width: 100%; height: 100%; border-radius: 50%; display: block; }
.chevron-icon { width: 12px; height: 12px; }
.switch-btn image { width: 14px; height: 14px; }
.cert-icon image { width: 21px; height: 21px; }
.service-icon image, .other-icon image { width: 22px; height: 22px; }
.tab-icon-wrap image { width: 22px; height: 22px; }
.tab-item.active .tab-icon-wrap image { opacity: 1; }

</style>
