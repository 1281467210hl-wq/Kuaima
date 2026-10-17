<template>
  <view class="login-page">
    <view
      class="phone-frame"
      :class="selectedRole"
      :style="{ paddingTop: `${statusBarHeight}px` }"
    >
      <view class="top-nav">
        <button v-if="userRole" class="nav-back" @click="goBack">
          <image
            class="nav-back-icon"
            src="/static/icons/login/chevron-left.svg"
            mode="aspectFit"
          />
        </button>
        <view v-else class="nav-placeholder"></view>
        <view v-if="userRole" class="brand-tag">快马日结</view>
      </view>
      <view v-if="!userRole" class="role-select-view">
        <view class="welcome-copy">
          <text class="welcome-subtitle">很高兴见到你</text>
          <text class="welcome-title">请选择你的身份</text>
        </view>
        <view class="role-grid">
          <button
            class="role-card worker"
            :class="{ active: selectedRole === 'worker' }"
            @click="selectedRole = 'worker'"
          >
            <view v-if="selectedRole === 'worker'" class="selected-mark">
              <image src="/static/icons/login/check-white.svg" mode="aspectFit" />
            </view>
            <text class="role-name">零工找活</text>
            <text class="role-desc">真老板</text>
            <text class="role-desc">真工价</text>
            <text class="role-desc">真日结</text>
            <image
              class="role-art"
              src="/static/login-worker.png"
              mode="widthFix"
            />
          </button>
          <button
            class="role-card boss"
            :class="{ active: selectedRole === 'boss' }"
            @click="selectedRole = 'boss'"
          >
            <view v-if="selectedRole === 'boss'" class="selected-mark">
              <image src="/static/icons/login/check-white.svg" mode="aspectFit" />
            </view>
            <text class="role-name">老板招工</text>
            <text class="role-desc">熟练工</text>
            <text class="role-desc">上岗快</text>
            <text class="role-desc">人靠谱</text>
            <image
              class="role-art"
              src="/static/login-boss.png"
              mode="widthFix"
            />
          </button>
        </view>
        <view class="bottom-action">
          <button class="role-cta" @click="confirmRole">
            {{ selectedRole === "worker" ? "我是零工，去找活" : "我是老板，去招工" }}
          </button>
        </view>
      </view>
      <view v-else class="login-view">
        <view class="hero"
          ><text class="hero-title">{{
            selectedRole === "boss" ? "千万老板的选择" : "3000万零工的选择"
          }}</text
          ><view class="hero-subtitle"
            ><text>近30%完单零工收入千元以上</text
            ><image
              class="hero-info-icon"
              src="/static/icons/login/circle-info.svg"
              mode="aspectFit"
            /></view
          ><text class="hero-tagline">{{
            selectedRole === "boss"
              ? "真零工·真上岗·真高效"
              : "真老板·真工价·真日结"
          }}</text></view
        >
        <view class="mobile-login-card"
          ><text class="login-title">{{ phoneRequired ? "绑定手机号" : "手机号一键登录" }}</text
          >
          <!-- #ifdef MP-WEIXIN -->
          <button
            v-if="phoneRequired || isLoginPrepareIncomplete"
            class="phone-input-btn"
            open-type="getPhoneNumber"
            :disabled="loggingIn || preparingLogin"
            @getphonenumber="handlePhoneLogin"
          >
            {{
              preparingLogin
                ? "准备中…"
                : loggingIn
                  ? "授权注册中…"
                  : phoneRequired
                    ? "授权手机号并完成注册"
                    : "手机号快捷登录"
            }}
          </button>
          <button
            v-else
            class="phone-input-btn"
            :disabled="loggingIn || preparingLogin"
            @click="doLogin()"
          >
            {{ preparingLogin ? "准备中…" : loggingIn ? "登录中…" : "手机号快捷登录" }}
          </button>
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <button
            class="phone-input-btn"
            :disabled="loggingIn"
            @click="doLogin()"
          >
            {{ loggingIn ? "登录中…" : "手机号快捷登录" }}
          </button>
          <!-- #endif -->
          <text class="divider-line" @click="goBack">切换身份</text
          ><view class="agreement-row"
            ><view
              class="custom-checkbox"
              :class="{ checked: agreed }"
              @click="agreed = !agreed"
            >
              <image
                v-if="agreed"
                src="/static/icons/login/check-white.svg"
                mode="aspectFit"
              /></view
            ><text
              >我已阅读、理解并同意
              <text class="link" @click="openAgreement('user-agreement')"
                >《服务协议》</text
              >
              及
              <text class="link" @click="openAgreement('privacy')"
                >《隐私协议》</text
              ></text
            ></view
          ><text v-if="errorMessage" class="error-message">{{
            errorMessage
          }}</text
          ><text v-else-if="flowMessage" class="flow-message">{{
            flowMessage
          }}</text
          ></view
        >
      </view>
    </view>

    <view v-if="agreementVisible" class="agreement-mask" @click="closeAgreement">
      <view class="agreement-modal" @click.stop>
        <view class="agreement-header">
          <text class="agreement-title">{{ activeAgreement?.name || "协议内容" }}</text>
          <text class="agreement-close" @click="closeAgreement">×</text>
        </view>
        <scroll-view scroll-y class="agreement-body">
          <text v-if="agreementLoading" class="agreement-loading">协议加载中…</text>
          <template v-else>
            <text class="agreement-content">{{ activeAgreement?.content || "暂未配置协议内容" }}</text>
            <button
              v-if="activeAgreement?.fileUrl"
              class="agreement-file-button"
              @click="openAgreementFile"
            >查看协议文件</button>
          </template>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { wechatLogin, getCurrentUser } from "@/api/auth";
