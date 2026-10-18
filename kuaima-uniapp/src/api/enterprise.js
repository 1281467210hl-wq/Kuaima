import { request } from "@/api/http";

function query(params) {
  return Object.entries(params || {})
    .filter(([, value]) => value !== undefined && value !== null && value !== "")
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join("&");
}

export const MEMBER_ROLE_TEXT = { OWNER: "超级管理员", ADMIN: "管理员", STAFF: "成员" };
export const APPLY_STATUS_TEXT = { PENDING: "待处理", AGREED: "已同意", REFUSED: "已拒绝" };
export const INVITE_STATUS_TEXT = { PENDING: "待接受", ACCEPTED: "已接受", REFUSED: "已拒绝", EXPIRED: "已失效" };

/** 企业成员列表 */
export function getMembers(keyword) {
  return request({ url: `/boss/enterprise/members?${query({ keyword })}` });
}

/** 成员详情 */
export function getMemberDetail(memberId) {
  return request({ url: `/boss/enterprise/members/${memberId}` });
}

/** 编辑成员角色 */
export function updateMemberRole(memberId, role) {
  return request({ url: `/boss/enterprise/members/${memberId}/role`, method: "PUT", data: { role } });
}

/** 移出企业 */
export function removeMember(memberId) {
  return request({ url: `/boss/enterprise/members/${memberId}`, method: "DELETE" });
}

/** 入企申请列表 */
export function getApplies(status, page = 0, size = 20) {
  return request({ url: `/boss/enterprise/applies?${query({ status, page, size })}` });
}

/** 待处理申请数量 */
export function getPendingApplyCount() {
  return request({ url: "/boss/enterprise/applies/pending-count" });
}

/** 同意申请 */
export function agreeApply(applyId) {
  return request({ url: `/boss/enterprise/applies/${applyId}/agree`, method: "POST" });
}

/** 拒绝申请 */
export function refuseApply(applyId) {
  return request({ url: `/boss/enterprise/applies/${applyId}/refuse`, method: "POST" });
}

/** 手机号邀请新成员 */
export function createInvite(phone, role) {
  return request({ url: "/boss/enterprise/invites", method: "POST", data: { phone, role } });
}

/** 邀请记录 */
export function getInvites(page = 0, size = 20) {
  return request({ url: `/boss/enterprise/invites?${query({ page, size })}` });
}

/** 发送邀请提醒 */
export function remindInvite(inviteId) {
  return request({ url: `/boss/enterprise/invites/${inviteId}/remind`, method: "POST" });
}

/** 邀请二维码信息 */
export function getInviteQr() {
  return request({ url: "/boss/enterprise/invite-qr" });
}

// -------------------- 被邀请人视角：我收到的企业邀请 --------------------

/** 我的待处理企业邀请（按当前登录手机号匹配） */
export function getMyInvites() {
  return request({ url: "/boss/enterprise/invites/my" });
}

/** 同意企业邀请（同意后成为企业成员） */
export function acceptInvite(inviteId) {
  return request({ url: `/boss/enterprise/invites/${inviteId}/accept`, method: "POST" });
}

/** 拒绝企业邀请 */
export function refuseInvite(inviteId) {
  return request({ url: `/boss/enterprise/invites/${inviteId}/refuse`, method: "POST" });
}

/** 退出企业信息 */
export function getExitInfo() {
  return request({ url: "/boss/enterprise/exit-info" });
}

/** 退出当前企业 */
export function exitEnterprise() {
  return request({ url: "/boss/enterprise/exit", method: "POST" });
}

// -------------------- 公开接口（无需登录）：邀请码入企申请 --------------------

/** 根据邀请码查询邀请信息与企业名称 */
export function getPublicInviteInfo(code) {
  return request({ url: `/public/enterprise/invite?code=${encodeURIComponent(code)}` });
}

/** 提交入企申请：{ code, name, phone, note } */
export function submitEnterpriseApply(data = {}) {
  return request({ url: "/public/enterprise/apply", method: "POST", data });
}
