<template>
  <view class="container">
    <!-- 顶部企业信息区 -->
    <view class="top-header">
      <view class="status-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
        <text>{{ currentTime }}</text>
        <view class="status-icons">
          <image class="status-svg" src="/static/icons/boss-profile/signal.svg" mode="aspectFit" />
          <image class="status-svg" src="/static/icons/boss-profile/wifi.svg" mode="aspectFit" />
          <image class="status-svg" src="/static/icons/boss-profile/battery.svg" mode="aspectFit" />
        </view>
      </view>
      <view class="company-nav">
        <text class="company-name">{{ companyName }}</text>
        <!-- 微信小程序右上角由微信绘制原生胶囊（⋯ / ⊙），自绘胶囊会与其重叠，故仅在非小程序端保留 -->
        <!-- #ifdef H5 -->
        <view class="wb-capsule">
          <view class="cap-btn"><image class="cap-svg" src="/static/icons/boss-profile/ellipsis.svg" mode="aspectFit" /></view>
          <view class="cap-divider"></view>
          <view class="cap-btn"><image class="cap-svg cap-svg-dot" src="/static/icons/boss-profile/dot.svg" mode="aspectFit" /></view>
        </view>
        <!-- #endif -->
      </view>

      <!-- 入口卡片 -->
      <view class="entry-cards">
        <view class="entry-card members" @click="navigateTo('members')">
          <text class="entry-name">企业成员</text>
          <text class="entry-desc">内部员工 团队协作</text>
          <image class="entry-svg" src="/static/icons/boss-workbench/users-white.svg" mode="aspectFit" />
        </view>
        <view class="entry-card projects" @click="navigateTo('projects')">
          <text class="entry-name">项目管理</text>
          <text class="entry-desc">签到考勤 入职离职</text>
          <image class="entry-svg" src="/static/icons/boss-workbench/diagram-project-white.svg" mode="aspectFit" />
        </view>
      </view>
    </view>

    <!-- 滚动内容区 -->
    <scroll-view scroll-y class="scroll-area">
      <!-- 智能招聘 -->
      <view class="wb-section-title" style="margin-top:2px;">
        <view class="wb-section-title-bar"></view>
        <text>智能招聘</text>
      </view>
      <view class="wb-grid">
        <view class="wb-grid-item" @click="navigateTo('resume')">
          <view class="wb-grid-icon" style="background: linear-gradient(135deg, #FF8C5A, #FF6B35)">
            <image class="grid-svg" src="/static/icons/boss-workbench/file-lines-white.svg" mode="aspectFit" />
          </view>
          <view class="wb-grid-info">
            <text class="wb-grid-name">简历库</text>
            <text class="wb-grid-desc">求职人员简历管理</text>
          </view>
        </view>
        <view class="wb-grid-item" @click="navigateTo('position')">
          <view class="wb-grid-icon" style="background: linear-gradient(135deg, #FFB84D, #F09A3E)">
            <image class="grid-svg" src="/static/icons/boss-authorize/briefcase-white.svg" mode="aspectFit" />
          </view>
          <view class="wb-grid-info">
            <text class="wb-grid-name">岗位管理</text>
            <text class="wb-grid-desc">零工岗位发布、编辑</text>
          </view>
        </view>
        <view class="wb-grid-item" @click="navigateTo('live-showcase')">
          <view class="wb-grid-icon" style="background: linear-gradient(135deg, #FF7743, #FF5C33)">
            <image class="grid-svg" src="/static/icons/boss-workbench/video-white.svg" mode="aspectFit" />
          </view>
          <view class="wb-grid-info">
            <text class="wb-grid-name">直播橱窗</text>
            <text class="wb-grid-desc">抖音直播、带岗招聘</text>
          </view>
        </view>
        <view class="wb-grid-item" @click="navigateTo('candidates')">
          <view class="wb-grid-icon" style="background: linear-gradient(135deg, #FFA94D, #FF8C42)">
            <image class="grid-svg" src="/static/icons/boss-workbench/user-check-white.svg" mode="aspectFit" />
          </view>
          <view class="wb-grid-info">
            <text class="wb-grid-name">候选人</text>
            <text class="wb-grid-desc">报名人员自动分配</text>
          </view>
        </view>
      </view>

      <!-- 闪电发薪 -->
      <view class="wb-section-title">
        <view class="wb-section-title-bar"></view>
        <text>闪电发薪</text>
      </view>
      <view class="wb-grid">
        <view class="wb-grid-item" @click="navigateTo('payroll')">
          <view class="wb-grid-icon" style="background: linear-gradient(135deg, #FF8C5A, #FF6B35)">
            <image class="grid-svg" src="/static/icons/boss-workbench/money-bill-wave-white.svg" mode="aspectFit" />
          </view>
          <view class="wb-grid-info">
            <text class="wb-grid-name">批量发薪</text>
            <text class="wb-grid-desc">一键制单、批量发薪</text>
          </view>
        </view>
        <view class="wb-grid-item" @click="navigateTo('approve')">
          <view class="wb-grid-icon" style="background: linear-gradient(135deg, #FFB84D, #F09A3E)">
            <image class="grid-svg" src="/static/icons/boss-workbench/clipboard-check-white.svg" mode="aspectFit" />
          </view>
          <view class="wb-grid-info">
            <text class="wb-grid-name">待我审批</text>
            <text class="wb-grid-desc">审核转账申请</text>
          </view>
        </view>
        <view class="wb-grid-item" @click="navigateTo('transfers')">
          <view class="wb-grid-icon" style="background: linear-gradient(135deg, #FF7743, #FF5C33)">
            <image class="grid-svg" src="/static/icons/boss-workbench/right-left-white.svg" mode="aspectFit" />
          </view>
          <view class="wb-grid-info">
            <text class="wb-grid-name">转账记录</text>
            <text class="wb-grid-desc">转账记录及明细查询</text>
          </view>
        </view>
        <view class="wb-grid-item" @click="navigateTo('balance')">
          <view class="wb-grid-icon" style="background: linear-gradient(135deg, #FFA94D, #FF8C42)">
            <image class="grid-svg" src="/static/icons/boss-workbench/wallet-white.svg" mode="aspectFit" />
          </view>
          <view class="wb-grid-info">
            <text class="wb-grid-name">余额查询</text>
            <text class="wb-grid-desc">商户号余额管理</text>
          </view>
        </view>
      </view>

      <!-- 员工管理 -->
      <view class="wb-section-title">
        <view class="wb-section-title-bar"></view>
        <text>员工管理</text>
      </view>
      <view class="wb-grid">
        <view class="wb-grid-item" @click="navigateTo('members-staff')">
          <view class="wb-grid-icon" style="background: linear-gradient(135deg, #FF8C5A, #FF6B35)">
            <image class="grid-svg" src="/static/icons/boss-workbench/user-group-white.svg" mode="aspectFit" />
          </view>
          <view class="wb-grid-info">
            <text class="wb-grid-name">员工列表</text>
            <text class="wb-grid-desc">企业员工信息管理</text>
          </view>
        </view>
        <view class="wb-grid-item" @click="goAttendance">
          <view class="wb-grid-icon" style="background: linear-gradient(135deg, #FFB84D, #F09A3E)">
            <image class="grid-svg" src="/static/icons/boss-workbench/user-clock-white.svg" mode="aspectFit" />
          </view>
          <view class="wb-grid-info">
            <text class="wb-grid-name">考勤打卡</text>
            <text class="wb-grid-desc">员工签到考勤管理</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 底部TabBar -->
    <view class="tab-bar">
      <view v-if="canAccess('HOME_VIEW')" class="tab-item" @click="switchTab('home')">
        <view class="tab-icon-wrap"><image class="tab-svg" src="/static/icons/boss-tabbar/house-gray.svg" mode="aspectFit" /></view>
        <text class="tab-label">首页</text>
      </view>
      <view v-if="canAccess('ORDER_VIEW')" class="tab-item" @click="switchTab('order')">
        <view class="tab-icon-wrap"><image class="tab-svg" src="/static/icons/boss-tabbar/calendar-check-gray.svg" mode="aspectFit" /></view>
        <text class="tab-label">招工订单</text>
      </view>
      <view v-if="canAccess('WORKBENCH_VIEW')" class="tab-item active">
        <view class="tab-icon-wrap active"><image class="tab-svg" src="/static/icons/boss-tabbar/briefcase-white.svg" mode="aspectFit" /></view>
        <text class="tab-label">工作台</text>
      </view>
      <view v-if="canAccess('MESSAGE_VIEW')" class="tab-item" @click="switchTab('message')">
        <view class="tab-icon-wrap"><image class="tab-svg" src="/static/icons/boss-tabbar/comment-dots-gray.svg" mode="aspectFit" /></view>
        <text class="tab-label">消息</text>
      </view>
      <view class="tab-item" @click="switchTab('profile')">
        <view class="tab-icon-wrap"><image class="tab-svg" src="/static/icons/boss-tabbar/face-smile-gray.svg" mode="aspectFit" /></view>
        <text class="tab-label">我的</text>
      </view>
    </view>
  </view>
