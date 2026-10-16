<template>
  <view class="container">
    <!-- 状态栏 -->
    <view class="status-bar">
      <text>19:53</text>
      <view class="status-icons">
        <text>📶</text>
        <text>📡</text>
        <text>🔋</text>
      </view>
    </view>

    <view class="page-bg">
      <!-- 导航栏 -->
      <view class="nav-bar">
        <view class="nav-back" @click="closePage">
          <text>←</text>
        </view>
        <view class="nav-right">
          <view class="nav-btn">
            <text style="color:#333;font-size:13px;">⋯</text>
          </view>
          <view style="width:1px;height:14px;background:#ddd;margin:0 2px;"></view>
          <view class="nav-btn">
            <text style="color:#333;font-size:11px;">●</text>
          </view>
        </view>
      </view>

      <scroll-view scroll-y class="scroll-area">
        <!-- 成功头部 -->
        <view class="success-header">
          <view class="success-icon">
            <text style="font-size:28px;color:#fff;">✓</text>
          </view>
          <text class="success-title">招工发布成功！</text>
          <text class="success-desc">您的岗位正在推送给零工...</text>
        </view>

        <!-- 诊断卡片 -->
        <view class="diagnosis-card">
          <view class="diagnosis-header">
            <text style="font-size:14px;">💡</text>
            <text style="margin-left:6px;">快马招工诊断：1个项目可优化</text>
          </view>
          <view class="diagnosis-item">
            <view class="diag-info">
              <text class="diag-title">岗位描述需完善</text>
              <text class="diag-tag" @click="navigateTo('task-content')">完善岗位描述 更多熟练工接单 ›</text>
            </view>
            <view class="diag-action" @click="navigateTo('task-content')">
              去填写
              <text style="font-size:10px;margin-left:4px;">›</text>
            </view>
          </view>
        </view>

        <!-- 岗位预览 -->
        <view class="preview-card">
          <view class="preview-header">
            <text class="preview-title">岗位预览</text>
            <text class="preview-status">
              <text style="margin-right:4px;">🕐</text>
              招聘中
            </text>
          </view>
          <view class="preview-info">
            <text class="preview-tag highlight">普工</text>
            <text class="preview-tag">电子厂</text>
            <text class="preview-tag">100元/小时</text>
            <text class="preview-tag">8小时</text>
            <text class="preview-tag">本周五</text>
          </view>
        </view>

        <!-- 快捷操作 -->
        <view class="quick-actions">
          <text class="quick-title">常用操作</text>
          <view class="action-row" @click="navigateTo('recruit-manager')">
            <view class="action-icon orange">
              <text style="font-size:16px;">👥</text>
            </view>
            <view class="action-info">
              <text class="action-title">管理报名零工</text>
              <text class="action-desc">查看已报名人员，安排面试</text>
            </view>
            <text class="› action-arrow"></text>
          </view>
          <view class="action-row" @click="navigateTo('publish-info')">
            <view class="action-icon blue">
              <text style="font-size:16px;">✏</text>
            </view>
            <view class="action-info">
              <text class="action-title">编辑招工信息</text>
              <text class="action-desc">修改岗位描述、工价等信息</text>
            </view>
            <text class="› action-arrow"></text>
          </view>
          <view class="action-row" @click="navigateTo('boss-order')">
            <view class="action-icon green">
              <text style="font-size:16px;">📋</text>
            </view>
            <view class="action-info">
              <text class="action-title">查看订单</text>
              <text class="action-desc">管理已开工、结算中的订单</text>
            </view>
            <text class="› action-arrow"></text>
          </view>
          <view class="action-row" @click="navigateTo('service-chat')">
            <view class="action-icon purple">
              <text style="font-size:16px;">🎧</text>
            </view>
            <view class="action-info">
              <text class="action-title">联系客服</text>
              <text class="action-desc">遇到问题？我们帮您解决</text>
            </view>
            <text class="› action-arrow"></text>
          </view>
        </view>

        <view style="height: 20px;"></view>
      </scroll-view>

      <!-- 底部按钮 -->
      <view class="bottom-bar">
        <button class="primary-btn" @click="viewJob">查看招工</button>
        <button class="secondary-btn" @click="backToHome">返回首页</button>
      </view>
    </view>

    <!-- 提示弹窗 -->
    <view class="modal-mask" :class="{ show: showTip }">
      <view class="modal-content">
        <text class="modal-title">温馨提示</text>
        <view class="modal-warnings">
          <view class="warning-item">
            <text style="color:#FA8C16;font-size:14px;margin-top:3px;flex-shrink:0;">⚠</text>
            <text>请注意接听零工电话！</text>
          </view>
          <view class="warning-item">
            <text style="color:#FA8C16;font-size:14px;margin-top:3px;flex-shrink:0;">⚠</text>
            <text>禁止添加零工联系方式私下交易，绕过平台等行为，否则将被封号且不退费！</text>
          </view>
          <view class="warning-item">
            <text style="color:#FA8C16;font-size:14px;margin-top:3px;flex-shrink:0;">⚠</text>
            <text>零工完工后，请立即通过平台支付报酬（禁止线下支付）！</text>
          </view>
        </view>
        <button class="modal-btn" @click="closeTip">知道了</button>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      showTip: false
    }
  },
  onLoad() {
    setTimeout(() => {
      this.showTip = true
    }, 800)
  },
  methods: {
    closePage() {
      uni.navigateBack()
    },
    navigateTo(page) {
      uni.navigateTo({ url: `/pages/boss/${page}` })
    },
    viewJob() {
      this.showTip = false
      uni.redirectTo({
        url: '/pages/boss/order',
        fail: () => uni.reLaunch({ url: '/pages/boss/order' }),
      })
    },
    closeTip() {
      this.showTip = false
      setTimeout(() => {
        uni.navigateTo({ url: '/pages/boss/order' })
      }, 300)
    },
    backToHome() {
      uni.reLaunch({ url: '/pages/boss/home' })
    }
  }
}
</script>

