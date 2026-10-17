<template>
  <view class="page">
    <view class="page-nav" :style="{ paddingTop: `${statusBarHeight}px` }"
      ><view class="page-nav-inner"><text>我的</text></view></view
    >
    <scroll-view scroll-y class="content">
      <view class="user-header">
        <view class="user-main" @click="go('/pages/worker/user-info')">
          <view class="avatar"
            ><image :src="profileAvatar" mode="aspectFill"
          /></view>
          <view class="user-detail"
            ><view class="name-row"
              ><text class="name">{{ profileName }}｜零工</text
              ><image
                class="name-arrow"
                :src="chevronBrownIcon"
                mode="aspectFit" /></view
          ></view>
        </view>
        <view
          class="switch-btn"
          @click="go('/pages/worker/switch-identity?role=worker')"
          ><image :src="exchangeOrangeIcon" mode="aspectFit" /><text
            >我要招人</text
          ></view
        >
      </view>

      <view class="star-card" @click="go('/pages/worker/star-level')">
        <view class="star-card-top">
          <view class="star-level"
            ><image class="medal" :src="medalIcon" mode="aspectFit" /><text>{{
              starLevelText
            }}</text></view
          >
          <view class="credit-score" @click.stop="go('/pages/worker/credit')"
            ><text>星级分</text
            ><text class="credit-value">{{ display(stats.creditScore) }}</text
            ><image :src="chevronGreenIcon" mode="aspectFit"
          /></view>
        </view>
        <view class="star-stats">
          <view v-for="item in rateStats" :key="item.label" class="star-stat"
            ><text class="stat-value">{{ percentage(item.value) }}</text
            ><text class="stat-label">{{ item.label }}</text></view
          >
        </view>
        <view class="star-bottom">
          <view class="bottom-item"
            ><text>累计获得报酬</text
            ><text class="bottom-value">{{
              moneyOrDash(stats.totalIncome)
            }}</text></view
          >
          <view class="bottom-separator" />
          <view class="bottom-item order-count"
            ><text>完单数</text
            ><text class="bottom-value">{{
              display(stats.completedOrders)
            }}</text></view
          >
        </view>
      </view>

      <view class="asset-card">
        <view class="asset-item" @click="go('/pages/worker/points')"
          ><view
            ><text class="asset-label">积分</text
            ><text class="asset-value">{{ display(assets.points) }}</text></view
          ><image :src="chevronGrayIcon" mode="aspectFit"
        /></view>
        <view class="asset-item" @click="go('/pages/worker/reward')"
          ><view
            ><text class="asset-label">奖励金(元)</text
            ><text class="asset-value">{{
              moneyOrDash(assets.rewardAmount, false)
            }}</text></view
          ><image :src="chevronGrayIcon" mode="aspectFit"
        /></view>
        <view class="asset-item" @click="go('/pages/worker/wallet')"
          ><view
            ><text class="asset-label">我的钱包</text
            ><text class="asset-value"
              >¥{{ moneyOrDash(assets.walletBalance, false) }}</text
            ></view
          ><image :src="chevronGrayIcon" mode="aspectFit"
        /></view>
      </view>

      <text class="section-title">我的服务</text>
      <view class="menu-grid service-grid">
        <view class="grid-item" @click="go('/pages/worker/join-group')"
          ><image class="grid-icon" :src="usersIcon" mode="aspectFit" /><text
            class="grid-label"
            >进找活群</text
          ></view
        >
        <view class="grid-item" @click="go('/pages/worker/service')"
          ><image class="grid-icon" :src="headsetIcon" mode="aspectFit" /><text
            class="grid-label"
            >联系客服</text
          ></view
        >
      </view>

      <text class="section-title">其他功能</text>
      <view class="menu-grid">
        <view
          v-for="item in otherMenus"
          :key="item.key"
          class="grid-item"
          @click="go(item.url)"
          ><image class="grid-icon" :src="item.icon" mode="aspectFit" /><text
            class="grid-label"
            >{{ item.label }}</text
          ></view
        >
      </view>
      <view class="bottom-space" />
    </scroll-view>
    <WorkerTabBar current="profile" />
  </view>
</template>

