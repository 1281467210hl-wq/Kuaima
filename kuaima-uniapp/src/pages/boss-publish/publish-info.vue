<template>
  <view class="container">
    <view
      class="nav-bar"
      :style="{
        paddingTop: `${statusBarHeight}px`,
        height: `${50 + statusBarHeight}px`,
      }"
    >
      <button class="nav-back" @click="goBack">
        <image src="/static/icons/boss-points/arrow-left-dark.svg" mode="aspectFit" />
      </button>
      <text class="nav-title">发布招工</text>
      <view class="nav-placeholder" />
    </view>

    <view class="stepper">
      <view class="step-dot">
        <image src="/static/icons/boss-recruit-settings/check-white.svg" mode="aspectFit" />
      </view>
      <text class="step-label">基础信息</text>
      <view class="step-line" />
      <view class="step-dot-pending">
        <image src="/static/icons/boss-publish-info/circle-white.svg" mode="aspectFit" />
      </view>
      <text class="step-label-pending">招工需求</text>
    </view>

    <scroll-view scroll-y class="scroll-area">
      <view class="form-section">
        <view class="form-item" @click="editJob">
          <text class="form-label">工种</text>
          <text class="form-value" :class="{ placeholder: !jobValue }">{{ jobValue || "请选择" }}</text>
          <image class="form-arrow" src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
        </view>
        <view class="form-item" @click="openTaskSheet">
          <text class="form-label">任务详情</text>
          <text class="form-value" :class="{ placeholder: !workContent && !taskDetail }">{{ taskSummary }}</text>
          <image class="form-arrow" src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
        </view>
        <view v-if="taskDetail" class="task-detail-preview">
          <text class="task-detail-text">{{ taskDetail }}</text>
        </view>
        <view class="form-item" @click="openGenderSheet">
          <text class="form-label">性别年龄</text>
          <text class="form-value">{{ genderAgeValue }}</text>
          <image class="form-arrow" src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
        </view>
      </view>

      <view class="date-section">
        <text class="date-section-title">工作日期</text>
        <view class="date-options">
          <picker class="date-picker" mode="date" :value="startDate" @change="changeStartDate">
            <view class="date-field">
              <text class="date-field-label">开始日期</text>
              <view class="date-field-value">
                <text>{{ formatDateDisplay(startDate) }}</text>
                <image src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
              </view>
            </view>
          </picker>
          <image class="date-range-arrow" src="/static/icons/boss-publish-info/arrow-right-long-gray.svg" mode="aspectFit" />
          <picker class="date-picker" mode="date" :value="endDate" :start="startDate" @change="changeEndDate">
            <view class="date-field">
              <text class="date-field-label">结束日期</text>
              <view class="date-field-value">
                <text>{{ formatDateDisplay(endDate) }}</text>
                <image src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
              </view>
            </view>
          </picker>
        </view>
      </view>

      <view class="form-section">
        <view class="work-time-card" @click="openWorkTimeSheet">
          <view class="card-head">
            <text class="card-title">工作时间</text>
            <text class="card-edit">{{ workTimeValue ? "修改" : "设置" }} ›</text>
          </view>
          <view class="work-time-grid">
            <view class="work-time-column">
              <text class="work-time-label">开工时间</text>
              <text class="work-time-value">{{ workStartTime }}</text>
            </view>
            <view class="work-time-column duration-column">
              <view class="duration-title">
                <text>{{ workDuration }}</text>
                <image src="/static/icons/boss-home/circle-question.svg" mode="aspectFit" />
              </view>
              <text class="duration-subtitle">每日工作时长</text>
              <image class="duration-arrow" src="/static/icons/boss-publish-info/arrow-right-long-gray.svg" mode="aspectFit" />
            </view>
            <view class="work-time-column">
              <text class="work-time-label">收工时间</text>
              <text class="work-time-value">{{ workEndTime }}</text>
            </view>
          </view>
          <view v-if="workTimeTags.length" class="work-time-tags">
            <text v-for="tag in workTimeTags" :key="tag" class="work-time-tag">{{ tag }}</text>
          </view>
        </view>
      </view>

      <view class="form-section">
        <view class="location-card" @click="navigateTo('location')">
          <view class="card-head">
            <text class="card-title">干活地点</text>
            <text class="card-edit">{{ workLocationValue ? "修改" : "设置" }} ›</text>
          </view>
          <view v-if="workLocationValue" class="location-body">
            <view class="location-name">
              <image src="/static/icons/worker-job-detail/map-marker-alt-blue.svg" mode="aspectFit" />
              <text>{{ workLocationName || "工作地点" }}</text>
            </view>
            <text class="location-address">{{ workLocationAddress || workLocationValue }}</text>
          </view>
          <text v-else class="location-placeholder">请设置工作地点</text>
        </view>
      </view>

      <view class="scroll-bottom-space" />
    </scroll-view>

    <view class="service-fab" @click="navigateTo('service-chat')">
      <image src="/static/icons/boss-profile/headset.svg" mode="aspectFit" />
      <text>客服</text>
    </view>

    <view class="bottom-bar">
      <button class="submit-btn" @click="nextStep">
        下一步
      </button>
    </view>

    <view v-if="activeSheet === 'task'" class="sheet-mask" @click="closeSheet">
      <view class="sheet-panel task-sheet" @click.stop>
        <view class="sheet-header">
          <text class="sheet-title">任务要求</text>
          <button class="sheet-close" @click="closeSheet">×</button>
        </view>
        <scroll-view scroll-y class="task-sheet-body">
          <view class="sheet-card">
            <text class="task-field-label">招工标题</text>
            <view class="task-title-wrap">
              <input
                v-model="taskDraft.title"
                class="task-title-input"
                maxlength="20"
                placeholder="请输入招工标题"
              />
              <text v-if="taskDraft.title" class="task-title-clear" @click="taskDraft.title = ''">×</text>
            </view>
            <text class="task-title-count">{{ taskDraft.title.length }}/20</text>
            <text class="history-title">历史发布</text>
            <text class="history-tag" @click="useHistoryTitle">{{ historyTaskTitle }}</text>
          </view>

          <view class="sheet-card">
            <view class="task-label-row">
              <text class="task-field-label">详情描述</text>
              <text class="label-muted">(非必填)</text>
            </view>
            <textarea
              v-model="taskDraft.desc"
              class="task-desc-input"
              maxlength="200"
              placeholder="补充工作要求或详细内容，请勿填写手机号或微信号"
            />
            <view class="upload-row">
              <view :class="['upload-item', { disabled: mediaUploading }]" @click="uploadTaskVideo">
                <text :class="['upload-icon', { loading: mediaUploading === 'video' }]">▶</text>
                <text>{{ mediaUploading === "video" ? "上传中..." : taskDraft.video ? "重新上传" : "上传视频" }}</text>
              </view>
              <view :class="['upload-item', { disabled: mediaUploading }]" @click="uploadTaskPhoto">
                <text :class="['upload-icon', { loading: mediaUploading === 'image' }]">▧</text>
                <text>{{ mediaUploading === "image" ? "上传中..." : "上传照片" }}</text>
              </view>
            </view>
            <view v-if="taskDraft.video || taskDraft.images.length" class="media-preview-list">
              <view v-if="taskDraft.video" class="video-preview">
                <video
                  id="taskVideoPreview"
                  :src="taskDraft.video.url"
                  class="media-video"
                  controls
                  object-fit="contain"
                  @fullscreenchange="handleTaskVideoFullscreenChange"
                />
                <view class="media-play-overlay" @click="previewTaskVideo">
                  <text class="media-play-icon">▶</text>
                </view>
                <view class="media-remove" @click.stop="removeTaskVideo">×</view>
              </view>
              <view v-for="(image, index) in taskDraft.images" :key="image.objectKey || index" class="image-preview">
                <image
                  :src="image.url"
                  class="media-image"
                  mode="aspectFill"
                  @click="previewTaskImage(index)"
                />
                <view class="media-remove" @click.stop="removeTaskImage(index)">×</view>
              </view>
            </view>
            <view class="upload-hint">
              <text>上传有工作内容的视频或照片，</text>
              <text class="hint-highlight">增加50%的接单率</text>
            </view>
          </view>

          <view class="sheet-card">
            <view class="task-label-row">
              <text class="task-field-label">工作福利</text>
              <text class="label-muted">可多选，</text>
              <text class="label-orange">有福利岗位接单更快</text>
            </view>
            <view class="option-tags">
              <text
                v-for="tag in benefitOptions"
                :key="tag"
                class="option-tag"
                :class="{ selected: taskDraft.benefits.includes(tag) }"
                @click="toggleTaskTag('benefits', tag)"
              >{{ tag }}</text>
            </view>
          </view>

          <view class="sheet-card">
            <text class="task-field-label">经验要求</text>
            <view class="option-tags">
              <text
                v-for="tag in experienceOptions"
                :key="tag"
                class="option-tag"
                :class="{ selected: taskDraft.exp.includes(tag) }"
                @click="toggleTaskTag('exp', tag)"
              >{{ tag }}</text>
            </view>
          </view>

          <view class="sheet-card">
            <view class="task-label-row">
              <text class="task-field-label">工作要求</text>
              <text class="label-muted">可多选</text>
            </view>
            <view class="option-tags">
              <text
                v-for="tag in requirementOptions"
                :key="tag"
                class="option-tag"
                :class="{ selected: taskDraft.requirements.includes(tag) }"
                @click="toggleTaskTag('requirements', tag)"
              >{{ tag }}</text>
            </view>
          </view>
          <view class="task-sheet-space" />
        </scroll-view>
        <view class="sheet-action-bar">
          <button class="sheet-confirm" @click="saveTaskDetail">完成</button>
        </view>
      </view>
    </view>

    <view v-if="activeSheet === 'gender'" class="sheet-mask" @click="closeSheet">
      <view class="sheet-panel gender-sheet" @click.stop>
        <view class="sheet-header">
          <text class="sheet-title">性别年龄</text>
          <button class="sheet-close" @click="closeSheet">×</button>
        </view>
        <view class="gender-sheet-body">
          <view class="gender-options">
            <view
              v-for="gender in genderOptions"
              :key="gender"
              class="gender-option"
              :class="{ selected: genderDraft === gender }"
              @click="genderDraft = gender"
            >{{ gender }}</view>
          </view>
          <text class="age-range-text">{{ draftAgeText }}</text>
          <view
            id="genderAgeSlider"
            class="age-slider"
            @touchstart="startAgeDrag"
            @touchmove.stop.prevent="moveAgeDrag"
            @touchend="endAgeDrag"
          >
            <view class="age-track" />
            <view class="age-fill" :style="ageFillStyle" />
            <view class="age-handle" :style="minAgeHandleStyle"><text>≡</text></view>
            <view class="age-handle" :style="maxAgeHandleStyle"><text>≡</text></view>
          </view>
          <view class="age-labels">
            <text v-for="age in ageStops" :key="String(age)">{{ age }}</text>
          </view>
        </view>
        <view class="gender-action-bar">
          <button class="sheet-confirm" @click="saveGenderAge">确定</button>
        </view>
      </view>
    </view>

    <view v-if="activeSheet === 'workTime'" class="sheet-mask" @click="closeSheet">
      <view class="sheet-panel work-time-sheet" @click.stop>
        <view class="sheet-header">
          <text class="sheet-title">工作时间</text>
          <button class="sheet-close" @click="closeSheet">×</button>
        </view>
        <scroll-view scroll-y class="work-time-sheet-body">
          <view class="work-time-setting-card">
            <text class="work-setting-label">工作时间</text>
            <view class="work-time-pickers">
              <picker mode="time" :value="workTimeDraft.startTime" @change="changeDraftWorkTime('startTime', $event)">
                <view class="work-time-picker-value">
                  <text>{{ workTimeDraft.startTime }}</text>
                  <text class="picker-caption">开工</text>
                </view>
              </picker>
              <text class="work-time-separator">~</text>
              <picker mode="time" :value="workTimeDraft.endTime" @change="changeDraftWorkTime('endTime', $event)">
                <view class="work-time-picker-value">
                  <text>{{ workTimeDraft.endTime }}</text>
                  <text class="picker-caption">收工</text>
                </view>
              </picker>
              <image src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
            </view>
          </view>

          <view class="rest-mode-tabs">
            <view
              class="rest-mode-tab"
              :class="{ selected: workTimeDraft.mode === 'none' }"
              @click="setWorkRestMode('none')"
            >
              <text class="mode-radio">{{ workTimeDraft.mode === 'none' ? '●' : '○' }}</text>
              <text>无休息时间</text>
            </view>
            <view
              class="rest-mode-tab"
              :class="{ selected: workTimeDraft.mode === 'rest' }"
              @click="setWorkRestMode('rest')"
            >
              <text class="mode-radio">{{ workTimeDraft.mode === 'rest' ? '●' : '○' }}</text>
              <text>有休息时间</text>
            </view>
          </view>

          <view v-if="workTimeDraft.mode === 'rest'" class="rest-setting-panel">
            <view class="rest-setting-head">
              <view class="fixed-rest-option" @click="workTimeDraft.fixed = true">
                <text class="mode-radio">{{ workTimeDraft.fixed ? '●' : '○' }}</text>
                <text>固定休息时段</text>
              </view>
              <view class="checkin-setting">
                <text>休息打卡(打{{ draftCheckinCount }}次/天)</text>
                <switch
                  color="#2F88FF"
                  :checked="workTimeDraft.checkin"
                  @change="workTimeDraft.checkin = $event.detail.value"
                />
              </view>
            </view>

            <view v-if="workTimeDraft.fixed">
              <view
                v-for="(slot, index) in workTimeDraft.restSlots"
                :key="index"
                class="rest-slot-row"
              >
                <view class="rest-slot-name">
                  <text>休息时段{{ index + 1 }}</text>
                  <text v-if="index > 0" class="rest-slot-delete" @click="deleteRestSlot(index)">删除</text>
                </view>
                <view class="rest-slot-pickers">
                  <picker mode="time" :value="slot.start" @change="changeRestSlot(index, 'start', $event)">
                    <text>{{ slot.start }}</text>
                  </picker>
                  <text>~</text>
                  <picker mode="time" :value="slot.end" @change="changeRestSlot(index, 'end', $event)">
                    <text>{{ slot.end }}</text>
                  </picker>
                  <text class="rest-slot-duration">{{ formatMinutes(draftRestSlotMinutes(slot)) }}</text>
                  <image src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
                </view>
              </view>
              <view class="add-rest-slot" @click="addRestSlot">＋ 添加休息时段</view>
            </view>

            <view class="unfixed-rest-option" @click="workTimeDraft.fixed = false">
              <text class="mode-radio">{{ workTimeDraft.fixed ? '○' : '●' }}</text>
              <text>不固定休息时段</text>
            </view>
          </view>

          <view class="work-duration-summary">
            <view>
              <text>工作时长 </text>
              <text class="work-duration-value">{{ formatMinutes(draftWorkMinutes, true) }}</text>
            </view>
            <text class="work-duration-detail">
              {{ formatMinutes(draftTotalMinutes) }} (总时长) - {{ formatMinutes(draftRestMinutes, true) }} (休息时长)
            </text>
          </view>
        </scroll-view>
        <view class="work-time-action-bar">
          <text class="work-time-notice">平台打卡仅参考，请和零工现场核对工时结算（平台和零工无任何雇佣关系）</text>
          <button class="sheet-confirm" @click="saveWorkTime">确定</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import {
  getBossOrderTemplate,
  getOrder,
  listBossRecruitAddresses,
  uploadRecruitmentMedia,
} from "@/api/backend";
import { handleTokenInvalid } from "@/api/auth";
import { normalizeBossAddressSelection, readAddressCoordinates } from "@/utils/boss-address";

