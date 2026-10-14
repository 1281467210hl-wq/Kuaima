<template>
  <view class="container">
    <!-- 导航栏（原型 boss-interview.html：wb-header 自绘头部） -->
    <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight}px` }">
      <view class="nav-back" @click="goBack">
        <image class="nav-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" />
      </view>
      <text class="nav-title">面试安排</text>
      <view class="nav-right" />
    </view>

    <scroll-view scroll-y class="scroll-area">
      <view v-if="loading" class="page-state">加载中...</view>

      <template v-else>
        <!-- 候选人头（原型 .iv-head：头像 + 姓名 + 状态标签 + 岗位） -->
        <view class="iv-head">
          <view class="iv-avatar">{{ initial }}</view>
          <view class="iv-head-main">
            <view class="iv-name-line">
              <text class="iv-name">{{ detail.name || "候选人" }}</text>
              <text class="cand-status" :class="statusCls">{{ statusText }}</text>
            </view>
            <text class="iv-sub">{{ positionName || "—" }}</text>
          </view>
        </view>

        <!-- 时间地点（可编辑：date/time 选择器 + 地点输入） -->
        <view class="wb-card">
          <view class="iv-meta-row">
            <view class="iv-meta-icon"><text class="icon-emoji">⏰</text></view>
            <view class="iv-meta-main">
              <text class="iv-meta-label">面试时间</text>
              <view class="iv-picker-line">
                <picker mode="date" :value="form.date" @change="onDateChange">
                  <view class="iv-picker" :class="{ placeholder: !form.date }">{{ form.date || "选择日期" }}</view>
                </picker>
                <picker mode="time" :value="form.time" @change="onTimeChange">
                  <view class="iv-picker" :class="{ placeholder: !form.time }">{{ form.time || "选择时间" }}</view>
                </picker>
              </view>
            </view>
          </view>
          <view class="iv-meta-row">
            <view class="iv-meta-icon"><text class="icon-emoji">📍</text></view>
            <view class="iv-meta-main">
              <text class="iv-meta-label">面试地点</text>
              <input class="iv-input" type="text" v-model="form.location" placeholder="请输入面试地点" />
            </view>
          </view>
        </view>

        <!-- 面试官（原型独立卡片） -->
        <view class="wb-card">
          <view class="block-title"><text class="icon-emoji">👔</text>面试官</view>
          <view class="iv-interviewer">
            <view class="iv-meta-icon blue"><text class="icon-emoji">👤</text></view>
            <input class="iv-input" type="text" v-model="form.interviewer" placeholder="请输入面试官姓名/职务" />
          </view>
        </view>

        <!-- 面试流程时间线（原型 .tl：初试 / 复试 / 终试） -->
        <view class="block-title plain"><text class="icon-emoji">🧭</text>面试流程</view>
        <view class="wb-card tl-card">
          <view v-for="stage in stages" :key="stage.name" class="tl-item">
            <view class="tl-dot" :class="stage.state">
              <text v-if="stage.state !== 'todo'" class="tl-dot-ico">✓</text>
              <view v-else class="tl-dot-inner" />
            </view>
            <view class="tl-title-line">
              <text class="tl-stage" :class="{ muted: stage.state === 'todo' }">{{ stage.name }}</text>
              <text class="tl-badge" :class="stage.state">{{ stage.badge }}</text>
            </view>
            <text class="tl-info" :class="{ todo: stage.state === 'todo' }">{{ stage.info }}</text>
          </view>
        </view>
      </template>

      <view class="bottom-space" />
    </scroll-view>

    <!-- 底部操作（原型 wb-footer：取消面试 / 更新安排） -->
    <view class="wb-footer">
      <view class="wb-btn-outline-red" @click="cancelInterview"><text>✕ 取消面试</text></view>
      <view class="wb-btn-primary-gradient" :class="{ disabled: saving }" @click="saveInterview">
        <text>✓ {{ saving ? "提交中..." : "更新安排" }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import {
  CANDIDATE_STATUS_TEXT,
  CANDIDATE_STATUS_CLS,
  normalizeCandidateStatus,
  getBossApplication,
  createBossInterview,
  updateBossApplicationStatus,
} from "@/api/candidate";
import { handleTokenInvalid } from "@/api/auth";

const BADGE_TEXT = { done: "已完成", doing: "进行中", todo: "待开始" };
const DATETIME_RE = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})/;

export default {
  data() {
    return {
      statusBarHeight: 0,
      applicationId: "",
      detail: {},
      loading: false,
      saving: false,
      form: { date: "", time: "", location: "", interviewer: "" },
    };
  },
  computed: {
    status() {
      return normalizeCandidateStatus(this.detail.status);
    },
    statusText() {
      return CANDIDATE_STATUS_TEXT[this.status] || this.detail.statusName || "待筛选";
    },
    statusCls() {
      return CANDIDATE_STATUS_CLS[this.status] || "gray";
    },
    initial() {
      return String(this.detail.name || "零工").slice(0, 1);
    },
    positionName() {
      return this.detail.positionName || this.detail.job || "";
    },
    scheduledText() {
      const t = String(this.detail.interviewTime || "").replace("T", " ");
      return DATETIME_RE.test(t) ? t.substring(0, 16) : "";
    },
    // 初试-复试-终试时间线：后端仅保留最近一轮面试数据，按状态推导各阶段状态
    stages() {
      const s = this.status;
      let states = ["todo", "todo", "todo"];
      if (s === "PENDING" || s === "PREINTERVIEW") states = ["doing", "todo", "todo"];
      else if (s === "INTERVIEWING") states = ["done", "doing", "todo"];
      else if (s === "HIRED") states = ["done", "done", "done"];
      const names = ["初试", "复试", "终试"];
      const info = this.scheduledText
        ? `${this.scheduledText}${this.detail.interviewer ? " · " + this.detail.interviewer : ""}`
        : this.form.date && this.form.time
          ? `${this.form.date} ${this.form.time}`
          : "";
      return names.map((name, index) => {
        const state = states[index];
        let text = "待定";
        if ((state === "doing" || state === "done") && info) text = info;
        return { name, state, badge: BADGE_TEXT[state], info: text };
      });
    },
  },
  onLoad(options) {
    const info = typeof uni.getWindowInfo === "function" ? uni.getWindowInfo() : uni.getSystemInfoSync();
    this.statusBarHeight = Number(info.statusBarHeight || 0);
    this.applicationId = (options && options.applicationId) || "";
    this.loadDetail();
  },
  methods: {
    async loadDetail() {
      if (!this.applicationId) {
        uni.showToast({ title: "缺少投递 id", icon: "none" });
        return;
      }
      this.loading = true;
      try {
        const d = (await getBossApplication(this.applicationId)) || {};
        this.detail = d;
        this.form.location = d.interviewLocation || "";
        this.form.interviewer = d.interviewer || "";
        const matched = DATETIME_RE.exec(String(d.interviewTime || "").replace("T", " "));
        if (matched) {
          this.form.date = matched[1];
          this.form.time = matched[2];
        }
      } catch (error) {
        this.handleRequestError(error, "加载失败");
      } finally {
        this.loading = false;
      }
    },
    onDateChange(e) {
      this.form.date = e.detail.value;
    },
    onTimeChange(e) {
      this.form.time = e.detail.value;
    },
    async saveInterview() {
      if (this.saving) return;
      if (!this.form.date || !this.form.time) {
        uni.showToast({ title: "请选择面试时间", icon: "none" });
        return;
      }
      const data = { interviewTime: `${this.form.date} ${this.form.time}` };
      const location = String(this.form.location || "").trim();
      const interviewer = String(this.form.interviewer || "").trim();
      if (location) data.location = location;
      if (interviewer) data.interviewer = interviewer;
      this.saving = true;
      try {
        // POST /interview 保存后状态自动置 INTERVIEWING
        await createBossInterview(this.applicationId, data);
        uni.showToast({ title: "面试安排已更新", icon: "none" });
        setTimeout(() => this.goBack(), 600);
      } catch (error) {
        this.handleRequestError(error, "保存失败");
      } finally {
        this.saving = false;
      }
    },
    cancelInterview() {
      uni.showModal({
        title: "提示",
        content: "确定取消该面试安排吗？",
        success: async (res) => {
          if (!res.confirm) return;
          try {
            await updateBossApplicationStatus(this.applicationId, { status: "PENDING" });
            uni.showToast({ title: "已取消面试", icon: "none" });
            setTimeout(() => this.goBack(), 600);
          } catch (error) {
            this.handleRequestError(error, "取消失败");
          }
        },
      });
    },
    goBack() {
      uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/boss/candidates" }) });
    },
    handleRequestError(error, fallback) {
      const status = Number(error?.code || error?.statusCode);
      if (status === 401) {
        handleTokenInvalid({ role: "boss" });
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
  background: #f3f4f6;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
}

/* 自绘头部（同 candidates.vue nav-bar） */
.nav-bar {
  display: flex;
  align-items: center;
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

.nav-title {
  font-size: 17px;
  font-weight: 600;
  color: #333;
}

.scroll-area {
  flex: 1;
  min-height: 0;
  width: 100%;
  box-sizing: border-box;
  padding: 12px 16px 0;
}

.page-state {
  padding: 32px 16px;
  color: #999;
  font-size: 14px;
  text-align: center;
}

/* 候选人头（原型 .iv-head） */
.iv-head {
  background: #fff;
  border-radius: 16px;
  padding: 17px 16px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  display: flex;
  align-items: center;
  gap: 13px;
}

.iv-avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  flex-shrink: 0;
  background: linear-gradient(135deg, #4a90e2, #357abd);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  font-weight: 600;
}

.iv-head-main {
  flex: 1;
  min-width: 0;
}

.iv-name-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.iv-name {
  font-size: 17px;
  font-weight: 700;
  color: #222;
}

.iv-sub {
  font-size: 12.5px;
  color: #888;
  margin-top: 5px;
  display: block;
}

.cand-status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 5px;
  font-weight: 600;
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

/* 卡片（原型 .wb-card） */
.wb-card {
  background: #fff;
  border-radius: 16px;
  padding: 2px 16px 6px;
  margin-top: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}

.iv-meta-row {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 12px 0;
  border-bottom: 0.5px solid #f6f6f6;
}

.iv-meta-row:last-child {
  border-bottom: none;
}

.iv-meta-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: #fff0e8;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.iv-meta-icon.blue {
  background: #ebf3ff;
}

.icon-emoji {
  font-size: 14px;
}

.iv-meta-main {
  flex: 1;
  min-width: 0;
}

.iv-meta-label {
  font-size: 11.5px;
  color: #aaa;
  display: block;
}

.iv-meta-val {
  font-size: 13.5px;
  color: #333;
  font-weight: 500;
  margin-top: 2px;
  display: block;
}

.iv-picker-line {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}

.iv-picker {
  padding: 5px 12px;
  background: #f6f7f9;
  border-radius: 8px;
  font-size: 13.5px;
  color: #333;
  font-weight: 500;
}

.iv-picker.placeholder {
  color: #bbb;
}

.iv-input {
  flex: 1;
  font-size: 13.5px;
  color: #333;
  margin-top: 2px;
  min-width: 0;
}

.block-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  padding: 13px 0 6px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.block-title .icon-emoji {
  color: #ff6b35;
}

.block-title.plain {
  padding: 15px 2px 6px;
}

.iv-interviewer {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 0;
}

/* 时间线（原型 .tl） */
.tl-card {
  padding: 14px 16px 16px;
}

.tl-item {
  position: relative;
  padding: 0 0 22px 30px;
}

.tl-item:last-child {
  padding-bottom: 2px;
}

.tl-item:not(:last-child)::before {
  content: "";
  position: absolute;
  left: 9px;
  top: 22px;
  bottom: 0;
  width: 2px;
  background: #ececec;
}

.tl-dot {
  position: absolute;
  left: 0;
  top: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}

.tl-dot.done {
  background: #10b981;
}

.tl-dot.doing {
  background: #2563eb;
  box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.12);
}

.tl-dot.todo {
  background: #d8d8d8;
}

.tl-dot-ico {
  color: #fff;
  font-size: 11px;
}

.tl-dot-inner {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #fff;
}

.tl-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tl-stage {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.tl-stage.muted {
  color: #999;
  font-weight: 500;
}

.tl-badge {
  font-size: 10.5px;
  padding: 1.5px 7px;
  border-radius: 5px;
}

.tl-badge.done {
  background: #e8f8ef;
  color: #10b981;
}

.tl-badge.doing {
  background: #ebf3ff;
  color: #2563eb;
}

.tl-badge.todo {
  background: #f3f4f6;
  color: #999;
}

.tl-info {
  font-size: 12px;
  color: #888;
  margin-top: 5px;
  line-height: 1.6;
  display: block;
}

.tl-info.todo {
  color: #bbb;
}

.bottom-space {
  height: 20px;
}

/* 底部操作（原型 wb-footer：取消面试 / 更新安排） */
.wb-footer {
  display: flex;
  gap: 12px;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.05);
  flex-shrink: 0;
}

.wb-footer view {
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 23px;
  font-size: 15px;
  font-weight: 600;
}

.wb-btn-outline-red {
  flex: 1;
  color: #ff4d4f;
  border: 1.5px solid #ffd0d0;
  background: #fff;
}

.wb-btn-primary-gradient {
  flex: 1.3;
  color: #fff;
  background: linear-gradient(135deg, #ff8c5a, #ff6b35);
  box-shadow: 0 4px 14px rgba(255, 107, 53, 0.3);
}

.wb-btn-primary-gradient.disabled {
  opacity: 0.6;
}
</style>
