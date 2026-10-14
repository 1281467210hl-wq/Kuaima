<template>
  <view class="container">
    <!-- 导航栏 -->
    <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight}px`, height: `${50 + statusBarHeight}px` }">
      <view class="nav-back" @click="goBack">
        <text>‹</text>
      </view>
      <text class="nav-title">确认付款</text>
      <view class="nav-right"></view>
    </view>

    <scroll-view scroll-y class="content-area">
      <!-- 付款金额 -->
      <view class="amount-card">
        <text class="amount-label">付款金额</text>
        <text class="amount-value">¥{{ finalAmount.toFixed(2) }}</text>
        <text class="amount-desc">共{{ count }}笔订单 · 待付款</text>
      </view>

      <!-- 付款订单 -->
      <view class="section-card">
        <text class="section-title">付款订单</text>
        <view class="order-summary">
          <text class="summary-tag" v-for="i in Math.min(count, 5)" :key="i">订单{{ i }}</text>
          <text class="summary-tag" v-if="count > 5">等{{ count }}笔</text>
        </view>
      </view>

      <!-- 费用明细 -->
      <view class="section-card">
        <text class="section-title">费用明细</text>
        <view class="fee-row">
          <text class="fee-label">订单总金额</text>
          <text class="fee-value">{{ formatFee(orderAmount) }}</text>
        </view>
        <view class="fee-row">
          <text class="fee-label">平台服务费</text>
          <text class="fee-value fee-accent">{{ formatFee(serviceFee) }}</text>
        </view>
        <view class="fee-row points-input-row">
          <view class="fee-label-wrap">
            <text class="fee-label">积分抵扣</text>
            <text class="fee-hint">{{ pointsHint }}</text>
          </view>
          <view class="points-input-wrap">
            <input
              class="points-input"
              type="number"
              :value="pointsToUse"
              placeholder="输入积分"
              @input="handlePointsInput"
            />
            <text class="points-unit">积分</text>
          </view>
        </view>
        <view class="fee-row">
          <text class="fee-label">积分抵扣金额</text>
          <text class="fee-value fee-green">{{ formatFee(pointsDeductAmount, true) }}</text>
        </view>
        <view class="fee-row coupon-row" @click="openCouponPicker">
          <text class="fee-label">优惠券</text>
          <text class="coupon-value">{{ couponLabel }} <text class="coupon-arrow">›</text></text>
        </view>
        <view class="fee-row">
          <text class="fee-label">优惠券抵扣</text>
          <text class="fee-value fee-green">{{ formatFee(couponDeductAmount, true) }}</text>
        </view>
        <view class="fee-row fee-total-row">
          <text class="fee-total-label">实际支付金额</text>
          <text class="fee-total-value">¥{{ finalAmount.toFixed(2) }}</text>
        </view>
      </view>

      <view v-if="couponPickerVisible" class="coupon-picker-mask" @click="closeCouponPicker">
        <view class="coupon-picker" @click.stop>
          <view class="coupon-picker-header">
            <text class="coupon-picker-title">选择优惠券</text>
            <text class="coupon-picker-close" @click="closeCouponPicker">×</text>
          </view>
          <scroll-view scroll-y class="coupon-picker-list">
            <view
              v-for="coupon in couponOptions"
              :key="coupon.userCouponId || coupon.id || coupon.couponId"
              class="coupon-option"
              :class="{ selected: String(selectedUserCouponId) === String(coupon.userCouponId) }"
              @click="selectCoupon(coupon)"
            >
              <view>
                <text class="coupon-option-name">{{ coupon.name || coupon.title || "优惠券" }}</text>
                <text class="coupon-option-desc">满{{ coupon.threshold || coupon.minSpend || 0 }}元可用 · 抵扣¥{{ Number(coupon.deductAmount ?? coupon.amount ?? 0).toFixed(2) }}</text>
              </view>
              <text class="coupon-option-check">{{ String(selectedUserCouponId) === String(coupon.userCouponId) ? "✓" : "" }}</text>
            </view>
            <view v-if="!couponOptions.length" class="coupon-empty">暂无可用优惠券</view>
          </scroll-view>
        </view>
      </view>

      <!-- 服务费收款账户 -->
      <view class="section-card">
        <text class="section-title">服务费收款账户</text>
        <view style="display:flex;align-items:center;gap:12px;margin-bottom:12px;">
          <view style="width:40px;height:40px;background:linear-gradient(135deg,#FF6B35,#FF8C5A);border-radius:10px;display:flex;align-items:center;justify-content:center;">
            <text style="color:white;font-size:18px;">🏛</text>
          </view>
          <view>
            <text style="font-weight:600;color:#333;font-size:14px;">快马日结平台</text>
            <text style="font-size:12px;color:#999;display:block;">平台服务费将汇入以下账户</text>
          </view>
        </view>
        <view style="background:#FAFAFA;border-radius:10px;padding:12px;">
          <view style="display:flex;justify-content:space-between;padding:6px 0;font-size:13px;">
            <text style="color:#999;">收款账户</text>
            <text style="color:#333;">快马日结科技有限公司</text>
          </view>
          <view style="display:flex;justify-content:space-between;padding:6px 0;font-size:13px;">
            <text style="color:#999;">收款银行</text>
            <text style="color:#333;">工商银行上海分行</text>
          </view>
          <view style="display:flex;justify-content:space-between;padding:6px 0;font-size:13px;">
            <text style="color:#999;">银行账号</text>
            <text style="color:#333;font-family:monospace;">6222 **** **** 8888</text>
          </view>
        </view>
      </view>

      <!-- 付款账号 -->
      <view class="section-card">
        <text class="section-title">付款账号</text>
        <view class="account-type-list">
          <view 
            class="account-type-item" 
            :class="{ active: selectedAccount === type.value }"
            v-for="type in accountTypes" 
            :key="type.value"
            @click="selectAccount(type.value)"
          >
            <view class="account-type-icon" :class="type.value">
              <text class="account-icon-text">{{ type.value === 'wechat' ? '微' : type.value === 'alipay' ? '支' : '¥' }}</text>
            </view>
            <view class="account-type-info">
              <text class="account-type-name">{{ type.name }}</text>
              <text class="account-type-desc">{{ type.desc }}</text>
            </view>
            <view class="account-type-check"></view>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 底部按钮 -->
    <view class="bottom-bar">
      <button class="confirm-btn" :disabled="!selectedAccount || submitting" @click="submitSettle">
        {{ submitting ? '处理中...' : `确认付款${selectedAccount ? ` ¥${finalAmount.toFixed(2)}` : ''}` }}
      </button>
    </view>
  </view>
</template>

<script>
import {
  createBossSettlementWechatPayment,
  getBossSettlementPayment,
  previewBossSettlementPayment,
} from "@/api/backend";

function parseSettlementIds(value) {
  const toNumberArray = (items) => {
    if (!Array.isArray(items)) return [];
    return items
      .filter((item) => !Array.isArray(item) && item !== "" && item != null)
      .map((item) => Number(item))
      .filter((item) => Number.isSafeInteger(item) && item > 0);
  };

  if (Array.isArray(value)) return toNumberArray(value);

  let text = String(value ?? "").trim();
  if (!text) return [];

  // 页面参数通常是 encodeURIComponent(JSON.stringify(ids)) 的结果，例如 %5B27%5D。
  try {
    text = decodeURIComponent(text);
  } catch (_) {}
  text = text.trim();

  try {
    const parsed = JSON.parse(text);
    if (Array.isArray(parsed)) return toNumberArray(parsed);
    // 不把 JSON 字符串再次包裹成数组；单个数字仅作为兼容输入解析。
    if (typeof parsed === "number") return toNumberArray([parsed]);
    if (typeof parsed === "string" && parsed.trim() !== text) {
      return toNumberArray(parsed.split(","));
    }
  } catch (_) {}

  return toNumberArray(text.split(",").map((item) => item.trim()));
}

function createIdempotencyKey() {
  return `boss-settlement-payment-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export default {
  data() {
    return {
      statusBarHeight: 0,
      settlementIds: [],
      amount: 0,
      count: 0,
      orderAmount: null,
      serviceFee: null,
      pointsAvailable: 0,
      pointsDeductRate: 100,
      pointsToUse: "",
      pointsHint: "可用积分暂未获取",
      pointsDeductAmount: 0,
      pointsPreviewTimer: null,
      couponLabel: "暂不可用",
      couponDeductAmount: 0,
      couponOptions: [],
      selectedUserCouponId: null,
      selectedCouponName: "",
      couponPickerVisible: false,
      finalAmount: 0,
      selectedAccount: "wechat",
      submitting: false,
      paymentNo: "",
      paymentTimer: null,
      polling: false,
      idempotencyKey: "",
      accountTypes: [
        { value: 'wechat', name: '微信支付账号', desc: '使用微信支付付款', icon: 'icon-wechat' },
      ]
    }
  },
  onLoad(options = {}) {
    try { const info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync(); this.statusBarHeight = Number(info.statusBarHeight || 0) } catch (_) {}
    // 兼容旧入口和原型中的 ids 参数，但最终统一转换为数字数组。
    this.settlementIds = parseSettlementIds(options.settlementIds ?? options.ids)
    this.count = this.settlementIds.length || Number(options.count || 0)
    const displayAmount = Number(options.amount)
    if (Number.isFinite(displayAmount) && displayAmount >= 0) {
      this.amount = displayAmount
      this.orderAmount = displayAmount
      this.finalAmount = displayAmount
    }
    if (this.settlementIds.length) this.loadPaymentPreview()
  },
  onUnload() {
    this.stopPaymentPolling()
    if (this.pointsPreviewTimer) clearTimeout(this.pointsPreviewTimer)
  },
  methods: {
    async loadPaymentPreview() {
      try {
        const result = await previewBossSettlementPayment(
          this.settlementIds,
          this.selectedUserCouponId,
          Number(this.pointsToUse) || 0,
        )
        const preview = result?.data || result || {}
        const orderAmount = Number(preview.orderAmount)
        const serviceFee = Number(preview.serviceFee)
        const pointsAvailable = Number(preview.pointsAvailable)
        const pointsDeductAmount = Number(preview.pointsDeductAmount)
        const couponDeductAmount = Number(preview.couponDeductAmount)
        if (Number.isFinite(orderAmount)) this.orderAmount = orderAmount
        if (Number.isFinite(serviceFee)) this.serviceFee = serviceFee
        if (Number.isFinite(pointsAvailable)) {
          const rate = Number(preview.pointsDeductRate)
          this.pointsAvailable = Math.max(0, Math.floor(pointsAvailable))
          if (Number.isFinite(rate) && rate > 0) this.pointsDeductRate = rate
          this.pointsHint = `可用积分 ${pointsAvailable}${Number.isFinite(rate) && rate > 0 ? ` · ${rate}积分 = ¥1` : ""}`
        }
        const pointsUsed = Number(preview.pointsUsed)
        if (Number.isFinite(pointsUsed)) {
          this.pointsToUse = pointsUsed > 0 ? String(pointsUsed) : ""
          this.pointsDeductAmount = Number((pointsUsed / this.pointsDeductRate).toFixed(2))
        } else if (Number.isFinite(pointsDeductAmount)) {
          this.pointsDeductAmount = pointsDeductAmount
        }
        if (Number.isFinite(couponDeductAmount)) this.couponDeductAmount = couponDeductAmount
        // 该字段由后端按当前结算单金额筛选，作为最终可用优惠券列表。
        const options = preview.couponOptions
        this.couponOptions = Array.isArray(options) ? options : []
        this.couponLabel = preview.couponAvailable === true
          ? (preview.couponName || this.selectedCouponName || "可用优惠券")
          : "暂不可用"
        const payableAmount = Number(preview.payableAmount ?? preview.amount)
        if (Number.isFinite(payableAmount) && payableAmount >= 0) {
          this.amount = payableAmount
          this.finalAmount = payableAmount
        }
      } catch (error) {
        uni.showToast({ title: error?.message || "费用明细加载失败", icon: "none" })
      }
    },
    openCouponPicker() {
      if (!this.couponOptions.length) {
        uni.showToast({ title: "暂无可用优惠券", icon: "none" })
        return
      }
      this.couponPickerVisible = true
    },
    closeCouponPicker() {
      this.couponPickerVisible = false
    },
    selectCoupon(coupon) {
      // 结算优惠券接口使用用户券记录 ID，不使用 couponId 作为支付入参。
      const userCouponId = coupon?.userCouponId
      if (userCouponId === undefined || userCouponId === null || userCouponId === "") {
        uni.showToast({ title: "优惠券信息不完整，请刷新后重试", icon: "none" })
        return
      }
      this.selectedUserCouponId = userCouponId
      this.selectedCouponName = coupon.name || coupon.title || "优惠券"
      this.couponPickerVisible = false
      if (this.pointsPreviewTimer) clearTimeout(this.pointsPreviewTimer)
      this.loadPaymentPreview()
    },
    formatFee(value, negative = false) {
      const amount = Number(value)
      if (!Number.isFinite(amount)) return "暂未提供"
      return `${negative ? "-" : ""}¥${amount.toFixed(2)}`
    },
    handlePointsInput(event) {
      const raw = String(event?.detail?.value ?? "").replace(/\D/g, "")
      if (!raw) {
        this.pointsToUse = ""
        this.pointsDeductAmount = 0
        return
      }
      const value = Math.min(Number(raw), this.pointsAvailable)
      this.pointsToUse = String(Number.isFinite(value) ? value : 0)
      this.pointsDeductAmount = Number(
        (Number(this.pointsToUse) / this.pointsDeductRate).toFixed(2),
      )
      const payableBeforePoints = Number(
        (Number(this.orderAmount || 0)
          + Number(this.serviceFee || 0)
          - Number(this.couponDeductAmount || 0)).toFixed(2),
      )
      this.finalAmount = Math.max(0, Number((payableBeforePoints - this.pointsDeductAmount).toFixed(2)))
      if (this.pointsPreviewTimer) clearTimeout(this.pointsPreviewTimer)
      this.pointsPreviewTimer = setTimeout(() => {
        this.pointsPreviewTimer = null
        this.loadPaymentPreview()
      }, 400)
    },
    goBack() {
      uni.navigateBack()
    },
    selectAccount(value) {
      if (value !== "wechat") {
        uni.showToast({ title: "当前仅支持微信支付", icon: "none" })
        return
      }
      this.selectedAccount = value
    },
    async submitSettle() {
      if (this.submitting) return
      if (this.selectedAccount !== "wechat") {
        uni.showToast({ title: "当前仅支持微信支付", icon: "none" })
        return
      }
      if (!this.settlementIds.length) {
        uni.showToast({ title: "缺少待付款结算单", icon: "none" })
        return
      }
      if (!this.idempotencyKey) this.idempotencyKey = createIdempotencyKey()
      this.submitting = true
      uni.showLoading({ title: "创建支付订单...", mask: true })
      try {
        const result = await createBossSettlementWechatPayment(
          this.settlementIds,
          this.idempotencyKey,
          this.selectedUserCouponId,
          Number(this.pointsToUse) || 0,
        )
        const payment = result?.data || result || {}
        const orderAmount = Number(payment.orderAmount)
        const serviceFee = Number(payment.serviceFee)
        const pointsAvailable = Number(payment.pointsAvailable)
        const pointsDeductAmount = Number(payment.pointsDeductAmount)
        const couponDeductAmount = Number(payment.couponDeductAmount)
        if (Number.isFinite(orderAmount)) this.orderAmount = orderAmount
        if (Number.isFinite(serviceFee)) this.serviceFee = serviceFee
        if (Number.isFinite(pointsAvailable)) {
          const rate = Number(payment.pointsDeductRate)
          this.pointsAvailable = Math.max(0, Math.floor(pointsAvailable))
          if (Number.isFinite(rate) && rate > 0) this.pointsDeductRate = rate
          this.pointsHint = `可用积分 ${pointsAvailable}${Number.isFinite(rate) && rate > 0 ? ` · ${rate}积分 = ¥1` : ""}`
        }
        if (Number.isFinite(pointsDeductAmount)) this.pointsDeductAmount = pointsDeductAmount
        if (Number.isFinite(couponDeductAmount)) this.couponDeductAmount = couponDeductAmount
        this.couponLabel = payment.couponAvailable === true
          ? (payment.couponName || "可用优惠券")
          : "暂不可用"
        const serverAmount = Number(payment.amount)
        if (Number.isFinite(serverAmount) && serverAmount >= 0) {
          this.amount = serverAmount
          this.finalAmount = serverAmount
        }
        this.paymentNo = payment.paymentNo || payment.orderNo || ""
        const payParams = payment.payParams || {}
        if (String(payment.status || "").toUpperCase() === "PAID" && Number(serverAmount) === 0) {
          uni.hideLoading()
          this.submitting = false
          uni.showToast({ title: "支付成功", icon: "success" })
          setTimeout(() => uni.navigateBack(), 700)
          return
        }
        const required = ["timeStamp", "nonceStr", "package", "paySign"]
        if (!this.paymentNo || required.some((key) => !payParams[key])) {
          throw new Error("支付参数不完整，请稍后重试")
        }
        uni.hideLoading()
        await this.requestWechatPayment(payParams)
        uni.showToast({ title: "支付处理中", icon: "none" })
        this.startPaymentPolling(this.paymentNo)
      } catch (error) {
        uni.hideLoading()
        this.submitting = false
        const message = String(error?.errMsg || error?.message || "支付失败")
        uni.showToast({
          title: /cancel/i.test(message) ? "支付已取消" : message,
          icon: "none",
        })
      }
    },
    requestWechatPayment(payParams) {
      return new Promise((resolve, reject) => {
        // #ifdef H5
        reject(new Error("请在微信小程序内完成支付"))
        // #endif
        // #ifndef H5
        uni.requestPayment({
          provider: "wxpay",
          timeStamp: String(payParams.timeStamp),
          nonceStr: String(payParams.nonceStr),
          package: String(payParams.package),
          signType: String(payParams.signType || "RSA"),
          paySign: String(payParams.paySign),
          success: resolve,
          fail: reject,
        })
        // #endif
      })
    },
    startPaymentPolling(paymentNo) {
      this.stopPaymentPolling()
      this.polling = true
      let attempts = 0
      const poll = async () => {
        if (!this.polling) return
        attempts += 1
        try {
          const result = await getBossSettlementPayment(paymentNo)
          const payment = result?.data || result || {}
          const status = String(payment.status || "").toUpperCase()
          if (status === "PAID") {
            this.stopPaymentPolling()
            this.submitting = false
            uni.showToast({ title: "支付成功", icon: "success" })
            setTimeout(() => uni.navigateBack(), 700)
            return
          }
          if (["FAILED", "CLOSED", "CANCELLED", "REFUNDED"].includes(status)) {
            this.stopPaymentPolling()
            this.submitting = false
            uni.showToast({
              title: payment.message || payment.failureReason || "支付失败",
              icon: "none",
            })
            return
          }
        } catch (_) {
          // 轮询期间的瞬时错误继续重试，避免误判为支付失败。
        }
        if (attempts >= 15) {
          this.stopPaymentPolling()
          this.submitting = false
          uni.showToast({ title: "支付结果确认中，请稍后查看结算记录", icon: "none" })
          return
        }
        this.paymentTimer = setTimeout(poll, 2000)
      }
      poll()
    },
    stopPaymentPolling() {
      this.polling = false
      if (this.paymentTimer) {
        clearTimeout(this.paymentTimer)
        this.paymentTimer = null
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.container {
  width: 100%;
  height: 100vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.nav-bar {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: #fff;
}

.nav-back {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #333;
}

.nav-title {
  font-size: 17px;
  font-weight: 600;
  color: #333;
}

.amount-card {
  background: linear-gradient(135deg, #FFE4B5 0%, #FFD700 100%);
  margin: 12px 16px;
  border-radius: 16px;
  padding: 20px;
  text-align: center;
}

.content-area {
  flex: 1;
  min-height: 0;
  height: 0;
  box-sizing: border-box;
  overflow-y: auto;
}

.bottom-bar {
  flex-shrink: 0;
  background: #fff;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}

.amount-label {
  font-size: 13px;
  color: #666;
  display: block;
}

.amount-value {
  font-size: 36px;
  font-weight: 700;
  margin-top: 8px;
  color: #333;
  display: block;
}

.amount-desc {
  font-size: 12px;
  color: #888;
  margin-top: 6px;
  display: block;
}

.section-card {
  background: white;
  margin: 12px 16px;
  border-radius: 16px;
  padding: 16px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 14px;
  display: block;
}

.fee-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  padding: 8px 0;
  border-bottom: 1px dashed #f0f0f0;
  box-sizing: border-box;
}

.fee-label-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.fee-label,
.fee-value,
.coupon-value {
  color: #666;
  font-size: 14px;
}

.fee-value {
  color: #333;
  font-weight: 500;
}

.fee-accent { color: #ff6b35; }
.fee-green { color: #059669; }

.fee-hint {
  color: #bbb;
  font-size: 11px;
}

.points-input-row { align-items: flex-start; }

.points-input-wrap {
  display: flex;
  align-items: center;
  height: 34px;
  background: #fafafa;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  overflow: hidden;
  opacity: .75;
}

.points-input {
  width: 88px;
  height: 34px;
  padding: 0 4px 0 10px;
  color: #333;
  font-size: 13px;
  text-align: right;
  background: transparent;
}

.points-unit {
  padding: 0 10px 0 4px;
  color: #999;
  font-size: 12px;
}

.coupon-row { align-items: center; }
.coupon-value { color: #999; }
.coupon-arrow { color: #bbb; font-size: 16px; }

.coupon-picker-mask {
  position: fixed;
  z-index: 30;
  inset: 0;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, .45);
}

.coupon-picker {
  width: 100%;
  max-height: 65vh;
  padding-bottom: env(safe-area-inset-bottom);
  background: #fff;
  border-radius: 20px 20px 0 0;
}

.coupon-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.coupon-picker-title { color: #333; font-size: 16px; font-weight: 600; }
.coupon-picker-close { color: #999; font-size: 24px; line-height: 1; }

.coupon-picker-list { max-height: 48vh; padding: 8px 16px 16px; box-sizing: border-box; }

.coupon-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 62px;
  padding: 12px;
  margin-top: 8px;
  background: #fafafa;
  border: 1px solid #eee;
  border-radius: 10px;
}

.coupon-option.selected { border-color: #ff6b35; background: #fff8f5; }
.coupon-option-name { display: block; color: #333; font-size: 14px; font-weight: 600; }
.coupon-option-desc { display: block; margin-top: 5px; color: #999; font-size: 12px; }
.coupon-option-check { color: #ff6b35; font-size: 18px; font-weight: 700; }
.coupon-empty { padding: 36px 0; color: #999; font-size: 13px; text-align: center; }

.fee-total-row {
  margin-top: 4px;
  border-bottom: 0;
}

.fee-total-label {
  color: #333;
  font-size: 15px;
  font-weight: 600;
}

.fee-total-value {
  color: #ff6b35;
  font-size: 18px;
  font-weight: 700;
}

.order-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.summary-tag {
  background: #FFF8F5;
  color: #FF6B35;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 10px;
}

.account-type-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.account-type-item {
  display: flex;
  align-items: center;
  padding: 14px;
  border: 1.5px solid #eee;
  border-radius: 12px;
}

.account-type-item.active {
  border-color: #FF6B35;
  background: #FFF8F5;
}

.account-type-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  flex-shrink: 0;
}

.account-type-icon.wechat { background: #E8F8E8; color: #09BB07; }
.account-type-icon.alipay { background: #E6F0FF; color: #1677FF; }
.account-type-icon.bank { background: #FFF5E6; color: #FF8C00; }

.account-type-info {
  flex: 1;
}

.account-type-name {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  display: block;
}

.account-type-desc {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
  display: block;
}

.account-type-check {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid #ddd;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.account-type-item.active .account-type-check {
  border-color: #FF6B35;
  background: #FF6B35;
}

.account-type-item.active .account-type-check::after {
  content: '';
  width: 8px;
  height: 8px;
  background: white;
  border-radius: 50%;
}

.confirm-btn {
  width: 100%;
  height: 48px;
  background: linear-gradient(135deg, #FF6B35, #FF8C5A);
  color: white;
  border: none;
  border-radius: 24px;
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  line-height: 48px;
}

.confirm-btn::after { border: none; }

.confirm-btn:disabled {
  opacity: 0.5;
}
</style>
