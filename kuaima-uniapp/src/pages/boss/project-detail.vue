<template>
  <view class="container">
    <BossPageHeader title="项目详情" />
    <scroll-view scroll-y class="body">
      <!-- 项目信息 -->
      <view class="card">
        <view v-if="project.name" class="detail-name">{{ project.name }}</view>
        <view class="detail-row">
          <text class="detail-label">用工企业：</text>
          <text class="detail-value">{{ project.companyName || "—" }}</text>
        </view>
        <view class="detail-row">
          <text class="detail-label">负责人：</text>
          <text class="detail-value">{{ project.leaderName || "—" }}</text>
        </view>
        <view class="detail-row">
          <text class="detail-label">立项日期：</text>
          <text class="detail-value">{{ formatCnDate(project.establishDate) || "—" }}</text>
        </view>
        <view class="sign-btn" @click="showSignCode">
          <image class="sign-btn-ico" src="/static/icons/boss-project-detail/qrcode-white.svg" mode="aspectFit" />
          <text>签到码</text>
        </view>
      </view>

      <!-- 快捷功能 -->
      <view class="card">
        <view class="quick-row">
          <view class="quick-item" @click="goAttendance">
            <view class="quick-icon">
              <image class="quick-img" src="/static/icons/boss-project-detail/fingerprint-white.svg" mode="aspectFit" />
            </view>
            <text class="quick-label">考勤打卡</text>
          </view>
          <view class="quick-item" @click="goOnsite">
            <view class="quick-icon orange">
              <image class="quick-img" src="/static/icons/boss-project-detail/location-dot-white.svg" mode="aspectFit" />
            </view>
            <text class="quick-label">驻场管理</text>
          </view>
          <view class="quick-item" @click="goSettings">
            <view class="quick-icon red">
              <image class="quick-img" src="/static/icons/boss-project-detail/gear-white.svg" mode="aspectFit" />
            </view>
            <text class="quick-label">项目设置</text>
          </view>
        </view>
      </view>

      <!-- 项目成员 -->
      <view class="card">
        <view class="card-head">
          <text class="card-head-title">项目成员</text>
          <view class="card-head-more" @click="goMembers">
            <text class="card-head-more-text">查看</text>
            <image class="card-head-more-ico" src="/static/icons/boss-project-detail/chevron-right-orange.svg" mode="aspectFit" />
          </view>
        </view>
        <view class="stats-row">
          <view class="stat">
            <text class="stat-value">{{ memberStats.all || 0 }}</text>
            <text class="stat-label">全部</text>
          </view>
          <view class="stat">
            <text class="stat-value">{{ memberStats.active || 0 }}</text>
            <text class="stat-label">在职</text>
          </view>
          <view class="stat">
            <text class="stat-value">{{ memberStats.left || 0 }}</text>
            <text class="stat-label">离职</text>
          </view>
          <view class="stat">
            <text class="stat-value">{{ memberStats.temp || 0 }}</text>
            <text class="stat-label">临时</text>
          </view>
        </view>
      </view>

      <!-- 人事管理 -->
      <view class="section-title">人事管理</view>
      <view class="card row-card">
        <view class="row-item" @click="goCheckin">
          <view class="row-icon">
            <image class="row-img" src="/static/icons/boss-project-detail/clipboard-user-white.svg" mode="aspectFit" />
          </view>
          <text class="row-title">签到记录</text>
          <image class="row-arrow" src="/static/icons/boss-project-detail/chevron-right-gray.svg" mode="aspectFit" />
        </view>
        <view class="row-item" @click="goOnboard">
          <view class="row-icon orange">
            <image class="row-img" src="/static/icons/boss-profile/user-plus-white.svg" mode="aspectFit" />
          </view>
          <text class="row-title">入职记录</text>
          <image class="row-arrow" src="/static/icons/boss-project-detail/chevron-right-gray.svg" mode="aspectFit" />
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import BossPageHeader from "@/components/BossPageHeader.vue";
import {
  getProject,
  getMemberStats,
  formatCnDate,
} from "@/api/project";

