import { request, uploadFile } from "@/api/http";
export { wechatLogin, getCurrentUser } from "@/api/auth";

function query(params) {
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

function normalizeId(value, fieldName) {
  const id = Number(value);
  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error(`${fieldName} 不合法`);
  }
  return id;
}

export async function getCertificationStatus(userId) {
  const id = userId || uni.getStorageSync("userId") || "2001";
  const profile = await request({ url: `/user/${encodeURIComponent(id)}` });
  let certStatus = profile?.certStatus || "未认证";

  // 当前后端审核接口只更新认证记录，可能未同步 User.certStatus；
  // 因此用户资料未显示通过时，再以最新的个人实名认证记录为准。
  if (!["已通过", "通过", "已认证"].includes(certStatus)) {
    try {
      const records = await request({
        url: `/user/${encodeURIComponent(id)}/certifications`,
      });
      const rows = Array.isArray(records)
        ? records
        : records?.records || records?.content || [];
      const realname = rows.find((item) =>
        ["REALNAME", "零工实名", "个人实名", "实名认证"].includes(
          item?.type || item?.certType,
        ),
      );
      if (realname?.status) certStatus = realname.status;
    } catch (_) {}
  }

  return { ...(profile || {}), certStatus };
}

export function submitCertification(data) {
  const userId = uni.getStorageSync("userId") || "2001";
  return request({
    url: `/user/${encodeURIComponent(userId)}/realname`,
    method: "POST",
    data,
  });
}

export function listOrders(params = {}) {
  return request({
    url: `/boss/order?${query({ page: 0, size: 20, ...params })}`,
    skipUserIdHeader: true,
  });
}

/** 老板端招工订单分页列表，保留后端的 page/total 等分页元数据。 */
export function listBossOrders(params = {}) {
  return request({
    url: `/boss/order?${query({ page: 0, size: 20, ...params })}`,
    rawResponse: true,
    skipUserIdHeader: true,
  });
}

export function getBossOrderStats(dateRange = "ALL") {
  return request({
    url: `/boss/order/stats?${query({ dateRange })}`,
    skipUserIdHeader: true,
  });
}

export function getOrder(id) {
  return request({ url: `/boss/order/${id}`, skipUserIdHeader: true });
}

/** 上传招工详情图片/视频；mediaType = image / video。 */
export function uploadRecruitmentMedia(filePath, mediaType = "image") {
  return uploadFile({
    url: `/boss/order/media/upload?mediaType=${encodeURIComponent(mediaType)}`,
    filePath,
    name: "file",
    skipUserIdHeader: true,
  });
}

/** 老板端全局招工默认设置，身份由当前 JWT 判断。 */
export function getBossRecruitSettings() {
  return request({ url: "/boss/recruit-settings", skipUserIdHeader: true });
}

export function updateBossRecruitSettings(data = {}) {
  return request({
    url: "/boss/recruit-settings",
    method: "PUT",
    data,
    skipUserIdHeader: true,
  });
}

