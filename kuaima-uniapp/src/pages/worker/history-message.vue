<template>
  <view class="page">
    <view class="nav" :style="{ paddingTop: `${statusBarHeight}px` }">
      <view class="nav-inner">
        <button class="back" @click="goBack">
          <image :src="arrowLeftIcon" mode="aspectFit" />
        </button>
        <text class="nav-title">历史消息</text>
        <view class="nav-placeholder" />
      </view>
    </view>

    <view class="filters">
      <view
        v-for="item in types"
        :key="item.key"
        :class="['filter', { active: type === item.key }]"
        @click="type = item.key"
      >
        {{ item.label }}
      </view>
    </view>

    <scroll-view scroll-y class="content">
      <view v-if="loading" class="empty">加载中…</view>
      <view v-else-if="loadError" class="empty error">{{ loadError }}</view>
      <view v-else-if="!groups.length" class="empty">暂无历史消息</view>
      <template v-else>
        <view v-for="group in groups" :key="group.key">
          <view class="group-title">{{ group.label }}</view>
          <view
            v-for="item in group.items"
            :key="item.id"
            class="msg-item"
          >
            <view class="msg-head">
              <view :class="['msg-icon', item.kind]">
                <image :src="item.icon" mode="aspectFit" />
              </view>
              <text class="msg-title">{{ item.title }}</text>
              <text class="msg-time">{{ item.time }}</text>
            </view>
            <text v-if="item.content" class="msg-content">{{ item.content }}</text>
          </view>
        </view>
        <view class="bottom-space" />
      </template>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { listMessages } from "@/api/backend";
import { handleTokenInvalid } from "@/api/auth";
import arrowLeftIcon from "/static/icons/worker-messages/arrow-left-gray.svg";
import bellIcon from "/static/icons/worker-messages/bell-orange.svg";
import moneyIcon from "/static/icons/worker-messages/money-purple.svg";
import checkIcon from "/static/icons/worker-messages/check-green.svg";
import warningIcon from "/static/icons/worker-messages/exclamation-orange.svg";
const statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 0;
const types = [
  { key: "all", label: "全部" },
  { key: "system", label: "系统" },
  { key: "settle", label: "结算" },
  { key: "apply", label: "报名" },
];
const type = ref("all");
const list = ref([]);
const loading = ref(true);
const loadError = ref("");

const groups = computed(() => {
  const map = new Map();
  list.value
    .filter((item) => type.value === "all" || item.kind === type.value)
    .forEach((item) => {
      const key = item.date
        ? `${item.date.getFullYear()}-${item.date.getMonth() + 1}`
        : "unknown";
      if (!map.has(key)) {
        map.set(key, {
          key,
          label: item.date
            ? `${item.date.getFullYear()}年${item.date.getMonth() + 1}月`
            : "其他时间",
          items: [],
        });
      }
      map.get(key).items.push(item);
    });
  return [...map.values()];
});

onMounted(async () => {
  try {
    const result = await listMessages(uni.getStorageSync("userId") || "2001", {
      page: 0,
      size: 100,
      role: "USER",
    });
    const rows = Array.isArray(result) ? result : result?.records || result?.content || [];
    list.value = rows.map(normalizeMessage);
  } catch (error) {
    if (Number(error?.code || error?.statusCode) === 401) {
      return handleTokenInvalid({ role: "worker" });
    }
    loadError.value = error?.message || "历史消息加载失败";
  } finally {
    loading.value = false;
  }
});

function normalizeMessage(item) {
  const source = `${item.type || ""} ${item.bizType || ""} ${item.title || ""}`.toLowerCase();
  const kind =
    source.includes("settle") ||
    source.includes("withdraw") ||
    source.includes("wallet") ||
    source.includes("结算")
      ? "settle"
      : source.includes("apply") ||
          source.includes("报名") ||
          source.includes("approve") ||
          source.includes("通过")
        ? "apply"
        : source.includes("warn") || source.includes("start") || source.includes("即将")
          ? "warn"
          : "system";
  return {
    ...item,
    kind,
    icon:
      kind === "settle"
        ? moneyIcon
        : kind === "apply"
          ? checkIcon
          : kind === "warn"
            ? warningIcon
            : bellIcon,
    date: parseDate(item.createTime),
    time: formatTime(item.createTime),
  };
}

function parseDate(value) {
  const date = value ? new Date(String(value).replace(" ", "T")) : null;
  return date && !Number.isNaN(date.getTime()) ? date : null;
}

function formatTime(value) {
  const date = parseDate(value);
  return date
    ? `${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`
    : "";
}

function goBack() {
  uni.navigateBack();
}

</script>

<style scoped>
.page {
  height: 100vh;
  overflow: hidden;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
}

.nav,
.filters {
  flex-shrink: 0;
}

.nav {
  background: #fff;
  border-bottom: 1rpx solid #f0f0f0;
}

.nav-inner {
  height: 100rpx;
  padding: 0 32rpx;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.back {
  width: 64rpx;
  height: 64rpx;
  padding: 0;
  margin: 0;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back::after {
  border: 0;
}

.back image {
  width: 36rpx;
  height: 36rpx;
}

.nav-title {
  color: #333;
  font-size: 34rpx;
  font-weight: 600;
}

.nav-placeholder {
  width: 64rpx;
  height: 64rpx;
}

.filters {
  display: flex;
  gap: 16rpx;
  padding: 24rpx 32rpx;
  background: #fff;
}

.filter {
  padding: 12rpx 28rpx;
  border-radius: 32rpx;
  background: #f5f5f5;
  color: #666;
  font-size: 26rpx;
  line-height: 1;
}

.filter.active {
  background: #fff3ed;
  color: #ff6b35;
}

.content {
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
  background: #f5f5f5;
}

.group-title {
  padding: 24rpx 32rpx 12rpx;
  color: #999;
  font-size: 24rpx;
}

.msg-item {
  margin: 8rpx 32rpx;
  padding: 28rpx;
  border-radius: 20rpx;
  background: #fff;
}

.msg-head {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.msg-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.msg-icon image {
  width: 24rpx;
  height: 24rpx;
}

.msg-icon.system {
  background: #fff3ed;
}

.msg-icon.settle {
  background: #f9f0ff;
}

.msg-icon.apply {
  background: #f6ffed;
}

.msg-icon.warn {
  background: #fff8e6;
}

.msg-title {
  min-width: 0;
  color: #333;
  font-size: 28rpx;
  font-weight: 600;
}

.msg-time {
  margin-left: auto;
  flex-shrink: 0;
  color: #999;
  font-size: 22rpx;
}

.msg-content {
  display: block;
  padding-left: 72rpx;
  color: #666;
  font-size: 26rpx;
  line-height: 1.5;
}

.empty {
  padding-top: 240rpx;
  text-align: center;
  color: #999;
  font-size: 28rpx;
}

.empty.error {
  color: #e35d5d;
}

.bottom-space {
  height: 40rpx;
}
</style>
