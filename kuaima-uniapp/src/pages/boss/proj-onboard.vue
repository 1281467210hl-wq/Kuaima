<template>
  <view class="container">
    <BossPageHeader title="入职记录" />
    <scroll-view scroll-y class="body">
      <!-- 已入职成员列表 -->
      <view v-if="list.length" class="apply-list">
        <view v-for="a in list" :key="a.id" class="apply-card">
          <view class="apply-head">
            <view class="apply-name">{{ a.name || "—" }}</view>
            <view class="apply-sub">{{ a.job || "岗位待定" }}</view>
          </view>
          <view class="apply-meta">
            <text>📋 入职日期：{{ a.joinDate || "—" }}</text>
            <text>📞 {{ a.phone || "—" }}</text>
          </view>
        </view>
      </view>
      <view v-else class="empty">
        <text class="empty-ico">📝</text>
        <text class="empty-text">{{ loading ? "加载中…" : "暂无已入职成员" }}</text>
      </view>
      <view class="bottom-space" />
    </scroll-view>

    <!-- 底部操作 -->
    <view class="footer">
      <view class="primary-btn" @click="openAddModal">
        <text class="btn-ico">＋</text>新增项目成员
      </view>
    </view>

    <!-- 新增项目成员弹窗（候选人选择） -->
    <view v-if="showAdd" class="ap-overlay" @click="closeAddModal">
      <view class="ap-sheet" @click.stop>
        <view class="ap-head">
          <text class="ap-head-title">新增项目成员</text>
          <text class="ap-head-close" @click="closeAddModal">✕</text>
        </view>
        <view class="ap-search">
          <text class="ap-search-ico">🔍</text>
          <input
            class="ap-search-input"
            v-model="keyword"
            placeholder="搜索候选人姓名"
            @input="onSearchInput"
          />
        </view>
        <scroll-view scroll-y class="ap-list">
          <view
            v-for="c in candidates"
            :key="c.id"
            class="ap-item"
            :class="{ selected: selectedId === c.id }"
            @click="selectCandidate(c.id)"
          >
            <view class="ap-avatar" :style="{ background: avatarColor(c.name) }">
              {{ (c.name || "?").charAt(0) }}
            </view>
            <view class="ap-info">
              <view class="ap-name">{{ c.name }}</view>
              <view class="ap-sub">{{ c.jobDesc }}</view>
            </view>
            <view class="ap-check">
              <text v-if="selectedId === c.id">✓</text>
            </view>
          </view>
          <view v-if="!candidates.length" class="ap-empty">
            {{ searching ? "搜索中…" : "暂无匹配候选人" }}
          </view>
        </scroll-view>
        <view class="ap-footer">
          <view class="ap-btn ap-cancel" @click="closeAddModal">取消</view>
          <view class="ap-btn ap-confirm" @click="confirmAdd">确认添加</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import BossPageHeader from "@/components/BossPageHeader.vue";
import { listOnboard, listMembers, addMember, avatarColor } from "@/api/project";
import { listBossTalents } from "@/api/backend";

