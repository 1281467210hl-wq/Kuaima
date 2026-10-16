<template>
  <view class="container">
    <scroll-view
      scroll-y
      class="scroll-area"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="refreshPage"
      @scrolltolower="loadMoreOrders"
    >
      <!-- 头部 -->
      <view
        class="header-bar"
        :style="{
          paddingTop: `${statusBarHeight + 12}px`,
          paddingRight: `${menuSafeRight}px`,
        }"
      >
        <view class="title-row">
          <text class="page-title">招工订单</text>
        </view>
      </view>

      <view class="filter-panel">
        <view class="stats-header"><text class="stats-title">统计</text><view class="stats-links"><text @click="navigateTo('talent-list')">查看零工</text><text @click="navigateTo('schedule-stats')">查看排班</text></view></view>
        <view class="time-tabs"><text v-for="item in timeTabs" :key="item.label" class="time-tab" :class="{ active: timeFilter === item.value }" @click="switchTimeFilter(item.value)">{{ item.label }}</text></view>
        <view class="status-row"><view v-for="item in orderStats" :key="item.label" class="status-cell"><text class="status-num">{{ item.value }}</text><text class="status-label">{{ item.label }}{{ item.link ? ' ›' : '' }}</text></view></view>
      </view>

      <!-- 实名认证Banner -->
      <view
        v-if="!isCertified"
        class="cert-bar"
        @click="navigateTo('realname')"
      >
        <view class="cert-left">
          <text class="cert-title">完成实名认证后获取更多招人权益</text>
        </view>
        <text class="cert-btn">立即实名 ›</text>
      </view>

      <view class="acct-bar" @click="showAccountSheet = true">
        <view class="acct-current"><view class="acct-avatar">{{ account.name.slice(0, 1) }}</view><view class="acct-info"><text>{{ account.name }}</text><text>{{ account.type }}</text></view><text class="acct-swap">⇄</text></view>
        <view class="acct-codes">
          <view @click.stop="confirmRefreshCode('work')">
            <text>开工码</text>
            <text class="code-num">{{ attendance.workCodeEnabled ? (attendance.workCode || '--') : '未开启' }}</text>
          </view>
          <view @click.stop="confirmRefreshCode('leave')">
            <text>早退码</text>
            <text class="code-num warn">{{ attendance.leaveCodeEnabled ? (attendance.leaveCode || '--') : '未开启' }}</text>
          </view>
        </view>
      </view>

      <!-- 列表头部 -->
      <view class="list-header">
        <text class="list-title">招工列表</text>
        <view style="display: flex; gap: 10px; align-items: center">
          <view class="filter-btn" @click="navigateTo('boss-filter')">
            <text class="filter-icon">⌕</text>
            <text>筛选</text>
            <text class="filter-arrow">⌄</text>
          </view>
          <view class="applicant-btn" @click="navigateTo('applicant-info')">
            <text>✓</text>
            <text>报名信息</text>
          </view>
        </view>
      </view>

      <!-- 招工卡片列表 -->
      <view id="jobList">
        <view class="job-card" v-for="job in jobList" :key="job.id">
          <view class="job-card-header">
            <view class="job-title-wrap">
              <text class="job-title">{{ job.title }}</text>
            </view>
            <text class="job-status" :class="statusMap[job.status]?.class">
              {{ statusMap[job.status]?.text || job.statusText }}
            </text>
          </view>
          <view class="job-info-grid">
            <view class="info-item">
              <text class="info-icon">▣</text>
              <text class="info-label">招聘岗位：</text>
              <text class="info-value">{{ job.title }}</text>
            </view>
            <view class="info-item">
              <text class="info-icon">◷</text>
              <text class="info-label">工作时间：</text>
              <text class="info-value">{{ job.workTime }}</text>
            </view>
            <view class="info-item">
              <text class="info-icon">⌖</text>
              <text class="info-label">工作地点：</text>
              <text class="info-value">{{ job.location }}</text>
            </view>
            <view class="info-item wage">
              <text class="info-icon">¥</text>
              <text class="info-label">报酬：</text>
              <text class="info-value">{{ job.wage }}</text>
            </view>
            <view class="info-item">
              <text class="info-icon">♟</text>
              <text class="info-label">招募人数：</text>
              <text class="info-value">{{ job.recruitCount }}人</text>
            </view>
            <view class="info-item">
              <text class="info-icon">✓</text>
              <text class="info-label">当前报名：</text>
              <text class="info-value">{{ job.currentApply }}人</text>
            </view>
          </view>
          <scroll-view
            scroll-x
            class="job-actions-scroll"
            :show-scrollbar="false"
          >
            <view class="job-actions">
              <template v-if="job.status === 'recruiting'">
              <text
                class="job-btn btn-primary"
                @click="navigateTo('applicant-info', { orderId: job.id })"
                >查看报名</text
              >
              <text
                class="job-btn btn-secondary"
                @click="navigateTo('publish-info', { id: job.id })"
                >编辑招工</text
              >
              </template>
              <template v-else-if="job.status === 'pending'">
                <text
                  class="job-btn btn-secondary"
                  @click="navigateTo('publish-info', { id: job.id })"
                  >编辑招工</text
                >
              </template>
              <template v-else-if="job.status === 'ended'">
                <text
                  class="job-btn btn-secondary"
                  @click="confirmArrival(job.id)"
                  >确认到岗</text
                >
                <text
                  class="job-btn btn-link"
                  @click="navigateTo('order-detail', { id: job.id })"
                  >详情</text
                >
              </template>
              <template v-else-if="job.status === 'settling'">
                <text
                  class="job-btn btn-primary"
                  @click="navigateTo('suspend-settle', { orderId: job.id })"
                  >去结算</text
                >
                <text
                  class="job-btn btn-link"
                  @click="navigateTo('order-detail', { id: job.id })"
                  >详情</text
                >
              </template>
              <template v-else-if="job.status === 'completed'">
                <text class="job-btn btn-primary" @click="openReview(job)">评价</text>
                <text class="job-btn btn-link" @click="navigateTo('order-detail', { id: job.id })">详情</text>
              </template>
              <template v-else>
                <text
                  class="job-btn btn-link"
                  @click="navigateTo('order-detail', { id: job.id })"
                  >详情</text
                >
              </template>
              <text
                v-if="['pending', 'recruiting'].includes(job.status)"
                class="job-btn btn-danger"
                @click="cancelJob(job.id)"
                >取消招工</text
              >
              <text class="job-btn btn-secondary" @click="saveAsTemplate(job)"
                >收藏为模板</text
              >
              <text class="job-btn btn-primary" @click="repeatOrder(job)"
                >再来一单</text
              >
            </view>
          </scroll-view>
        </view>
      </view>

      <view v-if="loading" class="list-state">正在加载招工订单...</view>
      <view v-else-if="jobList.length === 0" class="empty-state">
        <text class="empty-icon">▣</text>
        <text>暂无招工订单</text>
      </view>
      <view v-else-if="loadingMore" class="list-state">正在加载更多...</view>
      <view v-else-if="!hasMore" class="list-state">没有更多招工订单了</view>

      <view style="height: 20px"></view>
    </scroll-view>

    <!-- 底部TabBar -->
    <view class="tab-bar">
      <view v-if="canAccess('HOME_VIEW')" class="tab-item" @click="switchTab('home')">
        <view class="tab-icon-wrap">
          <image class="tab-svg" src="/static/icons/boss-tabbar/house-gray.svg" mode="aspectFit" />
        </view>
        <text class="tab-label">首页</text>
      </view>
      <view v-if="canAccess('ORDER_VIEW')" class="tab-item active" @click="switchTab('order')">
        <view class="tab-icon-wrap">
          <image class="tab-svg" src="/static/icons/boss-tabbar/calendar-check-white.svg" mode="aspectFit" />
        </view>
        <text class="tab-label">招工订单</text>
      </view>
      <view v-if="canAccess('WORKBENCH_VIEW')" class="tab-item" @click="switchTab('workbench')">
        <view class="tab-icon-wrap">
          <image class="tab-svg" src="/static/icons/boss-tabbar/briefcase-gray.svg" mode="aspectFit" />
        </view>
        <text class="tab-label">工作台</text>
      </view>
      <view v-if="canAccess('MESSAGE_VIEW')" class="tab-item" @click="switchTab('message')">
        <view class="tab-icon-wrap">
          <image class="tab-svg" src="/static/icons/boss-tabbar/comment-dots-gray.svg" mode="aspectFit" />
        </view>
        <text class="tab-label">消息</text>
      </view>
      <view class="tab-item" @click="switchTab('profile')">
        <view class="tab-icon-wrap">
          <image class="tab-svg" src="/static/icons/boss-tabbar/face-smile-gray.svg" mode="aspectFit" />
        </view>
        <text class="tab-label">我的</text>
      </view>
    </view>

    <view
      v-if="showAccountSheet"
      class="sheet-mask"
      @click="showAccountSheet = false"
    >
      <view class="acct-sheet" @click.stop>
        <view class="acct-sheet-header">
          <text class="acct-sheet-title">切换账号</text>
          <text class="acct-sheet-close" @click="showAccountSheet = false">×</text>
        </view>
        <view
          v-for="item in accounts"
          :key="item.id"
          class="acct-item"
          :class="{ selected: item.current || item.id === currentAccountId }"
          @click="selectRecruitAccount(item)"
        >
          <view class="acct-avatar">{{ (item.name || '账').slice(0, 1) }}</view>
          <view class="acct-item-info">
            <text class="acct-item-name">{{ item.name || '招聘账号' }}</text>
            <text>{{ formatAuthorizationType(item.authorizationType) }} · 开工码 {{ item.workCode || '--' }} · 早退码 {{ item.leaveCode || '未开启' }}</text>
          </view>
          <text v-if="item.current || item.id === currentAccountId" class="acct-item-check">✓</text>
        </view>
        <view v-if="!accounts.length" class="account-empty">暂无可切换账号</view>
      </view>
    </view>

    <!-- 取消确认弹窗 -->
    <view
      class="modal-mask"
      v-if="showCancelModal"
      @click="showCancelModal = false"
    >
      <view class="modal-content" @click.stop>
        <text class="modal-title">确认取消招工？</text>
        <text class="modal-subtitle">{{ cancelTargetTitle }}</text>
        <text class="modal-desc"
          >取消后将不再接收零工报名，已报名的零工将收到通知</text
        >
        <view class="modal-actions">
          <text
            class="modal-btn modal-btn-cancel"
            @click="showCancelModal = false"
            >再想想</text
          >
          <text class="modal-btn modal-btn-confirm" @click="confirmCancel"
            >确认取消</text
          >
        </view>
      </view>
    </view>

    <!-- 确认到岗弹窗 -->
    <view
      class="modal-mask"
      v-if="showConfirmModal"
      @click="showConfirmModal = false"
    >
      <view class="modal-content" @click.stop>
        <text class="modal-title">确认全部零工已到岗？</text>
        <text class="modal-subtitle">{{ confirmTargetTitle }}</text>
        <text class="modal-desc"
          >确认后状态将变更为待结算，可前往结算页面进行付款</text
        >
        <view class="modal-actions">
          <text
            class="modal-btn modal-btn-cancel"
            @click="showConfirmModal = false"
            >再想想</text
          >
          <text class="modal-btn modal-btn-confirm" @click="confirmArrive"
            >确认到岗</text
          >
        </view>
      </view>
    </view>

    <view class="modal-mask" v-if="showReviewModal" @click="closeReview">
      <view class="review-modal" @click.stop>
        <view class="review-head"><text class="review-title">评价零工</text><text class="review-close" @click="closeReview">×</text></view>
        <text class="review-order">订单：{{ reviewTarget?.title || '' }} · 请评价此次合作的零工</text>
        <view class="review-block">
          <text class="review-label">综合评分</text>
          <view class="stars"><text v-for="value in 5" :key="value" :class="['star', { active: reviewForm.overallScore >= value }]" @click="setReviewScore('overallScore', value)">★</text><text class="star-label">{{ reviewScoreLabel }}</text></view>
        </view>
        <view v-for="item in reviewDimensions" :key="item.key" class="dimension-row">
          <text>{{ item.label }}</text><view class="small-stars"><text v-for="value in 5" :key="value" :class="['small-star', { active: reviewForm[item.key] >= value }]" @click="setReviewScore(item.key, value)">★</text></view>
        </view>
        <view class="review-block"><text class="review-label">评价内容</text><textarea v-model="reviewForm.content" maxlength="200" class="review-textarea" placeholder="说说这次合作的感受吧～" /><text class="review-count">{{ reviewForm.content.length }}/200</text></view>
        <view class="modal-actions"><text class="modal-btn modal-btn-cancel" @click="closeReview">取消</text><text class="modal-btn modal-btn-confirm" @click="submitReview">{{ reviewSubmitting ? '提交中…' : '提交评价' }}</text></view>
      </view>
    </view>
  </view>
