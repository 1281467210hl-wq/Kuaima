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
        <text class="empty-text">暂无直播橱窗</text>
      </view>
      <view class="live-card" v-for="item in filteredList" :key="item.id">
        <view class="live-body">
          <view class="live-info-row">
            <text class="li-label">名称</text>
            <text class="li-value bold">{{ item.name }}</text>
          </view>
          <view class="live-info-row">
            <text class="li-label">主播人员</text>
            <text class="li-value">{{ item.anchor }}</text>
          </view>
          <view class="live-info-row">
            <text class="li-label">直播账号</text>
            <view class="li-value li-value-account">
              <image class="tiktok-ico" src="/static/icons/boss-live-showcase/tiktok-black.svg" mode="aspectFit" />
              <text class="li-account-text">{{ item.account }}</text>
            </view>
          </view>
          <view class="live-info-row">
            <text class="li-label">创建时间</text>
            <text class="li-value">{{ item.createTime }}</text>
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
            <view class="select-row" :class="{ picked: form.account }" @click="openAccountSheet">
              <text>{{ form.account || '请选择直播账号' }}</text>
              <image class="select-arrow" src="/static/icons/boss-live-showcase/chevron-right-gray.svg" mode="aspectFit" />
            </view>
          </view>
        </view>
        <view class="modal-footer">
          <view class="modal-btn outline" @click="closeCreateModal">取消</view>
          <view class="modal-btn primary" @click="confirmCreate">确定</view>
        </view>
      </view>
    </view>

    <!-- 直播账号选择底部抽屉 -->
    <view v-if="showAccountSheet" class="sheet-mask" @click="closeAccountSheet">
      <view class="acc-sheet" @click.stop>
        <view class="sheet-title">
          <text>选择直播账号</text>
          <view class="sheet-close" @click="closeAccountSheet">
            <image class="sheet-close-ico" src="/static/icons/boss-live-showcase/xmark-gray.svg" mode="aspectFit" />
          </view>
        </view>
        <view class="acc-item" v-for="acc in accounts" :key="acc.id" @click="pickAccount(acc)">
          <view class="acc-avatar" :style="{ background: acc.avatarBg }">{{ acc.initial }}</view>
          <view class="acc-info">
            <view class="acc-name-line">
              <text class="acc-name">{{ acc.name }}</text>
              <text class="acc-bind" :class="{ none: !acc.authorized }">{{ acc.authorized ? '已授权' : '未授权' }}</text>
            </view>
            <text class="acc-id">{{ acc.desc }}</text>
          </view>
          <image v-if="form.accountName === acc.name" class="acc-check" src="/static/icons/boss-live-showcase/check-orange.svg" mode="aspectFit" />
        </view>
        <view class="acc-item" @click="bindNewAccount">
          <view class="acc-avatar add">
            <image class="acc-avatar-ico" src="/static/icons/boss-live-showcase/plus-gray.svg" mode="aspectFit" />
          </view>
          <view class="acc-info">
            <text class="acc-name add-name">绑定新的抖音直播账号</text>
            <text class="acc-id">授权后即可用于直播带岗</text>
          </view>
          <image class="select-arrow" src="/static/icons/boss-live-showcase/chevron-right-gray.svg" mode="aspectFit" />
        </view>
      </view>
    </view>

    <!-- 绑定新抖音账号弹窗 -->
    <view v-if="showBindModal" class="modal-mask" @click="closeBindModal">
      <view class="modal-box" @click.stop>
        <view class="modal-header">
          <text class="modal-title">绑定新的抖音直播账号</text>
          <view class="modal-close" @click="closeBindModal">
            <image class="modal-close-ico" src="/static/icons/boss-live-showcase/xmark-gray.svg" mode="aspectFit" />
          </view>
        </view>
        <view class="modal-body">
          <view class="bind-tip">
            <text>填写抖音账号信息后即可完成绑定；如需官方授权，请在抖音小程序端完成授权。</text>
          </view>
          <view class="form-item">
            <text class="form-label">账号名称</text>
            <input class="form-input" v-model="bindForm.name" placeholder="请输入账号名称（如：晴时招工）" />
          </view>
          <view class="form-item">
            <text class="form-label">抖音号</text>
            <input class="form-input" v-model="bindForm.douyinId" placeholder="请输入抖音号 / 抖音ID" />
          </view>
          <view class="form-item" style="margin-bottom:0;">
            <text class="form-label">主播人员</text>
            <input class="form-input" v-model="bindForm.anchor" placeholder="选填，负责该账号的主播" />
          </view>
        </view>
        <view class="modal-footer">
          <view class="modal-btn outline" @click="closeBindModal">取消</view>
          <view class="modal-btn primary" @click="confirmBind">确认绑定</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import {
  listLiveShowcases, createLiveShowcase,
  listDouyinAccounts, bindDouyinAccount,
} from "@/api/liveShowcase";

