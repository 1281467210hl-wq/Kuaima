<template>
  <view class="page">
    <view
      class="nav-bar"
      :style="{ paddingTop: `${statusBarHeight}px`, height: `${statusBarHeight + 50}px` }"
    >
      <view class="nav-action" @click="goBack">
        <image src="/static/icons/boss-expense-detail/arrow-left-dark.svg" mode="aspectFit" />
      </view>
      <text class="nav-title">支出明细</text>
      <view class="nav-action placeholder" />
    </view>

    <scroll-view
      scroll-y
      class="scroll-area"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="refresh"
    >
      <view class="summary-card">
        <view class="summary-total" @click="selectFilter('all')">
          <view>
            <text class="summary-total-label">总支出（近30天）</text>
            <text class="summary-total-value">{{ money(summary.total) }}</text>
          </view>
        </view>
        <view class="summary-items">
          <view
            v-for="item in summaryItems"
            :key="item.key"
            class="summary-item"
            :class="{ active: activeFilter === item.key }"
            @click="selectFilter(item.key)"
          >
            <text class="summary-value">{{ money(summary[item.key]) }}</text>
            <text class="summary-label">{{ item.label }}</text>
          </view>
        </view>
      </view>

      <scroll-view scroll-x class="filter-scroll" :show-scrollbar="false">
        <view class="filter-tabs">
          <view
            v-for="item in filters"
            :key="item.key"
            class="filter-tab"
            :class="{ active: activeFilter === item.key }"
            @click="selectFilter(item.key)"
          >
            {{ item.label }}
          </view>
        </view>
      </scroll-view>

      <view v-if="loading" class="loading-text">支出明细加载中...</view>
      <view v-else-if="!filteredRecords.length" class="empty-state">
        <image
          class="empty-icon"
          src="/static/icons/boss-expense-detail/money-bill-transfer-gray.svg"
          mode="aspectFit"
        />
        <text class="empty-text">暂无支出记录</text>
      </view>
      <view v-else class="record-list">
        <view v-for="item in filteredRecords" :key="item.id" class="record-card">
          <view class="record-head">
            <text class="record-title">{{ item.title }}</text>
            <text class="record-status" :class="`status-${item.status}`">{{ item.statusText }}</text>
          </view>
          <text class="record-desc">{{ item.description }}</text>
          <view class="record-tags">
            <text v-for="tag in item.tags" :key="tag" class="record-tag">{{ tag }}</text>
          </view>
          <text class="record-amount">¥ {{ money(item.amount) }}</text>
          <text class="record-time">{{ item.time }}</text>
        </view>
      </view>
      <view class="safe-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getBossExpenseDetails } from "@/api/backend";

export default {
  data() {
    return {
      statusBarHeight: 44,
      loading: false,
      refreshing: false,
      activeFilter: "all",
      summary: { total: 0, points: 0, settle: 0, recharge: 0 },
      records: [],
      filters: [
        { key: "all", label: "全部" },
        { key: "points", label: "购买积分" },
        { key: "settle", label: "招工订单结算" },
        { key: "recharge", label: "充值" },
      ],
    };
  },
  computed: {
    summaryItems() {
      return [
        { key: "points", label: "购买积分" },
        { key: "settle", label: "招工订单结算" },
        { key: "recharge", label: "充值" },
      ];
    },
    filteredRecords() {
      if (this.activeFilter === "all") return this.records;
      return this.records.filter((item) => item.type === this.activeFilter);
    },
  },
  onLoad() {
    try {
      const info = typeof uni.getWindowInfo === "function"
        ? uni.getWindowInfo()
        : uni.getSystemInfoSync();
      this.statusBarHeight = Number(info.statusBarHeight) || 44;
    } catch (_) {
      this.statusBarHeight = 44;
    }
  },
  onShow() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = !this.refreshing;
      try {
        const result = await getBossExpenseDetails();
        this.summary = {
          total: Number(result?.total || 0),
          points: Number(result?.points || 0),
          settle: Number(result?.settle || 0),
          recharge: Number(result?.recharge || 0),
        };
        this.records = Array.isArray(result?.records) ? result.records : [];
      } catch (error) {
        uni.showToast({ title: error?.message || "支出明细加载失败", icon: "none" });
      } finally {
        this.loading = false;
        this.refreshing = false;
      }
    },
    refresh() {
      this.refreshing = true;
      this.load();
    },
    selectFilter(type) {
      this.activeFilter = type;
    },
    money(value) {
      return Number(value || 0).toLocaleString("zh-CN", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });
    },
    goBack() {
      uni.navigateBack();
    },
  },
};
</script>

