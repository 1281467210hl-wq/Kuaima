<template>
  <view class="page">
    <AppNavBar title="选择工种" :show-back="true" />
    <scroll-view scroll-y class="content">
      <text class="page-title">要招的工种</text>
      <text class="page-desc">选对工种，平台推荐熟手</text>
      <view class="search-box">
        <text class="search-icon">⌕</text>
        <input
          v-model="searchText"
          type="text"
          confirm-type="search"
          placeholder="填写工种名称，如‘普工’"
        />
      </view>
      <view class="section-header"
        ><text class="section-icon">●</text
        ><text class="section-title">{{
          searchText ? "搜索结果" : "热门工种"
        }}</text></view
      >
      <view v-if="loading" class="state">工种加载中...</view>
      <view v-else-if="loadError" class="state error" @click="retryLoad"
        >工种加载失败，点击重试</view
      >
      <view v-else-if="!visibleJobs.length" class="state">{{ searchText ? "暂无匹配工种" : "暂无热门工种" }}</view>
      <view v-else class="hot-grid">
        <view
          v-for="job in visibleJobs"
          :key="job.id || job.name"
          class="hot-item"
          @click="selectJob(job)"
          >
          <text class="hot-name">{{ job.name || job.displayName }}</text>
          <text v-if="job.description" class="hot-desc">{{ job.description }}</text>
          <text v-if="searchText && (job.industryName || job.enterpriseTypeName)" class="hot-context">
            {{ [job.industryName, job.enterpriseTypeName].filter(Boolean).join(" · ") }}
          </text>
        </view
        >
      </view>
      <view class="view-all" @click="viewAllJobs"
        >查看全部工种 <text class="view-all-arrow">›</text></view
      >
      <view class="bottom-space" />
    </scroll-view>
    <view class="float-service" @click="openService"
      ><text class="service-icon">◉</text
      ><text class="service-label">客服</text></view
    >
  </view>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import AppNavBar from "@/components/AppNavBar.vue";
import {
  listHotJobCategories,
  searchJobCategories,
} from "@/api/backend";

const searchText = ref("");
const jobs = ref([]);
const loading = ref(false);
const loadError = ref(false);
const visibleJobs = computed(() => jobs.value);
let searchTimer = null;
onMounted(() => {
  // 从“选择工种”进入代表开始新建岗位，不能沿用上次编辑的草稿缓存。
  [
    "taskContent",
    "genderAgeSelection",
    "workLocationSelection",
    "workTimeSelection",
    "recruitSettings",
    "jobCategorySelection",
  ].forEach((key) => uni.removeStorageSync(key));
  loadHotJobs();
});
onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer);
});
watch(searchText, () => {
  if (searchTimer) clearTimeout(searchTimer);
  const keyword = searchText.value.trim();
  if (!keyword) {
    loadHotJobs();
    return;
  }
  searchTimer = setTimeout(() => searchJobs(keyword), 300);
});
function extractRows(result) {
  if (Array.isArray(result)) return result;
  if (Array.isArray(result?.data)) return result.data;
  if (Array.isArray(result?.records)) return result.records;
  if (Array.isArray(result?.content)) return result.content;
  if (Array.isArray(result?.list)) return result.list;
  if (Array.isArray(result?.data?.records)) return result.data.records;
  if (Array.isArray(result?.data?.content)) return result.data.content;
  return [];
}
async function loadHotJobs() {
  loading.value = true;
  loadError.value = false;
  try {
    jobs.value = extractRows(await listHotJobCategories()).map((item) => ({
      ...item,
      name: item.name || item.displayName || "",
    })).filter((item) => item.name);
  } catch (error) {
    jobs.value = [];
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}
async function searchJobs(keyword) {
  loading.value = true;
  loadError.value = false;
  try {
    jobs.value = extractRows(await searchJobCategories(keyword, 20)).filter(
      (item) => item && (item.name || item.displayName),
    );
  } catch (_) {
    jobs.value = [];
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}
function retryLoad() {
  const keyword = searchText.value.trim();
  if (keyword) searchJobs(keyword);
  else loadHotJobs();
}
function selectJob(job) {
  const params = [
    `job=${encodeURIComponent(job.name || job.displayName)}`,
    `jobId=${encodeURIComponent(job.jobCategoryId || job.id)}`,
  ];
  if (job.industryId) params.push(`industryId=${encodeURIComponent(job.industryId)}`);
  if (job.enterpriseTypeId) {
    params.push(`enterpriseTypeIds=${encodeURIComponent(job.enterpriseTypeId)}`);
  }
  uni.navigateTo({
    url: `/pages/boss-publish/publish-info?${params.join("&")}`,
  });
}
function viewAllJobs() {
  uni.navigateTo({ url: "/pages/boss/all-jobs" });
}
function openService() {
  uni.navigateTo({ url: "/pages/boss/service-chat" });
}
</script>

<style scoped>
.page {
  position: relative;
  min-height: 100vh;
  background: #fff;
}
.content {
  height: calc(100vh - 176rpx);
  padding: 32rpx;
  box-sizing: border-box;
}
.page-title {
  display: block;
  color: #333;
  font-size: 36rpx;
  font-weight: 700;
}
.page-desc {
  display: block;
  margin-top: 8rpx;
  color: #999;
  font-size: 26rpx;
}
.search-box {
  display: flex;
  align-items: center;
  margin: 32rpx 0 48rpx;
  padding: 0 36rpx;
  height: 88rpx;
  border-radius: 44rpx;
  background: #f5f5f5;
}
.search-icon {
  margin-right: 20rpx;
  color: #999;
  font-size: 32rpx;
}
.search-box input {
  flex: 1;
  height: 88rpx;
  color: #333;
  font-size: 28rpx;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 28rpx;
}
.section-icon {
  color: #ff6b35;
  font-size: 24rpx;
}
.section-title {
  color: #333;
  font-size: 32rpx;
  font-weight: 600;
}
.hot-grid {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  justify-content: space-between;
}
.hot-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  flex: 0 0 48.5%;
  width: 48.5%;
  min-width: 0;
  margin-bottom: 20rpx;
  padding: 32rpx 12rpx;
  border-radius: 20rpx;
  background: #f8f8f8;
  color: #333;
  text-align: center;
  font-size: 28rpx;
}
.hot-name {
  color: inherit;
  font-size: 28rpx;
  font-weight: 500;
}
.hot-desc,
.hot-context {
  display: block;
  width: 100%;
  margin-top: 8rpx;
  overflow: hidden;
  color: #999;
  font-size: 22rpx;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-item:active {
  background: #fff3ed;
  color: #ff6b35;
}
.state {
  padding: 100rpx 0;
  color: #999;
  text-align: center;
  font-size: 26rpx;
}
.state.error {
  color: #ff6b35;
}
.view-all {
  margin-top: 48rpx;
  color: #ff6b35;
  text-align: center;
  font-size: 28rpx;
}
.view-all-arrow {
  margin-left: 6rpx;
  font-size: 24rpx;
}
.bottom-space {
  height: 160rpx;
}
.float-service {
  position: fixed;
  right: 32rpx;
  bottom: calc(40rpx + env(safe-area-inset-bottom));
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.12);
}
.service-icon {
  color: #ff6b35;
  font-size: 36rpx;
}
.service-label {
  margin-top: 4rpx;
  color: #ff6b35;
  font-size: 20rpx;
}
</style>
