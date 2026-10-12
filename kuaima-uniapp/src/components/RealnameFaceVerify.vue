<template>
  <view class="container">
    <view class="page-bg">
      <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight + 14}px` }">
        <view class="nav-back" @click="goBack">
          <image class="nav-back-icon" src="/static/icons/realname/arrow-left-dark.svg" mode="aspectFit" />
        </view>
        <text class="nav-title">实名认证</text>
        <view class="nav-placeholder"></view>
      </view>

      <scroll-view scroll-y class="content">
        <!-- Step 1 上传 -->
        <view v-if="step === 'upload'">
          <view class="top-shield">
            <image class="top-shield-icon" src="/static/icons/realname/user-shield-dark.svg" mode="aspectFit" />
          </view>
          <text class="page-title">{{ entryTitle }}</text>
          <text class="page-desc">根据国家法律法规，需要您实名认证</text>

          <view class="upload-group">
            <text class="upload-label">上传身份证人像面</text>
            <view class="upload-box" :class="{ 'has-img': idCardImageUrl }" @click="chooseImage('idCard')">
              <image v-if="idCardImageUrl" class="upload-preview" :src="idCardImageUrl" mode="aspectFill" />
              <template v-else>
                <image class="upload-icon" src="/static/icons/realname/id-card-gray.svg" mode="aspectFit" />
                <text class="up-text">点击上传身份证人像面</text>
              </template>
              <view v-if="idCardImageUrl" class="re-upload"><text>重新上传</text></view>
              <view v-if="uploading === 'idCard'" class="uploading"><text>上传中...</text></view>
            </view>
          </view>

          <view class="upload-group">
            <text class="upload-label">上传人脸图片</text>
            <view class="upload-box" :class="{ 'has-img': faceImageUrl }" @click="chooseImage('face')">
              <image v-if="faceImageUrl" class="upload-preview" :src="faceImageUrl" mode="aspectFill" />
              <template v-else>
                <image class="upload-icon" src="/static/icons/realname/user-circle-gray.svg" mode="aspectFit" />
                <text class="up-text">点击上传人脸图片</text>
              </template>
              <view v-if="faceImageUrl" class="re-upload"><text>重新上传</text></view>
              <view v-if="uploading === 'face'" class="uploading"><text>上传中...</text></view>
            </view>
          </view>

          <button class="face-btn" :disabled="!canVerify || verifying" @click="startVerify">
            <image class="face-btn-icon" src="/static/icons/realname/shield-halved-dark.svg" mode="aspectFit" />
            <text>{{ verifying ? '验证中...' : '开始验证' }}</text>
          </button>

          <view class="agreement">
            <view class="checkbox-custom" :class="{ checked: agreed }" @click="agreed = !agreed">
              <image v-if="agreed" class="checkbox-icon" src="/static/icons/realname/check-small-white.svg" mode="aspectFit" />
            </view>
            <text class="agreement-text">
              您已阅读并同意<text class="agree-link" @click.stop="openAgreement">《快马日结实名认证&人脸验证服务协议》</text><text class="agree-link" @click.stop="openAgreement('publish-rule')">《快马日结信息发布规则》</text>
            </text>
          </view>
        </view>

        <!-- Step 2 比对中 -->
        <view v-else-if="step === 'matching'" class="step-center">
          <view class="top-shield">
            <image class="top-shield-icon" src="/static/icons/realname/clone-dark.svg" mode="aspectFit" />
          </view>
          <text class="page-title">人脸匹配验证</text>
          <text class="page-desc">正在比对人脸图片与身份证照片\n请稍候...</text>
          <view class="match-area">
            <view class="match-photo">
              <image :src="idCardImageUrl" mode="aspectFill" />
              <view class="ph-label"><text>身份证照</text></view>
            </view>
            <view class="match-vs">
              <image class="match-vs-icon" src="/static/icons/realname/exchange-alt-yellow.svg" mode="aspectFit" />
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
        <view v-else-if="step === 'result'" class="step-center result-center">
          <view class="result-icon" :class="passed ? 'success' : 'fail'">
            <image class="result-icon-img" :src="passed ? '/static/icons/realname/check-white.svg' : '/static/icons/realname/xmark-white.svg'" mode="aspectFit" />
          </view>
          <text class="page-title">{{ passed ? '实名认证成功' : '认证失败' }}</text>
          <text class="page-desc">{{ resultMessage }}</text>
          <button class="face-btn" @click="finish">{{ passed ? '完 成' : '重新上传' }}</button>
        </view>

        <!-- 已认证 -->
        <view v-else-if="step === 'verified'" class="step-center verified-center">
          <text class="page-title">已实名认证</text>
          <text class="page-desc">{{ verifiedDesc }}</text>
          <view class="verified-photos">
            <view class="v-photo">
              <image :src="idCardImageUrl" mode="aspectFill" />
              <view class="v-label"><text>身份证照</text></view>
            </view>
            <view class="v-photo">
              <image :src="faceImageUrl" mode="aspectFill" />
              <view class="v-label"><text>人脸照</text></view>
            </view>
          </view>
          <button v-if="verifiedMode === 'reverify'" class="face-btn" @click="reVerify">重新认证</button>
          <button v-else class="face-btn" @click="goBack">返 回</button>
        </view>

        <view class="scroll-bottom-space"></view>
      </scroll-view>
    </view>
  </view>
</template>

<style scoped>
.container { min-height: 100vh; background: #fff; }
.page-bg { min-height: 100vh; background: #fff; }
.nav-bar { display: flex; align-items: center; justify-content: space-between; padding: 0 32rpx 20rpx; background: #fff; }
.nav-back { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; }
.nav-back-icon { width: 36rpx; height: 36rpx; }
.nav-title { font-size: 34rpx; font-weight: 600; color: #333; }
.nav-placeholder { width: 64rpx; }
.content { height: calc(100vh - 120rpx); padding: 0 48rpx; box-sizing: border-box; }
.step-center { display: flex; flex-direction: column; align-items: center; }
.result-center { padding-top: 120rpx; }
.verified-center { padding-top: 144rpx; }
.top-shield { width: 144rpx; height: 144rpx; margin: 16rpx auto 40rpx; background: linear-gradient(135deg, #FFD966, #FFB84D); clip-path: polygon(50% 0%, 100% 20%, 100% 60%, 50% 100%, 0% 60%, 0% 20%); display: flex; align-items: center; justify-content: center; box-shadow: 0 16rpx 48rpx rgba(255,180,50,.35); }
.top-shield-icon { width: 64rpx; height: 64rpx; }
.page-title { display: block; text-align: center; font-size: 44rpx; font-weight: 700; color: #333; margin-bottom: 20rpx; }
.page-desc { display: block; margin-bottom: 60rpx; text-align: center; font-size: 26rpx; color: #999; line-height: 1.6; white-space: pre-line; }
.upload-group { margin-bottom: 40rpx; }
.upload-label { display: block; font-size: 28rpx; color: #666; margin-bottom: 20rpx; }
.upload-box { position: relative; width: 100%; height: 260rpx; border: 3rpx dashed #ddd; border-radius: 20rpx; background: #fafafa; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12rpx; overflow: hidden; }
.upload-box.has-img { border-style: solid; border-color: #eaeaea; background: #fff; }
.upload-box .upload-preview { width: 100%; height: 100%; }
.upload-icon { width: 52rpx; height: 52rpx; flex-shrink: 0; }
.up-text { font-size: 26rpx; color: #999; }
.re-upload { position: absolute; right: 12rpx; bottom: 12rpx; padding: 6rpx 16rpx; border-radius: 20rpx; background: rgba(0,0,0,.5); color: #fff; font-size: 22rpx; }
.uploading { position: absolute; left: 0; right: 0; top: 0; bottom: 0; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.7); color: #ffb84d; font-size: 26rpx; }
.face-btn { width: 100%; margin-top: 32rpx; height: 112rpx; display: flex; align-items: center; justify-content: center; gap: 16rpx; border: none; border-radius: 14rpx; background: #ffc95a; color: #040600; font-size: 34rpx; font-weight: 600; box-shadow: 0 16rpx 40rpx rgba(255,201,90,.35); }
.face-btn[disabled] { opacity: .6; box-shadow: none; background: #ffc95a; color: #040600; }
.face-btn::after { border: none; }
.face-btn-icon { width: 34rpx; height: 34rpx; }
.agreement { display: flex; align-items: flex-start; gap: 16rpx; margin-top: 36rpx; padding: 0 8rpx; }
.checkbox-custom { width: 32rpx; height: 32rpx; border: 4rpx solid #ddd; border-radius: 8rpx; background: #fff; flex-shrink: 0; margin-top: 4rpx; display: flex; align-items: center; justify-content: center; }
.checkbox-custom.checked { background: #ffd700; border-color: #ffd700; }
.checkbox-icon { width: 20rpx; height: 20rpx; }
.agreement-text { font-size: 24rpx; color: #999; line-height: 1.7; }
.agree-link { color: #ffa500; }
.match-area { margin: 48rpx 0; display: flex; align-items: center; justify-content: center; gap: 32rpx; }
.match-photo { position: relative; width: 240rpx; height: 280rpx; border-radius: 20rpx; overflow: hidden; background: #f5f5f5; border: 2rpx solid #eaeaea; }
.match-photo image { width: 100%; height: 100%; }
.ph-label { position: absolute; left: 0; right: 0; bottom: 0; padding: 6rpx 0; background: rgba(0,0,0,.5); color: #fff; font-size: 22rpx; text-align: center; }
.match-vs { display: flex; flex-direction: column; align-items: center; gap: 8rpx; color: #999; font-size: 28rpx; font-weight: 600; }
.match-vs-icon { width: 40rpx; height: 40rpx; }
.match-hint { margin-top: 40rpx; font-size: 28rpx; color: #999; text-align: center; }
.result-icon { width: 160rpx; height: 160rpx; margin: 0 auto 32rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.result-icon.success { background: linear-gradient(135deg, #52c41a, #73d13d); box-shadow: 0 16rpx 48rpx rgba(82,196,26,.3); }
.result-icon.fail { background: linear-gradient(135deg, #ef4444, #f87171); box-shadow: 0 16rpx 48rpx rgba(239,68,68,.3); }
.result-icon-img { width: 72rpx; height: 72rpx; }
.result-center .face-btn { margin-top: 64rpx; }
.verified-photos { display: flex; gap: 24rpx; width: 100%; margin-top: 56rpx; }
.v-photo { flex: 1; background: #fafafa; border: 2rpx solid #eee; border-radius: 20rpx; overflow: hidden; }
.v-photo image { width: 100%; height: 280rpx; display: block; }
.v-label { padding: 16rpx 0; background: #fafafa; font-size: 24rpx; color: #666; text-align: center; }
.verified-center .face-btn { margin-top: 72rpx; }
.scroll-bottom-space { height: 60rpx; }
</style>
<script>
import { getRealnameStatus, uploadRealnameImage, verifyRealname } from "@/api/backend";

const MATCH_HINTS = ["正在提取身份证人脸特征...", "正在提取上传人脸特征...", "特征比对中..."];

export default {
  props: {
    role: { type: String, default: "worker" },
    entryTitle: { type: String, default: "完成实名后开启接单" },
    successDesc: { type: String, default: "现在可以正常浏览岗位并接单" },
    verifiedDesc: { type: String, default: "您的账户已完成实名认证" },
    verifiedMode: { type: String, default: "reverify" },
    homePath: { type: String, default: "/pages/worker/home" },
  },
  data() {
    return {
      statusBarHeight: 0,
      step: "upload",
      agreed: false,
      idCardImageUrl: "",
      faceImageUrl: "",
      idCardImageKey: "",
      faceImageKey: "",
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
      return this.agreed && !!this.idCardImageKey && !!this.faceImageKey;
    },
  },
  mounted() {
    try {
      const info = typeof uni.getWindowInfo === "function" ? uni.getWindowInfo() : uni.getSystemInfoSync();
      this.statusBarHeight = Number(info.statusBarHeight || 0);
    } catch (_) {}
    this.loadStatus();
  },
  beforeUnmount() {
    if (this.hintTimer) clearInterval(this.hintTimer);
  },
  methods: {
    async loadStatus() {
      try {
        const result = await getRealnameStatus(this.role);
        const data = result?.data || result || {};
        this.idCardImageUrl = data.idCardImageUrl || "";
        this.faceImageUrl = data.faceImageUrl || "";
        this.idCardImageKey = data.idCardImageKey || data.idCardImageUrl || "";
        this.faceImageKey = data.faceImageKey || data.faceImageUrl || "";
        if (data.realnameStatus === "APPROVED" || data.realnameStatus === "已通过") {
          this.step = "verified";
        }
      } catch (error) {
        console.warn("[WorkerRealname] 加载认证状态失败:", error);
      }
    },
    goBack() {
      uni.navigateBack({ fail: () => uni.switchTab({ url: this.homePath }) });
    },
    openAgreement(type) {
      const suffix = type === "publish-rule" ? "?type=publish-rule" : "";
      uni.navigateTo({ url: `/pages/worker/user-agreement${suffix}` });
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
            const uploaded = await uploadRealnameImage(filePath, this.role);
            const url = uploaded?.url || "";
            const key = uploaded?.objectKey || "";
            if (!url || !key) throw new Error("上传结果缺少文件地址");
            if (type === "idCard") {
              this.idCardImageUrl = url;
              this.idCardImageKey = key;
            } else {
              this.faceImageUrl = url;
              this.faceImageKey = key;
            }
          } catch (error) {
            console.warn("[WorkerRealname] 图片上传失败:", error);
            uni.showToast({ title: error?.message || "图片上传失败，请重试", icon: "none" });
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
        const result = await verifyRealname(this.role, {
          idCardImageUrl: this.idCardImageKey,
          faceImageUrl: this.faceImageKey,
        });
        const data = result?.data || result || {};
        this.passed = !!data.passed;
        this.resultMessage = data.message
          || (this.passed ? `人脸与身份证匹配成功，您的账户已实名\n${this.successDesc}` : "人脸与身份证不匹配");
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
