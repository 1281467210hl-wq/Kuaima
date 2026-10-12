package com.kuaima.app.controller.boss;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

import com.kuaima.app.common.Result;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.model.BossOrderQuery;
import com.kuaima.app.domain.boss.service.BossOrderService;
import com.kuaima.app.domain.boss.service.BossProfileService;
import com.kuaima.app.domain.jobcategory.model.JobCategoryModels.HotItem;
import com.kuaima.app.domain.jobcategory.service.JobCategoryService;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.service.CertificationService;
import com.kuaima.app.security.model.LoginUser;
import com.kuaima.app.domain.wallet.service.WalletService;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.coupon.repository.UserCouponRepository;
import com.kuaima.app.domain.invite.repository.InviteRelationRepository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.enterprise.service.EnterpriseContextService;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.common.service.OssUploadResult;

@RestController
@RequestMapping("/boss")
@Tag(name = "老板-招工", description = "老板发布招工、订单管理、报名处理")
public class BossController {

    private final BossOrderService bossOrderService;
    private final JobCategoryService jobCategoryService;
    private final CertificationService certificationService;
    private final WalletService walletService;
    private final PointsAccountRepository pointsAccountRepository;
    private final UserCouponRepository userCouponRepository;
    private final InviteRelationRepository inviteRelationRepository;
    private final BossOrderRespository orderRepository;
    private final BaseOrderItemRespository itemRepository;
    private final BossProfileService bossProfileService;
    private final EnterpriseContextService enterpriseContexts;
    private final OssStorageService ossStorageService;

    @Autowired
    public BossController(BossOrderService bossOrderService, JobCategoryService jobCategoryService,
                          CertificationService certificationService, WalletService walletService,
                          PointsAccountRepository pointsAccountRepository, UserCouponRepository userCouponRepository,
                          InviteRelationRepository inviteRelationRepository, BossOrderRespository orderRepository,
                          BaseOrderItemRespository itemRepository, BossProfileService bossProfileService,
                          EnterpriseContextService enterpriseContexts, OssStorageService ossStorageService) {
        this.bossOrderService = bossOrderService;
        this.jobCategoryService = jobCategoryService;
        this.certificationService = certificationService;
        this.walletService = walletService;
        this.pointsAccountRepository = pointsAccountRepository;
        this.userCouponRepository = userCouponRepository;
        this.inviteRelationRepository = inviteRelationRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.bossProfileService = bossProfileService;
        this.enterpriseContexts = enterpriseContexts;
        this.ossStorageService = ossStorageService;
    }

    /** 兼容既有单元测试及其他直接构造调用。 */
    public BossController(BossOrderService bossOrderService, JobCategoryService jobCategoryService,
                          CertificationService certificationService) {
        this(bossOrderService, jobCategoryService, certificationService, null, null, null, null, null, null, null, null, null);
    }

    @Operation(summary = "招工订单运营统计", description = "按订单工作日期及零工报名状态统计当前老板可见订单；跨天订单按开始日期归属")
    @GetMapping("/order/stats")
    public Result<Map<String, Object>> orderStats(@RequestParam(defaultValue = "ALL") String dateRange,
                                                   Authentication authentication) {
        Long bossId = requireCurrentBossId(authentication);
        LocalDate today = LocalDate.now();
        LocalDate target = switch (dateRange.toUpperCase()) { case "YESTERDAY" -> today.minusDays(1); case "TODAY" -> today; case "TOMORROW" -> today.plusDays(1); default -> null; };
        Set<Long> orderIds = new HashSet<>();
        for (BossOrder o : orderRepository.findByCreateByOrderByIdDesc(bossId)) {
            if (target == null || o.getStartTime() == null || o.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().equals(target)) orderIds.add(o.getId());
        }
        int applicant=0, accepted=0, arrived=0, working=0, pending=0;
        for (Long id : orderIds) for (BaseOrderItem i : itemRepository.findByOrderId(id)) {
            switch (i.getStatus()) { case BossStatus.ITEM_APPLIED -> applicant++; case BossStatus.ITEM_HIRED -> accepted++; case BossStatus.ITEM_ON_WORK -> { accepted++; arrived++; working++; } case BossStatus.ITEM_FINISHED -> { accepted++; pending++; } default -> {} }
        }
        return Result.success(Map.of("dateRange", dateRange.toUpperCase(), "applicantCount", applicant, "acceptedCount", accepted, "arrivedCount", arrived, "workingCount", working, "pendingSettlementCount", pending));
    }

