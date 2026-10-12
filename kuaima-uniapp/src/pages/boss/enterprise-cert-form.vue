<template>
  <view class="container">
    <!-- 导航栏 -->
    <view
      class="nav-bar"
      :style="{
        paddingTop: `${statusBarHeight}px`,
        height: `${statusBarHeight + 50}px`,
      }"
    >
      <view class="nav-back" @click="goBack">
        <image
          class="nav-back-icon"
          src="/static/icons/enterprise-cert-form/arrow-left.svg"
          mode="aspectFit"
        />
      </view>
      <text class="nav-title">企业自招认证</text>
      <view class="nav-right">
        <image
          class="nav-more-icon"
          src="/static/icons/enterprise-cert-form/ellipsis.svg"
          mode="aspectFit"
        />
      </view>
    </view>

    <scroll-view scroll-y class="scroll-area">
      <!-- 认证类型 -->
      <view class="cert-type">
        <text class="cert-type-label">认证类型</text>
        <text class="cert-type-value">企业自招（仅限本公司招工）</text>
      </view>

      <!-- 企业信息 -->
      <view class="form-card">
        <text class="form-title">企业信息</text>
        <view class="form-item">
          <text class="form-label">企业名称</text>
          <input
            class="form-input"
            placeholder="请输入营业执照上的全称"
            v-model="companyName"
            maxlength="100"
          />
        </view>
        <view class="form-item">
          <text class="form-label">统一社会信用代码</text>
          <input
            class="form-input"
            placeholder="请输入18位信用代码"
            v-model="creditCode"
            maxlength="18"
          />
        </view>
        <view class="form-item">
          <text class="form-label">法定代表人</text>
          <input
            class="form-input"
            placeholder="请输入法人姓名"
            v-model="legalPerson"
            maxlength="50"
          />
        </view>
        <view class="form-item">
          <text class="form-label">联系电话</text>
          <input
            class="form-input"
            placeholder="请输入联系电话"
            v-model="contactPhone"
            maxlength="11"
            type="number"
          />
        </view>
        <view class="form-item">
          <text class="form-label">成立日期</text>
          <picker class="form-input date-picker" mode="date" :value="foundingDate" @change="onFoundingDateChange">
            <view class="date-picker-value" :class="{ placeholder: !foundingDate }">
              {{ foundingDate || "请选择成立日期" }}
            </view>
          </picker>
        </view>
        <view class="form-item">
          <text class="form-label">经营范围</text>
          <textarea
            class="form-input scope-input"
            placeholder="请输入营业执照上的经营范围"
            v-model="businessScope"
            maxlength="500"
          />
        </view>
      </view>

      <!-- 上传营业执照 -->
      <view class="form-card">
        <text class="form-title">上传营业执照</text>
        <view class="upload-area" :class="{ 'has-image': licensePreview }" @click="uploadLicense">
          <image
            v-if="licensePreview"
            class="license-preview"
            :src="licensePreview"
            mode="aspectFill"
          />
          <view v-else class="upload-icon">
            <image
              src="/static/icons/enterprise-cert-form/cloud-arrow-up.svg"
              mode="aspectFit"
            />
          </view>
          <text class="upload-text">{{
          licenseUploading
            ? "营业执照上传中..."
            : licensePreview
              ? "点击替换营业执照"
              : licenseImageUrl
                ? "营业执照已上传，点击更换"
                : "点击上传营业执照"
          }}</text>
          <text v-if="!licensePreview" class="upload-hint"
            >请上传原件或加盖公章的复印件，支持JPG/PNG格式，不超过5M</text
          >
        </view>
      </view>

      <!-- 同意协议 -->
      <view class="agree-row">
        <view
          class="agree-checkbox"
          :class="{ checked: agreed }"
          @click="toggleAgree"
        >
          <image
            v-if="agreed"
            src="/static/icons/enterprise-cert-form/check.svg"
            mode="aspectFit"
          />
        </view>
        <text class="agree-text">
          我已阅读并同意<text
            class="agree-link"
            @click.stop="showAgreement('service')"
            >《企业认证服务协议》</text
          >和<text class="agree-link" @click.stop="showAgreement('rule')"
            >《信息发布规则》</text
          >，承诺所提交的材料真实有效。
        </text>
      </view>

      <!-- 提交按钮 -->
      <button
        class="submit-btn"
        :disabled="!agreed || submitting"
        @click="submitForm"
      >
        {{ submitting ? "提交中..." : "提交认证" }}
      </button>
    </scroll-view>
  </view>
