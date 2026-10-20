<template>
  <view class="container">
    <!-- 顶部导航 -->
    <view class="wb-header" :style="{ paddingTop: statusBarHeight + 8 + 'px' }">
      <view class="wb-back" @click="goBack"><text class="back-ico">‹</text></view>
      <text class="wb-title">退出当前企业</text>
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
      <view v-if="loading" class="wb-empty"><text class="wb-empty-text">加载中...</text></view>
      <template v-else>
        <!-- 企业信息 -->
        <view class="ex-head">
          <view class="ex-logo">{{ (info.companyName || '企').charAt(0) }}</view>
          <view class="ex-head-info">
            <view class="ex-name-line">
              <text class="ex-name">{{ info.companyName }}</text>
              <text class="ex-tag">✓ 已认证</text>
            </view>
            <text class="ex-meta">企业成员 {{ info.memberCount || 0 }} 人 · 我于 {{ info.joinDate || '—' }} 加入</text>
          </view>
        </view>

        <!-- 我的角色 -->
        <view class="ex-role">
          <view class="ex-role-icon"><text>👔</text></view>
          <view class="ex-role-main">
            <text class="ex-role-title">当前角色：{{ roleText(info.role) }}</text>
            <text class="ex-role-desc">拥有企业全部管理权限，含成员管理与发薪审批</text>
          </view>
        </view>

        <!-- 退出影响 -->
        <view class="ex-note-card">
          <view class="ex-note-title"><text class="note-ico">⚠️</text>退出前请知晓</view>
          <view class="ex-note"><text class="note-dot">●</text><view>退出后将<text class="note-bold">无法查看</text>该企业的岗位、考勤、发薪等全部数据</view></view>
          <view class="ex-note"><text class="note-dot">●</text><view>正在负责的 <text class="note-bold">{{ info.jobCount || 0 }} 个岗位</text>、<text class="note-bold">{{ info.pendingApproval || 0 }} 条审批</text>需先转让给其他管理员</view></view>
          <view class="ex-note"><text class="note-dot">●</text><view>历史发薪记录将保留，可申请导出后留存</view></view>
          <view class="ex-note"><text class="note-dot">●</text><view>如需再次加入，需重新提交申请并经管理员同意</view></view>
        </view>

        <!-- 勾选 -->
        <view class="ex-agree" @click="agreed = !agreed">
          <view class="ex-checkbox" :class="{ on: agreed }"><text v-if="agreed" class="check">✓</text></view>
          <view class="agree-text">我已知晓上述影响，且负责事项已交接完成，<text class="agree-link">《退出企业须知》</text></view>
        </view>
      </template>
    </view>

    <!-- 底部操作 -->
    <view class="wb-footer">
      <view class="ex-btn-danger" :class="{ disabled: !agreed }" @click="openExit">确认退出企业</view>
      <view class="wb-btn-outline-full" @click="goBack">暂不退出</view>
    </view>

    <!-- 二次确认弹窗 -->
    <view class="wb-modal" v-if="exitModalShow" @click.self="closeExit">
      <view class="wb-modal-box">
        <view class="wb-modal-body center">
          <text class="modal-icon red">🚪</text>
          <text class="modal-title">确认退出「{{ info.companyName }}」？</text>
          <text class="modal-desc">退出后企业相关数据将不再可见\n需重新申请方可再次加入</text>
        </view>
        <view class="wb-modal-footer">
          <view class="wb-btn-outline" @click="closeExit">再想想</view>
          <view class="wb-btn-danger" @click="confirmExit">确认退出</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { getExitInfo, exitEnterprise } from "@/api/enterprise";

