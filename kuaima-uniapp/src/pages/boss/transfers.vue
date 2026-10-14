<template>
  <view class="container">
    <!-- 导航栏 -->
    <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight}px`, height: `${statusBarHeight + 50}px` }">
      <view class="nav-back" @click="goBack">
        <image class="nav-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" />
      </view>
      <text class="nav-title">转账记录</text>
      <view class="nav-right">
        <image class="nav-dots-svg" src="/static/icons/boss-profile/ellipsis.svg" mode="aspectFit" />
      </view>
    </view>

    <scroll-view scroll-y class="body">
      <!-- 搜索行：下载 + 筛选 + 搜索（原型 boss-transfers.html：筛选打开底部弹层） -->
      <view class="action-row">
        <view class="dl-btn" @click="goExport">
          <image class="dl-svg" src="/static/icons/boss-workbench/download-orange.svg" mode="aspectFit" />
          <text>下载</text>
        </view>
        <view class="filter-btn" :class="{ active: hasFilter }" @click="openFilter">
          <image class="filter-svg" src="/static/icons/boss-resume/sliders-gray.svg" mode="aspectFit" />
          <text>筛选</text>
          <image class="filter-arrow-svg" src="/static/icons/boss-authorize/chevron-down-gray.svg" mode="aspectFit" />
        </view>
        <view class="search-bar">
          <image class="search-svg" src="/static/icons/boss-points/search-gray.svg" mode="aspectFit" />
          <input class="search-input" v-model="keyword" placeholder="请输入转账标题" @confirm="loadRecords" @input="onInput" />
        </view>
      </view>

      <!-- 已应用筛选条件 -->
      <view v-if="hasFilter" class="applied-row">
        <view class="applied-tags">
          <text v-for="(tag, i) in appliedTags" :key="i" class="applied-tag">{{ tag }}</text>
        </view>
        <text class="applied-clear" @click="clearFilter">清空筛选</text>
      </view>

      <!-- 转账统计 -->
      <view class="stat-card">
        <view class="stat-title">
          <text>转账统计</text>
          <text class="stat-range">( {{ statRange }} )</text>
        </view>
        <view class="stat-row">
          <view class="stat-item">
            <text class="stat-value">{{ stats.count || 0 }}</text>
            <text class="stat-label">转账笔数</text>
          </view>
          <view class="stat-item">
            <text class="stat-value">{{ stats.people || 0 }}</text>
            <text class="stat-label">转账人数</text>
          </view>
          <view class="stat-item">
            <text class="stat-value orange">¥{{ formatYuan(stats.amount) }}</text>
            <text class="stat-label">转账金额</text>
          </view>
        </view>
      </view>

      <!-- 记录列表 -->
      <view v-if="loading" class="page-state">加载中...</view>
      <view v-else-if="!records.length" class="empty-state">
        <view class="empty-icon">
          <image class="empty-svg" src="/static/icons/boss-workbench/money-empty.svg" mode="aspectFit" />
        </view>
        <text class="empty-text">暂无转账记录</text>
      </view>
      <view class="record-card" v-for="record in records" :key="record.id">
        <view class="record-head">
          <view class="record-title">
            <text class="record-tag" :class="record.type">{{ getTypeText(record.type) }}</text>
            {{ record.title }}
          </view>
          <text class="record-amount">¥{{ formatYuan(record.amount) }}</text>
        </view>
        <view class="record-line"><text class="lab">应发金额</text> {{ record.peopleCount || 0 }}人，¥{{ formatYuan(record.amount) }}</view>
        <view class="record-line"><text class="lab">所属项目</text> {{ record.projectName || '—' }}</view>
        <view class="record-line"><text class="lab">制单人员</text> {{ record.creator || '—' }}</view>
        <view class="record-line"><text class="lab">支付日期</text> {{ formatTime(record.payTime || record.reviewTime) }}</view>
      </view>
      <view class="bottom-space" />
    </scroll-view>

    <!-- 筛选弹层（原型：项目名称 / 所属项目 / 转账时间区间 + 重置 / 确认筛选） -->
    <view v-if="filterShow" class="filter-overlay" @click.self="closeFilter">
      <view class="filter-sheet">
        <view class="filter-sheet-head">
          <text>筛选转账记录</text>
          <view class="filter-close" @click="closeFilter">
            <image class="close-svg" src="/static/icons/boss-points/xmark-gray.svg" mode="aspectFit" />
          </view>
        </view>
        <scroll-view scroll-y class="filter-sheet-body">
          <view class="f-item">
            <text class="f-label">项目名称</text>
            <input
              class="f-input"
              v-model="filterDraft.projectName"
              type="text"
              placeholder="请输入项目名称"
            />
          </view>
          <view class="f-item">
            <text class="f-label">所属项目</text>
            <view class="f-opts">
              <text
                v-for="p in projectOptions"
                :key="p.id"
                class="f-opt"
                :class="{ active: String(filterDraft.projectId) === String(p.id) }"
                @click="filterDraft.projectId = p.id"
              >{{ p.name }}</text>
            </view>
            <text v-if="!projects.length" class="f-opts-hint">项目列表加载失败或为空，可先用「项目名称」输入筛选</text>
          </view>
          <view class="f-item">
            <text class="f-label">转账时间</text>
            <view class="f-date-row">
              <picker mode="date" :value="filterDraft.startDate" @change="onStartDateChange">
                <view class="f-input f-date" :class="{ placeholder: !filterDraft.startDate }">
                  {{ filterDraft.startDate || "开始日期" }}
                </view>
              </picker>
              <text class="f-date-sep">至</text>
              <picker mode="date" :value="filterDraft.endDate" @change="onEndDateChange">
                <view class="f-input f-date" :class="{ placeholder: !filterDraft.endDate }">
                  {{ filterDraft.endDate || "结束日期" }}
                </view>
              </picker>
            </view>
          </view>
        </scroll-view>
        <view class="filter-sheet-footer">
          <view class="f-btn f-reset" @click="resetFilter">重置</view>
          <view class="f-btn f-confirm" @click="applyFilter">确认筛选</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { listTransferRecords, listBossProjects } from "@/api/backend";

function parsePayload(data) {
  return data?.data ?? data ?? {};
}

const EMPTY_FILTER = () => ({ projectName: "", projectId: "", startDate: "", endDate: "" });

export default {
  data() {
    return {
      statusBarHeight: 0,
      keyword: "",
      searchTimer: null,
      loading: false,
      records: [],
      stats: { count: 0, people: 0, amount: 0 },
      statRange: "",
      // 筛选（原型 filterModal）
      filterShow: false,
      filter: EMPTY_FILTER(),
      filterDraft: EMPTY_FILTER(),
      projects: [],
    };
  },
  computed: {
    projectOptions() {
      return [{ id: "", name: "全部项目" }, ...this.projects];
    },
    hasFilter() {
      const f = this.filter;
      return Boolean(f.projectName || f.projectId || f.startDate || f.endDate);
    },
    appliedTags() {
      const f = this.filter;
      const tags = [];
      if (f.projectName) tags.push(`项目名称：${f.projectName}`);
      if (f.projectId) {
        const hit = this.projects.find((p) => String(p.id) === String(f.projectId));
        tags.push(`所属项目：${hit ? hit.name : f.projectId}`);
      }
      if (f.startDate || f.endDate) {
        tags.push(`转账时间：${f.startDate || "不限"} ~ ${f.endDate || "不限"}`);
      }
      return tags;
    },
  },
  onLoad() {
    const info = typeof uni.getWindowInfo === "function"
      ? uni.getWindowInfo() : uni.getSystemInfoSync();
    this.statusBarHeight = Number(info.statusBarHeight || 0);
    this.loadRecords();
  },
  onShow() {
    if (this.statusBarHeight) this.loadRecords();
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
    goExport() {
      uni.navigateTo({ url: "/pages/boss/transfer-export" });
    },
    openFilter() {
      this.filterDraft = { ...this.filter };
      this.filterShow = true;
      if (!this.projects.length) this.loadProjects();
    },
    closeFilter() {
      this.filterShow = false;
    },
    resetFilter() {
      this.filterDraft = EMPTY_FILTER();
    },
    applyFilter() {
      const draft = this.filterDraft;
      const start = draft.startDate;
      const end = draft.endDate;
      if (start && end && start > end) {
        uni.showToast({ title: "转账时间区间不正确", icon: "none" });
        return;
      }
      this.filter = { ...draft };
      this.filterShow = false;
      this.loadRecords();
      if (this.appliedTags.length) {
        uni.showToast({ title: `已应用筛选：${this.appliedTags.join("；")}`, icon: "none" });
      }
    },
    clearFilter() {
      this.filter = EMPTY_FILTER();
      this.filterDraft = EMPTY_FILTER();
      this.loadRecords();
    },
    onStartDateChange(e) {
      this.filterDraft.startDate = e.detail.value;
    },
    onEndDateChange(e) {
      this.filterDraft.endDate = e.detail.value;
    },
    /** 所属项目筛选项：复用老板项目管理真实接口 */
    async loadProjects() {
      try {
        const data = await listBossProjects({ page: 0, size: 50 });
        const body = parsePayload(data);
        const rows = Array.isArray(body) ? body : body.records || body.content || [];
        this.projects = (Array.isArray(rows) ? rows : [])
          .map((item) => ({
            id: item.id ?? item.projectId,
            name: item.name || item.projectName || `项目${item.id ?? ""}`,
          }))
          .filter((item) => item.id !== undefined && item.id !== null && item.name);
      } catch (error) {
        this.projects = [];
      }
    },
    async loadRecords() {
      this.loading = true;
      try {
        const f = this.filter;
        const data = await listTransferRecords({
          keyword: this.keyword.trim() || undefined,
          // 待后端：/boss/transfers 目前只支持 keyword(标题)/projectId/creator/日期，projectName 模糊匹配未实现
          projectName: f.projectName || undefined,
          projectId: f.projectId || undefined,
          startDate: f.startDate || undefined,
          endDate: f.endDate || undefined,
        });
        const body = parsePayload(data);
        this.records = Array.isArray(body.records) ? body.records : (Array.isArray(body) ? body : []);
        this.stats = body.stats || { count: 0, people: 0, amount: 0 };
        this.statRange = body.range || "";
      } catch (error) {
        this.records = [];
      } finally {
        this.loading = false;
      }
    },
    onInput() {
      if (this.searchTimer) clearTimeout(this.searchTimer);
      this.searchTimer = setTimeout(() => this.loadRecords(), 350);
    },
    formatYuan(fen) {
      const n = Number(fen || 0);
      return (n / 100).toFixed(2);
    },
    formatTime(value) {
      if (!value) return "—";
      return String(value).replace("T", " ").substring(0, 16);
    },
    getTypeText(t) {
      const map = { wage: "工资", advance: "预支", other: "其他" };
      return map[t] || t || "工资";
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100%;
  background: #f3f4f6;
  overflow-x: hidden;
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
.nav-back, .nav-right { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; font-size: 18px; color: #333; }
.nav-svg, .nav-dots-svg { width: 16px; height: 16px; }
.nav-title { font-size: 17px; font-weight: 600; color: #333; }

.body {
  flex: 1;
  min-height: 0;
  padding: 12px 16px 0;
  width: 100%;
  box-sizing: border-box;
}

.action-row {
  display: flex; align-items: center; gap: 10px; margin-bottom: 12px;
  width: 100%;
  box-sizing: border-box;
}
.filter-btn {
  display: inline-flex; align-items: center; gap: 5px; font-size: 13px; color: #666;
  flex-shrink: 0; background: #fff; border: 1px solid #f0f0f0; border-radius: 10px; padding: 8px 12px;
}
.filter-btn.active { color: #FF6B35; border-color: #ffd2bc; }
.filter-svg { width: 13px; height: 13px; flex-shrink: 0; }
.filter-arrow-svg { width: 10px; height: 10px; flex-shrink: 0; }
.dl-btn {
  display: inline-flex; align-items: center; gap: 5px; background: #fff;
  border: 1px solid #f0f0f0; border-radius: 10px; padding: 8px 14px; font-size: 13px; color: #FF6B35;
  flex-shrink: 0;
}
.dl-svg { width: 13px; height: 13px; flex-shrink: 0; }
.search-bar {
  flex: 1; display: flex; align-items: center; gap: 8px; padding: 8px 14px;
  background: #fff; border: 1px solid #f0f0f0; border-radius: 10px;
  min-width: 0;
}
.search-svg { width: 15px; height: 15px; flex-shrink: 0; }
.search-input { flex: 1; font-size: 13px; color: #333; }

/* 已应用筛选条件 */
.applied-row {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  margin-bottom: 12px;
}
.applied-tags { display: flex; flex-wrap: wrap; gap: 6px; flex: 1; min-width: 0; }
.applied-tag {
  font-size: 11px; color: #FF6B35; background: #fff3ed; border-radius: 6px; padding: 3px 8px;
}
.applied-clear { font-size: 12px; color: #999; flex-shrink: 0; }

.stat-card {
  background: #fff; border-radius: 16px; padding: 14px 16px; margin-bottom: 12px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04);
  width: 100%;
  box-sizing: border-box;
}
.stat-title {
  font-size: 14px; font-weight: 600; color: #333;
  display: flex; align-items: center; justify-content: space-between;
}
.stat-range { font-size: 12px; color: #999; font-weight: 400; }
.stat-row { display: flex; margin-top: 14px; }
.stat-item { flex: 1; text-align: center; }
.stat-value { font-size: 18px; font-weight: 700; color: #333; display: block; }
.stat-value.orange { color: #FF6B35; }
.stat-label { font-size: 11px; color: #999; display: block; margin-top: 4px; }

.page-state { padding: 32px 0; color: #999; font-size: 14px; text-align: center; }
.empty-state { text-align: center; padding: 80px 40px; }
.empty-icon {
  width: 84px; height: 84px; border-radius: 50%; background: #F5F0EA;
  display: flex; align-items: center; justify-content: center;
  margin: 0 auto 12px;
}
.empty-svg { width: 36px; height: 36px; }
.empty-text { font-size: 13px; color: #999; }

.record-card {
  background: #fff; border-radius: 16px; padding: 14px 16px; margin-bottom: 10px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04);
  width: 100%;
  box-sizing: border-box;
}
.record-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.record-title { font-size: 14px; font-weight: 600; color: #333; display: flex; align-items: center; gap: 6px; min-width: 0; flex: 1; }
.record-title text:first-child { flex-shrink: 0; }
.record-tag { font-size: 11px; padding: 2px 7px; border-radius: 6px; background: #fff3ed; color: #ff6b35; font-weight: 500; flex-shrink: 0; }
.record-tag.advance { background: #fff8e6; color: #d48806; }
.record-tag.other { background: #e6f7ff; color: #1890ff; }
.record-amount { color: #FF6B35; font-weight: 700; font-size: 15px; flex-shrink: 0; }
.record-line { font-size: 12px; color: #888; margin-top: 5px; }
.record-line .lab { color: #B0B0B0; }
.bottom-space { height: 20px; }

/* 筛选弹层（原型 .filter-overlay / .filter-sheet） */
.filter-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 80;
  display: flex;
  align-items: flex-end;
}
.filter-sheet {
  width: 100%;
  background: #fff;
  border-radius: 16px 16px 0 0;
  box-sizing: border-box;
}
.filter-sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}
.filter-close { display: flex; align-items: center; justify-content: center; padding: 4px; }
.close-svg { width: 16px; height: 16px; }
.filter-sheet-body {
  padding: 16px 18px;
  max-height: 60vh;
}
.f-item { margin-bottom: 16px; }
.f-label { font-size: 13px; color: #666; font-weight: 500; display: block; margin-bottom: 8px; }
.f-input {
  width: 100%;
  border: 1px solid #e5e5e5;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  color: #333;
  box-sizing: border-box;
  background: #fff;
}
.f-input.placeholder { color: #c0c0c0; }
.f-date-row { display: flex; align-items: center; gap: 8px; }
.f-date-row picker { flex: 1; min-width: 0; }
.f-date { text-align: center; }
.f-date-sep { color: #999; font-size: 13px; flex-shrink: 0; }
.f-opts { display: flex; flex-wrap: wrap; gap: 8px; }
.f-opt {
  padding: 7px 14px;
  border-radius: 16px;
  background: #f5f6f8;
  color: #666;
  font-size: 13px;
  border: 1px solid transparent;
}
.f-opt.active {
  background: #fff0e8;
  color: #ff6b35;
  border-color: #ffd2bc;
  font-weight: 500;
}
.f-opts-hint {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: #999;
}
.filter-sheet-footer {
  display: flex;
  gap: 12px;
  padding: 12px 18px calc(18px + env(safe-area-inset-bottom));
  border-top: 1px solid #f0f0f0;
}
.f-btn {
  flex: 1;
  text-align: center;
  padding: 12px 0;
  border-radius: 24px;
  font-size: 15px;
  font-weight: 600;
}
.f-reset { background: #f5f5f5; color: #666; }
.f-confirm { background: linear-gradient(135deg, #ff6b35, #ff8c5a); color: #fff; }
</style>
