<template>
  <view class="container">
    <view class="page-bg">
      <!-- 导航栏 -->
      <view
        class="nav-bar"
        :style="{ paddingTop: `${statusBarHeight + 14}px` }"
      >
        <view class="nav-back" @click="goBack">
          <text>←</text>
        </view>
        <text class="nav-title">发布招工</text>
        <view class="nav-placeholder"></view>
      </view>

      <!-- 步骤条 -->
      <view class="stepper">
        <view class="step active">
          <view class="dot">
            <text style="font-size: 10px">✓</text>
          </view>
          <text>基础信息</text>
        </view>
        <view class="step-line active"></view>
        <view class="step active">
          <view class="dot">2</view>
          <text>招工需求</text>
        </view>
      </view>

      <scroll-view scroll-y class="scroll-area">
        <!-- 需求人数 -->
        <view class="section-card">
          <view class="section-header">
            <text class="section-title">需求人数</text>
          </view>
          <view class="counter-row">
            <view class="counter-btn" @click="changeCount(-1)">
              <text>−</text>
            </view>
            <view>
              <text class="counter-value">{{ count }}</text>
              <text class="counter-unit">人</text>
            </view>
            <view class="counter-btn active" @click="changeCount(1)">
              <text>+</text>
            </view>
          </view>
        </view>

        <!-- 工价设置 -->
        <view class="section-card">
          <view class="section-header">
            <text class="section-title">工价设置</text>
          </view>
          <view class="tab-group">
            <text
              class="tab-item"
              :class="{ active: payType === 'hourly' }"
              @click="payType = 'hourly'"
              >计时</text
            >
            <text
              class="tab-item"
              :class="{ active: payType === 'piece' }"
              @click="payType = 'piece'"
              >计件</text
            >
          </view>

          <!-- 计时面板 -->
          <view v-if="payType === 'hourly'">
            <view class="form-row" @click="setSalary">
              <view class="form-value placeholder">
                <text>{{ hasSalary ? salary + "元/小时" : "请设置工价" }}</text>
                <text class="› arrow"></text>
              </view>
            </view>
            <view class="salary-row">
              <text class="salary-label">预计报酬</text>
              <view class="salary-content">
                <view class="salary-value">
                  <text>{{ hasSalary ? salary * workHours : "--" }}</text>
                  <text class="salary-unit">元/天</text>
                </view>
                <text class="salary-sub">{{
                  hasSalary
                    ? `每个零工${salary}元/小时*${workHours}小时`
                    : "请先设置工价"
                }}</text>
              </view>
            </view>
          </view>

          <!-- 计件面板 -->
          <view v-if="payType === 'piece'">
            <view class="form-row" @click="setPiecePrice">
              <text style="font-size: 14px; color: #333">单价</text>
              <view class="form-value placeholder">
                <text>{{
                  piecePrice ? `${piecePrice}元/${pieceUnit}` : "请设置单价"
                }}</text>
                <text class="› arrow"></text>
              </view>
            </view>
            <view class="form-row" @click="setPieceUnit">
              <text class="form-label">计件单位</text>
              <view class="form-value placeholder"
                ><text>{{ pieceUnit || "请选择单位" }}</text
                ><text class="arrow">›</text></view
              >
            </view>
            <view class="form-row" @click="setEstOutput">
              <text class="form-label">预估日产量</text>
              <view class="form-value placeholder"
                ><text>{{
                  estOutput ? `${estOutput}${pieceUnit}/天` : "请设置预估产量"
                }}</text
                ><text class="arrow">›</text></view
              >
            </view>
            <view class="salary-row">
              <text class="salary-label">预计报酬</text>
              <view class="salary-content">
                <view class="salary-value">
                  <text>{{
                    piecePrice && estOutput ? piecePrice * estOutput : "--"
                  }}</text>
                  <text class="salary-unit">元/天</text>
                </view>
                <text class="salary-sub">{{
                  piecePrice && estOutput
                    ? `${piecePrice}元/${pieceUnit} × ${estOutput}${pieceUnit}/天`
                    : "单价×预估日产量"
                }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 联系电话 -->
        <view class="section-card">
          <view class="section-header" style="justify-content: space-between">
            <text class="section-title">联系电话</text>
            <text style="font-size: 14px; color: #333">{{ contactPhone }}</text>
          </view>
          <view class="form-row" style="border-bottom: none">
            <text style="font-size: 12px; color: #999"
              >零工电话报名、问路等</text
            >
            <view
              style="
                display: flex;
                align-items: center;
                gap: 4px;
                margin-left: auto;
              "
              @click="navigateTo('backup-phone')"
            >
              <text class="link-btn">
                <text style="margin-right: 4px">+</text>
                添加备用联系人
              </text>
              <text style="color: #1e88e5; font-size: 12px">›</text>
            </view>
          </view>
        </view>

        <!-- 邀请指定零工 -->
        <view class="section-card">
          <view
            class="form-row"
            @click="navigateTo('invite-worker')"
            style="border-bottom: none"
          >
            <text style="font-size: 14px; color: #333">邀请指定零工接单</text>
            <view
              class="form-value"
              :class="{
                placeholder: selectedInviteCount === 0,
                selected: selectedInviteCount > 0,
              }"
              style="flex: 1; justify-content: flex-end"
            >
              <text>{{ selectedInviteCount > 0 ? `已选${selectedInviteCount}人` : "请选择" }}</text>
              <text class="› arrow"></text>
            </view>
          </view>
        </view>

        <!-- 零工意外保障 -->
        <view class="section-card" @click="navigateTo('insurance')">
          <view class="form-row" style="border-bottom: none">
            <view class="insurance-entry">
              <view class="insurance-entry-title">
                <text class="insurance-entry-icon">🛡</text>
                <text>零工意外保障</text>
              </view>
              <view class="insurance-entry-sub">
                <text>{{ insuranceSubtitle }}</text>
              </view>
            </view>
            <view
              class="form-value selected"
              style="flex: 1; justify-content: flex-end"
            >
              <text class="insurance-job-tag">{{ insurancePlan.job }}</text>
              <text class="› arrow"></text>
            </view>
          </view>
        </view>

        <!-- 招工设置 -->
        <view class="section-card" @click="navigateTo('recruit-settings')">
          <view class="single-row">
            <text class="label"
              >招工设置 <text style="color: #ff6b35">•</text></text
            >
            <view class="desc">
              <text>{{ settlementLabel }}、报名等设置</text>
              <text class="› arrow"></text>
            </view>
          </view>
        </view>

        <view class="scroll-bottom-space"></view>
      </scroll-view>

      <!-- 浮动客服 -->
      <view class="service-fab" @click="navigateTo('service-chat')">
        <text class="service-icon">◉</text>
        <text>客服</text>
      </view>

      <!-- 底部按钮 -->
      <view class="bottom-bar">
        <button class="submit-btn" @click="publishJob">发布招工</button>
      </view>
    </view>
  </view>
