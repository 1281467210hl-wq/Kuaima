<template>
  <view class="container">
    <!-- 导航栏 -->
    <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight}px` }">
      <view class="nav-back" @click="goBack">
        <image class="nav-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" />
      </view>
      <text class="nav-title">人才库</text>
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
          @confirm="search"
          @input="handleSearchInput"
        />
      </view>
    </view>

    <!-- 筛选标签 -->
    <view class="filter-tabs">
      <text
        class="filter-tab"
        :class="{ active: currentTab === tab.value }"
        v-for="tab in tabs"
        :key="tab.value"
        @click="switchFilter(tab)"
      >{{ tab.label }}</text>
    </view>

    <scroll-view
      scroll-y
      class="scroll-area"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="refresh"
      @scrolltolower="loadMore"
    >
      <view v-if="loading && !workers.length" class="page-state">人才加载中...</view>
      <view v-else-if="!workers.length" class="empty-state">
        <view class="empty-icon">
          <image class="empty-svg" src="/static/icons/boss-talent-list/inbox-gray.svg" mode="aspectFit" />
        </view>
        <text class="empty-text">暂无数据</text>
      </view>
      <view class="worker-card" v-for="worker in workers" :key="worker.id">
        <view class="worker-avatar" :style="{ background: worker.avatarBg }">
          {{ worker.initial }}
        </view>
        <view class="worker-info">
          <view class="worker-name">
            {{ worker.name }}
            <text class="worker-tag" :class="{ blue: worker.type === 'new' }">{{ worker.type === 'skilled' ? '熟练工' : '新零工' }}</text>
          </view>
          <text class="worker-meta">{{ worker.meta }}</text>
          <view class="worker-skills">
            <text class="skill-tag" v-for="(skill, i) in worker.skills" :key="i">{{ skill }}</text>
          </view>
        </view>
        <view class="worker-action">
          <button class="btn-sm btn-outline" :disabled="worker.operating" @click="collect(worker)">{{ worker.isFavorite ? '已收藏' : '收藏' }}</button>
          <button class="btn-sm btn-primary" @click="goHire(worker)">雇佣</button>
        </view>
      </view>
      <view v-if="loadingMore" class="list-state">加载中...</view>
      <view v-else-if="workers.length && !hasMore" class="list-state">没有更多了</view>
    </scroll-view>

    <!-- 添加零工到人才库 浮层 -->
    <view v-if="showAddSheet" class="add-mask" @click="closeAddSheet" />
    <view class="add-sheet" :class="{ show: showAddSheet }">
      <view class="add-head">
        添加零工到人才库
        <view class="add-close" @click="closeAddSheet">
          <image class="add-close-svg" src="/static/icons/boss-points/xmark-gray.svg" mode="aspectFit" />
        </view>
      </view>
      <view class="add-label">自动加入</view>
      <text class="add-text">零工报名您的岗位后，将自动加入人才库。</text>
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
  listTalentPool,
  toggleTalentPoolFavorite,
} from "@/api/backend";
import { handleTokenInvalid } from "@/api/auth";

function normalizeResponse(payload, requestedPage) {
  const body = payload?.data ?? payload ?? {};
  const records = Array.isArray(body)
    ? body
    : body.records || body.content || [];
  const totalValue = body.total ?? payload?.total;
  const total = Number(totalValue);
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
      currentTab: "all",
      tabs: [
        { label: "全部", value: "all" },
        { label: "熟练工", value: "skilled" },
        { label: "新零工", value: "new" },
        { label: "我收藏的", value: "favorite" },
      ],
      workers: [],
      page: 0,
      size: 20,
      total: null,
      hasMore: true,
      loading: false,
      loadingMore: false,
      refreshing: false,
      authRedirecting: false,
      showAddSheet: false,
    };
  },
  onLoad() {
    const info = typeof uni.getWindowInfo === "function"
      ? uni.getWindowInfo()
      : uni.getSystemInfoSync();
    this.statusBarHeight = Number(info.statusBarHeight || 0);
    this.loadWorkers(true);
  },
  onUnload() {
    if (this.searchTimer) clearTimeout(this.searchTimer);
  },
  methods: {
    goBack() {
      uni.navigateBack({
        fail: () => uni.reLaunch({ url: "/pages/boss/workbench" }),
      });
    },
    getFilterParams() {
      return {
        keyword: this.searchText.trim() || undefined,
        type: this.currentTab === "all" || this.currentTab === "favorite"
          ? undefined
          : this.currentTab,
        favoriteOnly: this.currentTab === "favorite" ? true : undefined,
      };
    },
    normalizeWorker(item, index) {
      const name = item.name || `零工${item.id || ""}`;
      const skills = Array.isArray(item.skills)
        ? item.skills
        : String(item.skills || "").split(/[,，]/).filter(Boolean);
      const rate = item.goodRate;
      const meta = [
        item.experience,
        item.category,
        !rate || rate === "无" ? "首次接单" : `好评率${rate}`,
      ].filter(Boolean).join(" · ");
      const colors = [
        "linear-gradient(135deg, #FF6B35, #FF8C5A)",
        "linear-gradient(135deg, #52C41A, #73D13D)",
        "linear-gradient(135deg, #1890FF, #40A9FF)",
        "linear-gradient(135deg, #FA8C16, #FFC53D)",
        "linear-gradient(135deg, #EB2F96, #F759AB)",
        "linear-gradient(135deg, #722ED1, #9254DE)",
      ];
      let avatarBg = colors[index % colors.length];
      if (item.avatarColor) {
        const parts = item.avatarColor.split(",").map((s) => s.trim());
        if (parts.length >= 2) {
          avatarBg = `linear-gradient(135deg, ${parts[0]}, ${parts[1]})`;
        }
      }
      return {
        ...item,
        name,
        initial: name.slice(0, 1),
        type: item.type || "new",
        meta: meta || "暂无工作信息",
        skills,
        avatarBg,
        isFavorite: item.favorite === true,
        operating: false,
      };
    },
    async loadWorkers(reset = false) {
      if ((this.loading || this.loadingMore) && !reset) return;
      const targetPage = reset ? 0 : this.page;
      if (reset) this.loading = true;
      else this.loadingMore = true;
      try {
        const payload = await listTalentPool({
          page: targetPage,
          size: this.size,
          ...this.getFilterParams(),
        });
        const result = normalizeResponse(payload, targetPage);
        const rows = result.records.map((item, index) => this.normalizeWorker(item, index));
        this.workers = reset ? rows : [...this.workers, ...rows];
        this.total = result.total;
        this.page = result.page + 1;
        this.hasMore = result.total !== null
          ? this.workers.length < result.total
          : rows.length === this.size;
      } catch (error) {
        if (reset) this.workers = [];
        this.handleRequestError(error, "人才加载失败");
      } finally {
        this.loading = false;
        this.loadingMore = false;
        this.refreshing = false;
      }
    },
    search() {
      this.loadWorkers(true);
    },
    handleSearchInput() {
      if (this.searchTimer) clearTimeout(this.searchTimer);
      this.searchTimer = setTimeout(() => this.loadWorkers(true), 350);
    },
    switchFilter(tab) {
      if (this.currentTab === tab.value) return;
      this.currentTab = tab.value;
      this.loadWorkers(true);
    },
    refresh() {
      this.refreshing = true;
      this.loadWorkers(true);
    },
    loadMore() {
      if (this.hasMore) this.loadWorkers(false);
    },
    async collect(worker) {
      if (!worker?.id || worker.operating) return;
      worker.operating = true;
      const target = !worker.isFavorite;
      try {
        const result = await toggleTalentPoolFavorite(worker.id, target);
        worker.isFavorite = result?.favorite ?? target;
        uni.showToast({ title: worker.isFavorite ? "收藏成功" : "已取消收藏", icon: "success" });
        if (this.currentTab === "favorite" && !worker.isFavorite) {
          this.workers = this.workers.filter((item) => item.id !== worker.id);
        }
      } catch (error) {
        this.handleRequestError(error, "收藏操作失败");
      } finally {
        worker.operating = false;
      }
    },
    goHire(worker) {
      uni.navigateTo({
        url: `/pages/boss/hire-worker?id=${worker.id}`,
      });
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
      const title = status === 403
        ? "无权操作"
        : status === 404
          ? "人才不存在"
          : error?.message || fallback;
      uni.showToast({ title, icon: "none" });
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

.nav-back {
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

.nav-title {
  font-size: 17px;
  font-weight: 600;
  color: #333;
}

.nav-right {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-plus-svg {
  width: 18px;
  height: 18px;
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
  display: flex;
  gap: 8px;
  padding: 0 16px 12px;
  background: #fff;
  width: 100%;
  box-sizing: border-box;
  flex-shrink: 0;
}

.filter-tab {
  padding: 6px 14px;
  font-size: 13px;
  color: #666;
  background: #f5f5f5;
  border-radius: 16px;
  flex-shrink: 0;
}

.filter-tab.active {
  background: #fff3ed;
  color: #ff6b35;
}

.scroll-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
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
  background: #F5F0EA;
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
  display: flex;
  gap: 12px;
  box-sizing: border-box;
  width: calc(100% - 32px);
  cursor: pointer;
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

.worker-tag {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  background: #fff3ed;
  color: #ff6b35;
  flex-shrink: 0;
}

.worker-tag.blue {
  background: #e6f7ff;
  color: #1890ff;
}

.worker-meta {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  display: block;
}

.worker-skills {
  display: flex;
  gap: 6px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.skill-tag {
  font-size: 11px;
  padding: 2px 8px;
  background: #f5f5f5;
  color: #666;
  border-radius: 4px;
}

.worker-action {
  display: flex;
  flex-direction: column;
  gap: 6px;
  justify-content: center;
  flex-shrink: 0;
}

.btn-sm {
  padding: 6px 12px;
  border-radius: 14px;
  font-size: 12px;
  font-weight: 500;
  border: none;
  line-height: 1.4;
}

.btn-primary {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
}

.btn-outline {
  background: #fff;
  color: #ff6b35;
  border: 1px solid #ff6b35;
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
