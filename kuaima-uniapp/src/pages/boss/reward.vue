<template>
  <view class="page">
    <view class="nav" :style="{ paddingTop: `${statusBarHeight}px` }">
      <view class="nav-inner">
        <view class="nav-back" @click="goBack"><image :src="arrowLeftIcon" mode="aspectFit" /></view>
        <text class="nav-title">奖励金</text>
        <view class="nav-space" />
      </view>
    </view>

    <scroll-view scroll-y class="content" @scrolltolower="loadMore">
      <view class="content-inner">
        <view class="balance-card">
          <text class="balance-label">我的奖励金</text>
          <text class="balance-value">¥{{ formatMoney(balance) }}</text>
          <view class="balance-actions">
            <view class="balance-btn" @click="recharge"><image :src="plusIcon" mode="aspectFit" /><text>充值</text></view>
            <view class="balance-btn" @click="withdraw"><image :src="arrowDownIcon" mode="aspectFit" /><text>提现</text></view>
          </view>
        </view>

        <view class="tab-switch">
          <view v-for="tab in tabs" :key="tab.key" :class="['tab-switch-item', { active: currentTab === tab.key }]" @click="switchTab(tab.key)">{{ tab.label }}</view>
        </view>

        <view class="income-section">
          <text class="income-title">收支明细</text>
          <view v-if="records.length" class="income-list">
            <view v-for="item in records" :key="item.id" class="income-item">
              <view class="income-info">
                <view class="income-icon"><image :src="recordIcon(item.icon)" mode="aspectFit" /></view>
                <view>
                  <text class="income-name">{{ item.name }}</text>
                  <text class="income-date">{{ item.date }}</text>
                  <text v-if="item.statusText" :class="['income-status', item.statusClass]">{{ item.statusText }}</text>
                </view>
              </view>
              <text :class="['income-amount', item.type]">{{ item.type === 'income' ? '+' : '-' }}¥{{ formatMoney(item.amount) }}</text>
            </view>
          </view>
          <view v-if="recordsLoading" class="empty-state"><text>加载中...</text></view>
          <view v-else-if="recordsError" class="empty-state retry" @click="loadRecords(true)"><text>{{ recordsError }}，点击重试</text></view>
          <view v-else-if="!records.length" class="empty-state"><text>暂无相关记录</text></view>
          <view v-else-if="hasMore" class="load-more" @click="loadMore">加载更多</view>
        </view>

        <view class="rules-card">
          <view class="rules-title"><image :src="infoIcon" mode="aspectFit" /><text>奖励金说明</text></view>
          <text class="rules-item">1. 奖励金可用于发布订单、购买积分等</text>
          <text class="rules-item">2. 1元奖励金 = 1元现金，可提现至微信或支付宝</text>
          <text class="rules-item">3. 提现最低金额10元，T+1到账</text>
          <text class="rules-item">4. 奖励金有效期永久有效</text>
        </view>
        <view class="safe-space" />
      </view>
    </scroll-view>

    <view v-if="rechargeVisible" class="sheet-mask" @click="closeRecharge">
      <view class="action-sheet" @click.stop>
        <view class="sheet-handle" />
        <text class="sheet-title">奖励金充值</text>
        <text class="sheet-tip">微信支付成功后，奖励金将由支付回调入账</text>
        <view class="amount-input-wrap"><text>¥</text><input v-model="rechargeAmount" type="digit" placeholder="最低0.01元" @input="onRechargeInput" /></view>
        <view class="quick-row"><text v-for="value in rechargeQuickAmounts" :key="value" @click="setRechargeAmount(value)">¥{{ value }}</text></view>
        <button class="sheet-confirm" :disabled="rechargeSubmitting" @click="submitRecharge">{{ rechargeSubmitting ? '处理中...' : '微信支付' }}</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onShow, onUnload } from "@dcloudio/uni-app";
import {
  createBossRewardRecharge,
  getBossRewardOverview,
  getBossRewardRecharge,
  listBossRewardRecords,
} from "@/api/backend";
import arrowLeftIcon from "/static/icons/boss-points/arrow-left-dark.svg";
import plusIcon from "/static/icons/boss-reward/plus-white.svg";
import arrowDownIcon from "/static/icons/boss-reward/arrow-down-white.svg";
import giftIcon from "/static/icons/boss-reward/gift-orange.svg";
import bullhornIcon from "/static/icons/worker-rule/bullhorn-orange.svg";
import trophyIcon from "/static/icons/boss-reward/trophy-orange.svg";
import shareIcon from "/static/icons/boss-reward/share-orange.svg";
import coinsIcon from "/static/icons/worker-home/coins-orange.svg";
import moneyIcon from "/static/icons/boss-reward/money-bill-wave-orange.svg";
import infoIcon from "/static/icons/boss-points/circle-info-orange.svg";

const statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 0;
const balance = ref(0);
const withdrawableAmount = ref(0);
const currentTab = ref("ALL");
const records = ref([]);
const recordsLoading = ref(false);
const recordsError = ref("");
const recordsPage = ref(0);
const recordsTotal = ref(0);
const hasMore = ref(false);
const tabs = [{ key: "ALL", label: "全部" }, { key: "INCOME", label: "收入" }, { key: "EXPENSE", label: "支出" }];
const icons = { gift: giftIcon, bullhorn: bullhornIcon, trophy: trophyIcon, share: shareIcon, coins: coinsIcon, money: moneyIcon };
const rechargeVisible = ref(false);
const rechargeAmount = ref("");
const rechargeSubmitting = ref(false);
const rechargeIdempotencyKey = ref("");
const rechargeQuickAmounts = [0.01, 10, 50, 100];
let paymentTimer = null;
let paymentPolling = false;

onShow(() => Promise.all([loadOverview(), loadRecords(true)]));
onUnload(stopPaymentPolling);

async function loadOverview() {
  try {
    const data = await getBossRewardOverview();
    balance.value = safeNumber(data?.balance);
    withdrawableAmount.value = safeNumber(data?.withdrawableAmount ?? data?.balance);
  } catch (error) {
    uni.showToast({ title: error?.message || "奖励金加载失败", icon: "none" });
  }
}

async function loadRecords(reset = false) {
  if (recordsLoading.value) return;
  if (reset) {
    recordsPage.value = 0;
    records.value = [];
    recordsTotal.value = 0;
    hasMore.value = false;
  }
  recordsLoading.value = true;
  recordsError.value = "";
  try {
    const data = await listBossRewardRecords({ page: recordsPage.value, size: 20, type: currentTab.value });
    const list = Array.isArray(data?.records) ? data.records : [];
    records.value = reset ? list.map(normalizeRecord) : records.value.concat(list.map(normalizeRecord));
    recordsTotal.value = Number(data?.total ?? records.value.length);
    hasMore.value = records.value.length < recordsTotal.value;
  } catch (error) {
    if (!reset && recordsPage.value > 0) recordsPage.value -= 1;
    recordsError.value = error?.message || "明细加载失败";
  } finally {
    recordsLoading.value = false;
  }
}

function switchTab(tab) {
  if (currentTab.value === tab) return;
  currentTab.value = tab;
  loadRecords(true);
}

function loadMore() {
  if (!hasMore.value || recordsLoading.value) return;
  recordsPage.value += 1;
  loadRecords(false);
}

function normalizeRecord(item, index) {
  const rawType = String(item.type || item.direction || "").toUpperCase();
  return {
    id: item.id || `${item.createdAt || "reward"}-${index}`,
    type: rawType === "INCOME" ? "income" : "expense",
    name: item.name || item.title || item.remark || "奖励金变动",
    date: formatTime(item.createdAt || item.date || item.time || item.createTime),
    amount: safeNumber(item.amount ?? item.value),
    icon: item.icon || iconByName(item.name || item.title || item.remark),
    statusText: formatRecordStatus(item.status, item.type),
    statusClass: String(item.status || "").toLowerCase(),
  };
}
function formatRecordStatus(status, type) {
  if (!status || String(type).toUpperCase() !== "EXPENSE") return "";
  return {
    PENDING: "处理中",
    PROCESSING: "处理中",
    SUCCESS: "已到账",
    FAILED: "提现失败",
    CANCELED: "已取消",
  }[String(status).toUpperCase()] || "";
}
function iconByName(name = "") {
  if (name.includes("邀请")) return "gift";
  if (name.includes("发布")) return "bullhorn";
  if (name.includes("新人")) return "trophy";
  if (name.includes("分享")) return "share";
  if (name.includes("积分")) return "coins";
  return "money";
}
function recordIcon(name) { return icons[name] || moneyIcon; }
function formatMoney(value) {
  return Math.abs(safeNumber(value)).toFixed(2);
}
function safeNumber(value) { const number = Number(value); return Number.isFinite(number) ? number : 0; }
function formatTime(value) {
  if (!value) return "--";
  return String(value).replace("T", " ").slice(0, 16);
}
function goBack() { uni.navigateBack(); }

function recharge() {
  uni.navigateTo({ url: "/pages/boss/reward-recharge" });
}
function closeRecharge() { if (!rechargeSubmitting.value) rechargeVisible.value = false; }
function setRechargeAmount(value) { rechargeAmount.value = String(value); rechargeIdempotencyKey.value = ""; }
function onRechargeInput(event) {
  rechargeAmount.value = sanitizeMoney(event?.detail?.value);
  rechargeIdempotencyKey.value = "";
}