const AVATAR_PALETTE = [
  "linear-gradient(135deg,#FF6B35,#FF8C5A)",
  "linear-gradient(135deg,#FFB84D,#F0A500)",
  "linear-gradient(135deg,#8B5CF6,#6D28D9)",
  "linear-gradient(135deg,#10B981,#059669)",
  "linear-gradient(135deg,#3B82F6,#2563EB)",
];

export default {
  data() {
    return {
      statusBarHeight: 0,
      keyword: "",
      loading: false,
      showCreateModal: false,
      showAccountSheet: false,
      showBindModal: false,
      creating: false,
      binding: false,
      bindForm: { name: "", douyinId: "", anchor: "" },
      form: { name: "", anchor: "", account: "", accountName: "", douyinAccount: "" },
      list: [],
      accounts: [],
    };
  },
  computed: {
    filteredList() {
      const kw = this.keyword.trim();
      if (!kw) return this.list;
      return this.list.filter(
        (item) => item.name.includes(kw) || item.anchor.includes(kw)
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
        const [rows, accounts] = await Promise.all([
          listLiveShowcases({ page: 0, size: 50 }).catch(() => []),
          listDouyinAccounts().catch(() => []),
        ]);
        this.list = Array.isArray(rows) ? rows : (rows && rows.data) || [];
        this.accounts = (Array.isArray(accounts) ? accounts : []).map((a, i) => this.toAccountView(a, i));
      } catch (e) {
        console.warn("直播橱窗加载失败", e);
      } finally {
        this.loading = false;
      }
    },
    toAccountView(a, i) {
      return {
        id: a.id, name: a.name, douyinId: a.douyinId || "",
        anchorName: a.anchorName || "", authorized: a.authorized !== false,
        desc: `抖音号：${a.douyinId || "—"} · ${a.authorized !== false ? "已绑定" : "未授权"}`,
        initial: (a.name || "?").charAt(0),
        avatarBg: AVATAR_PALETTE[(i || 0) % AVATAR_PALETTE.length],
      };
    },
    goBack() {
      uni.navigateBack({
        fail: () => uni.reLaunch({ url: "/pages/boss/workbench" }),
      });
    },
    openCreateModal() {
      this.form = { name: "", anchor: "", account: "", accountName: "", douyinAccount: "" };
      this.showCreateModal = true;
    },
    closeCreateModal() {
      this.showCreateModal = false;
      this.closeAccountSheet();
    },
    async confirmCreate() {
      if (this.creating) return;
      if (!this.form.name.trim()) {
        uni.showToast({ title: "请输入橱窗名称", icon: "none" });
        return;
      }
      if (!this.form.anchor.trim()) {
        uni.showToast({ title: "请输入主播人员姓名", icon: "none" });
        return;
      }
      if (!this.form.account) {
        uni.showToast({ title: "请选择直播账号", icon: "none" });
        return;
      }
      this.creating = true;
      try {
        await createLiveShowcase({
          title: this.form.name.trim(),
          anchorName: this.form.anchor.trim(),
          douyinAccount: this.form.douyinAccount || this.form.accountName,
        });
        uni.showToast({ title: `直播橱窗「${this.form.name.trim()}」创建成功！`, icon: "success" });
        this.closeCreateModal();
        const rows = await listLiveShowcases({ page: 0, size: 50 }).catch(() => []);
        this.list = Array.isArray(rows) ? rows : (rows && rows.data) || [];
      } catch (e) {
        uni.showToast({ title: "创建失败，请重试", icon: "none" });
      } finally {
        this.creating = false;
      }
    },
    openAccountSheet() {
      this.showAccountSheet = true;
    },
    closeAccountSheet() {
      this.showAccountSheet = false;
    },
    pickAccount(acc) {
      this.form.account = `${acc.name}（抖音号 ${acc.douyinId || ""}）`;
      this.form.accountName = acc.name;
      this.form.douyinAccount = acc.douyinId || acc.name;
      this.closeAccountSheet();
    },
    bindNewAccount() {
      this.bindForm = { name: "", douyinId: "", anchor: "" };
      this.showBindModal = true;
    },
    closeBindModal() {
      this.showBindModal = false;
    },
    async confirmBind() {
      if (this.binding) return;
      const name = this.bindForm.name.trim();
      const douyinId = this.bindForm.douyinId.trim();
      if (!name) {
        uni.showToast({ title: "请输入账号名称", icon: "none" });
        return;
      }
      if (!douyinId) {
        uni.showToast({ title: "请输入抖音号", icon: "none" });
        return;
      }
      if (this.accounts.some((a) => a.name === name || (a.douyinId && a.douyinId === douyinId))) {
        uni.showToast({ title: "该账号已存在", icon: "none" });
        return;
      }
      this.binding = true;
      try {
        const saved = await bindDouyinAccount({
          name, douyinId, anchorName: this.bindForm.anchor.trim(),
        });
        const list = await listDouyinAccounts().catch(() => this.accounts);
        this.accounts = (Array.isArray(list) ? list : []).map((a, i) => this.toAccountView(a, i));
        const picked = this.accounts.find((a) => a.name === name)
          || this.toAccountView(saved, this.accounts.length);
        this.pickAccount(picked);
        this.showBindModal = false;
        uni.showToast({ title: `已绑定「${name}」`, icon: "success" });
      } catch (e) {
        uni.showToast({ title: (e && e.message) || "绑定失败，请重试", icon: "none" });
      } finally {
        this.binding = false;
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
.bind-tip {
  background: #FFF6F0;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 14px;
  font-size: 12px;
  color: #FF6B35;
  line-height: 1.5;
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
.select-row {
  width: 100%;
  height: 46px;
  padding: 0 16px;
  border: 1.5px solid #e8e8e8;
  border-radius: 14px;
  font-size: 15px;
  background: #fff;
  color: #B8B8B8;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
}
.select-row.picked { color: #333; }
.select-arrow { width: 12px; height: 12px; flex-shrink: 0; }
.modal-footer {
  display: flex; gap: 12px; padding: 16px; border-top: 0.5px solid #f0f0f0;
  width: 100%; box-sizing: border-box;
}
.modal-btn { flex: 1; text-align: center; padding: 11px 0; border-radius: 22px; font-size: 14px; font-weight: 600; }
.modal-btn.outline { background: #fff; color: #333; border: 1px solid #e0e0e0; }
.modal-btn.primary { background: linear-gradient(135deg, #ff6b35, #ff8c5a); color: #fff; }

/* 账号选择抽屉 */
.sheet-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 105; display: flex; align-items: flex-end; }
.acc-sheet {
  width: 100%; background: #fff;
  border-radius: 18px 18px 0 0; padding: 10px 0 24px; z-index: 110;
  animation: slideUp 0.25s ease-out;
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
@keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }
.sheet-title {
  text-align: center; font-size: 15px; font-weight: 600; color: #333;
  padding: 8px 0 12px; position: relative;
}
.sheet-close {
  position: absolute; right: 16px; top: 50%; transform: translateY(-50%);
  width: 30px; height: 30px;
  display: flex; align-items: center; justify-content: center;
}
.sheet-close-ico { width: 13px; height: 13px; }
.acc-item {
  display: flex; align-items: center; gap: 11px; padding: 12px 18px;
  width: 100%; box-sizing: border-box;
}
.acc-item:active { background: #F7F7F7; }
.acc-avatar {
  width: 40px; height: 40px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 16px; font-weight: 600;
}
.acc-avatar.add { background: #F0F0F0; color: #999; }
.acc-avatar-ico { width: 14px; height: 14px; }
.acc-info { flex: 1; min-width: 0; }
.acc-name-line { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.acc-name { font-size: 14px; font-weight: 500; color: #333; }
.acc-name.add-name { color: #FF6B35; }
.acc-id { font-size: 12px; color: #999; margin-top: 2px; display: block; }
.acc-bind { font-size: 11px; color: #10B981; background: #E8F8EF; padding: 2px 7px; border-radius: 6px; }
.acc-bind.none { color: #FF6B35; background: #FFF0E8; }
.acc-check { width: 14px; height: 14px; flex-shrink: 0; }
</style>
