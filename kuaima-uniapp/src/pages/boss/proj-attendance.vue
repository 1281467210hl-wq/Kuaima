<template>
  <view class="container">
    <BossPageHeader title="考勤打卡" />
    <scroll-view scroll-y class="body">
      <view class="date-line">{{ project.companyName || project.name || "项目" }} · 今日 {{ today }}</view>

      <!-- 今日统计 -->
      <view class="stats">
        <view class="stat">
          <text class="stat-value">{{ stats.shouldArrive || 0 }}</text>
          <text class="stat-label">应到</text>
        </view>
        <view class="stat">
          <text class="stat-value orange">{{ stats.actual || 0 }}</text>
          <text class="stat-label">实到</text>
        </view>
        <view class="stat">
          <text class="stat-value">{{ stats.late || 0 }}</text>
          <text class="stat-label">迟到</text>
        </view>
        <view class="stat">
          <text class="stat-value">{{ stats.absent || 0 }}</text>
          <text class="stat-label">缺卡</text>
        </view>
      </view>

      <!-- 筛选 -->
      <view class="tab-bar">
        <view
          v-for="t in tabs"
          :key="t.key"
          class="tab-item"
          :class="{ active: filter === t.key }"
          @click="switchFilter(t.key)"
        >{{ t.label }}</view>
      </view>

      <!-- 签到明细列表 -->
      <view v-for="r in filteredList" :key="r.key" class="ck-card">
        <view class="ck-avatar" :style="{ background: avatarColor(r.name) }">
          {{ (r.name || "?").charAt(0) }}
        </view>
        <view class="ck-info">
          <view class="ck-name">
            <text>{{ r.name }}</text>
            <text class="status-pill" :class="pillClass(r.status)">{{ ATTEND_STATUS_TEXT[r.status] || r.status }}</text>
          </view>
          <view class="ck-time">{{ timeText(r) }}</view>
        </view>
        <view class="ck-right" :class="rightClass(r.status)">{{ rightText(r) }}</view>
      </view>
      <view v-if="!filteredList.length" class="empty-text">暂无考勤记录</view>
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import BossPageHeader from "@/components/BossPageHeader.vue";
import {
  getProject,
  getAttendanceStats,
  listAttendance,
  listMembers,
  avatarColor,
  ATTEND_STATUS_TEXT,
} from "@/api/project";