export function createOrder(data) {
  return request({
    url: "/boss/order",
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

export function updateOrder(id, data) {
  return request({
    url: `/boss/order/${id}`,
    method: "PUT",
    data,
    skipUserIdHeader: true,
  });
}

export function deleteOrder(id) {
  return request({
    url: `/boss/order/${id}`,
    method: "DELETE",
    skipUserIdHeader: true,
  });
}

export function changeOrderStatus(id, target) {
  return request({
    url: `/boss/order/${id}/status?target=${encodeURIComponent(target)}`,
    method: "PUT",
    skipUserIdHeader: true,
  });
}

export function createBossOrderTemplate(orderId, data) {
  return request({
    url: `/boss/order/${normalizeId(orderId, "orderId")}/template`,
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

export function listBossOrderTemplates(params = {}) {
  return request({
    url: `/boss/order/templates?${query({ page: 0, size: 20, ...params })}`,
    skipUserIdHeader: true,
  });
}

export function getBossOrderTemplate(id) {
  return request({
    url: `/boss/order/templates/${normalizeId(id, "templateId")}`,
    skipUserIdHeader: true,
  });
}

export function updateBossOrderTemplate(id, data) {
  return request({
    url: `/boss/order/templates/${normalizeId(id, "templateId")}`,
    method: "PUT",
    data,
    skipUserIdHeader: true,
  });
}

export function deleteBossOrderTemplate(id) {
  return request({
    url: `/boss/order/templates/${normalizeId(id, "templateId")}`,
    method: "DELETE",
    skipUserIdHeader: true,
  });
}

export function getBossRecruitAccounts() {
  return request({ url: "/boss/recruit-accounts", skipUserIdHeader: true });
}

export function addBossRecruitAccount(data = {}) {
  return request({
    url: "/boss/recruit-accounts",
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

/** 已关联老板账号快速登录，后端需校验绑定关系并签发目标账号 JWT。 */
export function quickLoginBossRecruitAccount(accountId) {
  return request({
    url: `/boss/recruit-accounts/${normalizeId(accountId, "accountId")}/quick-login`,
    method: "POST",
    skipUserIdHeader: true,
  });
}

export function switchBossRecruitAccount(accountId) {
  return request({
    url: "/boss/recruit-accounts/current",
    method: "PUT",
    data: { accountId: normalizeId(accountId, "accountId") },
    skipUserIdHeader: true,
  });
}

export function remindOrderStart(id) {
  return request({ url: `/boss/order/${id}/remind-start`, method: "POST" });
}


/** 零工端公开岗位，只返回后端允许展示的“招工中”岗位。 */
export function listPublicJobs(params = {}) {
  return request({
    url: `/jobs?${query({ page: 0, size: 20, ...params })}`,
  });
}

export function reverseGeocode(latitude, longitude) {
  return request({
    url: `/location/reverse-geocode?${query({ latitude, longitude })}`,
  });
}

export function getPublicJob(orderId) {
  return request({ url: `/jobs/${normalizeId(orderId, "orderId")}` });
}

/** 零工身份由 JWT 获取，禁止再提交 userId。 */
/** 后台「系统设置 - 招工规则」：jobAudit / realNameRequired / minDailyWage / maxJobValidity。 */
export function getRecruitRules() {
  return request({ url: "/public/recruit-rules", skipUserIdHeader: true });
}

/** 是否要求零工实名后才能报名；规则读取失败时按要求处理，最终以后端校验为准。 */
export async function isRealnameRequiredForApply() {
  try {
    const rules = await getRecruitRules();
    return rules?.realNameRequired !== false;
  } catch (_) {
    return true;
  }
}

export function applyPublicJob(orderId, data = {}) {
  return request({
    url: `/jobs/${normalizeId(orderId, "orderId")}/apply`,
    method: "POST",
    data: {
      remark: String(data.remark || "").trim(),
      trial: data.trial === true,
    },
  });
}

export function listWorkerItems(userId) {
  return request({
    url: `/boss/user/items?userId=${encodeURIComponent(userId)}`,
  });
}

/** 零工端查询当前登录零工本人的报名条目（岗位维度）列表。 */
export function listWorkerApplications(userId) {
  const uid = userId || uni.getStorageSync("userId");
  return request({
    url: `/worker/items${uid ? `?userId=${encodeURIComponent(uid)}` : ""}`,
  });
}

/** 零工端取消自己的报名条目；reason 可为空字符串。 */
export function cancelWorkerItem(id, reason) {
  const r = String(reason || "").trim();
  return request({
    url: `/worker/items/${normalizeId(id, "itemId")}/cancel${r ? `?reason=${encodeURIComponent(r)}` : ""}`,
    method: "PUT",
  });
}

/** 当前登录零工的个人资料，身份由 JWT 获取。 */
export function getWorkerProfile() {
  return request({ url: "/worker/profile" });
}

/** 零工端“我的”页面统计概览，身份由当前 JWT 获取。 */
export function getWorkerProfileOverview() {
  return request({
    url: "/worker/profile/overview",
    skipUserIdHeader: true,
  });
}

export function updateWorkerProfile(data = {}) {
  return request({ url: "/worker/profile", method: "PUT", data });
}

/** 零工订单聚合列表，岗位信息由后端一次返回。 */
export function listWorkerOrders(params = {}) {
  return request({
    url: `/worker/orders?${query({ page: 0, size: 20, ...params })}`,
  });
}

/** 零工订单详情：返回报名状态及关联岗位快照，岗位停止招工后仍可查看。 */
export function getWorkerOrder(itemId) {
  return request({
    url: `/worker/orders/${normalizeId(itemId, "itemId")}`,
    skipUserIdHeader: true,
  });
}

/** 零工评价订单所属老板；同一报名条目重复提交时更新原评价。 */
export function saveWorkerBossReview(itemId, data) {
  return request({
    url: `/worker/items/${normalizeId(itemId, "itemId")}/boss-review`,
    method: "PUT",
    data,
  });
}

/** 老板评价已完成订单内的零工；同一报名条目只生成一次评价。 */
export function saveBossWorkerReview(orderId, data) {
  return request({
    url: `/boss/orders/${normalizeId(orderId, "orderId")}/worker-review`,
    method: "PUT",
    data,
  });
}

export function getBossWorkerReviews(orderId) {
  return request({
    url: `/boss/orders/${normalizeId(orderId, "orderId")}/worker-review`,
  });
}

/** 查询零工对当前企业/老板的评价。 */
export function getWorkerBossReviews(orderId) {
  return request({
    url: `/boss/orders/${normalizeId(orderId, "orderId")}/boss-review`,
  });
}

export function listOrderItems(orderId) {
  return request({
    url: `/boss/order/${orderId}/items`,
    skipUserIdHeader: true,
  });
}

export function hireOrderItem(id) {
  return request({ url: `/boss/item/${id}/hire`, method: "PUT" });
}

export function rejectOrderItem(id, reason) {
  return request({
    url: `/boss/item/${id}/reject?${query({ reason: String(reason || "").trim() })}`,
    method: "PUT",
  });
}

export function confirmOrderItemWork(id) {
  return request({ url: `/boss/item/${id}/work`, method: "PUT" });
}

export function finishOrderItem(id) {
  return request({ url: `/boss/item/${id}/finish`, method: "PUT" });
}

export function cancelOrderItem(id, reason) {
  return request({
    url: `/boss/item/${id}/cancel?${query({ reason })}`,
    method: "PUT",
  });
}

export function createSettlement(itemId, workDays) {
  return request({
    url: `/settle?${query({ itemId, workDays })}`,
    method: "POST",
  });
}

export function paySettlement(id) {
  return request({ url: `/settle/${id}/pay`, method: "POST" });
}

/** 创建老板结算微信支付单，金额和身份均由服务端根据结算单确认。 */
export function createBossSettlementWechatPayment(
  settlementIds = [],
  idempotencyKey = "",
  userCouponId = null,
  pointsToUse = 0,
) {
  return request({
    url: "/boss/settlements/payments/wechat",
    method: "POST",
    data: {
      settlementIds,
      ...(userCouponId ? { userCouponId } : {}),
      pointsToUse: Number(pointsToUse) || 0,
    },
    header: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {},
    skipUserIdHeader: true,
    skipMock: true,
  });
}

/** 查询老板结算付款预览，不创建支付订单。 */
export function previewBossSettlementPayment(
  settlementIds = [],
  userCouponId = null,
  pointsToUse = 0,
) {
  return request({
    url: `/boss/settlements/payments/preview?${query({
      settlementIds: settlementIds.join(","),
      userCouponId: userCouponId || undefined,
      pointsToUse: Number(pointsToUse) || 0,
    })}`,
    skipUserIdHeader: true,
    skipMock: true,
  });
}

/** 查询老板结算微信支付单状态。 */
export function getBossSettlementPayment(paymentNo) {
  return request({
    url: `/boss/settlements/payments/${encodeURIComponent(paymentNo)}`,
    skipUserIdHeader: true,
    skipMock: true,
  });
}

export function listOrderSettlements(orderId) {
  return request({ url: `/settle/order/${orderId}` });
}

export function listBossPendingSettlements() {
  return request({ url: "/boss/settlements/pending" });
}

export function createExpenseApplication(data, idempotencyKey) {
  return request({
    url: "/expenses/applications",
    method: "POST",
    data,
    header: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {},
    skipMock: true,
  });
}

export function listExpenseApplications(params = {}) {
  return request({
    url: `/expenses/applications?${query({ page: 0, size: 20, ...params })}`,
    skipMock: true,
  });
}

/** 老板端支出明细：购买积分、招工订单结算、账户充值。 */
export function getBossExpenseDetails() {
  return request({ url: "/boss/expense-details", skipUserIdHeader: true });
}

export function uploadExpenseFile(filePath) {
  return uploadFile({ url: "/files/upload", filePath, name: "file" }).then(result => ({
    ...result,
    // 群二维码落库只保存 objectKey；列表展示由后端动态生成签名 URL。
    url: result?.objectKey || result?.url,
  }));
}

/** 上传实名认证图片（身份证人像面 / 人脸照片），按端走 /{role}/realname/upload。 */
export function uploadRealnameImage(filePath, role = "worker") {
  return uploadFile({ url: `/${role}/realname/upload`, filePath, name: "file" });
}

/** 按端查询实名认证状态与照片：role = boss / worker。 */
export function getRealnameStatus(role = "boss") {
  return request({ url: `/${role}/realname/status` });
}

/** 按端提交人脸比对实名认证：role = boss / worker。 */
export function verifyRealname(role = "boss", data) {
  return request({ url: `/${role}/realname/verify`, method: "POST", data });
}

/** 老板端：查询实名认证状态与照片。 */
export function getBossRealnameStatus() {
  return getRealnameStatus("boss");
}

/** 老板端：提交人脸比对实名认证。 */
export function verifyBossRealname(data) {
  return verifyRealname("boss", data);
}

/** 零工端：查询实名认证状态与照片。 */
export function getWorkerRealnameStatus() {
  return getRealnameStatus("worker");
}

/** 零工端：提交人脸比对实名认证。 */
export function verifyWorkerRealname(data) {
  return verifyRealname("worker", data);
}

/** 上传企业认证营业执照，后端保存到 OSS enterprise/license 目录。 */
export function uploadEnterpriseLicense(filePath) {
  return uploadFile({
    url: "/boss/enterprise-cert/license-upload",
    filePath,
    name: "file",
    skipUserIdHeader: true,
  });
}

export function getWallet(userId) {
  return request({ url: `/wallet/${userId}` });
}

export function listWalletFlows(userId) {
  return request({ url: `/wallet/${userId}/flows` });
}

export function listWithdraws(userId) {
  return request({ url: `/wallet/${userId}/withdraws` });
}

export function applyWithdraw({ userId, amount, account, remark }) {
  return request({
    url: `/wallet/withdraw?${query({ userId, amount, account, remark })}`,
    method: "POST",
  });
}

/** 零工钱包微信提现，金额单位为元，身份由当前 JWT 判断。 */
export function withdrawWorkerWallet(data = {}, idempotencyKey = "") {
  return request({
    url: "/worker/wallet/withdraw",
    method: "POST",
    data: { amount: data.amount },
    header: { "Idempotency-Key": idempotencyKey },
    skipUserIdHeader: true,
    skipMock: true,
  });
}

export function listSettlements(userId) {
  return request({ url: `/settle/worker/${encodeURIComponent(userId)}` });
}

export function listMessages(userId, params = {}) {
  return request({
    url: `/message/list?${query({ userId, page: 0, size: 20, ...params })}`,
  });
}

export function getBossMessageSummary() {
  return request({ url: "/boss/message/summary" });
}

export function listBossMessageHistory(params = {}) {
  return request({
    url: `/boss/message/history?${query({ page: 0, size: 20, ...params })}`,
    rawResponse: true,
  });
}

/** 获取已发布公告列表（管理员人工创建） */
export function listNotices(params = {}) {
  return request({
    url: `/notices?${query({ page: 0, size: 20, ...params })}`,
  });
}

export function unreadMessages(userId, role) {
  return request({ url: `/message/unread?${query({ userId, role })}` });
}

export function readMessage(id, userId) {
  return request({
    url: `/message/${id}/read?userId=${encodeURIComponent(userId)}`,
    method: "PUT",
  });
}

export function readAllMessages(userId) {
  return request({
    url: `/message/readAll?userId=${encodeURIComponent(userId)}`,
    method: "PUT",
  });
}

export function getUser(id) {
  return request({ url: `/user/${id}` });
}

export function updateUser(id, data) {
  return request({ url: `/user/${id}`, method: "PUT", data });
}

export function submitRealname(id, data) {
  return request({ url: `/user/${id}/realname`, method: "POST", data });
}

export function listCertifications(id) {
  return request({ url: `/user/${id}/certifications` });
}

export function getCredit(id) {
  return request({ url: `/user/${id}/credit` });
}

export function listCreditFlows(id, params = {}) {
  return request({
    url: `/user/${id}/credit/flows?${query({ page: 0, size: 20, ...params })}`,
  });
}

export function switchRole(role) {
  return request({
    url: `/auth/switch-role?${query({ role })}`,
    method: "POST",
  });
}

/** 注销当前登录账号：后端按登录态识别账号，不再传 userId（旧接口可被用来注销他人账号）。 */
export function cancelAccount(reason) {
  return request({
    url: `/auth/cancel?${query({ reason })}`,
    method: "POST",
    skipUserIdHeader: true,
  });
}

export function submitEnterpriseCertification(data) {
  return request({ url: "/boss/enterprise-cert", method: "POST", data });
}

export function getBossProfile(userId) {
  return request({ url: `/boss/profile/${userId}` });
}

export function getBossProfileStats(userId) {
  return request({ url: `/boss/profile/${userId}/stats` });
}

export function getBossProfileAssets(userId) {
  return request({ url: `/boss/profile/${userId}/assets` });
}

export function getBossStats(userId) {
  return request({ url: `/boss/stats?${query({ userId })}` });
}

export function getBossHomeOverview(params = {}) {
  return request({
    url: `/boss/home/overview?${query(params)}`,
    skipUserIdHeader: true,
  });
}

export function getBossHomeSchedule(date, accountId) {
  return request({
    url: `/boss/home/schedule?${query({ date, accountId })}`,
    skipUserIdHeader: true,
  });
}

/** 当前老板今日开工码/早退码状态，身份由 JWT 获取。 */
export function getBossAttendanceCodes() {
  return request({ url: "/boss/attendance-codes", skipUserIdHeader: true, skipMock: true });
}

/** 老板项目管理接口，统一放在主 API 模块，避免微信分包加载独立 API 模块失败。 */
export function listBossProjects(params = {}) {
  return request({
    url: `/boss/projects?${query({ page: 0, size: 50, ...params })}`,
    skipUserIdHeader: true,
  });
}

export function getBossProjectOverview() {
  return request({ url: "/boss/projects/stats/overview", skipUserIdHeader: true });
}

export function createBossProject(data = {}, idempotencyKey) {
  return request({
    url: "/boss/projects",
    method: "POST",
    data,
    skipUserIdHeader: true,
    header: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {},
  });
}

export function formatBossProjectDate(iso) {
  if (!iso) return "";
  const match = String(iso).match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
  return match ? `${match[1]}年${Number(match[2])}月${Number(match[3])}日` : iso;
}

export function refreshBossWorkCode() {
  return request({
    url: "/boss/attendance-codes/work/refresh",
    method: "POST",
    skipUserIdHeader: true,
    skipMock: true,
  });
}

export function refreshBossLeaveCode() {
  return request({
    url: "/boss/attendance-codes/leave/refresh",
    method: "POST",
    skipUserIdHeader: true,
    skipMock: true,
  });
}

export function workerCheckIn(orderId, code) {
  return request({
    url: `/worker/orders/${normalizeId(orderId, "orderId")}/check-in`,
    method: "POST",
    data: { code: String(code || "").trim() },
    skipUserIdHeader: true,
    skipMock: true,
  });
}

export function workerEarlyLeave(orderId, code) {
  return request({
    url: `/worker/orders/${normalizeId(orderId, "orderId")}/early-leave`,
    method: "POST",
    data: { code: String(code || "").trim() },
    skipUserIdHeader: true,
    skipMock: true,
  });
}

export function saveOrderDraft(data) {
  return request({
    url: "/boss/order/draft",
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

export function listOrderDrafts() {
  return request({ url: "/boss/orders/drafts", skipUserIdHeader: true });
}

export function updateOrderDraft(id, data) {
  return request({
    url: `/boss/order/${id}/draft`,
    method: "PUT",
    data,
    skipUserIdHeader: true,
  });
}

export function listJobCategories() {
  return request({ url: "/boss/job-categories" });
}

export function listJobCategoryTree() {
  return request({ url: "/job-categories/tree" });
}

export function listHotJobCategories() {
  return request({ url: "/job-categories/hot" });
}

export function searchJobCategories(keyword, size = 20) {
  return request({
    url: `/job-categories/search?${query({ keyword, size })}`,
  });
}

export function createJobEnterpriseType(data = {}) {
  return request({
    url: "/boss/job-categories/enterprise-types",
    method: "POST",
    data,
  });
}

export function createJobCategory(data = {}) {
  return request({
    url: "/boss/job-categories/jobs",
    method: "POST",
    data,
  });
}

export function listBossAddresses(userId) {
  return request({ url: `/boss/addresses?${query({ userId })}` });
}
export function searchBossAddresses(userId, keyword) {
  return request({
    url: `/boss/addresses/search?${query({ userId, keyword })}`,
  });
}

export function createBossAddress(data) {
  return request({ url: "/boss/addresses", method: "POST", data });
}

export function deleteBossAddress(id) {
  return request({ url: `/boss/addresses/${id}`, method: "DELETE" });
}
export function updateBossAddress(id, data) {
  return request({
    url: `/boss/addresses/${encodeURIComponent(id)}`,
    method: "PUT",
    data,
  });
}
export function useBossAddress(id) {
  return request({
    url: `/boss/addresses/${encodeURIComponent(id)}/use`,
    method: "PUT",
  });
}

export function setDefaultBossAddress(id) {
  return request({ url: `/boss/addresses/${id}/default`, method: "PUT" });
}

/** 老板端招工地址，归属由当前 JWT 判断。 */
export function listBossRecruitAddresses() {
  return request({ url: "/boss/recruit-addresses", skipUserIdHeader: true });
}

export function createBossRecruitAddress(data = {}) {
  return request({
    url: "/boss/recruit-addresses",
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

export function updateBossRecruitAddress(id, data = {}) {
  return request({
    url: `/boss/recruit-addresses/${normalizeId(id, "addressId")}`,
    method: "PUT",
    data,
    skipUserIdHeader: true,
  });
}

export function deleteBossRecruitAddress(id) {
  return request({
    url: `/boss/recruit-addresses/${normalizeId(id, "addressId")}`,
    method: "DELETE",
    skipUserIdHeader: true,
  });
}

export function setDefaultBossRecruitAddress(id) {
  return request({
    url: `/boss/recruit-addresses/${normalizeId(id, "addressId")}/default`,
    method: "PUT",
    skipUserIdHeader: true,
  });
}

export function listBossContacts(userId) {
  return request({ url: `/boss/contacts?${query({ userId })}` });
}

export function createBossContact(data) {
  return request({ url: "/boss/contacts", method: "POST", data });
}

export function deleteBossContact(id) {
  return request({ url: `/boss/contacts/${id}`, method: "DELETE" });
}

export function setDefaultBossContact(id) {
  return request({ url: `/boss/contacts/${id}/default`, method: "PUT" });
}

export function searchTalents(params = {}) {
  return request({
    url: `/talent/search?${query({ page: 0, size: 20, ...params })}`,
  });
}

/** 老板端人才库，人才与老板身份均由后端 JWT/真实数据确定。 */
export function listBossTalents(params = {}) {
  return request({
    url: `/boss/talents?${query({ page: 0, size: 20, ...params })}`,
    rawResponse: true,
    skipUserIdHeader: true,
  });
}

export function toggleBossTalentFavorite(workerId, favorite) {
  return request({
    url: `/boss/talents/${normalizeId(workerId, "workerId")}/favorite`,
    method: "PUT",
    data: favorite === undefined ? {} : { favorite: Boolean(favorite) },
    skipUserIdHeader: true,
  });
}

export function inviteBossTalent(workerId, data = {}) {
  return request({
    url: `/boss/talents/${normalizeId(workerId, "workerId")}/invite`,
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

export function listFavoriteTalents(bossId) {
  return request({ url: `/talent/favorites?${query({ bossId })}` });
}

export function favoriteTalent(data) {
  return request({ url: "/talent/favorites", method: "POST", data });
}

export function unfavoriteTalent(id) {
  return request({ url: `/talent/favorites/${id}`, method: "DELETE" });
}

export function listTalentHistory() {
  // 后端从当前老板 JWT 识别归属，不再传 bossId，也不使用 X-User-Id 作为查询依据。
  return request({ url: "/talent/history", skipUserIdHeader: true });
}

export function inviteTalent(data) {
  return request({ url: "/talent/invite", method: "POST", data });
}

// -------------------- 转账记录模块 --------------------

/** 转账记录列表与统计：{ keyword, type, projectId, startDate, endDate, creator } */
export function listTransferRecords(params = {}) {
  return request({
    url: `/boss/transfers?${query(params)}`,
    skipUserIdHeader: true,
  });
}

/** 转账明细列表：{ type, startDate, endDate } */
export function listTransferDetails(params = {}) {
  return request({
    url: `/boss/transfers/details?${query(params)}`,
    skipUserIdHeader: true,
  });
}

/** 转账明细汇总：{ startDate, endDate } */
export function getTransferSummary(params = {}) {
  return request({
    url: `/boss/transfers/summary?${query(params)}`,
    skipUserIdHeader: true,
  });
}

/** 导出转账记录（按筛选条件） */
export function exportTransferRecords(data = {}) {
  return request({
    url: "/boss/transfers/export",
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

/** 导出转账明细 */
export function exportTransferDetails(data = {}) {
  return request({
    url: "/boss/transfers/details/export",
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

/** 导出转账汇总报告 */
export function exportTransferSummary(data = {}) {
  return request({
    url: "/boss/transfers/summary/export",
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

// -------------------- 余额查询模块 --------------------

/** 老板商户账户余额查询，身份由当前 JWT 判断 */
export function getBossBalance() {
  return request({ url: "/boss/balance", skipUserIdHeader: true });
}

/** 老板端创建商户余额充值订单。 */
export function createBossRechargeOrder({ amount, payMethod }) {
  return request({
    url: "/boss/balance/recharge/create",
    method: "POST",
    skipUserIdHeader: true,
    data: { amount, payMethod },
  });
}

/** 老板端主动查询充值订单支付结果。 */
export function queryBossRechargeOrder(orderNo) {
  return request({ url: `/boss/balance/recharge/query?orderNo=${encodeURIComponent(orderNo)}`, skipUserIdHeader: true });
}

/** 老板钱包微信充值，金额单位为元。 */
export function createBossWalletWechatRecharge(data = {}, idempotencyKey = "") {
  return request({
    url: "/boss/balance/recharge/create",
    method: "POST",
    data: { amount: data.amount, payMethod: "wechat" },
    header: { "Idempotency-Key": idempotencyKey },
    skipUserIdHeader: true,
    skipMock: true,
  });
}

/** 查询老板钱包微信充值结果。 */
export function getBossWalletRechargeResult(orderNo) {
  return request({
    url: `/boss/balance/recharge/query?orderNo=${encodeURIComponent(orderNo)}`,
    skipUserIdHeader: true,
    skipMock: true,
  });
}

/** 老板端充值订单列表。 */
export function listBossRechargeOrders(status) {
  const qs = status ? `?status=${encodeURIComponent(status)}` : "";
  return request({ url: `/boss/balance/recharge/orders${qs}`, skipUserIdHeader: true });
}

// -------------------- 人才库（候选人模块） --------------------

/** 人才库列表：type=skilled熟练工/new新零工，favoriteOnly=true 我收藏的 */
export function listTalentPool(params = {}) {
  return request({
    url: `/boss/talent-pool?${query({ page: 0, size: 20, ...params })}`,
    skipUserIdHeader: true,
  });
}

/** 人才详情（含雇主评价与雇佣记录） */
export function getTalentPoolDetail(id) {
  return request({ url: `/boss/talent-pool/${id}`, skipUserIdHeader: true });
}

/** 添加零工到人才库 */
export function addTalentToPool(data = {}) {
  return request({ url: "/boss/talent-pool", method: "POST", data, skipUserIdHeader: true });
}

/** 收藏/取消收藏人才 */
export function toggleTalentPoolFavorite(id, favorite) {
  return request({
    url: `/boss/talent-pool/${id}/favorite`,
    method: "PUT",
    data: favorite === undefined ? {} : { favorite: Boolean(favorite) },
    skipUserIdHeader: true,
  });
}

/** 雇佣零工：{ jobId, jobName, workDate, note } */
export function hireTalentWorker(id, data = {}) {
  return request({
    url: `/boss/talent-pool/${id}/hire`,
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

/** 删除人才库条目 */
export function removeTalentFromPool(id) {
  return request({
    url: `/boss/talent-pool/${id}`,
    method: "DELETE",
    skipUserIdHeader: true,
  });
}

// -------------------- 批量发薪模块 --------------------

/** 我的发薪单列表 */
export function listBossPayrollOrders(params = {}) {
  return request({
    url: `/boss/payroll/orders?${query(params)}`,
    skipUserIdHeader: true,
  });
}

/** 创建发薪单：{ title, projectId, projectName, type, details?[] }。
 *  不传 details 时创建为「草稿」单，后续走添加人员 / 设置薪资 / 提交审核流程。 */
export function createBossPayrollOrder(data = {}) {
  return request({
    url: "/boss/payroll/orders",
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

/** 发薪单详情（含人员明细）：返回 { order, details:[{userId,name,phone,job,dailyWage,attendDays,amount,status}] } */
export function getBossPayrollOrder(orderId) {
  return request({
    url: `/boss/payroll/orders/${normalizeId(orderId, "orderId")}`,
    skipUserIdHeader: true,
  });
}

/** 草稿-添加/替换发薪人员：members[{userId,name,phone,job,dailyWage?,attendDays?}]（仅草稿态） */
export function setBossPayrollMembers(orderId, members = []) {
  return request({
    url: `/boss/payroll/orders/${normalizeId(orderId, "orderId")}/members`,
    method: "PUT",
    data: { members },
    skipUserIdHeader: true,
  });
}

/** 草稿-设置薪资：salaries[{userId,phone,dailyWage(分),attendDays}]（仅草稿态） */
export function setBossPayrollSalary(orderId, salaries = []) {
  return request({
    url: `/boss/payroll/orders/${normalizeId(orderId, "orderId")}/salary`,
    method: "PUT",
    data: { salaries },
    skipUserIdHeader: true,
  });
}

/** 草稿-提交审核：校验已有人员且金额>0，草稿转待审批 */
export function submitBossPayrollOrder(orderId) {
  return request({
    url: `/boss/payroll/orders/${normalizeId(orderId, "orderId")}/submit`,
    method: "POST",
    skipUserIdHeader: true,
  });
}

/** 项目在职成员（批量发薪用）：含姓名/手机号/岗位/userId/日薪（分，0 表示需手填） */
export function listBossPayrollMembers(projectId) {
  return request({
    url: `/boss/payroll/orders/members?projectId=${encodeURIComponent(projectId)}`,
    skipUserIdHeader: true,
  });
}

/** 已审批的发薪记录 */
export function listApprovedPayrollOrders(params = {}) {
  return request({
    url: `/boss/payroll/orders/approved?${query(params)}`,
    skipUserIdHeader: true,
  });
}

/** 待我审批的发薪单列表 */
export function listPendingPayrollOrders(params = {}) {
  return request({
    url: `/boss/payroll/orders/pending?${query(params)}`,
    skipUserIdHeader: true,
  });
}

/** 已驳回的发薪记录 */
export function listRejectedPayrollOrders(params = {}) {
  return request({
    url: `/boss/payroll/orders/rejected?${query(params)}`,
    skipUserIdHeader: true,
  });
}

/** 已审批记录（包含已通过和已驳回） */
export function listReviewedPayrollOrders(params = {}) {
  return request({
    url: `/boss/payroll/orders/reviewed?${query(params)}`,
    skipUserIdHeader: true,
  });
}

/** 审批通过发薪单 */
export function approvePayrollOrder(orderId) {
  return request({
    url: `/boss/payroll/orders/${normalizeId(orderId, "orderId")}/approve`,
    method: "PUT",
    skipUserIdHeader: true,
  });
}

/** 驳回发薪单 */
export function rejectPayrollOrder(orderId, reason) {
  return request({
    url: `/boss/payroll/orders/${normalizeId(orderId, "orderId")}/reject`,
    method: "PUT",
    data: { reason: reason || "" },
    skipUserIdHeader: true,
  });
}

/** 发薪员工列表与统计 */
export function listPayrollEmployees(params = {}) {
  return request({
    url: `/boss/payroll/employees?${query(params)}`,
    skipUserIdHeader: true,
  });
}

/** 老板企业成员列表，身份由当前 JWT 判断。 */
export function listBossEnterpriseMembers(params = {}) {
  return request({
    url: `/boss/enterprise/members?${query(params)}`,
    skipUserIdHeader: true,
  });
}

/** 发薪员工详情（含最近发薪记录） */
export function getPayrollEmployeeDetail(id) {
  return request({
    url: `/boss/payroll/employees/${id}`,
    skipUserIdHeader: true,
  });
}

/** 添加发薪员工 */
export function addPayrollEmployee(data = {}) {
  return request({
    url: "/boss/payroll/employees",
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

export function listTalentBlacklist(bossId) {
  return request({ url: `/talent/blacklist?${query({ bossId })}` });
}

export function filterOrders(params = {}) {
  return request({
    url: `/boss/order/filter?${query({ page: 0, size: 20, ...params })}`,
  });
}

export function listMonthlyOrders(userId) {
  return request({ url: `/boss/order/monthly?${query({ userId })}` });
}

export function listPressSalaryOrders(userId) {
  return request({ url: `/boss/order/press-salary?${query({ userId })}` });
}

export function listFavoriteJobs(userId) {
  return request({ url: `/jobs/favorites?${query({ userId })}` });
}

export function favoriteJob(data = {}) {
  return request({
    url: "/jobs/favorites",
    method: "POST",
    data: {
      userId: normalizeId(data.userId, "userId"),
      orderId: normalizeId(data.orderId, "orderId"),
    },
  });
}

export function unfavoriteJob(id) {
  return request({ url: `/jobs/favorites/${id}`, method: "DELETE" });
}

export function checkFavoriteJob(userId, orderId) {
  return request({
    url: `/jobs/favorites/check?${query({ userId, orderId })}`,
  });
}

export function listBrowseHistory(userId, params = {}) {
  return request({
    url: `/jobs/history?${query({ userId, page: 0, size: 20, ...params })}`,
  });
}

export function recordJobBrowse(data = {}) {
  return request({
    url: "/jobs/history",
    method: "POST",
    data: {
      userId: normalizeId(data.userId, "userId"),
      orderId: normalizeId(data.orderId, "orderId"),
    },
  });
}

export function clearJobBrowseHistory(userId) {
  return request({
    url: `/jobs/history?${query({ userId })}`,
    method: "DELETE",
  });
}

export function getSettlementDetail(id) {
  return request({ url: `/settle/${id}/detail` });
}

export function getMessage(id) {
  return request({ url: `/message/${id}` });
}

export function listSystemMessages(userId, params = {}) {
  return request({
    url: `/message/system?${query({ userId, page: 0, size: 20, ...params })}`,
  });
}

export function getNotificationSettings(userId) {
  return request({ url: `/notification-settings/${userId}` });
}

export function updateNotificationSettings(userId, data) {
  return request({
    url: `/notification-settings/${userId}`,
    method: "PUT",
    data,
  });
}

export function getPoints(userId) {
  return request({ url: `/points/${userId}` });
}

/** 老板端积分余额、积分套餐和规则，身份由当前 JWT 判断。 */
export function getBossPointsOverview() {
  return request({ url: "/boss/points", skipUserIdHeader: true });
}

/** 老板端创建积分购买订单，并获取微信 JSAPI 支付参数。 */
export function createBossPointsPurchase(data = {}, idempotencyKey = "") {
  return request({
    url: "/boss/points/purchase",
    method: "POST",
    data,
    header: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {},
    skipUserIdHeader: true,
  });
}

/** 老板端赠送积分给零工，身份由当前 JWT 判断。 */
export function giftBossPoints(data = {}, idempotencyKey = "") {
  return request({
    url: "/boss/points/gift",
    method: "POST",
    data,
    header: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {},
    skipUserIdHeader: true,
  });
}

/** 老板端积分明细，身份由当前 JWT 判断，支持兑换/购买分类和分页。 */
export function listBossPointRecords(params = {}) {
  return request({
    url: `/boss/points/records?${query({ page: 0, size: 20, category: "EXCHANGE", ...params })}`,
    skipUserIdHeader: true,
    rawResponse: true,
  });
}

export function listPointFlows(userId, params = {}) {
  return request({
    url: `/points/${userId}/flows?${query({ page: 0, size: 20, ...params })}`,
  });
}

/** 零工端积分提现配置，身份由当前 JWT 判断。 */
export function getWorkerPointsWithdrawConfig() {
  return request({
    url: "/worker/points/withdraw-config",
    skipUserIdHeader: true,
  });
}

/** 零工端提交积分提现申请。 */
export function createWorkerPointsWithdrawal(data = {}, idempotencyKey = "") {
  return request({
    url: "/worker/points/withdrawals",
    method: "POST",
    data,
    header: { "Idempotency-Key": idempotencyKey },
    skipUserIdHeader: true,
  });
}

/** 零工端积分提现记录，支持状态筛选和分页。 */
export function listWorkerPointsWithdrawals(params = {}) {
  return request({
    url: `/worker/points/withdrawals?${query({ page: 0, size: 20, status: "ALL", ...params })}`,
    skipUserIdHeader: true,
  });
}

/** 零工端单笔积分提现详情。 */
export function getWorkerPointsWithdrawalDetail(id) {
  return request({
    url: `/worker/points/withdrawals/${normalizeId(id, "id")}`,
    skipUserIdHeader: true,
  });
}

export function listRewards() {
  return request({ url: "/rewards" });
}

/** 老板端奖励金账户概览。 */
export function getBossRewardOverview() {
  return request({ url: "/boss/rewards/overview", skipUserIdHeader: true });
}

/** 老板端奖励金收支明细。 */
export function listBossRewardRecords(params = {}) {
  return request({
    url: `/boss/rewards/records?${query({ page: 0, size: 20, type: "ALL", ...params })}`,
    skipUserIdHeader: true,
  });
}

/** 创建老板奖励金微信充值订单，金额单位为元。 */
export function createBossRewardRecharge(amount, idempotencyKey = "", payMethod = "WECHAT") {
  return request({
    url: "/boss/rewards/recharge",
    method: "POST",
    data: { amount, payMethod },
    header: { "Idempotency-Key": idempotencyKey },
    skipUserIdHeader: true,
  });
}

/** 老板奖励金充值页配置。 */
export function getBossRewardRechargeConfig() {
  return request({ url: "/boss/rewards/recharge/config", skipUserIdHeader: true });
}

/** 老板奖励金充值记录。 */
export function listBossRewardRechargeRecords(params = {}) {
  return request({
    url: `/boss/rewards/recharge-records?${query({ page: 0, size: 20, status: "ALL", ...params })}`,
    skipUserIdHeader: true,
  });
}

/** 查询老板奖励金充值订单。 */
export function getBossRewardRecharge(orderNo) {
  return request({
    url: `/boss/rewards/recharge/${encodeURIComponent(orderNo)}`,
    skipUserIdHeader: true,
  });
}

/** 老板端奖励金提现，金额单位为元。 */
export function createBossRewardWithdrawal(amount, idempotencyKey = "", channel = "WECHAT", account = "") {
  return request({
    url: "/boss/rewards/withdraw",
    method: "POST",
    data: { amount, channel, account },
    header: { "Idempotency-Key": idempotencyKey },
    skipUserIdHeader: true,
  });
}

/** 老板端奖励金提现记录。 */
export function listBossRewardWithdrawals(params = {}) {
  return request({
    url: `/boss/rewards/withdrawals?${query({ page: 0, size: 20, ...params })}`,
    skipUserIdHeader: true,
  });
}

/** 零工端奖励金账户概览，身份由当前 JWT 判断。 */
export function getWorkerRewardOverview() {
  return request({
    url: "/worker/rewards/overview",
    skipUserIdHeader: true,
  });
}

/** 零工端奖励金明细，支持类型筛选和数据库分页。 */
export function getWorkerRewardRecords(params = {}) {
  return request({
    url: `/worker/rewards/records?${query({ page: 0, size: 20, type: "ALL", ...params })}`,
    skipUserIdHeader: true,
  });
}

/** 零工端奖励金获取及提现规则。 */
export function getWorkerRewardRules() {
  return request({
    url: "/worker/rewards/rules",
    skipUserIdHeader: true,
  });
}

/** 零工端申请奖励金提现。 */
export function withdrawWorkerReward(data = {}, idempotencyKey = "") {
  return request({
    url: "/worker/rewards/withdraw",
    method: "POST",
    data: {
      amount: data.amount,
      channel: data.channel || "WECHAT",
    },
    header: { "Idempotency-Key": idempotencyKey },
    skipUserIdHeader: true,
    skipMock: true,
  });
}

export function listRewardExchanges(userId) {
  return request({ url: `/rewards/exchanges?${query({ userId })}` });
}
export function exchangeReward(id, userId) {
  return request({
    url: `/rewards/${id}/exchange?${query({ userId })}`,
    method: "POST",
  });
}
export function listCoupons(userId, status) {
  return request({ url: `/coupons?${query({ userId, status })}` });
}
export function claimCoupon(id, userId) {
  return request({
    url: `/coupons/${id}/claim?${query({ userId })}`,
    method: "POST",
  });
}

export function listBossCouponAvailableOrders(userCouponId, params = {}) {
  return request({
    url: `/boss/coupons/${normalizeId(userCouponId, "userCouponId")}/available-orders?${query({ page: 0, size: 20, ...params })}`,
  });
}
export function getDeposit(userId) {
  return request({ url: `/deposits/${userId}` });
}
export function createDeposit(data) {
  return request({ url: "/deposits", method: "POST", data });
}
export function refundDeposit(id) {
  return request({ url: `/deposits/${id}/refund`, method: "POST" });
}
export function getInviteCode(userId) {
  return request({ url: `/invite/code?${query({ userId })}` });
}
export function getInvitePoster(userId) {
  return request({ url: `/invite/poster?${query({ userId })}` });
}
export function listInviteRelations(userId) {
  return request({ url: `/invite/relations?${query({ userId })}` });
}
export function listBadges() {
  return request({ url: "/badges" });
}
export function listUserBadges(userId) {
  return request({ url: `/badges/user/${userId}` });
}
export function getStarLevel(userId) {
  return request({ url: `/star-level/${userId}` });
}
export function listInsurance(userId) {
  return request({ url: `/insurance?${query({ userId })}` });
}
export function getInsurance(id) {
  return request({ url: `/insurance/${id}` });
}
export function buyInsurance(data) {
  return request({ url: "/insurance", method: "POST", data });
}
export function listInsuranceProducts() {
  return request({ url: "/insurance/products" });
}

export function listCourses(params = {}) {
  return request({
    url: `/courses?${query({ page: 0, size: 20, ...params })}`,
  });
}

export function getCourse(id) {
  return request({ url: `/courses/${id}` });
}

export function listCourseVideos(id) {
  return request({ url: `/courses/${id}/videos` });
}

export function getExam(courseId) {
  return request({ url: `/exams/${courseId}` });
}

export function submitExam(courseId, data) {
  return request({ url: `/exams/${courseId}/submit`, method: "POST", data });
}

export function getExamResult(userId, examId) {
  return request({ url: `/exams/result?${query({ userId, examId })}` });
}

export function listTrainingTasks(userId) {
  return request({ url: `/training-tasks?${query({ userId })}` });
}

export function listBossSubAccounts() {
  return request({ url: "/boss/sub-accounts", skipUserIdHeader: true });
}

export function createBossSubAccount(data = {}) {
  return request({
    url: "/boss/sub-accounts",
    method: "POST",
    data,
    skipUserIdHeader: true,
  });
}

/** 修改当前企业成员的菜单权限，memberId 为企业成员 ID。 */
export function updateBossSubAccountPermissions(memberId, permissionCodes = []) {
  return request({
    url: `/boss/sub-accounts/${encodeURIComponent(memberId)}/permissions`,
    method: "PUT",
    data: { permissionCodes },
    skipUserIdHeader: true,
  });
}

/** 撤销当前企业成员的企业端权限。 */
export function revokeBossSubAccountPermissions(memberId) {
  return request({
    url: `/boss/sub-accounts/${encodeURIComponent(memberId)}/permissions`,
    method: "DELETE",
    skipUserIdHeader: true,
  });
}

/** 零工课堂首页聚合内容（课程、视频、考试题目和平台规则）。 */
export function getWorkerClassroomOverview() {
  return request({ url: "/worker/classroom/overview", skipUserIdHeader: true });
}

export function getWorkerClassroomQuiz() {
  return request({ url: "/worker/classroom/quiz" });
}

export function submitWorkerClassroomQuiz(data) {
  return request({ url: "/worker/classroom/quiz/submit", method: "POST", data });
}

export function completeTrainingTask(id) {
  return request({ url: `/training-tasks/${id}/complete`, method: "PUT" });
}

export function listRules(category) {
  return request({
    url: `/rules?${query({ category })}`,
    method: "GET",
  });
}

/** 老板端诚意分规则，读取后台已发布信用分规则。 */
export function listBossCreditRules(params = {}) {
  return request({
    url: `/admin/rules?${query({
      type: "credit",
      category: "信用分规则",
      status: "published",
      page: 0,
      size: 200,
      ...params,
    })}`,
    skipUserIdHeader: true,
  });
}

export function getRule(id) {
  return request({
    url: `/rules/${encodeURIComponent(id)}`,
    method: "GET",
  });
}

export function listFaq(category) {
  return request({ url: `/faq?${query({ category })}` });
}

export function createChatSession(data) {
  return request({ url: "/chat/sessions", method: "POST", data });
}

/** 用户侧会话列表：轮询兜底时用来感知会话被客服关闭。 */
export function listMyChatSessions(userId) {
  return request({ url: `/chat/sessions?userId=${encodeURIComponent(userId)}` });
}

export function listChatMessages(id, params = {}) {
  return request({
    url: `/chat/sessions/${id}/messages?${query({ page: 0, size: 50, ...params })}`,
  });
}

export function sendChatMessage(id, data) {
  return request({
    url: `/chat/sessions/${id}/messages`,
    method: "POST",
    data,
  });
}

export function listSocialGroups() {
  return request({ url: "/social-groups" });
}

export function createSocialGroup(data) {
  return request({ url: "/social-groups", method: "POST", data });
}

export function listBossContracts(bossId) {
  return request({ url: `/boss/contracts?${query({ bossId })}` });
}

export function getBossContract(id) {
  return request({ url: `/boss/contracts/${id}` });
}

export function createBossContract(data) {
  return request({ url: "/boss/contracts", method: "POST", data });
}

export function listExpenses(userId, params = {}) {
  return request({
    url: `/expenses?${query({ userId, page: 0, size: 20, ...params })}`,
  });
}

export function listPayments(userId, params = {}) {
  return request({
    url: `/payments?${query({ userId, page: 0, size: 20, ...params })}`,
  });
}

export function listMissedCalls(userId, params = {}) {
  return request({
    url: `/missed-calls?${query({ userId, page: 0, size: 20, ...params })}`,
  });
}

export function readMissedCall(id) {
  return request({ url: `/missed-calls/${id}/read`, method: "PUT" });
}

/** 登录前读取后台配置的启用协议。 */
export function getPublicAgreement(type) {
  return request({
    url: `/public/agreements?type=${encodeURIComponent(type)}`,
    skipUserIdHeader: true,
  });
}
