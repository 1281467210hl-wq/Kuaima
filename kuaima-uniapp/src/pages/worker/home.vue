<template>
  <view class="page">
    <scroll-view scroll-y class="scroll">
      <view class="header" :style="{ paddingTop: `${headerPaddingTop}px` }">
        <view class="brand">
          <view class="brand-copy">
            <text class="brand-title">快马日结</text>
            <text class="slogan">真老板 | 真工价 | 真日结</text>
          </view>
          <view class="header-actions">
            <view class="header-action" @click="openHeaderPage('rule')">
              <image
                class="header-action-icon"
                src="/static/icons/worker-home/scale-balanced-brown.svg"
                mode="aspectFit"
              />
              <text>规则</text>
            </view>
            <view class="header-action" @click="openHeaderPage('classroom')">
              <image
                class="header-action-icon"
                src="/static/icons/worker-home/graduation-cap-brown.svg"
                mode="aspectFit"
              />
              <text>课堂</text>
            </view>
          </view>
        </view>
      </view>

      <view class="home-notice">
        <image
          class="notice-icon"
          src="/static/icons/worker-home/bullhorn-green.svg"
          mode="aspectFit"
        />
        <view class="notice-viewport">
          <view class="notice-track">
            <view v-for="(notice, index) in notices" :key="index" class="notice-item">
              <text>{{ notice.prefix }}</text>
              <text class="notice-emphasis">{{ notice.emphasis }}</text>
              <text>{{ notice.suffix }}</text>
            </view>
            <view class="notice-item">
              <text>{{ notices[0].prefix }}</text>
              <text class="notice-emphasis">{{ notices[0].emphasis }}</text>
              <text>{{ notices[0].suffix }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="tabs">
        <text
          v-for="tab in tabs"
          :key="tab.key"
          class="tab"
          :class="{ active: activeTab === tab.key }"
          @click="switchTab(tab.key)"
        >
          {{ tab.label }}
        </text>
      </view>

      <view class="tools">
        <view class="location" @click="chooseLocation">
          <image
            class="location-icon"
            src="/static/icons/worker-home/location-dot-orange.svg"
            mode="aspectFit"
          />
          <text class="location-text">{{ location }}</text>
          <image
            class="location-arrow"
            src="/static/icons/worker-home/chevron-down-gray.svg"
            mode="aspectFit"
          />
        </view>
        <view class="tool-btn" @click="showFilter">
          <text>筛选</text>
          <image
            class="tool-icon"
            src="/static/icons/worker-home/chevron-down-dark-gray.svg"
            mode="aspectFit"
          />
        </view>
        <view class="tool-btn" @click="search">
          <image
            class="tool-icon"
            src="/static/icons/worker-home/magnifying-glass-dark-gray.svg"
            mode="aspectFit"
          />
          <text>搜索</text>
        </view>
      </view>

      <view v-if="loading" class="state">正在加载岗位…</view>
      <view v-else-if="error" class="state error" @click="loadJobs">
        岗位加载失败，点击重试
      </view>
      <view v-else-if="filteredJobs.length === 0" class="state">
        暂无符合条件的岗位
      </view>
      <view v-else class="jobs">
        <JobCard
          v-for="job in filteredJobs"
          :key="job.id"
          :job="job"
          @select="openDetail"
          @apply="applyJob"
        />
      </view>
    </scroll-view>

    <view
      v-if="filterVisible"
      class="filter-mask"
      @click="filterVisible = false"
    >
      <view class="filter-panel" @click.stop>
        <view class="filter-head"
          ><text>筛选</text
          ><image
            class="filter-close"
            src="/static/icons/worker-home/xmark-gray.svg"
            mode="aspectFit"
            @click="filterVisible = false"
          /></view
        >
        <scroll-view scroll-y class="filter-content"
          ><view
            v-for="group in filterGroups"
            :key="group.key"
            class="filter-section"
            ><text class="filter-title">{{ group.title }}</text
            ><view class="filter-options"
              ><text
                v-for="option in group.options"
                :key="option"
                class="filter-option"
                :class="{ active: filterForm[group.key] === option }"
                @click="filterForm[group.key] = option"
                >{{ option }}</text
              ></view
            ></view
          ></scroll-view
        >
        <view class="filter-actions"
          ><button class="filter-reset" @click="resetFilter">重置</button
          ><button class="filter-confirm" @click="confirmFilter">
            确定
          </button></view
        >
      </view>
    </view>

    <WorkerTabBar current="home" />
  </view>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import JobCard from "@/components/JobCard.vue";
import WorkerTabBar from "@/components/WorkerTabBar.vue";
import { request, USE_MOCK } from "@/api/http";
import {
  applyPublicJob,
  getWorkerRealnameStatus,
  isRealnameRequiredForApply,
  listPublicJobs,
  reverseGeocode,
} from "@/api/backend";
import { getCachedLocation, requestCurrentLocation } from "@/utils/location";

const tabs = [
  { key: "DAY", label: "每天日结" },
  { key: "PRESS", label: "压薪日结" },
  { key: "MONTHLY", label: "月结" },
];

const activeTab = ref("DAY");
const statusBarHeight = ref(0);
const headerPaddingTop = ref(8);
const location = ref("定位中...");
const selectedLocation = ref(null);
const currentLocation = ref(null);
const notices = [
  { prefix: "魏*洁提现 ", emphasis: "2538.6 元", suffix: "，已到账" },
  { prefix: "张*强今日完工到账 ", emphasis: "320 元", suffix: "" },
  { prefix: "李*明提现 ", emphasis: "860 元", suffix: "，已到账" },
  { prefix: "王*芳日结工资 ", emphasis: "1526.8 元", suffix: "，已到账" },
];
const loading = ref(false);
const error = ref(false);
const jobs = ref([]);
const filterVisible = ref(false);
const dayFilterGroups = [
  { key: "date", title: "日期", options: ["全部", "今天", "明天", "后天"] },
  { key: "type", title: "类型", options: ["全部", "计时", "计件"] },
  { key: "days", title: "天数", options: ["全部", "一天日结", "多天日结"] },
  {
    key: "duration",
    title: "任务时长",
    options: ["全部", "3小时以内", "3~6小时", "6小时以上"],
  },
  {
    key: "sort",
    title: "排序",
    options: ["推荐排序", "近距离优先", "时间优先", "老雇主优先"],
  },
];
const pressFilterGroups = [
  {
    key: "date",
    title: "日期",
    options: ["全部", "今天", "明天", "后天", "本周内"],
  },
  {
    key: "salary",
    title: "薪资范围",
    options: ["不限", "100元以下", "100-200元", "200-300元", "300元以上"],
  },
  {
    key: "distance",
    title: "距离",
    options: ["不限", "3公里内", "5公里内", "10公里内"],
  },
  {
    key: "jobType",
    title: "工种",
    options: [
      "全部",
      "餐饮服务",
      "快递配送",
      "仓储物流",
      "制造业",
      "建筑装修",
      "零售促销",
    ],
  },
  {
    key: "duration",
    title: "工作时长",
    options: ["不限", "3小时内", "3-6小时", "6小时以上"],
  },
  {
    key: "tag",
    title: "特色标签",
    options: ["免服务费", "包吃住", "有空调", "日结", "可长期"],
  },
];
const filterGroups = computed(() =>
  activeTab.value === "PRESS" ? pressFilterGroups : dayFilterGroups,
);
const filterDefaults = {
  date: "全部",
  type: "全部",
  days: "全部",
  duration: "全部",
  sort: "推荐排序",
  salary: "不限",
  distance: "不限",
  jobType: "全部",
  tag: "",
};
const filterForm = reactive({ ...filterDefaults });

const filteredJobs = computed(() =>
  jobs.value.filter(
    (job) =>
      (job.settlementType || job.salaryType || "DAY") === activeTab.value,
  ),
);

const mockJobs = [
  {
    id: "demo-1",
    title: "餐饮清洁工",
    displayTitle: "餐饮清洁工",
    unitPrice: 162,
    wageUnit: "元/单",
    salaryType: "DAY",
    startTime: "08:00",
    endTime: "18:00",
    address: "松江区泗泾镇",
    headcount: 3,
    hiredCount: 5,
    insurance: true,
    duration: 9,
    ageLimit: "22岁以上",
  },
  {
    id: "demo-2",
    title: "餐饮服务员、洗碗员",
    shortTitle: "餐饮服务员、洗...",
    displayTitle: "餐饮服务员",
    unitPrice: 170,
    wageUnit: "元/天",
    salaryType: "PRESS",
    startTime: "10:00",
    endTime: "20:00",
    address: "松江区中山街道",
    headcount: 2,
    hiredCount: 8,
    duration: 10,
    tagText: "压薪日结",
  },
  {
    id: "demo-3",
    title: "氩弧焊和气保焊",
    displayTitle: "氩弧焊焊工",
    unitPrice: 280,
    wageUnit: "元/天",
    salaryType: "DAY",
    startTime: "08:00",
    endTime: "17:00",
    address: "松江区佘山镇",
    headcount: 2,
    hiredCount: 3,
    duration: 8,
    skillText: "有经验",
  },
  {
    id: "demo-monthly-1",
    title: "餐饮服务员",
    displayTitle: "餐饮服务员",
    unitPrice: 4500,
    wageUnit: "元/月",
    salaryType: "MONTHLY",
    interview: "明天 08:00-20:00",
    distance: "徐泾镇 9.4km",
    monthlySalary: 4500,
    trialSalary: 180,
    thumbnail: "🍽️",
    startTime: "08:00",
    endTime: "20:00",
    address: "松江区徐泾镇",
    headcount: 2,
    hiredCount: 1,
    insurance: true,
    duration: 12,
    ageLimit: "30-50岁",
    tagText: "月结试工",
  },
  {
    id: "demo-4",
    title: "快递分拣员",
    displayTitle: "快递分拣员",
    unitPrice: 96,
    wageUnit: "元/单",
    salaryType: "DAY",
    startTime: "13:00",
    endTime: "19:00",
    address: "松江区",
    headcount: 5,
    hiredCount: 12,
    duration: 6,
  },
];

function formatDateValue(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(
    date.getDate(),
  ).padStart(2, "0")}`;
}

function appendDateFilter(params) {
  const value = filterForm.date;
  const today = new Date();
  if (value === "今天" || value === "明天" || value === "后天") {
    const offset = value === "今天" ? 0 : value === "明天" ? 1 : 2;
    const date = new Date(today);
    date.setDate(date.getDate() + offset);
    params.startDate = formatDateValue(date);
    params.endDate = formatDateValue(date);
  } else if (value === "本周内") {
    const endDate = new Date(today);
    endDate.setDate(endDate.getDate() + ((endDate.getDay() || 7) - 1));
    params.startDate = formatDateValue(today);
    params.endDate = formatDateValue(endDate);
  }
}

function appendSalaryFilter(params) {
  const value = filterForm.salary;
  if (value === "100元以下") params.salaryMax = 100;
  if (value === "100-200元") {
    params.salaryMin = 100;
    params.salaryMax = 200;
  }
  if (value === "200-300元") {
    params.salaryMin = 200;
    params.salaryMax = 300;
  }
  if (value === "300元以上") params.salaryMin = 300;
}

function appendDurationFilter(params) {
  const value = String(filterForm.duration || "");
  if (!value || value === "全部" || value === "不限") return;
  if (value.includes("以内") || value.includes("内")) params.durationMax = 3;
  if (value.includes("3") && (value.includes("~") || value.includes("-"))) {
    params.durationMin = 3;
    params.durationMax = 6;
  }
  if (value.includes("以上")) params.durationMin = 6;
}

function appendFilterParams(params) {
  appendDateFilter(params);
  appendSalaryFilter(params);
  appendDurationFilter(params);
  if (filterForm.type === "计时" || filterForm.type === "计件") {
    params.tags = filterForm.type;
  }
  if (filterForm.days === "一天日结") params.sameDay = true;
  if (filterForm.days === "多天日结") params.sameDay = false;
  if (filterForm.jobType && filterForm.jobType !== "全部") params.position = filterForm.jobType;
  if (filterForm.tag) {
    params.tags = [params.tags, filterForm.tag].filter(Boolean).join(",");
    params.tagMode = "ALL";
  }
  params.sort = {
    推荐排序: "RECOMMEND",
    近距离优先: "NEARBY",
    时间优先: "TIME",
    老雇主优先: "SENIOR_EMPLOYER",
  }[filterForm.sort] || "RECOMMEND";
}

async function loadJobs() {
  loading.value = true;
  error.value = false;
  try {
    const params = {
      page: 0,
      size: 20,
      type: activeTab.value === "DAY" ? "daily" : activeTab.value === "PRESS" ? "heldBack" : "month",
    };
    appendFilterParams(params);
    const distanceText = String(filterForm.distance || "不限");
    const needsLocation = distanceText !== "不限" || params.sort === "NEARBY";
    if (needsLocation) {
      const distanceKm = distanceText === "不限"
        ? null
        : Number(distanceText.match(/[\d.]+/)?.[0]);
      const located = selectedLocation.value || currentLocation.value || await requestCurrentLocation();
      if (located && (!Number.isFinite(distanceKm) || distanceKm > 0)) {
        params.longitude = located.longitude;
        params.latitude = located.latitude;
        if (Number.isFinite(distanceKm) && distanceKm > 0) params.distanceKm = distanceKm;
      } else if (!located) {
        uni.showToast({ title: "请授权定位后才能按距离筛选", icon: "none" });
      }
    }
    const result = await listPublicJobs(params);
    const records = result?.records || result;
    jobs.value = Array.isArray(records)
      ? records.map((item) => {
          const job = normalizeJob(item);
          job.hiredCount = item.applyCount ?? item.currentApplyCount ?? item.hiredCount ?? 0;
          return job;
        })
      : [];
  } catch (e) {
    jobs.value = USE_MOCK ? mockJobs.map(normalizeJob) : [];
    error.value = true;
  } finally {
    loading.value = false;
  }
}

function normalizeJob(item) {
  const typeMap = { daily: "DAY", heldBack: "PRESS", month: "MONTHLY" };
  const hourlyMatch = String(item.tags || "").match(/时薪:([\d.]+)/);
  const pieceMatch = String(item.tags || "").match(/计件单价:([\d.]+)/);
  const pieceUnitMatch = String(item.tags || "").match(/计件单位:([^,]+)/);
  return {
    ...item,
    id: item.id,
    title: item.title || item.orderTitle || item.postion || "岗位",
    displayTitle:
      item.displayTitle || item.postion || item.title || item.orderTitle,
    unitPrice:
      hourlyMatch?.[1] ??
      pieceMatch?.[1] ??
      item.unitPrice ??
      item.wage ??
      item.salary,
    wageUnit: hourlyMatch
      ? "元/小时"
      : pieceMatch
        ? `元/${pieceUnitMatch?.[1] || "件"}`
        : item.wageUnit,
    settlementType:
      item.settlementType || item.salaryType || typeMap[item.type] || "DAY",
    salaryType:
      item.salaryType || item.settlementType || typeMap[item.type] || "DAY",
    headcount:
      item.headcount ?? item.recruitCount ?? item.needCount ?? item.orderNum,
    hiredCount:
      item.hiredCount ?? item.applyCount ?? item.currentApplyCount ?? 0,
    address:
      item.address ||
      item.workAddress ||
      item.locationName ||
      item.location ||
      item.detailAddress ||
      "",
    orderRemark: item.orderRemark || "",
  };
}

function chooseLocation() {
  if (typeof uni.chooseLocation !== "function") {
    uni.showToast({ title: "当前环境不支持选择位置", icon: "none" });
    return;
  }

  const current = selectedLocation.value || getCachedLocation();
  const options = {
    success: ({ name, address, latitude, longitude }) => {
      const label = String(name || address || "").trim();
      selectedLocation.value = {
        name: label,
        address: String(address || "").trim(),
        latitude: Number(latitude),
        longitude: Number(longitude),
        updatedAt: Date.now(),
      };
      location.value = label || "已选择位置";
      uni.setStorageSync("workerSelectedLocation", selectedLocation.value);
      uni.showToast({ title: "位置已切换", icon: "success" });
      loadJobs();
    },
    fail: handleLocationError,
  };
  if (current?.latitude != null && current?.longitude != null) {
    options.latitude = Number(current.latitude);
    options.longitude = Number(current.longitude);
  }
  uni.chooseLocation(options);
}

function handleLocationError(error) {
  const message = String(error?.errMsg || error?.message || "");
  if (message.toLowerCase().includes("cancel")) return;
  if (
    message.toLowerCase().includes("auth deny") ||
    message.toLowerCase().includes("authorize") ||
    message.toLowerCase().includes("permission denied")
  ) {
    uni.showModal({
      title: "需要位置权限",
      content: "请在设置中开启位置权限后重新选择",
      confirmText: "去设置",
      success: ({ confirm }) => {
        if (!confirm) return;
        // App 没有 openSetting，需跳转系统的应用权限设置页
        // #ifdef APP-PLUS
        uni.openAppAuthorizeSetting();
        // #endif
        // #ifndef APP-PLUS
        uni.openSetting({});
        // #endif
      },
    });
    return;
  }
  uni.showToast({
    title: message.replace(/^chooseLocation:fail\s*/i, "") || "选择位置失败",
    icon: "none",
  });
}

function showFilter() {
  filterVisible.value = true;
}
function resetFilter() {
  Object.assign(filterForm, filterDefaults);
}
async function confirmFilter() {
  uni.setStorageSync("workerJobFilter", { ...filterForm });
  filterVisible.value = false;
  await loadJobs();
}

function search() {
  uni.navigateTo({ url: "/pages/worker/search" });
}

function openHeaderPage(page) {
  const routes = {
    rule: "/pages/worker/rule",
    classroom: "/pages/worker/classroom",
  };
  const url = routes[page];
  if (url) uni.navigateTo({ url });
}

function switchTab(key) {
  activeTab.value = key;
  loadJobs();
}

function openDetail(job) {
  uni.navigateTo({
    url: `/pages/worker/job-detail?id=${encodeURIComponent(job.id)}`,
  });
}

async function applyJob(job) {
  // 后台开启「实名认证要求」时，报名须完成人脸实名，与后端 /realname/status 口径一致
  const realnameRequired = await isRealnameRequiredForApply();
  if (realnameRequired && uni.getStorageSync("workerRealname") !== true) {
    try {
      const result = await getWorkerRealnameStatus();
      if (result?.realnameStatus === "APPROVED") uni.setStorageSync("workerRealname", true);
    } catch (_) {}
  }
  if (realnameRequired && uni.getStorageSync("workerRealname") !== true) {
    uni.showToast({ title: "请先完成实名认证", icon: "none" });
    return uni.navigateTo({ url: "/pages/worker/realname" });
  }

  try {
    await applyPublicJob(job.id, { trial: job.salaryType === "MONTHLY" });
    job.applied = true;
    job.appliedStatus = "已报名";
    job.hiredCount = Number(job.hiredCount || 0) + 1;
    uni.showToast({ title: "报名成功", icon: "success" });
  } catch (e) {
    uni.showToast({ title: e.message || "报名失败", icon: "none" });
    if (/实名/.test(e?.message || "")) {
      // 本地缓存的实名标记已过期（如旧流程认证），以后端结果为准
      uni.removeStorageSync("workerRealname");
      uni.navigateTo({ url: "/pages/worker/realname" });
    }
  }
}

onMounted(async () => {
  try {
    const info =
      typeof uni.getWindowInfo === "function"
        ? uni.getWindowInfo()
        : uni.getSystemInfoSync();
    statusBarHeight.value = Number(info.statusBarHeight || 0);
    headerPaddingTop.value = statusBarHeight.value + 8;
    if (typeof uni.getMenuButtonBoundingClientRect === "function") {
      const menuButton = uni.getMenuButtonBoundingClientRect();
      if (menuButton?.bottom) {
        headerPaddingTop.value = Number(menuButton.bottom) + 8;
      }
    }
  } catch (_) {}
  const savedLocation = uni.getStorageSync("workerSelectedLocation");
  if (
    savedLocation &&
    Number.isFinite(Number(savedLocation.latitude)) &&
    Number.isFinite(Number(savedLocation.longitude))
  ) {
    selectedLocation.value = savedLocation;
    location.value = savedLocation.name || savedLocation.address || "已选择位置";
  } else {
    await initializeCurrentLocation();
  }
  const savedFilter = uni.getStorageSync("workerJobFilter");
  if (savedFilter) Object.assign(filterForm, filterDefaults, savedFilter);
  await loadJobs();
});

async function initializeCurrentLocation() {
  const result = await requestCurrentLocation();
  if (!result) {
    location.value = "定位失败";
    return;
  }

  currentLocation.value = result;
  try {
    location.value = await reverseGeocode(result.latitude, result.longitude);
  } catch (_) {
    location.value = formatCoordinateLocation(result);
  }
}

function formatCoordinateLocation(location) {
  return `${Number(location.latitude).toFixed(6)}, ${Number(location.longitude).toFixed(6)}`;
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f5f5;
}

.scroll {
  height: calc(100vh - 166rpx);
}

.header {
  position: relative;
  min-height: 156rpx;
  box-sizing: border-box;
  padding: 8px 32rpx 40rpx;
  background: linear-gradient(180deg, #ffd59e 0%, #ffe4b5 50%, #fffbf5 100%);
}

.brand {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.brand-title {
  display: block;
  color: #2f2b28;
  font-size: 40rpx;
  line-height: 1.2;
  font-weight: 800;
}

.slogan {
  display: block;
  margin-top: 4rpx;
  color: #8b4513;
  font-size: 22rpx;
  font-weight: 500;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 36rpx;
  padding-top: 2rpx;
}

.header-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rpx;
  color: #8b4513;
  font-size: 20rpx;
  font-weight: 500;
}

.header-action-icon {
  width: 34rpx;
  height: 34rpx;
}

.home-notice {
  height: 36rpx;
  margin: 16rpx 32rpx 0;
  padding: 18rpx 28rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  overflow: hidden;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.04);
}

.notice-icon {
  width: 28rpx;
  height: 28rpx;
  flex-shrink: 0;
}

.notice-viewport {
  position: relative;
  flex: 1;
  height: 36rpx;
  overflow: hidden;
}

.notice-track {
  animation: notice-roll 12s cubic-bezier(0.4, 0, 0.2, 1) infinite;
}

.notice-item {
  height: 36rpx;
  overflow: hidden;
  color: #333;
  font-size: 26rpx;
  line-height: 36rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-emphasis {
  color: #57bc7a;
  font-weight: 700;
}

@keyframes notice-roll {
  0%,
  18% {
    transform: translateY(0);
  }
  25%,
  43% {
    transform: translateY(-36rpx);
  }
  50%,
  68% {
    transform: translateY(-72rpx);
  }
  75%,
  93% {
    transform: translateY(-108rpx);
  }
  100% {
    transform: translateY(-144rpx);
  }
}

.tabs {
  display: flex;
  background: #fff;
  padding: 0 32rpx;
  margin-top: 16rpx;
  border-bottom: 1rpx solid #f1f1f1;
}

.tab {
  position: relative;
  flex: 1;
  text-align: center;
  padding: 28rpx 0 24rpx;
  color: #999;
  font-size: 28rpx;
  line-height: 1.2;
}

.tab.active {
  color: #333;
  font-weight: 700;
}

.tab.active::after {
  content: "";
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 48rpx;
  height: 6rpx;
  border-radius: 6rpx;
  background: #ff6b35;
  transform: translateX(-50%);
}

.tools {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 32rpx;
}

.location {
  display: flex;
  align-items: center;
  gap: 6rpx;
  max-width: 50%;
  min-width: 0;
  color: #444;
  font-size: 26rpx;
}

.location-icon {
  width: 20rpx;
  height: 24rpx;
  flex-shrink: 0;
}

.location-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.location-arrow {
  width: 18rpx;
  height: 18rpx;
  flex-shrink: 0;
}

.tool-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  background: #fff;
  border-radius: 28rpx;
  padding: 12rpx 24rpx;
  color: #666;
  font-size: 24rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.04);
}

.tool-icon {
  width: 20rpx;
  height: 20rpx;
  flex-shrink: 0;
}

.jobs {
  padding: 0 32rpx 32rpx;
}

.state {
  text-align: center;
  padding: 160rpx 32rpx;
  color: #999;
  font-size: 26rpx;
}

.state.error {
  color: #e34d59;
}
.filter-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0, 0, 0, 0.45);
}
.filter-panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 88vh;
  overflow: hidden;
  border-radius: 40rpx 40rpx 0 0;
  background: #f5f5f5;
}
.filter-head {
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  background: #fff;
  border-bottom: 1rpx solid #eee;
  color: #222;
  font-size: 36rpx;
  font-weight: 700;
}
.filter-close {
  width: 32rpx;
  height: 40rpx;
}
.filter-content {
  height: calc(88vh - 220rpx);
}
.filter-section {
  padding: 30rpx 32rpx 34rpx;
  margin-bottom: 12rpx;
  background: #fff;
}
.filter-title {
  display: block;
  margin-bottom: 24rpx;
  color: #333;
  font-size: 32rpx;
  font-weight: 700;
}
.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 18rpx 16rpx;
}
.filter-option {
  min-width: 120rpx;
  padding: 16rpx 22rpx;
  border: 2rpx solid transparent;
  border-radius: 38rpx;
  background: #f5f5f5;
  color: #666;
  font-size: 27rpx;
  text-align: center;
  box-sizing: border-box;
}
.filter-option.active {
  border-color: #ffc84d;
  background: linear-gradient(135deg, #ffedbb, #ffda72);
  color: #d47635;
  font-weight: 600;
}
.filter-actions {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 20rpx;
  padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid #eee;
  background: #fff;
}
.filter-reset,
.filter-confirm {
  height: 84rpx;
  line-height: 84rpx;
  border-radius: 44rpx;
  font-size: 28rpx;
}
.filter-reset {
  width: 34%;
  border: 2rpx solid #ddd;
  background: #fff;
  color: #666;
}
.filter-confirm {
  flex: 1;
  border: 0;
  background: linear-gradient(135deg, #ff6b35, #ff9b42);
  color: #fff;
  font-weight: 700;
}
.filter-reset::after,
.filter-confirm::after {
  border: 0;
}
</style>