</template>

<script>
import {
  createOrder,
  getCurrentUser,
  getBossOrderTemplate,
  getOrder,
  getUser,
  inviteTalent,
  updateOrder,
} from "@/api/backend";
import { handleTokenInvalid } from "@/api/auth";
import { normalizeBossAddressSelection, validateBossWorkLocation } from "@/utils/boss-address";

function mergeTemplateSource(detail = {}, source = {}) {
  const merged = { ...source, ...detail };
  [
    "orderContent",
    "signMode",
    "phoneNotify",
    "signNotify",
    "startRemind",
    "settleNotify",
    "inviteWorkerIds",
    "invitedWorkerIds",
    "workerIds",
    "inviteWorkers",
    "invitedWorkers",
  ].forEach((key) => {
    if (detail[key] === undefined || detail[key] === null || detail[key] === "") {
      merged[key] = source[key];
    }
  });
  return merged;
}

function parseTemplateTaskContent(detail = {}) {
  const raw = String(detail.orderContent || "").trim();
  const separatorIndex = raw.indexOf(" - ");
  return {
    title: String(
      detail.workContent ||
        detail.taskTitle ||
        (separatorIndex >= 0 ? raw.slice(0, separatorIndex) : raw),
    ).trim(),
    desc: String(
      detail.taskDetail ||
        detail.contentDetail ||
        (separatorIndex >= 0 ? raw.slice(separatorIndex + 3) : raw),
    ).trim(),
  };
}

function normalizeSettlementType(value) {
  return {
    daily: "daily",
    DAY: "daily",
    DAILY: "daily",
    heldBack: "heldBack",
    PRESS: "heldBack",
    month: "month",
    MONTH: "month",
    MONTHLY: "month",
  }[value] || value;
}

