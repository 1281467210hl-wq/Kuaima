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
            <view class="service-meta">
              <text class="meta-icon">🛡</text>
              <text>众安保险承保</text>
              <text class="divider">|</text>
              <text class="link" @click="goDetail">更多详情 ›</text>
            </view>
          </view>
          <view class="tier-row">
            <view
              v-for="tier in tiers"
              :key="tier.amount"
              class="tier-item"
              :class="{ selected: selectedAmount === tier.amount }"
              @click="selectTier(tier)"
            >
              <view class="amount"><text>{{ tier.amount }}万</text></view>
              <view class="cost"><text>{{ tier.points }}积分/天</text></view>
              <view class="check"><text>✓</text></view>
            </view>
          </view>
          <view class="coverage-detail">
            <view class="row">
              <text>意外伤害身故残疾</text>
              <text class="val">{{ selectedTier.death }}万</text>
            </view>
            <view class="row">
              <text>意外医疗</text>
              <text class="val">{{ selectedTier.medical }}万</text>
            </view>
            <view class="hint">
              <text>注：意外医疗限二级及以上公立医院就诊，限额内按实际费用 100% 赔付。</text>
            </view>
          </view>
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
export default {
  data() {
    return {
      enabled: true,
      statusBarHeight: 0,
      jobName: "普工",
      selectedJob: "普工",
      selectedAmount: 33,
      tiers: [
        { amount: 11, points: 1, death: 10, medical: 1 },
        { amount: 33, points: 2, death: 30, medical: 3 },
        { amount: 55, points: 3, death: 50, medical: 5 },
        { amount: 88, points: 4, death: 80, medical: 8 }
      ]
    };
  },
  computed: {
    selectedTier() {
      return this.tiers.find(tier => tier.amount === this.selectedAmount) || this.tiers[1];
    }
  },
  onLoad(options) {
    if (options?.job) this.jobName = decodeURIComponent(options.job) || "普工";
    this.selectedJob = this.jobName;
    const saved = uni.getStorageSync("recruitInsurancePlan");
    if (saved && typeof saved === "object") {
      this.enabled = saved.enabled !== false;
      this.selectedAmount = Number(saved.amount) || 33;
      this.selectedJob = saved.job || this.jobName;
    }
  },
  methods: {
    goBack() {
      uni.navigateBack({ fail: () => uni.redirectTo({ url: "/pages/boss-publish/recruit-demand" }) });
    },
    goDetail() {
      uni.navigateTo({
        url: `/pages/worker/insurance-detail?amount=${this.selectedTier.amount}&cost=${this.selectedTier.points}&medical=${this.selectedTier.medical}&disability=${this.selectedTier.death}`
      });
    },
    selectTier(tier) {
      this.selectedAmount = tier.amount;
    },
    savePlan() {
      const plan = {
        enabled: this.enabled,
        amount: this.selectedTier.amount,
        points: this.selectedTier.points,
        death: this.selectedTier.death,
        medical: this.selectedTier.medical,
        job: this.selectedJob
      };
      uni.setStorageSync("recruitInsurancePlan", plan);
      uni.$emit("insurancePlanSaved", plan);
      uni.showToast({
        title: `已确定：${plan.amount}万 · ${plan.points}积分/天`,
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
