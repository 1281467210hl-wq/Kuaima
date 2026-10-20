<template>
  <view class="container">
    <!-- 顶部导航（原型 boss-applicants.html：返回岗位详情） -->
    <view class="wb-header" :style="{ paddingTop: statusBarHeight + 8 + 'px' }">
      <view class="wb-back" @click="goBack"><image class="back-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" /></view>
      <text class="wb-title">岗位申请列表</text>
      <!-- 微信小程序原生胶囊已占右上角，自绘胶囊会与其重叠，仅在非小程序端保留 -->
      <!-- #ifdef H5 -->
      <view class="wb-capsule">
        <view class="cap-btn"><image class="cap-svg" src="/static/icons/boss-profile/ellipsis.svg" mode="aspectFit" /></view>
        <view class="cap-divider"></view>
        <view class="cap-btn"><image class="cap-svg cap-svg-dot" src="/static/icons/boss-profile/dot.svg" mode="aspectFit" /></view>
      </view>
      <!-- #endif -->
    </view>

    <view class="wb-body">
      <!-- 状态 Tab：全部 / 待筛选 / 待面试 / 面试中 / 已录用 / 已拒绝 -->
      <scroll-view scroll-x class="ap-tabs-scroll" :show-scrollbar="false">
        <view class="ap-tabs">
          <text
            v-for="tab in tabs"
            :key="tab.key"
            class="ap-tab"
            :class="{ active: currentTab === tab.key }"
            @click="switchTab(tab.key)"
          >{{ tab.label }}</text>
        </view>
      </scroll-view>

      <!-- 搜索 -->
      <view class="wb-search">
        <image class="search-svg" src="/static/icons/boss-points/search-gray.svg" mode="aspectFit" />
        <input v-model="keyword" type="text" placeholder="搜索候选人姓名、岗位名称..." @confirm="reload" @input="onSearchInput" />
      </view>

      <!-- 岗位过滤提示（原型：?job=岗位名 只看该岗位申请） -->
      <view v-if="jobName" class="job-tip">
        <text>岗位：{{ jobName }}</text>
        <text class="job-tip-clear" @click="clearJob">清空</text>
      </view>

      <!-- 列表 -->
      <view v-if="loading" class="wb-empty"><text class="wb-empty-text">加载中...</text></view>
      <view v-else-if="!list.length" class="wb-empty">
        <view class="wb-empty-icon"><image class="empty-svg" src="/static/icons/boss-workbench/users-empty.svg" mode="aspectFit" /></view>
        <text class="wb-empty-text">暂无相关申请记录</text>
      </view>

      <view v-for="item in list" :key="item.id" class="ap-card" @click="goDetail(item)">
        <view class="ap-top">
          <view class="ap-avatar" :style="{ background: item.avatarBg }">{{ item.initial }}</view>
          <view class="ap-main">
            <view class="ap-name-line">
              <text class="ap-name">{{ item.name }}</text>
              <text class="ap-pos">{{ item.position }}</text>
            </view>
            <view class="ap-time">
              <image class="ap-time-svg" src="/static/icons/worker-classroom/clock-gray.svg" mode="aspectFit" />
              <text>应聘时间：{{ item.applyTime || '—' }}</text>
            </view>
          </view>
          <view class="ap-right"><text class="wb-tag" :class="item.statusCls">{{ item.statusText }}</text></view>
        </view>
        <!-- 卡片按钮随状态变化（原型 statusActions） -->
        <view v-if="item.actions.length" class="ap-actions">
          <text
            v-for="action in item.actions"
            :key="action.act"
            class="ap-btn"
            :class="{ disabled: item.operating }"
            @click.stop="doAction(action, item)"
          >{{ action.label }}</text>
        </view>
      </view>

      <view v-if="loadingMore" class="list-state">加载中...</view>
      <view v-else-if="list.length && !hasMore" class="list-state">没有更多了</view>
      <view class="bottom-space" />
    </view>
  </view>
</template>

<script>
import {
  CANDIDATE_TABS,
  CANDIDATE_STATUS_TEXT,
  CANDIDATE_STATUS_CLS,
  CANDIDATE_ACTION_STATUS,
  getCandidateActions,
  listBossApplications,
  updateBossApplicationStatus,
  normalizeCandidateStatus,
} from "@/api/candidate";
import { avatarColor } from "@/api/project";

