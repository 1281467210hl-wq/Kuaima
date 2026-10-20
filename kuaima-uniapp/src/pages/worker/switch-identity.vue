<template>
  <view class="page">
    <AppNavBar title="切换身份" :show-back="true" />
    <view class="content">
      <view class="avatar-section">
        <view class="avatar">
          <image
            :src="current === 'boss' ? defaultBossAvatar : defaultWorkerAvatar"
            mode="aspectFill"
          />
        </view>
      </view>
      <text class="identity"
        >你当前身份是{{ current === "worker" ? "零工" : "老板" }}</text
      >
      <view class="buttons">
        <button class="primary" :disabled="switching" @click="switchRole">
          {{ switching ? "切换中…" : `切换为${current === "worker" ? "老板" : "零工"}身份` }}
        </button>
        <button class="secondary" @click="cancel">暂不切换</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import AppNavBar from "@/components/AppNavBar.vue";
import { wechatLogin } from "@/api/auth";
import { switchRole as switchRoleApi } from "@/api/backend";
import { USE_MOCK } from "@/api/http";
import defaultBossAvatar from "/static/avatars/default-boss-avatar.png";
import defaultWorkerAvatar from "/static/avatars/default-worker-avatar.png";

function normalizeCurrentRole(value) {
  const role = String(value || "").trim().toUpperCase();
  return role === "BOSS" ? "boss" : "worker";
}

const storedRole =
  uni.getStorageSync("currentRole") || uni.getStorageSync("role");
const current = ref(normalizeCurrentRole(storedRole));
const switching = ref(false);

onLoad((options = {}) => {
  const routeRole = options.role || options.currentRole;
  current.value = normalizeCurrentRole(routeRole || storedRole);
});

function switchRole() {
  const target = current.value === "worker" ? "boss" : "worker";
  const role = target === "boss" ? "BOSS" : "USER";
  if (switching.value) return;
  switching.value = true;

  if (USE_MOCK) {
    finishSwitch({
      role,
      userId: uni.getStorageSync("userId") || "2001",
      accessToken: uni.getStorageSync("token"),
    });
    return;
  }

  // 身份切换必须重新获取微信 code，并由登录接口签发目标角色的 JWT。
  // #ifdef MP-WEIXIN
  uni.login({
    provider: "weixin",
    success: async ({ code }) => {
      try {
        const result = await wechatLogin({ code, role });
        finishSwitch(result);
      } catch (error) {
        switching.value = false;
        uni.showToast({
          title: error.message || "身份切换失败",
          icon: "none",
        });
      }
    },
    fail: () => {
      switching.value = false;
      uni.showToast({ title: "无法获取微信登录凭证", icon: "none" });
    },
  });
  return;
  // #endif

  // App / H5 没有微信 code：用当前登录态调用 switch-role 签发目标身份的 JWT（返回结构与登录一致）
  // #ifndef MP-WEIXIN
  switchRoleApi(role)
    .then((result) => finishSwitch(result))
    .catch((error) => {
      switching.value = false;
      uni.showToast({ title: error.message || "身份切换失败", icon: "none" });
    });
  // #endif
}

function finishSwitch(result = {}) {
  const targetRole = result.role || (current.value === "worker" ? "BOSS" : "USER");
  if (result.accessToken) uni.setStorageSync("token", result.accessToken);
  if (result.userId !== undefined) {
    uni.setStorageSync("userId", String(result.userId));
  }
  uni.setStorageSync("role", targetRole);
  uni.setStorageSync("currentRole", targetRole === "BOSS" ? "boss" : "worker");
  uni.showToast({
    // 微信小程序 toast 标题长度有限，控制在短文案内避免被截断。
    title: targetRole === "BOSS" ? "已切换为老板" : "已切换为零工",
    icon: "success",
  });
  setTimeout(
    () =>
      uni.reLaunch({
        url: targetRole === "BOSS" ? "/pages/boss/home" : "/pages/worker/home",
      }),
    500,
  );
}

function cancel() {
  uni.redirectTo({
    url:
      current.value === "worker"
        ? "/pages/worker/profile"
        : "/pages/boss/profile",
  });
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #fff;
}
.content {
  min-height: calc(100vh - 176rpx);
  background: #fff;
}
.avatar-section {
  display: flex;
  justify-content: center;
  padding: 120rpx 0 60rpx;
}
.avatar {
  width: 240rpx;
  height: 240rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffb347, #ff6b35);
  border: 6rpx solid rgba(255, 255, 255, 0.5);
}
.avatar image {
  width: 100%;
  height: 100%;
  display: block;
  border-radius: 50%;
}
.identity {
  display: block;
  margin-bottom: 120rpx;
  text-align: center;
  color: #333;
  font-size: 44rpx;
  font-weight: 600;
}
.buttons {
  padding: 0 48rpx;
}
.primary,
.secondary {
  width: 100%;
  height: 112rpx;
  line-height: 112rpx;
  margin: 0 0 32rpx;
  padding: 0;
  border-radius: 56rpx;
  font-size: 32rpx;
}
.primary {
  border: 0;
  background: linear-gradient(135deg, #ffd700, #ffa500);
  color: #fff;
  font-weight: 600;
}
.secondary {
  border: 2rpx solid #eee;
  background: #fff;
  color: #666;
  font-weight: 500;
}
.primary::after,
.secondary::after {
  border: 0;
}
</style>