</template>

<script>
import { hasEnterprisePermission } from "@/api/enterprise-context";
import { getBossProfile } from "@/api/backend";
import { listProjects } from "@/api/project";
export default {
  data() {
    return {
      statusBarHeight: 44,
      currentTime: "13:14",
      companyName: "我的企业",
    };
  },
  onLoad() {
    try {
      const sysInfo = uni.getSystemInfoSync();
      this.statusBarHeight = sysInfo.statusBarHeight || 44;
    } catch (e) {
      // ignore
    }
    const now = new Date();
    this.currentTime = `${now.getHours()}:${String(now.getMinutes()).padStart(2, "0")}`;
    this.loadCompanyName();
  },
  onShow() {
    // 企业切换后回到工作台，刷新企业名称保持与个人中心一致
    this.loadCompanyName();
  },
  methods: {
    loadCompanyName() {
      const cached = uni.getStorageSync("enterpriseName");
      if (cached) this.companyName = cached;
      const userId = uni.getStorageSync("userId");
      if (!userId) return;
      getBossProfile(userId)
        .then((p) => {
          const name = (p && (p.companyName || p.enterpriseName || p.name)) || "";
          if (name) this.companyName = name;
        })
        .catch(() => {});
    },
    canAccess(code) { return hasEnterprisePermission(code); },
    goAttendance() {
      // 考勤打卡页需要项目ID：取当前企业第一个项目进入；无项目则提示先创建
      listProjects()
        .then((res) => {
          const list = Array.isArray(res) ? res : (res && (res.content || res.records)) || [];
          if (!list.length) {
            uni.showToast({ title: "暂无项目，请先在项目管理中创建", icon: "none" });
            return;
          }
          uni.navigateTo({ url: `/pages/boss/proj-attendance?id=${list[0].id}` });
        })
        .catch(() => {
          uni.showToast({ title: "项目加载失败，请稍后重试", icon: "none" });
        });
    },
    navigateTo(page) {
      const pages = {
        members: "/pages/boss/members",
        "members-staff": "/pages/boss/members?src=staff",
        projects: "/pages/boss/projects",
        resume: "/pages/boss/resume",
        position: "/pages/boss/position",
        "live-showcase": "/pages/boss/live-showcase",
        "talent-list": "/pages/boss/talent-list",
        "candidates": "/pages/boss/candidates",
        payroll: "/pages/boss/payroll",
        approve: "/pages/boss/approve",
        transfers: "/pages/boss/transfers",
        balance: "/pages/boss/balance",
        "payroll-employees": "/pages/boss/payroll-employees",
      };
      if (pages[page]) {
        uni.navigateTo({ url: pages[page] });
      }
    },
    switchTab(tab) {
      const tabPages = {
        home: "/pages/boss/home",
        order: "/pages/boss/order",
        workbench: "/pages/boss/workbench",
        message: "/pages/boss/message",
        profile: "/pages/boss/profile",
      };
      const target = tabPages[tab];
      const currentRoute = getCurrentPages().slice(-1)[0]?.route;
      if (!target || `/${currentRoute}` === target) return;
      uni.redirectTo({
        url: target,
        fail: (error) => {
          console.error("Boss 主导航跳转失败", error);
          uni.reLaunch({
            url: target,
            fail: () =>
              uni.showToast({ title: "页面跳转失败，请重试", icon: "none" }),
          });
        },
      });
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  height: 100vh;
  width: 100%;
  display: flex;
  flex-direction: column;
  background: #f7f7f7;
  overflow-x: hidden;
  box-sizing: border-box;
}

.top-header {
  background: linear-gradient(180deg, #ffd96f 0%, #ffe7a3 70%, #f7f7f7 100%);
  padding: 0 16px 20px;
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;
}

.status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 20px;
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.status-icons {
  display: flex;
  gap: 5px;
}

.status-icon {
  font-size: 12px;
}

.status-svg {
  width: 12px;
  height: 12px;
}

.company-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0 14px;
}

.company-name {
  font-size: 16px;
  font-weight: 600;
  color: #4a3500;
}

.wb-capsule {
  display: flex;
  align-items: center;
  gap: 2px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 20px;
  padding: 4px 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}

.cap-btn {
  width: 28px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cap-ico {
  font-size: 16px;
  color: #4a3500;
}

.cap-svg {
  width: 16px;
  height: 16px;
}

.cap-svg-dot {
  width: 20px;
  height: 20px;
}

.cap-divider {
  width: 1px;
  height: 14px;
  background: #ddd;
}

.entry-cards {
  display: flex;
  gap: 12px;
  width: 100%;
  box-sizing: border-box;
}

.entry-card {
  flex: 1;
  border-radius: 16px;
  padding: 16px 14px;
  position: relative;
  overflow: hidden;
  min-height: 92px;
  cursor: pointer;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  transition: transform 0.15s;
}

.entry-card:active {
  transform: scale(0.97);
}

.entry-card.members {
  background: linear-gradient(135deg, #ff7a45 0%, #ff9a62 100%);
  box-shadow: 0 6px 16px rgba(255, 122, 69, 0.35);
}

.entry-card.projects {
  background: linear-gradient(135deg, #f0a500 0%, #ffc53d 100%);
  box-shadow: 0 6px 16px rgba(240, 165, 0, 0.35);
}

.entry-name {
  font-size: 17px;
  font-weight: 700;
  color: #fff;
}

.entry-desc {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.9);
  margin-top: 6px;
}

.entry-icon {
  position: absolute;
  right: 10px;
  bottom: 8px;
  font-size: 34px;
  color: rgba(255, 255, 255, 0.3);
}

.entry-svg {
  position: absolute;
  right: 10px;
  bottom: 8px;
  width: 34px;
  height: 34px;
  opacity: 0.3;
}

.scroll-area {
  flex: 1;
  min-height: 0;
  height: 0;
  overflow-y: auto;
  padding: 0 16px 20px;
  width: 100%;
  box-sizing: border-box;
  -webkit-overflow-scrolling: touch;
}

.wb-section-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin: 18px 2px 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.wb-section-title-bar {
  width: 4px;
  height: 15px;
  border-radius: 2px;
  background: linear-gradient(180deg, #ff6b35, #ff8c5a);
}

.wb-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
  box-sizing: border-box;
}

.wb-grid-item {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  transition: transform 0.15s;
  cursor: pointer;
  box-sizing: border-box;
  min-width: 0;
}

.wb-grid-item:active {
  transform: scale(0.97);
}

.wb-grid-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.grid-ico {
  font-size: 20px;
}

.grid-svg {
  width: 17px;
  height: 17px;
}

.wb-grid-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.wb-grid-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.wb-grid-desc {
  font-size: 11px;
  color: #999;
  margin-top: 2px;
}

.tab-bar {
  box-sizing: content-box;
  flex-shrink: 0;
  height: 63px;
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(20px);
  border-top: 0.5px solid rgba(0, 0, 0, 0.05);
  display: flex;
  padding-bottom: env(safe-area-inset-bottom);
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

.tab-item.active .tab-label {
  color: #FF6B35;
}

.tab-svg {
  width: 22px;
  height: 22px;
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

.tab-item.active .tab-icon-wrap {
  background: linear-gradient(135deg, #FF6B35, #FF8C5A);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  color: white;
  margin-bottom: 2px;
  box-shadow: 0 4px 10px rgba(255, 107, 53, 0.3);
}
</style>
