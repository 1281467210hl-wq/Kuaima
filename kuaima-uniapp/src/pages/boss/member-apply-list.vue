<template>
  <view class="container">
    <!-- 顶部导航 -->
    <view class="wb-header" :style="{ paddingTop: statusBarHeight + 8 + 'px' }">
      <view class="wb-back" @click="goBack"><text class="back-ico">‹</text></view>
      <text class="wb-title">申请列表</text>
      <!-- 微信小程序原生胶囊已占右上角，自绘胶囊会与其重叠，仅在非小程序端保留 -->
      <!-- #ifdef H5 -->
      <view class="wb-capsule">
        <view class="cap-btn"><text class="cap-ico">⋯</text></view>
        <view class="cap-divider"></view>
        <view class="cap-btn"><text class="cap-ico">○</text></view>
      </view>
      <!-- #endif -->
    </view>

    <view class="wb-body">
      <!-- Tab切换 -->
      <view class="ap-tabs">
        <view class="ap-tab" :class="{ active: currentTab === 'pending' }" @click="switchTab('pending')">
          待处理 <text class="cnt" v-if="pendingCount">({{ pendingCount }})</text>
        </view>
        <view class="ap-tab" :class="{ active: currentTab === 'agreed' }" @click="switchTab('agreed')">已同意</view>
        <view class="ap-tab" :class="{ active: currentTab === 'refused' }" @click="switchTab('refused')">已拒绝</view>
      </view>

      <view v-if="loading" class="wb-empty"><text class="wb-empty-text">加载中...</text></view>
      <view v-else-if="!applies.length" class="wb-empty">
        <view class="wb-empty-icon"><text>⏱</text></view>
        <text class="wb-empty-text">{{ emptyText }}</text>
      </view>

      <view v-for="a in applies" :key="a.id" class="ap-card">
        <view class="ap-top">
          <view class="ap-avatar" :style="{ background: avatarColor(a.phone + '') }">{{ a.name.charAt(0) }}</view>
          <view class="ap-main">
            <view class="ap-name-line">
              <text class="ap-name">{{ a.name }}</text>
              <text v-if="a.status === 'AGREED'" class="wb-tag green">已同意</text>
              <text v-else-if="a.status === 'REFUSED'" class="wb-tag gray">已拒绝</text>
            </view>
            <text class="ap-role">申请成为 {{ roleText(a.applyRole) }}</text>
          </view>
        </view>
        <view class="ap-info">
          <view class="ap-info-row"><text class="info-label">📞 联系电话</text><text class="info-val">{{ a.phone }}</text></view>
          <view class="ap-info-row"><text class="info-label">💬 申请留言</text><text class="info-val">{{ a.note || '—' }}</text></view>
        </view>
        <view class="ap-footer">
          <text class="ap-time">{{ formatTime(a.timestamp) }}</text>
          <text class="ap-source">来源：{{ a.source || '成员邀请链接' }}</text>
        </view>
        <view v-if="a.status === 'PENDING'" class="ap-btns">
          <view class="ap-btn ap-btn-reject" @click="openRefuse(a)">拒绝</view>
          <view class="ap-btn ap-btn-agree" @click="agree(a)">✓ 同意</view>
        </view>
      </view>
    </view>

    <!-- 拒绝确认弹窗 -->
    <view class="wb-modal" v-if="refuseModalShow" @click.self="closeRefuse">
      <view class="wb-modal-box">
        <view class="wb-modal-body">
          <text class="modal-icon">❓</text>
          <text class="modal-title">拒绝该入企申请？</text>
          <text class="modal-desc">拒绝后将通知对方，对方可重新提交申请</text>
        </view>
        <view class="wb-modal-footer">
          <view class="wb-btn-outline" @click="closeRefuse">取消</view>
          <view class="wb-btn-danger" @click="confirmRefuse">确定拒绝</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { getApplies, agreeApply, refuseApply } from "@/api/enterprise";
import { avatarColor } from "@/api/project";

