import { request } from "@/api/http";

function query(params) {
  return Object.entries(params || {})
    .filter(([, value]) => value !== undefined && value !== null && value !== "")
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join("&");
}

/** 橱窗列表（当前老板）：GET /boss/live-showcases。 */
export function listLiveShowcases(params = {}) {
  return request({
    url: `/boss/live-showcases?${query({ page: 0, size: 50, ...params })}`,
    skipUserIdHeader: true,
  });
}

/** 新建橱窗：POST /boss/live-showcases { title, anchorName, douyinAccount }。 */
export function createLiveShowcase(data = {}) {
  return request({
    url: "/boss/live-showcases",
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

/** 橱窗状态流转：PUT /boss/live-showcases/{id}/status { status }。 */
export function updateLiveShowcaseStatus(id, status) {
  return request({
    url: `/boss/live-showcases/${id}/status`,
    method: "PUT",
    data: { status },
    skipUserIdHeader: true,
  });
}

/** 删除橱窗：DELETE /boss/live-showcases/{id}。 */
export function deleteLiveShowcase(id) {
  return request({
    url: `/boss/live-showcases/${id}`,
    method: "DELETE",
    skipUserIdHeader: true,
  });
}

/** 已绑定抖音账号列表：GET /boss/douyin-accounts。 */
export function listDouyinAccounts() {
  return request({ url: "/boss/douyin-accounts", skipUserIdHeader: true });
}

/** 绑定新抖音账号：POST /boss/douyin-accounts { name, douyinId, anchorName }。 */
export function bindDouyinAccount(data = {}) {
  return request({
    url: "/boss/douyin-accounts",
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}