</template>

<script>
import {
  getBossProfile,
  submitEnterpriseCertification,
  uploadEnterpriseLicense,
} from "@/api/backend";

export default {
  data() {
    return {
      statusBarHeight: 0,
      userId: "",
      companyName: "",
      creditCode: "",
      legalPerson: "",
      contactPhone: "",
      foundingDate: "",
      businessScope: "",
      licensePath: "",
      licensePreview: "",
      licenseImageUrl: "",
      licenseUploading: false,
      agreed: false,
      submitting: false,
    };
  },
  onLoad() {
    try {
      const info =
        typeof uni.getWindowInfo === "function"
          ? uni.getWindowInfo()
          : uni.getSystemInfoSync();
      this.statusBarHeight = Number(info.statusBarHeight || 0);
    } catch (_) {}
    this.userId = String(uni.getStorageSync("userId") || "2001");
    this.loadProfile();
  },
  methods: {
    async loadProfile() {
      try {
        const profile = await getBossProfile(this.userId);
        if (!profile) return;
        this.companyName = profile.companyName || "";
        this.creditCode = profile.licenseNo || "";
        this.legalPerson = profile.legalRep || profile.realName || "";
        this.contactPhone = profile.contactPhone || profile.phone || "";
        this.foundingDate = profile.foundingDate || "";
        this.businessScope = profile.businessScope || "";
        this.licenseImageUrl = profile.licenseImageUrl || "";
        this.licensePreview = "";
      } catch (_) {
        // 未登录或接口不可用时保留空表单，不用演示数据覆盖用户输入。
      }
    },
    goBack() {
      uni.navigateBack();
    },
    toggleAgree() {
      this.agreed = !this.agreed;
    },
    uploadLicense() {
      if (this.licenseUploading) return;
      uni.chooseImage({
        count: 1,
        sizeType: ["compressed"],
        sourceType: ["album", "camera"],
        success: async (res) => {
          const path = res.tempFilePaths?.[0];
          if (!path) return;
          const selected = (res.tempFiles || []).find((file) => file.path === path) || res.tempFiles?.[0];
          if (selected?.size > 5 * 1024 * 1024) {
            uni.showToast({ title: "营业执照图片不能超过5MB", icon: "none" });
            return;
          }
          this.licensePath = path;
          this.licenseUploading = true;
          try {
            const uploaded = await uploadEnterpriseLicense(path);
            this.licenseImageUrl = uploaded?.objectKey || "";
            this.licensePreview = path;
            if (!this.licenseImageUrl) throw new Error("上传结果缺少文件地址");
            uni.showToast({ title: "营业执照上传成功", icon: "success" });
          } catch (error) {
            this.licensePath = "";
            this.licensePreview = "";
            this.licenseImageUrl = "";
            uni.showToast({ title: error.message || "营业执照上传失败", icon: "none" });
          } finally {
            this.licenseUploading = false;
          }
        },
      });
    },
    onFoundingDateChange(event) {
      this.foundingDate = event.detail.value || "";
    },
    showAgreement(type) {
      if (type === "service") {
        uni.showToast({ title: "《企业认证服务协议》", icon: "none" });
      } else {
        uni.showToast({ title: "《信息发布规则》", icon: "none" });
      }
    },
    async submitForm() {
      if (this.submitting) return;
      const checks = [
        [this.companyName, "请填写企业名称"],
        [this.creditCode, "请填写统一社会信用代码"],
        [this.legalPerson, "请填写法定代表人"],
        [this.contactPhone, "请填写联系电话"],
        [this.foundingDate, "请选择成立日期"],
        [this.businessScope, "请填写经营范围"],
      ];
      const missing = checks.find(([value]) => !String(value || "").trim());
      if (missing) {
        uni.showToast({ title: missing[1], icon: "none" });
        return;
      }
      const creditCode = this.creditCode.trim().toUpperCase();
      if (!/^[0-9A-Z]{18}$/.test(creditCode)) {
        uni.showToast({
          title: "统一社会信用代码须为18位数字或字母",
          icon: "none",
        });
        return;
      }
      if (!/^1\d{10}$/.test(this.contactPhone.trim())) {
        uni.showToast({ title: "请输入正确的联系电话", icon: "none" });
        return;
      }
      this.submitting = true;
      try {
        await submitEnterpriseCertification({
          userId: this.userId,
          companyName: this.companyName.trim(),
          industry: "企业自招",
          licenseNo: creditCode,
          legalRep: this.legalPerson.trim(),
          contactPhone: this.contactPhone.trim(),
          foundingDate: this.foundingDate,
          businessScope: this.businessScope.trim(),
          licenseImageUrl: this.licenseImageUrl,
        });
        uni.showToast({ title: "提交成功，请等待审核", icon: "success" });
        setTimeout(() => uni.navigateBack(), 900);
      } catch (error) {
        uni.showToast({ title: error.message || "提交认证失败", icon: "none" });
      } finally {
        this.submitting = false;
      }
    },
  },
};
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
  align-items: flex-end;
  justify-content: space-between;
  padding: 0 32rpx 18rpx;
  background: #fff;
  flex-shrink: 0;
  position: relative;
}

