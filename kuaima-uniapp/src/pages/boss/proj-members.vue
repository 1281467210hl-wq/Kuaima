<template>
  <view class="container">
    <BossPageHeader title="项目成员" />
    <scroll-view scroll-y class="body">
      <!-- 当前项目 banner -->
      <view class="pm-banner">
        <view class="pm-icon"><text class="pm-emoji">💼</text></view>
        <view class="pm-info">
          <text class="pm-name">{{ project.name || "项目" }}</text>
          <text class="pm-sub">用工企业：{{ project.companyName || "—" }} · 负责人：{{ project.leaderName || "—" }}</text>
        </view>
      </view>

      <!-- 统计 -->
      <view class="pm-stats">
        <view class="pm-stat">
          <text class="pm-stat-value">{{ stats.all || 0 }}</text>
          <text class="pm-stat-label">全部</text>
        </view>
        <view class="pm-stat">
          <text class="pm-stat-value">{{ stats.active || 0 }}</text>
          <text class="pm-stat-label">在职</text>
        </view>
        <view class="pm-stat">
          <text class="pm-stat-value">{{ stats.left || 0 }}</text>
          <text class="pm-stat-label">离职</text>
        </view>
        <view class="pm-stat">
          <text class="pm-stat-value">{{ stats.temp || 0 }}</text>
          <text class="pm-stat-label">临时</text>
        </view>
      </view>

      <!-- Tab 过滤 -->
      <view class="pm-tabs">
        <view
          v-for="t in tabs"
          :key="t.key"
          class="pm-tab"
          :class="{ active: status === t.key }"
          @click="switchStatus(t.key)"
        >{{ t.label }}</view>
      </view>

      <!-- 成员列表 -->
      <view v-if="list.length" class="pm-list">
        <view v-for="m in list" :key="m.id" class="pm-card" @click="showDetail(m)">
          <view class="pm-avatar" :style="{ background: avatarColor(m.name) }">
            {{ (m.name || "?").charAt(0) }}
          </view>
          <view class="pm-info">
            <view class="pm-name-line">
              <text class="pm-name">{{ m.name }}</text>
              <text class="pm-role">{{ m.role || "成员" }}</text>
              <text class="pm-status" :class="m.status">{{ MEMBER_STATUS_TEXT[m.status] || m.status }}</text>
            </view>
            <view class="pm-sub">
              <text>{{ m.phone || "—" }}</text>
              <text>入职 {{ m.joinDate || "—" }}</text>
            </view>
          </view>
          <view class="pm-actions">
            <view class="pm-btn outline" @click.stop="openEditStatus(m)">编辑</view>
          </view>
        </view>
      </view>
      <view v-else class="empty">
        <text class="empty-ico">👥</text>
        <text class="empty-text">{{ loading ? "加载中…" : "该状态下暂无成员" }}</text>
      </view>
      <view class="bottom-space" />
    </scroll-view>

    <!-- 底部操作：新增项目成员（原型 boss/boss-proj-members.html:178） -->
    <view class="footer">
      <view class="primary-btn" @click="openAdd">
        <image class="primary-btn-svg" src="/static/icons/boss-reward/plus-white.svg" mode="aspectFit" />新增项目成员
      </view>
    </view>

    <!-- 编辑成员状态弹层 -->
    <view v-if="editing" class="mask" @click="closeEditStatus">
      <view class="edit-modal" @click.stop>
        <view class="modal-header">
          <text class="modal-title">编辑成员状态</text>
          <text class="modal-close" @click="closeEditStatus">✕</text>
        </view>
        <view class="modal-body">
          <view class="form-group">
            <text class="form-label">姓名</text>
            <view class="form-static">{{ editing.name || "—" }}</view>
          </view>
          <view class="form-group">
            <text class="form-label">岗位</text>
            <view class="form-static">{{ editing.role || "—" }}</view>
          </view>
          <view class="form-group">
            <text class="form-label"><text class="req">*</text> 状态</text>
            <view class="status-options">
              <view
                v-for="s in statusOptions"
                :key="s.key"
                class="status-option"
                :class="{ selected: pickedStatus === s.key }"
                @click="pickedStatus = s.key"
              >
                <view class="status-radio" />
                <text>{{ s.label }}</text>
              </view>
            </view>
          </view>
        </view>
        <view class="modal-footer">
          <view class="sheet-btn cancel" @click="closeEditStatus">取消</view>
          <view class="sheet-btn confirm" @click="saveEditStatus">保存</view>
        </view>
      </view>
    </view>

    <!-- 新增项目成员：候选人选择弹窗（原型 boss/boss-proj-members.html:181-198，可选项来源=候选人列表） -->
    <view v-if="showAdd" class="mask" @click="closeAdd">
      <view class="add-modal" @click.stop>
        <view class="modal-header">
          <text class="modal-title">新增项目成员</text>
          <text class="modal-close" @click="closeAdd">✕</text>
        </view>
        <view class="ap-search">
          <input class="ap-search-input" v-model="apKeyword" placeholder="搜索候选人姓名" />
        </view>
        <scroll-view scroll-y class="ap-list">
          <view v-if="apLoading" class="ap-empty">加载中…</view>
          <view v-else-if="!filteredCandidates.length" class="ap-empty">暂无匹配候选人</view>
          <view
            v-for="c in filteredCandidates"
            :key="c.id"
            class="ap-item"
            :class="{ selected: apSelectedId === c.id }"
            @click="selectCandidate(c)"
          >
            <view class="ap-avatar" :style="{ background: avatarColor(c.name) }">{{ (c.name || "?").charAt(0) }}</view>
            <view class="ap-info">
              <view class="ap-name">{{ c.name }}</view>
              <view class="ap-sub">{{ c.job || "候选人" }}{{ c.exp ? " · " + c.exp : "" }}</view>
            </view>
            <view class="ap-check">{{ apSelectedId === c.id ? "✓" : "" }}</view>
          </view>
        </scroll-view>
        <view class="modal-footer">
          <view class="sheet-btn cancel" @click="closeAdd">取消</view>
          <view class="sheet-btn confirm" :class="{ disabled: apSubmitting }" @click="confirmAdd">{{ apSubmitting ? "添加中…" : "确认添加" }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import BossPageHeader from "@/components/BossPageHeader.vue";
import {
  getProject,
  getMemberStats,
  listMembers,
  addMember,
  updateMemberStatus,
  avatarColor,
  MEMBER_STATUS_TEXT,
} from "@/api/project";
import { listBossApplications } from "@/api/candidate";

export default {
  components: { BossPageHeader },
  data() {
    return {
      MEMBER_STATUS_TEXT,
      projectId: "",
      project: {},
      stats: {},
      status: "all",
      tabs: [
        { key: "all", label: "全部" },
        { key: "active", label: "在职" },
        { key: "left", label: "已离职" },
        { key: "temp", label: "临时" },
      ],
      list: [],
      loading: false,
      editing: null,
      pickedStatus: "active",
      statusOptions: [
        { key: "active", label: "在职" },
        { key: "left", label: "离职" },
        { key: "temp", label: "临时" },
      ],
      // 新增项目成员：候选人选择
      showAdd: false,
      apKeyword: "",
      apCandidates: [],
      apSelectedId: null,
      apLoading: false,
      apSubmitting: false,
    };
  },
  onLoad(query) {
    this.projectId = query.id;
    this.load();
  },
  computed: {
    filteredCandidates() {
      const kw = (this.apKeyword || "").trim();
      if (!kw) return this.apCandidates;
      return this.apCandidates.filter((c) => (c.name || "").includes(kw));
    },
  },
  methods: {
    avatarColor,
    async load() {
      this.loading = true;
      try {
        const [project, stats, list] = await Promise.all([
          getProject(this.projectId).catch(() => ({})),
          getMemberStats(this.projectId).catch(() => ({})),
          listMembers(this.projectId, this.status).catch(() => []),
        ]);
        this.project = project || {};
        this.stats = stats || {};
        this.list = Array.isArray(list) ? list : [];
      } catch (e) {
        console.warn("成员列表加载失败", e);
        this.list = [];
      } finally {
        this.loading = false;
      }
    },
    switchStatus(key) {
      if (this.status === key) return;
      this.status = key;
      this.load();
    },
    showDetail(m) {
      // 1013 原型：点击成员卡片进入成员详情
      uni.navigateTo({
        url: `/pages/boss/member-detail?id=${m.id}&projectId=${this.projectId}&userId=${m.userId || ""}`,
      });
    },
    openEditStatus(m) {
      this.editing = m;
      this.pickedStatus = m.status || "active";
    },
    closeEditStatus() {
      this.editing = null;
    },
    async saveEditStatus() {
      if (!this.editing) return;
      const m = this.editing;
      if (m.status === this.pickedStatus) {
        this.editing = null;
        return;
      }
      try {
        await updateMemberStatus(this.projectId, m.id, this.pickedStatus);
        uni.showToast({ title: "成员状态已更新", icon: "success" });
        this.editing = null;
        this.load();
      } catch (e) {
        uni.showToast({ title: "保存失败，请重试", icon: "none" });
      }
    },
    openAdd() {
      this.apSelectedId = null;
      this.apKeyword = "";
      this.showAdd = true;
      this.loadCandidates();
    },
    closeAdd() {
      if (this.apSubmitting) return;
      this.showAdd = false;
    },
    async loadCandidates() {
      this.apLoading = true;
      try {
        // 可选项来源=候选人列表：GET /boss/applications（当前老板的岗位投递）
        const res = await listBossApplications({ status: "all", page: 0, size: 100 });
        const rows = Array.isArray(res) ? res : res?.records || res?.content || [];
        this.apCandidates = rows.map((item) => ({
          id: item.id,
          userId: item.userId,
          name: item.name || item.resumeName || "零工",
          job: item.job || item.positionName || "",
          phone: item.phone || "",
          exp: item.expLabel || "",
          salary: item.salary || item.salaryRange || "",
        }));
      } catch (e) {
        console.warn("候选人列表加载失败", e);
        this.apCandidates = [];
      } finally {
        this.apLoading = false;
      }
    },
    selectCandidate(c) {
      this.apSelectedId = c.id;
    },
    async confirmAdd() {
      if (this.apSubmitting) return;
      const c = this.apCandidates.find((x) => x.id === this.apSelectedId);
      if (!c) {
        uni.showToast({ title: "请选择要新增的候选人", icon: "none" });
        return;
      }
      if (!this.projectId) {
        uni.showToast({ title: "缺少项目 id", icon: "none" });
        return;
      }
      this.apSubmitting = true;
      try {
        // 沿用现有新增成员接口 POST /boss/projects/{id}/members；
        // salary 优先取候选人携带值，缺省时由后端按岗位薪资范围回填。
        await addMember(this.projectId, {
          userId: c.userId,
          name: c.name,
          phone: c.phone,
          role: c.job,
          salary: c.salary || undefined,
        });
        uni.showToast({ title: `已将 ${c.name} 新增为项目成员`, icon: "success" });
        this.showAdd = false;
        this.load();
      } catch (e) {
        uni.showToast({ title: "新增失败，请重试", icon: "none" });
      } finally {
        this.apSubmitting = false;
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
.body {
  flex: 1;
  overflow-y: auto;
  width: 100%;
  padding: 12px 16px 0;
  box-sizing: border-box;
}
.pm-banner {
  background: linear-gradient(135deg, #fff7f0, #ffe9d8);
  border-radius: 14px;
  padding: 14px 16px;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  box-sizing: border-box;
}
.pm-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.pm-emoji { font-size: 20px; }
.pm-info { flex: 1; min-width: 0; }
.pm-name { font-size: 15px; font-weight: 600; color: #333; display: block; }
.pm-sub { font-size: 12px; color: #999; margin-top: 3px; display: block; }
.pm-stats {
  display: flex;
  background: #fff;
  border-radius: 12px;
  padding: 12px 0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  margin-bottom: 14px;
  width: 100%;
  box-sizing: border-box;
}
.pm-stat { flex: 1; text-align: center; position: relative; }
.pm-stat:not(:last-child)::after {
  content: "";
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  height: 20px;
  width: 0.5px;
  background: #f0f0f0;
}
.pm-stat-value { font-size: 18px; font-weight: 700; color: #ff6b35; display: block; }
.pm-stat-label { font-size: 11px; color: #999; margin-top: 3px; display: block; }
.pm-tabs { display: flex; gap: 8px; margin-bottom: 12px; }
.pm-tab {
  padding: 6px 14px;
  border-radius: 14px;
  font-size: 12px;
  color: #666;
  background: #f5f5f5;
}
.pm-tab.active { background: linear-gradient(135deg, #ff6b35, #ff8c5a); color: #fff; font-weight: 600; }
.pm-card {
  background: #fff;
  border-radius: 14px;
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}
.pm-avatar {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 17px;
  font-weight: 600;
}
.pm-info { flex: 1; min-width: 0; }
.pm-name-line { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.pm-name { font-size: 15px; font-weight: 600; color: #333; }
.pm-role { font-size: 11px; padding: 1px 6px; border-radius: 4px; background: #fff0e8; color: #ff6b35; }
.pm-status { font-size: 11px; padding: 1px 6px; border-radius: 4px; }
.pm-status.active { background: #e8f8ef; color: #10b981; }
.pm-status.left { background: #f3f4f6; color: #9ca3af; }
.pm-status.temp { background: #fef3c7; color: #d97706; }
.pm-sub { font-size: 12px; color: #999; margin-top: 4px; display: flex; gap: 10px; flex-wrap: wrap; }
.pm-actions { display: flex; flex-direction: column; gap: 6px; }
.pm-btn {
  padding: 4px 10px;
  border-radius: 10px;
  font-size: 11px;
}
.pm-btn.outline { background: #fff0e8; color: #ff6b35; }
.empty { text-align: center; padding: 40px 0; color: #bbb; }
.empty-ico { font-size: 32px; display: block; margin-bottom: 10px; }
.empty-text { font-size: 13px; }
.bottom-space { height: 16px; }
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  box-sizing: border-box;
}
.edit-modal {
  background: #fff;
  border-radius: 16px;
  width: 100%;
  max-width: 340px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid #f0f0f0;
}
.modal-title { font-size: 16px; font-weight: 600; color: #333; }
.modal-close { color: #999; font-size: 15px; padding: 2px 4px; }
.modal-body { padding: 18px; }
.form-group { margin-bottom: 16px; }
.form-group:last-child { margin-bottom: 0; }
.form-label { display: block; font-size: 13px; color: #333; margin-bottom: 6px; font-weight: 500; }
.form-static {
  width: 100%;
  padding: 11px 14px;
  border: 1px solid #eee;
  border-radius: 10px;
  font-size: 14px;
  color: #999;
  background: #f9fafb;
  box-sizing: border-box;
}
.req { color: #ef4444; margin-right: 2px; }
.status-options { display: flex; gap: 10px; }
.status-option {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
  padding: 10px 0;
  border: 1px solid #e5e5e5;
  border-radius: 10px;
  font-size: 13px;
  color: #666;
}
.status-option.selected { border-color: #ff6b35; color: #ff6b35; background: #fff8f4; font-weight: 600; }
.status-radio { width: 14px; height: 14px; border-radius: 50%; border: 2px solid #ddd; }
.status-option.selected .status-radio { border-color: #ff6b35; box-shadow: inset 0 0 0 3px #ff6b35; }
.modal-footer { display: flex; gap: 10px; padding: 14px 18px; border-top: 1px solid #f0f0f0; }
.sheet-btn { flex: 1; text-align: center; padding: 11px 0; border-radius: 10px; font-size: 14px; font-weight: 500; }
.sheet-btn.cancel { background: #f5f5f5; color: #666; }
.sheet-btn.confirm { background: linear-gradient(135deg, #ff6b35, #ff8c5a); color: #fff; font-weight: 600; }
.sheet-btn.confirm.disabled { opacity: 0.6; }

/* 底部操作按钮 */
.footer {
  flex-shrink: 0;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 0.5px solid #f0f0f0;
}
.primary-btn {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  border-radius: 24px;
  text-align: center;
  padding: 13px 0;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
}
.primary-btn-svg { width: 16px; height: 16px; margin-right: 4px; }

/* 新增项目成员：候选人选择弹窗 */
.add-modal {
  background: #fff;
  border-radius: 16px;
  width: 100%;
  max-width: 360px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}
.ap-search { padding: 14px 18px 6px; }
.ap-search-input {
  width: 100%;
  box-sizing: border-box;
  padding: 9px 12px;
  background: #f5f5f5;
  border-radius: 10px;
  font-size: 13px;
  color: #333;
}
.ap-list { max-height: 300px; padding: 4px 12px 12px; }
.ap-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border-bottom: 1px solid #f0f0f0;
}
.ap-item:last-child { border-bottom: none; }
.ap-item.selected { background: #fff8f4; border-radius: 10px; }
.ap-avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}
.ap-info { flex: 1; min-width: 0; }
.ap-name { font-size: 14px; font-weight: 600; color: #333; }
.ap-sub { font-size: 12px; color: #999; margin-top: 2px; }
.ap-check { width: 20px; text-align: center; color: #ff6b35; font-size: 16px; font-weight: 700; }
.ap-empty { text-align: center; padding: 30px; color: #bbb; font-size: 13px; }
</style>
