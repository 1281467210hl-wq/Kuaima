import { request } from "@/api/http";

function query(params) {
  return Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== "")
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join("&");
}

const AVATAR_PALETTE = [
  "linear-gradient(135deg,#FFB84D,#F09A3E)",
  "linear-gradient(135deg,#FF7743,#FF5C33)",
  "linear-gradient(135deg,#FF8C5A,#FF6B35)",
  "linear-gradient(135deg,#10B981,#34D399)",
  "linear-gradient(135deg,#2563EB,#3B82F6)",
  "linear-gradient(135deg,#8B5CF6,#A78BFA)",
  "linear-gradient(135deg,#F59E0B,#FBBF24)",
  "linear-gradient(135deg,#EF4444,#F87171)",
];

/** 根据种子字符串稳定地挑选一个头像渐变色。 */
export function avatarColor(seed = "") {
  let h = 0;
  const s = String(seed);
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  return AVATAR_PALETTE[h % AVATAR_PALETTE.length];
}

const WEEKDAYS = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];

/** 将 yyyy-MM-dd 转成「2026年9月8日」。 */
export function formatCnDate(iso) {
  if (!iso) return "";
  const m = String(iso).match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
  if (!m) return iso;
  return `${m[1]}年${Number(m[2])}月${Number(m[3])}日`;
}

/** 将 yyyy-MM-dd 转成「9月8日」。 */
export function formatShortDate(iso) {
  if (!iso) return "";
  const m = String(iso).match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
  if (!m) return iso;
  return `${Number(m[2])}月${Number(m[3])}日`;
}

/** 返回 yyyy-MM-dd 对应的中文星期。 */
export function weekdayOf(iso) {
  if (!iso) return "";
  const m = String(iso).match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
  if (!m) return "";
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  return WEEKDAYS[d.getDay()];
}

export const PROJECT_STATUS_TEXT = { active: "进行中", archived: "已归档", deleted: "已删除" };
export const MEMBER_STATUS_TEXT = { active: "在职", temp: "临时", left: "已离职" };
export const ATTEND_STATUS_TEXT = { on: "出勤", late: "迟到", absent: "缺卡", leave: "请假" };
export const ONBOARD_STATUS_TEXT = { pending: "审核中", passed: "已通过", rejected: "已拒绝" };
export const ONSITE_ROLE_TEXT = { leader: "项目负责人", assistant: "项目助理" };

// -------------------- 项目 --------------------
export function listProjects(params = {}) {
  return request({ url: `/boss/projects?${query({ page: 0, size: 50, ...params })}` });
}

export function getProjectOverview() {
  return request({ url: "/boss/projects/stats/overview" });
}

export function getProject(id) {
  return request({ url: `/boss/projects/${id}` });
}

export function createProject(data = {}) {
  return request({ url: "/boss/projects", method: "POST", data });
}

export function updateProject(id, data = {}) {
  return request({ url: `/boss/projects/${id}`, method: "PUT", data });
}

export function archiveProject(id) {
  return request({ url: `/boss/projects/${id}/archive`, method: "POST" });
}

export function deleteProject(id) {
  return request({ url: `/boss/projects/${id}`, method: "DELETE" });
}

// -------------------- 项目成员 --------------------
export function listMembers(projectId, status = "all") {
  return request({ url: `/boss/projects/${projectId}/members?${query({ status })}` });
}

export function getMemberStats(projectId) {
  return request({ url: `/boss/projects/${projectId}/members/stats` });
}

export function addMember(projectId, data = {}) {
  return request({ url: `/boss/projects/${projectId}/members`, method: "POST", data });
}

export function updateMemberStatus(projectId, memberId, status) {
  return request({
    url: `/boss/projects/${projectId}/members/${memberId}?${query({ status })}`,
    method: "PUT",
  });
}

export function removeMember(projectId, memberId) {
  return request({ url: `/boss/projects/${projectId}/members/${memberId}`, method: "DELETE" });
}

// -------------------- 考勤打卡 --------------------
export function listAttendance(projectId, date) {
  return request({ url: `/boss/projects/${projectId}/attendance?${query({ date })}` });
}

export function getAttendanceStats(projectId, date) {
  return request({ url: `/boss/projects/${projectId}/attendance/stats?${query({ date })}` });
}

export function clock(projectId, data = {}) {
  return request({ url: `/boss/projects/${projectId}/attendance/clock?${query(data)}`, method: "POST" });
}

export function listCheckin(projectId, start, end) {
  return request({ url: `/boss/projects/${projectId}/checkin?${query({ start, end })}` });
}

// -------------------- 签到/签退码（老板当日两码，后端 BossAttendanceCodeController） --------------------
export function getAttendanceCodes() {
  return request({ url: "/boss/attendance-codes" });
}

export function refreshWorkAttendanceCode() {
  return request({ url: "/boss/attendance-codes/work/refresh", method: "POST" });
}

export function refreshLeaveAttendanceCode() {
  return request({ url: "/boss/attendance-codes/leave/refresh", method: "POST" });
}

// -------------------- 入职记录 --------------------
export function listOnboard(projectId, status = "all") {
  return request({ url: `/boss/projects/${projectId}/onboard?${query({ status })}` });
}

export function getOnboardStats(projectId) {
  return request({ url: `/boss/projects/${projectId}/onboard/stats` });
}

export function passOnboard(projectId, applyId) {
  return request({ url: `/boss/projects/${projectId}/onboard/${applyId}/pass`, method: "POST" });
}

export function rejectOnboard(projectId, applyId) {
  return request({ url: `/boss/projects/${projectId}/onboard/${applyId}/reject`, method: "POST" });
}

// -------------------- 驻场管理 --------------------
export function listOnsite(projectId) {
  return request({ url: `/boss/projects/${projectId}/onsite` });
}

export function addOnsite(projectId, data = {}) {
  return request({ url: `/boss/projects/${projectId}/onsite`, method: "POST", data });
}

export function removeOnsite(projectId, onsiteId) {
  return request({ url: `/boss/projects/${projectId}/onsite/${onsiteId}`, method: "DELETE" });
}