</template>

<script>
import { hasEnterprisePermission } from "@/api/enterprise-context";
import {
  changeOrderStatus,
  getBossAttendanceCodes,
  getBossOrderStats,
  getBossRecruitAccounts,
  getCurrentUser,
  listBossOrders,
  refreshBossLeaveCode,
  refreshBossWorkCode,
  switchBossRecruitAccount,
  saveBossWorkerReview,
} from "@/api/backend";
import { handleTokenInvalid } from "@/api/auth";

function getSafeArea() {
  try {
    const info =
      typeof uni.getWindowInfo === "function"
        ? uni.getWindowInfo()
        : uni.getSystemInfoSync();
    let menuSafeRight = 16;
    // #ifdef MP-WEIXIN
    const menu = uni.getMenuButtonBoundingClientRect();
    if (menu?.left)
      menuSafeRight = Math.max(16, info.windowWidth - menu.left + 12);
    // #endif
    return {
      statusBarHeight: Number(info.statusBarHeight || 0),
      menuSafeRight,
    };
  } catch (_) {
    return { statusBarHeight: 0, menuSafeRight: 16 };
  }
}

const statusByBackend = {
  待审核: "pending",
  审核中: "pending",
  审核拒绝: "rejected",
  招工中: "recruiting",
  招工结束: "ended",
  待结算: "settling",
  已完成: "completed",
  取消招工: "cancelled",
};