    // ==================== 招工订单 ====================

    /** 发布订单 */
    @Operation(summary = "发布招工订单", description = "创建 BossOrder，初始状态自动为「待审核」。工作地址必须提供 longitude 和 latitude；也可传当前企业常用地址 addressId，由后端复制地址和坐标。")
    @PostMapping("/order")
    public Result<BossOrder> createOrder(@RequestBody BossOrder order, Authentication authentication) {
        EnterpriseContextService.Context context = requireEnterprise(authentication, "ORDER_CREATE");
        order.setCreateBy(context.user().getId());
        order.setEnterpriseId(context.enterprise().getId());
        return Result.success(bossOrderService.createOrder(order));
    }

    @Operation(summary = "发布资格查询", description = "查询当前老板个人实名认证和企业认证状态")
    @GetMapping("/publish-eligibility")
    public Result<Map<String, Object>> publishEligibility(Authentication authentication) {
        return Result.success(certificationService.publishEligibility(requireCurrentBossId(authentication)));
    }

    /** 修改订单（仅招工中） */
    @Operation(summary = "修改招工订单", description = "仅「待审核」或「招工中」状态可修改；请求体字段非空才会被更新。类型改为非月结时清空 trialDuration；试工时间仅在类型为 month 时可设置")
    @PutMapping("/order/{id}")
    public Result<BossOrder> updateOrder(@PathVariable Long id, @RequestBody BossOrder order,
                                         Authentication authentication) {
        EnterpriseContextService.Context context = requireEnterprise(authentication, "ORDER_CREATE");
        requireOwnedOrder(id, authentication);
        order.setEnterpriseId(context.enterprise().getId());
        return Result.success(bossOrderService.updateOrder(id, order));
    }

    /** 订单详情 */
    @Operation(summary = "订单详情", description = "返回完整订单对象（含 id、date、createBy、timestamp 及订单字段）")
    @GetMapping("/order/{id}")
    public Result<BossOrder> getOrder(@PathVariable Long id, Authentication authentication) {
        requireOwnedOrder(id, authentication);
        return Result.success(bossOrderService.getOrder(id));
    }

    /**
     * 订单列表分页查询：/boss/order?type=daily&status=招工中&title=xxx&page=0&size=10
     * type 取值：daily(每天日结) / heldBack(压薪日结) / month(月结)
     * page 从 0 开始，size 默认 10
     */
    @Operation(summary = "订单列表分页", description = "仅返回当前老板账号创建的订单。所有非空筛选条件按 AND 组合，支持 type、status、title、startDate/endDate、jobCategoryId、salaryMin/salaryMax、经纬度距离、experience、gender、tags、tagMode(ALL/ANY) 和分页；返回准确 total 及 currentApply")
    @GetMapping("/order")
    public Result<List<BossOrder>> listOrders(@ModelAttribute BossOrderQuery query,
                                               Authentication authentication) {
        EnterpriseContextService.Context context = requireEnterprise(authentication, "ORDER_VIEW");
        Page<BossOrder> result = bossOrderService.listOrdersByEnterprise(context.enterprise().getId(), query);
        return Result.success(result.getContent(), result.getNumber(), result.getTotalElements());
    }

    /** 删除订单（仅招工中/已取消） */
    @Operation(summary = "删除订单", description = "仅「待审核」/「审核拒绝」/「招工中」/「取消招工」状态的订单可删除")
    @DeleteMapping("/order/{id}")
    public Result<Void> deleteOrder(@PathVariable Long id, Authentication authentication) {
        requireOwnedOrder(id, authentication);
        bossOrderService.deleteOrder(id);
        return Result.success();
    }