function getSettlementLabel(value) {
  return {
    daily: "日结",
    heldBack: "压薪日结",
    month: "月结",
  }[normalizeSettlementType(value)] || "";
}

function extractInviteWorkerIds(detail = {}) {
  const source =
    detail.inviteWorkerIds ||
    detail.invitedWorkerIds ||
    detail.workerIds ||
    detail.inviteWorkers ||
    detail.invitedWorkers ||
    [];
  const rows = Array.isArray(source) ? source : String(source).split(",");
  return [...new Set(
    rows
      .map((item) => item?.workerId || item?.userId || item?.id || item)
      .map((id) => String(id || "").trim())
      .filter(Boolean),
  )];
}

export default {
  data() {
    return {
      statusBarHeight: 0,
      count: 1,
      payType: "hourly",
      hasSalary: false,
      salary: "",
      piecePrice: 0,
      pieceUnit: "件",
      estOutput: 0,
      publishing: false,
      jobName: "临时工岗位",
      settlementType: "daily",
      settlementLabel: "日结",
      recruitSettings: {
        signMode: "auto",
        phoneNotify: true,
        signNotify: true,
        startRemind: true,
        settleNotify: true,
      },
      hasExplicitType: false,
      orderId: "",
      sourceOrderId: "",
      templateId: "",
      selectedInviteCount: 0,
      insurancePlan: {
        enabled: true,
        amount: 33,
        points: 2,
        death: 30,
        medical: 3,
        job: "普工",
      },
      workTimeVersion: 0,
      contactPhone: "暂无手机号",
    };
  },
  computed: {
    insuranceSubtitle() {
      const plan = this.insurancePlan || {};
      if (plan.enabled === false) return "未开启保障";
      return `${plan.amount}万意外保障 · 保24小时、保开完工`;
    },
    workHours() {
      this.workTimeVersion;
      const workTime = uni.getStorageSync("workTimeSelection") || {};
      const durationMatch = String(workTime.duration || "").match(/[\d.]+/);
      if (durationMatch) return Number(durationMatch[0]);
      const start = String(workTime.startTime || "");
      const end = String(workTime.endTime || "");
      if (!/^\d{1,2}:\d{2}$/.test(start) || !/^\d{1,2}:\d{2}$/.test(end)) {
        return 0;
      }
      const [sh, sm] = start.split(":").map(Number);
      const [eh, em] = end.split(":").map(Number);
      let minutes = eh * 60 + em - (sh * 60 + sm);
      if (minutes <= 0) minutes += 24 * 60;
      return minutes / 60;
    },
  },
  async onLoad(options) {
    try {
      const info =
        typeof uni.getWindowInfo === "function"
          ? uni.getWindowInfo()
          : uni.getSystemInfoSync();
      this.statusBarHeight = Number(info.statusBarHeight || 0);
    } catch (_) {}
    uni.removeStorageSync("recruitDraftSettings");
    if (options?.job) this.jobName = decodeURIComponent(options.job);
    if (options?.id) this.orderId = options.id;
    this.sourceOrderId = options?.sourceOrderId || "";
    if (!this.orderId) {
      uni.removeStorageSync("pendingInviteWorkerIds");
      this.selectedInviteCount = 0;
    }
    if (options?.type) {
      this.hasExplicitType = true;
      this.applyRecruitSettings({ type: options.type });
    }
    this.templateId = options?.templateId || "";
    uni.$on("recruitSettingsSaved", this.applyRecruitSettings);
    uni.$on("inviteWorkersSelected", this.applyInviteSelection);
    uni.$on("insurancePlanSaved", this.applyInsurancePlan);
    this.loadRecruitSettings();
    this.loadInsurancePlan();
    if (this.templateId) await this.loadTemplateDraft(this.templateId);
    if (this.orderId) await this.loadOrder(this.orderId);
    if (this.sourceOrderId) await this.loadOrder(this.sourceOrderId);
  },
  onShow() {
    this.loadRecruitSettings();
    this.loadContactPhone();
    this.loadInviteSelection();
    this.loadInsurancePlan();
    this.workTimeVersion += 1;
  },
  onUnload() {
    uni.$off("recruitSettingsSaved", this.applyRecruitSettings);
    uni.$off("inviteWorkersSelected", this.applyInviteSelection);
    uni.$off("insurancePlanSaved", this.applyInsurancePlan);
    uni.removeStorageSync("recruitDraftSettings");
  },
  methods: {
    async loadTemplateDraft(templateId) {
      try {
        const cached = uni.getStorageSync("templatePublishDraft");
        let detail = (cached?.id || cached?.templateId) && String(cached.id || cached.templateId) === String(templateId)
          ? cached
          : await getBossOrderTemplate(templateId);
        if (!detail || typeof detail !== "object") return;
        const needsSourceOrder =
          !detail.orderContent ||
          !detail.signMode ||
          extractInviteWorkerIds(detail).length === 0 ||
          ["phoneNotify", "signNotify", "startRemind", "settleNotify"].some(
            (key) => typeof detail[key] !== "boolean",
          );
        if (needsSourceOrder && detail.sourceOrderId) {
          const source = await getOrder(detail.sourceOrderId).catch(() => null);
          if (source) detail = mergeTemplateSource(detail, source);
        }
        uni.setStorageSync("templatePublishDraft", detail);
        const taskContent = parseTemplateTaskContent(detail);
        if (taskContent.title || taskContent.desc) {
          uni.setStorageSync("taskContent", taskContent);
        }
        if (detail.orderNum != null) this.count = Math.max(1, Number(detail.orderNum));
        const tags = String(detail.tags || "");
        const hourly = tags.match(/时薪[:：]([\d.]+)/);
        const piece = tags.match(/计件单价[:：]([\d.]+)/);
        if (hourly) {
          this.payType = "hourly";
          this.salary = Number(hourly[1]);
          this.hasSalary = this.salary > 0;
        } else if (piece) {
          this.payType = "piece";
          this.piecePrice = Number(piece[1]);
          this.pieceUnit = String(tags.match(/计件单位[:：]([^,，]+)/)?.[1] || this.pieceUnit);
          this.estOutput = parsePieceOutput(detail, this.piecePrice);
          this.hasSalary = false;
        } else if (detail.salary != null) {
          const hours = Math.max(1, this.workHours || 8);
          this.payType = "hourly";
          this.salary = Number(detail.salary) / hours;
          this.hasSalary = this.salary > 0;
        }
        const templateSettings = {
          type: normalizeSettlementType(detail.type),
          settleMode: detail.settleMode || getSettlementLabel(detail.type),
          signMode: detail.signMode,
          phoneNotify: detail.phoneNotify,
          signNotify: detail.signNotify,
          startRemind: detail.startRemind,
          settleNotify: detail.settleNotify,
        };
        this.applyRecruitSettings(templateSettings);
        uni.setStorageSync("recruitDraftSettings", {
          ...(uni.getStorageSync("recruitDraftSettings") || {}),
          ...templateSettings,
        });
        const inviteWorkerIds = extractInviteWorkerIds(detail);
        uni.setStorageSync("pendingInviteWorkerIds", inviteWorkerIds);
        this.applyInviteSelection(inviteWorkerIds);
      } catch (error) {
        uni.showToast({ title: error?.message || "模板数据加载失败", icon: "none" });
      }
    },
    async loadContactPhone() {
      const cachedUser = uni.getStorageSync("userInfo") || {};
      this.contactPhone =
        cachedUser.phone || cachedUser.phoneNumber || "暂无手机号";
      try {
        const userId = uni.getStorageSync("userId");
        const [currentUser, user] = await Promise.all([
          getCurrentUser().catch(() => null),
          userId ? getUser(userId).catch(() => null) : Promise.resolve(null),
        ]);
        this.contactPhone =
          currentUser?.phone ||
          currentUser?.phoneNumber ||
          user?.phone ||
          user?.phoneNumber ||
          this.contactPhone;
      } catch (_) {}
    },
    goBack() {
      uni.navigateBack();
    },
    navigateTo(page) {
      const query =
        page === "invite-worker" && this.orderId
          ? `?orderId=${encodeURIComponent(this.orderId)}`
          : page === "insurance"
          ? `?job=${encodeURIComponent(this.jobName || "普工")}`
          : "";
      uni.navigateTo({ url: `/pages/boss/${page}${query}` });
    },
    applyInviteSelection(workerIds = []) {
      this.selectedInviteCount = Array.isArray(workerIds)
        ? workerIds.length
        : 0;
    },
    loadInviteSelection() {
      if (this.orderId) {
        this.selectedInviteCount = 0;
        return;
      }
      this.applyInviteSelection(
        uni.getStorageSync("pendingInviteWorkerIds") || [],
      );
    },
    async invitePendingWorkers(orderId) {
      const workerIds = uni.getStorageSync("pendingInviteWorkerIds");
      if (!Array.isArray(workerIds) || workerIds.length === 0) {
        return { invited: 0, failed: 0 };
      }
      const bossId = uni.getStorageSync("userId");
      const results = await Promise.allSettled(
        workerIds.map((workerId) =>
          inviteTalent({ bossId, workerId: Number(workerId), orderId }),
        ),
      );
      const failedIds = workerIds.filter(
        (_, index) => results[index].status === "rejected",
      );
      uni.removeStorageSync("pendingInviteWorkerIds");
      this.selectedInviteCount = 0;
      return {
        invited: workerIds.length - failedIds.length,
        failed: failedIds.length,
      };
    },
    loadInsurancePlan() {
      const saved = uni.getStorageSync("recruitInsurancePlan");
      if (saved && typeof saved === "object") {
        this.applyInsurancePlan(saved);
        return;
      }
      this.applyInsurancePlan({ job: this.jobName || "普工" });
    },
    applyInsurancePlan(plan = {}) {
      const current = this.insurancePlan || {};
      this.insurancePlan = {
        enabled: plan.enabled !== undefined ? !!plan.enabled : current.enabled,
        amount: Number(plan.amount) || current.amount || 33,
        points: Number(plan.points) || current.points || 2,
        death: Number(plan.death) || current.death || 30,
        medical: Number(plan.medical) || current.medical || 3,
        job: plan.job || current.job || "普工",
      };
    },
    loadRecruitSettings() {
      const saved =
        uni.getStorageSync("recruitDraftSettings") ||
        uni.getStorageSync("recruitSettings");
      if (saved && typeof saved === "object") {
        // 发布页 URL 中的 type 是当前岗位结算方式，不能覆盖本地保存的通知开关。
        this.applyRecruitSettings(
          this.hasExplicitType ? { ...saved, type: undefined } : saved,
        );
      }
    },
    applyRecruitSettings(data = {}) {
      const typeMap = {
        daily: "日结",
        heldBack: "压薪日结",
        month: "月结",
      };
      const settlementType = normalizeSettlementType(data.type);
      if (typeMap[settlementType]) {
        this.settlementType = settlementType;
        this.settlementLabel = data.settleMode || typeMap[settlementType];
      }
      const settings = this.recruitSettings || {};
      const signMode = String(data.signMode || "").toLowerCase();
      this.recruitSettings = {
        ...settings,
        ...(signMode === "auto" || signMode === "manual"
          ? { signMode }
          : {}),
        ...["phoneNotify", "signNotify", "startRemind", "settleNotify"]
          .filter((key) => typeof data[key] === "boolean")
          .reduce((result, key) => ({ ...result, [key]: data[key] }), {}),
      };
    },
    async loadOrder(id) {
      try {
        const detail = await getOrder(id);
        if (!detail || typeof detail !== "object") return;
        this.jobName = detail.orderTitle || detail.postion || this.jobName;
        this.count = Number(detail.orderNum || this.count);
        this.settlementType = detail.type || this.settlementType;
        // 编辑岗位时优先使用详情接口返回的通知配置，避免沿用其他岗位的本地缓存。
        this.applyRecruitSettings({ ...detail, type: this.settlementType });
        const settlementLabels = {
          daily: "日结",
          heldBack: "压薪日结",
          month: "月结",
        };
        uni.setStorageSync("recruitDraftSettings", {
          ...(uni.getStorageSync("recruitDraftSettings") || {}),
          type: normalizeSettlementType(this.settlementType),
          settleMode:
            detail.settleMode || settlementLabels[normalizeSettlementType(this.settlementType)],
          signMode: detail.signMode,
          phoneNotify: detail.phoneNotify,
          signNotify: detail.signNotify,
          startRemind: detail.startRemind,
          settleNotify: detail.settleNotify,
        });
        const hourly = String(detail.tags || "").match(/时薪:([\d.]+)/);
        const piece = String(detail.tags || "").match(/计件单价:([\d.]+)/);
        if (hourly) {
          this.payType = "hourly";
          this.salary = Number(hourly[1]);
          this.hasSalary = true;
        } else if (piece) {
          this.payType = "piece";
          this.piecePrice = Number(piece[1]);
          this.pieceUnit = String(String(detail.tags || "").match(/计件单位[:：]([^,，]+)/)?.[1] || this.pieceUnit);
          this.estOutput = parsePieceOutput(detail, this.piecePrice);
        }
      } catch (_) {}
    },
    changeCount(delta) {
      this.count = Math.max(1, Math.min(100, this.count + delta));
    },
    setSalary() {
      uni.showModal({
        title: "设置工价",
        editable: true,
        placeholderText: "请输入工价",
        success: (res) => {
          if (res.confirm && res.content) {
            const salary = parseFloat(res.content);
            if (Number.isFinite(salary) && salary > 0) {
              this.salary = salary;
              this.hasSalary = true;
            }
          }
        },
      });
    },
    setPiecePrice() {
      uni.showModal({
        title: "设置单价",
        editable: true,
        placeholderText: "请输入单价",
        success: (res) => {
          if (res.confirm && res.content)
            this.piecePrice = Number(res.content) || this.piecePrice;
        },
      });
    },
    setPieceUnit() {
      uni.showActionSheet({
        itemList: ["件", "个", "套", "箱", "包"],
        success: (res) => {
          this.pieceUnit = ["件", "个", "套", "箱", "包"][res.tapIndex];
        },
      });
    },
    setEstOutput() {
      uni.showModal({
        title: "预估日产量",
        editable: true,
        placeholderText: "请输入数量",
        success: (res) => {
          if (res.confirm && res.content)
            this.estOutput = Number(res.content) || 0;
        },
      });
    },
    async publishJob() {
      if (this.publishing) return;
      if (this.payType === "hourly" && !this.hasSalary) {
        uni.showToast({ title: "请设置工价", icon: "none" });
        return;
      }
      if (this.payType === "piece" && (!this.piecePrice || !this.estOutput)) {
        uni.showToast({ title: "请完善计件价格和日产量", icon: "none" });
        return;
      }
      this.publishing = true;
      try {
        const workTime = uni.getStorageSync("workTimeSelection") || {};
        const dateText =
          Array.isArray(workTime.selectedDates) && workTime.selectedDates.length
            ? workTime.selectedDates[0]
            : formatLocalDate(new Date());
        const workLocation = uni.getStorageSync("workLocationSelection") || {};
        const location = normalizeBossAddressSelection(workLocation);
        const locationValidation = validateBossWorkLocation(location);
        if (!locationValidation.valid) {
          uni.showToast({ title: locationValidation.message, icon: "none" });
          return;
        }
        const taskContent = uni.getStorageSync("taskContent") || {};
        const jobCategory = uni.getStorageSync("jobCategorySelection") || {};
        const genderAge = uni.getStorageSync("genderAgeSelection") || {};
        const durationMatch = String(workTime.duration || "").match(/[\d.]+/);
        const workHours = Math.max(1, Number(durationMatch?.[0] || 8));
        const workDays = Math.max(
          1,
          Number(workTime.selectedDates?.length || 1),
        );
        const duration = this.settlementType === "daily" ? workHours : workDays;
        const salary =
          this.payType === "hourly"
            ? Number(this.salary) * workHours
            : Number(this.piecePrice * this.estOutput);
        const payTags =
          this.payType === "hourly"
            ? `计时,时薪:${Number(this.salary)}`
            : `计件,计件单价:${Number(this.piecePrice)},计件单位:${this.pieceUnit},日产量:${Number(this.estOutput)}`;
        const payload = {
          orderTitle: this.jobName,
          type: this.settlementType,
          postion: this.jobName,
          industryId: jobCategory.industryId || undefined,
          enterpriseTypeIds: Array.isArray(jobCategory.enterpriseTypeIds) && jobCategory.enterpriseTypeIds.length
            ? jobCategory.enterpriseTypeIds.join(",")
            : undefined,
          jobIds: Array.isArray(jobCategory.jobIds) && jobCategory.jobIds.length
            ? jobCategory.jobIds.join(",")
            : undefined,
          orderNum: Number(this.count),
          duration,
          addressId: location.addressId || undefined,
          address: location.address,
          longitude: location.longitude,
          latitude: location.latitude,
          tags: payTags,
          imageKeys: Array.isArray(taskContent.images)
            ? taskContent.images.map((item) => item?.objectKey).filter(Boolean).join(",")
            : taskContent.imageKeys || "",
          videoKey: taskContent.video?.objectKey || taskContent.videoKey || "",
          startTime: `${dateText} ${workTime.startTime || "08:00"}:00`,
          endTime: `${dateText} ${workTime.endTime || "18:00"}:00`,
          salary,
          orderContent: composeTaskContent(taskContent),
          gender: genderAge.gender || "不限",
          experience: formatAgeRequirement(genderAge),
          orderRemark: [
            taskContent.benefits,
            taskContent.exp,
            taskContent.requirements,
          ]
            .filter(Boolean)
            .join("；"),
          // 招工设置必须随岗位新增/编辑一起提交，后端据此持久化并在详情中回显。
          signMode: this.recruitSettings.signMode,
          phoneNotify: this.recruitSettings.phoneNotify,
          signNotify: this.recruitSettings.signNotify,
          startRemind: this.recruitSettings.startRemind,
          settleNotify: this.recruitSettings.settleNotify,
        };
        const editing = Boolean(this.orderId);
        let invitationResult = { invited: 0, failed: 0 };
        if (editing) {
          await updateOrder(this.orderId, payload);
        } else {
          const createdOrder = await createOrder(payload);
          const createdOrderId = createdOrder?.id || createdOrder?.orderId;
          if (!createdOrderId) throw new Error("岗位创建成功但未返回岗位 ID");
          invitationResult = await this.invitePendingWorkers(createdOrderId);
        }
        uni.showToast({
          title: invitationResult.failed
            ? `岗位已发布，${invitationResult.failed}人邀请失败`
            : invitationResult.invited
              ? `发布成功，已邀请${invitationResult.invited}人`
              : "发布成功",
          icon: invitationResult.failed ? "none" : "success",
        });
        setTimeout(
          () =>
            editing
              ? uni.redirectTo({ url: "/pages/boss/order" })
              : uni.navigateTo({ url: "/pages/boss-publish/publish-success" }),
          800,
        );
      } catch (error) {
        if (error?.statusCode === 403) {
          uni.showToast({
            title: error.message || "发布受限，请联系平台处理",
            icon: "none",
          });
        } else {
          uni.showToast({ title: error.message || "发布失败", icon: "none" });
        }
      } finally {
        this.publishing = false;
      }
    },
  },
};