export default {
  data() {
    return {
      statusBarHeight: 44,
      info: {},
      loading: false,
      agreed: false,
      exitModalShow: false,
    };
  },
  onLoad() {
    try {
      const info = uni.getSystemInfoSync();
      this.statusBarHeight = info.statusBarHeight || 44;
    } catch (e) {}
    this.loadInfo();
  },
  methods: {
    roleText(role) {
      return { OWNER: "超级管理员", ADMIN: "管理员", STAFF: "成员" }[role] || role || "成员";
    },
    async loadInfo() {
      this.loading = true;
      try {
        this.info = (await getExitInfo()) || {};
      } catch (e) {
        uni.showToast({ title: e.message || "加载失败", icon: "none" });
      } finally {
        this.loading = false;
      }
    },
    openExit() {
      if (!this.agreed) {
        uni.showToast({ title: "请先勾选「我已知晓上述影响」", icon: "none" });
        return;
      }
      this.exitModalShow = true;
    },
    closeExit() {
      this.exitModalShow = false;
    },
    async confirmExit() {
      try {
        await exitEnterprise();
        this.closeExit();
        uni.showToast({ title: "已退出企业，正在返回工作台", icon: "none" });
        setTimeout(() => uni.reLaunch({ url: "/pages/boss/workbench" }), 800);
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

.ex-head {
  background: #fff;
  border-radius: 16px;
  padding: 18px 16px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 13px;
}

.ex-logo {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, #ffd96f, #f0a500);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 800;
  color: #4a3500;
  flex-shrink: 0;
}

.ex-head-info {
  flex: 1;
  min-width: 0;
}

.ex-name-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ex-name {
  font-size: 17px;
  font-weight: 700;
  color: #333;
}

.ex-tag {
  background: #e8f8ef;
  color: #10b981;
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 5px;
  font-weight: 600;
}

.ex-meta {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  display: block;
}

.ex-role {
  background: #fff;
  border-radius: 16px;
  padding: 14px 16px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.ex-role-icon {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #fff0e8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  flex-shrink: 0;
}

.ex-role-main {
  flex: 1;
}

.ex-role-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  display: block;
}

.ex-role-desc {
  font-size: 12px;
  color: #999;
  margin-top: 3px;
  display: block;
}

.ex-note-card {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  margin-bottom: 14px;
}

.ex-note-title {
  font-size: 14px;
  font-weight: 700;
  color: #333;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 7px;
}

.note-ico {
  font-size: 14px;
}

.ex-note {
  display: flex;
  gap: 9px;
  font-size: 13px;
  color: #666;
  line-height: 21px;
  margin-bottom: 9px;
}

.ex-note:last-child {
  margin-bottom: 0;
}

.note-dot {
  color: #f59e0b;
  font-size: 8px;
  margin-top: 5px;
  flex-shrink: 0;
}

.note-bold {
  color: #333;
  font-weight: 600;
}

.ex-agree {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  font-size: 12px;
  color: #666;
  margin: 4px 2px 16px;
}

.ex-checkbox {
  width: 17px;
  height: 17px;
  border-radius: 50%;
  border: 1.5px solid #d8d8d8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 9px;
  color: #fff;
  flex-shrink: 0;
  margin-top: 1px;
}

.ex-checkbox.on {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  border-color: #ff6b35;
}

.check {
  font-size: 10px;
}

.agree-text {
  flex: 1;
  line-height: 18px;
}

.agree-link {
  color: #ff6b35;
  font-weight: 500;
}

.wb-footer {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.98);
  border-top: 0.5px solid rgba(0, 0, 0, 0.05);
}

.ex-btn-danger {
  background: linear-gradient(135deg, #ff5c33, #ff7743);
  color: #fff;
  border-radius: 24px;
  padding: 13px 0;
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 6px 16px rgba(255, 92, 51, 0.3);
}

.ex-btn-danger.disabled {
  background: #e5e5e5;
  color: #a8a8a8;
  box-shadow: none;
}

.wb-btn-outline-full {
  border: 1px solid #e5e5e5;
  color: #666;
  border-radius: 24px;
  padding: 12px 0;
  text-align: center;
  font-size: 15px;
  background: #fff;
}

.wb-empty {
  padding: 40px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
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

.wb-modal-body.center {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 20px 18px;
}

.modal-icon {
  font-size: 32px;
}

.modal-icon.red {
  color: #ff5c33;
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
  white-space: pre-line;
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
  background: linear-gradient(135deg, #ff7743, #ff5c33);
  color: #fff;
  font-weight: 600;
}
</style>