export default {
  components: { BossPageHeader },
  data() {
    return {
      ATTEND_STATUS_TEXT,
      projectId: "",
      project: {},
      today: (() => {
        const d = new Date();
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
      })(),
      stats: {},
      filter: "all",
      tabs: [
        { key: "all", label: "全部" },
        { key: "on", label: "出勤" },
        { key: "late", label: "迟到" },
        { key: "absent", label: "缺卡" },
      ],
      list: [],
    };
  },
  computed: {
    filteredList() {
      if (this.filter === "all") return this.list;
      return this.list.filter((r) => r.status === this.filter);
    },
  },
  onLoad(query) {
    this.projectId = query.id;
    this.load();
  },
  methods: {
    avatarColor,
    async load() {
      try {
        const [project, stats, records, members] = await Promise.all([
          getProject(this.projectId).catch(() => ({})),
          getAttendanceStats(this.projectId).catch(() => ({})),
          listAttendance(this.projectId).catch(() => []),
          listMembers(this.projectId, "all").catch(() => []),
        ]);
        this.project = project || {};
        this.stats = stats || {};
        const rows = (Array.isArray(records) ? records : []).map((r) => ({
          key: `r${r.id}`,
          name: r.name,
          status: r.status,
          signIn: this.formatTime(r.signInTime),
          signOut: this.formatTime(r.signOutTime),
          lateMinutes: r.lateMinutes,
        }));
        // 缺卡成员：在职/临时成员中今日无打卡记录者（与统计里的缺卡数对应）
        const active = (Array.isArray(members) ? members : []).filter(
          (m) => m.status === "active" || m.status === "temp",
        );
        const present = new Set(rows.map((r) => r.name));
        const absentCount = Math.max((stats.absent || 0) - rows.filter((r) => r.status === "absent").length, 0);
        active
          .filter((m) => !present.has(m.name))
          .slice(0, Math.min(absentCount, 20))
          .forEach((m) =>
            rows.push({ key: `a${m.id}`, name: m.name, status: "absent", signIn: "", signOut: "", lateMinutes: 0 }),
          );
        this.list = rows;
      } catch (e) {
        console.warn("考勤加载失败", e);
        this.list = [];
      }
    },
    switchFilter(key) {
      this.filter = key;
    },
    formatTime(v) {
      if (!v) return "";
      const s = String(v).replace("T", " ");
      const m = s.match(/\d{4}-\d{2}-\d{2} (\d{2}:\d{2})/);
      return m ? m[1] : s.slice(11, 16);
    },
    timeText(r) {
      if (r.status === "absent") return "今日未签到，标记为缺卡";
      const lateTip = r.status === "late" && r.lateMinutes ? `（迟到${r.lateMinutes}分钟）` : "";
      return `签到 ${r.signIn || "—"}${lateTip} · 签退 ${r.signOut || "—"}`;
    },
    pillClass(status) {
      return {
        on: "status-on",
        late: "status-late",
        absent: "status-absent",
        leave: "status-leave",
      }[status] || "status-on";
    },
    rightClass(status) {
      if (status === "late") return "late";
      if (status === "absent") return "absent";
      return "";
    },
    rightText(r) {
      if (r.status === "late") return `迟到${r.lateMinutes || "?"}分`;
      if (r.status === "absent") return "缺卡";
      if (r.status === "leave") return "请假";
      return "已完成";
    },
  },
};
</script>

<style lang="scss" scoped>
.container { display: flex; flex-direction: column; height: 100vh; background: #f3f4f6; }
.body { flex: 1; overflow-y: auto; padding: 12px 16px 0; }
.date-line { font-size: 13px; color: #999; margin-bottom: 10px; }
.stats {
  display: flex;
  background: #fff;
  border-radius: 12px;
  padding: 14px 0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  margin-bottom: 12px;
}
.stat { flex: 1; text-align: center; position: relative; }
/* 原型统计列间 0.5px 分隔线 */
.stat:not(:last-child)::after {
  content: '';
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  height: 22px;
  width: 0.5px;
  background: #f0f0f0;
}
.stat-value { font-size: 20px; font-weight: 700; color: #333; display: block; }
.stat-value.orange { color: #ff6b35; }
.stat-label { font-size: 11px; color: #999; margin-top: 3px; display: block; }
.tab-bar {
  display: flex;
  background: #fff;
  padding: 4px;
  border-radius: 12px;
  margin-bottom: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}
.tab-item {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 13px;
  color: #888;
  border-radius: 10px;
}
.tab-item.active { background: linear-gradient(135deg, #fff0e8, #ffe0d0); color: #ff6b35; font-weight: 600; }
.ck-card {
  background: #fff;
  border-radius: 14px;
  padding: 14px 16px;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}
.ck-avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}
.ck-info { flex: 1; min-width: 0; }
.ck-name { font-size: 15px; font-weight: 600; color: #333; display: flex; align-items: center; gap: 6px; }
.ck-time { font-size: 12px; color: #999; margin-top: 4px; }
.ck-right { text-align: right; font-size: 12px; color: #666; flex-shrink: 0; }
.ck-right.late { color: #d97706; font-weight: 600; }
.ck-right.absent { color: #dc2626; font-weight: 600; }
.status-pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 600;
}
.status-on { background: #e8f8ef; color: #10b981; }
.status-late { background: #fef3c7; color: #d97706; }
.status-absent { background: #fee2e2; color: #dc2626; }
.status-leave { background: #e0edff; color: #2563eb; }
.empty-text { text-align: center; padding: 24px 0; color: #bbb; font-size: 13px; }
.bottom-space { height: 16px; }
</style>