function normalizeResponse(payload, requestedPage) {
  const body = payload?.data ?? payload ?? {};
  const records = Array.isArray(body) ? body : body.records || body.content || [];
  const total = Number(body.total ?? payload?.total);
  const page = Number(body.page ?? payload?.page ?? requestedPage);
  return {
    records: Array.isArray(records) ? records : [],
    total: Number.isFinite(total) ? total : null,
    page: Number.isFinite(page) ? page : requestedPage,
  };
}

export default {
  data() {
    return {
      statusBarHeight: 44,
      tabs: CANDIDATE_TABS,
      currentTab: "all",
      keyword: "",
      searchTimer: null,
      positionId: "",
      jobName: "",
      list: [],
      page: 0,
      size: 20,
      total: null,
      hasMore: true,
      loading: false,
      loadingMore: false,
      // 从面试安排页返回后刷新列表
      pendingReload: false,
    };
  },
  onLoad(options) {
    try {
      const info = uni.getSystemInfoSync();
      this.statusBarHeight = info.statusBarHeight || 44;
    } catch (e) {}
    const opts = options || {};
    if (opts.tab) this.currentTab = opts.tab;
    this.positionId = opts.positionId || "";
    this.jobName = opts.job ? decodeURIComponent(opts.job) : "";
    this.reload();
  },
  onShow() {
    if (this.pendingReload) {
      this.pendingReload = false;
      this.loadList(true);
    }
  },
  onUnload() {
    if (this.searchTimer) clearTimeout(this.searchTimer);
  },
  methods: {
    normalizeItem(item) {
      const status = normalizeCandidateStatus(item.status || item.statusText);
      const name = item.name || item.resumeName || "零工";
      return {
        ...item,
        name,
        initial: String(name).slice(0, 1),
        position: item.positionName || item.job || item.position || "",
        applyTime: String(item.applyTime || item.createTime || "").replace("T", " ").substring(0, 16),
        status,
        statusText: CANDIDATE_STATUS_TEXT[status] || item.statusText || "待筛选",
        statusCls: CANDIDATE_STATUS_CLS[status] || "gray",
        actions: getCandidateActions(status),
        avatarBg: item.avatarColor
          ? `linear-gradient(135deg, ${String(item.avatarColor).split(",").map((s) => s.trim()).slice(0, 2).join(", ")})`
          : avatarColor(name),
        operating: false,
      };
    },
    getParams() {
      const tab = CANDIDATE_TABS.find((item) => item.key === this.currentTab);
      return {
        status: tab && tab.status ? tab.status : undefined,
        keyword: this.keyword.trim() || undefined,
        positionId: this.positionId || undefined,
      };
    },
    reload() {
      return this.loadList(true);
    },
    async loadList(reset = false) {
      if ((this.loading || this.loadingMore) && !reset) return;
      const targetPage = reset ? 0 : this.page;
      if (reset) this.loading = true;
      else this.loadingMore = true;
      try {
        const payload = await listBossApplications({ page: targetPage, size: this.size, ...this.getParams() });
        const result = normalizeResponse(payload, targetPage);
        const rows = result.records.map((item) => this.normalizeItem(item));
        this.list = reset ? rows : [...this.list, ...rows];
        this.total = result.total;
        this.page = result.page + 1;
        this.hasMore = result.total !== null ? this.list.length < result.total : rows.length === this.size;
      } catch (error) {
        // 接口异常时按原型渲染空态，避免首屏报错
        if (reset) this.list = [];
        this.hasMore = false;
        console.warn("[applicants] 岗位申请接口不可用，渲染空态", error);
      } finally {
        this.loading = false;
        this.loadingMore = false;
      }
    },
    onSearchInput() {
      if (this.searchTimer) clearTimeout(this.searchTimer);
      this.searchTimer = setTimeout(() => this.loadList(true), 350);
    },
    switchTab(key) {
      if (this.currentTab === key) return;
      this.currentTab = key;
      this.loadList(true);
    },
    clearJob() {
      this.jobName = "";
      this.positionId = "";
      this.loadList(true);
    },
    confirmText(action) {
      const map = {
        reject: "确定拒绝该候选人的申请吗？",
        cancelInterview: "确定取消该面试安排吗？",
        cancelHire: "确定取消录用吗？",
      };
      return map[action] || "";
    },
    async doAction(action, item) {
      if (item.operating) return;
      // 邀请面试/重新邀请面试：跳面试安排页（原型 boss-interview.html），保存走 POST /interview
      if (action.act === "invite" || action.act === "reinvite") {
        this.goInterview(item);
        return;
      }
      const target = CANDIDATE_ACTION_STATUS[action.act];
      if (!target) return;
      const tip = this.confirmText(action.act);
      if (tip) {
        const confirmed = await new Promise((resolve) => {
          uni.showModal({
            title: "提示",
            content: tip,
            success: (res) => resolve(res.confirm),
            fail: () => resolve(false),
          });
        });
        if (!confirmed) return;
      }
      item.operating = true;
      try {
        await updateBossApplicationStatus(item.id, { status: target });
        uni.showToast({ title: `${action.label}成功`, icon: "none" });
        this.loadList(true);
      } catch (error) {
        uni.showToast({ title: error?.message || `${action.label}失败`, icon: "none" });
      } finally {
        item.operating = false;
      }
    },
    goInterview(item) {
      this.pendingReload = true;
      uni.navigateTo({ url: `/pages/boss/interview?applicationId=${item.id}` });
    },
    goDetail(item) {
      // 原型跳「申请详情」页（boss-applicant-detail.html）：uniapp 暂无该页面，
      // 有简历 id 时复用简历详情，否则提示待接入。
      if (item.resumeId) {
        uni.navigateTo({ url: `/pages/boss/resume-detail?id=${item.resumeId}` });
        return;
      }
      uni.showToast({ title: "申请详情页待接入", icon: "none" });
    },
    goBack() {
      uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/boss/position" }) });
    },
    loadMore() {
      if (this.hasMore) this.loadList(false);
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

/* Tab（原型 .ap-tabs：白色胶囊，选中态橙色渐变） */
.ap-tabs-scroll {
  width: 100%;
  white-space: nowrap;
  flex-shrink: 0;
}

.ap-tabs {
  display: flex;
  gap: 8px;
  padding-bottom: 2px;
}

.ap-tab {
  flex-shrink: 0;
  padding: 6px 15px;
  border-radius: 16px;
  background: #fff;
  color: #666;
  font-size: 13px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  white-space: nowrap;
  font-weight: 500;
}

.ap-tab.active {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  font-weight: 600;
}

.wb-search {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border-radius: 12px;
  padding: 11px 14px;
  margin-top: 12px;
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

.job-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  padding: 8px 12px;
  background: #fff3ed;
  border-radius: 10px;
  font-size: 12px;
  color: #ff6b35;
}

.job-tip-clear {
  font-size: 12px;
  color: #999;
}

.ap-card {
  background: #fff;
  border-radius: 14px;
  padding: 13px 14px;
  margin-top: 10px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}

.ap-top {
  display: flex;
  align-items: center;
  gap: 11px;
}

.ap-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.ap-main {
  flex: 1;
  min-width: 0;
}

.ap-name-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ap-name {
  font-size: 15px;
  font-weight: 600;
  color: #222;
}

.ap-pos {
  font-size: 12.5px;
  color: #888;
}

.ap-time {
  font-size: 11px;
  color: #bbb;
  margin-top: 5px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.ap-time-svg {
  width: 11px;
  height: 11px;
  flex-shrink: 0;
}

.ap-right {
  flex-shrink: 0;
}

.ap-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 0.5px solid #f0f0f0;
}

.ap-btn {
  padding: 5px 14px;
  border-radius: 14px;
  font-size: 12px;
  border: 1px solid #2563eb;
  color: #2563eb;
  background: #fff;
  font-weight: 500;
}

.ap-btn.disabled {
  opacity: 0.5;
}

.wb-tag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 5px;
  font-weight: 600;
}

.wb-tag.orange {
  background: #fff1e6;
  color: #fa6e28;
}

.wb-tag.blue {
  background: #e8f3ff;
  color: #2b7de0;
}

.wb-tag.cyan {
  background: #e6f9ff;
  color: #0ea5e9;
}

.wb-tag.green {
  background: #e9f9ef;
  color: #34b96f;
}

.wb-tag.gray {
  background: #f0f0f0;
  color: #999;
}

.wb-empty {
  padding: 50px 0;
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

.list-state {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: #999;
}

.bottom-space {
  height: 20px;
}
</style>
