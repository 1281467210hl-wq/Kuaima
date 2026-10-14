import { request } from "@/api/http";

function query(params) {
  return Object.entries(params || {})
    .filter(([, value]) => value !== undefined && value !== null && value !== "")
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join("&");
}

/**
 * 候选人（岗位投递）状态字典 —— 取自原型 talent-list.html / boss-applicants.html。
 * tab.key 与原型 data-tab / data-type 一致；status 为后端 position_application.status 取值。
 * 后端 6 态（含 PREINTERVIEW）已全部支持：BossApplicationController
 * GET /boss/applications、GET /boss/applications/{id}、PUT /{id}/status、POST /{id}/interview。
 */
export const CANDIDATE_TABS = [
  { key: "all", label: "全部", status: "" },
  { key: "pending", label: "待筛选", status: "PENDING" },
  { key: "preinterview", label: "待面试", status: "PREINTERVIEW" },
  { key: "interview", label: "面试中", status: "INTERVIEWING" },
  { key: "hired", label: "已录用", status: "HIRED" },
  { key: "rejected", label: "已拒绝", status: "REJECTED" },
];

export const CANDIDATE_STATUS_TEXT = {
  PENDING: "待筛选",
  PREINTERVIEW: "待面试",
  INTERVIEWING: "面试中",
  HIRED: "已录用",
  REJECTED: "已拒绝",
};

/** 状态标签配色：原型 cand-status / wb-tag 类名 */
export const CANDIDATE_STATUS_CLS = {
  PENDING: "orange",
  PREINTERVIEW: "blue",
  INTERVIEWING: "cyan",
  HIRED: "green",
  REJECTED: "gray",
};

/** 状态 -> 卡片操作按钮（原型 statusActions 表） */
export const CANDIDATE_ACTIONS = {
  PENDING: [
    { act: "reject", label: "拒绝" },
    { act: "invite", label: "邀请面试" },
  ],
  PREINTERVIEW: [{ act: "cancelInterview", label: "取消面试" }],
  INTERVIEWING: [{ act: "hire", label: "录用" }],
  HIRED: [{ act: "cancelHire", label: "取消录用" }],
  REJECTED: [{ act: "reinvite", label: "重新邀请面试" }],
};

/** 操作 -> 目标状态（原型 doAction 的状态流转；invite/reinvite 不落状态，跳面试安排页 interview.vue 走 POST /interview） */
export const CANDIDATE_ACTION_STATUS = {
  reject: "REJECTED",
  cancelInterview: "PENDING",
  hire: "HIRED",
  cancelHire: "INTERVIEWING",
  invite: "PREINTERVIEW",
  reinvite: "PREINTERVIEW",
};

/** 状态归一：兼容后端大写枚举、原型小写 key 与中文文案 */
export function normalizeCandidateStatus(value) {
  if (!value) return "";
  const raw = String(value).trim();
  const upper = raw.toUpperCase();
  if (CANDIDATE_STATUS_TEXT[upper]) return upper;
  const tab = CANDIDATE_TABS.find((item) => item.key === raw.toLowerCase());
  if (tab) return tab.status;
  const byText = Object.entries(CANDIDATE_STATUS_TEXT).find(([, text]) => text === raw);
  return byText ? byText[0] : upper;
}

export function getCandidateActions(status) {
  return CANDIDATE_ACTIONS[normalizeCandidateStatus(status)] || [];
}

/**
 * 候选人（岗位投递）列表：status / keyword / positionId / page / size。
 * 后端：GET /boss/applications（BossApplicationController，仅当前登录老板的投递）。
 */
export function listBossApplications(params = {}) {
  return request({
    url: `/boss/applications?${query({ page: 0, size: 20, ...params })}`,
    skipUserIdHeader: true,
  });
}

/**
 * 候选人（面试安排页）详情：GET /boss/applications/{id}。
 * 返回行/详情字段：id、name、job/positionName、status、applyTime、interviewTime、
 * interviewLocation、interviewer、remark 及简历信息。
 */
export function getBossApplication(id) {
  return request({
    url: `/boss/applications/${id}`,
    skipUserIdHeader: true,
  });
}

/**
 * 安排面试：POST /boss/applications/{id}/interview。
 * body { interviewTime 必填 'yyyy-MM-dd HH:mm[:ss]', location?, interviewer? }；
 * 保存后状态置 INTERVIEWING，返回即候选人详情。
 */
export function createBossInterview(id, data = {}) {
  return request({
    url: `/boss/applications/${id}/interview`,
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

/**
 * 更新候选人状态：PUT /boss/applications/{id}/status { status, interviewTime?, remark? }
 */
export function updateBossApplicationStatus(id, data = {}) {
  return request({
    url: `/boss/applications/${id}/status`,
    method: "PUT",
    data,
    skipUserIdHeader: true,
  });
}
