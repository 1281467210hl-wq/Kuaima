<template>
  <view class="container">
    <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight}px`, height: `${50 + statusBarHeight}px` }">
      <view class="nav-back" @click="goBack">
        <text>‹</text>
      </view>
      <text class="nav-title">报名通知</text>
      <view class="nav-right">
        <text>⋯</text>
      </view>
    </view>
    <scroll-view scroll-y class="scroll-area" refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="refreshList">
      <view class="stats-card">
        <view class="stat-item">
          <text class="stat-num">{{ stats.pending }}</text>
          <text class="stat-label">待处理</text>
        </view>
        <view class="stat-item">
          <text class="stat-num">{{ stats.approved }}</text>
          <text class="stat-label">已通过</text>
        </view>
        <view class="stat-item">
          <text class="stat-num">{{ stats.rejected }}</text>
          <text class="stat-label">已拒绝</text>
        </view>
      </view>
      <view v-if="loading" class="page-state">报名通知加载中…</view>
      <view v-else-if="loadError" class="page-state error" @click="loadApplies">加载失败，点击重试</view>
      <view class="apply-item" v-for="item in applies" :key="item.messageId || item.id" @click="openApply(item)">
        <view class="apply-header">
          <view class="apply-avatar" :style="{ background: item.avatarBg }">{{ item.initial }}</view>
          <view class="apply-info">
            <view class="apply-name">{{ item.name }}<text class="apply-badge">{{ item.tag }}</text></view>
            <text class="apply-meta">报名时间：{{ item.time }}</text>
          </view>
        </view>
        <text class="apply-job">应聘岗位：<text style="font-weight:600;">{{ item.job }}</text> · {{ item.date }} · {{ item.count }}人</text>
        <view class="apply-actions" v-if="item.status === 'pending'">
          <button class="btn-approve" :disabled="operatingId === item.id" @click.stop="approve(item)">通过</button>
          <button class="btn-reject" :disabled="operatingId === item.id" @click.stop="reject(item)">拒绝</button>
        </view>
      </view>
      <view v-if="!loading && !loadError && !applies.length" class="page-state">暂无报名通知</view>
    </scroll-view>
  </view>
</template>

<script>
import {
  hireOrderItem,
  listMessages,
  listOrderItems,
  listOrders,
  readMessage,
  rejectOrderItem,
} from "@/api/backend";

export default {
  data() {
    return {
      statusBarHeight: 0,
      stats: { pending: 0, approved: 0, rejected: 0 },
      applies: [],
      loading: false,
      loadError: false,
      refreshing: false,
      operatingId: null,
    }
  },
  onLoad() {
    try {
      const info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
      this.statusBarHeight = Number(info.statusBarHeight || 0)
    } catch (_) {}
  },
  onShow() { this.loadApplies() },
  methods: {
    goBack() { uni.navigateBack() },
    async loadApplies() {
      if (this.loading) return;
      const userId = uni.getStorageSync("userId");
      if (!userId) return uni.showToast({ title: "请先登录", icon: "none" });
      this.loading = !this.refreshing;
      this.loadError = false;
      try {
        const [messageResult, orderResult] = await Promise.all([
          listMessages(userId, { page: 0, size: 50, role: "BOSS" }),
          listOrders({ page: 0, size: 100 }),
        ]);
        const messages = (Array.isArray(messageResult) ? messageResult : messageResult?.records || messageResult?.content || [])
          .filter((message) => message.type === "ORDER_APPLY");
        const orders = Array.isArray(orderResult) ? orderResult : orderResult?.records || orderResult?.content || [];
        const groups = await Promise.all(orders.map((order) => listOrderItems(order.id).catch(() => [])));
        const itemMap = new Map(groups.flat().map((item) => [String(item.id), item]));
        const orderMap = new Map(orders.map((order) => [String(order.id), order]));
        this.applies = messages.map((message) => normalizeApply(message, itemMap.get(String(message.bizId)), orderMap));
        this.updateStats();
      } catch (error) {
        this.applies = [];
        this.updateStats();
        this.loadError = true;
        uni.showToast({ title: error.message || "报名通知加载失败", icon: "none" });
      } finally {
        this.loading = false;
        this.refreshing = false;
      }
    },
    updateStats() {
      this.stats = this.applies.reduce((result, item) => {
        result[item.status] += 1;
        return result;
      }, { pending: 0, approved: 0, rejected: 0 });
    },
    refreshList() { this.refreshing = true; this.loadApplies() },
    async markRead(item) {
      if (!item.messageId || item.read) return true;
      try {
        await readMessage(item.messageId, uni.getStorageSync("userId"));
        item.read = true;
        return true;
      } catch (error) {
        uni.showToast({ title: error.message || "消息标记已读失败", icon: "none" });
        return false;
      }
    },
    async openApply(item) {
      if (!(await this.markRead(item))) return;
      uni.navigateTo({ url: `/pages/boss/applicant-info?id=${encodeURIComponent(item.id)}&messageId=${encodeURIComponent(item.messageId || "")}` });
    },
    async approve(item) {
      if (this.operatingId) return;
      this.operatingId = item.id;
      try {
        await hireOrderItem(item.id);
        await this.markRead(item);
        uni.showToast({ title: `已通过${item.name}`, icon: "success" });
        await this.loadApplies();
      } catch (error) {
        uni.showToast({ title: error.message || "通过报名失败", icon: "none" });
      } finally { this.operatingId = null; }
    },
    async reject(item) {
      if (this.operatingId) return;
      const reason = await inputRejectReason();
      if (!reason) return;
      this.operatingId = item.id;
      try {
        await rejectOrderItem(item.id, reason);
        await this.markRead(item);
        uni.showToast({ title: "已拒绝报名", icon: "success" });
        await this.loadApplies();
      } catch (error) {
        uni.showToast({ title: error.message || "拒绝报名失败", icon: "none" });
      } finally {
        this.operatingId = null;
      }
    }
  }
}

function inputRejectReason() {
  return new Promise((resolve) => {
    uni.showModal({
      title: "拒绝报名",
      content: "",
      editable: true,
      placeholderText: "请输入拒绝原因",
      confirmText: "确认拒绝",
      success: ({ confirm, content }) => {
        if (!confirm) return resolve("");
        const reason = String(content || "").trim();
        if (!reason) {
          uni.showToast({ title: "请输入拒绝原因", icon: "none" });
          return resolve("");
        }
        resolve(reason);
      },
      fail: () => resolve(""),
    });
  });
}

function normalizeApply(message = {}, item = {}, orderMap) {
  const worker = item.user || item.worker || {};
  const order = item.order || orderMap.get(String(item.orderId)) || {};
  const name = item.nickname || item.realName || item.name || worker.nickname || worker.realName || worker.name || (item.userId ? `零工${item.userId}` : "零工");
  const status = mapApplyStatus(item.status);
  return {
    id: Number(message.bizId || item.id), messageId: message.id, read: isRead(message.readFlag), status,
    initial: name.charAt(0), name, tag: worker.skills || (status === "pending" ? "待审核" : status === "approved" ? "已通过" : "已拒绝"),
    time: formatDateTime(item.applyDate || message.createTime, true), job: order.orderTitle || order.postion || item.orderTitle || "招工岗位",
    date: formatDateTime(order.startTime || item.workDate), count: Number(order.orderNum || 1), avatarBg: "linear-gradient(135deg, #FF6B35, #FF8C5A)",
  };
}
function mapApplyStatus(status) {
  if (["已录用", "已到岗", "已完成"].includes(status)) return "approved";
  if (["取消报名", "拒绝", "已拒绝", "取消招工"].includes(status)) return "rejected";
  return "pending";
}
function isRead(value) { return value === true || value === 1 || value === "已读" || value === "READ" }
function formatDateTime(value, withSeconds = false) {
  if (value === null || value === undefined || value === "") return "时间待定";
  if (typeof value === "number" || /^\d+$/.test(String(value))) {
    const date = new Date(Number(value));
    if (!Number.isNaN(date.getTime())) {
      const pad = (item) => String(item).padStart(2, "0");
      const result = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
      return withSeconds ? result : result.slice(0, 16);
    }
  }
  const text = String(value).replace("T", " ");
  const length = withSeconds ? 19 : 16;
  return text.length >= length ? text.slice(0, length) : text;
}
</script>

<style lang="scss" scoped>
.container { width:100%; height:100vh; background:#f5f5f5; display:flex; flex-direction:column; overflow:hidden; }
.nav-bar { display:flex; align-items:center; justify-content:space-between; padding:0 16px; box-sizing:border-box; background:#fff; flex-shrink:0; }
.nav-back { width:32px; height:32px; display:flex; align-items:center; justify-content:center; font-size:24px; color:#333; }
.nav-title { font-size:17px; font-weight:600; color:#333; }
.nav-right { display:flex; gap:14px; color:#333; }
.scroll-area { flex:1; height:0; min-height:0; overflow-y:auto; }
.stats-card { background:linear-gradient(135deg,#FF6B35,#FF8C5A); margin:12px 16px; border-radius:12px; padding:16px; color:#fff; display:flex; justify-content:space-around; }
.stat-item { text-align:center; }
.stat-num { font-size:24px; font-weight:700; display:block; }
.stat-label { font-size:12px; opacity:0.9; margin-top:4px; display:block; }
.apply-item { background:#fff; margin:12px 16px; border-radius:12px; padding:16px; box-shadow:0 2px 8px rgba(0,0,0,0.04); }
.apply-header { display:flex; align-items:center; gap:10px; margin-bottom:10px; }
.apply-avatar { width:44px; height:44px; border-radius:50%; display:flex; align-items:center; justify-content:center; color:#fff; font-weight:600; }
.apply-info { flex:1; }
.apply-name { font-size:15px; font-weight:600; color:#333; display:flex; align-items:center; gap:6px; }
.apply-badge { font-size:11px; padding:2px 6px; background:#FFF3ED; color:#FF6B35; border-radius:4px; }
.apply-meta { font-size:12px; color:#999; margin-top:2px; }
.apply-job { font-size:13px; color:#666; line-height:1.5; margin-bottom:12px; display:block; }
.apply-actions { display:flex; gap:10px; }
.btn-approve { flex:1; padding:10px; background:linear-gradient(135deg,#FF6B35,#FF8C5A); color:white; border:none; border-radius:8px; font-size:13px; font-weight:500; }
.btn-reject { flex:1; padding:10px; background:#fff; color:#666; border:1px solid #ddd; border-radius:8px; font-size:13px; font-weight:500; }
.btn-approve, .btn-reject { margin:0; line-height:1.4; }
.btn-approve::after, .btn-reject::after { border:none; }
.page-state { padding:120rpx 32rpx; text-align:center; color:#999; font-size:26rpx; }
.page-state.error { color:#FF6B35; }
</style>