function buildDateOptions() {
  const weekdays = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
  const result = [];
  const today = new Date();
  const currentWeek = weekStart(today).getTime();
  for (let index = 0; index < 7; index += 1) {
    const date = new Date(today);
    date.setDate(today.getDate() + index);
    const weekLabel = `${weekStart(date).getTime() === currentWeek ? "本周" : "下周"}${weekdays[date.getDay()]}`;
    result.push({
      value: formatLocalDate(date),
      weekday: weekLabel,
      date: `${date.getMonth() + 1}月${date.getDate()}日`,
      selected: index < 2,
    });
  }
  return result;
}

function weekStart(value) {
  const date = new Date(value);
  const weekday = date.getDay() || 7;
  date.setHours(0, 0, 0, 0);
  date.setDate(date.getDate() - weekday + 1);
  return date;
}

function formatLocalDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function mergeTemplateSource(detail = {}, source = {}) {
  const merged = { ...source, ...detail };
  [
    "orderContent",
    "industryId",
    "enterpriseTypeIds",
    "jobIds",
    "jobCategoryId",
    "address",
    "addressId",
    "longitude",
    "latitude",
    "signMode",
    "phoneNotify",
    "signNotify",
    "startRemind",
    "settleNotify",
  ].forEach((key) => {
    if (detail[key] === undefined || detail[key] === null || detail[key] === "") {
      merged[key] = source[key];
    }
  });
  return merged;
}

function parseTemplateTaskContent(detail = {}) {
  const explicitTitle = detail.workContent || detail.taskTitle || detail.contentTitle;
  const explicitDesc = detail.taskDetail || detail.contentDetail;
  const raw = String(detail.orderContent || "").trim();
  const separatorIndex = raw.indexOf(" - ");
  const title = String(
    explicitTitle || (separatorIndex >= 0 ? raw.slice(0, separatorIndex) : raw),
  ).trim();
  const desc = String(
    explicitDesc || (separatorIndex >= 0 ? raw.slice(separatorIndex + 3) : raw),
  ).trim();
  return {
    title,
    desc,
    requirements: detail.requirements || detail.orderRemark || "",
    benefits: detail.benefits || "",
    exp: detail.exp || "",
    images: parseMediaItems(detail.imageKeys, detail.imageUrls),
    video: parseMediaItem(detail.videoKey, detail.videoUrl),
  };
}

function parseMediaItems(keys, urls) {
  const keyList = String(keys || "")
    .split(",")
    .map((key) => key.trim())
    .filter(Boolean);
  const urlList = Array.isArray(urls) ? urls : [];
  return keyList.map((objectKey, index) => ({
    objectKey,
    url: urlList[index] || objectKey,
  }));
}

function parseMediaItem(key, url) {
  const objectKey = String(key || "").trim();
  return objectKey ? { objectKey, url: url || objectKey } : null;
}