<script setup>
import { computed, reactive, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import WorkerTabBar from "@/components/WorkerTabBar.vue";
import { getWorkerProfile, getWorkerProfileOverview } from "@/api/backend";
import { request } from "@/api/http";
import defaultWorkerAvatar from "/static/avatars/default-worker-avatar.png";
import chevronBrownIcon from "/static/icons/worker-profile/chevron-right-brown.svg";
import chevronGrayIcon from "/static/icons/worker-profile/chevron-right-gray.svg";
import chevronGreenIcon from "/static/icons/worker-profile/chevron-right-green.svg";
import exchangeOrangeIcon from "/static/icons/worker-profile/exchange-orange.svg";
import medalIcon from "/static/icons/worker-profile/medal.svg";
import usersIcon from "/static/icons/worker-profile/users-dark.svg";
import headsetIcon from "/static/icons/worker-profile/headset-dark.svg";
import exchangeIcon from "/static/icons/worker-profile/exchange-dark.svg";
import idCardIcon from "/static/icons/worker-profile/id-card-dark.svg";
import balanceIcon from "/static/icons/worker-profile/balance-dark.svg";
import fileIcon from "/static/icons/worker-profile/file-lines-dark.svg";
import userLockIcon from "/static/icons/worker-profile/user-lock-dark.svg";
import copyrightIcon from "/static/icons/worker-profile/copyright-dark.svg";

const profile = ref({});
const statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 0;
const stats = reactive({
  level: null,
  creditScore: null,
  completionRate: null,
  cancellationRate: null,
  noShowRate: null,
  earlyLeaveRate: null,
  totalIncome: null,
  completedOrders: null,
});
const assets = reactive({
  points: null,
  rewardAmount: null,
  walletBalance: null,
});
const profileAvatar = computed(
  () => profile.value.avatar || profile.value.avatarUrl || defaultWorkerAvatar,
);
const profileName = computed(
  () =>
    profile.value.nickname ||
    profile.value.name ||
    profile.value.realName ||
    profile.value.username ||
    "未设置昵称",
);
const starLevelText = computed(() =>
  stats.level == null ? "星级待评定" : `${chineseLevel(stats.level)}星零工`,
);
const rateStats = computed(() => [
  { label: "完成率", value: stats.completionRate },
  { label: "取消率", value: stats.cancellationRate },
  { label: "失约率", value: stats.noShowRate },
  { label: "早退率", value: stats.earlyLeaveRate },
]);
const otherMenus = [
  {
    key: "switch",
    label: "切换账号",
    icon: exchangeIcon,
    url: "/pages/worker/switch-identity",
  },
  {
    key: "realname",
    label: "实名认证",
    icon: idCardIcon,
    url: "/pages/worker/realname",
  },
  {
    key: "rule",
    label: "平台规则",
    icon: balanceIcon,
    url: "/pages/worker/rule",
  },
  {
    key: "agreement",
    label: "用户服务协议",
    icon: fileIcon,
    url: "/pages/worker/user-agreement",
  },
  {
    key: "privacy",
    label: "隐私协议",
    icon: userLockIcon,
    url: "/pages/worker/privacy",
  },
  {
    key: "copyright",
    label: "知识产权规则",
    icon: copyrightIcon,
    url: "/pages/worker/copyright",
  },
];

onShow(loadProfile);

async function loadProfile() {
  const [profileResult, overviewResult, walletResult] =
    await Promise.allSettled([
      getWorkerProfile(),
      getWorkerProfileOverview(),
      request({ url: "/worker/wallet" }),
    ]);
  const cached = uni.getStorageSync("userInfo") || {};
  profile.value =
    profileResult.status === "fulfilled"
      ? { ...cached, ...(profileResult.value || {}) }
      : cached;
  if (overviewResult.status === "fulfilled" && overviewResult.value) {
    applyOverview(overviewResult.value);
  } else if (overviewResult.status === "rejected") {
    uni.showToast({
      title: overviewResult.reason?.message || "我的数据加载失败",
      icon: "none",
    });
  }
  if (walletResult.status === "fulfilled" && walletResult.value) {
    const wallet = walletResult.value;
    assets.walletBalance = Number(
      wallet.balance ?? wallet.available ?? wallet.availableBalance ?? 0,
    );
  }
}

function applyOverview(data) {
  stats.level = data.level ?? null;
  stats.creditScore = data.creditScore ?? null;
  stats.completionRate = data.completionRate ?? 0;
  stats.cancellationRate = data.cancellationRate ?? 0;
  stats.noShowRate = data.noShowRate ?? 0;
  stats.earlyLeaveRate = data.earlyLeaveRate ?? 0;
  stats.totalIncome = fromCents(data.totalIncome);
  stats.completedOrders = data.completedOrders ?? 0;
  assets.points = data.points ?? 0;
  assets.rewardAmount = fromCents(data.rewardAmount);
}

function fromCents(value) {
  if (value === null || value === undefined || value === "") return null;
  const cents = Number(value);
  return Number.isFinite(cents) ? cents / 100 : null;
}

function chineseLevel(value) {
  return ["零", "一", "二", "三", "四", "五"][Number(value)] || String(value);
}
function display(value) {
  return value === null || value === undefined || value === ""
    ? "--"
    : Number(value).toLocaleString("zh-CN");
}
function percentage(value) {
  return value === null || value === undefined || value === ""
    ? "--"
    : `${Number(value)}%`;
}
function moneyOrDash(value, withUnit = true) {
  if (value === null || value === undefined || value === "") return "--";
  const text = Number(value).toLocaleString("zh-CN", {
    minimumFractionDigits: withUnit ? 0 : 2,
    maximumFractionDigits: 2,
  });
  return withUnit ? `${text}元` : text;
}
function go(url) {
  uni.navigateTo({ url });
}
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: #f7f7f7;
}
.page-nav {
  position: relative;
  z-index: 10;
  flex-shrink: 0;
  box-sizing: border-box;
  background: #ffd96f;
}
.page-nav-inner {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 32rpx;
}
.page-nav-inner text {
  color: #333;
  font-size: 34rpx;
  font-weight: 600;
}
.content {
  flex: 1;
  min-height: 0;
  height: 0;
  box-sizing: border-box;
}
.user-header {
  display: flex;
  align-items: center;
  padding: 32rpx;
  background: #ffd96f;
}
.user-main {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}
.avatar {
  width: 120rpx;
  height: 120rpx;
  flex-shrink: 0;
  overflow: hidden;
  border: 4rpx solid rgba(255, 255, 255, 0.5);
  border-radius: 50%;
  background: #fff;
}
.avatar image {
  width: 100%;
  height: 100%;
}
.user-detail {
  flex: 1;
  min-width: 0;
  margin-left: 24rpx;
}
.name-row {
  display: flex;
  align-items: center;
  min-width: 0;
}
.name {
  overflow: hidden;
  color: #8b4513;
  font-size: 36rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.name-arrow {
  width: 22rpx;
  height: 28rpx;
  margin-left: 8rpx;
  flex-shrink: 0;
}
.switch-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
  padding: 16rpx 28rpx;
  border-radius: 40rpx;
  background: #fff;
  color: #ff6b35;
  font-size: 26rpx;
}
.switch-btn image {
  width: 28rpx;
  height: 28rpx;
}
.star-card {
  margin: 0 24rpx 24rpx;
  padding: 32rpx;
  border-radius: 32rpx;
  background: linear-gradient(135deg, #c8f0dc 0%, #a8e6c3 100%);
}
.star-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 24rpx;
  border-bottom: 1rpx solid rgba(90, 160, 120, 0.25);
}
.star-level {
  display: flex;
  align-items: center;
  gap: 16rpx;
  color: #1e5e3a;
  font-size: 34rpx;
  font-weight: 800;
}
.medal {
  width: 44rpx;
  height: 44rpx;
}
.credit-score {
  display: flex;
  align-items: center;
  gap: 8rpx;
  color: #1e5e3a;
  font-size: 28rpx;
}
.credit-score image {
  width: 14rpx;
  height: 22rpx;
}
.credit-value {
  font-size: 40rpx;
  font-weight: 800;
}
.star-stats {
  display: flex;
  margin-top: 28rpx;
}
.star-stat {
  position: relative;
  flex: 1;
  text-align: center;
}
.star-stat + .star-stat {
  border-left: 1rpx solid rgba(90, 160, 120, 0.25);
}
.stat-value,
.stat-label {
  display: block;
}
.stat-value {
  color: #1e5e3a;
  font-size: 40rpx;
  font-weight: 800;
}
.stat-label {
  margin-top: 6rpx;
  color: #4e8563;
  font-size: 24rpx;
}
.star-bottom {
  display: flex;
  align-items: center;
  margin-top: 28rpx;
  padding: 20rpx 28rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.45);
}
.bottom-item {
  display: flex;
  align-items: center;
  flex: 1;
  color: #2f6b4a;
  font-size: 26rpx;
}
.bottom-value {
  margin-left: 16rpx;
  color: #1e5e3a;
  font-size: 30rpx;
  font-weight: 800;
}
.bottom-separator {
  width: 1rpx;
  height: 32rpx;
  background: rgba(90, 160, 120, 0.35);
}
.order-count {
  justify-content: space-between;
  padding-left: 28rpx;
}
.asset-card {
  display: flex;
  margin: 0 24rpx 24rpx;
  padding: 28rpx 0;
  border-radius: 32rpx;
  background: #fff;
}
.asset-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  padding: 4rpx 32rpx;
}
.asset-item + .asset-item {
  border-left: 1rpx solid #f0f0f0;
}
.asset-item > image {
  width: 14rpx;
  height: 24rpx;
}
.asset-label,
.asset-value {
  display: block;
}
.asset-label {
  color: #666;
  font-size: 26rpx;
}
.asset-value {
  margin-top: 8rpx;
  color: #222;
  font-size: 48rpx;
  font-weight: 800;
}
.section-title {
  display: block;
  padding: 8rpx 32rpx 20rpx;
  color: #333;
  font-size: 30rpx;
  font-weight: 600;
}
.menu-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32rpx 0;
  margin: 0 24rpx 24rpx;
  padding: 32rpx 16rpx;
  border-radius: 24rpx;
  background: #fff;
}
.grid-item {
  text-align: center;
}
.grid-icon {
  display: block;
  width: 48rpx;
  height: 48rpx;
  margin: 0 auto 12rpx;
}
.grid-label {
  display: block;
  color: #666;
  font-size: 24rpx;
}
.bottom-space {
  height: calc(190rpx + env(safe-area-inset-bottom));
}
@media (max-width: 340px) {
  .switch-btn {
    padding: 14rpx 18rpx;
  }
  .name {
    font-size: 32rpx;
  }
  .star-card {
    padding: 26rpx 20rpx;
  }
  .stat-value {
    font-size: 34rpx;
  }
}
</style>