export default {
  components: { BossPageHeader },
  data() {
    return {
      projectId: "",
      project: {},
      memberStats: {},
    };
  },
  onLoad(query) {
    const id = query && (query.id || query.projectId);
    if (!id || id === "undefined" || id === "null") {
      // 工作台「考勤打卡」入口不带项目 id：先让用户选择项目，避免请求 /projects/undefined
      uni.showToast({ title: "请先选择要查看的项目", icon: "none" });
      setTimeout(() => uni.redirectTo({ url: "/pages/boss/projects" }), 600);
      return;
    }
    this.projectId = id;
    this.load();
  },
  methods: {
    formatCnDate,
    async load() {
      if (!this.projectId || this.projectId === "undefined") return;
      try {
        const [project, stats] = await Promise.all([
          getProject(this.projectId).catch(() => ({})),
          getMemberStats(this.projectId).catch(() => ({})),
        ]);
        this.project = project || {};
        this.memberStats = stats || {};
      } catch (e) {
        console.warn("项目详情加载失败", e);
      }
    },
    goAttendance() {
      uni.navigateTo({ url: `/pages/boss/proj-attendance?id=${this.projectId}` });
    },
    goOnsite() {
      uni.navigateTo({ url: `/pages/boss/proj-onsite?id=${this.projectId}` });
    },
    goSettings() {
      uni.navigateTo({ url: `/pages/boss/proj-settings?id=${this.projectId}` });
    },
    goMembers() {
      uni.navigateTo({ url: `/pages/boss/proj-members?id=${this.projectId}` });
    },
    goCheckin() {
      uni.navigateTo({ url: `/pages/boss/proj-checkin?id=${this.projectId}` });
    },
    goOnboard() {
      uni.navigateTo({ url: `/pages/boss/proj-onboard?id=${this.projectId}` });
    },
    showSignCode() {
      uni.navigateTo({ url: `/pages/boss/sign-code?id=${this.projectId}` });
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100%;
  background: #f3f4f6;
  overflow-x: hidden;
  box-sizing: border-box;
}
.body {
  flex: 1;
  overflow-y: auto;
  width: 100%;
  padding: 0 16px 30px;
  box-sizing: border-box;
}
.card {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}
.detail-name { font-size: 17px; font-weight: 700; color: #333; margin-bottom: 10px; }
.detail-row { display: flex; font-size: 13px; margin-top: 7px; }
.detail-label { color: #999; width: 70px; flex-shrink: 0; }
.detail-value { color: #333; flex: 1; }
.sign-btn {
  margin-top: 14px;
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  border-radius: 12px;
  padding: 11px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
}
.sign-btn-ico { width: 15px; height: 15px; margin-right: 6px; flex-shrink: 0; }
.quick-row { display: flex; padding: 16px 0 6px; }
.quick-item { flex: 1; text-align: center; }
.quick-icon {
  width: 46px;
  height: 46px;
  margin: 0 auto 7px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #ff8c5a, #ff6b35);
}
.quick-icon.orange { background: linear-gradient(135deg, #ffb84d, #f09a3e); }
.quick-icon.red { background: linear-gradient(135deg, #ff7743, #ff5c33); }
.quick-img { width: 20px; height: 20px; }
.quick-label { font-size: 12px; color: #666; }
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.card-head-title { font-size: 15px; font-weight: 600; color: #333; }
.card-head-more { display: flex; align-items: center; gap: 3px; }
.card-head-more-text { font-size: 12px; color: #ff6b35; }
.card-head-more-ico { width: 10px; height: 10px; flex-shrink: 0; }
.stats-row { display: flex; }
.stat { flex: 1; text-align: center; position: relative; }
.stat:not(:last-child)::after {
  content: "";
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  height: 20px;
  width: 0.5px;
  background: #f0f0f0;
}
.stat-value { font-size: 20px; font-weight: 700; color: #333; display: block; }
.stat-label { font-size: 11px; color: #999; margin-top: 3px; display: block; }
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin: 18px 2px 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.section-title::before {
  content: "";
  width: 4px;
  height: 15px;
  border-radius: 2px;
  background: linear-gradient(180deg, #ff6b35, #ff8c5a);
}
.row-card { padding: 4px 16px; }
.row-item {
  display: flex;
  align-items: center;
  padding: 13px 0;
  border-bottom: 0.5px solid #f0f0f0;
}
.row-item:last-child { border-bottom: none; }
.row-icon {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  flex-shrink: 0;
  background: linear-gradient(135deg, #ff8c5a, #ff6b35);
}
.row-icon.orange { background: linear-gradient(135deg, #ffb84d, #f09a3e); }
.row-img { width: 16px; height: 16px; }
.row-title { flex: 1; font-size: 15px; font-weight: 500; color: #333; }
.row-arrow { width: 12px; height: 12px; flex-shrink: 0; }
</style>
