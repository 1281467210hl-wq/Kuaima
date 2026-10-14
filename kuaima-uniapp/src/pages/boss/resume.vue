<template>
  <view class="container">
    <!-- 顶部导航 -->
    <view class="wb-header" :style="{ paddingTop: statusBarHeight + 8 + 'px' }">
      <view class="wb-back" @click="goBack"><image class="back-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" /></view>
      <text class="wb-title">简历库</text>
      <!-- 微信小程序原生胶囊已占右上角，自绘胶囊会与其重叠，仅在非小程序端保留 -->
      <!-- #ifndef MP-WEIXIN -->
      <view class="wb-capsule">
        <view class="cap-btn"><image class="cap-svg" src="/static/icons/boss-profile/ellipsis.svg" mode="aspectFit" /></view>
        <view class="cap-divider"></view>
        <view class="cap-btn"><image class="cap-svg cap-svg-dot" src="/static/icons/boss-profile/dot.svg" mode="aspectFit" /></view>
      </view>
      <!-- #endif -->
    </view>

    <view class="wb-body has-footer">
      <!-- 搜索 -->
      <view class="wb-search" @click="goList()">
        <image class="search-svg" src="/static/icons/boss-points/search-gray.svg" mode="aspectFit" />
        <input type="text" placeholder="搜索职位、姓名、关键词等" disabled />
      </view>

      <!-- 统计卡片（原型 boss-resume.html：两张卡 —— 简历总数 / 今日新增） -->
      <view class="stat-grid">
        <view class="stat-mini">
          <view class="stat-mini-icon" style="background: linear-gradient(135deg, #4A90E2, #357ABD)"><image class="stat-svg" src="/static/icons/boss-workbench/file-lines-white.svg" mode="aspectFit" /></view>
          <view class="stat-mini-info">
            <text class="stat-mini-num">{{ stats.total || 0 }}</text>
            <text class="stat-mini-label">简历总数</text>
            <text v-if="stats.totalRate" class="stat-mini-rate">↑ {{ stats.totalRate }}%</text>
          </view>
        </view>
        <view class="stat-mini">
          <view class="stat-mini-icon" style="background: linear-gradient(135deg, #10B981, #059669)"><image class="stat-svg" src="/static/icons/boss-profile/user-plus-white.svg" mode="aspectFit" /></view>
          <view class="stat-mini-info">
            <text class="stat-mini-num">{{ stats.todayNew || 0 }}</text>
            <text class="stat-mini-label">今日新增</text>
            <text v-if="stats.todayNewRate" class="stat-mini-rate">↑ {{ stats.todayNewRate }}%</text>
          </view>
        </view>
      </view>

      <!-- 最新简历 -->
      <view class="section-head">
        <text class="section-title">最新简历</text>
        <view class="section-more" @click="goList()">查看更多<image class="more-svg" src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" /></view>
      </view>
      <view class="wb-card">
        <view v-if="loading" class="wb-empty"><text class="wb-empty-text">加载中...</text></view>
        <view v-else-if="!latest.length" class="wb-empty">
          <view class="wb-empty-icon"><image class="empty-svg" src="/static/icons/boss-resume/file-lines-gray.svg" mode="aspectFit" /></view>
          <text class="wb-empty-text">暂无简历</text>
        </view>
        <view v-for="r in latest" :key="r.id" class="resume-row" @click="goDetail(r)">
          <view class="resume-avatar" :style="{ background: avatarColor(r.name) }">{{ (r.name || '人').charAt(0) }}</view>
          <view class="resume-main">
            <view class="resume-name-line">
              <text class="resume-name">{{ r.name }}</text>
              <text class="resume-pos">{{ r.position || '' }}</text>
            </view>
            <text class="resume-sub">{{ r.gender || '—' }} · {{ r.experience || '—' }} · {{ r.age || '—' }}岁</text>
          </view>
          <view class="resume-right">
            <text class="resume-date">{{ r.date || '' }}</text>
            <text class="wb-tag" :class="statusCls(r.status)">{{ statusText(r.status) }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 底部按钮（原型：简历导入 描边 + 进入简历管理 主按钮，各占一半） -->
    <view class="wb-footer">
      <view class="footer-btn-outline" @click="goImport"><image class="footer-svg" src="/static/icons/enterprise-cert-form/cloud-arrow-up.svg" mode="aspectFit" /><text>简历导入</text></view>
      <view class="footer-btn-primary" @click="goList()"><image class="footer-svg" src="/static/icons/boss-authorize/clipboard-list-white.svg" mode="aspectFit" /><text>进入简历管理</text></view>
    </view>
  </view>
</template>

<script>
import { getResumeStats, getLatestResumes, RESUME_STATUS_TEXT, RESUME_STATUS_CLS } from "@/api/resume";
import { avatarColor } from "@/api/project";

export default {
  data() {
    return {
      statusBarHeight: 44,
      stats: {},
      latest: [],
      loading: false,
    };
  },
  onLoad() {
    try {
      const info = uni.getSystemInfoSync();
      this.statusBarHeight = info.statusBarHeight || 44;
    } catch (e) {}
    this.loadData();
  },
  methods: {
    avatarColor,
    statusText: (s) => RESUME_STATUS_TEXT[s] || s,
    statusCls: (s) => RESUME_STATUS_CLS[s] || "gray",
    async loadData() {
      this.loading = true;
      try {
        this.stats = (await getResumeStats()) || {};
        this.latest = (await getLatestResumes()) || [];
      } catch (e) {
        uni.showToast({ title: e.message || "加载失败", icon: "none" });
      } finally {
        this.loading = false;
      }
    },
    goList(tab) {
      uni.navigateTo({ url: `/pages/boss/resume-list${tab ? `?tab=${tab}` : ""}` });
    },
    goImport() {
      uni.navigateTo({ url: "/pages/boss/resume-import" });
    },
    goDetail(r) {
      uni.navigateTo({ url: `/pages/boss/resume-detail?id=${r.id}` });
    },
    goBack() {
      uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/boss/workbench" }) });
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
  padding: 8px 16px 12px;
  flex-shrink: 0;
}

.wb-back {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.back-svg {
  width: 16px;
  height: 16px;
}

.wb-title {
  flex: 1;
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
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

.cap-svg {
  width: 14px;
  height: 14px;
}

.cap-svg-dot {
  width: 9px;
  height: 9px;
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

.wb-body.has-footer {
  padding-bottom: 90px;
}

.wb-search {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border-radius: 12px;
  padding: 11px 14px;
  margin-bottom: 14px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}

.search-svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

.wb-search input {
  flex: 1;
  font-size: 14px;
  color: #333;
}

.stat-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 14px;
}

.stat-mini {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  display: flex;
  align-items: center;
  gap: 11px;
}

.stat-mini-icon {
  width: 40px;
  height: 40px;
  border-radius: 11px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-svg {
  width: 18px;
  height: 18px;
}

.stat-mini-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.stat-mini-num {
  font-size: 19px;
  font-weight: 700;
  color: #333;
  line-height: 1.1;
  display: block;
}

.stat-mini-label {
  font-size: 11px;
  color: #999;
  margin-top: 3px;
  display: block;
}

/* 原型的增长率：后端暂未返回 totalRate / todayNewRate，缺数据时不渲染 */
.stat-mini-rate {
  font-size: 11px;
  color: #10b981;
  margin-top: 2px;
  font-weight: 600;
  display: block;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 18px 2px 10px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  display: flex;
  align-items: center;
}

.section-title::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 14px;
  background: #ff6b35;
  border-radius: 2px;
  margin-right: 6px;
}

.section-more {
  font-size: 12px;
  color: #999;
  display: flex;
  align-items: center;
  gap: 2px;
}

.more-svg {
  width: 10px;
  height: 10px;
}

.wb-card {
  background: #fff;
  border-radius: 16px;
  padding: 4px 16px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}

.resume-row {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 0;
  border-bottom: 0.5px solid #f4f4f4;
}

.resume-row:last-child {
  border-bottom: none;
}

.resume-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.resume-main {
  flex: 1;
  min-width: 0;
}

.resume-name-line {
  display: flex;
  align-items: center;
  gap: 7px;
}

.resume-name {
  font-size: 14px;
  font-weight: 600;
  color: #222;
}

.resume-pos {
  font-size: 13px;
  color: #666;
}

.resume-sub {
  font-size: 11px;
  color: #999;
  margin-top: 3px;
  display: block;
}

.resume-right {
  text-align: right;
  flex-shrink: 0;
}

.resume-date {
  font-size: 11px;
  color: #bbb;
  margin-bottom: 5px;
  display: block;
}

.wb-tag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 5px;
  font-weight: 600;
}

.wb-tag.blue {
  background: #ebf3ff;
  color: #357abd;
}

.wb-tag.green {
  background: #e8f8ef;
  color: #10b981;
}

.wb-tag.orange {
  background: #fff7e0;
  color: #d97706;
}

.wb-tag.gray {
  background: #f5f5f5;
  color: #999;
}

.wb-empty {
  padding: 30px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.wb-empty-icon {
  margin-bottom: 10px;
}

.empty-svg {
  width: 34px;
  height: 34px;
}

.wb-empty-text {
  font-size: 13px;
  color: #999;
}

.wb-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 10px;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.98);
  border-top: 0.5px solid rgba(0, 0, 0, 0.05);
}

/* 简历导入：原型底部描边按钮（wb-btn-outline） */
.footer-btn-outline {
  flex: 1;
  border: 1px solid #e0e0e0;
  color: #666;
  background: #fff;
  border-radius: 24px;
  padding: 12px 0;
  text-align: center;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
}

.footer-btn-primary {
  flex: 1;
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  border-radius: 24px;
  padding: 13px 0;
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 6px 16px rgba(255, 107, 53, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.footer-svg {
  width: 16px;
  height: 16px;
}
</style>
