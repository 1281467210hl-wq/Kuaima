<template>
  <view class="page">
    <view class="nav-header" :style="{ paddingTop: `${statusBarHeight + 4}px` }">
      <view class="nav-back" @click="goBack">‹</view>
      <view class="nav-center">
        <text class="nav-title">招工设置</text>
        <text class="nav-sub">发布招工将默认按如下设置</text>
      </view>
      <view class="nav-placeholder" />
    </view>

    <scroll-view scroll-y class="page-content">
      <view v-if="loading" class="state">设置加载中...</view>
      <template v-else>
        <text class="section-title">零工报名时</text>
        <view class="set-grid">
          <view v-for="item in signupSettings" :key="item.key" class="set-card" @click="openSetting(item.key)">
            <image class="set-icon" :src="item.icon" mode="aspectFit" />
            <text class="set-name">{{ item.name }}</text>
            <text class="set-desc">{{ item.desc }}</text>
            <text class="set-value" :class="{ active: item.active }">{{ item.value }}</text>
          </view>
        </view>

        <text class="section-title work-title">零工开工时</text>
        <view class="set-grid bottom-grid">
          <view v-for="item in workSettings" :key="item.key" class="set-card" @click="openSetting(item.key)">
            <image class="set-icon" :src="item.icon" mode="aspectFit" />
            <text class="set-name">{{ item.name }}</text>
            <text class="set-desc">{{ item.desc }}</text>
            <text class="set-value" :class="{ active: item.active }">{{ item.value }}</text>
          </view>
        </view>
      </template>
    </scroll-view>

    <view v-if="phoneSheetVisible" class="phone-sheet-mask" @click="closePhoneSheet" />
    <view v-if="phoneSheetVisible" class="phone-sheet">
      <view class="phone-sheet-head">
        <text class="phone-sheet-title">接听零工电话设置</text>
        <image class="phone-sheet-close" :src="icons.xmark" mode="aspectFit" @click="closePhoneSheet" />
      </view>
      <text class="phone-sheet-sub">是否需要员工电话沟通</text>
      <view
        v-for="(option, index) in phoneOptions"
        :key="option.value"
        class="phone-option"
        :class="{ selected: phoneDraftMode === option.value }"
        @click="phoneDraftMode = option.value"
      >
        <view class="phone-option-copy">
          <text class="phone-option-title">{{ option.title }}</text>
          <text class="phone-option-sub">{{ option.sub }}</text>
        </view>
        <view class="phone-radio" :class="{ checked: phoneDraftMode === option.value }">
          <image v-if="phoneDraftMode === option.value" class="phone-check" :src="icons.check" mode="aspectFit" />
        </view>
      </view>
      <button class="phone-confirm" :disabled="saving" @click="confirmPhoneSetting">确定</button>
    </view>

    <view v-if="settingSheet" class="phone-sheet-mask" @click="closeSettingSheet" />

      <view v-if="settingSheet === 'stop'" class="phone-sheet">
      <view class="phone-sheet-head"><text class="phone-sheet-title">停招时间</text><image class="phone-sheet-close" :src="icons.xmark" mode="aspectFit" @click="closeSettingSheet" /></view>
      <text class="phone-sheet-sub">设置自动暂停招人时间</text>
      <view class="phone-option compact" :class="{ selected: stopDraftType === 'start' }" @click="stopDraftType = 'start'"><text class="phone-option-title">开工时自动暂停</text><view class="phone-radio" :class="{ checked: stopDraftType === 'start' }"><image v-if="stopDraftType === 'start'" class="phone-check" :src="icons.check" mode="aspectFit" /></view></view>
      <view class="phone-option custom-stop" :class="{ selected: stopDraftType === 'custom' }" @click="stopDraftType = 'custom'">
        <view class="option-head"><text class="phone-option-title">自定义时间</text><view class="phone-radio" :class="{ checked: stopDraftType === 'custom' }"><image v-if="stopDraftType === 'custom'" class="phone-check" :src="icons.check" mode="aspectFit" /></view></view>
        <view v-if="stopDraftType === 'custom'" class="stop-picker" @click.stop>
          <view class="picker-column"><text :class="{ chosen: stopDraftDirection === 'before' }" @click="stopDraftDirection = 'before'">开工前</text><text :class="{ chosen: stopDraftDirection === 'after' }" @click="stopDraftDirection = 'after'">开工后</text></view>
          <text class="picker-separator">至</text>
          <scroll-view scroll-y class="hour-column"><text v-for="hour in 5" :key="hour" :class="{ chosen: stopDraftHour === hour }" @click="stopDraftHour = hour">{{ hour }}小时</text></scroll-view>
        </view>
      </view>
      <view class="phone-option compact" :class="{ selected: stopDraftType === 'none' }" @click="stopDraftType = 'none'"><text class="phone-option-title">不自动暂停，一直招人</text><view class="phone-radio" :class="{ checked: stopDraftType === 'none' }"><image v-if="stopDraftType === 'none'" class="phone-check" :src="icons.check" mode="aspectFit" /></view></view>
      <button class="phone-confirm" :disabled="saving" @click="confirmStopSetting">确定</button>
    </view>

      <view v-if="settingSheet === 'dnd'" class="phone-sheet">
      <view class="phone-sheet-head"><text class="phone-sheet-title">夜间免打扰</text><image class="phone-sheet-close" :src="icons.xmark" mode="aspectFit" @click="closeSettingSheet" /></view>
      <text class="phone-sheet-sub">设置零工禁止拨打电话时间</text>
      <view class="toggle-row"><text>免打扰</text><view class="setting-switch" :class="{ on: toggleDraft }" @click="toggleDraft = !toggleDraft"><view class="switch-dot" /></view></view>
      <text class="setting-tip">开启后招工备用联系人也会被禁止零工拨打电话</text>
      <button class="phone-confirm spacious" :disabled="saving" @click="confirmToggleSetting('dndEnabled')">确认</button>
    </view>

    <view v-if="settingSheet === 'startCode' || settingSheet === 'earlyCode'" class="phone-sheet code-sheet">
      <image class="phone-sheet-close floating-close" :src="icons.xmark" mode="aspectFit" @click="closeSettingSheet" />
      <view class="code-head"><view class="code-title-wrap"><image class="code-title-icon" :src="settingSheet === 'startCode' ? icons.flag : icons.running" mode="aspectFit" /><text>{{ settingSheet === 'startCode' ? '开工码' : '早退码' }}</text></view><view class="setting-switch" :class="{ on: toggleDraft }" @click="toggleDraft = !toggleDraft"><view class="switch-dot" /></view></view>
      <view class="code-tip"><text class="code-tip-title">{{ settingSheet === 'startCode' ? '可防止零工提前开工、欺诈打卡' : '可防止零工不打招呼早退跑路' }}</text><text class="code-tip-text">开启后，零工需输入老板端显示的{{ settingSheet === 'startCode' ? '开工码' : '早退码' }}，才能{{ settingSheet === 'startCode' ? '打卡开工' : '提前打卡完工' }}</text></view>
      <view class="code-demo"><view class="demo-card"><text>招工地址管理</text><small>工作地点与联系人</small></view><view class="demo-card accent"><text>首页验证码</text><small>开启后自动展示</small></view><view class="demo-badges"><view><small>开工码</small><b>----</b></view><view class="blue"><small>早退码</small><b>---</b></view></view></view>
      <button class="phone-confirm" :disabled="saving" @click="confirmToggleSetting(settingSheet === 'startCode' ? 'startCodeEnabled' : 'earlyCodeEnabled')">确定</button>
    </view>

      <view v-if="settingSheet === 'attendance'" class="phone-sheet">
      <view class="phone-sheet-head"><text class="phone-sheet-title">计薪工时</text><image class="phone-sheet-close" :src="icons.xmark" mode="aspectFit" @click="closeSettingSheet" /></view>
      <view v-for="option in attendanceOptions" :key="option.value" class="time-card" :class="{ selected: attendanceDraft === option.value }" @click="attendanceDraft = option.value">
        <view class="option-head"><text class="phone-option-title">{{ option.title }}</text><view class="phone-radio" :class="{ checked: attendanceDraft === option.value }"><image v-if="attendanceDraft === option.value" class="phone-check" :src="icons.check" mode="aspectFit" /></view></view>
        <view class="time-track"><view class="time-bar" :class="option.barClass">{{ option.hours }}</view></view>
        <view class="time-labels"><text><b>7:46</b><small>打卡</small></text><text><b>8:00</b><small>开工时间</small></text><text><b>20:00</b><small>完工时间</small></text><text><b>20:16</b><small>打卡</small></text></view>
      </view>
      <text class="setting-tip">您可在首页-招工设置、发布招工-零工接单设置里进行更改</text>
      <button class="phone-confirm" :disabled="saving" @click="confirmAttendanceSetting">确定</button>
    </view>

      <view v-if="settingSheet === 'face'" class="phone-sheet face-sheet">
      <view class="face-head"><view><text class="face-title">人脸打卡</text><text class="face-desc">零工打卡需拍照，验证人脸，确保本人干活</text></view><view class="setting-switch blue" :class="{ on: toggleDraft }" @click="toggleDraft = !toggleDraft"><view class="switch-dot" /></view></view>
      <view class="face-divider" /><text class="face-message">零工需要扫描人脸，才能开工</text>
      <view class="face-visual"><image :src="icons.face" mode="aspectFit" /><view class="scan-line" /></view>
      <button class="phone-confirm" :disabled="saving" @click="confirmToggleSetting('faceClockEnabled')">确定</button>
    </view>
  </view>