function composeTaskContent(data = {}) {
  const title = String(data.title || "").trim();
  const desc = String(data.desc || "").trim();
  if (title && desc && title !== desc) return `${title} - ${desc}`;
  return title || desc;
}

function parsePieceOutput(detail = {}, piecePrice = 0) {
  const tags = String(detail.tags || "");
  const tagged = tags.match(/日产量[:：]([\d.]+)/)?.[1];
  if (tagged) return Number(tagged) || 0;
  const explicit = detail.estimatedDailyOutput ?? detail.dailyOutput ?? detail.estOutput;
  if (explicit != null && explicit !== "") return Number(explicit) || 0;
  const salary = Number(detail.salary || 0);
  return piecePrice > 0 && salary > 0 ? Number((salary / piecePrice).toFixed(2)) : 0;
}

function formatAgeRequirement(selection = {}) {
  const ageMin = Number(selection.ageMin || 18);
  const rawAgeMax = selection.ageMax;
  const ageMax = rawAgeMax === "不限" || Number(rawAgeMax) >= 60
    ? "不限"
    : Number(rawAgeMax || 60);
  return `${ageMin}岁~${ageMax === "不限" ? "不限" : `${ageMax}岁`}`;
}

function formatLocalDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
</script>

<style lang="scss" scoped>

