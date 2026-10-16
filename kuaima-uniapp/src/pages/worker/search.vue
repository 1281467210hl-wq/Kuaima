<template>
  <view class="page"
    ><AppNavBar title="搜索岗位" :show-back="true" /><view class="search"
      ><input
        v-model="keyword"
        confirm-type="search"
        placeholder="输入岗位名称或工作地点"
        @confirm="doSearch"
      /><button @click="doSearch">搜索</button></view
    ><scroll-view scroll-y class="content"
      ><view v-if="searched && !results.length" class="empty">暂无相关岗位</view
      ><JobCard
        v-for="job in results"
        :key="job.id"
        :job="job"
        @select="open"
        @apply="apply" /></scroll-view
  ></view>
</template>
<script setup>
import { ref } from "vue";
import AppNavBar from "@/components/AppNavBar.vue";
import JobCard from "@/components/JobCard.vue";
import { applyPublicJob, listPublicJobs } from "@/api/backend";
const keyword = ref("");
const searched = ref(false);
const results = ref([]);
const mock = [
  {
    id: "demo-search-1",
    title: "餐饮服务员",
    unitPrice: 180,
    salaryType: "DAY",
    startTime: "09:00",
    endTime: "18:00",
    address: "松江区方松街道",
    headcount: 2,
    hiredCount: 1,
    insurance: true,
    duration: 8,
  },
];
async function doSearch() {
  if (!keyword.value.trim())
    return uni.showToast({ title: "请输入搜索内容", icon: "none" });
  searched.value = true;
  try {
    const r = await listPublicJobs({ keyword: keyword.value.trim(), page: 0, size: 20 });
    results.value = (Array.isArray(r) ? r : r?.records || []).map((item) => ({
      ...item,
      title: item.orderTitle || item.title || item.postion,
      unitPrice: item.unitPrice ?? item.wage ?? item.salary,
      salaryType:
        item.type === "month"
          ? "MONTHLY"
          : item.type === "heldBack"
            ? "PRESS"
            : "DAY",
      address: item.address || item.workAddress || "",
    }));
  } catch (_) {
    results.value = [];
  }
}
function open(job) {
  uni.navigateTo({ url: `/pages/worker/job-detail?id=${job.id}` });
}
async function apply(job) {
  try {
    await applyPublicJob(job.id, { trial: job.salaryType === "MONTHLY" });
    job.applied = true;
    uni.showToast({ title: "报名成功", icon: "success" });
  } catch (e) {
    uni.showToast({ title: e.message || "报名失败", icon: "none" });
  }
}
</script>
<style scoped>
.page {
  min-height: 100vh;
  background: #f5f5f5;
}
.search {
  display: flex;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  background: #fff;
}
.search input {
  flex: 1;
  background: #f5f5f5;
  border-radius: 36rpx;
  padding: 0 26rpx;
  height: 72rpx;
  font-size: 25rpx;
}
.search button {
  width: 130rpx;
  height: 72rpx;
  line-height: 72rpx;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 36rpx;
  background: #ff6b35;
  color: #fff;
  font-size: 25rpx;
}
.content {
  height: calc(100vh - 280rpx);
  padding: 22rpx;
  box-sizing: border-box;
}
.empty {
  text-align: center;
  padding: 200rpx 0;
  color: #aaa;
  font-size: 25rpx;
}
</style>