export default {
  components: { BossPageHeader },
  data() {
    return {
      projectId: "",
      list: [],
      loading: false,
      showAdd: false,
      keyword: "",
      candidates: [],
      selectedId: null,
      searching: false,
      searchTimer: null,
    };
  },
  onLoad(query) {
    this.projectId = query.id;
    this.load();
  },
  methods: {
    avatarColor,
    async load() {
      this.loading = true;
      try {
        // 1013 原型：入职记录页展示已入职成员列表
        // 数据 = 已通过入职申请 + 在职项目成员（新增项目成员直接写入成员表，按名字去重合并）
        const [onboards, members] = await Promise.all([
          listOnboard(this.projectId, "passed").catch(() => []),
          listMembers(this.projectId, "active").catch(() => []),
        ]);
        const rows = (Array.isArray(onboards) ? onboards : []).map((a) => ({
          id: `o${a.id}`,
          name: a.name,
          job: a.job,
          phone: a.phone,
          joinDate: a.joinDate,
        }));
        const seen = new Set(rows.map((r) => r.name));
        (Array.isArray(members) ? members : [])
          .filter((m) => !seen.has(m.name))
          .forEach((m) =>
            rows.push({ id: `m${m.id}`, name: m.name, job: m.role, phone: m.phone, joinDate: m.joinDate }),
          );
        rows.sort((x, y) => String(y.joinDate || "").localeCompare(String(x.joinDate || "")));
        this.list = rows;
      } catch (e) {
        console.warn("入职记录加载失败", e);
        this.list = [];
      } finally {
        this.loading = false;
      }
    },
    openAddModal() {
      this.selectedId = null;
      this.keyword = "";
      this.showAdd = true;
      this.searchCandidates();
    },
    closeAddModal() {
      this.showAdd = false;
    },
    onSearchInput() {
      if (this.searchTimer) clearTimeout(this.searchTimer);
      this.searchTimer = setTimeout(() => this.searchCandidates(), 300);
    },
    async searchCandidates() {
      this.searching = true;
      try {
        // 候选人来源：老板人才库（GET /boss/talents，keyword 按姓名模糊匹配）
        const res = await listBossTalents({ page: 0, size: 20, keyword: this.keyword.trim() });
        const body = (res && res.data) || res || {};
        const records = Array.isArray(body) ? body : body.records || body.content || [];
        this.candidates = records.map((u) => ({
          id: u.id,
          name: u.nickname || u.name || "候选人",
          job: (u.category || "").split(",")[0] || "零工",
          exp: u.experience || "",
          jobDesc: `${(u.category || "").split(",")[0] || "零工"} · ${u.experience || "经验不限"}`,
        }));
      } catch (e) {
        console.warn("候选人搜索失败", e);
        this.candidates = [];
      } finally {
        this.searching = false;
      }
    },
    selectCandidate(id) {
      this.selectedId = id;
    },
    async confirmAdd() {
      if (!this.selectedId) {
        uni.showToast({ title: "请选择要新增的候选人", icon: "none" });
        return;
      }
      const c = this.candidates.find((x) => x.id === this.selectedId);
      if (!c) return;
      const d = new Date();
      const joinDate = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
      try {
        await addMember(this.projectId, {
          userId: c.id,
          name: c.name,
          role: c.job,
          status: "active",
          joinDate,
        });
        uni.showToast({ title: `已将 ${c.name} 新增为项目成员`, icon: "none" });
        this.showAdd = false;
        this.load();
      } catch (e) {
        uni.showToast({ title: "添加失败，请重试", icon: "none" });
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
.apply-card {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 10px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}
.apply-head { margin-bottom: 10px; }
.apply-name { font-size: 15px; font-weight: 600; color: #333; }
.apply-sub { font-size: 12px; color: #999; margin-top: 3px; }
.apply-meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 11px;
  color: #999;
  padding-top: 10px;
  border-top: 0.5px solid #f5f5f5;
}
.empty { text-align: center; padding: 40px 0; color: #bbb; }
.empty-ico { font-size: 32px; display: block; margin-bottom: 10px; }
.empty-text { font-size: 13px; }
.bottom-space { height: 16px; }
.footer {
  flex-shrink: 0;
  display: flex;
  gap: 12px;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 0.5px solid #f0f0f0;
}
.primary-btn {
  flex: 1;
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  border-radius: 24px;
  text-align: center;
  padding: 13px 0;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
}
.btn-ico { margin-right: 6px; font-weight: 700; }

/* 新增项目成员弹窗（候选人选择） */
.ap-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 90;
  display: flex;
  align-items: flex-end;
}
.ap-sheet {
  width: 100%;
  background: #fff;
  border-radius: 16px 16px 0 0;
  max-height: 75vh;
  display: flex;
  flex-direction: column;
}
.ap-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 18px;
  border-bottom: 1px solid #f0f0f0;
}
.ap-head-title { font-size: 16px; font-weight: 600; color: #333; }
.ap-head-close { font-size: 15px; color: #999; padding: 2px 6px; }
.ap-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border-bottom: 1px solid #f5f5f5;
}
.ap-search-ico { color: #999; font-size: 14px; }
.ap-search-input { flex: 1; border: none; outline: none; font-size: 14px; background: transparent; }
.ap-list { max-height: 46vh; padding: 8px 0; }
.ap-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
}
.ap-item.selected { background: #fff0e8; }
.ap-avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  flex-shrink: 0;
}
.ap-info { flex: 1; min-width: 0; }
.ap-name { font-size: 14px; font-weight: 600; color: #333; }
.ap-sub { font-size: 12px; color: #999; margin-top: 2px; }
.ap-check {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid #e5e5e5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 11px;
  flex-shrink: 0;
}
.ap-item.selected .ap-check { background: #ff6b35; border-color: #ff6b35; }
.ap-empty { text-align: center; padding: 30px 0; color: #bbb; font-size: 13px; }
.ap-footer {
  display: flex;
  gap: 12px;
  padding: 12px 18px calc(18px + env(safe-area-inset-bottom));
  border-top: 1px solid #f0f0f0;
}
.ap-btn {
  flex: 1;
  text-align: center;
  padding: 12px 0;
  border-radius: 24px;
  font-size: 15px;
  font-weight: 600;
}
.ap-cancel { background: #f5f5f5; color: #666; }
.ap-confirm { background: linear-gradient(135deg, #ff6b35, #ff8c5a); color: #fff; }
</style>
