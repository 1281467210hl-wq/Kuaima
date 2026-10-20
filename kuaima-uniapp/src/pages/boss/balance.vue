<template>
  <view class="container">
    <!-- 导航栏 -->
    <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight}px`, height: `${statusBarHeight + 50}px` }">
      <view class="nav-back" @click="goBack">
        <image class="nav-back-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" />
      </view>
      <text class="nav-title">余额查询</text>
      <view class="nav-right">
        <image class="nav-dots-svg" src="/static/icons/boss-profile/ellipsis.svg" mode="aspectFit" />
      </view>
    </view>

    <scroll-view scroll-y class="body">
      <view v-if="loading" class="page-state">加载中...</view>
      <template v-else>
        <!-- 账户卡片 -->
        <view class="account-card">
          <view class="account-head">
            <text class="account-name">{{ account.accountName || '企业账户' }}</text>
            <text v-if="account.isDefault" class="account-tag">默认账户</text>
          </view>
          <view class="info-row">
            <text class="info-label">主体全称</text>
            <text class="info-value">{{ account.subjectName || '—' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">商户号</text>
            <text class="info-value">{{ maskMerchantNo(account.merchantNo) }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">账户余额</text>
            <text class="balance-value">{{ showBalance ? `¥${formatAccountBalance(account)}` : '¥ **,***.**' }}</text>
          </view>
        </view>

        <!-- 提示 -->
        <view class="tip-card">
          <image class="tip-ico-svg" src="/static/icons/boss-points/circle-info-orange.svg" mode="aspectFit" />
          <text>余额为商户号可用余额，实际到账以银行流水为准；如需充值对公转账后请联系客服处理。</text>
        </view>
      </template>
      <view class="bottom-space" />
    </scroll-view>

    <!-- 底部充值 -->
    <view class="footer">
      <view class="footer-btn primary" @click="openRecharge">充值</view>
    </view>

    <!-- 充值弹框（手机端底部弹层） -->
    <view v-if="rechargeVisible" class="rb-mask" @click="closeRecharge">
      <view class="rb-sheet" @click.stop>
        <view class="rb-handle" />
        <view class="rb-header">
          <text class="rb-title">账户充值</text>
          <text class="rb-close" @click="closeRecharge"><image class="rb-close-svg" src="/static/icons/boss-points/xmark-gray.svg" mode="aspectFit" /></text>
        </view>
        <scroll-view scroll-y class="rb-body">
          <view class="rb-body-inner">
            <view class="rb-section-title">选择充值金额</view>
            <view class="rb-amount-row">
              <view class="rb-amount-chip" :class="{ active: amount === 500 }" @click="pickAmount(500)">¥500</view>
              <view class="rb-amount-chip" :class="{ active: amount === 1000 }" @click="pickAmount(1000)">¥1000</view>
              <view class="rb-amount-chip" :class="{ active: amount === 2000 }" @click="pickAmount(2000)">¥2000</view>
            </view>
            <view class="rb-input-amount">
              <text class="yuan">¥</text>
              <input
                type="digit"
                class="rb-input"
                placeholder="自定义金额（最低0.01）"
                v-model="customAmount"
                @input="onCustomInput"
              />
            </view>

            <view class="rb-section-title">支付方式</view>
            <view class="rb-pay-item active">
              <view class="rb-pay-icon wechat"><image class="rb-pay-svg" src="/static/icons/worker-points-withdraw/weixin-white.svg" mode="aspectFit" /></view>
              <view class="rb-pay-info">
                <text class="rb-pay-name">微信支付</text>
                <text class="rb-pay-desc">推荐 · 实时到账</text>
              </view>
              <view class="rb-pay-radio"><view class="rb-pay-radio-dot" /></view>
            </view>
          </view>
        </scroll-view>
        <view class="rb-footer">
          <view class="rb-confirm-btn" :class="{ disabled: rechargeLoading }" @click="confirmRecharge">
            <text v-if="!rechargeLoading">确认充值 ¥{{ displayAmount }}</text>
            <text v-else>处理中...</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import {
  getBossBalance,
  createBossWalletWechatRecharge,
  getBossWalletRechargeResult,
} from "@/api/backend";

function parsePayload(data) {
  return data?.data ?? data ?? {};
}

export default {
  data() {
    return {
      statusBarHeight: 0,
      loading: false,
      showBalance: false,
      account: {
        accountName: "",
        subjectName: "",
        merchantNo: "",
        balanceFen: 0,
        balanceYuan: "0.00",
        isDefault: true,
      },
      rechargeVisible: false,
      amount: 1000,
      customAmount: "",
      rechargeLoading: false,
      pollingTimer: null,
      rechargeIdempotencyKey: "",
    };
  },
  computed: {
    displayAmount() {
      const v = this.customAmount && Number(this.customAmount) > 0
        ? Number(this.customAmount)
        : this.amount;
      return v;
    },
  },
  onLoad() {
    const info = typeof uni.getWindowInfo === "function"
      ? uni.getWindowInfo() : uni.getSystemInfoSync();
    this.statusBarHeight = Number(info.statusBarHeight || 0);
    this.loadBalance();
  },
  onUnload() {
    if (this.pollingTimer) clearTimeout(this.pollingTimer);
  },
  methods: {
    goBack() {
      uni.navigateBack({
        fail: () => uni.reLaunch({ url: "/pages/boss/workbench" }),
      });
    },
    async loadBalance() {
      this.loading = true;
      try {
        const data = await getBossBalance();
        const body = parsePayload(data);
        // 后端返回 balanceFen(分) 和 balanceYuan(元) 都可用
        this.account = { ...this.account, ...body };
        this.showBalance = true;
      } catch (error) {
        this.showBalance = false;
      } finally {
        this.loading = false;
      }
    },
    maskMerchantNo(no) {
      const n = String(no || "");
      if (!n) return "—";
      if (n.length <= 6) return n;
      return `${n.slice(0, 5)}**${n.slice(-3)}`;
    },
    formatAccountBalance(account) {
      const yuan = Number(account?.balanceYuan);
      if (Number.isFinite(yuan)) {
        return yuan.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
      }
      const fen = Number(account?.balanceFen ?? account?.balance);
      return (Number.isFinite(fen) ? fen / 100 : 0).toLocaleString("zh-CN", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });
    },

    // ---------- 充值弹窗 ----------
    openRecharge() {
      this.rechargeVisible = true;
      this.rechargeIdempotencyKey = "";
    },
    closeRecharge() {
      this.rechargeVisible = false;
      if (this.pollingTimer) {
        clearTimeout(this.pollingTimer);
        this.pollingTimer = null;
      }
    },
    pickAmount(v) {
      this.amount = v;
      this.customAmount = "";
      this.rechargeIdempotencyKey = "";
    },
    onCustomInput(event) {
      const value = String(event?.detail?.value || "").replace(/[^\d.]/g, "");
      const dot = value.indexOf(".");
      this.customAmount = dot < 0 ? value : `${value.slice(0, dot)}.${value.slice(dot + 1).replace(/\./g, "").slice(0, 2)}`;
      this.rechargeIdempotencyKey = "";
      const n = Number(this.customAmount);
      if (!Number.isNaN(n) && n > 0) {
        // 清除选中的快捷金额
        this.amount = -1;
      }
    },
    async confirmRecharge() {
      const amt = this.displayAmount;
      if (!/^(?:\d+)(?:\.\d{1,2})?$/.test(String(amt)) || Number(amt) < 0.01) {
        uni.showToast({ title: "最低充值金额为 ¥0.01，最多两位小数", icon: "none" });
        return;
      }
      if (this.rechargeLoading) return;
      // #ifndef MP-WEIXIN
      uni.showToast({ title: "请在微信小程序内完成支付", icon: "none" });
      return;
      // #endif
      if (!this.rechargeIdempotencyKey) this.rechargeIdempotencyKey = `boss-wallet-recharge-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      this.rechargeLoading = true;
      try {
        const res = await createBossWalletWechatRecharge({ amount: Number(Number(amt).toFixed(2)) }, this.rechargeIdempotencyKey);
        const body = parsePayload(res);
        const orderNo = body.orderNo;
        const payParams = body.payParams || {};
        if (!orderNo || !payParams.package || !payParams.paySign || !payParams.nonceStr || !payParams.timeStamp) throw new Error("微信支付参数无效");
        // #ifdef MP-WEIXIN
        await new Promise((resolve, reject) => uni.requestPayment({
          provider: "wxpay",
          timeStamp: String(payParams.timeStamp),
          nonceStr: String(payParams.nonceStr),
          package: String(payParams.package),
          signType: String(payParams.signType || "RSA"),
          paySign: String(payParams.paySign),
          success: resolve,
          fail: reject,
        }));
        this.startPayPolling(orderNo);
        // #endif
      } catch (e) {
        const message = String(e?.errMsg || e?.message || "微信支付失败");
        uni.showToast({ title: /cancel/i.test(message) ? "支付已取消" : message, icon: "none" });
      } finally {
        this.rechargeLoading = false;
      }
    },
    startPayPolling(orderNo) {
      let ticks = 0;
      const poll = async () => {
        ticks++;
        try {
          const res = await getBossWalletRechargeResult(orderNo);
          const body = parsePayload(res);
          if (body.status === "paid") {
            clearTimeout(this.pollingTimer);
            this.pollingTimer = null;
            this.rechargeIdempotencyKey = "";
            uni.showToast({ title: "充值成功", icon: "success" });
            this.closeRecharge();
            this.loadBalance();
            return;
          } else if (ticks >= 15) {
            clearTimeout(this.pollingTimer);
            this.pollingTimer = null;
            uni.showToast({ title: "支付结果确认中，请稍后在充值记录中查看", icon: "none" });
            return;
          }
        } catch (e) {
          if (ticks >= 15) {
            clearTimeout(this.pollingTimer);
            this.pollingTimer = null;
            uni.showToast({ title: "支付结果确认中，请稍后在充值记录中查看", icon: "none" });
            return;
          }
        }
        this.pollingTimer = setTimeout(poll, 2000);
      };
      poll();
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
.nav-back, .nav-right { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; font-size: 18px; color: #333; }
.nav-back-svg { width: 18px; height: 18px; }
.nav-dots-svg { width: 18px; height: 18px; }
.nav-title { font-size: 17px; font-weight: 600; color: #333; }

.body {
  flex: 1;
  min-height: 0;
  padding: 12px 16px 0;
  width: 100%;
  box-sizing: border-box;
}

.page-state { padding: 32px 0; color: #999; font-size: 14px; text-align: center; }

.account-card {
  background: #fff; border-radius: 16px; padding: 20px 18px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04);
  width: 100%;
  box-sizing: border-box;
}
.account-head { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.account-name { font-size: 16px; font-weight: 700; color: #333; }
.account-tag {
  font-size: 11px; padding: 2px 8px; border-radius: 6px;
  background: #e6f7ff; color: #1890ff; font-weight: 500; flex-shrink: 0;
}
.info-row {
  display: flex; justify-content: space-between; font-size: 13px;
  padding: 10px 0; border-bottom: 0.5px solid #F5F5F5;
}
.info-row:last-child { border-bottom: none; }
.info-label { color: #999; }
.info-value { color: #333; font-weight: 500; word-break: break-all; }
.balance-value { color: #FF6B35; font-weight: 700; font-size: 15px; }

.tip-card {
  margin-top: 14px; background: #FFF7E8; border-radius: 12px;
  padding: 12px 14px; font-size: 12px; color: #A0620D; line-height: 1.7;
  width: 100%;
  box-sizing: border-box;
  display: flex;
  gap: 5px;
}
.tip-ico { flex-shrink: 0; }
.tip-ico-svg { width: 14px; height: 14px; flex-shrink: 0; margin-top: 2px; }
.bottom-space { height: 20px; }

.footer {
  display: flex; padding: 12px 16px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom));
  background: #fff; border-top: 0.5px solid #eee;
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;
}
.footer-btn {
  flex: 1; text-align: center; padding: 12px 0; border-radius: 24px;
  font-size: 14px; font-weight: 600;
}
.footer-btn.primary { background: linear-gradient(135deg, #FF6B35, #FF8C5A); color: #fff; }

/* ---- 充值弹框（对齐上传 HTML） ---- */
.rb-mask {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.5);
  z-index: 100;
  display: flex; align-items: flex-end; justify-content: center;
}
.rb-sheet {
  width: 100%;
  max-height: 80vh;
  background: #fff;
  border-radius: 18px 18px 0 0;
  display: flex; flex-direction: column;
  box-sizing: border-box;
  animation: rbSlideUp 0.25s ease-out;
}
@keyframes rbSlideUp {
  from { transform: translateY(30px); }
  to { transform: translateY(0); }
}
.rb-handle {
  width: 36px; height: 4px; background: #E5E5E5; border-radius: 2px;
  margin: 8px auto 0;
}
.rb-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px 12px;
}
.rb-title { font-size: 16px; font-weight: 600; color: #333; }
.rb-close { padding: 4px; display: flex; align-items: center; justify-content: center; }
.rb-close-svg { width: 14px; height: 14px; }
.rb-body { flex: 1; min-height: 0; width: 100%; box-sizing: border-box; }
.rb-body-inner { padding: 0 18px 8px; box-sizing: border-box; }

.rb-section-title {
  font-size: 13px; font-weight: 600; color: #666;
  margin-bottom: 12px; margin-top: 4px;
}
.rb-section-title:first-child { margin-top: 0; }

.rb-amount-row { display: flex; gap: 10px; margin-bottom: 18px; }
.rb-amount-chip {
  flex: 1; height: 42px;
  border: 1.5px solid #EEE; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; color: #666; background: #FAFAFA;
}
.rb-amount-chip.active {
  border-color: #FF6B35; color: #FF6B35; background: #FFF7F3; font-weight: 600;
}

.rb-input-amount {
  display: flex; align-items: center;
  border: 1.5px solid #EEE; border-radius: 10px;
  padding: 0 12px; height: 42px; margin-bottom: 18px;
  background: #FAFAFA;
}
.rb-input-amount .yuan { font-size: 14px; color: #999; margin-right: 6px; }
.rb-input { flex: 1; border: none; outline: none; background: transparent; font-size: 15px; font-weight: 600; color: #333; height: 42px; }

.rb-pay-item {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 0; border-bottom: 1px solid #F5F5F5;
}
.rb-pay-item:last-child { border-bottom: none; }
.rb-pay-icon {
  width: 36px; height: 36px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; color: #fff; flex-shrink: 0;
}
.rb-pay-icon.wechat { background: #07C160; }
.rb-pay-icon.alipay { background: #1677FF; }
.rb-pay-svg { width: 20px; height: 20px; }
.rb-pay-info { flex: 1; min-width: 0; }
.rb-pay-name { font-size: 14px; font-weight: 600; color: #333; display: block; }
.rb-pay-desc { font-size: 11px; color: #999; margin-top: 2px; display: block; }
.rb-pay-radio {
  width: 20px; height: 20px; border-radius: 50%;
  border: 2px solid #DDD; display: flex;
  align-items: center; justify-content: center; flex-shrink: 0;
}
.rb-pay-item.active .rb-pay-radio { border-color: #FF6B35; }
.rb-pay-radio-dot { width: 10px; height: 10px; border-radius: 50%; background: #FF6B35; }

.rb-footer {
  padding: 14px 18px calc(14px + env(safe-area-inset-bottom));
  border-top: 0.5px solid #F0F0F0;
}
.rb-confirm-btn {
  width: 100%; height: 46px; border-radius: 23px;
  background: linear-gradient(135deg, #FF6B35, #FF8C5A);
  color: #fff; font-size: 15px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}
.rb-confirm-btn.disabled { opacity: 0.6; }
</style>
