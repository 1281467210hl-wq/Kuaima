<template>
  <view class="container">
    <BossPageHeader title="签到记录" />
    <scroll-view scroll-y class="body">
      <view class="date-line">{{ project.companyName || project.name || "项目" }} · {{ monthLabel }}</view>

      <!-- 日期筛选 -->
      <view class="date-picker">
        <view
          class="date-chip"
          :class="{ active: range === '7' }"
          @click="switchRange('7')"
        >近7天</view>
        <view
          class="date-chip"
          :class="{ active: range === '30' }"
          @click="switchRange('30')"
        >近30天</view>
        <view
          class="date-chip"
          :class="{ active: range === 'custom' }"
          @click="switchRange('custom')"
        >自定义</view>
      </view>

      <view v-if="range === 'custom'" class="custom-range">
        <picker mode="date" :value="start" @change="onStart">
          <view class="range-box">{{ start || "开始日期" }}</view>
        </picker>
        <text class="range-sep">至</text>
        <picker mode="date" :value="end" @change="onEnd">
          <view class="range-box">{{ end || "结束日期" }}</view>
        </picker>
      </view>

      <!-- 每日签到签退明细 -->
      <view v-for="d in days" :key="d.date" class="day-card">
        <view class="day-head">
          <view class="day-date">
            {{ formatShortDate(d.date) }}
            <text class="day-week">{{ weekdayOf(d.date) }}</text>
          </view>
          <view class="day-badges">
            <text v-if="d.actual" class="day-badge green">实到 {{ d.actual }}</text>
            <text v-if="d.late" class="day-badge orange">迟到 {{ d.late }}</text>
            <text v-if="d.absent" class="day-badge red">缺卡 {{ d.absent }}</text>
          </view>
        </view>
        <view v-if="d.people" class="checkin-list">
          <view v-for="(p, i) in d.people" :key="i" class="checkin-item">
            <view class="ci-avatar" :style="{ background: avatarColor(p.name) }">
              {{ (p.name || "?").charAt(0) }}
            </view>
            <view class="ci-main">
              <view class="ci-name">
                {{ p.name }}
                <text class="ci-pill" :class="pillClass(p.status)">{{ ATTEND_STATUS_TEXT[p.status] || p.status }}</text>
              </view>
              <view v-if="p.status === 'absent'" class="ci-time absent-text">今日未签到，标记为缺卡</view>
              <view v-else class="ci-time">
                <text class="time-cell">↓ {{ p.signIn || "—" }}</text>
                <text class="time-cell">↑ {{ p.signOut || "—" }}</text>
              </view>
            </view>
            <view class="ci-status" :class="rightClass(p)">{{ rightText(p) }}</view>
          </view>
          <view v-if="!d.people.length" class="ci-empty">当日无签到明细</view>
        </view>
      </view>

      <view v-if="!days.length" class="empty">
        <text class="empty-ico">📅</text>
        <text class="empty-text">{{ loading ? "加载中…" : "暂无签到记录" }}</text>
      </view>
      <view v-else class="end-tip">—— 已显示近 {{ range === "custom" ? "自定义区间" : range + " 天" }} ——</view>
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import BossPageHeader from "@/components/BossPageHeader.vue";
import {
  getProject,
  listCheckin,
  listAttendance,
  listMembers,
  avatarColor,
  ATTEND_STATUS_TEXT,
  formatShortDate,
  weekdayOf,
} from "@/api/project";