function getTemplateRecruitSettings(detail = {}) {
  const type = {
    DAY: "daily",
    DAILY: "daily",
    PRESS: "heldBack",
    MONTH: "month",
    MONTHLY: "month",
  }[detail.type] || detail.type;
  const signMode = String(detail.signMode || "").toLowerCase();
  return {
    type,
    settleMode: detail.settleMode,
    signMode: ["auto", "manual"].includes(signMode) ? signMode : undefined,
    phoneNotify: detail.phoneNotify,
    signNotify: detail.signNotify,
    startRemind: detail.startRemind,
    settleNotify: detail.settleNotify,
  };
}

function normalizeGender(value) {
  const gender = String(value || "").trim();
  if (!gender || ["不限", "男女不限", "男女均可", "性别不限"].includes(gender)) {
    return "不限";
  }
  if (["男", "男性", "男性优先", "男性优选", "仅限男性"].includes(gender)) {
    return "男性优选";
  }
  if (["女", "女性", "女性优先", "女性优选", "仅限女性"].includes(gender)) {
    return "女性优选";
  }
  return gender;
}

function parseGenderAge(detail = {}) {
  const requirement = String(
    detail.ageRequirement || detail.experience || "",
  ).trim();
  const ages = requirement.match(/\d+/g)?.map(Number) || [];
  const ageMin = Number(detail.ageMin ?? detail.minAge ?? ages[0] ?? 18);
  const rawAgeMax = detail.ageMax ?? detail.maxAge ?? ages[1];
  const ageMax =
    rawAgeMax === undefined ||
    rawAgeMax === null ||
    rawAgeMax === "" ||
    rawAgeMax === "不限" ||
    /不限/.test(requirement)
      ? "不限"
      : Number(rawAgeMax);
  const gender = normalizeGender(detail.gender);
  return {
    gender,
    ageMin: Number.isFinite(ageMin) ? ageMin : 18,
    ageMax: ageMax === "不限" || !Number.isFinite(ageMax) ? "不限" : ageMax,
  };
}

function formatGenderAge(selection = {}) {
  const gender = normalizeGender(selection.gender);
  const genderText = gender === "不限" ? "性别不限" : gender;
  const ageMin = Number(selection.ageMin || 18);
  const ageMax = selection.ageMax === "不限" ? "不限" : Number(selection.ageMax || 60);
  return `${genderText}、${ageMin}岁~${ageMax === "不限" || ageMax >= 60 ? "不限" : `${ageMax}岁`}`;
}