import { getPublicAgreement } from "@/api/backend";

const pages = getCurrentPages();
const query = pages[pages.length - 1]?.options || {};
const statusBarHeight = Number(uni.getWindowInfo().statusBarHeight || 0);

// 检测是否在微信开发者工具中运行
function isDevTools() {
  try {
    const systemInfo = uni.getSystemInfoSync();
    return systemInfo.platform === "devtools";
  } catch (_) {
    return false;
  }
}
const userRole = ref(
  query.role === "boss" || query.role === "worker" ? query.role : "",
);
const selectedRole = ref(userRole.value || "worker");
const agreed = ref(false);
const errorMessage = ref("");
const flowMessage = ref("");
const phoneRequired = ref(false);
const loggingIn = ref(false);
const preparingLogin = ref(false);
const registrationToken = ref("");
const preparedLoginResult = ref(null);
const agreementVisible = ref(false);
const agreementLoading = ref(false);
const activeAgreement = ref(null);
const agreementTypes = {
  "user-agreement": "用户服务协议",
  privacy: "隐私协议",
};
const isLoginPrepareIncomplete = computed(() => {
  if (!userRole.value) return true;
  if (preparingLogin.value) return false;
  if (preparedLoginResult.value) return false;
  if (registrationToken.value) return false;
  return !!errorMessage.value;
});

function confirmRole() {
  userRole.value = selectedRole.value;
  prepareWechatLogin();
}
function goBack() {
  userRole.value = "";
  agreed.value = false;
  errorMessage.value = "";
  flowMessage.value = "";
  phoneRequired.value = false;
  registrationToken.value = "";
  preparedLoginResult.value = null;
}
async function openAgreement(page) {
  const type = agreementTypes[page] || agreementTypes["user-agreement"];
  activeAgreement.value = null;
  agreementLoading.value = true;
  agreementVisible.value = true;
  try {
    const result = await getPublicAgreement(type);
    activeAgreement.value = result || {
      name: page === "privacy" ? "隐私协议" : "服务协议",
      content: "暂未配置协议内容，请稍后再试。",
    };
  } catch (error) {
    activeAgreement.value = {
      name: page === "privacy" ? "隐私协议" : "服务协议",
      content: error?.message || "协议加载失败，请稍后再试。",
    };
  } finally {
    agreementLoading.value = false;
  }
}

function closeAgreement() {
  if (!agreementLoading.value) agreementVisible.value = false;
}

function openAgreementFile() {
  const fileUrl = activeAgreement.value?.fileUrl;
  if (!fileUrl) return;
  uni.downloadFile({
    url: fileUrl,
    success(response) {
      if (response.statusCode !== 200) {
        uni.showToast({ title: "协议文件下载失败", icon: "none" });
        return;
      }
      uni.openDocument({
        filePath: response.tempFilePath,
        showMenu: true,
        fail: () => uni.showToast({ title: "协议文件打开失败", icon: "none" }),
      });
    },
    fail: () => uni.showToast({ title: "协议文件下载失败", icon: "none" }),
  });
}
function handlePhoneLogin(event) {
  const phoneCode = event?.detail?.code;
  if (!phoneCode) {
    flowMessage.value = "";
    errorMessage.value = "需要授权手机号后才能完成注册";
    return;
  }
  doLogin(phoneCode);
}