function isoDaysAgo(n) {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

/** 仅为最近 N 天拉取每人签到明细，控制请求量。 */
const DETAIL_DAYS = 7;

export default {
  components: { BossPageHeader },
  data() {
    return {
      ATTEND_STATUS_TEXT,
      projectId: "",
      project: {},
      range: "7",
      start: isoDaysAgo(6),
      end: isoDaysAgo(0),
      days: [],
      members: [],
      loading: false,
    };
  },
  computed: {
    monthLabel() {
      const m = String(this.end || "").match(/^\d{4}-(\d{1,2})/);
      return m ? `${Number(m[1])}月考勤` : "考勤";
    },
  },
  onLoad(query) {
    this.projectId = query.id;
    this.load();
  },
  methods: {
    avatarColor,
    formatShortDate,
    weekdayOf,
    async load() {
      this.loading = true;
      try {
        const [project, summary, members] = await Promise.all([
          getProject(this.projectId).catch(() => ({})),
          listCheckin(this.projectId, this.start, this.end).catch(() => []),
          listMembers(this.projectId, "all").catch(() => []),
        ]);
        this.project = project || {};
        this.members = (Array.isArray(members) ? members : []).filter(
          (m) => m.status === "active" || m.status === "temp",
        );
        const rows = (Array.isArray(summary) ? summary : []).slice().reverse();
        const detailRows = rows.slice(0, DETAIL_DAYS);
        const details = await Promise.all(
          detailRows.map((d) => listAttendance(this.projectId, d.date).catch(() => [])),
        );
        this.days = detailRows
          .map((d, i) => this.buildDay(d, Array.isArray(details[i]) ? details[i] : []))
          .concat(rows.slice(DETAIL_DAYS).map((d) => this.buildDay(d, null)));
      } catch (e) {
        console.warn("签到记录加载失败", e);
        this.days = [];
      } finally {
        this.loading = false;
      }
    },
    /** 组装某一天的成员签到/签退明细；records 为 null 表示该天不展示明细。 */
    buildDay(d, records) {
      if (!records) return { ...d, people: null };
      const people = records.map((r) => ({
        name: r.name,
        status: r.status,
        signIn: this.formatTime(r.signInTime),
        signOut: this.formatTime(r.signOutTime),
        lateMinutes: r.lateMinutes,
      }));
      // 缺卡成员：当日无打卡记录的在职/临时成员（后端汇总里有 absent 计数）
      if (d.absent > 0) {
        const present = new Set(people.map((p) => p.name));
        this.members
          .filter((m) => !present.has(m.name))
          .slice(0, Math.min(d.absent, 10))
          .forEach((m) => people.push({ name: m.name, status: "absent", signIn: "", signOut: "" }));
      }
      return { ...d, people };
    },
    formatTime(v) {
      if (!v) return "";
      const s = String(v).replace("T", " ");
      const m = s.match(/\d{4}-\d{2}-\d{2} (\d{2}:\d{2})/);
      return m ? m[1] : s.slice(11, 16);
    },
    pillClass(status) {
      return {
        on: "green",
        late: "orange",
        absent: "red",
        leave: "blue",
      }[status] || "green";
    },
    rightClass(p) {
      return {
        on: "on",
        late: "late",
        absent: "absent",
        leave: "leave",
      }[p.status] || "on";
    },
    rightText(p) {
      if (p.status === "late") return `迟到${p.lateMinutes || "?"}分`;
      if (p.status === "absent") return "缺卡";
      if (p.status === "leave") return "请假";
      return "已完成";
    },
    switchRange(r) {
      this.range = r;
      if (r === "7") {
        this.start = isoDaysAgo(6);
        this.end = isoDaysAgo(0);
      } else if (r === "30") {
        this.start = isoDaysAgo(29);
        this.end = isoDaysAgo(0);
      }
      this.load();
    },
    onStart(e) {
      this.start = e.detail.value;
      this.load();
    },
    onEnd(e) {
      this.end = e.detail.value;
      this.load();
    },
  },
};
</script>

<style lang="scss" scoped>
.container { display: flex; flex-direction: column; height: 100vh; background: #f3f4f6; }
.body { flex: 1; overflow-y: auto; padding: 12px 16px 0; }
.date-line { font-size: 13px; color: #999; margin-bottom: 10px; }
.date-picker { display: flex; gap: 10px; margin-bottom: 12px; }
.date-chip {
  flex: 1;
  text-align: center;
  padding: 9px 0;
  background: #fff;
  border-radius: 20px;
  font-size: 12px;
  color: #666;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}
.date-chip.active { background: linear-gradient(135deg, #ff6b35, #ff8c5a); color: #fff; font-weight: 600; }
.custom-range { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.range-box {
  flex: 1;
  text-align: center;
  padding: 9px 0;
  background: #fff;
  border-radius: 10px;
  font-size: 13px;
  color: #666;
}
.range-sep { color: #999; font-size: 12px; }
.day-card {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 10px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}
.day-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.day-date { font-size: 15px; font-weight: 600; color: #333; display: flex; align-items: center; gap: 6px; }
.day-week { font-size: 11px; color: #999; font-weight: 400; }
.day-badges { display: flex; gap: 6px; }
.day-badge { font-size: 10px; padding: 2px 8px; border-radius: 10px; font-weight: 600; }
.day-badge.green { background: #e8f8ef; color: #10b981; }
.day-badge.orange { background: #fef3c7; color: #d97706; }
.day-badge.red { background: #fee2e2; color: #dc2626; }
.checkin-list { border-top: 0.5px dashed #eee; padding-top: 6px; }
.checkin-item { display: flex; align-items: center; padding: 10px 0; border-bottom: 0.5px solid #f5f5f5; }
.checkin-item:last-child { border-bottom: none; }
.ci-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}
.ci-main { flex: 1; margin-left: 10px; min-width: 0; }
.ci-name { font-size: 14px; font-weight: 600; color: #333; display: flex; align-items: center; gap: 6px; }
.ci-pill { font-size: 10px; padding: 1px 7px; border-radius: 8px; font-weight: 600; }
.ci-pill.green { background: #e8f8ef; color: #10b981; }
.ci-pill.orange { background: #fef3c7; color: #d97706; }
.ci-pill.red { background: #fee2e2; color: #dc2626; }
.ci-pill.blue { background: #dbeafe; color: #2563eb; }
.ci-time { font-size: 12px; color: #999; margin-top: 3px; display: flex; align-items: center; gap: 8px; }
.ci-time.absent-text { color: #dc2626; }
.time-cell { white-space: nowrap; }
.ci-status { font-size: 11px; color: #999; text-align: right; flex-shrink: 0; }
.ci-status.on { color: #10b981; }
.ci-status.late { color: #d97706; }
.ci-status.absent { color: #dc2626; }
.ci-status.leave { color: #2563eb; }
.ci-empty { text-align: center; padding: 16px 0; color: #bbb; font-size: 12px; }
.empty { text-align: center; padding: 40px 0; color: #bbb; }
.empty-ico { font-size: 32px; display: block; margin-bottom: 10px; }
.empty-text { font-size: 13px; }
.end-tip { text-align: center; font-size: 12px; color: #bbb; padding: 18px 0; }
.bottom-space { height: 16px; }
</style>