export default {
  data() {
    return {
      statusBarHeight: 0,
      jobValue: "",
      workContent: "",
      taskDetail: "",
      taskTags: [],
      genderAgeValue: "性别不限、18岁~不限",
      workLocationValue: "",
      workLocationName: "",
      workLocationAddress: "",
      workLocationAddressId: null,
      workLocationLongitude: null,
      workLocationLatitude: null,
      workTimeValue: "",
      workTimeData: {},
      jobName: "",
      primaryJobName: "",
      publishType: "",
      orderId: "",
      sourceOrderId: "",
      templateId: "",
      templateDraft: null,
      industryId: "",
      enterpriseTypeIds: [],
      jobIds: [],
      dates: buildDateOptions(),
      startDate: formatLocalDate(new Date()),
      endDate: formatLocalDate(new Date()),
      activeSheet: "",
      taskDraft: {
        title: "",
        desc: "",
        benefits: [],
        exp: [],
        requirements: [],
        images: [],
        video: null,
      },
      mediaUploading: "",
      videoFullscreen: false,
      benefitOptions: ["包工作餐", "中午包饭", "晚上包饭", "有空调", "有风扇", "室内工作"],
      experienceOptions: ["只要熟手", "欢迎新手"],
      requirementOptions: ["禁止吸烟", "认真负责", "手脚麻利", "提前到场", "不磨洋工", "禁穿凉鞋短裤"],
      genderOptions: ["不限", "男性优选", "女性优选"],
      ageStops: [18, 20, 30, 40, 50, "不限"],
      genderDraft: "不限",
      minAgeIndex: 0,
      maxAgeIndex: 5,
      activeAgeHandle: "",
      ageSliderRect: null,
      workTimeDraft: {
        startTime: "08:00",
        endTime: "18:00",
        mode: "rest",
        fixed: true,
        checkin: true,
        restSlots: [
          { start: "12:00", end: "13:00" },
        ],
      },
    };
  },
  computed: {
    taskSummary() {
      if (this.workContent) return this.workContent;
      if (this.taskDetail) return this.taskDetail;
      return `${this.primaryJobName || this.jobName || "当前工种"} 未填写详情`;
    },
    workStartTime() {
      return this.workTimeData.startTime || extractTime(this.workTimeValue) || "--:--";
    },
    workEndTime() {
      return this.workTimeData.endTime || extractEndTime(this.workTimeValue) || "--:--";
    },
    workDuration() {
      return formatWorkDuration(this.workTimeData, this.workStartTime, this.workEndTime);
    },
    workTimeTags() {
      const tags = [];
      const restCount = Number(
        this.workTimeData.restCount ?? this.workTimeData.restSlots?.length ?? 0,
      );
      const checkinCount = Number(
        this.workTimeData.checkinCount ?? this.workTimeData.checkInCount ?? 0,
      );
      if (restCount > 0) tags.push(`已设置${restCount}个休息时段`);
      else if (this.workTimeData.restText) tags.push(this.workTimeData.restText);
      if (checkinCount > 0) tags.push(`要求零工打卡${checkinCount}次/天`);
      else if (this.workTimeData.checkinRequired) tags.push("要求零工打卡");
      return tags;
    },
    historyTaskTitle() {
      return `${this.primaryJobName || this.jobName || "招工"}111`;
    },
    draftAgeText() {
      const min = this.ageStops[this.minAgeIndex];
      const max = this.ageStops[this.maxAgeIndex];
      return `${min}岁~${max === "不限" ? "不限" : `${max}岁`}`;
    },
    minAgePercent() {
      return (this.minAgeIndex / (this.ageStops.length - 1)) * 100;
    },
    maxAgePercent() {
      return (this.maxAgeIndex / (this.ageStops.length - 1)) * 100;
    },
    ageFillStyle() {
      return `left:${this.minAgePercent}%;width:${this.maxAgePercent - this.minAgePercent}%;`;
    },
    minAgeHandleStyle() {
      return `left:${this.minAgePercent}%;`;
    },
    maxAgeHandleStyle() {
      return `left:${this.maxAgePercent}%;`;
    },
    draftTotalMinutes() {
      return timeRangeMinutes(this.workTimeDraft.startTime, this.workTimeDraft.endTime);
    },
    draftRestMinutes() {
      if (this.workTimeDraft.mode !== "rest" || !this.workTimeDraft.fixed) return 0;
      const minutes = this.workTimeDraft.restSlots.reduce(
        (total, slot) => total + this.draftRestSlotMinutes(slot),
        0,
      );
      return Math.min(minutes, this.draftTotalMinutes);
    },
    draftWorkMinutes() {
      return Math.max(0, this.draftTotalMinutes - this.draftRestMinutes);
    },
    draftCheckinCount() {
      if (this.workTimeDraft.mode !== "rest" || !this.workTimeDraft.checkin) return 0;
      const restCount = this.workTimeDraft.fixed ? this.workTimeDraft.restSlots.length : 1;
      return 2 + restCount * 2;
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
    uni.$on("taskContentSaved", this.applyTaskContent);
    uni.$on("genderAgeSelected", this.applyGenderAge);
    uni.$on("workLocationSelected", this.applyWorkLocation);
    uni.$on("workTimeSelected", this.applyWorkTime);
    uni.$on("jobsSelected", this.applyJobsSelected);
    this.orderId = options?.id || "";
    this.sourceOrderId = options?.sourceOrderId || "";
    this.templateId = options?.templateId || "";
    if (this.orderId || this.sourceOrderId || this.templateId) {
      [
        "taskContent",
        "genderAgeSelection",
        "workLocationSelection",
        "workTimeSelection",
        "jobCategorySelection",
      ].forEach((key) => uni.removeStorageSync(key));
    } else {
      const saved = uni.getStorageSync("taskContent");
      if (saved) this.applyTaskContent(saved);
      const savedGenderAge = uni.getStorageSync("genderAgeSelection");
      if (savedGenderAge) this.applyGenderAge(savedGenderAge);
      const savedWorkLocation = uni.getStorageSync("workLocationSelection");
      if (savedWorkLocation) this.applyWorkLocation(savedWorkLocation);
      const savedWorkTime = uni.getStorageSync("workTimeSelection");
      if (savedWorkTime) this.applyWorkTime(savedWorkTime);
      if (savedWorkTime?.selectedDates?.length)
        this.applySelectedDates(savedWorkTime.selectedDates);
    }
    if (options?.job) {
      this.jobName = decodeURIComponent(options.job);
      this.jobValue = this.jobName;
      this.primaryJobName = this.jobName.split("、").filter(Boolean)[0] || "";
    }
    this.industryId = options?.industryId || "";
    this.enterpriseTypeIds = parseIdList(options?.enterpriseTypeIds);
    this.jobIds = parseIdList(options?.jobIds || options?.jobId);
    if (this.jobName || this.jobIds.length) this.saveJobCategorySelection();
    this.publishType = options?.type || "";
    if (this.templateId) await this.loadTemplate(this.templateId);
    if (this.orderId) await this.loadOrder(this.orderId);
    if (this.sourceOrderId) await this.loadOrder(this.sourceOrderId);
    this.loadCommonAddresses();
  },
  onUnload() {
    uni.$off("taskContentSaved", this.applyTaskContent);
    uni.$off("genderAgeSelected", this.applyGenderAge);
    uni.$off("workLocationSelected", this.applyWorkLocation);
    uni.$off("workTimeSelected", this.applyWorkTime);
    uni.$off("jobsSelected", this.applyJobsSelected);
  },
  onShow() {
    // 编辑已有订单时，岗位详情接口是唯一数据源，避免旧草稿覆盖接口回显。
    if (!this.orderId && !this.templateId && !this.sourceOrderId) {
      const saved = uni.getStorageSync("taskContent");
      if (saved) this.applyTaskContent(saved);
    }
    if (!this.templateId) {
      const savedGenderAge = uni.getStorageSync("genderAgeSelection");
      if (savedGenderAge) this.applyGenderAge(savedGenderAge);
      const savedWorkLocation = uni.getStorageSync("workLocationSelection");
      if (savedWorkLocation) this.applyWorkLocation(savedWorkLocation);
      const savedWorkTime = uni.getStorageSync("workTimeSelection");
      if (savedWorkTime) this.applyWorkTime(savedWorkTime);
      if (savedWorkTime?.selectedDates?.length)
        this.applySelectedDates(savedWorkTime.selectedDates);
    }
  },
  methods: {
    async loadCommonAddresses() {
      try {
        const result = await listBossRecruitAddresses();
        const rows = Array.isArray(result)
          ? result
          : result?.records || result?.content || [];
        uni.setStorageSync(
          "bossRecruitAddressOptions",
          rows.map(normalizeBossAddressSelection),
        );
      } catch (error) {
        uni.showToast({ title: error?.message || "常用地址加载失败", icon: "none" });
      }
    },
    openTaskSheet() {
      const saved = uni.getStorageSync("taskContent") || {};
      this.taskDraft = {
        title: saved.title || this.workContent || this.primaryJobName || this.jobName || "",
        desc: saved.desc || this.taskDetail || "",
        benefits: splitDisplayTags(saved.benefits || (this.taskTags.length ? "" : "室内工作")),
        exp: splitDisplayTags(saved.exp || (this.taskTags.length ? "" : "欢迎新手")),
        requirements: splitDisplayTags(saved.requirements),
        images: Array.isArray(saved.images)
          ? saved.images
          : parseMediaItems(saved.imageKeys, saved.imageUrls),
        video: saved.video || parseMediaItem(saved.videoKey, saved.videoUrl),
      };
      this.activeSheet = "task";
    },
    openGenderSheet() {
      const saved = uni.getStorageSync("genderAgeSelection") || {};
      const current = saved.display ? saved : parseGenderAge({
        gender: saved.gender,
        ageMin: saved.ageMin,
        ageMax: saved.ageMax,
        ageRequirement: this.genderAgeValue,
      });
      this.genderDraft = normalizeGender(current.gender);
      this.minAgeIndex = this.resolveAgeIndex(current.ageMin, false);
      this.maxAgeIndex = this.resolveAgeIndex(current.ageMax, true);
      if (this.minAgeIndex > this.maxAgeIndex) this.minAgeIndex = this.maxAgeIndex;
      this.activeSheet = "gender";
      this.$nextTick(this.measureAgeSlider);
    },
    openWorkTimeSheet() {
      const saved = uni.getStorageSync("workTimeSelection") || {};
      const rawSlots = saved.restSlots || saved.slots || [];
      const restSlots = Array.isArray(rawSlots)
        ? rawSlots.map((slot) => ({
          start: slot.start || slot.s || "12:00",
          end: slot.end || slot.e || "13:00",
        }))
        : [];
      this.workTimeDraft = {
        startTime: saved.startTime || saved.start || this.workStartTime.replace("--:--", "08:00"),
        endTime: saved.endTime || saved.end || this.workEndTime.replace("--:--", "18:00"),
        mode: normalizeWorkTimeMode(saved),
        fixed: saved.fixed !== false,
        checkin: saved.checkin ?? saved.checkinRequired ?? true,
        restSlots: restSlots.length ? restSlots : [{ start: "12:00", end: "13:00" }],
      };
      this.activeSheet = "workTime";
    },
    closeSheet() {
      this.activeSheet = "";
      this.activeAgeHandle = "";
    },
    useHistoryTitle() {
      this.taskDraft.title = this.historyTaskTitle.slice(0, 20);
    },
    toggleTaskTag(type, tag) {
      const values = this.taskDraft[type];
      if (type === "exp") {
        this.taskDraft.exp = values[0] === tag ? [] : [tag];
        return;
      }
      const index = values.indexOf(tag);
      if (index >= 0) values.splice(index, 1);
      else values.push(tag);
    },
    uploadTaskVideo() {
      if (this.mediaUploading) return;
      uni.chooseVideo({
        sourceType: ["album"],
        compressed: true,
        maxDuration: 60,
        success: async ({ tempFile, tempFilePath, duration, size }) => {
          const filePath = tempFilePath || tempFile?.path;
          if (!filePath) return;
          if (Number(duration) > 60) {
            uni.showToast({ title: "视频最长60秒", icon: "none" });
            return;
          }
          if (Number(size) > 100 * 1024 * 1024) {
            uni.showToast({ title: "视频不能超过100MB", icon: "none" });
            return;
          }
          this.mediaUploading = "video";
          try {
            const result = await uploadRecruitmentMedia(filePath, "video");
            this.taskDraft.video = {
              objectKey: result.objectKey,
              url: filePath || result.url,
            };
            uni.showToast({ title: "视频上传成功", icon: "success" });
          } catch (error) {
            uni.showToast({ title: error?.message || "视频上传失败", icon: "none" });
          } finally {
            this.mediaUploading = "";
          }
        },
      });
    },
    uploadTaskPhoto() {
      if (this.mediaUploading) return;
      const remaining = 9 - this.taskDraft.images.length;
      if (remaining <= 0) {
        uni.showToast({ title: "最多上传9张图片", icon: "none" });
        return;
      }
      uni.chooseImage({
        count: remaining,
        sizeType: ["compressed"],
        success: async ({ tempFiles, tempFilePaths }) => {
          const files = (tempFiles || [])
            .map((file, index) => ({
              file,
              filePath: tempFilePaths?.[index] || file?.path,
            }))
            .filter((item) => item.filePath);
          this.mediaUploading = "image";
          try {
            for (const item of files) {
              const result = await uploadRecruitmentMedia(item.filePath, "image");
              this.taskDraft.images.push({
                objectKey: result.objectKey,
                url: item.filePath || result.url,
              });
            }
            uni.showToast({ title: "图片上传成功", icon: "success" });
          } catch (error) {
            uni.showToast({ title: error?.message || "图片上传失败", icon: "none" });
          } finally {
            this.mediaUploading = "";
          }
        },
      });
    },
    previewTaskImage(index) {
      const urls = this.taskDraft.images.map((item) => item.url).filter(Boolean);
      const current = this.taskDraft.images[index]?.url;
      if (!current) return;
      uni.previewImage({ current, urls });
    },
    previewTaskVideo() {
      if (!this.taskDraft.video?.url) return;
      const context = uni.createVideoContext("taskVideoPreview", this);
      context.requestFullScreen(0);
      context.play();
    },
    handleTaskVideoFullscreenChange(event) {
      this.videoFullscreen = Boolean(event?.fullScreen);
      if (!this.videoFullscreen) {
        uni.createVideoContext("taskVideoPreview", this).pause();
      }
    },
    removeTaskVideo() {
      this.taskDraft.video = null;
    },
    removeTaskImage(index) {
      this.taskDraft.images.splice(index, 1);
    },
    saveTaskDetail() {
      const title = String(this.taskDraft.title || "").trim();
      if (!title) {
        uni.showToast({ title: "请填写招工标题", icon: "none" });
        return;
      }
      const data = {
        title,
        desc: String(this.taskDraft.desc || "").trim(),
        benefits: this.taskDraft.benefits.join("、"),
        exp: this.taskDraft.exp.join("、"),
        requirements: this.taskDraft.requirements.join("、"),
        images: this.taskDraft.images,
        video: this.taskDraft.video,
      };
      uni.setStorageSync("taskContent", data);
      this.applyTaskContent(data);
      uni.$emit("taskContentSaved", data);
      this.closeSheet();
    },
    resolveAgeIndex(value, isMax) {
      if (value === "不限" || value === undefined || value === null || value === "") {
        return isMax ? this.ageStops.length - 1 : 0;
      }
      const numeric = Number(value);
      let closest = 0;
      let distance = Infinity;
      this.ageStops.forEach((age, index) => {
        if (age === "不限") return;
        const nextDistance = Math.abs(Number(age) - numeric);
        if (nextDistance < distance) {
          distance = nextDistance;
          closest = index;
        }
      });
      return closest;
    },
    measureAgeSlider() {
      uni.createSelectorQuery()
        .in(this)
        .select("#genderAgeSlider")
        .boundingClientRect((rect) => {
          if (rect) this.ageSliderRect = rect;
        })
        .exec();
    },
    ageIndexFromTouch(event) {
      const touch = event.touches?.[0] || event.changedTouches?.[0];
      const rect = this.ageSliderRect;
      if (!touch || !rect?.width) return null;
      const ratio = Math.max(0, Math.min(1, (touch.clientX - rect.left) / rect.width));
      return Math.round(ratio * (this.ageStops.length - 1));
    },
    startAgeDrag(event) {
      if (!this.ageSliderRect) this.measureAgeSlider();
      const index = this.ageIndexFromTouch(event);
      if (index === null) return;
      this.activeAgeHandle = Math.abs(index - this.minAgeIndex) <= Math.abs(index - this.maxAgeIndex)
        ? "min"
        : "max";
      this.updateAgeIndex(index);
    },
    moveAgeDrag(event) {
      const index = this.ageIndexFromTouch(event);
      if (index !== null) this.updateAgeIndex(index);
    },
    endAgeDrag() {
      this.activeAgeHandle = "";
    },
    updateAgeIndex(index) {
      if (this.activeAgeHandle === "min") this.minAgeIndex = Math.min(index, this.maxAgeIndex);
      if (this.activeAgeHandle === "max") this.maxAgeIndex = Math.max(index, this.minAgeIndex);
    },
    saveGenderAge() {
      const ageMin = this.ageStops[this.minAgeIndex];
      const ageMax = this.ageStops[this.maxAgeIndex];
      const data = {
        gender: this.genderDraft,
        ageMin,
        ageMax,
        display: formatGenderAge({ gender: this.genderDraft, ageMin, ageMax }),
      };
      this.applyGenderAge(data);
      uni.$emit("genderAgeSelected", data);
      this.closeSheet();
    },
    changeDraftWorkTime(field, event) {
      this.workTimeDraft[field] = event.detail.value;
    },
    setWorkRestMode(mode) {
      this.workTimeDraft.mode = mode;
    },
    changeRestSlot(index, field, event) {
      this.workTimeDraft.restSlots[index][field] = event.detail.value;
    },
    addRestSlot() {
      if (this.workTimeDraft.restSlots.length >= 4) {
        uni.showToast({ title: "最多添加4个休息时段", icon: "none" });
        return;
      }
      const previous = this.workTimeDraft.restSlots[this.workTimeDraft.restSlots.length - 1];
      this.workTimeDraft.restSlots.push({
        start: previous?.end || "15:00",
        end: addMinutesToTime(previous?.end || "15:00", 30),
      });
    },
    deleteRestSlot(index) {
      this.workTimeDraft.restSlots.splice(index, 1);
    },
    draftRestSlotMinutes(slot) {
      return timeRangeMinutes(slot.start, slot.end, false);
    },
    formatMinutes(minutes, decimal = false) {
      return formatDurationMinutes(minutes, decimal);
    },
    saveWorkTime() {
      if (!this.draftTotalMinutes) {
        uni.showToast({ title: "请设置有效的工作时间", icon: "none" });
        return;
      }
      const selectedDates = buildDateRange(this.startDate, this.endDate);
      const restSlots = this.workTimeDraft.mode === "rest" && this.workTimeDraft.fixed
        ? this.workTimeDraft.restSlots.map((slot) => ({ ...slot }))
        : [];
      const data = {
        ...this.workTimeData,
        startTime: this.workTimeDraft.startTime,
        endTime: this.workTimeDraft.endTime,
        start: this.workTimeDraft.startTime,
        end: this.workTimeDraft.endTime,
        mode: this.workTimeDraft.mode,
        fixed: this.workTimeDraft.fixed,
        checkin: this.workTimeDraft.checkin,
        checkinRequired: this.workTimeDraft.mode === "rest" && this.workTimeDraft.checkin,
        checkinCount: this.draftCheckinCount,
        restSlots,
        slots: restSlots.map((slot) => ({ s: slot.start, e: slot.end })),
        restCount: restSlots.length,
        restMinutes: this.draftRestMinutes,
        duration: formatDurationMinutes(this.draftWorkMinutes, true),
        selectedSlot: `${this.workTimeDraft.startTime} - ${this.workTimeDraft.endTime}`,
        selectedDates,
        dateOptions: selectedDates.map((value) => ({
          value,
          weekday: formatWeekday(value),
          date: this.formatDateDisplay(value),
          selected: true,
        })),
        display: `${this.workTimeDraft.startTime} - ${this.workTimeDraft.endTime}`,
      };
      uni.setStorageSync("workTimeSelection", data);
      this.applyWorkTime(data);
      uni.$emit("workTimeSelected", data);
      this.closeSheet();
    },
    async loadTemplate(id) {
      try {
        let detail = await getBossOrderTemplate(id);
        if (!detail || typeof detail !== "object") return;
        const needsSourceOrder =
          !detail.orderContent ||
          !detail.signMode ||
          ["phoneNotify", "signNotify", "startRemind", "settleNotify"].some(
            (key) => typeof detail[key] !== "boolean",
          );
        if (needsSourceOrder && detail.sourceOrderId) {
          const source = await getOrder(detail.sourceOrderId).catch(() => null);
          if (source) detail = mergeTemplateSource(detail, source);
        }
        this.templateDraft = detail;
        this.jobName = detail.orderTitle || detail.postion || this.jobName;
        this.jobValue = this.jobName;
        this.primaryJobName = detail.postion || this.jobName.split("、")[0] || "";
        this.industryId = detail.industryId || "";
        this.enterpriseTypeIds = parseIdList(detail.enterpriseTypeIds);
        this.jobIds = parseIdList(detail.jobIds || detail.jobCategoryIds || detail.jobCategoryId);
        this.saveJobCategorySelection();
        const taskContent = parseTemplateTaskContent(detail);
        this.applyTaskContent(taskContent);
        uni.setStorageSync("taskContent", taskContent);
        const templateSettings = getTemplateRecruitSettings(detail);
        this.publishType = templateSettings.type || this.publishType;
        uni.setStorageSync("recruitDraftSettings", {
          ...(uni.getStorageSync("recruitDraftSettings") || {}),
          ...templateSettings,
        });
        if (detail.gender || detail.experience || detail.ageMin != null || detail.ageMax != null) {
          this.applyGenderAge(parseGenderAge(detail));
        }
        if (detail.address) {
          this.applyWorkLocation({
            addressId: detail.addressId,
            name: detail.addressName || detail.locationName || "工作地点",
            address: detail.address,
            longitude: detail.longitude ?? detail.lng,
            latitude: detail.latitude ?? detail.lat,
            display: detail.address,
          });
        }
        if (detail.startTime || detail.endTime) {
          const startDate = String(detail.startTime || detail.endTime).slice(0, 10);
          const time = {
            startTime: extractTime(detail.startTime) || "08:00",
            endTime: extractTime(detail.endTime) || "18:00",
            mode: normalizeWorkTimeMode(detail),
            fixed: true,
            checkin: true,
            restSlots: normalizeRestSlots(detail),
            display: formatWorkTime(detail.startTime, detail.endTime),
            selectedDates: startDate ? [startDate] : [],
          };
          this.applyWorkTime(time);
          uni.setStorageSync("workTimeSelection", time);
        }
        uni.setStorageSync("templatePublishDraft", detail);
      } catch (error) {
        uni.showToast({ title: error?.message || "模板加载失败", icon: "none" });
      }
    },
    async loadOrder(id) {
      try {
        const detail = await getOrder(id);
        if (!detail || typeof detail !== "object") return;
        this.jobName = detail.orderTitle || detail.postion || this.jobName;
        this.jobValue = this.jobName;
        this.primaryJobName = detail.postion || this.jobName.split("、")[0] || "";
        this.industryId = detail.industryId || this.industryId;
        this.enterpriseTypeIds = parseIdList(
          detail.enterpriseTypeIds || this.enterpriseTypeIds,
        );
        this.jobIds = parseIdList(
          detail.jobIds || detail.jobCategoryIds || detail.jobCategoryId || this.jobIds,
        );
        this.saveJobCategorySelection();
        const taskContent = parseTemplateTaskContent(detail);
        this.applyTaskContent(taskContent);
        if (detail.address) {
          this.applyWorkLocation({
            addressId: detail.addressId,
            name: detail.addressName || detail.locationName || "工作地点",
            address: detail.address,
            longitude: detail.longitude ?? detail.lng,
            latitude: detail.latitude ?? detail.lat,
            display: detail.address,
          });
        }
        this.applyGenderAge(parseGenderAge(detail));
        const orderStartDate = String(detail.startTime || "").slice(0, 10);
        const orderEndDate = String(detail.endTime || detail.startTime || "").slice(0, 10);
        if (orderStartDate) this.applyDateRange(orderStartDate, orderEndDate);
        this.publishType = detail.type || this.publishType;
        if (detail.orderContent) {
          uni.setStorageSync("taskContent", taskContent);
        }
        if (detail.address) {
        uni.setStorageSync("workLocationSelection", {
            addressId: detail.addressId,
            address: detail.address,
            longitude: detail.longitude ?? detail.lng,
            latitude: detail.latitude ?? detail.lat,
            display: detail.address,
          });
        }
        if (detail.startTime || detail.endTime) {
          const time = {
            startTime: extractTime(detail.startTime) || "08:00",
            endTime: extractTime(detail.endTime) || "18:00",
            mode: normalizeWorkTimeMode(detail),
            fixed: true,
            checkin: true,
            restSlots: normalizeRestSlots(detail),
            display: formatWorkTime(detail.startTime, detail.endTime),
          };
          this.applyWorkTime(time);
          uni.setStorageSync("workTimeSelection", time);
        }
      } catch (error) {
        uni.showToast({
          title: error.message || "岗位信息加载失败",
          icon: "none",
        });
      }
    },
    goBack() {
      uni.navigateBack();
    },
    navigateTo(page) {
      const contentOrderId = this.orderId || this.sourceOrderId;
      const contentParams = [];
      if (contentOrderId) contentParams.push(`id=${encodeURIComponent(contentOrderId)}`);
      if (page === "task-content" && this.templateId) {
        contentParams.push(`templateId=${encodeURIComponent(this.templateId)}`);
      }
      const query = contentParams.length ? `?${contentParams.join("&")}` : "";
      uni.navigateTo({ url: `/pages/boss/${page}${query}` });
    },
    editJob() {
      const query = this.orderId
        ? `?id=${encodeURIComponent(this.orderId)}`
        : "";
      uni.navigateTo({ url: `/pages/boss/all-jobs${query}` });
    },
    applyTaskContent(data = {}) {
      this.workContent = data.title || data.desc || this.workContent;
      this.taskDetail = data.desc || this.taskDetail;
      this.taskTags = uniqueTags([
        ...splitDisplayTags(data.requirements),
        ...splitDisplayTags(data.benefits),
        ...splitDisplayTags(data.exp),
      ]);
    },
    applyJobsSelected(data = {}) {
      const names = Array.isArray(data.jobs) ? data.jobs.filter(Boolean) : [];
      if (names.length) {
        this.jobName = names.join("、");
        this.jobValue = this.jobName;
        this.primaryJobName = names[0];
      }
      this.industryId = data.industryId || this.industryId;
      this.enterpriseTypeIds = parseIdList(data.enterpriseTypeIds);
      this.jobIds = parseIdList(data.jobIds);
      this.saveJobCategorySelection(data);
    },
    saveJobCategorySelection(extra = {}) {
      const previous = uni.getStorageSync("jobCategorySelection") || {};
      uni.setStorageSync("jobCategorySelection", {
        ...previous,
        ...extra,
        industryId: this.industryId || extra.industryId || "",
        enterpriseTypeIds: [...this.enterpriseTypeIds],
        jobIds: [...this.jobIds],
        jobs: this.jobName ? this.jobName.split("、").filter(Boolean) : [],
      });
    },
    applyGenderAge(data = {}) {
      const selection = {
        gender: normalizeGender(data.gender),
        ageMin: Number(data.ageMin || 18),
        ageMax:
          data.ageMax === "不限" || Number(data.ageMax) >= 60
            ? "不限"
            : Number(data.ageMax || 60),
      };
      this.genderAgeValue = data.display || formatGenderAge(selection);
      uni.setStorageSync("genderAgeSelection", {
        ...selection,
        display: this.genderAgeValue,
      });
    },
    applyWorkLocation(data = {}) {
      const normalized = normalizeBossAddressSelection(data);
      const name = String(normalized.name || data.locationName || "").trim();
      const address = String(normalized.address || data.detail || "").trim();
      const display = String(data.display || [name, address].filter(Boolean).join(" ")).trim();
      if (display) this.workLocationValue = display;
      this.workLocationName = name || this.workLocationName;
      this.workLocationAddress = address || display || this.workLocationAddress;
      this.workLocationAddressId = normalized.addressId;
      this.workLocationLongitude = normalized.longitude;
      this.workLocationLatitude = normalized.latitude;
      uni.setStorageSync("workLocationSelection", {
        ...normalized,
        name,
        address,
        display,
      });
    },
    applyWorkTime(data = {}) {
      this.workTimeData = { ...this.workTimeData, ...data };
      if (data.display) this.workTimeValue = data.display;
      if (Array.isArray(data.selectedDates))
        this.applySelectedDates(data.selectedDates);
    },
    applySelectedDates(values = []) {
      const normalized = values.map(String).filter(Boolean).sort();
      if (normalized.length) {
        this.startDate = normalized[0];
        this.endDate = normalized[normalized.length - 1];
      }
      const selected = new Set(normalized);
      this.dates.forEach((item) => {
        item.selected =
          selected.has(String(item.value)) || selected.has(String(item.date));
      });
      this.persistDateRange();
    },
    applyDateRange(start, end = start) {
      this.startDate = start;
      this.endDate = end < start ? start : end;
      this.persistDateRange();
    },
    changeStartDate(event) {
      const value = event.detail.value;
      this.startDate = value;
      if (this.endDate < value) this.endDate = value;
      this.persistDateRange();
    },
    changeEndDate(event) {
      this.endDate = event.detail.value < this.startDate
        ? this.startDate
        : event.detail.value;
      this.persistDateRange();
    },
    persistDateRange() {
      const selectedDates = buildDateRange(this.startDate, this.endDate);
      const workTime = uni.getStorageSync("workTimeSelection") || {};
      const data = { ...workTime, selectedDates };
      this.workTimeData = { ...this.workTimeData, ...data };
      uni.setStorageSync("workTimeSelection", data);
    },
    formatDateDisplay(value) {
      const date = parseLocalDate(value);
      return `${date.getMonth() + 1}月${date.getDate()}日`;
    },
    nextStep() {
      if (!this.jobName) {
        uni.showToast({ title: "请先选择工种", icon: "none" });
        return;
      }
      if (!this.workContent) {
        uni.showToast({ title: "请先填写任务详情", icon: "none" });
        return;
      }
      if (!this.workLocationValue) {
        uni.showToast({ title: "请先选择工作地点", icon: "none" });
        return;
      }
      const settings = uni.getStorageSync("recruitSettings") || {};
      const type = this.publishType || settings.type || "daily";
      const categoryQuery = [
        this.industryId ? `&industryId=${encodeURIComponent(this.industryId)}` : "",
        this.enterpriseTypeIds.length
          ? `&enterpriseTypeIds=${encodeURIComponent(this.enterpriseTypeIds.join(","))}`
          : "",
        this.jobIds.length
          ? `&jobIds=${encodeURIComponent(this.jobIds.join(","))}`
          : "",
      ].join("");
      uni.redirectTo({
        url: `/pages/boss-publish/recruit-demand?job=${encodeURIComponent(this.primaryJobName || this.jobName.split("、")[0])}&type=${encodeURIComponent(type)}${this.orderId ? `&id=${encodeURIComponent(this.orderId)}` : ""}${this.sourceOrderId ? `&sourceOrderId=${encodeURIComponent(this.sourceOrderId)}` : ""}${this.templateId ? `&templateId=${encodeURIComponent(this.templateId)}` : ""}${categoryQuery}`,
      });
    },
  },
};

function extractTime(value) {
  const match = String(value || "").match(/(?:T|\s)(\d{1,2}:\d{2})/);
  if (match) return match[1];
  return String(value || "").match(/\d{1,2}:\d{2}/)?.[0] || "";
}

function extractEndTime(value) {
  const matches = String(value || "").match(/\d{1,2}:\d{2}/g) || [];
  return matches[1] || "";
}

function parseLocalDate(value) {
  const [year, month, day] = String(value || "").split("-").map(Number);
  return new Date(year, Math.max(0, month - 1), day || 1);
}

function buildDateRange(start, end) {
  const left = parseLocalDate(start);
  const right = parseLocalDate(end);
  const result = [];
  for (
    const cursor = new Date(left);
    cursor <= right;
    cursor.setDate(cursor.getDate() + 1)
  ) {
    result.push(formatLocalDate(cursor));
  }
  return result;
}

function formatWeekday(value) {
  return ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][
    parseLocalDate(value).getDay()
  ];
}