    /** 订单状态流转：/boss/order/{id}/status?target=招工结束|待结算|已完成|取消招工 */
    @Operation(summary = "订单状态流转", description = "target 取值：招工结束 / 待结算 / 已完成 / 取消招工。取消招工会将该订单下所有未完成报名记录置为「取消招工」并记录取消时间")
    @PutMapping("/order/{id}/status")
    public Result<BossOrder> changeOrderStatus(@PathVariable Long id, @RequestParam String target,
                                               Authentication authentication) {
        requireOwnedOrder(id, authentication);
        return Result.success(bossOrderService.changeOrderStatus(id, target));
    }

    /**
     * 【预留】岗位快开始提醒：手动触发，通知该订单 已报名/已录用/已到岗 的用户
     * （当前不做定时任务，由前端/人工调用；后续如需自动提醒可加定时扫描）
     */
    @Operation(summary = "岗位快开始提醒", description = "手动触发「岗位快开始」通知，推送给该订单下「已报名/已录用/已到岗」的零工。当前不做定时任务，由前端/人工调用")
    @PostMapping("/order/{id}/remind-start")
    public Result<Void> notifyOrderStart(@PathVariable Long id) {
        bossOrderService.notifyOrderStart(id);
        return Result.success();
    }

    private BossOrder requireOwnedOrder(Long orderId, Authentication authentication) {
        EnterpriseContextService.Context context = requireEnterprise(authentication, "ORDER_VIEW");
        BossOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("订单不存在"));
        boolean currentEnterprise = order.getEnterpriseId() != null
                && order.getEnterpriseId().equals(context.enterprise().getId());
        boolean legacyOwner = order.getEnterpriseId() == null
                && context.user().getId().equals(order.getCreateBy());
        if (!currentEnterprise && !legacyOwner) {
            throw new ForbiddenBusinessException("无权操作该订单");
        }
        return order;
    }

    private EnterpriseContextService.Context requireEnterprise(Authentication authentication, String permission) {
        if (enterpriseContexts == null) {
            throw new ForbiddenBusinessException("企业上下文服务未配置");
        }
        return enterpriseContexts.require(authentication, permission);
    }

    // ==================== 报名记录 ====================

    /** 用户报名：/boss/order/{orderId}/apply?userId=1&remark=xxx&trial=true（trial 仅月结订单有效） */
    @Operation(summary = "用户报名岗位", description = "订单须为「招工中」，同一用户不可重复报名；「已录用+已到岗+已完成」人数达到招工人数后不可再报名。trial「我要试工」标记仅月结(month)订单可传 true")
    @PostMapping("/order/{orderId}/apply")
    public Result<BaseOrderItem> applyOrder(@PathVariable Long orderId,
                                            @RequestParam Long userId,
                                            @RequestParam(required = false) String remark,
                                            @RequestParam(required = false) Boolean trial) {
        return Result.success(bossOrderService.applyOrder(orderId, userId, remark, trial));
    }

    /** 老板录用 */
    @Operation(summary = "老板录用零工", description = "仅「已报名」的记录可录用，状态变为「已录用」并记录录用时间")
    @PutMapping("/item/{id}/hire")
    public Result<BaseOrderItem> hireItem(@PathVariable Long id) {
        return Result.success(bossOrderService.hireItem(id));
    }

    /** 老板拒绝待审核报名。 */
    @Operation(summary = "老板拒绝报名", description = "仅岗位所属老板可拒绝状态为「已报名」的记录，并通知零工")
    @PutMapping("/item/{id}/reject")
    public Result<BaseOrderItem> rejectItem(@PathVariable Long id,
                                             @RequestParam(required = false) String reason,
                                             Authentication authentication) {
        return Result.success(bossOrderService.rejectItem(id, requireCurrentBossId(authentication), reason));
    }

    /** 用户确认到岗 */
    @Operation(summary = "用户确认到岗", description = "仅「已录用」的记录可确认到岗，状态变为「已到岗」并记录到岗时间")
    @PutMapping("/item/{id}/work")
    public Result<BaseOrderItem> confirmWork(@PathVariable Long id) {
        return Result.success(bossOrderService.confirmWork(id));
    }

    /** 完成 */
    @Operation(summary = "完成报名记录", description = "仅「已到岗」的记录可完成，状态变为「已完成」并记录完成时间")
    @PutMapping("/item/{id}/finish")
    public Result<BaseOrderItem> finishItem(@PathVariable Long id) {
        return Result.success(bossOrderService.finishItem(id));
    }

    /** 取消报名：/boss/item/{id}/cancel?reason=xxx */
    @Operation(summary = "取消报名", description = "仅「已报名」/「已录用」状态可取消，状态变为「取消报名」并记录取消时间、原因")
    @PutMapping("/item/{id}/cancel")
    public Result<BaseOrderItem> cancelItem(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return Result.success(bossOrderService.cancelItem(id, reason));
    }

    /** 某订单的报名列表 */
    @Operation(summary = "订单报名列表", description = "返回该订单下所有报名记录数组，包含 userId 对应的 nickname（nickname 为非持久化展示字段）")
    @GetMapping("/order/{orderId}/items")
    public Result<List<BaseOrderItem>> listItemsByOrder(@PathVariable Long orderId) {
        return Result.success(bossOrderService.listItemsByOrder(orderId));
    }

    /** 某用户的报名记录：/boss/user/items?userId=1 */
    @Operation(summary = "用户报名记录", description = "返回该用户所有报名记录数组")
    @GetMapping("/user/items")
    public Result<List<BaseOrderItem>> listItemsByUser(@RequestParam Long userId) {
        return Result.success(bossOrderService.listItemsByUser(userId));
    }

    // ==================== 草稿管理 ====================

    /** 保存草稿 */
    @Operation(summary = "保存订单草稿", description = "创建 BossOrder，orderStatus 置为「草稿」")
    @PostMapping("/order/draft")
    public Result<BossOrder> saveDraft(@RequestBody BossOrder order, Authentication authentication) {
        EnterpriseContextService.Context context = requireEnterprise(authentication, "ORDER_CREATE");
        order.setCreateBy(context.user().getId());
        order.setEnterpriseId(context.enterprise().getId());
        return Result.success(bossOrderService.saveDraft(order));
    }

    /** 草稿列表：/boss/orders/drafts?userId=1 */
    @Operation(summary = "草稿订单列表", description = "返回当前用户的草稿订单列表")
    @GetMapping("/orders/drafts")
    public Result<List<BossOrder>> listDrafts(@RequestParam(required = false) Long userId,
                                               Authentication authentication) {
        Long currentBossId = requireCurrentBossId(authentication);
        if (userId != null && !currentBossId.equals(userId)) {
            throw new ForbiddenBusinessException("只能查询当前老板账号草稿");
        }
        return Result.success(bossOrderService.listDrafts(currentBossId));
    }

    /** 更新草稿 */
    @Operation(summary = "更新订单草稿", description = "按 id 更新草稿订单字段")
    @PutMapping("/order/{id}/draft")
    public Result<BossOrder> updateDraft(@PathVariable Long id, @RequestBody BossOrder order,
                                         Authentication authentication) {
        EnterpriseContextService.Context context = requireEnterprise(authentication, "ORDER_CREATE");
        requireOwnedOrder(id, authentication);
        order.setEnterpriseId(context.enterprise().getId());
        return Result.success(bossOrderService.updateDraft(id, order));
    }

    // ==================== 统计与资料 ====================

    /** 老板首页统计：/boss/stats?userId=1 */
    @Operation(summary = "老板首页统计", description = "返回 totalOrders、recruitingCount、applicantCount、settledAmount 等汇总数据")
    @GetMapping("/stats")
    public Result<com.kuaima.app.domain.boss.model.BossHomeModels.BossStats> getBossStats(
            @RequestParam(required = false) Long userId, Authentication authentication) {
        Long currentBossId = requireCurrentBossId(authentication);
        if (userId != null && !currentBossId.equals(userId)) {
            throw new ForbiddenBusinessException("只能查询当前老板账号统计");
        }
        return Result.success(bossOrderService.getBossStats(currentBossId));
    }

    /** 工种分类：/boss/job-categories */
    @Operation(summary = "热门工种列表", description = "兼容原有前端路径，返回基础数据表中启用的热门工种；完整三级分类请使用 /job-categories/tree")
    @GetMapping("/job-categories")
    public Result<List<HotItem>> getJobCategories() {
        return Result.success(jobCategoryService.hot());
    }

    /** 老板资料：/boss/profile/{userId} */
    @Operation(summary = "老板资料详情", description = "返回老板用户完整信息（含 companyName、industry、contact、contactPhone 等企业字段）")
    @GetMapping("/profile/{userId}")
    public Result<User> getBossProfile(@PathVariable Long userId) {
        return Result.success(bossOrderService.getBossProfile(userId));
    }

    /** 老板账户统计：/boss/profile/{userId}/stats */
    @Operation(summary = "老板个人页统计", description = "仅允许当前JWT老板查询本人；保留基础统计并返回诚意分、好评率、到岗完成率、24小时结算率、累计支付分值和已完成订单数")
    @GetMapping("/profile/{userId}/stats")
    public Result<com.kuaima.app.domain.boss.model.BossProfileModels.ProfileStats> getBossProfileStats(
            @PathVariable Long userId, Authentication authentication) {
        Long currentBossId = requireCurrentBossId(authentication);
        if (!currentBossId.equals(userId)) {
            throw new ForbiddenBusinessException("只能查询当前老板账号统计");
        }
        return Result.success(bossProfileService.stats(currentBossId));
    }

    /** 老板个人页统计（推荐）：身份只从JWT获取，不传userId。 */
    @Operation(summary = "当前老板个人页统计", description = "好评率、到达完成率、24小时结算率均由真实订单、评价及支付数据计算")
    @GetMapping("/profile/stats")
    public Result<com.kuaima.app.domain.boss.model.BossProfileModels.ProfileStats> getCurrentBossProfileStats(
            Authentication authentication) {
        return Result.success(bossProfileService.stats(requireCurrentBossId(authentication)));
    }

    /** 老板个人页资产汇总：余额、积分、未使用券包和已发放邀请奖励金。 */
    @Operation(summary = "老板资产汇总", description = "返回 balance(分)、points、couponCount、rewardAmount(分)；仅允许当前老板查询本人")
    @GetMapping("/profile/{userId}/assets")
    public Result<Map<String, Object>> getBossProfileAssets(@PathVariable Long userId, Authentication authentication) {
        Long current = requireCurrentBossId(authentication);
        if (!current.equals(userId)) throw new ForbiddenBusinessException("只能查询当前老板账号资产");
        Map<String, Object> data = new HashMap<>();
        data.put("balance", walletService.getOrCreateWallet(userId, UserRole.BOSS).getBalance());
        data.put("points", pointsAccountRepository.findByUserIdAndRole(userId, UserRole.BOSS)
                .map(a -> a.getBalance() == null ? 0 : a.getBalance()).orElse(0));
        Date now = new Date(System.currentTimeMillis());
        long coupons = userCouponRepository.countByUserIdAndStatusAndExpireAtIsNull(userId, "UNUSED")
                + userCouponRepository.countByUserIdAndStatusAndExpireAtGreaterThanEqual(userId, "UNUSED", now);
        data.put("couponCount", coupons);
        data.put("rewardAmount", inviteRelationRepository.countByInviterIdAndRewardStatus(userId, "SENT") * 5000L);
        return Result.success(data);
    }

    // ==================== 高级筛选（零工端） ====================

    /** 高级筛选岗位：/boss/order/filter?city=&salaryMin=&salaryMax=&tag=&type=&duration=&page=0&size=20 */
    @Operation(summary = "高级筛选岗位", description = "扩展 BossOrder 查询参数，支持 city、salaryMin/salaryMax 薪资范围、tag 标签、type 招工类型、duration 工作时长筛选")
    @GetMapping("/order/filter")
    public Result<List<BossOrder>> filterOrders(@RequestParam(required = false) String city,
                                                @RequestParam(required = false) Integer salaryMin,
                                                @RequestParam(required = false) Integer salaryMax,
                                                @RequestParam(required = false) String tag,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) Integer duration,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        Page<BossOrder> result = bossOrderService.filterOrders(city, salaryMin, salaryMax, tag, type, duration, page, size);
        return Result.success(result.getContent(), result.getNumber(), result.getTotalElements());
    }

    /** 零工的月结订单列表：/boss/order/monthly?userId=1 */
    @Operation(summary = "零工月结订单列表", description = "返回零工的月结类型订单列表（BaseOrderItem 关联 BossOrder where type=month）")
    @GetMapping("/order/monthly")
    public Result<List<BaseOrderItem>> listMonthlyOrders(@RequestParam Long userId) {
        return Result.success(bossOrderService.listMonthlyOrders(userId));
    }

    /** 零工的压薪日结订单列表：/boss/order/press-salary?userId=1 */
    @Operation(summary = "零工压薪订单列表", description = "返回零工的压薪类型订单列表（type=heldBack）")
    @GetMapping("/order/press-salary")
    public Result<List<BaseOrderItem>> listPressSalaryOrders(@RequestParam Long userId) {
        return Result.success(bossOrderService.listPressSalaryOrders(userId));
    }

    // ==================== 企业认证 ====================

    /** 提交企业认证：POST /boss/enterprise-cert */
    @Operation(summary = "提交企业认证", description = "请求体含 companyName、industry、licenseNo、legalRep、licenseImageUrl。设置 User.certType=ENTERPRISE、certStatus=待审核")
    @PostMapping("/enterprise-cert")
    public Result<User> submitEnterpriseCert(@RequestBody Map<String, String> body, Authentication authentication) {
        Long userId = requireCurrentBossId(authentication);
        java.time.LocalDate foundingDate = null;
        if (body.get("foundingDate") != null && !body.get("foundingDate").isBlank()) {
            foundingDate = java.time.LocalDate.parse(body.get("foundingDate").trim());
        }
        return Result.success(certificationService.submitEnterprise(userId,
                body.get("companyName"), body.get("industry"),
                body.get("licenseNo"), body.get("legalRep"), body.get("licenseImageUrl"),
                foundingDate, body.get("businessScope")));
    }

    /** 上传企业营业执照到 OSS：POST /boss/enterprise-cert/license-upload */
    @Operation(summary = "上传营业执照", description = "仅支持 JPG、JPEG、PNG，文件大小不超过 5MB，保存到 insurance/enterprise-license 目录")
    @PostMapping("/enterprise-cert/license-upload")
    public Result<OssUploadResult> uploadEnterpriseLicense(@RequestParam("file") MultipartFile file,
                                                             Authentication authentication) {
        requireCurrentBossId(authentication);
        if (ossStorageService == null) throw new IllegalStateException("OSS 上传服务未配置");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        if (file.getSize() > 5L * 1024 * 1024) throw new IllegalArgumentException("营业执照图片不能超过5MB");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extension(original);
        if (!(java.util.Set.of("jpg", "jpeg", "png").contains(extension))
                || !(java.util.Set.of("image/jpeg", "image/png").contains(contentType))) {
            throw new IllegalArgumentException("仅支持 JPG、JPEG、PNG 图片");
        }
        com.kuaima.app.common.service.OssUploadResult uploaded =
                ossStorageService.uploadResult(file, "insurance/enterprise-license", "." + extension);
        // 私有 Bucket：objectKey 用于落库/回显状态，url 生成限时签名地址供页面直接查看。
        return Result.success(new com.kuaima.app.common.service.OssUploadResult(
                uploaded.objectKey(), ossStorageService.playableUrl(uploaded.objectKey()),
                uploaded.fileName()));
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /** 从 JWT 认证主体提取老板 ID。 */
    private Long currentBossId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser
                && UserRole.BOSS.equals(loginUser.role())) {
            if (loginUser.id() == null) {
                throw new IllegalStateException("老板登录身份缺少用户ID，请重新登录");
            }
            return loginUser.id();
        }
        throw new ForbiddenBusinessException("当前登录账号不是老板账号");
    }

    private Long requireCurrentBossId(Authentication authentication) {
        Long bossId = currentBossId(authentication);
        if (bossId == null) {
            throw new IllegalStateException("当前登录账号不是老板账号");
        }
        return bossId;
    }
}