export default {
  data() {
    return {
      statusBarHeight: 44,
      currentTab: "pending",
      pendingCount: 0,
      applies: [],
      loading: false,
      refuseModalShow: false,
      refuseTarget: null,
    };
  },
  computed: {
    emptyText() {
      return { pending: "暂无待处理的申请", agreed: "暂无已同意的申请", refused: "暂无已拒绝的申请" }[this.currentTab];
    },
  },
  onLoad() {
    try {
      const info = uni.getSystemInfoSync();
      this.statusBarHeight = info.statusBarHeight || 44;
    } catch (e) {}
    this.loadApplies();
  },
  onShow() {
    // 从「邀请二维码 / 入企申请」返回时刷新，避免缓存页面展示旧列表
    this.loadApplies();
  },
  methods: {
    avatarColor,
    roleText(role) {
      return { OWNER: "超级管理员", ADMIN: "管理员", STAFF: "成员" }[role] || role || "成员";
    },
    formatTime(ts) {
      if (!ts) return "";
      const d = new Date(ts);
      const p = (n) => String(n).padStart(2, "0");
      return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
    },
    async loadApplies() {
      this.loading = true;
      try {
        const data = await getApplies(this.currentTab);
        this.applies = data?.content || data || [];
        if (this.currentTab === "pending") this.pendingCount = this.applies.length;
      } catch (e) {
        uni.showToast({ title: e.message || "加载失败", icon: "none" });
      } finally {
        this.loading = false;
      }
    },
    switchTab(tab) {
      this.currentTab = tab;
      this.loadApplies();
    },
    async agree(a) {
      try {
        await agreeApply(a.id);
        uni.showToast({ title: `已同意 ${a.name} 加入企业`, icon: "none" });
        this.loadApplies();
      } catch (e) {
        uni.showToast({ title: e.message || "操作失败", icon: "none" });
      }
    },
    openRefuse(a) {
      this.refuseTarget = a;
      this.refuseModalShow = true;
    },
    closeRefuse() {
      this.refuseModalShow = false;
      this.refuseTarget = null;
    },
    async confirmRefuse() {
      if (!this.refuseTarget) return;
      try {
        await refuseApply(this.refuseTarget.id);
        uni.showToast({ title: "已拒绝该申请", icon: "none" });
        this.closeRefuse();
        this.loadApplies();
      } catch (e) {
        uni.showToast({ title: e.message || "操作失败", icon: "none" });
      }
    },
    goBack() {
      uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/boss/members" }) });
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f3f4f6;
}

.wb-header {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  padding: 6px 16px 12px;
  flex-shrink: 0;
}

.wb-back {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-ico {
  font-size: 22px;
  color: #333;
}

.wb-title {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.wb-capsule {
  display: flex;
  align-items: center;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 17px;
  padding: 0 6px;
  height: 32px;
}

.cap-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cap-ico {
  font-size: 14px;
  color: #666;
}

.cap-divider {
  width: 1px;
  height: 16px;
  background: rgba(0, 0, 0, 0.15);
  margin: 0 2px;
}

.wb-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px 20px;
}

.ap-tabs {
  display: flex;
  background: #fff;
  border-radius: 16px;
  padding: 4px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  margin-bottom: 12px;
}

.ap-tab {
  flex: 1;
  text-align: center;
  padding: 9px 0;
  font-size: 14px;
  color: #666;
  border-radius: 12px;
}

.ap-tab.active {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 3px 8px rgba(255, 107, 53, 0.3);
}

.cnt {
  font-size: 11px;
  opacity: 0.85;
}

.ap-card {
  background: #fff;
  border-radius: 16px;
  padding: 14px 16px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  margin-bottom: 10px;
}

.ap-top {
  display: flex;
  align-items: center;
  gap: 11px;
}

.ap-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ap-main {
  flex: 1;
  min-width: 0;
}

.ap-name-line {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ap-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.ap-role {
  font-size: 12px;
  color: #999;
  margin-top: 3px;
  display: block;
}

.wb-tag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 5px;
  font-weight: 600;
}

.wb-tag.green {
  background: #e8f8ef;
  color: #10b981;
}

.wb-tag.gray {
  background: #f5f5f5;
  color: #999;
}

.ap-info {
  background: #fafafa;
  border-radius: 10px;
  padding: 9px 12px;
  margin-top: 10px;
}

.ap-info-row {
  display: flex;
  align-items: center;
  font-size: 12px;
  line-height: 22px;
}

.info-label {
  width: 80px;
  color: #999;
  flex-shrink: 0;
}

.info-val {
  color: #444;
  flex: 1;
}

.ap-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  font-size: 11px;
  color: #b0b0b0;
}

.ap-btns {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.ap-btn {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  border-radius: 9999px;
  font-size: 13px;
  font-weight: 600;
}

.ap-btn-reject {
  background: #fff;
  color: #999;
  border: 1px solid #e5e5e5;
}

.ap-btn-agree {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  box-shadow: 0 3px 8px rgba(255, 107, 53, 0.25);
}

.wb-empty {
  padding: 40px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.wb-empty-icon {
  font-size: 34px;
  color: #ddd;
  margin-bottom: 10px;
}

.wb-empty-text {
  font-size: 13px;
  color: #999;
}

.wb-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
  padding: 32px;
}

.wb-modal-box {
  background: #fff;
  border-radius: 18px;
  width: 100%;
  overflow: hidden;
}

.wb-modal-body {
  text-align: center;
  padding: 24px 20px 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.modal-icon {
  font-size: 34px;
}

.modal-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-top: 12px;
}

.modal-desc {
  font-size: 13px;
  color: #999;
  margin-top: 8px;
  line-height: 20px;
}

.wb-modal-footer {
  display: flex;
  border-top: 1px solid #f5f5f5;
}

.wb-modal-footer view {
  flex: 1;
  text-align: center;
  padding: 14px 0;
  font-size: 15px;
}

.wb-btn-outline {
  color: #666;
  border-right: 1px solid #f5f5f5;
}

.wb-btn-danger {
  color: #ff5c33;
  font-weight: 600;
  background: linear-gradient(135deg, #ff7743, #ff5c33);
  color: #fff;
}
</style>
