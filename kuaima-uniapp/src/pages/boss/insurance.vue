<template>
  <view class="page-mask">
    <view class="modal">
      <view class="modal-header">
        <view class="modal-title">
          <text class="title-icon">🛡</text>
          <text>零工意外保障</text>
        </view>
        <view class="header-actions">
          <view class="toggle" :class="{ off: !enabled }" @click="enabled = !enabled"></view>
          <text class="close-btn" @click="goBack">✕</text>
        </view>
      </view>

      <scroll-view scroll-y class="modal-body">
        <view class="info-block">
          <view class="info-num">
            <view class="n"><text>1</text></view>
            <text>零工开工打卡后，连续保障24小时</text>
          </view>
          <view class="info-num">
            <view class="n"><text>2</text></view>
            <text>保障生效期间，零工来回路上均有保障</text>
          </view>
        </view>
        <view class="info-disclaimer">
          <text>注：根据《工伤保险条例》，上下班途中受到非本人主要责任的交通事故应当认定为工伤，雇主应承担相应赔偿责任。</text>
        </view>

        <view class="service-block">
          <view class="service-header">
            <text class="service-title">服务包</text>
            <view v-if="currentProduct" class="service-meta">
              <text class="meta-icon">🛡</text>
              <text>{{ currentProduct.carrier }}承保</text>
              <text class="divider">|</text>
              <text class="link" @click="goDetail">更多详情 ›</text>
            </view>
          </view>
          <view v-if="products.length > 1" class="product-row">
            <text
              v-for="product in products"
              :key="product.id"
              class="product-chip"
              :class="{ selected: product.id === productId }"
              @click="selectProduct(product)"
            >{{ product.carrier }} · {{ product.code }}</text>
          </view>
          <view v-if="loading" class="empty-tip"><text>保障方案加载中...</text></view>
          <view v-else-if="!tiers.length" class="empty-tip">
            <text>{{ loadError ? "保障方案加载失败，请稍后重试" : "平台暂未上架保险产品" }}</text>
          </view>
          <template v-else>
            <view class="tier-row">
              <view
                v-for="(tier, index) in tiers"
                :key="index"
                class="tier-item"
                :class="{ selected: selectedAmount === tier.amount }"
                @click="selectTier(tier)"
              >
                <view class="amount"><text>{{ tier.amountText }}</text></view>
                <view class="cost"><text>{{ tier.points }}积分/天</text></view>
                <view class="check"><text>✓</text></view>
              </view>
            </view>
            <view class="coverage-detail">
              <view class="row">
                <text>意外伤害身故残疾</text>
                <text class="val">{{ selectedTier.deathText }}</text>
              </view>
              <view class="row">
                <text>意外医疗</text>
                <text class="val">{{ selectedTier.medicalText }}</text>
              </view>
              <view class="hint">
                <text>注：意外医疗限二级及以上公立医院就诊，限额内按实际费用 100% 赔付。</text>
              </view>
            </view>
          </template>
        </view>

        <view class="job-block">
          <view class="job-title"><text>投保工种</text></view>
          <view class="job-row" @click="selectedJob = jobName">
            <text class="name selected">{{ jobName }}</text>
            <view class="check"><text>✓</text></view>
          </view>
        </view>
      </scroll-view>

      <view class="bottom-bar">
        <button class="confirm-btn" @click="savePlan">确定保障方案</button>
      </view>
    </view>
  </view>
</template>

<script>
import { listInsuranceProducts } from "@/api/backend";

const toWan = (text, value) => text || (value != null && value !== "" ? `${value}万` : "-");