.nav-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
}

.nav-back-icon {
  width: 34rpx;
  height: 34rpx;
}

.nav-title {
  position: absolute;
  left: 50%;
  bottom: 24rpx;
  transform: translateX(-50%);
  font-size: 34rpx;
  font-weight: 600;
  color: #333;
}

.nav-right {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-more-icon {
  width: 30rpx;
  height: 30rpx;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 20px;
}

.cert-type {
  background: #fff;
  margin: 12px 16px;
  border-radius: 12px;
  padding: 16px;
}

.cert-type-label {
  display: block;
  font-size: 13px;
  color: #999;
}

.cert-type-value {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-top: 4px;
}

.form-card {
  background: #fff;
  margin: 12px 16px;
  border-radius: 12px;
  padding: 16px;
}

.form-title {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
}

.form-item {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}

.form-item:last-child {
  border-bottom: none;
}

.form-label {
  flex-shrink: 0;
  font-size: 14px;
  color: #333;
  width: 130px;
}

.form-input {
  min-width: 0;
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: #666;
}

.form-input::placeholder {
  color: #ccc;
}

.upload-area {
  border: 1px dashed #ff6b35;
  border-radius: 12px;
  padding: 24px;
  text-align: center;
  background: #fff8f5;
}

.upload-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 36px;
}

.upload-icon image {
  width: 44px;
  height: 36px;
}

.upload-text {
  display: block;
  font-size: 14px;
  color: #333;
  margin-top: 8px;
  font-weight: 500;
}

.upload-hint {
  display: block;
  max-width: 292px;
  margin-left: auto;
  margin-right: auto;
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.agree-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 16px;
}

.agree-checkbox {
  width: 16px;
  height: 16px;
  border: 1.5px solid #ddd;
  border-radius: 3px;
  flex-shrink: 0;
  margin-top: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.agree-checkbox.checked {
  background: #ff6b35;
  border-color: #ff6b35;
}

.agree-checkbox image {
  width: 11px;
  height: 11px;
}

.agree-text {
  font-size: 12px;
  color: #666;
  line-height: 1.5;
}

.agree-link {
  color: #ff6b35;
}

.submit-btn {
  box-sizing: border-box;
  width: calc(100% - 32px);
  height: 48px;
  padding: 0;
  line-height: 48px;
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  border: none;
  border-radius: 24px;
  font-size: 15px;
  font-weight: 600;
  margin: 20px 16px 30px;
}

.submit-btn:disabled {
  opacity: 0.5;
}
.date-picker {
  padding: 0;
}
.date-picker-value {
  padding: 22rpx 24rpx;
  color: #333;
  font-size: 28rpx;
}
.date-picker-value.placeholder {
  color: #999;
}
.scope-input {
  height: 168rpx;
  line-height: 1.6;
  padding: 20rpx 24rpx;
}
.license-preview {
  width: 100%;
  height: 320rpx;
  border-radius: 12rpx;
}
.upload-area.has-image {
  padding: 12rpx;
}
</style>
