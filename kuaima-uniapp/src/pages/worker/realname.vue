<template>
  <view class="container">
    <view class="page-bg">
      <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight + 14}px` }">
        <view class="nav-back" @click="goBack"><text>←</text></view>
        <text class="nav-title">实名认证</text>
        <view class="nav-placeholder"></view>
      </view>

      <scroll-view scroll-y class="content">
        <!-- Step 1 上传 -->
        <view v-if="step === 'upload'">
          <view class="top-shield"><text>🛡</text></view>
          <text class="page-title">完成实名后开启接单</text>
          <text class="page-desc">根据国家法律法规，需要您实名认证</text>

          <view class="upload-group">
            <text class="upload-label">上传身份证人像面</text>
            <view class="upload-box" :class="{ 'has-img': idCardImageUrl }" @click="chooseImage('idCard')">
              <image v-if="idCardImageUrl" :src="idCardImageUrl" mode="aspectFill" />
              <template v-else>
                <text class="up-icon">🪪</text>
                <text class="up-text">点击上传身份证人像面</text>
              </template>
              <view v-if="idCardImageUrl" class="re-upload"><text>重新上传</text></view>
              <view v-if="uploading === 'idCard'" class="uploading"><text>上传中...</text></view>
            </view>
          </view>

          <view class="upload-group">
            <text class="upload-label">上传人脸图片</text>
            <view class="upload-box" :class="{ 'has-img': faceImageUrl }" @click="chooseImage('face')">
              <image v-if="faceImageUrl" :src="faceImageUrl" mode="aspectFill" />
              <template v-else>
                <text class="up-icon">👤</text>
                <text class="up-text">点击上传人脸图片</text>
              </template>
              <view v-if="faceImageUrl" class="re-upload"><text>重新上传</text></view>
              <view v-if="uploading === 'face'" class="uploading"><text>上传中...</text></view>
            </view>
          </view>

          <button class="face-btn" :disabled="!canVerify || verifying" @click="startVerify">
            {{ verifying ? '验证中...' : '🛡 开始验证' }}
          </button>

          <view class="agreement">
            <view class="checkbox-custom" :class="{ checked: agreed }" @click="agreed = !agreed"></view>
            <text class="agreement-text">
              您已阅读并同意<text class="agree-link" @click.stop="openAgreement">《快马日结实名认证&人脸验证服务协议》</text><text class="agree-link" @click.stop="openAgreement">《快马日结信息发布规则》</text>
            </text>
          </view>
        </view>

        <!-- Step 2 比对中 -->
        <view v-else-if="step === 'matching'" class="step-center">
          <view class="top-shield"><text>🪞</text></view>
          <text class="page-title">人脸匹配验证</text>
          <text class="page-desc">正在比对人脸图片与身份证照片\n请稍候...</text>
          <view class="match-area">
            <view class="match-photo">
              <image :src="idCardImageUrl" mode="aspectFill" />
              <view class="ph-label"><text>身份证照</text></view>
            </view>
            <view class="match-vs">
              <text>⇄</text>
              <text>VS</text>
            </view>
            <view class="match-photo">
              <image :src="faceImageUrl" mode="aspectFill" />
              <view class="ph-label"><text>人脸照</text></view>
            </view>
          </view>
          <text class="match-hint">{{ matchHint }}</text>
        </view>

        <!-- Step 3 结果 -->
        <view v-else-if="step === 'result'" class="step-center">
          <view class="result-icon" :class="passed ? 'success' : 'fail'">
            <text>{{ passed ? '✓' : '✕' }}</text>
          </view>
          <text class="page-title">{{ passed ? '实名认证成功' : '认证失败' }}</text>
          <text class="page-desc">{{ resultMessage }}</text>
          <button class="face-btn" @click="finish">{{ passed ? '完 成' : '重新上传' }}</button>
        </view>

        <!-- 已认证 -->
        <view v-else-if="step === 'verified'" class="step-center verified-center">
          <view class="result-icon success"><text>✓</text></view>
          <text class="page-title">已实名认证</text>
          <text class="page-desc">您的账户已完成实名认证</text>
          <view class="match-area">
            <view class="match-photo">
              <image :src="idCardImageUrl" mode="aspectFill" />
              <view class="ph-label"><text>身份证照</text></view>
            </view>
            <view class="match-photo">
              <image :src="faceImageUrl" mode="aspectFill" />
              <view class="ph-label"><text>人脸照</text></view>
            </view>
          </view>
          <button class="face-btn ghost" @click="reVerify">重新认证</button>
        </view>

        <view class="scroll-bottom-space"></view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import { getWorkerRealnameStatus, uploadRealnameImage, verifyWorkerRealname } from "@/api/backend";

const MATCH_HINTS = ["正在提取身份证人脸特征...", "正在提取上传人脸特征...", "特征比对中..."];

export default {
  data() {
    return {
      statusBarHeight: 0,
      step: "upload",
      agreed: false,
      idCardImageUrl: "",
      faceImageUrl: "",
      uploading: "",
      verifying: false,
      passed: false,
      resultMessage: "",
      matchHint: MATCH_HINTS[0],
      hintTimer: null,
    };
  },
  computed: {
    canVerify() {
      return this.agreed && !!this.idCardImageUrl && !!this.faceImageUrl;
    },
  },
  onLoad() {
    try {
      const info = typeof uni.getWindowInfo === "function" ? uni.getWindowInfo() : uni.getSystemInfoSync();
      this.statusBarHeight = Number(info.statusBarHeight || 0);
    } catch (_) {}
    this.loadStatus();
  },
  onUnload() {
    if (this.hintTimer) clearInterval(this.hintTimer);
  },
  methods: {
    async loadStatus() {
      try {
        const result = await getWorkerRealnameStatus();
        const data = result?.data || result || {};
        this.idCardImageUrl = data.idCardImageUrl || "";
        this.faceImageUrl = data.faceImageUrl || "";
        if (data.realnameStatus === "APPROVED" || data.realnameStatus === "已通过") {
          this.step = "verified";
        }
      } catch (error) {
        console.warn("[WorkerRealname] 加载认证状态失败:", error);
      }
    },
    goBack() {
      uni.navigateBack({ fail: () => uni.switchTab({ url: "/pages/worker/home" }) });
    },
    openAgreement() {
      uni.navigateTo({ url: "/pages/worker/user-agreement" });
    },
    chooseImage(type) {
      if (this.uploading || this.verifying) return;
      uni.chooseImage({
        count: 1,
        sizeType: ["compressed"],
        sourceType: ["album", "camera"],
        success: async (res) => {
          const filePath = res.tempFilePaths?.[0];
          if (!filePath) return;
          this.uploading = type;
          try {
            const uploaded = await uploadRealnameImage(filePath);
            const url = uploaded?.url || uploaded?.objectKey || "";
            if (!url) throw new Error("上传结果缺少文件地址");
            if (type === "idCard") this.idCardImageUrl = url;
            else this.faceImageUrl = url;
          } catch (error) {
            console.warn("[WorkerRealname] 图片上传失败:", error);
            uni.showToast({ title: "图片上传失败，请重试", icon: "none" });
          } finally {
            this.uploading = "";
          }
        },
      });
    },
    async startVerify() {
      if (!this.canVerify || this.verifying) return;
      this.verifying = true;
      this.step = "matching";
      this.matchHint = MATCH_HINTS[0];
      let hintIndex = 0;
      this.hintTimer = setInterval(() => {
        hintIndex = Math.min(hintIndex + 1, MATCH_HINTS.length - 1);
        this.matchHint = MATCH_HINTS[hintIndex];
      }, 1200);
      try {
        const result = await verifyWorkerRealname({
          idCardImageUrl: this.idCardImageUrl,
          faceImageUrl: this.faceImageUrl,
        });
        const data = result?.data || result || {};
        this.passed = !!data.passed;
        this.resultMessage = data.message
          || (this.passed ? "人脸与身份证匹配成功，您的账户已实名\n现在可以正常浏览岗位并接单" : "人脸与身份证不匹配");
        this.step = "result";
      } catch (error) {
        this.passed = false;
        this.resultMessage = error?.message || "人脸比对失败，请稍后重试";
        this.step = "result";
      } finally {
        if (this.hintTimer) clearInterval(this.hintTimer);
        this.verifying = false;
      }
    },
    finish() {
      if (this.passed) {
        this.step = "verified";
        return;
      }
      this.step = "upload";
    },
    reVerify() {
      this.agreed = false;
      this.step = "upload";
    },
  },
};
</script>

<style scoped>
.container { min-height: 100vh; background: #f5f5f5; }
.page-bg { min-height: 100vh; background: #f5f5f5; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; padding: 0 32rpx 20rpx; background: #f5f5f5; }
.nav-back { width: 56rpx; height: 56rpx; border-radius: 50%; background: #fff; display: flex; align-items: center; justify-content: center; font-size: 32rpx; color: #333; }
.nav-title { font-size: 32rpx; font-weight: 600; color: #333; }
.nav-placeholder { width: 56rpx; }
.content { height: calc(100vh - 120rpx); padding: 0 40rpx; box-sizing: border-box; }
.step-center { display: flex; flex-direction: column; align-items: center; padding-top: 80rpx; }
.verified-center { padding-top: 72rpx; }
.top-shield { width: 120rpx; height: 120rpx; margin: 40rpx auto 24rpx; border-radius: 50%; background: linear-gradient(135deg, #ff8c42, #ff6b35); display: flex; align-items: center; justify-content: center; font-size: 56rpx; color: #fff; box-shadow: 0 12rpx 32rpx rgba(255,107,53,.25); }
.page-title { display: block; text-align: center; font-size: 36rpx; font-weight: 600; color: #222; }
.page-desc { display: block; margin-top: 12rpx; text-align: center; font-size: 26rpx; color: #999; line-height: 1.6; white-space: pre-line; }
.upload-group { margin-top: 40rpx; }
.upload-label { display: block; font-size: 28rpx; color: #666; margin-bottom: 20rpx; }
.upload-box { position: relative; width: 100%; height: 260rpx; border: 3rpx dashed #ddd; border-radius: 20rpx; background: #fafafa; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12rpx; overflow: hidden; }
.upload-box.has-img { border-style: solid; border-color: #eaeaea; background: #fff; }
.upload-box image { width: 100%; height: 100%; }
.up-icon { font-size: 52rpx; }
.up-text { font-size: 26rpx; color: #999; }
.re-upload { position: absolute; right: 12rpx; bottom: 12rpx; padding: 6rpx 16rpx; border-radius: 20rpx; background: rgba(0,0,0,.5); color: #fff; font-size: 22rpx; }
.uploading { position: absolute; left: 0; right: 0; top: 0; bottom: 0; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.7); color: #ff6b35; font-size: 26rpx; }
.face-btn { width: 100%; margin-top: 48rpx; height: 96rpx; line-height: 96rpx; border: none; border-radius: 14rpx; background: linear-gradient(135deg, #ffd96f, #ffc95a); color: #040600; font-size: 32rpx; font-weight: 600; box-shadow: 0 12rpx 32rpx rgba(255,201,90,.35); }
.face-btn.ghost { background: #fff; color: #ff6b35; border: 2rpx solid #ffd0b8; box-shadow: none; margin-top: 48rpx; }
.face-btn[disabled] { opacity: .55; color: #040600; background: linear-gradient(135deg, #ffd96f, #ffc95a); }
.face-btn::after { border: none; }
.agreement { display: flex; align-items: flex-start; gap: 14rpx; margin-top: 32rpx; padding: 0 8rpx; }
.checkbox-custom { width: 36rpx; height: 36rpx; border: 2rpx solid #ddd; border-radius: 8rpx; background: #fff; flex-shrink: 0; margin-top: 2rpx; }
.checkbox-custom.checked { background: #ff6b35; border-color: #ff6b35; position: relative; }
.checkbox-custom.checked::after { content: "✓"; position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 22rpx; }
.agreement-text { font-size: 24rpx; color: #999; line-height: 1.6; }
.agree-link { color: #ff6b35; }
.match-area { display: flex; align-items: center; gap: 24rpx; margin-top: 56rpx; }
.match-photo { position: relative; width: 220rpx; height: 280rpx; border-radius: 20rpx; overflow: hidden; background: #fff; box-shadow: 0 8rpx 24rpx rgba(0,0,0,.06); }
.match-photo image { width: 100%; height: 100%; }
.ph-label { position: absolute; left: 0; right: 0; bottom: 0; padding: 10rpx 0; background: rgba(0,0,0,.45); color: #fff; font-size: 22rpx; text-align: center; }
.match-vs { display: flex; flex-direction: column; align-items: center; gap: 4rpx; color: #ff6b35; font-size: 24rpx; font-weight: 600; }
.match-hint { margin-top: 48rpx; font-size: 26rpx; color: #999; }
.result-icon { width: 140rpx; height: 140rpx; margin: 24rpx auto 24rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 64rpx; }
.result-icon.success { background: linear-gradient(135deg, #52c41a, #73d13d); box-shadow: 0 12rpx 36rpx rgba(82,196,26,.3); }
.result-icon.fail { background: linear-gradient(135deg, #ef4444, #f87171); box-shadow: 0 12rpx 36rpx rgba(239,68,68,.3); }
.step-center .face-btn { margin-top: 56rpx; }
.scroll-bottom-space { height: 60rpx; }
</style>