function splitDisplayTags(value) {
  if (Array.isArray(value)) {
    return value.map(String).map((item) => item.trim()).filter(Boolean);
  }
  return String(value || "")
    .split(/[、,，;；]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function uniqueTags(tags) {
  return [...new Set(tags)].slice(0, 8);
}

function formatWorkDuration(data, start, end) {
  let minutes = timeRangeMinutes(start, end);
  if (!minutes) return "--工时";
  const restMinutes = getRestMinutes(data);
  minutes -= restMinutes;
  return `${Math.max(0, minutes / 60).toFixed(1)}工时`;
}

function normalizeWorkTimeMode(value = {}) {
  const explicit = value.mode || value.restMode || value.breakMode;
  if (explicit) {
    const text = String(explicit).toLowerCase();
    if (["none", "no", "false", "无", "无休息", "无休息时间"].includes(text)) return "none";
    if (["rest", "break", "true", "有", "有休息", "有休息时间"].includes(text)) return "rest";
  }
  if (value.hasRest === false || value.hasBreak === false || value.rest === false || value.breakTime === false) return "none";
  if (value.hasRest === true || value.hasBreak === true || value.rest === true || value.breakTime === true) return "rest";
  const slots = value.restSlots || value.slots || value.restPeriods;
  if (Array.isArray(slots) && slots.length) return "rest";
  const restMinutes = parseDurationMinutes(value.restMinutes);
  if (restMinutes > 0) return "rest";
  const start = extractTime(value.startTime || value.start);
  const end = extractTime(value.endTime || value.end);
  const total = timeRangeMinutes(start, end);
  const durationText = String(value.duration || "");
  const durationMinutes = /小时|hour|h/i.test(durationText)
    ? parseDurationMinutes(value.duration)
    : Number(value.duration) * 60;
  if (total > 0 && durationMinutes > 0) return durationMinutes < total ? "rest" : "none";
  return "none";
}

function normalizeRestSlots(value = {}) {
  const rows = value.restSlots || value.slots || value.restPeriods;
  if (!Array.isArray(rows)) return [];
  return rows.map((slot) => ({ start: slot.start || slot.s || "12:00", end: slot.end || slot.e || "13:00" }));
}

function getRestMinutes(data = {}) {
  if (data.mode === "none" || data.fixed === false) return 0;
  const slots = data.restSlots || data.slots || data.restPeriods || [];
  if (Array.isArray(slots) && slots.length) {
    return slots.reduce((total, slot) => {
      const start = slot.start || slot.s;
      const end = slot.end || slot.e;
      return total + timeRangeMinutes(start, end);
    }, 0);
  }
  const stored = parseDurationMinutes(data.restMinutes);
  return stored >= 0 ? stored : 0;
}

function parseDurationMinutes(value) {
  if (typeof value === "number") return Number.isFinite(value) ? value : 0;
  const text = String(value || "").trim();
  if (!text) return 0;
  const hours = text.match(/([\d.]+)\s*(?:小时|h)/i);
  if (hours) return Number(hours[1]) * 60;
  const minutes = text.match(/([\d.]+)\s*(?:分钟|min)/i);
  if (minutes) return Number(minutes[1]);
  const number = Number(text);
  return Number.isFinite(number) ? number : 0;
}

function timeToMinutes(value) {
  const [hour, minute] = String(value || "").split(":").map(Number);
  return Number.isFinite(hour) && Number.isFinite(minute)
    ? hour * 60 + minute
    : null;
}

function timeRangeMinutes(start, end, crossDay = true) {
  const startMinutes = timeToMinutes(start);
  const endMinutes = timeToMinutes(end);
  if (startMinutes === null || endMinutes === null) return 0;
  let minutes = endMinutes - startMinutes;
  if (minutes < 0 && crossDay) minutes += 24 * 60;
  return Math.max(0, minutes);
}

function addMinutesToTime(value, minutes) {
  const base = timeToMinutes(value);
  if (base === null) return value;
  const next = (base + minutes + 24 * 60) % (24 * 60);
  return `${String(Math.floor(next / 60)).padStart(2, "0")}:${String(next % 60).padStart(2, "0")}`;
}

function formatDurationMinutes(minutes, decimal = false) {
  const safeMinutes = Math.max(0, Number(minutes) || 0);
  if (decimal) return `${(safeMinutes / 60).toFixed(1)}小时`;
  if (safeMinutes % 60 === 0) return `${safeMinutes / 60}小时`;
  return `${(safeMinutes / 60).toFixed(1)}小时`;
}

function parseIdList(value) {
  if (Array.isArray(value)) return value.filter((item) => item !== "" && item != null);
  if (value === undefined || value === null || value === "") return [];
  return String(value)
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function formatWorkTime(start, end) {
  const left = extractTime(start);
  const right = extractTime(end);
  return left && right ? `${left} - ${right}` : left || right || "";
}
</script>

<style lang="scss" scoped>
.container {
  width: 100%;
  height: 100vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.nav-bar {
  position: relative;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: #fff;
}

.nav-back {
  width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-back::after {
  border: 0;
}
.nav-back image {
  width: 18px;
  height: 18px;
}
.nav-placeholder {
  width: 32px;
}

.nav-title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  font-size: 17px;
  font-weight: 600;
  color: #333;
}

.stepper {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px 16px;
  background: #fff;
  gap: 4px;
}

.step-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
}
.step-dot image {
  width: 9px;
  height: 11px;
}

.step-label {
  font-size: 13px;
  color: #ff6b35;
  font-weight: 500;
}

.step-line {
  width: 30px;
  height: 2px;
  background: #ff6b35;
}

.step-label-pending {
  font-size: 13px;
  color: #999;
}

.step-dot-pending {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #e0e0e0;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
}
.step-dot-pending image {
  width: 6px;
  height: 6px;
}

.scroll-area {
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
}

.form-section {
  margin-top: 10px;
  background: #fff;
}

.form-item {
  display: flex;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #f5f5f5;
}

.form-item:last-child {
  border-bottom: none;
}

.form-label {
  font-size: 15px;
  color: #333;
  font-weight: 500;
  min-width: 80px;
}

.form-value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: right;
  font-size: 14px;
  color: #333;
  margin-right: 4px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-value.placeholder {
  color: #999;
}

.form-arrow {
  width: 8px;
  height: 13px;
  flex-shrink: 0;
}
.task-detail-preview {
  padding: 0 16px 14px;
}
.task-detail-text {
  display: block;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f5f6f8;
  color: #666;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.date-section {
  padding: 16px;
  background: #fff;
  margin-top: 10px;
}

.date-section-title {
  font-size: 15px;
  color: #333;
  font-weight: 500;
  margin-bottom: 12px;
  display: block;
}

.date-options {
  display: flex;
  align-items: stretch;
  gap: 10px;
  width: 100%;
}
.date-picker {
  flex: 1;
  min-width: 0;
}
.date-field {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f5f5f5;
}
.date-field-label {
  margin-bottom: 4px;
  color: #999;
  font-size: 12px;
}
.date-field-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #333;
  font-size: 16px;
  font-weight: 600;
}
.date-field-value image {
  width: 8px;
  height: 13px;
}
.date-range-arrow {
  align-self: center;
  width: 16px;
  height: 14px;
  flex-shrink: 0;
}
.work-time-card,
.location-card {
  padding: 14px 16px 16px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title {
  color: #333;
  font-size: 15px;
  font-weight: 500;
}
.card-edit {
  color: #ff6b35;
  font-size: 14px;
}
.work-time-grid {
  display: flex;
  align-items: stretch;
  margin-top: 14px;
  padding: 14px 8px;
  border-radius: 10px;
  background: #f7f9fc;
}
.work-time-column {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  text-align: center;
}
.work-time-column + .work-time-column {
  border-left: 1px dashed #e4e9f0;
}
.work-time-label {
  color: #999;
  font-size: 12px;
}
.work-time-value {
  margin-top: 6px;
  color: #1a1a1a;
  font-size: 20px;
  font-weight: 700;
}
.duration-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #333;
  font-size: 15px;
  font-weight: 700;
}
.duration-title image {
  width: 13px;
  height: 13px;
  opacity: 0.55;
}
.duration-subtitle {
  margin-top: 4px;
  color: #999;
  font-size: 11px;
}
.duration-arrow {
  width: 18px;
  height: 10px;
  margin-top: 4px;
}
.work-time-tags {
  display: flex;
  margin-top: 10px;
  overflow: hidden;
  border: 1px solid #d6e8ff;
  border-radius: 8px;
  background: #eaf4ff;
}
.work-time-tag {
  flex: 1;
  padding: 9px 4px;
  color: #2f7fe0;
  font-size: 12px;
  line-height: 1.3;
  text-align: center;
}
.work-time-tag + .work-time-tag {
  border-left: 1px solid #d6e8ff;
}
.location-body {
  margin-top: 12px;
}
.location-name {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #1a1a1a;
  font-size: 15px;
  font-weight: 600;
}
.location-name image {
  width: 15px;
  height: 15px;
}
.location-address,
.location-placeholder {
  display: block;
  margin-top: 6px;
  color: #999;
  font-size: 12px;
  line-height: 1.5;
}
.scroll-bottom-space {
  height: 120px;
}

.service-fab {
  position: absolute;
  right: 16px;
  bottom: 100px;
  width: 48px;
  height: 48px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 20;
}
.service-fab image {
  width: 18px;
  height: 18px;
  margin-bottom: 2px;
}
.service-fab text {
  color: #ff6b35;
  font-size: 10px;
}

.bottom-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

.submit-btn {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #ffd700, #ffa500);
  color: #fff;
  border: none;
  border-radius: 24px;
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  line-height: 1.4;
}

.submit-btn::after {
  border: none;
}
.submit-btn[disabled] {
  opacity: 0.65;
}

.sheet-mask {
  position: absolute;
  inset: 0;
  z-index: 300;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.45);
}

.sheet-panel {
  position: relative;
  display: flex;
  width: 100%;
  flex-direction: column;
  overflow: hidden;
  border-radius: 20px 20px 0 0;
  background: #fff;
  animation: sheet-up 0.22s ease;
}

@keyframes sheet-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

.task-sheet {
  height: 90%;
  background: #f5f5f5;
}

.gender-sheet {
  height: 54%;
  min-height: 390px;
}

.work-time-sheet {
  height: 92%;
}

.sheet-header {
  position: relative;
  flex-shrink: 0;
  box-sizing: border-box;
  height: 64px;
  padding: 20px 52px 12px;
  background: #fff;
  text-align: center;
}

.sheet-title {
  color: #333;
  font-size: 18px;
  font-weight: 700;
}

.sheet-close {
  position: absolute;
  top: 14px;
  right: 12px;
  display: flex;
  width: 36px;
  height: 36px;
  margin: 0;
  padding: 0;
  align-items: center;
  justify-content: center;
  border: 0;
  background: transparent;
  color: #999;
  font-size: 24px;
  font-weight: 300;
  line-height: 36px;
}

.sheet-close::after,
.sheet-confirm::after {
  border: 0;
}

.task-sheet-body {
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
  padding: 14px 14px 0;
}

.sheet-card {
  margin-bottom: 12px;
  padding: 16px;
  border-radius: 12px;
  background: #fff;
}

.task-field-label {
  color: #1a1a1a;
  font-size: 15px;
  font-weight: 600;
}

.task-title-wrap {
  position: relative;
  margin-top: 12px;
}

.task-title-input {
  box-sizing: border-box;
  width: 100%;
  height: 46px;
  padding: 0 42px 0 14px;
  border-radius: 10px;
  background: #f5f6f8;
  color: #333;
  font-size: 14px;
}

.task-title-clear {
  position: absolute;
  top: 12px;
  right: 10px;
  display: flex;
  width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #c7c7c7;
  color: #fff;
  font-size: 13px;
  line-height: 20px;
}

.task-title-count {
  display: block;
  margin-top: 6px;
  color: #bbb;
  font-size: 12px;
  text-align: right;
}

.history-title {
  display: block;
  margin: 14px 0 10px;
  color: #666;
  font-size: 13px;
}

.history-tag {
  display: inline-block;
  padding: 7px 14px;
  border-radius: 8px;
  background: #f5f6f8;
  color: #333;
  font-size: 13px;
}

.task-label-row {
  display: flex;
  align-items: baseline;
  margin-bottom: 12px;
}

.label-muted,
.label-orange {
  margin-left: 8px;
  color: #999;
  font-size: 13px;
}

.label-orange {
  margin-left: 0;
  color: #ff8c00;
}

.task-desc-input {
  box-sizing: border-box;
  width: 100%;
  min-height: 86px;
  padding: 13px 14px;
  border-radius: 10px;
  background: #f5f6f8;
  color: #333;
  font-size: 14px;
}

.upload-row {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}

.upload-item {
  display: flex;
  width: 96px;
  height: 88px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-sizing: border-box;
  border: 1.5px dashed #f5c242;
  border-radius: 10px;
  background: #fffbed;
  color: #333;
  font-size: 13px;
}

.upload-item.disabled {
  opacity: 0.55;
}

.upload-icon.loading {
  animation: media-spin 1s linear infinite;
}

@keyframes media-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.media-preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.video-preview,
.image-preview {
  position: relative;
  width: 96px;
  height: 88px;
  overflow: hidden;
  border-radius: 10px;
  background: #f5f6f8;
}

.media-video,
.media-image {
  width: 96px;
  height: 88px;
}

.media-play-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.22);
}

