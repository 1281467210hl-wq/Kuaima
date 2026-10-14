<template>
  <view class="container">
    <!-- 导航栏 -->
    <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight}px` }">
      <view class="nav-back" @click="goBack">
        <image class="nav-back-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" />
      </view>
      <text class="nav-title">直播橱窗</text>
      <view class="nav-right">
        <image class="nav-dots-svg" src="/static/icons/boss-profile/ellipsis.svg" mode="aspectFit" />
      </view>
    </view>

    <scroll-view scroll-y class="body">
      <!-- 搜索 -->
      <view class="search-bar">
        <image class="search-ico" src="/static/icons/boss-live-showcase/search-gray.svg" mode="aspectFit" />
        <input class="search-input" v-model="keyword" placeholder="请输入橱窗名称/主播姓名" />
      </view>

      <!-- 提示 -->
      <view class="live-notice">
        <image class="notice-ico" src="/static/icons/boss-live-showcase/circle-exclamation-orange.svg" mode="aspectFit" />
        <text class="notice-text">务必尽量挂载贵司的抖音小程序到直播间同步操作，否则抖音官方无法扶持直播间，也无法判断是否是专项诚招工的直播间。</text>
      </view>

      <!-- 直播橱窗列表 -->
      <view v-if="!filteredList.length" class="empty-state">
        <image class="empty-ico" src="/static/icons/boss-live-showcase/tiktok-black.svg" mode="aspectFit" />
        <text class="empty-text">{{ loading ? "加载中…" : "暂无直播橱窗" }}</text>
      </view>
      <view class="live-card" v-for="item in filteredList" :key="item.id">
        <view class="live-body">
          <view class="live-info-row">
            <text class="li-label">名称</text>
            <text class="li-value bold">{{ item.name }}</text>
          </view>
          <view class="live-info-row">
            <text class="li-label">主播人员</text>
            <text class="li-value">{{ item.anchor || "—" }}</text>
          </view>
          <view class="live-info-row">
            <text class="li-label">直播账号</text>
            <view class="li-value li-value-account">
              <image class="tiktok-ico" src="/static/icons/boss-live-showcase/tiktok-black.svg" mode="aspectFit" />
              <text class="li-account-text">{{ item.account || "—" }}</text>
            </view>
          </view>
          <view class="live-info-row">
            <text class="li-label">创建时间</text>
            <text class="li-value">{{ item.createTime || "—" }}</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 底部创建按钮 -->
    <view class="footer">
      <view class="footer-btn primary" @click="openCreateModal">
        <image class="footer-btn-ico" src="/static/icons/boss-live-showcase/plus-white.svg" mode="aspectFit" />
        <text>创建直播橱窗</text>
      </view>
    </view>

    <!-- 创建直播橱窗弹窗 -->
    <view v-if="showCreateModal" class="modal-mask" @click="closeCreateModal">
      <view class="modal-box" @click.stop>
        <view class="modal-header">
          <text class="modal-title">创建直播橱窗</text>
          <view class="modal-close" @click="closeCreateModal">
            <image class="modal-close-ico" src="/static/icons/boss-live-showcase/xmark-gray.svg" mode="aspectFit" />
          </view>
        </view>
        <view class="modal-body">
          <view class="form-item">
            <text class="form-label">名称</text>
            <input class="form-input" v-model="form.name" placeholder="请输入名称（如：XX的橱窗）" />
          </view>
          <view class="form-item">
            <text class="form-label">主播人员</text>
            <input class="form-input" v-model="form.anchor" placeholder="请输入主播人员姓名" />
          </view>
          <view class="form-item" style="margin-bottom:0;">
            <text class="form-label">直播账号</text>
            <input class="form-input" v-model="form.account" placeholder="请输入直播账号" />
          </view>
        </view>
        <view class="modal-footer">
          <view class="modal-btn outline" @click="closeCreateModal">取消</view>
          <view class="modal-btn primary" @click="confirmCreate">确定</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { listLiveShowcases, createLiveShowcase } from "@/api/liveShowcase";

export default {
  data() {
    return {
      statusBarHeight: 0,
      keyword: "",
      loading: false,
      showCreateModal: false,
      creating: false,
      form: { name: "", anchor: "", account: "" },
      list: [],
    };
  },
  computed: {
    filteredList() {
      const kw = this.keyword.trim();
      if (!kw) return this.list;
      return this.list.filter(
        (item) =>
          (item.name || "").includes(kw) || (item.anchor || "").includes(kw)
      );
    },
  },
  onLoad() {
    const info = typeof uni.getWindowInfo === "function"
      ? uni.getWindowInfo() : uni.getSystemInfoSync();
    this.statusBarHeight = Number(info.statusBarHeight || 0);
    this.loadAll();
  },
  methods: {
    async loadAll() {
      this.loading = true;
      try {
        const rows = await listLiveShowcases({ page: 0, size: 50 }).catch(() => []);
        this.list = Array.isArray(rows) ? rows : (rows && rows.data) || [];
      } catch (e) {
        console.warn("直播橱窗加载失败", e);
      } finally {
        this.loading = false;
      }
    },
    goBack() {
      uni.navigateBack({
        fail: () => uni.reLaunch({ url: "/pages/boss/workbench" }),
      });
    },
    openCreateModal() {
      this.form = { name: "", anchor: "", account: "" };
      this.showCreateModal = true;
    },
    closeCreateModal() {
      this.showCreateModal = false;
    },
    async confirmCreate() {
      if (this.creating) return;
      const name = this.form.name.trim();
      const anchor = this.form.anchor.trim();
      const account = this.form.account.trim();
      if (!name) {
        uni.showToast({ title: "请输入橱窗名称", icon: "none" });
        return;
      }
      if (!anchor) {
        uni.showToast({ title: "请输入主播人员姓名", icon: "none" });
        return;
      }
      if (!account) {
        uni.showToast({ title: "请输入直播账号", icon: "none" });
        return;
      }
      this.creating = true;
      try {
        await createLiveShowcase({ title: name, anchorName: anchor, douyinAccount: account });
        uni.showToast({ title: `直播橱窗「${name}」创建成功！`, icon: "success" });
        this.closeCreateModal();
        const rows = await listLiveShowcases({ page: 0, size: 50 }).catch(() => []);
        this.list = Array.isArray(rows) ? rows : (rows && rows.data) || [];
      } catch (e) {
        uni.showToast({ title: (e && e.message) || "创建失败，请重试", icon: "none" });
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
.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  padding: 8px 16px 12px;
  background: transparent;
  width: 100%;
  box-sizing: border-box;
  flex-shrink: 0;
}
.nav-back {
  width: 34px; height: 34px; border-radius: 50%; background: #fff;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.nav-back-svg { width: 14px; height: 14px; }
.nav-right { display: flex; align-items: center; justify-content: flex-end; flex-shrink: 0; }
.nav-dots-svg { width: 20px; height: 20px; }
.nav-title {
  position: absolute; left: 50%; transform: translateX(-50%);
  font-size: 17px; font-weight: 600; color: #1A1A1A;
}

.body {
  flex: 1;
  width: 100%;
  overflow-y: auto;
  padding: 0 16px 30px;
  box-sizing: border-box;
}

.search-bar {
  display: flex; align-items: center; gap: 8px; padding: 9px 14px; margin-bottom: 12px;
  background: #fff; border-radius: 20px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04);
  width: 100%; box-sizing: border-box;
}
.search-ico { width: 13px; height: 13px; flex-shrink: 0; }
.search-input { flex: 1; font-size: 13px; color: #333; }

.live-notice {
  display: flex; gap: 9px; background: #FFF8EC; border: 1px solid #FFE9BD;
  border-radius: 12px; padding: 11px 13px; margin-bottom: 12px;
  width: 100%; box-sizing: border-box;
}
.notice-ico { width: 14px; height: 14px; margin-top: 1px; flex-shrink: 0; }
.notice-text { font-size: 11.5px; color: #9A7B3F; line-height: 1.65; }

.empty-state { text-align: center; padding: 60px 40px; }
.empty-ico { display: block; width: 56px; height: 56px; margin: 0 auto 12px; }
.empty-text { font-size: 13px; color: #999; }

.live-card {
  background: #fff; border-radius: 16px; margin-bottom: 12px; overflow: hidden;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04);
  width: 100%; box-sizing: border-box;
}
.live-body { padding: 14px 14px 16px; display: flex; flex-direction: column; gap: 8px; width: 100%; box-sizing: border-box; }
.live-info-row { display: flex; align-items: center; font-size: 13px; }
.li-label { color: #999; width: 68px; flex-shrink: 0; }
.li-value { color: #333; flex: 1; min-width: 0; }
.li-value.bold { font-weight: 600; color: #1a1a1a; font-size: 14px; }
.li-value-account { display: flex; align-items: center; }
.tiktok-ico { width: 13px; height: 13px; margin-right: 4px; flex-shrink: 0; }
.li-account-text { color: #333; }

.footer {
  display: flex; padding: 12px 16px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom));
  background: #fff; border-top: 0.5px solid #eee;
  width: 100%; box-sizing: border-box;
}
.footer-btn {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px;
  text-align: center; padding: 12px 0; border-radius: 24px;
  font-size: 14px; font-weight: 600;
}
.footer-btn-ico { width: 14px; height: 14px; flex-shrink: 0; }
.footer-btn.primary { background: linear-gradient(135deg, #FF6B35, #FF8C5A); color: #fff; }

/* 弹窗 */
.modal-mask {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.5);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 20px;
  box-sizing: border-box;
}
.modal-box {
  width: 100%;
  box-sizing: border-box;
  max-width: 360px;
  background: #fff;
  border-radius: 18px;
  overflow: hidden;
  animation: modalIn 0.25s ease-out;
  z-index: 110;
}
@keyframes modalIn { from { transform: scale(0.9); opacity: 0; } to { transform: scale(1); opacity: 1; } }
.modal-header {
  display: flex; align-items: center; justify-content: center;
  padding: 16px; border-bottom: 0.5px solid #f0f0f0; position: relative;
  width: 100%; box-sizing: border-box;
}
.modal-title { font-size: 16px; font-weight: 600; color: #333; }
.modal-close {
  position: absolute; right: 16px; top: 50%; transform: translateY(-50%);
  width: 30px; height: 30px;
  display: flex; align-items: center; justify-content: center;
}
.modal-close-ico { width: 13px; height: 13px; }
.modal-body { padding: 20px 14px; width: 100%; box-sizing: border-box; }
.form-item { margin-bottom: 18px; }
.form-item:last-child { margin-bottom: 0; }
.form-label { display: block; font-size: 14px; color: #333; font-weight: 500; margin-bottom: 10px; }
.form-input {
  width: 100%;
  height: 46px;
  padding: 0 16px;
  border: 1.5px solid #e8e8e8;
  border-radius: 14px;
  font-size: 15px;
  background: #fff;
  box-sizing: border-box;
  color: #333;
  line-height: 46px;
}
.modal-footer {
  display: flex; gap: 12px; padding: 16px; border-top: 0.5px solid #f0f0f0;
  width: 100%; box-sizing: border-box;
}
.modal-btn { flex: 1; text-align: center; padding: 11px 0; border-radius: 22px; font-size: 14px; font-weight: 600; }
.modal-btn.outline { background: #fff; color: #333; border: 1px solid #e0e0e0; }
.modal-btn.primary { background: linear-gradient(135deg, #ff6b35, #ff8c5a); color: #fff; }
</style>
