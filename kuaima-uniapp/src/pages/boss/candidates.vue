<template>
  <view class="container">
    <!-- 导航栏（原型 talent-list.html：标题「候选人」） -->
    <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight}px` }">
      <view class="nav-back" @click="goBack">
        <image class="nav-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" />
      </view>
      <text class="nav-title">候选人</text>
      <view class="nav-right" @click="openAddSheet">
        <image class="nav-plus-svg" src="/static/icons/boss-location/plus-dark.svg" mode="aspectFit" />
      </view>
    </view>

    <!-- 搜索框 -->
    <view class="search-bar">
      <view class="search-input">
        <image class="search-svg" src="/static/icons/boss-points/search-gray.svg" mode="aspectFit" />
        <input
          v-model="searchText"
          type="text"
          placeholder="搜索零工姓名/工种"
          confirm-type="search"
          @confirm="reload"
          @input="onSearchInput"
        />
      </view>
    </view>

    <!-- 状态 Tab：全部 / 待筛选 / 待面试 / 面试中 / 已录用 / 已拒绝 -->
    <scroll-view scroll-x class="filter-tabs" :show-scrollbar="false">
      <view class="filter-tabs-inner">
        <text
          v-for="tab in tabs"
          :key="tab.key"
          class="filter-tab"
          :class="{ active: currentTab === tab.key }"
          @click="switchTab(tab.key)"
        >{{ tab.label }}</text>
      </view>
    </scroll-view>

    <scroll-view
      scroll-y
      class="scroll-area"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="refresh"
      @scrolltolower="loadMore"
    >
      <view v-if="loading && !list.length" class="page-state">候选人加载中...</view>
      <view v-else-if="!list.length" class="empty-state">
        <view class="empty-icon">
          <image class="empty-svg" src="/static/icons/boss-talent-list/inbox-gray.svg" mode="aspectFit" />
        </view>
        <text class="empty-text">暂无数据</text>
      </view>

      <view v-for="item in list" :key="item.id" class="worker-card" @click="goDetail(item)">
        <view class="worker-top">
          <view class="worker-avatar" :style="{ background: item.avatarBg }">{{ item.initial }}</view>
          <view class="worker-info">
            <view class="worker-name">
              <text class="worker-name-text">{{ item.name }}</text>
              <text class="job-name">{{ item.position }}</text>
            </view>
            <view class="apply-time">
              <image class="apply-time-svg" src="/static/icons/worker-classroom/clock-gray.svg" mode="aspectFit" />
              <text>应聘时间：{{ item.applyTime || '—' }}</text>
            </view>
          </view>
          <text class="cand-status" :class="item.statusCls">{{ item.statusText }}</text>
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
    </scroll-view>

    <!-- 添加零工到人才库 浮层（原型保留） -->
    <view v-if="showAddSheet" class="add-mask" @click="closeAddSheet" />
    <view class="add-sheet" :class="{ show: showAddSheet }">
      <view class="add-head">
        添加零工到人才库
        <view class="add-close" @click="closeAddSheet">
          <image class="add-close-svg" src="/static/icons/boss-points/xmark-gray.svg" mode="aspectFit" />
        </view>
      </view>
      <view class="add-label">自动加入</view>
      <text class="add-text">订单结算后，未差评或未拉黑的零工都将自动加入人才库。</text>
      <view class="add-label">订单加入</view>
      <text class="add-text">您可在「订单-更多」，手动将零工加入人才库。</text>
      <view class="add-label">面对面邀请加入</view>
      <view class="add-qr">
        <text class="add-qr-t">零工扫码加入人才库</text>
        <view class="add-qr-btn" @click="showQrCode">
          <image class="qr-svg" src="/static/icons/boss-talent-list/qrcode-blue.svg" mode="aspectFit" />
          <text>出示二维码</text>
        </view>
      </view>
      <button class="add-btn" @click="closeAddSheet">确定</button>
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
import { handleTokenInvalid } from "@/api/auth";

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
      statusBarHeight: 0,
      searchText: "",
      searchTimer: null,
      positionId: "",
      tabs: CANDIDATE_TABS,
      currentTab: "all",
      list: [],
      page: 0,
      size: 20,
      total: null,
      hasMore: true,
      loading: false,
      loadingMore: false,
      refreshing: false,
      authRedirecting: false,
      showAddSheet: false,
      // 从面试安排页返回后刷新列表
      pendingReload: false,
    };
  },
  onLoad(options) {
    const info = typeof uni.getWindowInfo === "function" ? uni.getWindowInfo() : uni.getSystemInfoSync();
    this.statusBarHeight = Number(info.statusBarHeight || 0);
    if (options && options.positionId) this.positionId = options.positionId;
    if (options && options.tab) this.currentTab = options.tab;
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
    normalizeItem(item, index) {
      const status = normalizeCandidateStatus(item.status || item.statusText);
      const name = item.name || item.resumeName || "零工";
      return {
        ...item,
        id: item.id,
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
        keyword: this.searchText.trim() || undefined,
        // 按岗位过滤：api/candidate.js listBossApplications 已支持 positionId
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
        const rows = result.records.map((item, index) => this.normalizeItem(item, index));
        this.list = reset ? rows : [...this.list, ...rows];
        this.total = result.total;
        this.page = result.page + 1;
        this.hasMore = result.total !== null ? this.list.length < result.total : rows.length === this.size;
      } catch (error) {
        // 接口异常时按原型渲染空态，避免首屏报错
        if (reset) this.list = [];
        this.hasMore = false;
        if (error && Number(error.code || error.statusCode) === 401) this.handleRequestError(error);
        console.warn("[candidates] 候选人列表接口不可用，渲染空态", error);
      } finally {
        this.loading = false;
        this.loadingMore = false;
        this.refreshing = false;
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
    refresh() {
      this.refreshing = true;
      this.loadList(true);
    },
    loadMore() {
      if (this.hasMore) this.loadList(false);
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
        this.handleRequestError(error, `${action.label}失败`);
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
    openAddSheet() {
      this.showAddSheet = true;
    },
    closeAddSheet() {
      this.showAddSheet = false;
    },
    showQrCode() {
      uni.showToast({ title: "原型演示：向零工出示二维码", icon: "none" });
    },
    goBack() {
      uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/boss/workbench" }) });
    },
    handleRequestError(error, fallback) {
      const status = Number(error?.code || error?.statusCode);
      if (status === 401) {
        if (!this.authRedirecting) {
          this.authRedirecting = true;
          handleTokenInvalid({ role: "boss" }).finally(() => {
            this.authRedirecting = false;
          });
        }
        return;
      }
      uni.showToast({ title: error?.message || fallback, icon: "none" });
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  width: 100%;
  height: 100vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
}

.nav-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 0 16px 8px;
  background: #fff;
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;
}

.nav-back,
.nav-right {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-svg {
  width: 16px;
  height: 16px;
}

.nav-plus-svg {
  width: 18px;
  height: 18px;
}

.nav-title {
  font-size: 17px;
  font-weight: 600;
  color: #333;
}

.search-bar {
  padding: 10px 16px;
  background: #fff;
  width: 100%;
  box-sizing: border-box;
  flex-shrink: 0;
}

.search-input {
  display: flex;
  align-items: center;
  background: #f5f5f5;
  border-radius: 20px;
  padding: 8px 14px;
}

.search-svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
  margin-right: 8px;
}

.search-input input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
}

.filter-tabs {
  background: #fff;
  width: 100%;
  flex-shrink: 0;
  white-space: nowrap;
}

.filter-tabs-inner {
  display: flex;
  gap: 8px;
  padding: 0 16px 12px;
}

.filter-tab {
  flex-shrink: 0;
  padding: 6px 14px;
  font-size: 13px;
  color: #666;
  background: #f5f5f5;
  border-radius: 16px;
}

.filter-tab.active {
  background: #fff3ed;
  color: #ff6b35;
}

.scroll-area {
  flex: 1;
  min-height: 0;
  width: 100%;
  box-sizing: border-box;
}

.page-state,
.list-state {
  padding: 32px 16px;
  color: #999;
  font-size: 14px;
  text-align: center;
}

.empty-state {
  text-align: center;
  padding: 60px 40px;
}

.empty-icon {
  width: 84px;
  height: 84px;
  margin: 0 auto 12px;
  border-radius: 50%;
  background: #f5f0ea;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-svg {
  width: 36px;
  height: 36px;
}

.empty-text {
  font-size: 13px;
  color: #999;
}

.worker-card {
  background: #fff;
  margin: 8px 16px;
  border-radius: 12px;
  padding: 14px;
  box-sizing: border-box;
  width: calc(100% - 32px);
}

.worker-top {
  display: flex;
  gap: 12px;
}

.worker-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
}

.worker-info {
  flex: 1;
  min-width: 0;
}

.worker-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.worker-name-text {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.job-name {
  font-size: 13px;
  font-weight: 400;
  color: #666;
}

.apply-time {
  font-size: 12px;
  color: #999;
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.apply-time-svg {
  width: 12px;
  height: 12px;
  flex-shrink: 0;
}

.cand-status {
  flex-shrink: 0;
  align-self: center;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 4px;
  font-weight: 500;
}

.cand-status.orange {
  background: #fff1e6;
  color: #fa6e28;
}

.cand-status.blue {
  background: #e8f3ff;
  color: #2b7de0;
}

.cand-status.cyan {
  background: #e6f9ff;
  color: #0ea5e9;
}

.cand-status.green {
  background: #e9f9ef;
  color: #34b96f;
}

.cand-status.gray {
  background: #f0f0f0;
  color: #999;
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

.bottom-space {
  height: 20px;
}

/* 添加零工到人才库 浮层 */
.add-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 90;
}

.add-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  border-radius: 16px 16px 0 0;
  padding: 18px 16px 20px;
  z-index: 91;
  transform: translateY(100%);
  transition: transform 0.25s ease;
  box-sizing: border-box;
}

.add-sheet.show {
  transform: translateY(0);
}

.add-head {
  position: relative;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: #333;
  padding-bottom: 14px;
  border-bottom: 1px solid #f0f0f0;
}

.add-close {
  position: absolute;
  right: 0;
  top: -3px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-close-svg {
  width: 16px;
  height: 16px;
}

.add-label {
  display: inline-block;
  font-size: 13px;
  font-weight: 600;
  color: #e88a00;
  padding: 4px 24px 4px 10px;
  margin: 16px 0 8px;
  background: linear-gradient(90deg, #ffedd1 0%, rgba(255, 244, 224, 0.45) 70%, rgba(255, 255, 255, 0) 100%);
  border-radius: 4px;
}

.add-text {
  font-size: 14px;
  color: #333;
  line-height: 1.6;
  display: block;
}

.add-qr {
  background: #f5f6f8;
  border-radius: 12px;
  padding: 24px 16px;
  text-align: center;
  margin-top: 10px;
}

.add-qr-t {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 16px;
  display: block;
}

.add-qr-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 28px;
  border: 1px solid #409eff;
  color: #409eff;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  background: #fff;
}

.qr-svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.add-btn {
  width: 100%;
  margin-top: 22px;
  background: #fdcd01;
  border: none;
  border-radius: 24px;
  padding: 13px;
  font-size: 16px;
  font-weight: 700;
  color: #222;
}
</style>