function formatTime(value) {
  if (!value) return "--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function normalizeOrder(order, currentApply = 0) {
  const hourlyMatch = String(order.tags || "").match(/时薪:([\d.]+)/);
  const pieceMatch = String(order.tags || "").match(/计件单价:([\d.]+)/);
  const pieceUnitMatch = String(order.tags || "").match(/计件单位:([^,]+)/);
  const wage = hourlyMatch
    ? `${hourlyMatch[1]}元/小时`
    : pieceMatch
      ? `${pieceMatch[1]}元/${pieceUnitMatch?.[1] || "件"}`
      : `${Number(order.salary || 0)}元/天`;
  return {
    ...order,
    title: order.orderTitle || order.postion || "未命名岗位",
    workTime: `${formatTime(order.startTime)} ~ ${formatTime(order.endTime)}`,
    location: order.address || "地点待定",
    wage,
    recruitCount: Number(order.orderNum || 0),
    currentApply: Number(order.currentApply ?? currentApply ?? 0),
    status: statusByBackend[order.orderStatus] || "pending",
    statusText: order.orderStatus || "待审核",
  };
}

function getTemplateRouteParams(job) {
  const wageText = String(job.wage || "0元/天");
  const wage = wageText.match(/[\d.]+/)?.[0] || "0";
  const unit = wageText.replace(/[\d.]+/, "") || "元/天";
  const timeMatch = String(job.workTime || "").match(
    /(\d{1,2}:\d{2})\s*~\s*(\d{1,2}:\d{2})/,
  );
  let time = job.workTime || "时间待定";
  let hours = job.duration ? `${job.duration}工时` : "工时待定";
  if (timeMatch) {
    time = `${timeMatch[1]}开工 ${timeMatch[2]}完工`;
    const [startHour, startMinute] = timeMatch[1].split(":").map(Number);
    const [endHour, endMinute] = timeMatch[2].split(":").map(Number);
    let minutes = endHour * 60 + endMinute - (startHour * 60 + startMinute);
    if (minutes <= 0) minutes += 24 * 60;
    hours = `${Number((minutes / 60).toFixed(1))}工时`;
  }
  return {
    orderId: job.id,
    name: job.title,
    wage,
    unit,
    tag: { month: "月结", heldBack: "压薪日结", daily: "每天日结" }[job.type] || "每天日结",
    time,
    hours,
    count: `招${job.recruitCount || 0}人`,
    age: job.experience || "18岁-不限",
    gender: job.gender || "男女不限",
  };
}

function formatLocalDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function getDateRange(label) {
  if (!label || label === "全部") return {};
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  let start = new Date(today);
  let end = new Date(today);
  if (label === "昨天") {
    start.setDate(start.getDate() - 1);
    end = new Date(start);
  } else if (label === "明天") {
    start.setDate(start.getDate() + 1);
    end = new Date(start);
  } else if (label === "后天") {
    start.setDate(start.getDate() + 2);
    end = new Date(start);
  } else if (label === "本周内") {
    const weekday = today.getDay() || 7;
    end.setDate(today.getDate() + (7 - weekday));
  } else if (label === "本月内") {
    end = new Date(today.getFullYear(), today.getMonth() + 1, 0);
  }
  return { startDate: formatLocalDate(start), endDate: formatLocalDate(end) };
}