async function submitRecharge() {
  if (rechargeSubmitting.value) return;
  const amount = validateMoney(rechargeAmount.value, 0.01, "充值金额不能低于0.01元");
  if (amount === null) return;
  // #ifndef MP-WEIXIN
  uni.showToast({ title: "请在微信小程序内完成支付", icon: "none" });
  return;
  // #endif
  if (!rechargeIdempotencyKey.value) rechargeIdempotencyKey.value = createIdempotencyKey("boss-reward-recharge");
  rechargeSubmitting.value = true;
  try {
    const order = await createBossRewardRecharge(amount, rechargeIdempotencyKey.value);
    const payParams = order?.payParams || {};
    if (!order?.orderNo) throw new Error("充值订单号无效");
    if (!payParams.package || !payParams.paySign || !payParams.nonceStr || !payParams.timeStamp) throw new Error("微信支付参数无效");
    // #ifdef MP-WEIXIN
    await requestWechatPayment(payParams);
    rechargeVisible.value = false;
    startPaymentPolling(order.orderNo);
    uni.showToast({ title: "支付结果确认中", icon: "none" });
    // #endif
  } catch (error) {
    const message = String(error?.errMsg || error?.message || "充值失败");
    uni.showToast({ title: /cancel/i.test(message) ? "支付已取消" : message, icon: "none" });
  } finally {
    rechargeSubmitting.value = false;
  }
}

function requestWechatPayment(payParams) {
  return new Promise((resolve, reject) => uni.requestPayment({
    provider: "wxpay",
    timeStamp: String(payParams.timeStamp),
    nonceStr: String(payParams.nonceStr),
    package: String(payParams.package),
    signType: String(payParams.signType || "RSA"),
    paySign: String(payParams.paySign),
    success: resolve,
    fail: reject,
  }));
}

function startPaymentPolling(orderNo) {
  stopPaymentPolling();
  paymentPolling = true;
  let attempts = 0;
  const poll = async () => {
    if (!paymentPolling) return;
    attempts += 1;
    try {
      const order = await getBossRewardRecharge(orderNo);
      if (["支付成功", "PAID", "SUCCESS"].includes(String(order?.status))) {
        stopPaymentPolling();
        rechargeIdempotencyKey.value = "";
        uni.showToast({ title: "充值成功", icon: "success" });
        await Promise.all([loadOverview(), loadRecords(true)]);
        return;
      }
    } catch (_) {}
    if (attempts >= 10) {
      stopPaymentPolling();
      uni.showToast({ title: "支付结果确认中，请稍后查看余额", icon: "none" });
      return;
    }
    paymentTimer = setTimeout(poll, 1000);
  };
  poll();
}
function stopPaymentPolling() { paymentPolling = false; if (paymentTimer) clearTimeout(paymentTimer); paymentTimer = null; }

function withdraw() {
  uni.navigateTo({
    url: `/pages/boss/reward-withdraw?balance=${encodeURIComponent(withdrawableAmount.value.toFixed(2))}`,
  });
}