<style lang="scss" scoped>
.page { display: flex; flex-direction: column; height: 100vh; overflow: hidden; background: #f5f5f5; }
.nav-bar { position: relative; display: flex; align-items: center; justify-content: space-between; flex-shrink: 0; padding: 0 32rpx; box-sizing: border-box; background: #fff; }
.nav-action { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 100rpx; }
.nav-action image { width: 34rpx; height: 34rpx; }
.nav-action.placeholder { visibility: hidden; }
.nav-title { position: absolute; left: 50%; color: #333; font-size: 34rpx; font-weight: 600; transform: translateX(-50%); }
.scroll-area { flex: 1; min-height: 0; }
.summary-card { margin: 24rpx 32rpx 16rpx; padding: 32rpx; border-radius: 24rpx; color: #fff; background: linear-gradient(135deg, #1890ff, #40a9ff); }
.summary-total { display: flex; align-items: center; justify-content: space-between; padding-bottom: 20rpx; margin-bottom: 20rpx; border-bottom: 1rpx solid rgba(255,255,255,.25); }
.summary-total-label { display: block; font-size: 24rpx; opacity: .9; }
.summary-total-value { display: block; margin-top: 8rpx; font-size: 48rpx; font-weight: 700; font-variant-numeric: tabular-nums; }
.summary-items { display: flex; gap: 8rpx; }
.summary-item { flex: 1; padding: 12rpx 0; border-radius: 16rpx; text-align: center; }
.summary-item.active { background: rgba(255,255,255,.2); }
.summary-value, .summary-label { display: block; }
.summary-value { font-size: 34rpx; font-weight: 700; font-variant-numeric: tabular-nums; }
.summary-label { margin-top: 8rpx; font-size: 24rpx; opacity: .9; }
.filter-scroll { width: 100%; white-space: nowrap; }
.filter-tabs { display: inline-flex; gap: 16rpx; padding: 8rpx 32rpx 24rpx; }
.filter-tab { padding: 10rpx 28rpx; border: 2rpx solid #eee; border-radius: 28rpx; color: #666; font-size: 24rpx; background: #fff; }
.filter-tab.active { border-color: #ff6b35; color: #ff6b35; background: #fff3ed; }
.record-card { margin: 16rpx 32rpx; padding: 28rpx; border-radius: 24rpx; background: #fff; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.04); }
.record-head { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; margin-bottom: 12rpx; }
.record-title { flex: 1; min-width: 0; color: #333; font-size: 28rpx; font-weight: 600; }
.record-status { flex-shrink: 0; padding: 4rpx 16rpx; border-radius: 8rpx; font-size: 22rpx; white-space: nowrap; }
.status-done { color: #52c41a; background: #f6ffed; }
.status-processing { color: #fa8c16; background: #fff8e6; }
.record-desc { display: block; margin-bottom: 12rpx; color: #999; font-size: 24rpx; line-height: 1.5; }
.record-tags { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 12rpx; }
.record-tag { padding: 2rpx 14rpx; border-radius: 6rpx; color: #666; font-size: 20rpx; background: #f5f5f5; }
.record-amount, .record-time { display: block; text-align: right; }
.record-amount { margin-top: 14rpx; color: #ff6b35; font-size: 32rpx; font-weight: 700; }
.record-time { margin-top: 8rpx; color: #bbb; font-size: 22rpx; }
.loading-text { padding: 100rpx 0; color: #999; font-size: 26rpx; text-align: center; }
.empty-state { display: flex; flex-direction: column; align-items: center; padding: 160rpx 80rpx; text-align: center; }
.empty-icon { width: 96rpx; height: 96rpx; margin-bottom: 24rpx; }
.empty-text { color: #999; font-size: 28rpx; }
.safe-space { height: calc(24rpx + env(safe-area-inset-bottom)); }
</style>
