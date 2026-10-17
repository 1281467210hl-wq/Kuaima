<template>
  <view class="page">
    <AppNavBar title="结算详情" :show-back="true" />
    <scroll-view scroll-y class="content">
      <view v-if="loading" class="page-state">结算详情加载中…</view>
      <view v-else-if="loadError" class="page-state error"
        >结算详情加载失败</view
      >
      <template v-else>
        <view class="settle-card">
          <view class="settle-header">
            <view class="settle-title">
              <text class="title-icon">¥</text>{{ detail.title }}
            </view>
            <text class="settle-status">{{ detail.statusText }}</text>
          </view>
          <text class="settle-amount">¥{{ detail.amount }}</text>
          <text class="settle-info">
            结算时间：{{ detail.settleTime || "待雇主确认" }}
          </text>
          <text class="settle-info">工作日期：{{ detail.date }}</text>
        </view>

        <view class="info-card">
          <text class="card-title">费用明细</text>
          <view class="detail-row">
            <text class="label">工作时长</text>
            <text class="value">{{ detail.hours || "--" }}</text>
          </view>
          <view class="detail-row">
            <text class="label">约定工资</text>
            <text class="value">¥{{ detail.gross }}</text>
          </view>
          <view class="detail-row">
            <text class="label">包餐补贴</text>
            <text class="value">¥{{ detail.meal || "0.00" }}</text>
          </view>
          <view class="detail-row">
            <text class="label">全勤奖励</text>
            <text class="value">¥{{ detail.bonus || "0.00" }}</text>
          </view>
          <view class="detail-row total">
            <text class="label">应得工资</text>
            <text class="value">¥{{ detail.gross }}</text>
          </view>
        </view>

        <view class="info-card">
          <text class="card-title">结算说明</text>
          <view class="detail-row">
            <text class="label">结算状态</text>
            <text class="value green">{{ detail.statusText }}</text>
          </view>
          <view class="detail-row">
            <text class="label">结算周期</text
            ><text class="value">当日结算</text>
          </view>
          <view class="detail-row">
            <text class="label">雇主名称</text>
            <text class="value">{{ detail.employer || "--" }}</text>
          </view>
          <view class="detail-row">
            <text class="label">工作地点</text>
            <text class="value">{{ detail.location || "--" }}</text>
          </view>
        </view>

        <view v-if="detail.account" class="account-card">
          <text class="card-title">到账账户</text>
          <view class="account-info">
            <text class="account-icon">微</text>
            <view class="account-details">
              <text class="account-name">{{
                detail.accountName || "到账账户"
              }}</text>
              <text class="account-desc">{{ detail.account }}</text>
            </view>
            <text class="account-amount">¥{{ detail.amount }}</text>
          </view>
        </view>
        <button
          class="wallet-btn"
          @click="uni.navigateTo({ url: '/pages/worker/wallet' })"
        >
          查看我的钱包
        </button>
      </template>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import AppNavBar from "@/components/AppNavBar.vue";
import { getPublicJob, listSettlements } from "@/api/backend";

const loading = ref(false);
const loadError = ref(false);
const detail = ref({
  title: "",
  date: "",
  gross: "0.00",
  amount: "0.00",
  statusText: "",
});
onLoad(async (options = {}) => {
  const id = options.id;
  if (!id) {
    loadError.value = true;
    return;
  }
  loading.value = true;
  try {
    const userId = uni.getStorageSync("userId") || "2001";
    const result = await listSettlements(userId);
    const item = (Array.isArray(result) ? result : []).find(
      (entry) => String(entry.id) === String(id),
    );
    if (!item) throw new Error("结算单不存在或暂无权限查看");
    let order = {};
    if (item.orderId) {
      try {
        order = await getPublicJob(item.orderId);
      } catch (_) {}
    }
    detail.value = normalizeSettlement(item, order);
  } catch (error) {
    loadError.value = true;
    uni.showToast({ title: error.message || "结算详情加载失败", icon: "none" });
  } finally {
    loading.value = false;
  }
});
function normalizeSettlement(item, order = {}) {
  const amount = Number(item.wage ?? item.totalAmount ?? 0).toFixed(2);
  return {
    ...detail.value,
    ...item,
    title: order.orderTitle || order.postion || `结算单 #${item.id}`,
    amount,
    gross: amount,
    date: order.date || item.createTime || item.payTime || "",
    statusText: item.status || "待支付",
    settleTime: item.payTime || "待支付",
    hours: order.duration ? `${order.duration}小时` : "--",
    employer:
      order.companyName ||
      (order.createBy ? `雇主 #${order.createBy}` : "平台认证雇主"),
    location: order.address || "地点待定",
    account: item.account || item.payAccount || "",
    accountName: item.accountName || item.payChannel || "",
    unit:
      order.type === "month"
        ? "月结"
        : order.type === "heldBack"
          ? "压薪日结"
          : "日结",
  };
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f8f4ed;
}
.content {
  height: calc(100vh - 176rpx);
  padding: 24rpx;
  box-sizing: border-box;
}
.page-state {
  padding: 180rpx 32rpx;
  color: #999;
  font-size: 26rpx;
  text-align: center;
}
.page-state.error {
  color: #e34d59;
}
.settle-card,
.info-card,
.account-card {
  margin-bottom: 20rpx;
  padding: 28rpx 24rpx;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 5rpx 18rpx rgba(88, 64, 32, 0.06);
}
.settle-card {
  background: linear-gradient(135deg, #fff1e6, #ffe1c2);
}
.settle-header,
.account-info,
.detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.settle-title {
  display: flex;
  align-items: center;
  color: #333;
  font-size: 30rpx;
  font-weight: 700;
}
.title-icon,
.account-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44rpx;
  height: 44rpx;
  margin-right: 12rpx;
  border-radius: 50%;
  background: #fff;
  color: #ff6b35;
  font-size: 23rpx;
  font-weight: 700;
}
.settle-status {
  padding: 6rpx 14rpx;
  border-radius: 18rpx;
  background: #e8f8e8;
  color: #52c41a;
  font-size: 21rpx;
}
.settle-amount {
  display: block;
  margin: 20rpx 0 12rpx;
  color: #ff6b35;
  font-size: 56rpx;
  font-weight: 800;
}
.settle-info {
  display: block;
  margin-top: 8rpx;
  color: #999;
  font-size: 22rpx;
}
.card-title {
  display: block;
  margin-bottom: 12rpx;
  color: #333;
  font-size: 29rpx;
  font-weight: 700;
}
.detail-row {
  padding: 18rpx 0;
  border-bottom: 1rpx solid #f4f1ed;
  font-size: 24rpx;
}
.detail-row:last-child {
  border-bottom: 0;
}
.label {
  color: #999;
}
.value {
  color: #333;
  text-align: right;
}
.total .label,
.total .value {
  color: #ff6b35;
  font-weight: 700;
}
.green {
  color: #52c41a;
}
.account-icon {
  margin: 0 16rpx 0 0;
  background: #e8f8e8;
  color: #52c41a;
}
.account-details {
  flex: 1;
}
.account-name,
.account-desc {
  display: block;
}
.account-name {
  color: #333;
  font-size: 27rpx;
}
.account-desc {
  margin-top: 6rpx;
  color: #999;
  font-size: 21rpx;
}
.account-amount {
  color: #ff6b35;
  font-size: 29rpx;
  font-weight: 700;
}
.wallet-btn {
  width: 100%;
  margin-bottom: 24rpx;
  border: 1rpx solid #ff6b35;
  border-radius: 44rpx;
  background: #fff;
  color: #ff6b35;
  font-size: 28rpx;
}
</style>
