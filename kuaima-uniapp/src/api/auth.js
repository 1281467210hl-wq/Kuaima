import { request } from "@/api/http";

export function wechatLogin(data) {
  return request({ url: "/auth/wechat/login", method: "POST", data, skipUserIdHeader: true });
}

/** App / H5 短信登录：发送登录验证码（无需登录）。 */
export function sendSmsLoginCode(phone) {
  return request({
    url: `/auth/sms/login/send?phone=${encodeURIComponent(phone)}`,
    method: "POST",
    skipUserIdHeader: true,
  });
}

/** App / H5 短信登录：data = { phone, code, role: BOSS | USER }，返回结构与微信登录一致。 */
export function smsLogin(data) {
  return request({ url: "/auth/sms/login", method: "POST", data, skipUserIdHeader: true });
}

export function getCurrentUser() {
  return request({ url: "/auth/me" });
}

export function getEnterpriseContexts() {
  return request({ url: "/auth/enterprise-contexts", skipUserIdHeader: true });
}

export function switchEnterpriseContext(enterpriseId) {
  return request({
    url: "/auth/enterprise-context/switch",
    method: "POST",
    data: { enterpriseId },
    skipUserIdHeader: true,
  });
}

export function saveEnterpriseContext(data = {}) {
  if (data.accessToken) uni.setStorageSync("token", data.accessToken);
  if (data.enterpriseId !== undefined) uni.setStorageSync("enterpriseId", String(data.enterpriseId));
  if (data.enterpriseName) uni.setStorageSync("enterpriseName", data.enterpriseName);
  if (data.memberRole) uni.setStorageSync("enterpriseMemberRole", data.memberRole);
  if (Array.isArray(data.permissions)) uni.setStorageSync("enterprisePermissions", data.permissions);
  uni.setStorageSync("role", data.role || "BOSS");
  uni.setStorageSync("currentRole", "boss");
}

export function sendSmsCode(phone) {
  const value = String(phone || "").trim();
  if (!/^1[3-9]\d{9}$/.test(value)) {
    throw new Error("请输入正确的手机号");
  }
  return request({
    url: `/auth/sms/send?phone=${encodeURIComponent(value)}`,
    method: "GET",
    skipUserIdHeader: true,
  });
}

export const LOGIN_STORAGE_KEYS = [
  "token",
  "userId",
  "userInfo",
  "role",
  "currentRole",
  "userPhone",
  "certStatus",
  "workerCertStatus",
  "bossCertStatus",
  "enterpriseId",
  "enterpriseName",
  "enterpriseMemberRole",
  "enterprisePermissions",
];

export function clearLoginStorage() {
  LOGIN_STORAGE_KEYS.forEach((key) => uni.removeStorageSync(key));
}

function redirectToLogin(roleHint) {
  const url = roleHint ? `/pages/login/login?role=${encodeURIComponent(roleHint)}` : "/pages/login/login";
  return new Promise((resolve) => {
    uni.reLaunch({ url, complete: resolve });
  });
}

export async function handleTokenInvalid(options = {}) {
  const { showToast = true, toastTitle = "登录已失效，请重新登录", role = "", clear = true } = options;
  if (clear) clearLoginStorage();
  if (showToast) uni.showToast({ title: toastTitle, icon: "none" });
  await new Promise((resolve) => setTimeout(resolve, 260));
  return redirectToLogin(role);
}

export async function logout() {
  let requestSucceeded = false;
  try {
    await request({
      url: "/auth/logout",
      method: "POST",
      skipUserIdHeader: true,
    });
    requestSucceeded = true;
  } catch (error) {
    console.warn("退出登录接口调用失败，继续清理本地登录态", error);
  } finally {
    clearLoginStorage();
    await redirectToLogin("");
    if (requestSucceeded) {
      try {
        uni.showToast({ title: "已退出登录", icon: "success" });
      } catch (_) {}
    }
  }
}