export default {
  data() {
    return {
      enabled: true,
      statusBarHeight: 0,
      jobName: "普工",
      selectedJob: "普工",
      selectedAmount: null,
      products: [],
      productId: null,
      loading: true,
      loadError: false
    };
  },
  computed: {
    currentProduct() {
      return this.products.find(product => product.id === this.productId) || this.products[0] || null;
    },
    tiers() {
      // 档位以后台「保险管理」发布的数据为准，展示文案优先用后台填写的原文
      return (this.currentProduct?.tiers || []).map(tier => ({
        amount: Number(tier.amount) || 0,
        points: Number(tier.points) || 0,
        death: Number(tier.death) || 0,
        medical: Number(tier.medical) || 0,
        amountText: toWan(tier.amountText, tier.amount),
        deathText: toWan(tier.deathText, tier.death),
        medicalText: toWan(tier.medicalText, tier.medical)
      }));
    },
    selectedTier() {
      return this.tiers.find(tier => tier.amount === this.selectedAmount) || this.tiers[0] || null;
    }
  },
  onLoad(options) {
    if (options?.job) this.jobName = decodeURIComponent(options.job) || "普工";
    this.selectedJob = this.jobName;
    const saved = uni.getStorageSync("recruitInsurancePlan");
    if (saved && typeof saved === "object") {
      this.enabled = saved.enabled !== false;
      this.selectedAmount = Number(saved.amount) || null;
      this.productId = saved.productId || null;
      this.selectedJob = saved.job || this.jobName;
    }
    this.loadProducts();
  },
  methods: {
    async loadProducts() {
      this.loading = true;
      this.loadError = false;
      try {
        const list = await listInsuranceProducts();
        this.products = Array.isArray(list) ? list : [];
      } catch (error) {
        console.warn("[insurance] 加载保险产品失败:", error);
        this.products = [];
        this.loadError = true;
      } finally {
        this.loading = false;
      }
      // 本地保存的产品可能已被后台停用，回落到最新启用的产品
      if (!this.products.some(product => product.id === this.productId)) {
        this.productId = this.products[0]?.id ?? null;
      }
      if (this.selectedTier) this.selectedAmount = this.selectedTier.amount;
    },
    goBack() {
      uni.navigateBack({ fail: () => uni.redirectTo({ url: "/pages/boss-publish/recruit-demand" }) });
    },
    goDetail() {
      if (!this.selectedTier) return;
      uni.navigateTo({
        url: `/pages/worker/insurance-detail?amount=${this.selectedTier.amount}&cost=${this.selectedTier.points}&medical=${this.selectedTier.medical}&disability=${this.selectedTier.death}`
      });
    },
    selectProduct(product) {
      this.productId = product.id;
      this.selectedAmount = this.selectedTier?.amount ?? null;
    },
    selectTier(tier) {
      this.selectedAmount = tier.amount;
    },
    savePlan() {
      const tier = this.selectedTier;
      if (this.enabled && !tier) {
        uni.showToast({ title: "暂无可选保障方案", icon: "none" });
        return;
      }
      const product = this.currentProduct;
      const plan = {
        enabled: this.enabled,
        productId: product?.id ?? null,
        productCode: product?.code || "",
        carrier: product?.carrier || "",
        amount: tier?.amount ?? 0,
        points: tier?.points ?? 0,
        death: tier?.death ?? 0,
        medical: tier?.medical ?? 0,
        job: this.selectedJob
      };
      uni.setStorageSync("recruitInsurancePlan", plan);
      uni.$emit("insurancePlanSaved", plan);
      uni.showToast({
        title: plan.enabled ? `已确定：${tier.amountText} · ${plan.points}积分/天` : "已关闭零工意外保障",
        icon: "none"
      });
      setTimeout(() => this.goBack(), 400);
    }
  }
};
</script>