.media-play-icon {
  color: #fff;
  font-size: 22px;
}

.media-remove {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 13px;
  line-height: 20px;
  text-align: center;
}

.upload-icon {
  color: #222;
  font-size: 21px;
}

.upload-hint {
  margin-top: 12px;
  color: #999;
  font-size: 12px;
  line-height: 1.8;
}

.hint-highlight {
  color: #ff8c00;
}

.option-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.task-label-row + .option-tags {
  margin-top: 0;
}

.option-tag {
  padding: 8px 16px;
  border: 1px solid #eee;
  border-radius: 8px;
  background: #f7f8fa;
  color: #666;
  font-size: 13px;
}

.option-tag.selected {
  border-color: #ffb088;
  background: #fff8e6;
  color: #ff6b35;
  font-weight: 500;
}

.task-sheet-space {
  height: 94px;
}

.sheet-action-bar,
.gender-action-bar {
  flex-shrink: 0;
  padding: 10px 16px calc(16px + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

.sheet-confirm {
  width: 100%;
  margin: 0;
  padding: 13px;
  border: 0;
  border-radius: 26px;
  background: linear-gradient(135deg, #ffd700, #ffb400);
  color: #4a3500;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
}

.gender-sheet-body {
  flex: 1;
  min-height: 0;
  padding: 20px 24px 0;
}

.gender-options {
  display: flex;
  gap: 14px;
}

.gender-option {
  flex: 1;
  padding: 13px 2px;
  border-radius: 10px;
  background: #f5f6f8;
  color: #333;
  font-size: 15px;
  text-align: center;
}

.gender-option.selected {
  background: linear-gradient(135deg, #ffd700, #ffc400);
  color: #3a2e00;
  font-weight: 600;
}

.age-range-text {
  display: block;
  margin: 32px 0 18px;
  color: #1a1a1a;
  font-size: 18px;
  font-weight: 600;
  text-align: center;
}

.age-slider {
  position: relative;
  height: 40px;
  margin: 0 8px;
}

.age-track,
.age-fill {
  position: absolute;
  top: 50%;
  right: 0;
  left: 0;
  height: 5px;
  transform: translateY(-50%);
  border-radius: 3px;
  background: #ececec;
}

.age-fill {
  right: auto;
  transform: translateY(-50%);
  background: #ffd000;
}

.age-handle {
  position: absolute;
  top: 50%;
  z-index: 2;
  display: flex;
  width: 26px;
  height: 32px;
  align-items: center;
  justify-content: center;
  transform: translate(-50%, -50%);
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.18);
  color: #bbb;
  font-size: 11px;
}

.age-labels {
  display: flex;
  justify-content: space-between;
  margin: 12px 8px 0;
  color: #999;
  font-size: 13px;
}

.gender-action-bar {
  padding-right: 20px;
  padding-left: 20px;
  box-shadow: none;
}

.work-time-sheet-body {
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
  padding: 8px 16px 18px;
  background: #f7f8fa;
}

.work-time-setting-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding: 16px;
  border-radius: 12px;
  background: #fff;
}

.work-setting-label {
  color: #1a1a1a;
  font-size: 16px;
  font-weight: 500;
}

.work-time-pickers {
  display: flex;
  align-items: center;
  gap: 8px;
}

.work-time-pickers image,
.rest-slot-pickers image {
  width: 8px;
  height: 13px;
}

.work-time-picker-value {
  display: flex;
  flex-direction: column;
  color: #1a1a1a;
  font-size: 16px;
  font-weight: 700;
  text-align: center;
}

.picker-caption {
  margin-top: 2px;
  color: #999;
  font-size: 11px;
  font-weight: 400;
}

.work-time-separator {
  color: #999;
}

.rest-mode-tabs {
  display: flex;
  gap: 10px;
  margin: 14px 0 12px;
  padding: 5px;
}

.rest-mode-tab {
  position: relative;
  flex: 1;
  padding: 12px 4px;
  border: 1px solid #ececec;
  border-radius: 9px;
  background: #fff;
  color: #999;
  font-size: 15px;
  text-align: center;
}

.rest-mode-tab.selected {
  border-color: transparent;
  background: linear-gradient(135deg, #ffd700, #ffc400);
  box-shadow: 0 3px 8px rgba(255, 190, 0, 0.35);
  color: #3a2e00;
  font-weight: 600;
}

.rest-mode-tab.selected::after {
  position: absolute;
  bottom: -12px;
  left: 50%;
  border: 6px solid transparent;
  border-top-color: #ffc400;
  content: "";
  transform: translateX(-50%);
}

.mode-radio {
  margin-right: 5px;
  color: #c8c8ce;
}

.selected .mode-radio,
.fixed-rest-option .mode-radio,
.unfixed-rest-option .mode-radio {
  color: #f5c400;
}

.rest-setting-panel {
  padding: 14px 14px 16px;
  border-radius: 12px;
  background: #fff3c2;
}

.rest-setting-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.fixed-rest-option,
.unfixed-rest-option {
  display: flex;
  align-items: center;
  color: #1a1a1a;
  font-size: 14px;
  font-weight: 600;
}

.checkin-setting {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #8a8a8a;
  font-size: 11px;
}

.checkin-setting switch {
  transform: scale(0.75);
  transform-origin: right center;
}

.rest-slot-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #fff;
}

.rest-slot-name {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  color: #333;
  font-size: 13px;
}

.rest-slot-delete {
  margin-left: 8px;
  color: #f5483b;
  font-size: 11px;
}

.rest-slot-pickers {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
  color: #ff8a1f;
  font-size: 13px;
  font-weight: 600;
}

.rest-slot-duration {
  color: #999;
  font-size: 11px;
  font-weight: 400;
}

.add-rest-slot {
  margin-top: 12px;
  color: #2f7fe0;
  font-size: 13px;
  text-align: center;
}

.unfixed-rest-option {
  margin-top: 14px;
  color: #555;
  font-weight: 400;
}

.work-duration-summary {
  margin-top: 22px;
  color: #1a1a1a;
  font-size: 16px;
  text-align: center;
}

.work-duration-value {
  margin-left: 4px;
  font-size: 20px;
  font-weight: 700;
}

.work-duration-detail {
  display: block;
  margin-top: 6px;
  color: #999;
  font-size: 12px;
}

.work-time-action-bar {
  flex-shrink: 0;
  padding: 10px 16px calc(16px + env(safe-area-inset-bottom));
  background: #fff;
}

.work-time-notice {
  display: block;
  margin-bottom: 10px;
  color: #b6b6b6;
  font-size: 11px;
  line-height: 1.6;
  text-align: center;
}
</style>