/* 零工意外保障入口 */
.insurance-entry { display: flex; flex-direction: column; gap: 6rpx; }
.insurance-entry-title { display: flex; align-items: center; gap: 12rpx; font-size: 28rpx; color: #333; }
.insurance-entry-icon { font-size: 30rpx; }
.insurance-entry-sub { font-size: 24rpx; color: #999; }
.insurance-job-tag { margin-right: 16rpx; padding: 6rpx 16rpx; border-radius: 8rpx; background: #fff3e0; color: #ff6b35; font-size: 22rpx; }
.container {
  width: 100%;
  height: 100vh;
  background: #fff8e6;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-bg {
  background: #fff;
  flex: 1;
  width: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  box-sizing: border-box;
  background: white;
  flex-shrink: 0;
}

.nav-back {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #333;
  font-size: 20px;
}

.nav-title {
  font-size: 17px;
  font-weight: 700;
  color: #333;
}

.nav-placeholder {
  width: 32px;
}

.stepper {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px 16px 16px;
  gap: 4px;
  flex-shrink: 0;
}

.step {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #999;
}

.step .dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #ddd;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 11px;
}

.step.active {
  color: #ff6b35;
  font-weight: 600;
}

.step.active .dot {
  background: #ff6b35;
}

.step-line {
  width: 40px;
  height: 2px;
  background: #ddd;
}

.step-line.active {
  background: #ff6b35;
}

.scroll-area {
  flex: 1;
  height: 0;
  overflow-y: auto;
  min-height: 0;
  width: 100%;
  box-sizing: border-box;
  background: #f5f5f5;
}

.scroll-bottom-space {
  height: 120px;
}

.section-card {
  background: white;
  display: block;
  height: auto;
  min-height: 0;
  width: auto;
  box-sizing: border-box;
  min-width: 0;
  margin: 12px;
  border-radius: 12px;
  padding: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.counter-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
}

.counter-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: #999;
}

.counter-btn.active {
  background: #ff6b35;
  color: white;
}

.counter-value {
  font-size: 32px;
  font-weight: 700;
  color: #333;
  min-width: 60px;
  text-align: center;
}

.counter-unit {
  font-size: 15px;
  color: #666;
  margin-left: 4px;
}

.tab-group {
  display: flex;
  gap: 0;
  background: #f5f5f5;
  border-radius: 8px;
  padding: 4px;
  margin-bottom: 12px;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 14px;
  color: #666;
  border-radius: 6px;
}

.tab-item.active {
  background: white;
  color: #ff6b35;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.form-row {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}

.form-label {
  font-size: 14px;
  color: #333;
  flex-shrink: 0;
  margin-right: 12px;
}

.form-row:last-child {
  border-bottom: none;
}

.form-value {
  flex: 1;
  font-size: 14px;
  color: #333;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

.form-value.placeholder {
  color: #bbb;
}

.form-value.selected {
  color: #ff6b35;
  font-weight: 500;
}

.form-value .arrow {
  color: #ccc;
  font-size: 12px;
}

.link-btn {
  color: #1e88e5;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 2px;
}

.salary-row {
  display: flex;
  align-items: flex-start;
  padding: 12px 0;
  gap: 12px;
}

.salary-label {
  font-size: 14px;
  color: #333;
  flex-shrink: 0;
  padding-top: 2px;
}

.salary-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.salary-value {
  font-size: 16px;
  font-weight: 700;
  color: #333;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}

.salary-unit {
  font-size: 13px;
  color: #666;
  font-weight: 400;
}

.salary-sub {
  font-size: 12px;
  color: #999;
}

.single-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}

.single-row .label {
  font-size: 14px;
  color: #333;
  display: flex;
  align-items: center;
  gap: 4px;
}

.single-row .desc {
  font-size: 13px;
  color: #999;
  display: flex;
  align-items: center;
  gap: 6px;
}

.single-row .desc .arrow {
  color: #ccc;
  font-size: 12px;
}

.service-fab {
  position: absolute;
  right: 16px;
  bottom: 80px;
  width: 48px;
  height: 48px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: #666;
  z-index: 10;
}

.service-icon {
  font-size: 18px;
  color: #ff6b35;
  margin-bottom: 2px;
}

.bottom-bar {
  background: white;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;
  padding-bottom: calc(12px + env(safe-area-inset-bottom));
  box-sizing: border-box;
  position: relative;
  z-index: 20;
}

.submit-btn {
  width: 100%;
  height: 48px;
  background: linear-gradient(135deg, #ffd700, #ffa500);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  padding: 0;
  line-height: normal;
  display: flex;
  align-items: center;
  justify-content: center;
}

.submit-btn::after {
  border: none;
}
</style>