async function doLogin(phoneCode = "") {
  if (loggingIn.value) return;
  if (!agreed.value) {
    flowMessage.value = "";
    errorMessage.value = "请先阅读并同意服务协议及隐私协议";
    return;
  }
  const role = selectedRole.value === "boss" ? "BOSS" : "USER";
  // #ifdef MP-WEIXIN
  loggingIn.value = true;
  errorMessage.value = "";
  flowMessage.value = "";

  if (!phoneCode && preparedLoginResult.value) {
      const result = preparedLoginResult.value;
      preparedLoginResult.value = null;
      registrationToken.value = "";
      try {
        await completeLogin(result);
      } catch (e) {
        errorMessage.value = e.message || "微信登录失败";
        loggingIn.value = false;
      }
      return;
    }
    const performWechatLogin = async (code) => {
      try {
        const payload = phoneCode && registrationToken.value
          ? { registrationToken: registrationToken.value, role, phoneCode }
          : { code, role, ...(phoneCode ? { phoneCode } : {}) };
        const result = await wechatLogin(payload);
        if (result.needPhoneNumber === true) {
          if (phoneCode) throw new Error("手机号绑定未完成，请重试");
          phoneRequired.value = true;
          flowMessage.value = "首次使用，请授权手机号完成注册";
          registrationToken.value = result.registrationToken || "";
          loggingIn.value = false;
          return;
        }
        if (result.needPhoneNumber !== false) {
          throw new Error("登录接口缺少 needPhoneNumber 字段");
        }
        phoneRequired.value = false;
        registrationToken.value = "";
        preparedLoginResult.value = null;
        await completeLogin(result);
      } catch (e) {
        console.error("[wechatLogin] error:", e?.message || e);
        flowMessage.value = "";
        errorMessage.value = e.message || "微信登录失败";
        loggingIn.value = false;
      }
    };
    if (phoneCode && registrationToken.value) {
      await performWechatLogin("");
      return;
    }
    uni.login({
      provider: "weixin",
      success: async ({ code }) => {
        await performWechatLogin(code);
      },
      fail: () => {
        flowMessage.value = "";
        errorMessage.value = "无法获取微信登录凭证";
        loggingIn.value = false;
      },
    });
    return;
    // #endif
    // #ifdef H5
    errorMessage.value =
      "真实接口暂仅支持微信小程序登录，请在微信开发者工具中运行";
    return;
    // #endif
}

function prepareWechatLogin() {
  if (!userRole.value || preparingLogin.value) return;
  // #ifdef MP-WEIXIN
  preparingLogin.value = true;
  registrationToken.value = "";
  preparedLoginResult.value = null;
  phoneRequired.value = false;
  errorMessage.value = "";
  flowMessage.value = "";
  const role = selectedRole.value === "boss" ? "BOSS" : "USER";
  uni.login({
    provider: "weixin",
    success: async ({ code }) => {
      try {
        const result = await wechatLogin({ code, role });
        if (result.needPhoneNumber === true) {
          registrationToken.value = result.registrationToken || "";
          phoneRequired.value = true;
          return;
        }
        if (result.needPhoneNumber !== false) {
          throw new Error("登录接口缺少 needPhoneNumber 字段");
        }
        phoneRequired.value = false;
        preparedLoginResult.value = result;
      } catch (e) {
        console.error("[prepareWechatLogin] error:", e?.message || e);
        errorMessage.value = e.message || "登录状态检查失败";
      } finally {
        preparingLogin.value = false;
      }
    },
    fail: () => {
      errorMessage.value = "无法获取微信登录凭证";
      preparingLogin.value = false;
    },
  });
  // #endif
}

onMounted(prepareWechatLogin);

