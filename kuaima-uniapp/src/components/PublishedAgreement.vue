<template>
  <view class="published-agreement">
    <view class="agreement-row">
      <view class="checkbox-custom" :class="{ checked: modelValue }" @click="$emit('update:modelValue', !modelValue)">
        <image v-if="modelValue" class="checkbox-icon" src="/static/icons/realname/check-small-white.svg" mode="aspectFit" />
      </view>
      <text class="agreement-text">您已阅读并同意</text>
      <text v-for="item in items" :key="item.type" class="agree-link" @click.stop="openAgreement(item)">《{{ item.label }}》</text>
    </view>
    <view v-if="visible" class="agreement-mask" @click="close">
      <view class="agreement-modal" @click.stop>
        <view class="agreement-header">
          <text class="agreement-title">{{ active?.name || activeItem?.label || '协议内容' }}</text>
          <text class="agreement-close" @click="close">×</text>
        </view>
        <scroll-view scroll-y class="agreement-body">
          <text v-if="loading" class="agreement-content">协议加载中…</text>
          <text v-else-if="errorMessage" class="agreement-content">{{ errorMessage }}</text>
          <template v-else>
            <text class="agreement-content">{{ active?.content || (active?.fileName ? `当前协议以附件形式发布：${active.fileName}` : '暂未配置协议内容') }}</text>
            <button v-if="active?.fileUrl" class="agreement-file-button" @click="openFile">查看协议内容</button>
          </template>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import { getPublicAgreement } from "@/api/backend";

export default {
  name: "PublishedAgreement",
  props: {
    modelValue: { type: Boolean, default: false },
    items: { type: Array, default: () => [
      { type: "实名认证&人脸验证服务协议", label: "快马日结实名认证&人脸验证服务协议" },
      { type: "信息发布协议", label: "快马日结信息发布规则" },
    ] },
  },
  data: () => ({ visible: false, loading: false, active: null, activeItem: null, errorMessage: "" }),
  methods: {
    async openAgreement(item) {
      this.activeItem = item; this.active = null; this.errorMessage = ""; this.loading = true; this.visible = true;
      try {
        const result = await getPublicAgreement(item.type);
        this.active = result?.data || result || null;
        if (!this.active) this.errorMessage = "暂未配置该协议内容";
      } catch (error) {
        this.errorMessage = error?.message || "协议加载失败，请稍后再试";
      } finally { this.loading = false; }
    },
    close() { if (!this.loading) this.visible = false; },
    openFile() {
      const fileUrl = this.active?.fileUrl; if (!fileUrl) return;
      const fileName = this.active?.fileName || "";
      const isPdf = /\.pdf(?:$|[?#])/i.test(fileName) || /\.pdf(?:$|[?#])/i.test(fileUrl);
      if (isPdf) {
        uni.setStorageSync("agreementPreviewPayload", {
          url: fileUrl,
          title: this.active?.name || this.activeItem?.label || "协议内容",
        });
        this.visible = false;
        uni.navigateTo({
          url: `/pages/agreement-preview?title=${encodeURIComponent(this.active?.name || this.activeItem?.label || "协议内容")}`,
          fail: () => uni.showToast({ title: "协议页面打开失败", icon: "none" }),
        });
        return;
      }
      uni.downloadFile({ url: fileUrl, success: (response) => {
        if (response.statusCode !== 200) return uni.showToast({ title: "协议文件下载失败", icon: "none" });
        uni.openDocument({ filePath: response.tempFilePath, showMenu: true, fail: () => uni.showToast({ title: "协议文件打开失败", icon: "none" }) });
      }, fail: () => uni.showToast({ title: "协议文件下载失败", icon: "none" }) });
    },
  },
};
</script>

<style scoped>
.agreement-row { display: flex; align-items: flex-start; flex-wrap: wrap; gap: 0 12rpx; margin-top: 36rpx; padding: 0 8rpx; }
.checkbox-custom { width: 32rpx; height: 32rpx; border: 4rpx solid #ddd; border-radius: 8rpx; background: #fff; flex-shrink: 0; margin-top: 4rpx; display: flex; align-items: center; justify-content: center; }
.checkbox-custom.checked { background: #ffd700; border-color: #ffd700; }
.checkbox-icon { width: 20rpx; height: 20rpx; }
.agreement-text, .agree-link { font-size: 24rpx; line-height: 1.7; }
.agreement-text { color: #999; }.agree-link { color: #ffa500; margin-right: 8rpx; }
.agreement-mask { position: fixed; z-index: 20; inset: 0; display: flex; align-items: center; justify-content: center; padding: 48rpx; background: rgba(0,0,0,.45); }
.agreement-modal { width: 100%; max-height: 78vh; overflow: hidden; border-radius: 20rpx; background: #fff; }
.agreement-header { display: flex; align-items: center; justify-content: space-between; padding: 28rpx 32rpx; border-bottom: 1rpx solid #eee; }
.agreement-title { flex: 1; font-size: 30rpx; font-weight: 600; color: #333; }.agreement-close { padding-left: 24rpx; font-size: 44rpx; line-height: 1; color: #999; }
.agreement-body { max-height: 62vh; padding: 28rpx 32rpx; box-sizing: border-box; }.agreement-content { display: block; white-space: pre-wrap; font-size: 26rpx; line-height: 1.8; color: #666; }
.agreement-file-button { margin: 32rpx auto 8rpx; color: #fff; background: #ffc95a; border: 0; }.agreement-file-button::after { border: 0; }
</style>
