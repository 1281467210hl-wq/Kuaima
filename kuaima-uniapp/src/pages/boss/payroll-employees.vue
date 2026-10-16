<template>
  <view class="container">
    <!-- 导航栏 -->
    <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight}px`, height: `${statusBarHeight + 50}px` }">
      <view class="nav-back" @click="goBack">
        <image class="nav-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" />
      </view>
      <text class="nav-title">员工</text>
      <view class="nav-right">
        <image class="nav-dots-svg" src="/static/icons/boss-profile/ellipsis.svg" mode="aspectFit" />
      </view>
    </view>

    <scroll-view scroll-y class="body">
      <!-- 搜索 -->
      <view class="search-bar">
        <image class="search-svg" src="/static/icons/boss-points/search-gray.svg" mode="aspectFit" />
        <input class="search-input" v-model="keyword" placeholder="请输入员工姓名" />
      </view>

      <!-- 员工统计 -->
      <view class="section-title">员工统计</view>
      <view class="stat-cards-row">
        <view class="stat-card">
          <text class="stat-num">{{ stats.total }}</text>
          <text class="stat-label">总人数</text>
        </view>
        <view class="stat-card">
          <text class="stat-num">{{ stats.monthNew }}</text>
          <text class="stat-label">本月新增</text>
        </view>
        <view class="stat-card">
          <text class="stat-num">{{ stats.todayNew }}</text>
          <text class="stat-label">今日新增</text>
        </view>
      </view>

      <!-- 员工列表 -->
      <view class="section-title">员工列表</view>
      <view v-if="loading" class="page-state">加载中...</view>
      <view v-else-if="!employees.length" class="empty-state">
        <view class="empty-icon">
          <image class="empty-svg" src="/static/icons/boss-workbench/users-empty.svg" mode="aspectFit" />
        </view>
        <text class="empty-text">暂无员工数据</text>
      </view>
      <view class="emp-card" v-for="emp in employees" :key="emp.key" @click="goDetail(emp)">
        <view class="emp-avatar" :style="{ background: emp.avatarBg }">{{ emp.initial }}</view>
        <view class="emp-main">
          <view class="emp-name">{{ emp.name }}</view>
          <view class="emp-info">{{ emp.gender || '—' }} | {{ emp.age || '—' }}岁 | {{ emp.phone || '—' }}</view>
          <view class="emp-time">加入时间：{{ formatJoinTime(emp.joinTime) }}</view>
        </view>
      </view>
      <view class="bottom-space" />
    </scroll-view>

    <!-- 底部Tab -->
    <view class="module-tabs">
      <view class="module-tab" @click="goPayroll">
        <image class="tab-svg-icon" src="/static/icons/boss-workbench/money-bill-wave-gray.svg" mode="aspectFit" /><text class="tab-label">发薪</text>
      </view>
      <view class="module-tab active" @click="goEmployees">
        <image class="tab-svg-icon" src="/static/icons/worker-home/users-orange.svg" mode="aspectFit" /><text class="tab-label">员工</text>
      </view>
    </view>
  </view>
</template>

<script>
import { listBossEnterpriseMembers } from "@/api/backend";
import { listBossApplications } from "@/api/candidate";

function extractRows(result) {
  const body = result?.data ?? result ?? [];
  if (Array.isArray(body)) return body;
  return body.members || body.records || body.content || body.list || body.employees || [];
}

/** 加入时间字段兼容：企业成员 joinDate / 候选人 applyTime / 兜底创建时间。 */
function joinTimeOf(item) {
  return item.joinTime || item.joinDate || item.applyTime || item.addTime
    || item.timestamp || item.createTime || "";
}

/** 取可比较的时间键 yyyy-MM-dd HH:mm（无时间部分则 yyyy-MM-dd）；无法解析返回空串。 */
function timeKey(value) {
  const m = String(value || "").replace("T", " ").match(/(\d{4})-(\d{1,2})-(\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?/);
  if (!m) return "";
  const date = `${m[1]}-${m[2].padStart(2, "0")}-${m[3].padStart(2, "0")}`;
  return m[4] ? `${date} ${m[4].padStart(2, "0")}:${m[5]}` : date;
}

function genderText(value) {
  const text = String(value || "").trim();
  if (!text) return "";
  const upper = text.toUpperCase();
  if (upper === "MALE") return "男";
  if (upper === "FEMALE") return "女";
  return text;
}

export default {
  data() {
    return {
      statusBarHeight: 0,
      keyword: "",
      loading: false,
      rows: [],
      stats: { total: 0, monthNew: 0, todayNew: 0 },
      avatarColors: [
        "linear-gradient(135deg, #FF6B35, #FF8C5A)",
        "linear-gradient(135deg, #FF7743, #FF5C33)",
        "linear-gradient(135deg, #FFB84D, #F09A3E)",
        "linear-gradient(135deg, #FFA94D, #FF8C42)",
        "linear-gradient(135deg, #F09A3E, #FFB84D)",
        "linear-gradient(135deg, #52C41A, #73D13D)",
        "linear-gradient(135deg, #1890FF, #40A9FF)",
      ],
    };
  },
  computed: {
    // 原型为纯列表：姓名/手机号在前端本地过滤，不再回源请求
    employees() {
      const kw = this.keyword.trim();
      if (!kw) return this.rows;
      return this.rows.filter((emp) => (emp.name || "").includes(kw) || (emp.phone || "").includes(kw));
    },
  },
  onLoad() {
    const info = typeof uni.getWindowInfo === "function"
      ? uni.getWindowInfo() : uni.getSystemInfoSync();
    this.statusBarHeight = Number(info.statusBarHeight || 0);
    this.loadEmployees();
  },
  methods: {
    goBack() {
      uni.navigateBack({
        fail: () => uni.reLaunch({ url: "/pages/boss/workbench" }),
      });
    },
    goPayroll() {
      uni.redirectTo({ url: "/pages/boss/payroll" });
    },
    goEmployees() {},
    goDetail(emp) {
      uni.navigateTo({ url: `/pages/boss/payroll-employee-detail?id=${emp.id}` });
    },
    async loadEmployees() {
      this.loading = true;
      try {
        // 员工来源=企业成员（内部员工）+ 候选人（外部零工），不再取发薪员工接口
        const [memberRes, applicationRes] = await Promise.all([
          listBossEnterpriseMembers({ page: 0, size: 100 }).catch(() => null),
          listBossApplications({ status: "all", page: 0, size: 100 }).catch(() => null),
        ]);
        const members = this.buildRows(extractRows(memberRes), "member");
        const candidates = this.buildRows(extractRows(applicationRes), "candidate");
        this.rows = this.mergeRows(members, candidates);
        this.stats = this.buildStats(this.rows);
      } catch (error) {
        this.rows = [];
        this.stats = { total: 0, monthNew: 0, todayNew: 0 };
      } finally {
        this.loading = false;
      }
    },
    buildRows(list, source) {
      return (Array.isArray(list) ? list : []).map((item, idx) => {
        const name = item.name || item.nickname || item.realName
          || `${source === "member" ? "成员" : "零工"}${item.userId ?? idx + 1}`;
        const timeKeyText = timeKey(joinTimeOf(item));
        return {
          ...item,
          key: `${source}-${item.id ?? item.memberId ?? idx}`,
          id: item.id ?? item.memberId ?? item.userId ?? `${source}${idx}`,
          userId: item.userId ?? "",
          name,
          initial: name.slice(0, 1),
          gender: genderText(item.gender),
          age: item.age ?? "",
          phone: String(item.phone || item.mobile || "").trim(),
          joinTime: timeKeyText || String(joinTimeOf(item) || ""),
          timeKey: timeKeyText,
          avatarBg: this.avatarColors[idx % this.avatarColors.length],
        };
      });
    },
    /** 企业成员优先合并候选人；按 userId 去重，无 userId 时按完整手机号去重（脱敏号不参与匹配）。 */
    mergeRows(members, candidates) {
      const byUserId = new Set();
      const byPhone = new Set();
      const merged = [];
      [...members, ...candidates].forEach((row) => {
        const userKey = row.userId === "" || row.userId == null ? "" : String(row.userId);
        const phoneKey = /^\d{11}$/.test(row.phone) ? row.phone : "";
        if ((userKey && byUserId.has(userKey)) || (phoneKey && byPhone.has(phoneKey))) return;
        if (userKey) byUserId.add(userKey);
        if (phoneKey) byPhone.add(phoneKey);
        merged.push(row);
      });
      return merged.sort((a, b) => String(b.timeKey).localeCompare(String(a.timeKey)));
    },
    buildStats(rows) {
      const now = new Date();
      const monthKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
      const dayKey = `${monthKey}-${String(now.getDate()).padStart(2, "0")}`;
      return {
        total: rows.length,
        monthNew: rows.filter((row) => row.timeKey.startsWith(monthKey)).length,
        todayNew: rows.filter((row) => row.timeKey.startsWith(dayKey)).length,
      };
    },
    /** 原型文案：YYYY年M月D日 HH:mm（接口只有日期时省略时分）。 */
    formatJoinTime(value) {
      const text = String(value || "").replace("T", " ").trim();
      const m = text.match(/(\d{4})-(\d{1,2})-(\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?/);
      if (!m) return text || "—";
      const date = `${m[1]}年${Number(m[2])}月${Number(m[3])}日`;
      if (!m[4]) return date;
      return `${date} ${m[4].padStart(2, "0")}:${m[5]}`;
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

.search-bar {
  display: flex; align-items: center; gap: 8px; padding: 10px 14px;
  background: #fff; border-radius: 22px;
  width: 100%;
  box-sizing: border-box;
}
.search-svg { width: 15px; height: 15px; flex-shrink: 0; }
.search-input { flex: 1; font-size: 13px; color: #333; }

.section-title { font-size: 12px; color: #999; margin: 14px 0 8px 2px; }

.stat-cards-row { display: flex; gap: 10px; margin-bottom: 4px; width: 100%; }
.stat-card {
  flex: 1; background: #fff; border-radius: 14px; padding: 13px 0;
  text-align: center; box-shadow: 0 1px 6px rgba(0,0,0,0.04);
  box-sizing: border-box;
}
.stat-num { font-size: 20px; font-weight: 700; color: #FF6B35; }
.stat-label { font-size: 11px; color: #999; margin-top: 3px; }

.page-state { padding: 32px 0; color: #999; font-size: 14px; text-align: center; }
.empty-state { text-align: center; padding: 60px 40px; }
.empty-icon {
  width: 84px; height: 84px; border-radius: 50%; background: #F5F0EA;
  display: flex; align-items: center; justify-content: center;
  margin: 0 auto 12px;
}
.empty-svg { width: 36px; height: 36px; }
.empty-text { font-size: 13px; color: #999; }

.emp-card {
  background: #fff; border-radius: 14px; padding: 13px 14px; margin-bottom: 10px;
  display: flex; align-items: center; gap: 10px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04);
  width: 100%;
  box-sizing: border-box;
}
.emp-avatar {
  width: 42px; height: 42px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 15px; font-weight: 600;
}
.emp-main { flex: 1; min-width: 0; }
.emp-name { font-size: 15px; font-weight: 600; color: #333; }
.emp-info { font-size: 12px; color: #888; margin-top: 4px; }
.emp-time { font-size: 11px; color: #B0B0B0; margin-top: 4px; }

.bottom-space { height: 20px; }

.module-tabs {
  display: flex; gap: 12px; padding: 10px 16px; padding-bottom: calc(10px + env(safe-area-inset-bottom));
  background: #fff; border-top: 0.5px solid #eee;
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;
}
.module-tab {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 8px 0; border-radius: 12px; font-size: 12px; color: #999;
}
.module-tab.active { color: #ff6b35; background: #fff3ed; }
.tab-svg-icon { width: 20px; height: 20px; }
.tab-label { font-size: 12px; }
</style>