async function completeLogin(result) {
  if (!result.accessToken || !result.userId || !result.role) {
    throw new Error("登录接口返回数据不完整");
  }
  uni.setStorageSync("token", result.accessToken);
  uni.setStorageSync("userId", String(result.userId));
  uni.setStorageSync("role", result.role);
  if (result.phone) uni.setStorageSync("userPhone", result.phone);
  if (result.enterpriseId !== undefined && result.enterpriseId !== null) uni.setStorageSync("enterpriseId", String(result.enterpriseId));
  else uni.removeStorageSync("enterpriseId");
  if (Array.isArray(result.permissions)) uni.setStorageSync("enterprisePermissions", result.permissions);
  else uni.removeStorageSync("enterprisePermissions");
  if (result.certStatus) {
    uni.setStorageSync("workerCertStatus", result.certStatus);
    uni.setStorageSync("certStatus", result.certStatus);
  }
  const target = result.role === "BOSS" ? "/pages/boss/home" : "/pages/worker/home";

  // 登录态已经写入本地，先进入首页；用户资料属于补充信息，不应阻塞主导航。
  getCurrentUser()
    .then((user) => {
      if (user && typeof user === "object") {
        uni.setStorageSync("userInfo", user);
        if (user.phone) uni.setStorageSync("userPhone", user.phone);
        if (user.certStatus) uni.setStorageSync("workerCertStatus", user.certStatus);
      }
    })
    .catch(() => {});

  return new Promise((resolve, reject) => {
    let settled = false;
    const finish = (error) => {
      if (settled) return;
      settled = true;
      if (error) reject(error);
      else resolve();
    };
    uni.reLaunch({
      url: target,
      success: () => finish(),
      fail: (error) => {
        // 开发者工具热重载期间 reLaunch 偶发超时，使用 redirectTo 做一次兜底。
        uni.redirectTo({
          url: target,
          success: () => finish(),
          fail: (redirectError) => finish(redirectError || error),
        });
      },
    });
  });
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  background: #f3f4f6;
}
.phone-frame {
  box-sizing: border-box;
  width: 390px;
  height: 844px;
  padding-top: var(--status-bar-height);
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #fffbf5 0%, #ffe4b5 50%, #ffbe5a 100%);
  color: #333;
}
.phone-frame.boss {
  background: linear-gradient(180deg, #fff5e6 0%, #ffe4b5 50%, #ffd966 100%);
}
.top-nav {
  height: 50px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-sizing: border-box;
}
.nav-placeholder,
.nav-back {
  width: 36px;
  height: 36px;
}
.nav-back {
  border: 0;
  border-radius: 50%;
  background: #ffffffb3;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-back::after {
  border: none;
}
.nav-back-icon {
  width: 10px;
  height: 16px;
}
.brand-tag {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  background: #fff;
  padding: 8px 20px;
  border-radius: 24px;
  font-weight: 700;
  font-size: 16px;
  color: #8b4513;
  box-shadow: 0 2px 10px #00000014;
  white-space: nowrap;
}
.role-select-view {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.login-view {
  flex: 1 1 auto;
  min-height: 0;
  display: block;
  background: transparent;
}
.role-select-view {
  padding: 18px 20px calc(24px + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background: #fff9d7;
}
.welcome-copy { text-align: center; margin-bottom: 22px; }
.welcome-subtitle { display: block; font-size: 13px; font-weight: 500; color: #8b6f00; margin-bottom: 8px; }
.welcome-title { display: block; font-size: 25px; line-height: 1.3; font-weight: 800; color: #2d2200; }
.role-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; align-items: stretch; }
.hero-subtitle {
  font-size: 14px;
  color: #a0522d;
}
.role-card {
  position: relative;
  width: auto;
  min-width: 0;
  min-height: 300px;
  border: 1.5px solid transparent;
  background: #f0deba;
  border-radius: 14px;
  padding: 18px 14px 14px;
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  text-align: left;
  box-shadow: none;
  box-sizing: border-box;
  overflow: hidden;
}
.role-card.worker { background: #f0deba; border-color: #ffbf00; }
.role-card.boss { background: #f0deba; border-color: #e9c74c; }
.role-card::after { border: none; }
.role-card.worker.active {
  border-color: #ff7743;
  background: #ffe375;
  box-shadow: 0 10px 22px rgba(45, 34, 0, 0.35);
}
.role-card.boss.active {
  border-color: #ff7743;
  background: #ffe375;
  box-shadow: 0 10px 22px rgba(45, 34, 0, 0.25);
}
.role-name, .role-desc {
  display: block;
}
.role-name {
  font-size: 23px;
  font-weight: 900;
  color: #2d2200;
  margin-bottom: 12px;
  line-height: 1.15;
  letter-spacing: 0.5px;
  text-align: left;
}
.role-desc {
  width: 100%;
  text-align: center;
  background: #fff;
  color: #4a3500;
  font-size: 13px;
  line-height: 34px;
  height: 34px;
  margin-top: 0;
  margin-bottom: 6px;
  border-radius: 12px;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.role-art {
  display: block;
  width: 100%;
  max-width: 200px;
  height: auto;
  margin-top: auto;
  align-self: center;
  margin-bottom: -14px;
}
.selected-mark {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 34px;
  height: 34px;
  border-radius: 0 14px 0 14px;
  background: #ff7743;
  display: flex;
  align-items: center;
  justify-content: center;
}
.selected-mark image {
  width: 12px;
  height: 14px;
}
.bottom-action {
  margin-top: auto;
  width: 100%;
}
.role-cta,
.phone-input-btn {
  border: 0;
  color: #fff;
  padding: 16px 20px;
  border-radius: 50px;
  font-weight: 600;
  font-size: 16px;
  box-shadow: 0 8px 24px #ff6b3559;
  line-height: normal;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}
.role-cta::after,
.phone-input-btn::after { border: none; }
.phone-input-btn[disabled] {
  opacity: 0.65;
}
.role-cta {
  width: 100%;
  margin-top: 28px;
  background: #fdcd01;
  color: #4a3500;
  font-weight: 800;
  letter-spacing: 1px;
  box-shadow: 0 8px 24px rgba(255, 200, 30, 0.45);
}
.hero {
  position: relative;
  padding: 16px 24px 0;
}
.hero-title {
  font-size: 36px;
  letter-spacing: -1px;
  line-height: 1.1;
  font-weight: 800;
  color: #8b4513;
}
.hero-subtitle {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  margin-bottom: 0;
}
.hero-info-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.hero-tagline {
  display: block;
  font-size: 20px;
  color: #d2691e;
  margin-top: 16px;
  font-weight: 700;
}
.mobile-login-card {
  margin: 32px 16px 0;
  background: #fff;
  border-radius: 24px;
  padding: 28px 24px 16px;
  box-sizing: border-box;
  width: auto;
  min-height: 0;
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  box-shadow: 0 -4px 30px rgba(0, 0, 0, 0.06);
}
.login-title {
  font-size: 24px;
  font-weight: 800;
  display: block;
  margin-bottom: 20px;
}
.phone-input-btn {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #2ecc71, #27ae60);
  box-shadow: 0 6px 20px rgba(46, 204, 113, 0.35);
}
.phone-input-btn:active {
  transform: scale(0.98);
}
.divider-line {
  display: block;
  margin: 16px auto;
  padding: 0;
  width: 100%;
  text-align: center;
  color: #999;
  font-size: 13px;
  line-height: 24px;
}
.agreement-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: #999;
  font-size: 12px;
  line-height: 1.6;
}
.custom-checkbox {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border: 2px solid #ddd;
  border-radius: 50%;
  background: #fff;
  color: #fff;
  flex: none;
  font-size: 10px;
  box-sizing: border-box;
  margin-top: 2px;
}
.custom-checkbox.checked {
  background: #ff6b35;
  border-color: #ff6b35;
}
.custom-checkbox image {
  width: 8px;
  height: 9px;
}
.link {
  color: #ff6b35;
  text-decoration: underline;
}
.error-message {
  color: #e34d59;
  font-size: 12px;
  margin-top: 12px;
}
.flow-message {
  color: #8b6a45;
  font-size: 12px;
  margin-top: 12px;
}
.agreement-mask {
  position: fixed;
  inset: 0;
  z-index: 99;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.45);
}
.agreement-modal {
  width: 100%;
  max-height: 82vh;
  padding: 20px 18px calc(20px + env(safe-area-inset-bottom));
  border-radius: 18px 18px 0 0;
  background: #fff;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}
.agreement-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.agreement-title {
  flex: 1;
  color: #333;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
}
.agreement-close {
  color: #999;
  font-size: 28px;
  line-height: 1;
  padding: 0 4px;
}
.agreement-body {
  flex: 1;
  min-height: 240px;
  max-height: 62vh;
}
.agreement-loading,
.agreement-content {
  display: block;
  color: #555;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
.agreement-loading {
  color: #999;
  text-align: center;
}
.agreement-file-button {
  margin: 18px 0 0;
  background: #ff6b35;
  color: #fff;
  font-size: 14px;
}
@media (max-width: 430px) {
  .phone-frame {
    width: 100%;
    height: 100vh;
  }
}
</style>