<style lang="scss" scoped>
.container {
  width: 100%;
  height: 100vh;
  background: #FFF8E6;
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
  background: #fff;
}

.status-icons {
  display: flex;
  align-items: center;
  gap: 4px;
}

.page-bg {
  background: #FFF8E6;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.nav-bar {
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  flex-shrink: 0;
}

.nav-back {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #333;
  font-size: 20px;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 2px;
}

.nav-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  background: #FFF8E6;
}

.success-header {
  text-align: center;
  padding: 30px 20px 20px;
  background: #fff;
}

.success-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #52C41A, #73D13D);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 14px;
  box-shadow: 0 6px 20px rgba(82, 196, 26, 0.3);
}

.success-title {
  font-size: 22px;
  font-weight: 700;
  color: #333;
  display: block;
}

.success-desc {
  font-size: 14px;
  color: #999;
  margin-top: 6px;
  display: block;
}

.diagnosis-card {
  background: #fff;
  margin: 16px 12px 0;
  border-radius: 12px;
  overflow: hidden;
}

.diagnosis-header {
  padding: 14px 16px;
  background: #FFFBE6;
  display: flex;
  align-items: center;
  font-size: 14px;
  font-weight: 500;
  color: #FA8C16;
}

.diagnosis-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-top: 1px solid #f5f5f5;
}

.diag-info {
  flex: 1;
}

.diag-title {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  display: block;
}

.diag-tag {
  display: inline-block;
  margin-top: 6px;
  font-size: 12px;
  color: #FF6B35;
  background: #FFF3ED;
  padding: 3px 8px;
  border-radius: 4px;
}

.diag-action {
  font-size: 14px;
  color: #FF6B35;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.preview-card {
  background: #fff;
  margin: 12px;
  border-radius: 12px;
  padding: 14px 16px;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.preview-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.preview-status {
  font-size: 12px;
  color: #52C41A;
}

.preview-info {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.preview-tag {
  font-size: 12px;
  color: #666;
  background: #F5F5F5;
  padding: 4px 10px;
  border-radius: 4px;
}

.preview-tag.highlight {
  background: #FFF3ED;
  color: #FF6B35;
}

.quick-actions {
  background: #fff;
  margin: 12px;
  border-radius: 12px;
  overflow: hidden;
}

.quick-title {
  padding: 12px 16px;
  font-size: 13px;
  color: #999;
  display: block;
}

.action-row {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  border-top: 1px solid #f5f5f5;
}

.action-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.action-icon.orange { background: #FFF3ED; color: #FF6B35; }
.action-icon.blue { background: #EBF5FF; color: #1677FF; }
.action-icon.green { background: #E8F8EF; color: #52C41A; }
.action-icon.purple { background: #F3EEFB; color: #722ED1; }

.action-info {
  flex: 1;
  margin-left: 12px;
}

.action-title {
  font-size: 14px;
  color: #333;
  font-weight: 500;
  display: block;
}

.action-desc {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
  display: block;
}

.action-arrow {
  color: #CCC;
  font-size: 12px;
}

.bottom-bar {
  background: #fff;
  padding: 12px 16px 30px;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.primary-btn {
  width: 100%;
  height: 48px;
  background: linear-gradient(135deg, #FFD700, #FFA500);
  color: #fff;
  border: none;
  border-radius: 24px;
  font-size: 16px;
  font-weight: 600;
}

.secondary-btn {
  width: 100%;
  height: 44px;
  background: #fff;
  color: #666;
  border: 1px solid #E0E0E0;
  border-radius: 22px;
  font-size: 14px;
  margin-top: 10px;
}

.modal-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.5);
  z-index: 100;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.3s;
}

.modal-mask.show {
  opacity: 1;
  pointer-events: auto;
}

.modal-content {
  width: 100%;
  background: #fff;
  border-radius: 20px 20px 0 0;
  padding: 24px 20px 30px;
  transform: translateY(100%);
  transition: transform 0.3s ease-out;
}

.modal-mask.show .modal-content {
  transform: translateY(0);
}

.modal-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  text-align: center;
  margin-bottom: 16px;
  display: block;
}

.modal-warnings {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.warning-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: #666;
  line-height: 1.6;
}

.modal-btn {
  width: 100%;
  height: 46px;
  background: linear-gradient(135deg, #FFD700, #FFA500);
  color: #fff;
  border: none;
  border-radius: 23px;
  font-size: 15px;
  font-weight: 600;
  margin-top: 20px;
}
</style>