function getSalaryRange(label) {
  return {
    "100元以下": { salaryMax: 100 },
    "100-200元": { salaryMin: 100, salaryMax: 200 },
    "200-300元": { salaryMin: 200, salaryMax: 300 },
    "300元以上": { salaryMin: 300 },
  }[label] || {};
}

function unpackOrderPage(result, requestedPage, pageSize) {
  const body = result?.data && !Array.isArray(result.data) ? result.data : result;
  const records = Array.isArray(result)
    ? result
    : Array.isArray(result?.data)
      ? result.data
      : body?.records || body?.content || [];
  const totalValue = result?.total ?? body?.total ?? body?.totalElements;
  const total = Number(totalValue);
  const page = Number(result?.page ?? body?.page ?? body?.number ?? requestedPage);
  const hasTotal = Number.isFinite(total) && total >= 0;
  return {
    records: Array.isArray(records) ? records : [],
    total: hasTotal ? total : null,
    page,
    hasMore: hasTotal
      ? (page + 1) * pageSize < total
      : Array.isArray(records) && records.length === pageSize,
  };
}

function getErrorMessage(error, fallback) {
  const status = Number(error?.code || error?.statusCode);
  if (status === 403) return error.message || "无权操作";
  if (status === 404) return error.message || "订单不存在";
  if (status === 400) return error.message || fallback;
  if (!error?.statusCode) return "加载失败";
  return error?.message || fallback;
}

