let BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api";
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";
const ENABLE_ORDER_REQUEST_LOG =
  import.meta.env.DEV || import.meta.env.VITE_ENABLE_API_LOG === "true";
const ENABLE_AUTH_TOKEN_LOG =
  import.meta.env.VITE_ENABLE_AUTH_TOKEN_LOG === "true";

// H5 开发环境通过 Vite 代理访问 /api；微信小程序和 App 没有该代理，相对地址会请求失败，必须用完整后端地址。
// #ifdef MP-WEIXIN || APP-PLUS
// 地址由 .env.development/.env.test/.env.production 统一控制。
BASE_URL = import.meta.env.VITE_MP_API_BASE_URL || "http://127.0.0.1:8080";
// #endif

function formatError(error, fallback = "请求失败") {
  if (error instanceof Error) return error;
  if (typeof error === "string") return new Error(error);
  if (error && typeof error === "object") {
    const message = error.message || error.errMsg || error.msg || error.error;
    if (message) return new Error(String(message));
    try {
      return new Error(JSON.stringify(error));
    } catch (_) {
      return new Error(fallback);
    }
  }
  return new Error(fallback);
}

function mockResponse(url, method = "GET", data) {
  if (method !== "GET") return { success: true, ...data };
  if (url.includes("/worker/jobs")) {
    return null;
  }
  if (url === "/worker/profile/overview")
    return {
      level: 1,
      creditScore: 30,
      completionRate: 70,
      cancellationRate: 10,
      noShowRate: 10,
      earlyLeaveRate: 10,
      totalIncome: 16000,
      completedOrders: 7,
      points: 9900,
      rewardAmount: 0,
    };
  if (url.includes("/worker/profile"))
    return { name: "晴时见禾", nickname: "晴时见禾", availableAmount: "0.00" };
  if (url === "/worker/wallet" || url.startsWith("/worker/wallet?"))
    return {
      available: "0.00",
      availableBalance: "0.00",
      totalIncome: "0.00",
      pendingAmount: "0.00",
      records: [],
    };
  // 返回 null 让页面保留自身的演示数据，避免无后端时首屏变成空列表。
  if (url.includes("withdraw-records")) return null;
  if (url.includes("/worker/orders")) return null;
  if (url.includes("/worker/settlements")) return null;
  if (url.includes("/worker/insurance")) return null;
  if (url.includes("/worker/notifications")) return null;
  return null;
}

export function request({
  url,
  method = "GET",
  data,
  header = {},
  skipUserIdHeader = false,
  skipMock = false,
  rawResponse = false,
}) {
  if (USE_MOCK && !skipMock)
    return Promise.resolve(mockResponse(url, method, data));
  const token = uni.getStorageSync("token");
  const userId = uni.getStorageSync("userId") || "2001";
  const realUrl = resolveBackendUrl(url, userId, data);
  const shouldLogOrderRequest = ENABLE_ORDER_REQUEST_LOG && isOrderRequest(url);
  if (shouldLogOrderRequest) {
    console.info("[订单接口请求]", {
      method,
      url: realUrl,
      data: sanitizeLogData(data),
    });
    if (ENABLE_AUTH_TOKEN_LOG) {
      console.info("[订单接口Token]", token || "[未登录]");
    }
  }
  return new Promise((resolve, reject) => {
    // 防御：路径中出现 undefined/null 段（如 /projects/undefined）说明上游漏传参数，
    // 直接在前端拦截，避免打到后端触发 Long 转换 500。
    if (/\/(undefined|null)(\/|\?|$)/.test(realUrl)) {
      const error = new Error(`请求地址缺少必要参数：${realUrl}`);
      error.statusCode = 400;
      console.error("[请求参数缺失]", { method, url: realUrl });
      reject(error);
      return;
    }
    uni.request({
      url: `${BASE_URL}${realUrl}`,
      method,
      data,
      timeout: 8000,
      header: {
        ...header,
        Authorization: token ? `Bearer ${token}` : "",
        ...(skipUserIdHeader ? {} : { "X-User-Id": userId }),
      },
      success(response) {
        const payload = response.data;
        if (shouldLogOrderRequest) {
          console.info("[订单接口响应]", {
            method,
            url: realUrl,
            statusCode: response.statusCode,
            code: payload?.code,
            message: payload?.message || payload?.msg,
          });
        }
        if (
          response.statusCode >= 200 &&
          response.statusCode < 300 &&
          (!payload?.code ||
            payload.code === 200 ||
            payload.code === "200" ||
            payload.code === 0 ||
            payload.code === "0")
        ) {
          resolve(rawResponse ? payload : (payload?.data ?? payload));
          return;
        }
        const errorMessage = payload?.message || payload?.msg || payload?.error;
        const error = formatError(
          payload,
          errorMessage || `请求失败（${response.statusCode}）`,
        );
        error.statusCode = response.statusCode;
        error.payload = payload;
        error.code = payload?.code;
        reject(error);
      },
      fail(error) {
        if (shouldLogOrderRequest) {
          console.warn("[订单接口失败]", {
            method,
            url: realUrl,
            error: error?.errMsg || error?.message || error,
          });
        }
        reject(formatError(error, "网络请求失败"));
      },
    });
  });
}