</template>

<script>
import {
  getBossRecruitSettings,
  listBossContacts,
  updateBossRecruitSettings,
} from "@/api/backend";
import { handleTokenInvalid } from "@/api/auth";
import phoneIcon from "/static/icons/boss-recruit-settings/phone.svg";
import mobileIcon from "/static/icons/boss-recruit-settings/mobile-screen.svg";
import userIcon from "/static/icons/boss-recruit-settings/circle-user.svg";
import bellSlashIcon from "/static/icons/boss-recruit-settings/bell-slash.svg";
import flagIcon from "/static/icons/boss-recruit-settings/flag.svg";
import runningIcon from "/static/icons/boss-recruit-settings/person-running.svg";
import stopwatchIcon from "/static/icons/boss-recruit-settings/stopwatch.svg";
import faceIcon from "/static/icons/boss-recruit-settings/face-smile.svg";
import xmarkIcon from "/static/icons/boss-recruit-settings/xmark-dark.svg";
import checkIcon from "/static/icons/boss-recruit-settings/check-white.svg";

export default {
  data() {
    return {
      statusBarHeight: 0,
      loading: false,
      saving: false,
      phoneSheetVisible: false,
      settingSheet: "",
      phoneDraftMode: "零工需打电话",
      stopDraftType: "custom",
      stopDraftDirection: "after",
      stopDraftHour: 3,
      toggleDraft: false,
      attendanceDraft: "按工作设定时间",
      phoneMode: "零工需打电话",
      backupPhone: "未设置",
      contactsLoaded: false,
      stopTime: "开工后3小时",
      dndEnabled: false,
      startCodeEnabled: false,
      earlyCodeEnabled: false,
      attendanceMode: "按工作设定时间",
      faceClockEnabled: true,
      settleMode: "日结",
      settleType: "daily",
      signMode: "auto",
      phoneNotify: true,
      signNotify: true,
      startRemind: true,
      settleNotify: true,
      icons: {
        phone: phoneIcon,
        mobile: mobileIcon,
        user: userIcon,
        bellSlash: bellSlashIcon,
        flag: flagIcon,
        running: runningIcon,
        stopwatch: stopwatchIcon,
        face: faceIcon,
        xmark: xmarkIcon,
        check: checkIcon,
      },
    };
  },
  computed: {
    phoneOptions() {
      return [
        { value: "零工必须打电话", title: "零工必须打电话，老板接通后，零工才可接单", sub: "必须电话面试" },
        { value: "零工无需打电话", title: "零工无需打电话，可直接接单", sub: "无需电话面试" },
        { value: "零工需打电话", title: "零工需打电话，不管打没打通，都可接单", sub: "不怕漏接电话错过零工，招人更快" },
      ];
    },
    attendanceOptions() {
      return [
        { value: "按实际打卡时间", title: "按零工打卡时间", hours: "计薪工时 12.5小时", barClass: "actual" },
        { value: "按工作设定时间", title: "按工作设定时间", hours: "计薪工时 12小时", barClass: "scheduled" },
      ];
    },
    signupSettings() {
      return [
        { key: "phone", icon: this.icons.phone, name: "是否接听电话", desc: "接单时是否需要电话沟通", value: this.phoneMode, active: true },
        { key: "contact", icon: this.icons.mobile, name: "备用联系人", desc: "零工电话报名、问路等", value: this.backupPhone, active: this.backupPhone !== "未设置" },
        { key: "stop", icon: this.icons.user, name: "自动停招时间", desc: "到时间自动暂停招人", value: this.stopTime, active: true },
        { key: "dnd", icon: this.icons.bellSlash, name: "夜间免打扰", desc: "禁止拨打电话时间", value: this.dndEnabled ? "已开启" : "未开启", active: this.dndEnabled },
      ];
    },
    workSettings() {
      return [
        { key: "startCode", icon: this.icons.flag, name: "开工码", desc: "防止提前开工、欺诈打卡", value: this.startCodeEnabled ? "已开启" : "未开启", active: this.startCodeEnabled },
        { key: "earlyCode", icon: this.icons.running, name: "早退码", desc: "防止不打招呼早退跑路", value: this.earlyCodeEnabled ? "已开启" : "未开启", active: this.earlyCodeEnabled },
        { key: "attendance", icon: this.icons.stopwatch, name: "考勤计时方式", desc: "选择报酬计算方式", value: this.attendanceMode, active: true },
        { key: "face", icon: this.icons.face, name: "人脸打卡", desc: "防止代打卡", value: this.faceClockEnabled ? "已开启" : "未开启", active: this.faceClockEnabled },
      ];
    },
  },
  async onLoad() {
    const info = typeof uni.getWindowInfo === "function" ? uni.getWindowInfo() : uni.getSystemInfoSync();
    this.statusBarHeight = Number(info.statusBarHeight || 0);
    await this.loadSettings();
    this.contactsLoaded = true;
  },
  onShow() {
    if (this.contactsLoaded) this.loadBackupContacts();
  },
  methods: {
    goBack() {
      uni.navigateBack();
    },
    applySettings(settings = {}) {
      ["phoneMode", "stopTime", "attendanceMode", "settleMode", "signMode"].forEach((key) => {
        if (settings[key] !== undefined && settings[key] !== null) this[key] = settings[key];
      });
      ["dndEnabled", "startCodeEnabled", "earlyCodeEnabled", "faceClockEnabled", "phoneNotify", "signNotify", "startRemind", "settleNotify"].forEach((key) => {
        if (typeof settings[key] === "boolean") this[key] = settings[key];
      });
      if (settings.type) this.settleType = settings.type;
    },
    async loadSettings() {
      this.loading = true;
      try {
        const [settings] = await Promise.all([
          getBossRecruitSettings(),
          this.loadBackupContacts(true),
        ]);
        this.applySettings(settings || {});
        uni.setStorageSync("recruitSettings", settings || {});
      } catch (error) {
        this.handleError(error, "招工设置加载失败");
      } finally {
        this.loading = false;
      }
    },
    async loadBackupContacts(silent = false) {
      try {
        const userId = uni.getStorageSync("userId");
        if (!userId) {
          this.backupPhone = "未设置";
          return;
        }
        const result = await listBossContacts(userId);
        const contacts = (Array.isArray(result) ? result : []).filter((item) => !item.isDefault);
        this.backupPhone = contacts[0]?.phone || "未设置";
      } catch (error) {
        if (!silent) this.handleError(error, "备用联系人加载失败");
      }
    },
    openSetting(key) {
      if (key === "phone") {
        this.phoneDraftMode = this.phoneMode;
        this.phoneSheetVisible = true;
        return;
      }
      if (key === "contact") {
        uni.navigateTo({ url: "/pages/boss/backup-phone" });
        return;
      }
      if (key === "stop") {
        this.initStopDraft();
        this.settingSheet = key;
        return;
      }
      if (key === "attendance") {
        this.attendanceDraft = this.attendanceMode;
        this.settingSheet = key;
        return;
      }
      const toggles = { dnd: "dndEnabled", startCode: "startCodeEnabled", earlyCode: "earlyCodeEnabled", face: "faceClockEnabled" };
      if (toggles[key]) {
        this.toggleDraft = this[toggles[key]];
        this.settingSheet = key;
      }
    },
    initStopDraft() {
      if (this.stopTime === "开工时" || this.stopTime === "开工时自动暂停") {
        this.stopDraftType = "start";
        return;
      }
      if (this.stopTime === "手动停招" || this.stopTime === "不自动暂停") {
        this.stopDraftType = "none";
        return;
      }
      const matched = String(this.stopTime).match(/开工(前|后)([1-5])小时/);
      this.stopDraftType = "custom";
      this.stopDraftDirection = matched?.[1] === "前" ? "before" : "after";
      this.stopDraftHour = Number(matched?.[2] || 3);
    },
    closePhoneSheet() {
      if (this.saving) return;
      this.phoneSheetVisible = false;
      this.phoneDraftMode = this.phoneMode;
    },
    async confirmPhoneSetting() {
      if (this.saving) return;
      const previousMode = this.phoneMode;
      this.phoneMode = this.phoneDraftMode;
      const saved = await this.saveSettings();
      if (saved) this.phoneSheetVisible = false;
      else this.phoneMode = previousMode;
    },
    closeSettingSheet() {
      if (this.saving) return;
      this.settingSheet = "";
    },
    async saveDraftField(field, value) {
      const previous = this[field];
      this[field] = value;
      const saved = await this.saveSettings();
      if (saved) this.settingSheet = "";
      else this[field] = previous;
    },
    confirmStopSetting() {
      const value = this.stopDraftType === "start"
        ? "开工时自动暂停"
        : this.stopDraftType === "none"
          ? "不自动暂停"
          : `开工${this.stopDraftDirection === "before" ? "前" : "后"}${this.stopDraftHour}小时`;
      return this.saveDraftField("stopTime", value);
    },
    confirmToggleSetting(field) {
      return this.saveDraftField(field, this.toggleDraft);
    },
    confirmAttendanceSetting() {
      return this.saveDraftField("attendanceMode", this.attendanceDraft);
    },
    async saveSettings() {
      if (this.saving) return false;
      const data = {
        phoneMode: this.phoneMode,
        backupPhone: this.backupPhone === "未设置" ? "" : this.backupPhone,
        stopTime: this.stopTime,
        dndEnabled: this.dndEnabled,
        startCodeEnabled: this.startCodeEnabled,
        earlyCodeEnabled: this.earlyCodeEnabled,
        attendanceMode: this.attendanceMode,
        faceClockEnabled: this.faceClockEnabled,
        settleMode: this.settleMode,
        type: this.settleType,
        signMode: this.signMode,
        phoneNotify: this.phoneNotify,
        signNotify: this.signNotify,
        startRemind: this.startRemind,
        settleNotify: this.settleNotify,
      };
      this.saving = true;
      try {
        const saved = await updateBossRecruitSettings(data);
        const result = saved || data;
        this.applySettings(result);
        uni.setStorageSync("recruitSettings", result);
        uni.$emit("recruitSettingsSaved", result);
        uni.showToast({ title: "设置已保存", icon: "success" });
        return true;
      } catch (error) {
        this.handleError(error, "设置保存失败");
        return false;
      } finally {
        this.saving = false;
      }
    },
    handleError(error, fallback) {
      const status = Number(error?.code || error?.statusCode);
      if (status === 401) return handleTokenInvalid({ role: "boss" });
      uni.showToast({ title: status === 403 ? "无权操作" : error?.message || fallback, icon: "none" });
    },
  },
};
</script>