export default {
  data() {
    const safeArea = getSafeArea();
    return {
      ...safeArea,
      jobList: [],
      statusMap: {
        recruiting: { text: "招工中", class: "status-recruiting" },
        pending: { text: "待审核", class: "status-ended" },
        rejected: { text: "审核拒绝", class: "status-cancelled" },
        ended: { text: "招工结束", class: "status-ended" },
        settling: { text: "待结算", class: "status-settling" },
        completed: { text: "已完成", class: "status-completed" },
        cancelled: { text: "取消招工", class: "status-cancelled" },
      },
      statusFilter: null,
      timeTabs: [
        { label: "全部", value: "ALL" },
        { label: "昨天", value: "YESTERDAY" },
        { label: "今天", value: "TODAY" },
        { label: "明天", value: "TOMORROW" },
      ],
      timeFilter: "ALL",
      orderStats: [
        { label: "报名", field: "applicantCount", value: 0, link: true },
        { label: "接单", field: "acceptedCount", value: 0, link: true },
        { label: "到达", field: "arrivedCount", value: 0 },
        { label: "工作中", field: "workingCount", value: 0, link: true },
        { label: "待结算", field: "pendingSettlementCount", value: 0, link: true },
      ],
      account: { name: "当前账号", type: "个人授权", workCode: "", leaveCode: "" },
      attendance: {
        workCodeEnabled: false,
        workCode: "",
        workCodeExpiresAt: "",
        leaveCodeEnabled: false,
        leaveCode: "",
        leaveCodeExpiresAt: "",
      },
      accounts: [],
      currentAccountId: null,
      showAccountSheet: false,
      switchingAccount: false,
      showCancelModal: false,
      showConfirmModal: false,
      showReviewModal: false,
      reviewSubmitting: false,
      reviewTarget: null,
      reviewForm: { overallScore: 0, attitudeScore: 0, efficiencyScore: 0, skillScore: 0, content: "" },
      reviewDimensions: [
        { key: "attitudeScore", label: "工作态度" },
        { key: "efficiencyScore", label: "工作效率" },
        { key: "skillScore", label: "专业技能" },
      ],
      cancelTargetId: null,
      cancelTargetTitle: "",
      confirmTargetId: null,
      confirmTargetTitle: "",
      loading: false,
      loadingMore: false,
      loadingStats: false,
      loadingAccounts: false,
      refreshing: false,
      authRedirecting: false,
      page: 0,
      pageSize: 20,
      orderRequestSequence: 0,
      total: 0,
      hasMore: true,
      operating: false,
      isCertified: false,
      orderFilter: null,
    };
  },
  computed: {
    reviewScoreLabel() {
      return ["请选择评分", "很差", "较差", "一般", "满意", "非常满意"][this.reviewForm.overallScore] || "请选择评分";
    },
  },
  onShow() {
    this.loadOrderFilter();
    this.loadCertificationStatus();
    this.refreshData();
  },
  onLoad() {
    uni.$on("filterChanged", this.applyOrderFilter);
    uni.$on("recruitSettingsSaved", this.loadAttendanceCodes);
  },
  onUnload() {
    uni.$off("filterChanged", this.applyOrderFilter);
    uni.$off("recruitSettingsSaved", this.loadAttendanceCodes);
  },
  methods: {
    canAccess(code) { return hasEnterprisePermission(code); },
    openReview(job) {
      this.reviewTarget = job;
      this.reviewForm = { overallScore: 0, attitudeScore: 0, efficiencyScore: 0, skillScore: 0, content: "" };
      this.showReviewModal = true;
    },
    closeReview() {
      if (!this.reviewSubmitting) this.showReviewModal = false;
    },
    setReviewScore(key, value) {
      this.reviewForm[key] = value;
    },
    async submitReview() {
      if (this.reviewSubmitting || !this.reviewTarget) return;
      const required = ["overallScore", "attitudeScore", "efficiencyScore", "skillScore"];
      if (required.some((key) => !this.reviewForm[key])) {
        return uni.showToast({ title: "请完成全部评分", icon: "none" });
      }
      this.reviewSubmitting = true;
      try {
        await saveBossWorkerReview(this.reviewTarget.id, { ...this.reviewForm, content: this.reviewForm.content.trim() });
        this.showReviewModal = false;
        uni.showToast({ title: "评价提交成功", icon: "success" });
      } catch (error) {
        this.handleRequestError(error, "评价提交失败");
      } finally {
        this.reviewSubmitting = false;
      }
    },
    handleRequestError(error, fallback) {
      if (Number(error?.code || error?.statusCode) === 401) {
        if (!this.authRedirecting) {
          this.authRedirecting = true;
          handleTokenInvalid({ role: "boss" }).finally(() => {
            this.authRedirecting = false;
          });
        }
        return;
      }
      uni.showToast({ title: getErrorMessage(error, fallback), icon: "none" });
    },
    loadOrderFilter() {
      const saved = uni.getStorageSync("bossOrderFilter");
      this.orderFilter = saved && typeof saved === "object" ? saved : null;
    },
    applyOrderFilter(filter) {
      this.orderFilter = filter && typeof filter === "object" ? filter : null;
    },
    async getOrderFilterParams() {
      const filter = this.orderFilter || {};
      const params = { ...getDateRange(filter.date), ...getSalaryRange(filter.salary) };
      [
        "type",
        "title",
        "startDate",
        "endDate",
        "jobCategoryId",
        "salaryMin",
        "salaryMax",
        "longitude",
        "latitude",
        "distanceKm",
        "experience",
        "gender",
        "tags",
        "tagMode",
      ].forEach((key) => {
        if (filter[key] !== undefined && filter[key] !== null && filter[key] !== "") {
          params[key] = filter[key];
        }
      });
      if (filter.jobType && filter.jobType !== "全部") {
        params.title = filter.jobType;
      }
      if (filter.experience && filter.experience !== "不限") {
        params.experience = filter.experience;
      }
      if (filter.gender && !["不限", "男女均可"].includes(filter.gender)) {
        params.gender = filter.gender;
      }
      const tags = Array.isArray(filter.tags) ? [...filter.tags] : [];
      if (filter.salary === "日结周结") tags.push("日结");
      if (tags.length) {
        params.tags = [...new Set(tags)].join(",");
        params.tagMode = "ALL";
      }
      if (filter.location && filter.location !== "不限") {
        if (filter.location !== "同城") {
          params.distanceKm = Number(String(filter.location).match(/[\d.]+/)?.[0] || 0);
        }
        const location = await this.getCurrentLocation();
        if (location) {
          params.longitude = location.longitude;
          params.latitude = location.latitude;
        }
      }
      return params;
    },
    getCurrentLocation() {
      return new Promise((resolve) => {
        uni.getLocation({
          type: "gcj02",
          success: ({ longitude, latitude }) => resolve({ longitude, latitude }),
          fail: () => resolve(null),
        });
      });
    },
    applyCertificationStatus(user = {}) {
      const status = String(
        user.certStatus || user.certificationStatus || "",
      ).trim();
      this.isCertified =
        status === "已通过" ||
        status === "通过" ||
        status.toUpperCase() === "APPROVED";
    },
    async loadCertificationStatus() {
      const cachedUser = uni.getStorageSync("userInfo");
      if (cachedUser && typeof cachedUser === "object") {
        this.applyCertificationStatus(cachedUser);
      }
      try {
        const user = await getCurrentUser();
        if (user && typeof user === "object") {
          uni.setStorageSync("userInfo", user);
          this.applyCertificationStatus(user);
        }
      } catch (_) {
        // 用户资料加载失败时保留本地缓存状态，不影响订单列表使用。
      }
    },
    navigateTo(pageName, params = {}) {
      const bossPages = [
        "boss-employer",
        "boss-home",
        "boss-message",
        "boss-order",
        "boss-profile",
        "boss-publish",
        "publish",
        "search-worker",
        "select-job",
        "publish-info",
        "publish-template",
        "schedule-stats",
        "enterprise-cert",
        "enterprise-cert-form",
        "creditor-score",
        "talent-list",
        "expense-detail",
        "payment-detail",
        "recruit-manager",
        "recruit-address",
        "sub-account",
        "suspend-settle",
        "switch-account",
        "invite-code",
        "blacklist",
        "all-jobs",
        "boss-filter",
        "settlement",
        "contract",
        "system-notice",
        "missed-call",
        "signup-notice",
        "invite-friend",
        "service-chat",
        "insurance",
        "realname",
        "applicant-info",
        "order-detail",
      ];

      let url = `/pages/boss/${pageName}`;
      if (!bossPages.includes(pageName)) {
        url = `/pages/${pageName}`;
      }

      const query = Object.entries(params)
        .map(
          ([key, value]) =>
            `${encodeURIComponent(key)}=${encodeURIComponent(value)}`,
        )
        .join("&");
      uni.navigateTo({ url: query ? `${url}?${query}` : url });
    },
    async loadOrders(reset = true) {
      if (!reset && (this.loading || this.loadingMore || !this.hasMore)) return;
      const requestSequence = ++this.orderRequestSequence;
      if (reset) {
        this.loading = true;
        this.page = 0;
        this.hasMore = true;
      } else {
        if (!this.hasMore) return;
        this.loadingMore = true;
      }
      try {
        const requestedPage = reset ? 0 : this.page;
        const result = await listBossOrders({
          page: requestedPage,
          size: this.pageSize,
          status: this.statusFilter || undefined,
          ...(await this.getOrderFilterParams()),
        });
        const pageData = unpackOrderPage(result, requestedPage, this.pageSize);
        if (requestSequence !== this.orderRequestSequence) return;
        const normalized = pageData.records.map((order) => normalizeOrder(order));
        this.jobList = reset ? normalized : [...this.jobList, ...normalized];
        this.total = pageData.total ?? this.jobList.length;
        this.hasMore = pageData.hasMore;
        this.page = requestedPage + 1;
      } catch (error) {
        if (requestSequence !== this.orderRequestSequence) return;
        if (reset) this.jobList = [];
        this.handleRequestError(error, "招工列表加载失败");
      } finally {
        if (requestSequence === this.orderRequestSequence) {
          this.loading = false;
          this.loadingMore = false;
        }
      }
    },
    loadMoreOrders() {
      this.loadOrders(false);
    },
    async loadStats() {
      if (this.loadingStats) return;
      this.loadingStats = true;
      try {
        const stats = await getBossOrderStats(this.timeFilter);
        this.orderStats = this.orderStats.map((item) => ({
          ...item,
          value: Number(stats?.[item.field] || 0),
        }));
      } catch (error) {
        this.handleRequestError(error, "统计加载失败");
      } finally {
        this.loadingStats = false;
      }
    },
    async switchTimeFilter(value) {
      if (this.timeFilter === value || this.loadingStats) return;
      this.timeFilter = value;
      await this.loadStats();
    },
    formatAuthorizationType(value) {
      return { PERSONAL: "个人授权", ENTERPRISE: "企业授权" }[value] || value || "未设置授权";
    },
    async loadAttendanceCodes() {
      try {
        const result = await getBossAttendanceCodes();
        this.attendance = { ...this.attendance, ...(result || {}) };
      } catch (error) {
        this.handleRequestError(error, "考勤码加载失败");
      }
    },
    confirmRefreshCode(type) {
      const label = type === "work" ? "开工码" : "早退码";
      uni.showModal({
        title: `刷新${label}`,
        content: `确定要刷新${label}吗？旧验证码将立即失效。`,
        success: async ({ confirm }) => {
          if (!confirm) return;
          try {
            const result = type === "work"
              ? await refreshBossWorkCode()
              : await refreshBossLeaveCode();
            this.attendance = { ...this.attendance, ...(result || {}) };
            uni.showToast({ title: `${label}已刷新`, icon: "success" });
          } catch (error) {
            this.handleRequestError(error, `${label}刷新失败`);
          }
        },
      });
    },
    applyCurrentAccount(item) {
      this.account = {
        name: item?.name || "当前账号",
        type: this.formatAuthorizationType(item?.authorizationType),
        workCode: item?.workCode || "",
        leaveCode: item?.leaveCode || "",
      };
    },
    async loadAccounts() {
      if (this.loadingAccounts) return;
      this.loadingAccounts = true;
      try {
        const result = await getBossRecruitAccounts();
        this.accounts = Array.isArray(result?.accounts) ? result.accounts : [];
        this.currentAccountId = result?.currentAccountId ?? this.accounts.find((item) => item.current)?.id ?? null;
        const current = this.accounts.find((item) => item.current || item.id === this.currentAccountId);
        this.applyCurrentAccount(current);
      } catch (error) {
        this.accounts = [];
        this.handleRequestError(error, "账号加载失败");
      } finally {
        this.loadingAccounts = false;
      }
    },
    async selectRecruitAccount(item) {
      if (!item?.id || item.id === this.currentAccountId || this.switchingAccount) {
        this.showAccountSheet = false;
        return;
      }
      this.switchingAccount = true;
      try {
        await switchBossRecruitAccount(item.id);
        this.showAccountSheet = false;
        uni.showToast({ title: "招聘账号已切换", icon: "success" });
        await Promise.all([
          this.loadOrders(true),
          this.loadStats(),
          this.loadAccounts(),
          this.loadAttendanceCodes(),
        ]);
      } catch (error) {
        this.handleRequestError(error, "账号切换失败");
      } finally {
        this.switchingAccount = false;
      }
    },
    saveAsTemplate(job) {
      if (!job?.id) {
        uni.showToast({ title: "订单信息不完整", icon: "none" });
        return;
      }
      this.navigateTo("publish-template", getTemplateRouteParams(job));
    },
    repeatOrder(job) {
      this.navigateTo("select-job");
    },
    switchTab(tab) {
      const tabPages = {
        home: "/pages/boss/home",
        order: "/pages/boss/order",
        workbench: "/pages/boss/workbench",
        message: "/pages/boss/message",
        profile: "/pages/boss/profile",
      };
      const target = tabPages[tab];
      const currentRoute = getCurrentPages().slice(-1)[0]?.route;
      if (!target || `/${currentRoute}` === target) return;
      uni.redirectTo({
        url: target,
        fail: (error) => {
          console.error("Boss 主导航跳转失败", error);
          uni.reLaunch({
            url: target,
            fail: () =>
              uni.showToast({ title: "页面跳转失败，请重试", icon: "none" }),
          });
        },
      });
    },
    cancelJob(id) {
      const job = this.jobList.find((j) => j.id === id);
      this.cancelTargetId = id;
      this.cancelTargetTitle = job ? job.title : "";
      this.showCancelModal = true;
    },
    async confirmCancel() {
      if (!this.cancelTargetId || this.operating) return;
      this.operating = true;
      try {
        await changeOrderStatus(this.cancelTargetId, "取消招工");
        this.showCancelModal = false;
        await Promise.all([this.loadOrders(true), this.loadStats()]);
        uni.showToast({ title: "招工已取消", icon: "success" });
      } catch (error) {
        this.handleRequestError(error, "取消失败");
      } finally {
        this.operating = false;
      }
    },
    confirmArrival(id) {
      const job = this.jobList.find((j) => j.id === id);
      this.confirmTargetId = id;
      this.confirmTargetTitle = job ? job.title : "";
      this.showConfirmModal = true;
    },
    async confirmArrive() {
      if (!this.confirmTargetId || this.operating) return;
      this.operating = true;
      try {
        await changeOrderStatus(this.confirmTargetId, "待结算");
        this.showConfirmModal = false;
        await Promise.all([this.loadOrders(true), this.loadStats()]);
        uni.showToast({ title: "订单已进入待结算", icon: "success" });
      } catch (error) {
        this.handleRequestError(error, "状态更新失败");
      } finally {
        this.operating = false;
      }
    },
    async refreshData() {
      await Promise.all([
        this.loadOrders(true),
        this.loadStats(),
        this.loadAccounts(),
        this.loadAttendanceCodes(),
      ]);
    },
    async refreshPage() {
      if (this.refreshing) return;
      this.refreshing = true;
      try {
        await this.refreshData();
      } finally {
        this.refreshing = false;
      }
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  width: 100%;
  height: 100vh;
  background: #F3F4F6;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  background: #F3F4F6;
}

.header-bar {
  background: linear-gradient(180deg, #ffd59e 0%, #ffe4b5 100%);
  padding: 12px 16px 16px;
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #8b4513;
}
.filter-panel,.acct-bar { background:#fff; margin:0 16px 12px; border-radius:14px; box-shadow:0 2px 10px rgba(0,0,0,.05); }
.filter-panel { padding:14px; }
.stats-header { display:flex; justify-content:space-between; align-items:center; }
.stats-title { font-size:16px; font-weight:700; color:#333; border-left:4px solid #ff6b35; padding-left:7px; }
.stats-links { display:flex; gap:14px; color:#e88a00; font-size:12px; }
.time-tabs { display:flex; gap:20px; margin:16px 0 14px; }
.time-tab { position:relative; padding-bottom:5px; color:#999; font-size:13px; }
.time-tab.active { color:#ff6b35; font-weight:600; }
.time-tab.active::after { content:""; position:absolute; bottom:0; left:50%; width:16px; height:3px; margin-left:-8px; border-radius:2px; background:#ff6b35; }
.status-row { display:flex; gap:8px; }
.status-cell { flex:1; min-width:0; padding:10px 2px; border-radius:10px; background:#f8f8fa; text-align:center; }
.status-num,.status-label { display:block; }
.status-num { color:#333; font-size:20px; font-weight:700; }
.status-cell:last-child .status-num { color:#ff6b35; }
.status-label { margin-top:3px; color:#999; font-size:11px; white-space:nowrap; }
.acct-bar { display:flex; justify-content:space-between; align-items:center; padding:12px 14px; }
.acct-current { display:flex; align-items:center; gap:8px; }
.acct-avatar { width:38px; height:38px; line-height:38px; border-radius:50%; text-align:center; color:#fff; background:linear-gradient(135deg,#ffb347,#ff6b35); }
.acct-info { display:flex; flex-direction:column; gap:3px; font-size:12px; color:#999; }
.acct-info text:first-child { color:#333; font-size:14px; font-weight:700; }
.acct-swap { color:#ff6b35; }
.acct-codes { display:flex; gap:18px; text-align:center; color:#999; font-size:11px; }
.acct-codes view { display:flex; flex-direction:column; gap:3px; }
.code-num { color:#333; font-size:16px; font-weight:700; }
.code-num.warn { color:#ff6b35; font-size:13px; }
.sheet-mask {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.5);
}
.acct-sheet {
  width: 100%;
  max-height: 70vh;
  padding: 18px 16px calc(24px + env(safe-area-inset-bottom));
  overflow-y: auto;
  border-radius: 20px 20px 0 0;
  background: #fff;
  box-sizing: border-box;
}
.acct-sheet-header { position:relative; display:flex; justify-content:center; align-items:center; margin-bottom:14px; }
.acct-sheet-title { color:#333; font-size:16px; font-weight:700; }
.acct-sheet-close { position:absolute; right:0; padding:4px 8px; color:#999; font-size:22px; }
.acct-item { display:flex; align-items:center; gap:10px; margin-bottom:10px; padding:12px; border:1.5px solid transparent; border-radius:14px; background:#f7f7f7; }
.acct-item.selected { border-color:#ff9800; background:#fff7e8; }
.acct-item-info { display:flex; flex:1; min-width:0; flex-direction:column; gap:4px; color:#999; font-size:12px; }
.acct-item-name { color:#333; font-size:14px; font-weight:600; }
.acct-item-check { color:#ff9800; font-size:16px; }
.account-empty { padding:32px 0; color:#999; font-size:13px; text-align:center; }
.job-actions-extra { margin-top:8px; padding-top:10px; border-top:1px solid #f5f5f5; }

.nav-icons {
  display: flex;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(20px);
  border-radius: 50px;
  padding: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.nav-icon-item {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-divider {
  width: 1px;
  height: 16px;
  background: #ddd;
  margin: 0 4px;
}

.notice-bar {
  background: rgba(255, 107, 53, 0.1);
  border-radius: 12px;
  padding: 10px 14px;
  margin: -2px 16px 12px;
  font-size: 12px;
  color: #d2691e;
  display: flex;
  align-items: center;
  gap: 8px;
  box-sizing: border-box;
}

.notice-icon,
.info-icon {
  color: #ff6b35;
  flex-shrink: 0;
  font-weight: 600;
}

.notice-text {
  flex: 1;
  min-width: 0;
  white-space: normal;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.cert-bar {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  margin: 16px;
  padding: 16px;
  border-radius: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: white;
}

.cert-title {
  font-size: 15px;
  font-weight: 600;
}

.cert-btn {
  background: white;
  color: #ff6b35;
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 0 16px 12px;
}

.list-title {
  font-size: 18px;
  font-weight: 700;
  color: #333;
  flex: 1;
  min-width: 0;
}

.filter-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: white;
  border-radius: 16px;
  font-size: 12px;
  color: #666;
  white-space: nowrap;
  flex-shrink: 0;
}

.filter-icon,
.filter-arrow {
  color: #999;
}

.filter-arrow {
  font-size: 12px;
}

.applicant-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  border-radius: 14px;
  font-size: 12px;
  color: white;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(255, 107, 53, 0.25);
  white-space: nowrap;
  flex-shrink: 0;
}

/* 招工卡片 */
.job-card {
  background: #fff;
  margin: 0 16px 12px;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}

.job-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-bottom: 12px;
  border-bottom: 1px solid #f5f5f5;
  margin-bottom: 12px;
  gap: 10px;
}

.job-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.job-title {
  font-size: 16px;
  font-weight: 700;
  color: #333;
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
  line-height: 1.45;
}

.job-status {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
}

.status-recruiting {
  background: #fff3e0;
  color: #ff6b35;
}
.status-ended {
  background: #e6f7ff;
  color: #1890ff;
}
.status-settling {
  background: #fff8e6;
  color: #fa8c16;
}
.status-completed {
  background: #f6ffed;
  color: #52c41a;
}
.status-cancelled {
  background: #f5f5f5;
  color: #999;
}

.job-info-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px 16px;
  margin-bottom: 14px;
}

.info-item {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 6px;
  font-size: 12px;
  color: #666;
  line-height: 1.5;
}

.info-icon {
  width: 14px;
  text-align: center;
  font-size: 12px;
}

.info-item .info-label {
  color: #999;
  font-size: 11px;
  flex: 0 0 auto;
}

.info-item .info-value {
  color: #333;
  font-weight: 500;
  margin-left: 2px;
  min-width: 0;
  flex: 1;
  white-space: normal;
  overflow-wrap: anywhere;
  line-height: 1.45;
}

.info-item.wage .info-value {
  color: #ff6b35;
  font-weight: 600;
}

.info-item.wage .info-label {
  color: #ffb088;
}

.info-item:nth-child(odd) {
  padding-right: 8px;
  border-right: 1px dashed #f0f0f0;
}

.job-actions-scroll {
  width: 100%;
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
  box-sizing: border-box;
  white-space: nowrap;
}

.job-actions {
  display: inline-flex;
  gap: 8px;
  min-width: 100%;
  align-items: center;
}

.job-actions .job-btn {
  flex: 0 0 auto;
  overflow: hidden;
  text-overflow: ellipsis;
}

.job-btn {
  padding: 6px 14px;
  border-radius: 14px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid transparent;
  white-space: nowrap;
  flex-shrink: 0;
}

.list-state,
.empty-state {
  text-align: center;
  color: #999;
  font-size: 14px;
  padding: 48px 20px;
}

.empty-icon {
  display: block;
  color: #d8d8d8;
  font-size: 48px;
  line-height: 1;
  margin-bottom: 14px;
}

.btn-primary {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: white;
}

.btn-secondary {
  background: #fff;
  color: #666;
  border-color: #e0e0e0;
}

.btn-danger {
  background: #fff;
  color: #f5222d;
  border-color: #ffece8;
}

.btn-link {
  background: transparent;
  color: #1890ff;
  border: none;
  padding: 6px 4px;
}

/* TabBar样式 */
.tab-bar {
  flex-shrink: 0;
  min-height: 63px;
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(20px);
  border-top: 0.5px solid rgba(0, 0, 0, 0.05);
  display: flex;
  padding-bottom: env(safe-area-inset-bottom);
  z-index: 50;
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.tab-icon-wrap {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  margin-bottom: 3px;
}

.tab-label {
  font-size: 10px;
  color: #999;
  font-weight: 500;
}

.tab-item.active .tab-label {
  color: #ff6b35;
}

.tab-item.active .tab-icon-wrap {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  color: white;
  margin-bottom: 2px;
  box-shadow: 0 4px 10px rgba(255, 107, 53, 0.3);
}

.tab-svg {
  width: 22px;
  height: 22px;
}

/* 弹窗样式 */
.modal-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-content {
  width: 280px;
  max-width: calc(100% - 48px);
  background: #fff;
  border-radius: 16px;
  padding: 24px 20px;
  text-align: center;
  box-sizing: border-box;
}

.modal-title {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
}

.modal-subtitle {
  display: block;
  font-size: 13px;
  color: #999;
  margin-bottom: 16px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.modal-desc {
  display: block;
  font-size: 12px;
  color: #666;
  margin-bottom: 20px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.modal-actions {
  display: flex;
  gap: 12px;
}

.modal-btn {
  flex: 1;
  height: 42px;
  padding: 0;
  border-radius: 20px;
  font-size: 14px;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.modal-btn-cancel {
  border: 1px solid #e0e0e0;
  color: #666;
}

.modal-btn-confirm {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
}
.review-modal{width:330px;max-width:calc(100% - 40px);background:#fff;border-radius:16px;padding:22px 20px;box-sizing:border-box}.review-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.review-title{font-size:16px;font-weight:700;color:#333}.review-close{font-size:24px;color:#999;padding:0 4px}.review-order{display:block;font-size:13px;color:#666;margin-bottom:14px;line-height:1.5}.review-block{position:relative;margin-bottom:14px}.review-label{display:block;font-size:13px;color:#333;font-weight:500;margin-bottom:7px}.stars{display:flex;align-items:center;gap:5px}.star{font-size:27px;color:#e0e0e0}.star.active,.small-star.active{color:#ffa14e}.star-label{margin-left:8px;font-size:13px;color:#999}.dimension-row{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;font-size:13px;color:#666}.small-stars{display:flex;gap:3px}.small-star{font-size:18px;color:#e0e0e0}.review-textarea{width:100%;height:80px;border:1px solid #e5e5e5;border-radius:10px;padding:10px;font-size:13px;box-sizing:border-box}.review-count{position:absolute;right:8px;bottom:6px;font-size:10px;color:#aaa}
</style>
