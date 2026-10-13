<template>
  <view class="container">
    <scroll-view scroll-y class="scroll-area">
      <view class="top-header-area">
        <view
          class="top-bar"
          :style="{
            paddingTop: `${statusBarHeight + 8}px`,
            paddingRight: `${menuSafeRight}px`,
          }"
        >
          <view class="brand-tag">快马日结</view>
        </view>
        <view class="hero-banner" @click="navigateTo('select-job')">
          <view class="hero-banner-icon">
            <image class="icon-svg" src="/static/icons/boss-home/trend.svg" mode="aspectFit" />
          </view>
          <text class="hero-banner-title"><text class="accent">旺季到</text> 工价涨</text>
          <text class="hero-banner-subtitle">两招教你快速招工！</text>
        </view>
      </view>

      <view class="employer-card">
        <view class="employer-card-deco" />
        <view class="employer-top">
          <view class="employer-location">
            <image class="inline-icon location-icon" src="/static/icons/boss-home/location.svg" mode="aspectFit" /><text>{{ city || "当前城市" }}</text>
          </view>
          <view
            class="employer-service"
            @click="navigateTo('service-chat')"
          >
            <image class="inline-icon service-icon" src="/static/icons/boss-home/headset.svg" mode="aspectFit" /><text>在线客服</text>
          </view>
        </view>
        <view class="employer-main">
          <view class="employer-stat">
            <text class="stat-line"
              >附近有<text class="employer-stat-num">{{ nearbyWorkers }}</text
              ><text class="employer-stat-text">位零工</text></text
            >
            <text class="employer-stat-sub">最快{{ fastestMinutes }}分钟内接单</text>
          </view>
        </view>
        <view class="employer-cta-wrap" @click="navigateTo('select-job')">
          <view class="employer-cta-btn">
            去发布招工
          </view>
        </view>
      </view>

      <view class="emp-tools-wrap">
        <view class="emp-tools-header"><view class="acct-current"><image v-if="account.avatar" class="acct-avatar account-avatar-image" :src="account.avatar" mode="aspectFill" /><view v-else class="acct-avatar">{{ (account.name || "账").slice(0, 1) }}</view><view class="acct-info"><text>{{ account.name || "当前账号" }}</text><text>{{ authorizationTypeText }}</text></view><image class="acct-swap-icon" src="/static/icons/boss-profile/exchange.svg" mode="aspectFit" /></view><view class="home-codes"><view @click.stop="navigateTo('recruit-set')"><text>开工码</text><b>{{ attendance.workCodeEnabled ? (attendance.workCode || "--") : "未开启" }}</b></view><view @click.stop="navigateTo('recruit-set')"><text>早退码</text><b class="warn">{{ attendance.leaveCodeEnabled ? (attendance.leaveCode || "--") : "未开启" }}</b></view></view></view>
        <view class="emp-tools-row"><view v-for="(item, index) in tools" :key="item.label" class="emp-tool-item" :class="{ active: index === 0 }" @click="navigateTo(item.page)"><view class="emp-tool-icon"><image :src="item.icon" mode="aspectFit"/></view><text class="emp-tool-label">{{ item.label }}</text></view></view>
        <view class="schedule-mini"><view class="schedule-tabs"><view v-for="day in schedule" :key="day.date" class="schedule-tab" :class="{active: selectedScheduleDate === day.date}" @click="selectSchedule(day)"><text>{{ day.label }}</text><small class="schedule-req">需求 {{ day.demand }}</small></view></view><view class="schedule-data"><view v-if="scheduleLoading" class="schedule-state">排班加载中...</view><template v-else><view class="schedule-stats"><view v-for="stat in selectedSchedule.stats" :key="stat.label" class="schedule-stat"><text :class="{highlight: stat.highlight}">{{ stat.value }}</text><small>{{ stat.label }}</small></view></view><view v-for="record in selectedSchedule.records" :key="record.id" class="schedule-record"><view class="schedule-record-icon"><image :src="record.icon" mode="aspectFit" /></view><view class="schedule-record-info"><text class="schedule-record-title">{{ record.title }}</text><text class="schedule-record-sub">{{ record.sub }}</text></view><text class="schedule-record-tag" :class="record.statusClass">{{ record.status }}</text></view><view v-if="!selectedSchedule.records.length" class="schedule-state">暂无排班记录</view></template></view></view>
      </view>

      <view class="scroll-bottom-space" />
    </scroll-view>

    <!-- 底部TabBar -->
    <view class="tab-bar">
      <view v-if="canAccess('HOME_VIEW')" class="tab-item active" @click="switchTab('home')">
        <view class="tab-icon-wrap"><image class="tab-svg" src="/static/icons/boss-tabbar/house-white.svg" mode="aspectFit" /></view>
        <text class="tab-label">首页</text>
      </view>
      <view v-if="canAccess('ORDER_VIEW')" class="tab-item" @click="switchTab('order')">
        <view class="tab-icon-wrap"><image class="tab-svg" src="/static/icons/boss-tabbar/calendar-check-gray.svg" mode="aspectFit" /></view>
        <text class="tab-label">招工订单</text>
      </view>
      <view v-if="canAccess('WORKBENCH_VIEW')" class="tab-item" @click="switchTab('workbench')">
        <view class="tab-icon-wrap"><image class="tab-svg" src="/static/icons/boss-tabbar/briefcase-gray.svg" mode="aspectFit" /></view>
        <text class="tab-label">工作台</text>
      </view>
      <view v-if="canAccess('MESSAGE_VIEW')" class="tab-item" @click="switchTab('message')">
        <view class="tab-icon-wrap"><image class="tab-svg" src="/static/icons/boss-tabbar/comment-dots-gray.svg" mode="aspectFit" /></view>
        <text class="tab-label">消息</text>
      </view>
      <view class="tab-item" @click="switchTab('profile')">
        <view class="tab-icon-wrap"><image class="tab-svg" src="/static/icons/boss-tabbar/face-smile-gray.svg" mode="aspectFit" /></view>
        <text class="tab-label">我的</text>
      </view>
    </view>
  </view>
