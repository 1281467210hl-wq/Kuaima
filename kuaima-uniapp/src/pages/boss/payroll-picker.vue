<template>
  <view class="container">
    <!-- 导航栏 -->
    <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight}px`, height: `${statusBarHeight + 50}px` }">
      <view class="nav-back" @click="goBack">
        <image class="nav-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" />
      </view>
      <text class="nav-title">添加人员</text>
      <view class="nav-right" />
    </view>

    <!-- 来源 Tab -->
    <view class="source-tabs">
      <view
        v-for="tab in sourceTabs"
        :key="tab.key"
        class="source-tab"
        :class="{ active: currentTab === tab.key }"
        @click="switchTab(tab.key)"
      >{{ tab.label }}</view>
    </view>

    <scroll-view scroll-y class="body">
      <view v-if="loading" class="page-state">加载中...</view>
      <block v-else>
        <view v-if="!currentRows.length" class="empty-state">
          <view class="empty-icon"><image class="empty-svg" src="/static/icons/boss-workbench/users-empty.svg" mode="aspectFit" /></view>
          <text class="empty-text">{{ emptyText }}</text>
        </view>
        <view
          v-for="row in currentRows"
          :key="row.key"
          class="pick-card"
          :class="{ picked: isSelected(row) }"
          @click="toggle(row)"
        >
          <view class="pick-check">
            <image v-if="isSelected(row)" class="pick-check-svg" src="/static/icons/boss-recruit-settings/check-white.svg" mode="aspectFit" />
          </view>
          <view class="pick-avatar" :style="{ background: row.avatarBg }">{{ row.initial }}</view>
          <view class="pick-main">
            <view class="pick-name">
              {{ row.name }}
              <text v-if="row.job" class="pick-job">{{ row.job }}</text>
            </view>
            <view class="pick-info">{{ row.phone || '—' }}</view>
            <view v-if="row.dailyWage > 0" class="pick-wage">日薪 ¥{{ formatYuan(row.dailyWage) }}</view>
          </view>
        </view>
      </block>
      <view class="bottom-space" />
    </scroll-view>

    <!-- 底部确认栏 -->
    <view class="pick-bar">
      <view class="pick-count">已选 <text class="pick-num">{{ selectedCount }}</text> 人</view>
      <view class="pick-confirm" :class="{ disabled: selectedCount === 0 || saving }" @click="confirmPick">
        {{ saving ? '保存中...' : '确定' }}
      </view>
    </view>
  </view>
</template>

<script>
import { listBossPayrollMembers, setBossPayrollMembers, listBossEnterpriseMembers } from "@/api/backend";
import { listBossApplications } from "@/api/candidate";

function extractRows(result) {
  const body = result?.data ?? result ?? [];
  if (Array.isArray(body)) return body;
  return body.members || body.records || body.content || body.list || [];
}

const AVATAR_COLORS = [
  "linear-gradient(135deg, #FF6B35, #FF8C5A)",
  "linear-gradient(135deg, #FF7743, #FF5C33)",
  "linear-gradient(135deg, #FFB84D, #F09A3E)",
  "linear-gradient(135deg, #52C41A, #73D13D)",
  "linear-gradient(135deg, #1890FF, #40A9FF)",
];

/** 薪资文案 → 「分」：解析首个数字（如 "200元/天"→20000、"150-200元/天"→15000），无则 0。 */
function parseSalaryToFen(value) {
  const m = String(value || "").match(/\d+(\.\d+)?/);
  if (!m) return 0;
  const yuan = Number(m[0]);
  return Number.isFinite(yuan) && yuan > 0 ? Math.round(yuan * 100) : 0;
}

/** 跨来源去重键：优先 userId，其次完整 11 位手机号（脱敏号不参与）。 */
function dedupKey(row) {
  if (row.userId != null && row.userId !== "") return `u:${row.userId}`;
  if (/^\d{11}$/.test(String(row.phone || ""))) return `p:${row.phone}`;
  return `n:${row.name}-${row.phone}`;
}

export default {
  data() {
    return {
      statusBarHeight: 0,
      orderId: null,
      projectId: null,
      currentTab: "project",
      loading: false,
      saving: false,
      projectRows: [],
      candidateRows: [],
      memberRows: [],
      // 选中集：key -> 人员对象（{userId,name,phone,job,dailyWage}）
      selected: {},
      sourceTabs: [
        { key: "project", label: "关联项目成员" },
        { key: "candidate", label: "候选人" },
        { key: "member", label: "企业成员" },
      ],
    };
  },
  computed: {
    currentRows() {
      if (this.currentTab === "project") return this.projectRows;
      if (this.currentTab === "candidate") return this.candidateRows;
      return this.memberRows;
    },
    selectedCount() {
      return Object.keys(this.selected).length;
    },
    emptyText() {
      const map = { project: "该项目暂无成员", candidate: "暂无候选人", member: "暂无企业成员" };
      return map[this.currentTab] || "暂无数据";
    },
  },
  onLoad(query) {
    const info = typeof uni.getWindowInfo === "function"
      ? uni.getWindowInfo() : uni.getSystemInfoSync();
    this.statusBarHeight = Number(info.statusBarHeight || 0);
    this.orderId = query.orderId ? Number(query.orderId) : null;
    this.projectId = query.projectId ? Number(query.projectId) : null;
    this.loadAll();
  },
  methods: {
    goBack() {
      uni.navigateBack({
        fail: () => uni.reLaunch({ url: "/pages/boss/payroll" }),
      });
    },
    switchTab(key) {
      this.currentTab = key;
    },
    formatYuan(fen) {
      return (Number(fen || 0) / 100).toFixed(2);
    },
    normalize(source, item, idx) {
      const name = item.name || item.nickname || `${source === "member" ? "成员" : "零工"}${idx + 1}`;
      const phone = String(item.phone || item.mobile || "").trim();
      const dailyWage = Number(item.dailyWage || 0) || parseSalaryToFen(item.salaryRange || item.expectedSalary);
      return {
        userId: item.userId != null ? item.userId : null,
        name,
        phone,
        job: item.job || item.positionName || item.title || item.role || "",
        dailyWage,
        initial: String(name).slice(0, 1),
        avatarBg: AVATAR_COLORS[idx % AVATAR_COLORS.length],
      };
    },
    rowWithKey(source, person) {
      return { ...person, key: dedupKey(person) + `#${source}` };
    },
    async loadAll() {
      this.loading = true;
      try {
        const [projRes, candRes, memRes] = await Promise.all([
          this.projectId
            ? listBossPayrollMembers(this.projectId).catch(() => null)
            : Promise.resolve(null),
          listBossApplications({ status: "all", page: 0, size: 100 }).catch(() => null),
          listBossEnterpriseMembers({ page: 0, size: 100 }).catch(() => null),
        ]);

        this.projectRows = extractRows(projRes).map((it, i) => this.rowWithKey("project", this.normalize("project", it, i)));
        this.candidateRows = extractRows(candRes).map((it, i) => this.rowWithKey("candidate", this.normalize("candidate", it, i)));
        this.memberRows = extractRows(memRes).map((it, i) => this.rowWithKey("member", this.normalize("member", it, i)));

        // 关联项目成员默认勾选
        const selected = {};
        this.projectRows.forEach((row) => {
          if (!this.conflictSelected(selected, row)) selected[row.key] = this.toPerson(row);
        });
        this.selected = selected;
      } catch (error) {
        uni.showToast({ title: error?.message || "人员加载失败", icon: "none" });
      } finally {
        this.loading = false;
      }
    },
    toPerson(row) {
      return {
        userId: row.userId,
        name: row.name,
        phone: row.phone,
        job: row.job,
        dailyWage: row.dailyWage,
      };
    },
    /** 去重：同一 userId/手机号 已存在于选中集时视为已选，避免跨来源重复添加。 */
    conflictSelected(selected, row) {
      const uk = `u:${row.userId}`;
      const pk = /^\d{11}$/.test(String(row.phone || "")) ? `p:${row.phone}` : "";
      return Object.values(selected).some((p) => {
        if (row.userId != null && p.userId != null && String(p.userId) === String(row.userId)) return true;
        if (pk && /^\d{11}$/.test(String(p.phone || "")) && `p:${p.phone}` === pk) return true;
        return uk && p.userId != null && `u:${p.userId}` === uk;
      });
    },
    isSelected(row) {
      return !!this.selected[row.key];
    },
    toggle(row) {
      const next = { ...this.selected };
      if (next[row.key]) {
        delete next[row.key];
      } else {
        next[row.key] = this.toPerson(row);
      }
      this.selected = next;
    },
    /** 展示层去重后的选中人数（跨来源同 userId/手机号只算一人）。 */
    uniqueSelected() {
      const seen = new Set();
      const list = [];
      Object.values(this.selected).forEach((p) => {
        const k = (p.userId != null && p.userId !== "") ? `u:${p.userId}`
          : (/^\d{11}$/.test(String(p.phone || "")) ? `p:${p.phone}` : `n:${p.name}-${p.phone}`);
        if (seen.has(k)) return;
        seen.add(k);
        list.push(p);
      });
      return list;
    },
    async confirmPick() {
      if (!this.orderId) {
        uni.showToast({ title: "发薪单不存在", icon: "none" });
        return;
      }
      const members = this.uniqueSelected();
      if (!members.length) {
        uni.showToast({ title: "请先勾选人员", icon: "none" });
        return;
      }
      if (this.saving) return;
      this.saving = true;
      try {
        await setBossPayrollMembers(this.orderId, members.map((m) => ({
          userId: m.userId,
          name: m.name,
          phone: m.phone || null,
          job: m.job || null,
          dailyWage: m.dailyWage > 0 ? m.dailyWage : undefined,
        })));
        uni.showToast({ title: `已添加 ${members.length} 人`, icon: "none" });
        uni.navigateBack({
          fail: () => uni.redirectTo({ url: "/pages/boss/payroll" }),
        });
      } catch (error) {
        uni.showToast({ title: error?.message || "保存失败", icon: "none" });
      } finally {
        this.saving = false;
      }
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
.nav-back, .nav-right { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.nav-svg { width: 16px; height: 16px; }
.nav-title { font-size: 17px; font-weight: 600; color: #333; }

.source-tabs {
  display: flex; gap: 10px; padding: 8px 16px 10px; background: #fff;
  border-bottom: 0.5px solid #f0f0f0; flex-shrink: 0; width: 100%; box-sizing: border-box;
}
.source-tab {
  flex: 1; text-align: center; padding: 8px 0; border-radius: 18px; font-size: 13px; color: #666;
  background: #f5f5f5;
}
.source-tab.active { background: #FFF0E8; color: #FF6B35; font-weight: 600; }

.body { flex: 1; min-height: 0; padding: 12px 16px 0; width: 100%; box-sizing: border-box; }

.page-state { padding: 32px 0; color: #999; font-size: 14px; text-align: center; }
.empty-state { text-align: center; padding: 60px 40px; }
.empty-icon {
  width: 84px; height: 84px; border-radius: 50%; background: #F5F0EA;
  display: flex; align-items: center; justify-content: center; margin: 0 auto 12px;
}
.empty-svg { width: 36px; height: 36px; }
.empty-text { font-size: 13px; color: #999; }

.pick-card {
  background: #fff; border-radius: 14px; padding: 12px 14px; margin-bottom: 10px;
  display: flex; align-items: center; gap: 10px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04);
  border: 1.5px solid transparent;
  width: 100%; box-sizing: border-box;
}
.pick-card.picked { border-color: #FF6B35; background: #FFFAF6; }
.pick-check {
  width: 20px; height: 20px; border-radius: 50%; border: 2px solid #DDD; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; background: #fff; box-sizing: border-box;
}
.pick-card.picked .pick-check { background: #FF6B35; border-color: #FF6B35; }
.pick-check-svg { width: 11px; height: 11px; }
.pick-avatar {
  width: 40px; height: 40px; border-radius: 50%; flex-shrink: 0; color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 15px; font-weight: 600;
}
.pick-main { flex: 1; min-width: 0; }
.pick-name { font-size: 15px; font-weight: 600; color: #333; display: flex; align-items: center; gap: 6px; }
.pick-job { font-size: 11px; color: #999; font-weight: 400; background: #f5f5f5; padding: 1px 6px; border-radius: 6px; }
.pick-info { font-size: 12px; color: #888; margin-top: 3px; }
.pick-wage { font-size: 12px; color: #FF6B35; margin-top: 3px; }

.bottom-space { height: 12px; }

.pick-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 16px; padding-bottom: calc(10px + env(safe-area-inset-bottom));
  background: #fff; border-top: 1px solid #f0f0f0; flex-shrink: 0; width: 100%; box-sizing: border-box;
}
.pick-count { font-size: 13px; color: #666; }
.pick-num { color: #FF6B35; font-size: 16px; font-weight: 700; }
.pick-confirm {
  padding: 10px 34px; border-radius: 22px;
  background: linear-gradient(135deg, #FF8C42, #FF6B35); color: #fff;
  font-size: 14px; font-weight: 600;
}
.pick-confirm.disabled { opacity: .45; }
</style>