function sanitizeMoney(value) {
  const clean = String(value || "").replace(/[^\d.]/g, "");
  const dot = clean.indexOf(".");
  return dot < 0 ? clean : `${clean.slice(0, dot)}.${clean.slice(dot + 1).replace(/\./g, "").slice(0, 2)}`;
}
function validateMoney(value, minimum, minimumMessage) {
  if (!/^\d+(\.\d{1,2})?$/.test(String(value || ""))) { uni.showToast({ title: "请输入正确金额，最多两位小数", icon: "none" }); return null; }
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount < minimum) { uni.showToast({ title: minimumMessage, icon: "none" }); return null; }
  return Number(amount.toFixed(2));
}
function createIdempotencyKey(prefix) { return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`; }
</script>

<style scoped>
.page { display:flex; flex-direction:column; height:100vh; overflow:hidden; background:#f5f5f5; }
.nav { flex-shrink:0; background:#f5f5f5; }.nav-inner { position:relative; display:flex; align-items:center; justify-content:space-between; height:100rpx; padding:0 32rpx; }.nav-back,.nav-space { width:64rpx; height:64rpx; }.nav-back { display:flex; align-items:center; justify-content:center; }.nav-back image { width:36rpx; height:36rpx; }.nav-title { position:absolute; left:50%; transform:translateX(-50%); color:#333; font-size:34rpx; font-weight:600; }
.content { flex:1; min-height:0; height:0; }.content-inner { padding:32rpx; box-sizing:border-box; }
.balance-card { margin-bottom:32rpx; padding:48rpx; border-radius:32rpx; color:#fff; background:linear-gradient(135deg,#ff6b35,#ff8c5a); box-shadow:0 16rpx 48rpx rgba(255,107,53,.3); }
.balance-label,.balance-value { display:block; }.balance-label { font-size:26rpx; opacity:.9; }.balance-value { margin-top:16rpx; font-size:64rpx; font-weight:700; }
.balance-actions { display:flex; gap:24rpx; margin-top:32rpx; }.balance-btn { display:flex; align-items:center; justify-content:center; flex:1; gap:8rpx; padding:20rpx; border-radius:16rpx; background:rgba(255,255,255,.2); font-size:26rpx; }.balance-btn:active { background:rgba(255,255,255,.3); }.balance-btn image { width:26rpx; height:26rpx; }
.tab-switch { display:flex; margin-bottom:24rpx; padding:8rpx; border-radius:20rpx; background:#fff; }.tab-switch-item { flex:1; padding:16rpx; border-radius:16rpx; color:#666; font-size:28rpx; text-align:center; }.tab-switch-item.active { color:#fff; font-weight:600; background:#ff6b35; }
.income-section { margin-bottom:32rpx; padding:32rpx; border-radius:24rpx; background:#fff; }.income-title { display:block; margin-bottom:24rpx; color:#333; font-size:30rpx; font-weight:600; }.income-list { display:flex; flex-direction:column; gap:24rpx; }.income-item { display:flex; align-items:center; justify-content:space-between; padding:20rpx 0; border-bottom:1rpx solid #f5f5f5; }.income-item:last-child { border-bottom:0; }.income-info { display:flex; align-items:center; gap:20rpx; min-width:0; }.income-icon { display:flex; align-items:center; justify-content:center; width:72rpx; height:72rpx; flex-shrink:0; border-radius:16rpx; background:#fff3e0; }.income-icon image { width:32rpx; height:32rpx; }.income-name,.income-date { display:block; }.income-name { color:#333; font-size:28rpx; font-weight:500; }.income-date { margin-top:4rpx; color:#999; font-size:24rpx; }.income-amount { flex-shrink:0; margin-left:16rpx; font-size:32rpx; font-weight:600; }.income-amount.income { color:#ff6b35; }.income-amount.expense { color:#999; }
.income-status { display:inline-block; margin-top:8rpx; padding:4rpx 12rpx; border-radius:18rpx; font-size:19rpx; }.income-status.pending,.income-status.processing { color:#f59e0b; background:#fff7e6; }.income-status.success { color:#10b981; background:#ecfdf5; }.income-status.failed { color:#e64340; background:#fff1f0; }.income-status.canceled { color:#888; background:#f2f2f2; }
.empty-state,.load-more { padding:60rpx 0 48rpx; color:#bbb; font-size:26rpx; text-align:center; }.empty-state.retry,.load-more { color:#ff6b35; }
.rules-card { padding:32rpx; border-radius:24rpx; background:#fff; }.rules-title { display:flex; align-items:center; gap:8rpx; margin-bottom:20rpx; color:#333; font-size:28rpx; font-weight:600; }.rules-title image { width:28rpx; height:28rpx; }.rules-item { display:block; color:#666; font-size:24rpx; line-height:1.8; }
.safe-space { height:calc(32rpx + env(safe-area-inset-bottom)); }
.sheet-mask { position:fixed; z-index:20; inset:0; display:flex; align-items:flex-end; background:rgba(0,0,0,.45); }
.action-sheet { width:100%; padding:20rpx 36rpx calc(36rpx + env(safe-area-inset-bottom)); border-radius:32rpx 32rpx 0 0; background:#fff; box-sizing:border-box; }
.sheet-handle { width:72rpx; height:8rpx; margin:0 auto 22rpx; border-radius:8rpx; background:#ddd; }
.sheet-title,.sheet-tip { display:block; text-align:center; }.sheet-title { color:#333; font-size:34rpx; font-weight:700; }.sheet-tip { margin-top:12rpx; color:#999; font-size:24rpx; }
.amount-input-wrap { display:flex; align-items:center; height:96rpx; margin-top:32rpx; padding:0 24rpx; border:2rpx solid #eee; border-radius:18rpx; color:#333; font-size:38rpx; box-sizing:border-box; }.amount-input-wrap input { flex:1; min-width:0; height:96rpx; margin-left:12rpx; font-size:36rpx; }.all-btn { color:#ff6b35; font-size:25rpx; }
.quick-row { display:flex; gap:16rpx; margin-top:20rpx; }.quick-row text { flex:1; padding:16rpx 0; border-radius:12rpx; color:#ff6b35; background:#fff3ed; font-size:24rpx; text-align:center; }
.sheet-confirm { height:88rpx; margin:32rpx 0 0; border:0; border-radius:44rpx; color:#fff; background:linear-gradient(135deg,#ff6b35,#ff8c5a); font-size:30rpx; font-weight:600; line-height:88rpx; }.sheet-confirm::after { border:0; }.sheet-confirm[disabled] { opacity:.6; }
</style>
