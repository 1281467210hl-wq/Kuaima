<template>
  <view class="page">
    <view class="status-spacer" :style="{ height: `${statusBarHeight}px` }" />
    <view class="nav-bar">
      <view class="nav-back" @click="goBack"><image :src="arrowLeftIcon" mode="aspectFit" /></view>
      <text class="nav-title">奖励金提现</text>
      <view class="nav-space" />
    </view>

    <scroll-view scroll-y class="content">
      <view class="balance-card">
        <text class="balance-label">可提现奖励金（元）</text>
        <text class="balance-value">¥{{ formatMoney(balance) }}</text>
      </view>

      <text class="section-title">选择提现方式</text>
      <view class="channel-list">
        <view class="channel-card" :class="{ selected: channel === 'WECHAT' }" @click="selectChannel('WECHAT')">
          <view class="channel-row">
            <view class="channel-icon wechat"><image :src="wechatIcon" mode="aspectFit" /></view>
            <view class="channel-info"><text class="channel-name">微信账户</text></view>
            <view class="radio" />
          </view>
          <view class="account-wrap">
            <input v-model="wechatAccount" placeholder="请输入微信账户（手机号 / 微信号）" @input="onAccountInput" />
          </view>
        </view>

        <view class="channel-card" :class="{ selected: channel === 'ALIPAY', disabled: !alipayEnabled }" @click="selectChannel('ALIPAY')">
          <view class="channel-row">
            <view class="channel-icon alipay"><image :src="alipayIcon" mode="aspectFit" /></view>
            <view class="channel-info">
              <text class="channel-name">支付宝账户</text>
              <text class="channel-desc">{{ alipayEnabled ? "转账至指定支付宝账户" : "待商户转账能力开通后启用" }}</text>
            </view>
            <text v-if="!alipayEnabled" class="disabled-tag">待开通</text>
          </view>
          <view :class="['account-wrap', { disabled: !alipayEnabled }]">
            <input v-model="aliAccount" :disabled="!alipayEnabled" placeholder="请输入支付宝账户（手机号 / 邮箱）" @input="onAccountInput" />
          </view>
        </view>
      </view>

      <text class="section-title">提现金额</text>
      <view class="amount-card">
        <view class="amount-row">
          <text class="currency">¥</text>
          <input v-model="amount" type="digit" placeholder="0.00" @input="onAmountInput" />
          <text class="all-button" @click="fillAll">全部提现</text>
        </view>
        <text v-if="amountError" class="amount-error">{{ amountError }}</text>
      </view>

      <view class="notice-bar">
        <image :src="infoIcon" mode="aspectFit" />
        <text>1元奖励金 = 1元现金；单笔最低提现 ¥10，手续费全免，到账时间以支付渠道为准。</text>
      </view>
      <view class="safe-space" />
    </scroll-view>

    <view class="footer">
      <button class="submit-button" :disabled="!canSubmit || submitting" @click="submit">
        {{ submitting ? "提交中..." : "确认提现" }}
      </button>
    </view>

    <view v-if="successVisible" class="mask">
      <view class="success-modal">
        <view class="success-icon"><image :src="checkIcon" mode="aspectFit" /></view>
        <text class="success-title">提现申请成功</text>
        <text class="success-desc">¥{{ formatMoney(successAmount) }} 将提现至 {{ successAccount }}\n可在奖励金明细中查看进度</text>
        <button class="success-button" @click="finish">返回奖励金</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import {
  createBossRewardWithdrawal,
  getBossRewardOverview,
  getUser,
} from "@/api/backend";
import arrowLeftIcon from "/static/icons/boss-points/arrow-left-dark.svg";
import wechatIcon from "/static/icons/worker-points-withdraw/weixin-white.svg";
import alipayIcon from "/static/icons/worker-points-withdraw/alipay-white.svg";
import infoIcon from "/static/icons/worker-points-withdraw/circle-info-gray.svg";
import checkIcon from "/static/icons/boss-authorize/check-white.svg";

const statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 0;
const balance = ref(0);
const alipayEnabled = ref(false);
const channel = ref("WECHAT");
const wechatAccount = ref("");
const aliAccount = ref("");
const amount = ref("");
const amountError = ref("");
const submitting = ref(false);
const successVisible = ref(false);
const successAmount = ref(0);
const successAccount = ref("");
const idempotencyKey = ref("");

