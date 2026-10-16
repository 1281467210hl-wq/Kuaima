<template>
  <view class="sign-wrap">
    <!-- 顶部导航 -->
    <view class="wb-header" style="position: relative; width: 100%">
      <view class="wb-back" @click="goBack"><text class="back-icon">‹</text></view>
      <view class="wb-title" style="color: #4a3500">{{ signLabel }}</view>
      <!-- 微信小程序原生胶囊已占右上角，自绘胶囊会与其重叠，仅在非小程序端保留 -->
      <!-- #ifndef MP-WEIXIN -->
      <view class="wb-capsule">
        <view class="cap-btn"><text class="cap-ico">⋯</text></view>
        <view class="cap-divider"></view>
        <view class="cap-btn"><text class="cap-ico">○</text></view>
      </view>
      <!-- #endif -->
    </view>

    <!-- 签到/签退切换 -->
    <view class="sign-tabs">
      <view
        class="sign-tab"
        :class="{ active: signType === 'in' }"
        @click="switchSignTab('in')"
      >签到码</view>
      <view
        class="sign-tab"
        :class="{ active: signType === 'out' }"
        @click="switchSignTab('out')"
      >签退码</view>
    </view>

    <view class="sign-title">{{ signLabel }}</view>
    <view class="sign-sub">
      <text class="shield-ico">🛡</text>
      <text>安全加密·隐私保护</text>
    </view>

    <!-- 二维码 -->
    <view class="qr-card">
      <image v-if="qrImageUrl" :src="qrImageUrl" class="qr-image" mode="aspectFit"></image>
      <view v-else class="qr-loading">
        <text>生成中...</text>
      </view>
      <view class="qr-mask">
        <text class="fingerprint-ico">👆</text>
      </view>
    </view>

    <view class="qr-tip">
      <text class="wechat-ico">💬</text>
      <text>{{ qrTip }}</text>
    </view>
  </view>
</template>

<script>
import { getProject, getAttendanceCodes } from "@/api/project";

export default {
  data() {
    return {
      projectId: "",
      project: {},
      signType: "in", // in=签到码 out=签退码
      codes: {}, // 后端当日两码 {workCode, workCodeExpiresAt, leaveCode, leaveCodeExpiresAt}
      qrContent: "",
      qrImageUrl: "",
    };
  },
  computed: {
    signLabel() {
      return this.signType === "in" ? "签到码" : "签退码";
    },
    qrTip() {
      return this.signType === "in" ? "微信扫一扫 开始签到" : "微信扫一扫 完成签退";
    },
    currentCode() {
      return this.signType === "in" ? this.codes.workCode : this.codes.leaveCode;
    },
  },
  onLoad(options) {
    this.projectId = options.id || "";
    this.loadCodes();
    if (this.projectId) {
      this.loadProject(this.projectId);
    } else {
      this.generateQR();
    }
  },
  methods: {
    async loadProject(id) {
      try {
        const res = await getProject(id);
        // request 已解包 Result.data，直接就是项目对象；兜底兼容未解包场景
        this.project = (res && res.id) ? res : (res && res.data) || {};
      } catch (e) {
        console.warn("加载项目失败", e);
      }
      this.generateQR();
    },
    async loadCodes() {
      // 后端签到/签退两码（GET /boss/attendance-codes），失败不阻塞二维码展示
      try {
        const res = await getAttendanceCodes();
        const body = (res && res.workCode !== undefined) ? res : (res && res.data) || {};
        this.codes = body || {};
      } catch (e) {
        console.warn("考勤码加载失败", e);
      }
    },
    switchSignTab(type) {
      if (this.signType === type) return;
      this.signType = type;
      this.generateQR();
    },
    generateQR() {
      // 二维码内容包含项目ID、签到/签退类型与后端验证码，扫码后完成对应打卡记录
      const projectId = this.project.id || this.projectId || "0";
      const timestamp = Date.now();
      const code = this.currentCode || "";
      this.qrContent = `https://kuaima.com/checkin?projectId=${projectId}&type=${this.signType}&code=${code}&t=${timestamp}&auto=1`;

      // 使用在线QR码生成服务
      this.generateQRImageUrl();
    },
    generateQRImageUrl() {
      // 使用 QR 码生成服务
      const content = encodeURIComponent(this.qrContent);
      const size = 300;
      this.qrImageUrl = `https://api.qrserver.com/v1/create-qr-code/?size=${size}x${size}&data=${content}`;
    },
    goBack() {
      uni.navigateBack();
    },
  },
};
</script>

<style lang="scss" scoped>
.sign-wrap {
  position: absolute;
  top: 47px;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(180deg, #ffd96f 0%, #ffc53d 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.wb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 44px;
  flex-shrink: 0;
}

.wb-back {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 50%;
}

.back-icon {
  font-size: 22px;
  color: #4a3500;
  font-weight: 700;
}

.wb-title {
  font-size: 17px;
  font-weight: 600;
}

.wb-capsule {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 17px;
  padding: 0 6px;
  height: 34px;
}

.cap-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cap-ico {
  font-size: 16px;
  color: #4a3500;
}

.cap-divider {
  width: 1px;
  height: 16px;
  background: rgba(74, 53, 0, 0.2);
  margin: 0 2px;
}

.sign-tabs {
  display: flex;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 22px;
  padding: 4px;
  margin-top: 18px;
  width: 200px;
  justify-content: center;
  flex-shrink: 0;
}

.sign-tab {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  border-radius: 18px;
  font-size: 14px;
  font-weight: 600;
  color: #4a3500;
  transition: all 0.2s;
}

.sign-tab.active {
  background: #fff;
  color: #4a3500;
  box-shadow: 0 2px 8px rgba(120, 80, 0, 0.15);
}

.sign-title {
  font-size: 20px;
  font-weight: 700;
  color: #4a3500;
  margin-top: 26px;
}

.sign-sub {
  font-size: 12px;
  color: rgba(74, 53, 0, 0.7);
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.shield-ico {
  font-size: 14px;
}

.qr-card {
  background: #fff;
  border-radius: 20px;
  padding: 22px;
  margin-top: 30px;
  box-shadow: 0 12px 30px rgba(120, 80, 0, 0.25);
  position: relative;
}

.qr-image {
  width: 218px;
  height: 218px;
}

.qr-loading {
  width: 218px;
  height: 218px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 14px;
}

.qr-mask {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 46px;
  height: 46px;
  background: #fff;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ff6b35;
  font-size: 22px;
  box-shadow: 0 0 0 4px #fff;
}

.fingerprint-ico {
  font-size: 24px;
}

.qr-tip {
  font-size: 13px;
  color: #666;
  margin-top: 28px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.wechat-ico {
  font-size: 16px;
}
</style>