/** 跨端上传文件，沿用 request 的鉴权和后端地址配置。 */
export function uploadFile({ url, filePath, name = "file", formData = {}, header = {}, skipUserIdHeader = false }) {
  const token = uni.getStorageSync("token");
  const userId = uni.getStorageSync("userId") || "2001";
  const realUrl = resolveBackendUrl(url, userId, formData);
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}${realUrl}`,
      filePath,
      name,
      formData,
      header: {
        ...header,
        Authorization: token ? `Bearer ${token}` : "",
        ...(skipUserIdHeader ? {} : { "X-User-Id": userId }),
      },
      success(response) {
        let payload = response.data;
        try { payload = typeof payload === "string" ? JSON.parse(payload) : payload; } catch (_) {}
        if (response.statusCode >= 200 && response.statusCode < 300 && (!payload?.code || [0, 200, "0", "200"].includes(payload.code))) {
          resolve(payload?.data ?? payload);
          return;
        }
        reject(formatError(payload, `上传失败（${response.statusCode}）`));
      },
      fail(error) { reject(formatError(error, "文件上传失败")); },
    });
  });
}

function isOrderRequest(url = "") {
  return /^\/(?:boss\/order(?:\/|\?|$)|jobs(?:\/|\?|$)|worker\/orders(?:\/|\?|$))/.test(url);
}

function sanitizeLogData(data) {
  if (!data || typeof data !== "object") return data;
  const sensitiveKeys = new Set(["token", "authorization", "password", "openid", "code"]);
  if (Array.isArray(data)) return data.map((item) => sanitizeLogData(item));
  return Object.fromEntries(
    Object.entries(data).map(([key, value]) => [
      key,
      sensitiveKeys.has(key.toLowerCase()) ? "[已隐藏]" : sanitizeLogData(value),
    ]),
  );
}

function resolveBackendUrl(url, userId, data) {
  if (url.startsWith("/worker/jobs/"))
    return url.replace("/worker/jobs/", "/jobs/");
  if (url === "/worker/jobs" || url.startsWith("/worker/jobs?")) {
    const query = url.includes("?")
      ? url.substring(url.indexOf("?"))
      : "?page=0&size=20";
    return `/jobs${query.replace("pageNo", "page").replace("pageSize", "size")}`;
  }
  if (url.startsWith("/worker/orders/apply/")) {
    const orderId = url.split("/").pop();
    return `/jobs/${orderId}/apply`;
  }
  if (
    url === "/worker/notifications" ||
    url.startsWith("/worker/notifications?")
  )
    return `/message/list?userId=${encodeURIComponent(userId)}&page=0&size=20`;
  if (url === "/worker/notification/unread")
    return `/message/unread?userId=${encodeURIComponent(userId)}`;
  // 零工认证状态：后端无 /worker/... Controller，重写到 /user/{userId}（User 实体含 certStatus 字段）
  if (url === "/worker/certification/status") return `/user/${userId}`;
  return url;
}

function toQuery(params = {}) {
  return Object.entries(params)
    .filter(
      ([, value]) => value !== undefined && value !== null && value !== "",
    )
    .map(
      ([key, value]) =>
        `${encodeURIComponent(key)}=${encodeURIComponent(value)}`,
    )
    .join("&");
}