const canSubmit = computed(() => {
  const value = Number(amount.value);
  const account = selectedAccount();
  return String(account || "").trim().length > 0
    && Number.isFinite(value)
    && value >= 10
    && value <= balance.value;
});

onLoad(async (options = {}) => {
  balance.value = Math.max(0, Number(decodeURIComponent(options.balance || "0")) || 0);
  await loadBalance();
  await loadDefaultAccount();
});

async function loadBalance() {
  try {
    const result = await getBossRewardOverview();
    balance.value = Number(result?.withdrawableAmount ?? result?.balance ?? balance.value) || 0;
    alipayEnabled.value = result?.alipayWithdrawEnabled === true;
  } catch (_) {}
}

async function loadDefaultAccount() {
  const userId = uni.getStorageSync("userId");
  if (!userId) return;
  try {
    const user = await getUser(userId);
    wechatAccount.value = String(user?.phone || "").trim();
  } catch (_) {}
}

function selectChannel(value) {
  if (value === "ALIPAY" && !alipayEnabled.value) {
    uni.showToast({ title: "支付宝提现开通后启用", icon: "none" });
    return;
  }
  channel.value = value;
}

function onAccountInput() {
  idempotencyKey.value = "";
}

function onAmountInput(event) {
  const clean = String(event?.detail?.value || "").replace(/[^\d.]/g, "");
  const dot = clean.indexOf(".");
  amount.value = dot < 0 ? clean : `${clean.slice(0, dot)}.${clean.slice(dot + 1).replace(/\./g, "").slice(0, 2)}`;
  idempotencyKey.value = "";
  validateAmount();
}

function fillAll() {
  amount.value = balance.value.toFixed(2);
  idempotencyKey.value = "";
  validateAmount();
}

function validateAmount() {
  const value = Number(amount.value);
  amountError.value = "";
  if (!Number.isFinite(value) || value <= 0) return;
  if (value > balance.value) amountError.value = `提现金额不能超过可提现余额 ¥${formatMoney(balance.value)}`;
  else if (value < 10) amountError.value = "单笔最低提现金额为 ¥10";
}

async function submit() {
  if (submitting.value || !canSubmit.value) return;
  const value = Number(amount.value);
  if (!idempotencyKey.value) idempotencyKey.value = createIdempotencyKey();
  submitting.value = true;
  try {
    const account = selectedAccount();
    await createBossRewardWithdrawal(value, idempotencyKey.value, channel.value, account);
    successAmount.value = value;
    successAccount.value = String(account || "").trim();
    successVisible.value = true;
  } catch (error) {
    uni.showToast({ title: error?.message || "提现失败", icon: "none" });
  } finally {
    submitting.value = false;
  }
}

function finish() {
  successVisible.value = false;
  uni.navigateBack();
}

function goBack() {
  uni.navigateBack();
}

function formatMoney(value) {
  const number = Number(value);
  return (Number.isFinite(number) ? number : 0).toFixed(2);
}

function selectedAccount() {
  return channel.value === "ALIPAY" ? aliAccount.value : wechatAccount.value;
}

