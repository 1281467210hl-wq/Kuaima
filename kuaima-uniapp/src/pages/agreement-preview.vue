<template>
  <view class="preview-page">
    <view v-if="loading" class="empty-state"><text>协议加载中…</text></view>
    <view v-else-if="errorMessage" class="empty-state">
      <text>{{ errorMessage }}</text><button class="retry-button" @click="preview">重新打开</button>
    </view>
    <view v-else class="empty-state"><text>正在打开协议文件…</text></view>
  </view>
</template>

<script setup>
import { onLoad, onUnload } from "@dcloudio/uni-app";
import { ref } from "vue";

const fileUrl = ref("");
const loading = ref(false);
const errorMessage = ref("");

onLoad((options) => {
  const payload = uni.getStorageSync("agreementPreviewPayload") || {};
  const title = payload.title || decodeURIComponent(options?.title || "协议内容");
  uni.setNavigationBarTitle({ title });
  fileUrl.value = payload.url || "";
  preview();
});

function preview() {
  if (!/^https:\/\//i.test(fileUrl.value)) {
    errorMessage.value = "协议地址无效，请返回后重试";
    return;
  }
  loading.value = true;
  errorMessage.value = "";
  uni.downloadFile({
    url: fileUrl.value,
    timeout: 20000,
    success: (response) => {
      if (response.statusCode !== 200 || !response.tempFilePath) {
        errorMessage.value = `协议下载失败（${response.statusCode || "网络异常"}）`;
        loading.value = false;
        return;
      }
      uni.openDocument({
        filePath: response.tempFilePath,
        fileType: "pdf",
        showMenu: true,
        success: () => { loading.value = false; },
        fail: () => {
          loading.value = false;
          errorMessage.value = "当前设备暂不支持预览 PDF，请下载后查看";
        },
      });
    },
    fail: (error) => {
      loading.value = false;
      errorMessage.value = error?.errMsg || "协议下载失败，请检查网络后重试";
    },
  });
}

onUnload(() => {
  uni.removeStorageSync("agreementPreviewPayload");
});
</script>

<style scoped>
.preview-page { min-height: 100vh; background: #f5f5f5; }
.empty-state { min-height: 70vh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 24rpx; padding: 48rpx; color: #999; font-size: 28rpx; text-align: center; }
.retry-button { margin: 0; color: #fff; background: #ffc95a; border: 0; }.retry-button::after { border: 0; }
</style>