</template>

<script>
import {
  getBossAttendanceCodes,
  getBossHomeOverview,
  getBossHomeSchedule,
  listBossOrderTemplates,
} from "@/api/backend";
import { handleTokenInvalid } from "@/api/auth";
import { hasEnterprisePermission } from "@/api/enterprise-context";
import squarePlusIcon from "/static/icons/boss-home/square-plus-orange.svg";
import fileLinesIcon from "/static/icons/boss-home/file-lines.svg";
import gearIcon from "/static/icons/boss-home/gear.svg";
import mapIcon from "/static/icons/boss-home/map.svg";
import circleQuestionIcon from "/static/icons/boss-home/circle-question.svg";
import industryIcon from "/static/icons/boss-home/industry.svg";

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

export default {
  data() {
    return {
      ...getSafeArea(),
      city: "",
      account: { name: "当前账号", avatar: "", authorizationType: "", workCode: "", leaveCode: "" },
      attendance: { workCodeEnabled: false, workCode: "", workCodeExpiresAt: "", leaveCodeEnabled: false, leaveCode: "", leaveCodeExpiresAt: "" },
      currentAccountId: null,
      nearbyWorkers: 0,
      fastestMinutes: 0,
      tools: [
        { label: "再来一单", page: "select-job", icon: squarePlusIcon },
        { label: "招工模版", page: "publish-template", icon: fileLinesIcon },
        { label: "招工设置", page: "recruit-set", icon: gearIcon },
        { label: "地址管理", page: "recruit-address", icon: mapIcon },
        { label: "帮助中心", page: "service-chat", icon: circleQuestionIcon }
      ],
      schedule: [],
      selectedScheduleDate: "",
      selectedSchedule: { stats: [{label:"接单",value:0},{label:"到达",value:0},{label:"开工",value:0},{label:"完工",value:0},{label:"结算",value:0,highlight:true}], records: [] },
      statsLoading: false,
      scheduleLoading: false,
      attendanceLoading: false,
      lastAttendanceLoadAt: 0,
      overviewLoading: false,
      templateChecking: false,
    };
  },
  onLoad() {
    this.schedule = this.buildScheduleDates();
    this.selectedScheduleDate = this.schedule.find((item) => item.key === "TODAY")?.date || "";
    uni.$on("recruitSettingsSaved", this.handleRecruitSettingsSaved);
    this.loadHomeOverview();
    this.loadAttendanceCodes();
  },
  onShow() {
    this.loadAttendanceCodes();
  },
  onUnload() {
    uni.$off("recruitSettingsSaved", this.handleRecruitSettingsSaved);
  },
  computed: {
    authorizationTypeText() {
      return { PERSONAL: "个人授权", ENTERPRISE: "企业授权" }[
        this.account.authorizationType
      ] || this.account.authorizationType || "";
    },
  },
  methods: {
    canAccess(code) { return hasEnterprisePermission(code); },
    handleRecruitSettingsSaved() {
      this.loadAttendanceCodes();
      this.loadHomeOverview();
    },
    async loadAttendanceCodes() {
      const now = Date.now();
      if (now - this.lastAttendanceLoadAt < 1000) return;
      if (this.attendanceLoading) return;
      this.attendanceLoading = true;
      this.lastAttendanceLoadAt = now;
      try {
        this.attendance = {
          ...this.attendance,
          ...(await getBossAttendanceCodes() || {}),
        };
      } catch (error) {
        this.handleAttendanceRequestError(error, "考勤码加载失败");
      } finally {
        this.attendanceLoading = false;
      }
    },
    handleAttendanceRequestError(error, fallback) {
      const status = Number(error?.code || error?.statusCode);
      if (status === 401) return handleTokenInvalid({ role: "boss" });
      uni.showToast({ title: error?.message || fallback, icon: "none" });
    },
    async loadHomeOverview() {
      if (this.overviewLoading) return;
      this.overviewLoading = true;
      this.statsLoading = true;
      try {
        // 首屏先不带定位立即加载主体数据；定位仅影响附近零工数，到货后后台静默补一次。
        const result = await getBossHomeOverview({
          accountId: this.currentAccountId || undefined,
        });
        this.city = result?.city || "";
        this.nearbyWorkers = Number(result?.nearbyWorkers || 0);
        this.fastestMinutes = Number(result?.fastestMinutes || 0);
        this.currentAccountId = result?.currentAccountId ?? null;
        this.account = { ...this.account, ...(result?.account || {}) };
        const scheduleDays = result?.scheduleDays || {};
        const dates = this.buildScheduleDates();
        this.schedule = dates.map((item) => ({
          ...item,
          demand: this.getScheduleDemand(scheduleDays, item),
        }));
        const today = this.schedule.find((item) => item.key === "TODAY") || this.schedule[0];
        if (today) {
          this.selectedScheduleDate = today.date;
          // 排班属于次级信息，独立加载，避免首页等待第二个接口。
          this.loadSchedule(today);
        }
        this.refreshNearbyInBackground();
      } catch (error) {
        this.handleRequestError(error, "首页数据加载失败");
      } finally {
        this.statsLoading = false;
        this.overviewLoading = false;
      }
    },
    async refreshNearbyInBackground() {
      const location = await this.getCurrentLocation(1000);
      if (!location || this.overviewLoading) return;
      try {
        const result = await getBossHomeOverview({
          ...location,
          accountId: this.currentAccountId || undefined,
        });
        if (result?.city) this.city = result.city;
        this.nearbyWorkers = Number(result?.nearbyWorkers || 0);
        this.fastestMinutes = Number(result?.fastestMinutes || 0);
      } catch (_) {
        // 定位增强信息失败不影响首页主体数据。
      }
    },

    getCurrentLocation(timeout = 1200) {
      return new Promise((resolve) => {
        let settled = false;
        const timer = setTimeout(() => finish(null), timeout);
        const finish = (value) => {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          resolve(value);
        };
        uni.getLocation({
          type: "gcj02",
          success: ({ longitude, latitude }) => finish({ longitude, latitude }),
          fail: () => finish(null),
        });
      });
    },
    buildScheduleDates() {
      const labels = [
        ["昨天", "YESTERDAY", -1],
        ["今天", "TODAY", 0],
        ["明天", "TOMORROW", 1],
        ["后天", "AFTER_TOMORROW", 2],
      ];
      const now = new Date();
      now.setHours(0, 0, 0, 0);
      return labels.map(([label, key, offset]) => {
        const date = new Date(now);
        date.setDate(date.getDate() + offset);
        return { label, key, date: this.formatDate(date), demand: 0 };
      });
    },
    formatDate(date) {
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    },
    getScheduleDemand(scheduleDays, day) {
      if (Array.isArray(scheduleDays)) {
        const matched = scheduleDays.find(
          (item) =>
            item?.date === day.date ||
            item?.label === day.label ||
            item?.key === day.key,
        );
        return Number(matched?.demand || 0);
      }
      return Number(
        scheduleDays?.[day.key] ??
          scheduleDays?.[day.key.toLowerCase()] ??
          scheduleDays?.[
            { YESTERDAY: "yesterday", TODAY: "today", TOMORROW: "tomorrow", AFTER_TOMORROW: "dayAfterTomorrow" }[day.key]
          ] ??
          scheduleDays?.[day.label] ??
          scheduleDays?.[day.date] ??
          0,
      );
    },
    async selectSchedule(day) {
      this.selectedScheduleDate = day.date;
      await this.loadSchedule(day);
    },
    async loadSchedule(day) {
      if (!day?.date || this.scheduleLoading) return;
      this.scheduleLoading = true;
      try {
        const result = await getBossHomeSchedule(day.date, this.currentAccountId);
        const stats = result?.stats || {};
        this.selectedSchedule = {
          stats: [
            { label: "接单", value: Number(stats.accepted || 0) },
            { label: "到达", value: Number(stats.arrived || 0) },
            { label: "开工", value: Number(stats.working || 0) },
            { label: "完工", value: Number(stats.finished || 0) },
            { label: "结算", value: Number(stats.settled || 0), highlight: true },
          ],
          records: this.normalizeScheduleRecords(result?.records),
        };
        day.demand = Number(result?.demand ?? day.demand ?? 0);
      } catch (error) {
        this.selectedSchedule = { stats: [{label:"接单",value:0},{label:"到达",value:0},{label:"开工",value:0},{label:"完工",value:0},{label:"结算",value:0,highlight:true}], records: [] };
        this.handleRequestError(error, "排班加载失败");
      } finally {
        this.scheduleLoading = false;
      }
    },
    normalizeScheduleRecords(records) {
      const list = Array.isArray(records) ? records : [];
      return list.map((record, index) => {
        const status = record.status || record.orderStatus || "待接单";
        const time =
          record.time ||
          record.workTime ||
          [record.startTime, record.endTime]
            .filter(Boolean)
            .map((value) => String(value).replace("T", " ").slice(11, 16))
            .join("-") ||
          "时间待定";
        return {
          id: record.id || record.orderId || index,
          title: record.title || record.orderTitle || record.postion || "招工岗位",
          sub:
            record.sub ||
            `招${record.orderNum || record.demand || 0}人 · ${time} · 已接单${record.accepted || 0}人`,
          status,
          statusClass:
            record.statusClass ||
            (["进行中", "招工中", "工作中"].includes(status)
              ? "doing"
              : "wait"),
          icon: record.icon || industryIcon,
        };
      });
    },
    handleRequestError(error, fallback) {
      const status = Number(error?.code || error?.statusCode);
      // 401 才是登录态失效；403 是业务权限/归属校验失败，应提示原因并保留当前登录。
      if (status === 401) return handleTokenInvalid({ role: "boss" });
      uni.showToast({ title: error?.message || fallback, icon: "none" });
    },
    async navigateTo(pageName) {
      if (pageName === "publish-template") {
        if (this.templateChecking) return;
        this.templateChecking = true;
        try {
          const result = await listBossOrderTemplates({ page: 0, size: 1 });
          const records = Array.isArray(result)
            ? result
            : result?.records || result?.content || result?.list || [];
          if (!records.length) {
            uni.navigateTo({ url: "/pages/boss/order" });
            return;
          }
        } catch (error) {
          this.handleRequestError(error, "模板加载失败");
          return;
        } finally {
          this.templateChecking = false;
        }
      }

      if (pageName === "select-job") {
        // 发布入口暂不校验认证，直接进入发布流程。
      }
      const bossPages = [
        "boss-employer",
        "boss-home",
        "boss-message",
        "boss-order",
        "boss-profile",
        "boss-publish",
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
        "recruit-settings",
        "recruit-set",
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
      ];
      let url = `/pages/boss/${pageName}`;
      if (!bossPages.includes(pageName)) {
        url = `/pages/${pageName}`;
      }
      uni.navigateTo({ url });
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
  },
};
</script>

<style lang="scss" scoped>
.container {
  box-sizing: border-box;
  width: 100%;
  height: 100vh;
  min-height: 100vh;
  background: #fff8e6;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: #333;
  font-family:
    -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", Arial,
    sans-serif;
}

.scroll-area {
  flex: 1;
  min-height: 0;
  background: #fff8e6;
}

.top-bar {
  box-sizing: border-box;
  min-height: 56px;
  display: flex;
  align-items: center;
  padding-left: 16px;
  padding-bottom: 12px;
}

.brand-tag {
  background: #fff;
  padding: 8px 16px;
  border-radius: 24px;
  font-weight: 700;
  font-size: 15px;
  color: #8b4513;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}

.hero-section {
  box-sizing: border-box;
  min-height: 142px;
  padding: 16px 16px 20px;
  position: relative;
}

.hero-title {
  display: block;
  font-size: 38px;
  font-weight: 800;
  line-height: 1.1;
  color: #8b4513;
}

.hero-subtitle {
  display: block;
  font-size: 24px;
  font-weight: 700;
  color: #d2691e;
  margin-top: 8px;
}

.hero-mascot {
  position: absolute;
  right: 10px;
  top: 30px;
  font-size: 108px;
  line-height: 1;
  opacity: 0.6;
  pointer-events: none;
}

.flow-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 16px;
  padding: 12px 16px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(139, 69, 19, 0.06);
}

.flow-item {
  color: #d2691e;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.flow-arrow {
  color: #ffb380;
  font-size: 20px;
  line-height: 1;
}

.info-bar {
  display: flex;
  padding: 16px;
  justify-content: space-between;
  font-size: 14px;
  color: #8b4513;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #8b4513;
}

.info-right {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #8b4513;
}

.info-icon {
  color: #ff6b35;
}

.info-highlight-bar {
  padding: 0 16px;
  margin-top: -8px;
  margin-bottom: 8px;
}

.nearby-summary {
  color: #8b4513;
  font-size: 14px;
}

.info-highlight {
  color: #ff6b35;
  font-weight: 600;
}

.publish-section {
  padding: 0 16px;
}

.section-title {
  display: block;
  font-size: 28px;
  font-weight: 800;
  color: #8b4513;
  margin-bottom: 6px;
  letter-spacing: 1px;
}

.section-subtitle {
  display: block;
  font-size: 15px;
  font-weight: 500;
  color: #ff6b35;
  margin-bottom: 16px;
  letter-spacing: 0.5px;
}

.publish-cards {
  display: flex;
  gap: 12px;
}

.publish-card {
  box-sizing: border-box;
  position: relative;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  padding: 16px;
  border-radius: 16px;
  color: #fff;
  text-align: center;
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  box-shadow: 0 6px 16px rgba(255, 107, 53, 0.2);
}

.publish-card:active {
  opacity: 0.88;
  transform: scale(0.98);
}

.publish-card.green {
  background: linear-gradient(135deg, #52c41a, #73d13d);
  box-shadow: 0 6px 16px rgba(82, 196, 26, 0.2);
}

.card-days {
  display: block;
  margin: 12px 0 4px;
  font-size: 36px;
  font-weight: 800;
}

.card-label {
  display: block;
  font-size: 14px;
  opacity: 0.9;
}

.card-btn {
  margin-top: 12px;
  padding: 10px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.25);
  font-size: 14px;
  font-weight: 600;
}

.peer-section {
  padding: 20px 16px;
}
.emp-tools-wrap { margin: 0 16px 12px; background:#fff; border-radius:14px; box-shadow:0 2px 10px rgba(0,0,0,.06); padding:12px; }
.emp-tools-header { display:flex; justify-content:space-between; align-items:center; }
.acct-current,.home-codes,.acct-info,.home-codes view { display:flex; align-items:center; }
.acct-current { gap:8px; }.acct-avatar { width:38px;height:38px;line-height:38px;text-align:center;border-radius:50%;background:#ff8b45;color:#fff; }.acct-info { flex-direction:column;align-items:flex-start;gap:2px;font-size:11px;color:#999; }.acct-info text:first-child { color:#333;font-size:14px;font-weight:700; }.acct-swap-icon { width:16px;height:16px;margin-left:4px; }.home-codes { gap:16px; }.home-codes view { flex-direction:column;gap:2px;color:#999;font-size:10px; }.home-codes b { color:#333;font-size:16px; }.home-codes .warn { color:#ff6b35;font-size:12px; }
.emp-tools-row { display:grid;grid-template-columns:repeat(5,1fr);gap:4px;margin-top:16px;border-top:1px solid #f5f5f5;padding-top:12px; }.emp-tool-item { display:flex;flex-direction:column;align-items:center;gap:5px;color:#666;font-size:11px; }.emp-tool-item image { width:22px;height:22px; }.schedule-mini { margin-top:14px;background:#fafafa;border-radius:10px;padding:10px; }.schedule-tabs { display:grid;grid-template-columns:repeat(4,1fr);gap:4px; }.schedule-tab { text-align:center;color:#999;font-size:12px;padding:5px 0; }.schedule-tab text,.schedule-tab small { display:block; }.schedule-tab small { font-size:10px;margin-top:3px; }.schedule-tab.active { color:#8b4513;font-weight:600;background:#ffe8b0;border:1.5px solid #ff9c4a;border-radius:8px; }.schedule-req { display:block;margin:3px auto 0;padding:1px 6px;border-radius:8px;background:#ff6b35;color:#fff;font-size:9px;width:max-content; }.schedule-data { margin-top:10px; }.schedule-stats { display:flex;background:#fff;border-radius:10px;padding:8px 4px;margin-bottom:8px; }.schedule-stat { flex:1;text-align:center; }.schedule-stat text,.schedule-stat small { display:block; }.schedule-stat text { color:#333;font-size:14px;font-weight:700; }.schedule-stat text.highlight { color:#ff6b35; }.schedule-stat small { margin-top:1px;color:#999;font-size:9px; }.schedule-record { display:flex;align-items:center;gap:8px;padding:7px 9px;margin-bottom:6px;border:1px solid #f2f2f2;border-radius:8px;background:#fafafa; }.schedule-record-icon { width:30px;height:30px;display:flex;align-items:center;justify-content:center;border-radius:8px;background:linear-gradient(135deg,#fff3d6,#ffe8b0); }.schedule-record-icon image { width:16px;height:16px; }.schedule-record-info { flex:1;min-width:0; }.schedule-record-title,.schedule-record-sub { display:block; }.schedule-record-title { color:#333;font-size:12px;font-weight:600; }.schedule-record-sub { margin-top:1px;color:#999;font-size:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis; }.schedule-record-tag { padding:2px 7px;border-radius:8px;font-size:9px;font-weight:600; }.schedule-record-tag.doing { background:#fff1e6;color:#ff6b35; }.schedule-record-tag.wait { background:#f2f3f5;color:#999; }

.peer-banner {
  box-sizing: border-box;
  background: linear-gradient(135deg, #ffe4c4 0%, #ffdab9 100%);
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 16px;
}

.peer-banner-content {
  position: relative;
  z-index: 1;
}

.peer-title {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: #8b4513;
  margin-bottom: 4px;
}

.peer-title-sub {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: #8b4513;
}

.peer-features {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  background: #f0f0f0;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 16px;
}

.peer-feature {
  box-sizing: border-box;
  min-width: 0;
  background: #fff;
  padding: 16px;
}

.peer-feature-icon {
  display: block;
  font-size: 20px;
  margin-bottom: 8px;
}

.peer-feature-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.peer-feature-desc {
  display: block;
  font-size: 12px;
  color: #666;
  line-height: 1.6;
}

.scroll-bottom-space {
  height: 16px;
}

.tab-bar {
  box-sizing: content-box;
  flex-shrink: 0;
  height: 63px;
  background: rgba(255, 255, 255, 0.98);
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

.tab-icon {
  font-size: 20px;
  line-height: 1;
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
  width: 32px;
  height: 32px;
  margin-bottom: 2px;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  box-shadow: 0 4px 10px rgba(255, 107, 53, 0.3);
}

/* 与老板端原型保持一致的首页视觉层 */
.container { background: #f7f7f7; color: #2c1810; }
.scroll-area { background: #f7f7f7; }
.top-header-area { padding: 8px 16px 14px; background: linear-gradient(180deg, #ffd8c4 0%, #ffe8d8 52%, #fff5ea 100%); }
.top-bar { min-height: 42px; padding: 0 0 10px 0; justify-content: space-between; }
.brand-tag { padding: 7px 15px; border-radius: 20px; background: linear-gradient(135deg,#ffe082,#ffd54f); color: #3e2723; font-size: 15px; font-weight: 800; letter-spacing: 1px; box-shadow: 0 2px 6px rgba(255,193,7,.25); }
.window-controls { display: flex; align-items: center; gap: 4px; padding: 3px 8px; border-radius: 999px; background: rgba(255,255,255,.75); box-shadow: 0 2px 6px rgba(0,0,0,.06); }
.window-btn { width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; color: #5d4037; font-size: 12px; }
.window-divider { width: 1px; height: 12px; background: rgba(93,64,55,.2); }
.hero-banner { position: relative; min-height: 76px; padding: 4px 4px 8px; }
.hero-banner-title { display: block; position: relative; z-index: 1; color: #2c1810; font-size: 24px; font-weight: 800; line-height: 1.2; }
.hero-banner-title .accent { color: #ff5722; }
.hero-banner-subtitle { display: block; position: relative; z-index: 1; margin-top: 6px; color: #5d4037; font-size: 14px; font-weight: 500; }
.hero-banner-icon { position: absolute; right: 18px; top: 50%; width: 54px; height: 54px; transform: translateY(-50%); display: flex; align-items: center; justify-content: center; border-radius: 16px; background: linear-gradient(135deg,#ff7043,#ff5722); color: #fff; font-size: 30px; font-weight: 700; box-shadow: 0 4px 12px rgba(255,87,34,.28); }
.icon-svg { width: 28px; height: 28px; }
.employer-card { position: relative; overflow: hidden; margin: 16px 16px 0; padding: 16px 18px 18px; border-radius: 18px; background: linear-gradient(135deg,#fff3c4,#ffe8a0); box-shadow: 0 4px 14px rgba(255,193,7,.18); }
.employer-card-deco { position: absolute; right: -30px; top: -30px; width: 100px; height: 100px; border-radius: 50%; background: rgba(255,255,255,.25); }
.employer-top,.employer-main,.employer-cta-wrap { position: relative; z-index: 1; }
.employer-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.employer-location { display: flex; align-items: center; gap: 6px; color: #5d4037; font-size: 14px; font-weight: 500; }
.location-icon,.service-icon { color: #ff7043; }
.inline-icon { width: 16px; height: 16px; flex: 0 0 16px; }
.employer-service { display: flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 20px; background: #fff; color: #5d4037; font-size: 12px; box-shadow: 0 2px 6px rgba(0,0,0,.05); }
.stat-line { display: block; color: #3e2723; font-size: 15px; font-weight: 600; }
.employer-stat-num { margin: 0 3px; color: #ff5722; font-size: 22px; font-weight: 800; }
.employer-stat-sub { display: block; margin-top: 4px; color: #8d6e63; font-size: 12px; }
.employer-cta-wrap { margin-top: 14px; }
.employer-cta-btn { padding: 14px; border: 2px solid #ff7043; border-radius: 7px; background: #fff; color: #000; text-align: center; font-size: 17px; font-weight: 700; letter-spacing: 4px; }
.peer-section { padding: 18px 16px 20px; }
.peer-features { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; background: transparent; }
.peer-feature { padding: 16px 14px; border: 1px solid rgba(255,112,67,.06); border-radius: 14px; background: #fff; box-shadow: 0 2px 10px rgba(44,24,16,.04); }
.peer-feature-icon { width: 36px; height: 36px; margin-bottom: 8px; display: flex; align-items: center; justify-content: center; border-radius: 10px; font-size: 18px; }
.peer-feature-icon.orange { background: #fff4e6; color: #ff7043; }.peer-feature-icon.green { background: #e8f5e9; color: #66bb6a; }.peer-feature-icon.blue { background: #e3f2fd; color: #42a5f5; }.peer-feature-icon.yellow { background: #fff8e1; color: #ffa726; }
.feature-svg { width: 20px; height: 20px; }
.peer-feature-title { display: block; margin-bottom: 6px; color: #2c1810; font-size: 14px; font-weight: 600; }
.peer-feature-desc { color: #6b4423; font-size: 12px; line-height: 1.6; opacity: .85; }
.bottom-slogan { padding: 24px 16px; text-align: center; }
.slogan-title { display: block; color: #ffe4cc; font-size: 32px; font-weight: 800; letter-spacing: 6px; }
.slogan-desc { display: block; margin-top: 8px; color: #d4b896; font-size: 13px; }
.tab-svg { width: 22px; height: 22px; opacity: .55; }
.tab-item.active .tab-svg { opacity: 1; }

/* 原型精确尺寸：业务区不包含浏览器手机壳层 */
.employer-card { background: #ffc95a; }
.emp-tools-wrap { margin: 12px 16px 10px; padding: 12px 14px; }
.emp-tools-header { margin-bottom: 12px; padding-bottom: 10px; border-bottom: 1px dashed #f0e6d2; font-size: 13px; }
.acct-avatar { width: 32px; height: 32px; line-height: 32px; font-size: 13px; }
.emp-tools-row { gap: 8px; margin-top: 0; margin-bottom: 12px; border-top: 0; padding-top: 0; }
.emp-tool-icon { height: 26px; margin-bottom: 5px; }
.emp-tool-item { font-size: 12px; }
.emp-tool-label { font-size: 12px; }
.schedule-mini { margin-top: 0; background: transparent; padding: 12px 0 0; border-top: 1px solid #f5f5f5; border-radius: 0; }
.schedule-tabs { display: flex; gap: 8px; }
.schedule-tab { flex: 1; padding: 7px 2px; background: #fff8e8; border: 1.5px solid transparent; border-radius: 10px; }
.schedule-tab.active { background: #ffe8b0; border-color: #ff9c4a; }
.schedule-tab small { font-size: 9px; }

/* 微信端文字层级按设计稿校准 */
.container {
  -webkit-text-size-adjust: 100%;
  text-size-adjust: 100%;
}
.acct-info { gap: 1px; font-size: 10px; line-height: 1.15; }
.acct-info text:first-child { font-size: 12px; font-weight: 600; line-height: 1.15; }
.acct-swap-icon { width: 13px; height: 13px; margin-left: 2px; }
.home-codes { gap: 8px; }
.home-codes view { min-width: 54px; padding: 4px 6px; border-radius: 8px; background: #fff8e8; font-size: 9px; line-height: 1.15; }
.home-codes b { margin-top: 2px; font-size: 13px; line-height: 1.1; color: #1890ff; }
.home-codes .warn { font-size: 10px; line-height: 1.25; color: #ffb020; }
.emp-tool-icon image { width: 20px; height: 20px; }
.emp-tool-label { color: #666; font-size: 10px; font-weight: 400; line-height: 1.2; white-space: nowrap; }
.emp-tool-item.active .emp-tool-label { color: #ff7a1a; }
.schedule-tab text { color: #8b4513; font-size: 11px; font-weight: 600; line-height: 1.2; }
.schedule-req { margin-top: 3px; padding: 1px 5px; font-size: 8px; font-weight: 600; line-height: 1.25; }
.schedule-data { margin-top: 7px; }
.schedule-stats { padding: 5px 4px; margin-bottom: 7px; }
.schedule-stat text { color: #333; font-size: 10px; font-weight: 700; line-height: 1.05; }
.schedule-stat small { margin-top: 3px; color: #2c1810; font-size: 12px; font-weight: 600; line-height: 1.1; }
.schedule-record { gap: 7px; padding: 6px 8px; margin-bottom: 5px; }
.schedule-record-icon { width: 28px; height: 28px; }
.schedule-record-icon image { width: 14px; height: 14px; }
.schedule-record-title { font-size: 11px; font-weight: 600; line-height: 1.2; }
.schedule-record-sub { margin-top: 2px; font-size: 9px; line-height: 1.2; }
.schedule-record-tag { padding: 2px 7px; font-size: 8px; line-height: 1.25; }

</style>
