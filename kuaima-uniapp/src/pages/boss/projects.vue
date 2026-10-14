<template>
  <view class="container">
    <BossPageHeader title="项目管理" />
    <scroll-view scroll-y class="body">
      <!-- 搜索 -->
      <view class="search-bar">
        <image class="search-svg" src="/static/icons/boss-points/search-gray.svg" mode="aspectFit" />
        <input
          class="search-input"
          v-model="keyword"
          placeholder="请输入项目名称/用工企业名称"
          confirm-type="search"
          @confirm="loadList"
        />
      </view>

      <!-- Tab 切换 -->
      <view class="proj-tabs">
        <view
          class="proj-tab"
          :class="{ active: tab === 'created' }"
          @click="switchTab('created')"
        >
          创建新的项目
          <text v-if="overview.created" class="tab-count">{{ overview.created }}</text>
        </view>
        <view
          class="proj-tab"
          :class="{ active: tab === 'archived' }"
          @click="switchTab('archived')"
        >
          已归档的项目
          <text v-if="overview.archived" class="tab-count">{{ overview.archived }}</text>
        </view>
      </view>

      <!-- 列表 -->
      <view v-if="filteredList.length" class="proj-list">
        <view
          v-for="item in filteredList"
          :key="item.id"
          class="proj-card"
          @click="goDetail(item)"
        >
          <view class="proj-name">
            <text class="proj-name-text">{{ item.name }}</text>
            <text v-if="tab === 'archived'" class="wb-tag gray">已归档</text>
            <image class="proj-arrow-svg" src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
          </view>
          <view class="proj-info">
            <image class="proj-info-svg" src="/static/icons/boss-projects/building-gray.svg" mode="aspectFit" />
            <text class="proj-info-text">用工企业：{{ item.companyName || "—" }}</text>
          </view>
          <view class="proj-info">
            <image class="proj-info-svg" src="/static/icons/boss-projects/user-gray.svg" mode="aspectFit" />
            <text class="proj-info-text">负责人：{{ item.leaderName || "—" }}</text>
          </view>
          <view class="proj-info">
            <image class="proj-info-svg" src="/static/icons/boss-tabbar/calendar-check-gray.svg" mode="aspectFit" />
            <text class="proj-info-text">立项日期：{{ formatCnDate(item.establishDate) || "—" }}</text>
          </view>
        </view>
      </view>
      <view v-else class="empty">
        <view class="empty-icon">
          <image class="empty-svg" src="/static/icons/boss-projects/folder-open-gray.svg" mode="aspectFit" />
        </view>
        <text class="empty-text">{{ loading ? "加载中…" : "暂无项目" }}</text>
      </view>
      <view class="bottom-space" />
    </scroll-view>

    <!-- 新建项目 -->
    <view class="footer">
      <view class="primary-btn" @click="openCreate">
        <image class="primary-btn-svg" src="/static/icons/boss-reward/plus-white.svg" mode="aspectFit" />创建新的项目
      </view>
    </view>

    <!-- 创建弹层 -->
    <view v-if="showCreate" class="mask" @click="closeCreate">
      <view class="sheet" @click.stop>
        <view class="sheet-title">创建新项目</view>
        <view class="form-item">
          <text class="form-label">项目名称<text class="req">*</text></text>
          <input class="form-input" v-model="form.name" placeholder="如：菜鸟·云联日结" />
        </view>
        <view class="form-item">
          <text class="form-label">用工企业</text>
          <input class="form-input" v-model="form.companyName" placeholder="用工企业名称" />
        </view>
        <view class="form-item">
          <text class="form-label">负责人</text>
          <input class="form-input" v-model="form.leaderName" placeholder="负责人姓名" />
        </view>
        <view class="form-item">
          <text class="form-label">立项日期</text>
          <picker mode="date" :value="form.establishDate" @change="onDateChange">
            <view class="form-input picker-text">
              {{ form.establishDate || "选择日期" }}
            </view>
          </picker>
        </view>
        <view class="sheet-actions">
          <view class="sheet-btn cancel" @click="closeCreate">取消</view>
          <view class="sheet-btn confirm" :class="{ disabled: creating }" @click="submitCreate">{{ creating ? "创建中…" : "确认创建" }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import BossPageHeader from "@/components/BossPageHeader.vue";
import {
  listBossProjects,
  getBossProjectOverview,
  createBossProject,
  formatBossProjectDate,
} from "@/api/backend";

export default {
  components: { BossPageHeader },
  data() {
    return {
      keyword: "",
      tab: "created",
      list: [],
      overview: {},
      loading: false,
      showCreate: false,
      creating: false,
      idemKey: "",
      form: {
        name: "",
        companyName: "",
        leaderName: "",
        establishDate: "",
      },
    };
  },
  computed: {
    filteredList() {
      const kw = this.keyword.trim();
      if (!kw) return this.list;
      return this.list.filter((p) => {
        return (
          (p.name || "").includes(kw) ||
          (p.companyName || "").includes(kw)
        );
      });
    },
  },
  onShow() {
    this.loadData();
  },
  methods: {
    formatCnDate: formatBossProjectDate,
    async loadData() {
      this.loading = true;
      try {
        const [overview, list] = await Promise.all([
          getBossProjectOverview().catch(() => ({})),
          listBossProjects({ status: this.tab === "created" ? "active" : "archived" }).catch(() => []),
        ]);
        this.overview = overview || {};
        this.list = Array.isArray(list) ? list : [];
      } catch (e) {
        console.warn("项目列表加载失败", e);
        this.list = [];
      } finally {
        this.loading = false;
      }
    },
    async loadList() {
      await this.loadData();
    },
    switchTab(tab) {
      if (this.tab === tab) return;
      this.tab = tab;
      this.loadData();
    },
    goDetail(item) {
      uni.navigateTo({ url: `/pages/boss/project-detail?id=${item.id}` });
    },
    openCreate() {
      this.form = { name: "", companyName: "", leaderName: "", establishDate: "" };
      // 每次打开创建弹层生成一个幂等键：同一次创建意图内多次点击共用它，后端据此去重。
      this.idemKey = "proj-" + Date.now() + "-" + Math.random().toString(36).slice(2, 10);
      this.showCreate = true;
    },
    closeCreate() {
      this.showCreate = false;
    },
    onDateChange(e) {
      this.form.establishDate = e.detail.value;
    },
    async submitCreate() {
      if (this.creating) return;
      if (!this.form.name.trim()) {
        uni.showToast({ title: "请填写项目名称", icon: "none" });
        return;
      }
      this.creating = true;
      try {
        await createBossProject({
          name: this.form.name.trim(),
          companyName: this.form.companyName.trim(),
          leaderName: this.form.leaderName.trim(),
          establishDate: this.form.establishDate || null,
          status: "active",
        }, this.idemKey);
        uni.showToast({ title: "创建成功", icon: "success" });
        this.showCreate = false;
        this.tab = "created";
        this.loadData();
      } catch (e) {
        uni.showToast({ title: "创建失败，请重试", icon: "none" });
      } finally {
        this.creating = false;
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
.search-bar {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 22px;
  padding: 0 14px;
  height: 40px;
  margin-bottom: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  width: 100%;
  box-sizing: border-box;
}
.search-svg { width: 15px; height: 15px; flex-shrink: 0; margin-right: 8px; }
.search-input { flex: 1; font-size: 13px; color: #333; }
.proj-tabs {
  display: flex;
  background: #fff;
  border-radius: 16px;
  padding: 4px;
  margin-bottom: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  width: 100%;
  box-sizing: border-box;
}
.proj-tab {
  flex: 1;
  text-align: center;
  padding: 9px 0;
  font-size: 13px;
  color: #666;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.proj-tab.active {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  font-weight: 600;
}
.tab-count {
  font-size: 11px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 8px;
  padding: 0 6px;
}
.proj-card {
  background: #fff;
  border-radius: 16px;
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}
.proj-list { width: 100%; box-sizing: border-box; }
.proj-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.proj-name-text { flex: 1; }
.proj-arrow-svg { width: 13px; height: 13px; margin-left: 6px; flex-shrink: 0; }
.wb-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 8px;
}
.wb-tag.gray { background: #f3f4f6; color: #999; margin-left: 6px; }
.proj-info {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #888;
  margin-top: 5px;
}
.proj-info-svg { width: 14px; height: 14px; flex-shrink: 0; }
.empty {
  text-align: center;
  padding: 60px 0;
  color: #bbb;
}
.empty-svg { width: 36px; height: 36px; }
.empty-icon {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  background: #F5F0EA;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 10px;
}
.empty-text { font-size: 13px; }
.bottom-space { height: 16px; }
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
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 90;
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  background: #fff;
  border-radius: 18px 18px 0 0;
  padding: 20px 16px calc(20px + env(safe-area-inset-bottom));
}
.sheet-title { font-size: 17px; font-weight: 700; color: #222; margin-bottom: 14px; }
.form-item {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}
.form-label { font-size: 14px; color: #333; width: 80px; flex-shrink: 0; }
.req { color: #ff3b30; margin-left: 2px; }
.form-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 14px;
  color: #333;
  text-align: right;
}
.picker-text { color: #333; }
.sheet-actions { display: flex; gap: 12px; margin-top: 18px; }
.sheet-btn {
  flex: 1;
  text-align: center;
  padding: 12px 0;
  border-radius: 24px;
  font-size: 15px;
  font-weight: 600;
}
.sheet-btn.cancel { background: #f3f4f6; color: #666; }
.sheet-btn.confirm {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
}
.sheet-btn.confirm.disabled { opacity: 0.6; }
</style>