function createIdempotencyKey() {
  return `boss-reward-withdraw-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
</script>

<style scoped>
.page { display:flex; flex-direction:column; height:100vh; overflow:hidden; background:#f5f5f5; }
.status-spacer { flex-shrink:0; background:#fff; }
.nav-bar { position:relative; display:flex; align-items:center; justify-content:space-between; height:100rpx; padding:0 32rpx; background:#fff; box-sizing:border-box; }
.nav-back,.nav-space { width:64rpx; height:64rpx; }.nav-back { display:flex; align-items:center; justify-content:center; }.nav-back image { width:36rpx; height:36rpx; }.nav-title { position:absolute; left:50%; transform:translateX(-50%); color:#333; font-size:34rpx; font-weight:600; }
.content { flex:1; min-height:0; height:0; }
.balance-card { margin:32rpx; padding:46rpx; border-radius:28rpx; color:#fff; background:linear-gradient(135deg,#ff6b35,#ff8c5a); box-shadow:0 16rpx 42rpx rgba(255,107,53,.25); }
.balance-label { display:block; font-size:24rpx; opacity:.9; }.balance-value { display:block; margin-top:18rpx; font-size:62rpx; font-weight:800; line-height:1; }
.section-title { display:block; margin:0 32rpx 18rpx; color:#333; font-size:26rpx; font-weight:600; }
.channel-list { margin:0 32rpx 30rpx; }
.channel-card { margin-bottom:20rpx; padding:24rpx; border:3rpx solid #fff; border-radius:24rpx; background:#fff; box-shadow:0 4rpx 16rpx rgba(0,0,0,.04); box-sizing:border-box; }.channel-card.selected { border-color:#ff6b35; background:#fffaf6; }.channel-card.disabled { opacity:.65; }
.channel-row { display:flex; align-items:center; gap:18rpx; }
.channel-icon { display:flex; align-items:center; justify-content:center; flex-shrink:0; width:68rpx; height:68rpx; border-radius:18rpx; }.channel-icon image { width:34rpx; height:34rpx; }.channel-icon.wechat { background:#09bb07; }.channel-icon.alipay { background:#1677ff; }
.channel-info { flex:1; min-width:0; }.channel-name { display:block; color:#333; font-size:27rpx; font-weight:600; }.channel-desc { display:block; margin-top:5rpx; color:#999; font-size:21rpx; }
.radio { flex-shrink:0; width:36rpx; height:36rpx; border:3rpx solid #ddd; border-radius:50%; box-sizing:border-box; }.channel-card.selected .radio { border:11rpx solid #ff6b35; }
.disabled-tag { padding:7rpx 14rpx; border-radius:20rpx; color:#999; background:#f2f2f2; font-size:20rpx; }
.account-wrap { margin-top:20rpx; padding:0 22rpx; border:2rpx solid #eee; border-radius:16rpx; background:#fff; }.account-wrap.disabled { opacity:.65; }.account-wrap input { height:82rpx; color:#333; font-size:24rpx; }
.amount-card { margin:0 32rpx 24rpx; padding:26rpx 28rpx; border-radius:24rpx; background:#fff; box-shadow:0 4rpx 16rpx rgba(0,0,0,.04); }
.amount-row { display:flex; align-items:center; gap:14rpx; }.currency { color:#333; font-size:42rpx; font-weight:700; }.amount-row input { flex:1; min-width:0; height:82rpx; color:#333; font-size:52rpx; font-weight:700; }.all-button { color:#ff6b35; font-size:24rpx; }
.amount-error { display:block; margin-top:10rpx; color:#e64340; font-size:22rpx; }
.notice-bar { display:flex; align-items:flex-start; gap:10rpx; margin:0 32rpx; padding:20rpx 22rpx; border-radius:16rpx; color:#999; background:#fff; font-size:21rpx; line-height:1.6; }.notice-bar image { flex-shrink:0; width:26rpx; height:26rpx; }
.safe-space { height:32rpx; }
.footer { flex-shrink:0; padding:18rpx 32rpx calc(24rpx + env(safe-area-inset-bottom)); background:#fff; box-sizing:border-box; }
.submit-button { height:92rpx; margin:0; border:0; border-radius:46rpx; color:#fff; background:linear-gradient(135deg,#ff6b35,#ff8c5a); box-shadow:0 8rpx 28rpx rgba(255,107,53,.28); font-size:30rpx; font-weight:600; line-height:92rpx; }.submit-button::after { border:0; }.submit-button[disabled] { opacity:.55; }
.mask { position:fixed; z-index:30; inset:0; display:flex; align-items:center; justify-content:center; padding:40rpx; background:rgba(0,0,0,.55); box-sizing:border-box; }
.success-modal { width:100%; padding:48rpx 36rpx; border-radius:30rpx; background:#fff; text-align:center; box-sizing:border-box; }
.success-icon { display:flex; align-items:center; justify-content:center; width:104rpx; height:104rpx; margin:0 auto; border-radius:50%; background:linear-gradient(135deg,#ff6b35,#ff8c5a); box-shadow:0 12rpx 30rpx rgba(255,107,53,.3); }.success-icon image { width:48rpx; height:48rpx; }
.success-title { display:block; margin-top:24rpx; color:#222; font-size:34rpx; font-weight:700; }.success-desc { display:block; margin-top:16rpx; color:#777; font-size:24rpx; line-height:1.7; }
.success-button { height:84rpx; margin:36rpx 0 0; border:0; border-radius:42rpx; color:#fff; background:linear-gradient(135deg,#ff6b35,#ff8c5a); font-size:28rpx; line-height:84rpx; }.success-button::after { border:0; }
</style>