<style scoped>
.page-mask { min-height: 100vh; background: rgba(0,0,0,.5); display: flex; flex-direction: column; justify-content: flex-end; }
.modal { max-height: 90vh; display: flex; flex-direction: column; background: #fff; border-radius: 32rpx 32rpx 0 0; overflow: hidden; }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding: 32rpx 32rpx 24rpx; border-bottom: 1rpx solid #f0f0f0; }
.modal-title { display: flex; align-items: center; gap: 16rpx; font-size: 32rpx; font-weight: 600; color: #333; }
.title-icon { font-size: 32rpx; }
.header-actions { display: flex; align-items: center; gap: 24rpx; }
.toggle { position: relative; width: 88rpx; height: 48rpx; border-radius: 24rpx; background: #1677ff; transition: background .2s; }
.toggle::after { content: ""; position: absolute; top: 4rpx; left: 44rpx; width: 40rpx; height: 40rpx; border-radius: 50%; background: #fff; box-shadow: 0 2rpx 6rpx rgba(0,0,0,.2); transition: left .2s; }
.toggle.off { background: #ddd; }
.toggle.off::after { left: 4rpx; }
.close-btn { width: 56rpx; text-align: center; color: #999; font-size: 36rpx; }
.modal-body { flex: 1; max-height: 70vh; }
.info-block { padding: 28rpx 32rpx 8rpx; }
.info-num { display: flex; align-items: flex-start; gap: 16rpx; margin-bottom: 20rpx; font-size: 26rpx; color: #333; line-height: 1.6; }
.info-num .n { display: flex; align-items: center; justify-content: center; width: 36rpx; height: 36rpx; margin-top: 2rpx; border-radius: 50%; background: #fff3ed; color: #ff6b35; font-size: 22rpx; font-weight: 600; flex-shrink: 0; }
.info-disclaimer { padding: 16rpx 32rpx 24rpx; font-size: 22rpx; color: #999; line-height: 1.5; }
.service-block { padding: 28rpx 32rpx; border-top: 12rpx solid #f5f5f5; }
.service-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.service-title { font-size: 28rpx; font-weight: 600; color: #333; }
.service-meta { display: flex; align-items: center; gap: 12rpx; font-size: 22rpx; color: #999; }
.meta-icon { font-size: 22rpx; }
.divider { color: #ddd; }
.link { color: #1677ff; }
.product-row { display: flex; flex-wrap: wrap; gap: 16rpx; margin-bottom: 24rpx; }
.product-chip { padding: 10rpx 20rpx; border: 2rpx solid #eee; border-radius: 28rpx; font-size: 22rpx; color: #666; }
.product-chip.selected { border-color: #16a34a; background: #f0fff4; color: #16a34a; }
.empty-tip { padding: 40rpx 0; text-align: center; font-size: 24rpx; color: #999; }
.tier-row { display: flex; gap: 16rpx; }
.tier-item { position: relative; flex: 1; padding: 20rpx 8rpx 16rpx; border: 3rpx solid #eee; border-radius: 16rpx; background: #fff; text-align: center; transition: all .15s; }
.tier-item .amount { font-size: 30rpx; font-weight: 700; color: #333; }
.tier-item .cost { margin-top: 6rpx; font-size: 20rpx; color: #999; }
.tier-item.selected { border-color: #16a34a; background: #f0fff4; }
.tier-item.selected .amount { color: #16a34a; }
.tier-item .check { display: none; position: absolute; top: -16rpx; right: -16rpx; width: 40rpx; height: 40rpx; border-radius: 50%; background: #16a34a; color: #fff; font-size: 20rpx; align-items: center; justify-content: center; box-shadow: 0 4rpx 12rpx rgba(22,163,74,.35); }
.tier-item.selected .check { display: flex; }
.coverage-detail { margin-top: 24rpx; padding: 24rpx; border-radius: 16rpx; background: #fafafa; font-size: 24rpx; color: #666; line-height: 1.7; }
.coverage-detail .row { display: flex; justify-content: space-between; }
.coverage-detail .val { color: #333; font-weight: 500; }
.coverage-detail .hint { margin-top: 16rpx; padding-top: 16rpx; border-top: 1rpx dashed #e5e5e5; font-size: 20rpx; color: #999; }
.job-block { padding: 28rpx 32rpx 40rpx; border-top: 12rpx solid #f5f5f5; }
.job-title { margin-bottom: 20rpx; font-size: 28rpx; font-weight: 600; color: #333; }
.job-row { display: flex; align-items: center; justify-content: space-between; padding: 24rpx; border: 1rpx solid #eee; border-radius: 16rpx; }
.job-row .name { font-size: 26rpx; color: #333; }
.job-row .name.selected { color: #1677ff; font-weight: 600; }
.job-row .check { display: flex; align-items: center; justify-content: center; width: 36rpx; height: 36rpx; border-radius: 50%; background: #1677ff; color: #fff; font-size: 22rpx; }
.bottom-bar { padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom, 20rpx)); border-top: 1rpx solid #f0f0f0; background: #fff; }
.confirm-btn { width: 100%; height: 88rpx; border: none; border-radius: 44rpx; background: linear-gradient(135deg, #ffd96f, #ffc107); color: #4a3500; font-size: 30rpx; font-weight: 600; }
.confirm-btn::after { border: none; }
</style>