<style lang="scss" scoped>
.page { width: 100%; height: 100vh; display: flex; flex-direction: column; overflow: hidden; background: #f5f6f8; color: #222; }
.nav-header { min-height: 59px; padding: 4px 16px; box-sizing: border-box; display: flex; align-items: center; position: relative; flex-shrink: 0; }
.nav-back, .nav-placeholder { width: 34px; height: 34px; display: flex; align-items: center; justify-content: center; font-size: 30px; }
.nav-placeholder { margin-left: auto; }
.nav-center { position: absolute; left: 50%; transform: translateX(-50%); text-align: center; display: flex; flex-direction: column; white-space: nowrap; }
.nav-title { font-size: 17px; font-weight: 700; }
.nav-sub { margin-top: 1px; color: #999; font-size: 11px; }
.page-content { flex: 1; min-height: 0; padding: 4px 16px calc(20px + env(safe-area-inset-bottom)); box-sizing: border-box; }
.state { padding: 100px 0; color: #999; text-align: center; }
.section-title { display: block; margin: 8px 2px; font-size: 14px; font-weight: 700; }
.work-title { margin-top: 18px; }
.set-grid { display: flex; flex-wrap: wrap; gap: 10px; }
.set-card { width: calc((100% - 10px) / 2); min-height: 139px; padding: 14px 10px 10px; box-sizing: border-box; border-radius: 14px; background: #fff; display: flex; flex-direction: column; align-items: center; text-align: center; }
.set-icon { width: 24px; height: 28px; margin-bottom: 6px; }
.set-name { font-size: 14px; font-weight: 700; }
.set-desc { margin: 3px 0 8px; color: #b0b0b0; font-size: 10px; line-height: 13px; }
.set-value { width: 100%; margin-top: auto; padding: 6px 4px; box-sizing: border-box; border-radius: 8px; background: #f5f6f8; color: #999; font-size: 12px; }
.set-value.active { color: #ff8c00; }
.bottom-grid { padding-bottom: 20px; }
.phone-sheet-mask { position: fixed; inset: 0; z-index: 60; background: rgba(0, 0, 0, .5); }
.phone-sheet { position: fixed; right: 0; bottom: 0; left: 0; z-index: 61; padding: 20px 16px calc(16px + env(safe-area-inset-bottom)); background: #f5f6f8; border-radius: 20px 20px 0 0; box-sizing: border-box; animation: phone-sheet-up .25s ease-out both; }
.phone-sheet-head { position: relative; text-align: center; }
.phone-sheet-title { color: #222; font-size: 17px; font-weight: 700; }
.phone-sheet-close { position: absolute; top: 1px; right: 2px; width: 18px; height: 18px; }
.phone-sheet-sub { display: block; margin: 4px 0 14px; color: #999; font-size: 12px; text-align: center; }
.phone-option { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 88px; margin-bottom: 12px; padding: 16px 14px; background: #fff; border: 1px solid transparent; border-radius: 12px; box-sizing: border-box; }
.phone-option.compact { min-height: 58px; }
.phone-option.custom-stop { display: block; }
.phone-option.selected { background: #fffdf4; border-color: #f0c94a; }
.option-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.phone-option-copy { flex: 1; min-width: 0; }
.phone-option-title { display: block; color: #222; font-size: 14px; font-weight: 700; line-height: 1.4; }
.phone-option-sub { display: block; margin-top: 3px; color: #999; font-size: 12px; }
.phone-radio { width: 22px; height: 22px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; background: #fff; border: 2px solid #ddd; border-radius: 50%; box-sizing: border-box; }
.phone-radio.checked { background: #ffd60a; border-color: #ffd60a; }
.phone-check { width: 11px; height: 11px; }
.phone-confirm { width: 100%; margin-top: 4px; padding: 13px; color: #222; font-size: 16px; font-weight: 700; line-height: 22px; background: #ffd60a; border: 0; border-radius: 12px; box-sizing: border-box; }
.phone-confirm::after { border: 0; }
.phone-confirm[disabled] { opacity: .65; }
.phone-confirm.spacious { margin-top: 24px; }
.stop-picker { display: flex; align-items: center; justify-content: center; gap: 18px; height: 116px; margin-top: 12px; padding: 8px 16px; background: #f5f6f8; border-radius: 10px; }
.picker-column { display: flex; flex-direction: column; gap: 8px; }
.picker-column text, .hour-column text { display: block; min-width: 68px; padding: 7px 10px; color: #999; font-size: 13px; text-align: center; border-radius: 7px; box-sizing: border-box; }
.picker-column text.chosen, .hour-column text.chosen { color: #ff8c00; font-weight: 700; background: #fff; }
.picker-separator { color: #666; font-size: 13px; }
.hour-column { width: 90px; height: 100px; }
.toggle-row { display: flex; align-items: center; justify-content: space-between; padding: 18px 14px; color: #222; font-size: 15px; font-weight: 700; background: #fff; border-radius: 12px; }
.setting-switch { position: relative; width: 44px; height: 24px; background: #ddd; border-radius: 14px; transition: background .2s; }
.setting-switch.on { background: #ffd60a; }
.setting-switch.blue.on { background: #1677ff; }
.switch-dot { position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; background: #fff; border-radius: 50%; box-shadow: 0 1px 4px rgba(0,0,0,.18); transition: transform .2s; }
.setting-switch.on .switch-dot { transform: translateX(20px); }
.setting-tip { display: block; margin-top: 10px; color: #999; font-size: 11px; line-height: 1.5; }
.code-sheet { overflow: hidden; }
.floating-close { top: 16px; right: 16px; }
.code-head { display: flex; align-items: center; justify-content: space-between; padding-right: 34px; }
.code-title-wrap { display: flex; align-items: center; gap: 8px; color: #222; font-size: 18px; font-weight: 700; }
.code-title-icon { width: 22px; height: 24px; }
.code-tip { margin-top: 16px; padding: 14px; background: #fff8dc; border-radius: 10px; }
.code-tip-title, .code-tip-text { display: block; }
.code-tip-title { color: #7a5700; font-size: 14px; font-weight: 700; }
.code-tip-text { margin-top: 5px; color: #98762a; font-size: 12px; line-height: 1.6; }
.code-demo { position: relative; min-height: 174px; margin-top: 14px; padding: 14px; background: linear-gradient(145deg,#fff,#eef3f8); border: 1px solid #e8ebef; border-radius: 12px; box-sizing: border-box; }
.demo-card { width: 46%; padding: 12px; color: #777; background: rgba(255,255,255,.8); border-radius: 9px; box-sizing: border-box; filter: blur(.3px); }
.demo-card.accent { position: absolute; top: 14px; right: 14px; color: #4382bd; }
.demo-card text, .demo-card small { display: block; }
.demo-card text { font-size: 12px; font-weight: 700; }.demo-card small { margin-top: 4px; font-size: 9px; }
.demo-badges { position: absolute; right: 18px; bottom: 22px; display: flex; gap: 8px; }
.demo-badges view { min-width: 62px; padding: 7px; color: #ff8c00; text-align: center; background: #fff8e6; border-radius: 8px; }.demo-badges view.blue { color: #1685d1; background: #edf8ff; }
.demo-badges small, .demo-badges b { display: block; }.demo-badges small { font-size: 9px; }.demo-badges b { margin-top: 2px; font-size: 15px; }
.time-card { margin-top: 12px; padding: 14px; background: #fff; border: 1px solid transparent; border-radius: 12px; }.time-card.selected { background: #fffdf4; border-color: #f0c94a; }
.time-track { position: relative; height: 28px; margin-top: 14px; background: #eee; border-radius: 14px; }
.time-bar { position: absolute; top: 0; height: 28px; color: #fff; font-size: 10px; line-height: 28px; text-align: center; background: linear-gradient(90deg,#ffc400,#ff8c00); border-radius: 14px; }.time-bar.actual { left: 10%; width: 82%; }.time-bar.scheduled { left: 19%; width: 62%; }
.time-labels { display: flex; justify-content: space-between; margin-top: 8px; }.time-labels text { display: flex; flex-direction: column; color: #777; font-size: 9px; text-align: center; }.time-labels b { color: #333; font-size: 10px; }.time-labels small { margin-top: 2px; }
.face-sheet { background: #fff; }
.face-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }.face-title, .face-desc { display: block; }.face-title { color: #222; font-size: 18px; font-weight: 700; }.face-desc { margin-top: 5px; color: #999; font-size: 11px; }
.face-divider { height: 1px; margin: 18px 0; background: #eee; }.face-message { display: block; margin-bottom: 8px; color: #222; font-size: 14px; font-weight: 700; }
.face-visual { position: relative; width: 72%; height: 170px; margin: 0 auto 18px; display: flex; align-items: center; justify-content: center; overflow: hidden; background: linear-gradient(145deg,#f7fbff,#e7f2ff); border-radius: 18px; }.face-visual image { width: 94px; height: 94px; opacity: .75; }.scan-line { position: absolute; left: 15%; right: 15%; top: 50%; height: 2px; background: #29a3ff; box-shadow: 0 0 12px #29a3ff; }
@keyframes phone-sheet-up { from { transform: translateY(100%); } to { transform: translateY(0); } }
</style>
