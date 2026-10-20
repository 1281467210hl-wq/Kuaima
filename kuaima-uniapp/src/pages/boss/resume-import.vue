<template>
  <view class="container">
    <view class="wb-header" :style="{ paddingTop: statusBarHeight + 8 + 'px' }">
      <view class="wb-back" @click="goBack"><image class="back-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" /></view>
      <text class="wb-title">简历导入</text>
      <!-- 微信小程序原生胶囊已占右上角，自绘胶囊会与其重叠，仅在非小程序端保留 -->
      <!-- #ifdef H5 -->
      <view class="wb-capsule">
        <view class="cap-btn"><image class="cap-svg" src="/static/icons/boss-profile/ellipsis.svg" mode="aspectFit" /></view>
        <view class="cap-divider"></view>
        <view class="cap-btn"><image class="cap-svg cap-svg-dot" src="/static/icons/boss-profile/dot.svg" mode="aspectFit" /></view>
      </view>
      <!-- #endif -->
    </view>

    <view class="wb-body">
      <!-- 上传区 -->
      <view class="upload-card">
        <view class="upload-icon"><image class="upload-icon-svg" src="/static/icons/enterprise-cert-form/cloud-arrow-up.svg" mode="aspectFit" /></view>
        <text class="upload-title">支持多种方式导入简历</text>
        <text class="upload-desc">上传简历文件后系统将自动解析入库\n便于统一筛选与管理</text>
        <view class="upload-btn" @click="chooseUpload">
          <image class="upload-btn-svg" src="/static/icons/boss-resume/upload-white.svg" mode="aspectFit" />
          <text>上传简历</text>
        </view>
        <view class="format-tags">
          <text class="format-tag">PDF</text>
          <text class="format-tag">DOC</text>
          <text class="format-tag">DOCX</text>
          <text class="format-tag">JPG</text>
          <text class="format-tag">PNG</text>
        </view>
      </view>

      <!-- 导入记录 -->
      <view class="section-title">导入记录</view>
      <view v-if="loading" class="wb-empty"><text class="wb-empty-text">加载中...</text></view>
      <view v-else-if="!records.length" class="wb-empty">
        <view class="wb-empty-icon"><image class="empty-svg" src="/static/icons/boss-resume/file-lines-gray.svg" mode="aspectFit" /></view>
        <text class="wb-empty-text">暂无导入记录</text>
      </view>
      <view v-for="r in records" :key="r.id" class="record-item">
        <view class="file-icon" :class="r.status === 'FAILED' ? 'fail' : r.status === 'SUCCESS' ? 'ok' : ''"><image class="file-svg" src="/static/icons/worker-job-detail/file-alt-orange.svg" mode="aspectFit" /></view>
        <view class="file-main">
          <text class="file-name">{{ r.fileName }}</text>
          <text class="file-time">{{ r.timestamp || '' }}</text>
        </view>
        <text class="file-status" :class="r.status === 'FAILED' ? 'fail' : 'ok'">{{ r.status === 'FAILED' ? '解析失败' : '导入成功' }}</text>
        <text class="file-view" @click="onRecordAction(r)">{{ r.status === 'FAILED' ? '重传' : '查看' }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { getResumeImports, createResumeImport } from "@/api/resume";

export default {
  data() {
    return {
      statusBarHeight: 44,
      loading: false,
      records: [],
    };
  },
  onLoad() {
    try {
      const info = uni.getSystemInfoSync();
      this.statusBarHeight = info.statusBarHeight || 44;
    } catch (e) {}
    this.loadRecords();
  },
  methods: {
    async loadRecords() {
      this.loading = true;
      try {
        this.records = (await getResumeImports()) || [];
      } catch (e) {
        // 暂不展示错误
      } finally {
        this.loading = false;
      }
    },
    /** 统一导入流程：上传文件 → 刷新导入记录 */
    async doImport(filePath, fileName) {
      if (!filePath) return;
      uni.showLoading({ title: "上传中...", mask: true });
      try {
        // 真实上传文件到后端（后端存 OSS 并创建一条 IMPORT 来源的简历草稿）
        await createResumeImport(filePath, fileName);
        uni.hideLoading();
        uni.showToast({ title: "导入成功，已加入简历库", icon: "none" });
        this.loadRecords();
      } catch (e) {
        uni.hideLoading();
        uni.showToast({ title: e?.message || "上传失败", icon: "none" });
      }
    },
    /** 上传入口：弹出 ActionSheet 选择来源，两种来源能力均保留 */
    chooseUpload() {
      uni.showActionSheet({
        itemList: ["从微信聊天选择文件", "从相册/拍照选择图片"],
        success: (res) => {
          if (res.tapIndex === 0) this.chooseFile();
          else if (res.tapIndex === 1) this.chooseImage();
        },
        fail: () => {},
      });
    },
    /** 从微信聊天选择文件（PDF / Word / 图片） */
    chooseFile() {
      uni.chooseMessageFile({
        count: 1,
        type: "file",
        extension: ["pdf", "doc", "docx", "jpg", "jpeg", "png"],
        success: (res) => {
          const file = res.tempFiles?.[0];
          if (file) this.doImport(file.path, file.name);
        },
        fail: (err) => {
          if (!/cancel/i.test(err?.errMsg || "")) {
            uni.showToast({ title: "选择文件失败", icon: "none" });
          }
        },
      });
    },
    /**
     * 从相册/拍照选择图片简历（jpg / png）。
     * 相册里的照片无法通过 chooseMessageFile 选到，必须走 chooseImage。
     */
    chooseImage() {
      uni.chooseImage({
        count: 1,
        sizeType: ["original", "compressed"],
        sourceType: ["album", "camera"],
        success: (res) => {
          const path = res.tempFilePaths?.[0];
          if (!path) return;
          // chooseImage 不返回原始文件名，按临时路径后缀推扩展名，兜底 jpg
          const matched = /\.([A-Za-z0-9]+)$/.exec(path.split("?")[0]);
          const ext = matched ? matched[1].toLowerCase() : "jpg";
          this.doImport(path, `图片简历.${ext}`);
        },
        fail: (err) => {
          if (!/cancel/i.test(err?.errMsg || "")) {
            uni.showToast({ title: "无法打开相册，请检查相册/相机授权", icon: "none" });
          }
        },
      });
    },
    /** 记录操作：失败记录「重传」（重新走选择流程），成功记录「查看」跳详情 */
    onRecordAction(r) {
      if (r.status === "FAILED") this.chooseUpload();
      else this.openResume(r);
    },
    openResume(r) {
      if (r.resumeId) {
        uni.navigateTo({ url: `/pages/boss/resume-detail?id=${r.resumeId}` });
      } else {
        // 仅在历史脏数据（导入记录未关联简历）时出现，新导入均有 resumeId
        uni.showToast({ title: "该记录未关联简历，请重新导入", icon: "none" });
      }
    },
    goBack() {
      uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/boss/resume" }) });
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f3f4f6;
}

.wb-header {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  padding: 8px 16px 12px;
  flex-shrink: 0;
}

.wb-back {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.back-svg {
  width: 16px;
  height: 16px;
}

.wb-title {
  flex: 1;
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
}

.wb-capsule {
  display: flex;
  align-items: center;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 17px;
  padding: 0 6px;
  height: 32px;
}

.cap-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cap-svg {
  width: 14px;
  height: 14px;
}

.cap-svg-dot {
  width: 9px;
  height: 9px;
}

.cap-divider {
  width: 1px;
  height: 16px;
  background: rgba(0, 0, 0, 0.15);
  margin: 0 2px;
}

.wb-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px 20px;
}

.upload-card {
  background: #fff;
  border-radius: 16px;
  padding: 34px 20px 30px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  text-align: center;
  margin-bottom: 18px;
}

.upload-icon {
  width: 74px;
  height: 74px;
  border-radius: 50%;
  margin: 0 auto 14px;
  background: linear-gradient(135deg, #ffe8d6, #ffd2bc);
  display: flex;
  align-items: center;
  justify-content: center;
}

.upload-icon-svg {
  width: 34px;
  height: 34px;
}

.upload-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  display: block;
}

.upload-desc {
  font-size: 12px;
  color: #999;
  margin-top: 7px;
  line-height: 1.6;
  display: block;
  white-space: pre-line;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 18px;
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  padding: 11px 42px;
  border-radius: 24px;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 6px 16px rgba(255, 107, 53, 0.3);
}

.upload-btn-svg {
  width: 16px;
  height: 16px;
}

.upload-btn:active {
  transform: scale(0.97);
}

.format-tags {
  display: flex;
  justify-content: center;
  gap: 7px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.format-tag {
  font-size: 11px;
  color: #999;
  background: #f5f6f8;
  padding: 3px 10px;
  border-radius: 6px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin: 4px 0 10px;
  display: flex;
  align-items: center;
}

.section-title::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 14px;
  background: #ff6b35;
  border-radius: 2px;
  margin-right: 6px;
}

.record-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
  border-radius: 14px;
  margin-bottom: 10px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}

.file-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
  background: #fff0e8;
  display: flex;
  align-items: center;
  justify-content: center;
}

.file-svg {
  width: 18px;
  height: 18px;
}

.file-icon.fail {
  background: #fef2f2;
  color: #ff4d4f;
}

.file-icon.ok {
  background: #e8f8ef;
  color: #10b981;
}

.file-main {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  display: block;
}

.file-time {
  font-size: 11.5px;
  color: #aaa;
  margin-top: 4px;
  display: block;
}

.file-status {
  font-size: 12px;
  font-weight: 500;
  flex-shrink: 0;
}

.file-status.ok {
  color: #10b981;
}

.file-status.fail {
  color: #ff4d4f;
}

.file-view {
  font-size: 12.5px;
  color: #ff6b35;
  flex-shrink: 0;
  margin-left: 4px;
}

.wb-empty {
  padding: 40px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.wb-empty-icon {
  margin-bottom: 10px;
}

.empty-svg {
  width: 34px;
  height: 34px;
}

.wb-empty-text {
  font-size: 13px;
  color: #999;
}
</style>
