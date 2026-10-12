package com.kuaima.app.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.HashMap;
import java.time.LocalDate;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.jobcategory.entity.JobCategory;
import com.kuaima.app.domain.jobcategory.repository.JobCategoryRepository;
import com.kuaima.app.domain.jobcategory.repository.JobIndustryRepository;
import com.kuaima.app.domain.jobcategory.entity.JobIndustry;
import com.kuaima.app.domain.points.entity.PointsAccount;
import com.kuaima.app.domain.points.entity.PointsFlow;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.points.repository.PointsFlowRepository;
import com.kuaima.app.domain.coupon.entity.Coupon;
import com.kuaima.app.domain.coupon.entity.UserCoupon;
import com.kuaima.app.domain.coupon.repository.CouponRepository;
import com.kuaima.app.domain.coupon.repository.AdminCouponRecordRow;
import com.kuaima.app.domain.coupon.repository.UserCouponRepository;
import com.kuaima.app.domain.reward.entity.RewardAccount;
import com.kuaima.app.domain.reward.entity.RewardFlow;
import com.kuaima.app.domain.reward.repository.RewardAccountRepository;
import com.kuaima.app.domain.reward.repository.RewardFlowRepository;
import com.kuaima.app.domain.user.constant.CertificationStatus;
import com.kuaima.app.domain.user.constant.EnterpriseCode;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.entity.Wallet;
import com.kuaima.app.domain.wallet.repository.WalletRespository;
import com.kuaima.app.domain.wallet.repository.WalletFlowRespository;
import com.kuaima.app.security.model.LoginUser;
import com.kuaima.app.websocket.WebSocketSessionManager;

import jakarta.persistence.EntityNotFoundException;

/**
 * 后台用户管理（零工列表 / 雇主列表 / 冻结解冻）
 */
@RestController
@RequestMapping("/admin/users")
@Tag(name = "后台-用户", description = "用户列表与统计")
public class AdminUserController {

    private final UserRepository userRepository;
    private final BaseOrderItemRespository orderItemRepository;
    private final BossOrderRespository bossOrderRepository;
    private final JobCategoryRepository jobCategoryRepository;
    private final JobIndustryRepository jobIndustryRepository;
    private final WalletRespository walletRepository;
    private final PointsAccountRepository pointsAccountRepository;
    private final RewardAccountRepository rewardAccountRepository;
    private final PointsFlowRepository pointsFlowRepository;
    private final RewardFlowRepository rewardFlowRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final WalletFlowRespository walletFlowRepository;
    private final WebSocketSessionManager webSocketSessionManager;
    private final com.kuaima.app.admin.repository.CertificationRepository certificationRepository;

    public AdminUserController(UserRepository userRepository,
                               BaseOrderItemRespository orderItemRepository,
                               BossOrderRespository bossOrderRepository,
                               WalletRespository walletRepository,
                               PointsAccountRepository pointsAccountRepository,
                               RewardAccountRepository rewardAccountRepository,
                               PointsFlowRepository pointsFlowRepository,
                               RewardFlowRepository rewardFlowRepository,
                               UserCouponRepository userCouponRepository,
                               CouponRepository couponRepository) {
        this(userRepository, orderItemRepository, bossOrderRepository, walletRepository, pointsAccountRepository,
                rewardAccountRepository, pointsFlowRepository, rewardFlowRepository, userCouponRepository, couponRepository, null, null, null, null, null);
    }

    public AdminUserController(UserRepository userRepository,
                               BaseOrderItemRespository orderItemRepository,
                               BossOrderRespository bossOrderRepository,
                               WalletRespository walletRepository,
                               PointsAccountRepository pointsAccountRepository,
                               RewardAccountRepository rewardAccountRepository,
                               PointsFlowRepository pointsFlowRepository,
                               RewardFlowRepository rewardFlowRepository,
                               UserCouponRepository userCouponRepository,
                               CouponRepository couponRepository,
                               WalletFlowRespository walletFlowRepository,
                               WebSocketSessionManager webSocketSessionManager) {
        this(userRepository, orderItemRepository, bossOrderRepository, walletRepository, pointsAccountRepository,
                rewardAccountRepository, pointsFlowRepository, rewardFlowRepository, userCouponRepository, couponRepository,
                walletFlowRepository, webSocketSessionManager, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminUserController(UserRepository userRepository,
                               BaseOrderItemRespository orderItemRepository,
                               BossOrderRespository bossOrderRepository,
                               WalletRespository walletRepository,
                               PointsAccountRepository pointsAccountRepository,
                               RewardAccountRepository rewardAccountRepository,
                               PointsFlowRepository pointsFlowRepository,
                               RewardFlowRepository rewardFlowRepository,
                               UserCouponRepository userCouponRepository,
                               CouponRepository couponRepository,
                               WalletFlowRespository walletFlowRepository,
                               WebSocketSessionManager webSocketSessionManager,
                               JobCategoryRepository jobCategoryRepository,
                               JobIndustryRepository jobIndustryRepository,
                               com.kuaima.app.admin.repository.CertificationRepository certificationRepository) {
        this.userRepository = userRepository;
        this.orderItemRepository = orderItemRepository;
        this.bossOrderRepository = bossOrderRepository;
        this.walletRepository = walletRepository;
        this.pointsAccountRepository = pointsAccountRepository;
        this.rewardAccountRepository = rewardAccountRepository;
        this.pointsFlowRepository = pointsFlowRepository;
        this.rewardFlowRepository = rewardFlowRepository;
        this.userCouponRepository = userCouponRepository;
        this.couponRepository = couponRepository;
        this.walletFlowRepository = walletFlowRepository;
        this.webSocketSessionManager = webSocketSessionManager;
        this.jobCategoryRepository = jobCategoryRepository;
        this.jobIndustryRepository = jobIndustryRepository;
        this.certificationRepository = certificationRepository;
    }

    /** 零工列表（附加 completedOrders 已完成订单数） */
    @Operation(summary = "零工列表分页", description = "参数：status(正常/冻结)、keyword(昵称/手机号模糊)、page(默认 0)、size(默认 10)。返回 User，不含 password，附加 completedOrders")
    @GetMapping("/workers")
    public Result<Page<JSONObject>> workers(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            Authentication authentication) {
        requireAdmin(authentication);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        String kw = keyword != null && !keyword.isBlank() ? keyword : null;
        Page<User> result;
        if ("在线".equals(status) || "离线".equals(status)) {
            Set<Long> onlineIds = webSocketSessionManager == null
                    ? Set.of() : webSocketSessionManager.getOnlineWorkerIds();
            Set<Long> targetIds = new java.util.HashSet<>(onlineIds);
            if ("离线".equals(status)) {
                targetIds = new java.util.HashSet<>(userRepository.findWorkerIdentityIds());
                targetIds.removeAll(onlineIds);
            }
            if (targetIds.isEmpty()) {
                result = new PageImpl<>(List.of(), pageable, 0);
            } else {
                result = userRepository.searchWorkersByIds(UserRole.USER, new ArrayList<>(targetIds), kw, startDate, endDate, pageable);
            }
        } else {
            result = userRepository.searchWorkers(UserRole.USER, status, kw, startDate, endDate, pageable);
        }

        // 批量统计零工已完成订单数
        Set<Long> userIds = result.stream().map(User::getId).collect(Collectors.toSet());
        Map<Long, Long> completedMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            orderItemRepository.countCompletedByUserIds(userIds)
                    .forEach(row -> completedMap.put((Long) row[0], (Long) row[1]));
        }

        Map<Long, List<com.kuaima.app.domain.boss.entity.BaseOrderItem>> itemsByUser = new HashMap<>();
        Map<Long, Long> incomeMap = new HashMap<>();
        Map<Long, BigDecimal> rewardBalanceMap = new HashMap<>();
        Map<Long, Long> pointsBalanceMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            orderItemRepository.findByUserIdIn(userIds)
                    .forEach(item -> itemsByUser.computeIfAbsent(item.getUserId(), ignored -> new ArrayList<>()).add(item));
            if (walletFlowRepository != null) {
                walletFlowRepository.sumIncomeByUserIds(userIds)
                        .forEach(row -> incomeMap.put((Long) row[0], ((Number) row[1]).longValue()));
            }
            rewardAccountRepository.findByUserIdIn(userIds)
                    .forEach(account -> {
                        if (UserRole.USER.equals(account.getRole())) {
                            rewardBalanceMap.put(account.getUserId(), moneyValue(account.getBalance()));
                        } else {
                            rewardBalanceMap.putIfAbsent(account.getUserId(), moneyValue(account.getBalance()));
                        }
                    });
            pointsAccountRepository.findByUserIdInAndRole(userIds, UserRole.USER)
                    .forEach(account -> pointsBalanceMap.merge(account.getUserId(), (long) integerValue(account.getBalance()), Math::max));
        }

        Page<JSONObject> views = result.map(u -> {
            JSONObject obj = (JSONObject) JSON.toJSON(u);
            removeSensitive(obj);
            // 管理端零工列表沿用 creditScore 字段展示信用分，零工实际维护的是星级分 starScore。
            obj.put("creditScore", integerValue(u.getStarScore()));
            obj.put("completedOrders", completedMap.getOrDefault(u.getId(), 0L));
            putWorkerStats(obj, itemsByUser.getOrDefault(u.getId(), Collections.emptyList()),
                    incomeMap.getOrDefault(u.getId(), 0L), rewardBalanceMap.getOrDefault(u.getId(), BigDecimal.ZERO),
                    pointsBalanceMap.getOrDefault(u.getId(), 0L));
            return obj;
        });
        return Result.success(views, page, result.getTotalElements());
    }

    @Operation(summary = "零工管理统计", description = "返回零工总人数、本月新增、当前在线和已冻结人数")
    @GetMapping("/workers/stats")
    public Result<Map<String, Object>> workerStats(Authentication authentication) {
        requireAdmin(authentication);
        YearMonth month = YearMonth.now();
        Map<String, Object> stats = new LinkedHashMap<>();
        long total = userRepository.countByRole(UserRole.USER);
        long monthNew = userRepository.countCurrentMonthByRole(UserRole.USER);
        long online = webSocketSessionManager == null ? 0L
                : (long) webSocketSessionManager.getOnlineWorkerIds().size();
        long frozen = userRepository.countByRoleAndStatus(UserRole.USER, "冻结");
        stats.put("total", total);
        stats.put("monthNew", monthNew);
        stats.put("online", online);
        stats.put("frozen", frozen);

        // 仅返回有可靠历史基准的数据；前端不得自行填充静态百分比。
        Map<String, Object> changes = new LinkedHashMap<>();
        long previousTotal = userRepository.countByRoleAndDateLessThanEqual(
                UserRole.USER, month.minusMonths(1).atEndOfMonth());
        long previousMonthNew = userRepository.countByRoleAndDateBetween(
                UserRole.USER, month.minusMonths(1).atDay(1), month.minusMonths(1).atEndOfMonth());
        changes.put("total", change(previousTotal, total, "较上月"));
        changes.put("monthNew", change(previousMonthNew, monthNew, "较上月"));
        changes.put("online", null);
        changes.put("frozen", null);
        stats.put("changes", changes);
        return Result.success(stats);
    }

    private Map<String, Object> change(long previous, long current, String label) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("label", label);
        value.put("value", previous == 0 ? null : Math.round((current - previous) * 10000.0 / previous) / 100.0);
        return value;
    }

    /** 雇主列表（附加经营与资产字段，使用 fastjson 序列化确保 password 不泄露） */
    public Result<Page<JSONObject>> bosses(String status, String enterpriseStatus, String industry,
                                            String keyword, int page, int size) {
        return bosses(status, enterpriseStatus, industry, null, keyword, page, size);
    }

    @Operation(summary = "雇主列表分页", description = "参数：status(正常/冻结)、enterpriseStatus、jobType(发布岗位工种)、keyword、page、size。返回公司信息、招工数、信用分、余额、奖励金和老板身份积分")
    @GetMapping("/bosses")
    public Result<Page<JSONObject>> bosses(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String enterpriseStatus,
                                     @RequestParam(required = false) String industry,
                                     @RequestParam(required = false) String jobType,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        String kw = keyword != null && !keyword.isBlank() ? keyword : null;
        String es = enterpriseStatus != null && !enterpriseStatus.isBlank() ? enterpriseStatus : null;
        String industryCondition = industry != null && !industry.isBlank() && !"全部".equals(industry) ? industry.trim() : null;
        String requestedIndustry = industryCondition != null ? industryCondition
                : (jobType != null && !jobType.isBlank() ? jobType.trim() : null);
        Page<User> result;
        if (requestedIndustry != null) {
            List<Long> ownerIds = bossOrderRepository.findOwnerIdsByIndustry(requestedIndustry);
            result = ownerIds.isEmpty() ? new PageImpl<>(List.of(), pageable, 0)
                    : userRepository.searchBossesByIds(ownerIds, status, es, kw, pageable);
        } else {
            result = userRepository.searchBosses(UserRole.BOSS, status, es, industryCondition, kw, pageable);
        }

        Set<Long> bossIds = result.stream().map(User::getId).collect(Collectors.toSet());
        Map<Long, Long> jobsMap = new HashMap<>();
        Map<Long, BigDecimal> walletMap = new HashMap<>();
        Map<Long, Long> pointsMap = new HashMap<>();
        Map<Long, BigDecimal> rewardMap = new HashMap<>();
        Map<Long, String> jobTypeMap = new HashMap<>();
        Map<Long, String> industryMap = new HashMap<>();
        if (!bossIds.isEmpty()) {
            bossOrderRepository.countByCreateByIds(bossIds)
                    .forEach(row -> jobsMap.put((Long) row[0], (Long) row[1]));
            walletRepository.findByUserIdIn(bossIds)
                    .forEach(wallet -> walletMap.put(wallet.getUserId(), moneyValue(wallet.getBalance())));
            pointsAccountRepository.findByUserIdInAndRole(bossIds, UserRole.BOSS)
                    .forEach(account -> pointsMap.put(account.getUserId(),
                            account.getBalance() == null ? 0L : account.getBalance().longValue()));
            rewardAccountRepository.findByUserIdIn(bossIds)
                    .forEach(account -> rewardMap.put(account.getUserId(), moneyValue(account.getBalance())));
            List<BossOrder> orders = bossOrderRepository.findByCreateByInOrderByIdDesc(bossIds);
            if (jobIndustryRepository != null) {
                Map<Long, String> industryNames = new HashMap<>();
                Set<Long> industryIds = orders.stream().map(BossOrder::getIndustryId)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
                jobIndustryRepository.findAllById(industryIds)
                        .forEach(item -> industryNames.put(item.getId(), item.getName()));
                for (BossOrder order : orders) {
                    String name = industryNames.get(order.getIndustryId());
                    if (name != null && !name.isBlank()) industryMap.putIfAbsent(order.getCreateBy(), name);
                }
            }
            if (jobCategoryRepository != null) {
                Map<Long, String> categoryNames = new HashMap<>();
                Set<Long> categoryIds = orders.stream()
                        .flatMap(order -> jobCategoryIds(order).stream())
                        .collect(Collectors.toSet());
                jobCategoryRepository.findAllById(categoryIds)
                        .forEach(category -> categoryNames.put(category.getId(), category.getName()));
                for (BossOrder order : orders) {
                    String names = jobCategoryIds(order).stream()
                            .map(categoryNames::get)
                            .filter(name -> name != null && !name.isBlank())
                            .distinct()
                            .collect(Collectors.joining("、"));
                    if (names.isBlank()) names = order.getPostion();
                    if (names != null && !names.isBlank()) jobTypeMap.putIfAbsent(order.getCreateBy(), names);
                }
            }
        }

        Page<JSONObject> views = result.map(u -> {
            JSONObject obj = (JSONObject) JSON.toJSON(u);
            // 企业字段属于老板列表固定契约；未认证用户也必须明确返回 null，不能由序列化器省略。
            obj.put("companyCode", u.getCompanyCode());
            obj.put("companyName", u.getCompanyName());
            obj.put("jobType", jobTypeMap.getOrDefault(u.getId(), ""));
            obj.put("industry", industryMap.getOrDefault(u.getId(), ""));
            obj.put("jobsCount", jobsMap.getOrDefault(u.getId(), 0L));
            obj.put("creditScore", integerValue(u.getCreditScore()));
            obj.put("balance", walletMap.getOrDefault(u.getId(), BigDecimal.ZERO));
            obj.put("rewardAmount", rewardMap.getOrDefault(u.getId(), BigDecimal.ZERO));
            obj.put("points", pointsMap.getOrDefault(u.getId(), 0L));
            return obj;
        });
        return Result.success(views, page, result.getTotalElements());
    }

    /** 用户详情 */
    @Operation(summary = "用户详情", description = "按 id 查询用户完整信息，用户不存在返回 404")
    @GetMapping("/{id}")
    public Result<JSONObject> get(@PathVariable Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + id));
        JSONObject obj = (JSONObject) JSON.toJSON(u);
        obj.remove("password");
        obj.put("companyCode", u.getCompanyCode());
        obj.put("companyName", u.getCompanyName());
        if (UserRole.isBossIdentity(u)) {
            bossOrderRepository.countByCreateByIds(Set.of(id))
                    .forEach(row -> obj.put("jobsCount", (Long) row[1]));
            // 资产账户按业务身份隔离；不能按 userId 单独查询，否则历史上同一用户存在多角色账户时
            // Optional 查询会因多行结果直接抛出 NonUniqueResultException。
            obj.put("balance", walletRepository.findFirstByUserIdAndRoleOrderByIdDesc(id, UserRole.BOSS)
                    .or(() -> walletRepository.findFirstByUserIdOrderByIdDesc(id))
                    .map(wallet -> moneyValue(wallet.getBalance())).orElse(BigDecimal.ZERO));
            obj.put("points", pointsAccountRepository.findFirstByUserIdAndRoleOrderByIdDesc(id, UserRole.BOSS)
                    .map(account -> integerValue(account.getBalance())).orElse(0));
            obj.put("rewardAmount", rewardAccountRepository.findFirstByUserIdAndRoleOrderByIdDesc(id, UserRole.BOSS)
                    .or(() -> rewardAccountRepository.findFirstByUserIdOrderByIdDesc(id))
                    .map(account -> moneyValue(account.getBalance())).orElse(BigDecimal.ZERO));
            obj.put("pointRecords", pointRecords(id));
            obj.put("rewardRecords", rewardRecords(id));
            BigDecimal rewardIncome = rewardFlowRepository.sumByUserIdAndType(id, "INCOME");
            obj.put("rewardIncome", moneyValue(rewardIncome));
            obj.put("rewardUsed", moneyValue(rewardIncome).subtract(moneyValue((BigDecimal) obj.get("rewardAmount")).max(BigDecimal.ZERO)));
            obj.put("coupons", coupons(id));
        }
        // 附加完成订单数
        Map<Long, Long> completedMap = new HashMap<>();
        Set<Long> uid = Set.of(id);
        orderItemRepository.countCompletedByUserIds(uid)
                .forEach(row -> completedMap.put((Long) row[0], (Long) row[1]));
        obj.put("completedOrders", completedMap.getOrDefault(id, 0L));
        return Result.success(obj);
    }

    /** 零工详情概览，金额单位为分，百分比为数字。 */
    @Operation(summary = "零工详情概览", description = "返回零工资料、账户余额、订单履约统计和技能明细")
    @GetMapping("/{id}/overview")
    public Result<JSONObject> workerOverview(@PathVariable Long id, Authentication authentication) {
        requireAdmin(authentication);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + id));
        if (!UserRole.isWorkerIdentity(user)) throw new ForbiddenBusinessException("目标用户不是零工账号");
        JSONObject result = new JSONObject();
        result.put("id", id); result.put("realName", text(user.getRealName())); result.put("nickname", text(user.getNickname()));
        result.put("username", text(user.getUsername())); result.put("phone", text(user.getPhone())); result.put("avatar", text(user.getAvatar()));
        result.put("gender", text(user.getGender())); result.put("age", integerValue(user.getAge())); result.put("city", text(user.getCity()));
        result.put("skills", skillList(user.getSkills())); result.put("realnameStatus", text(user.getRealnameStatus())); result.put("status", text(user.getStatus()));
        result.put("createdAt", user.getDate() == null ? "" : user.getDate().toString()); result.put("lastLoginAt", "");
        result.put("creditScore", integerValue(user.getCreditScore()));
        // 详情页页头资产按 USER 身份返回；reward_account 金额单位为元，积分为整数。
        result.put("rewardBalance", rewardAccountRepository.findFirstByUserIdAndRoleOrderByIdDesc(id, UserRole.USER)
                .or(() -> rewardAccountRepository.findFirstByUserIdOrderByIdDesc(id))
                .map(account -> moneyValue(account.getBalance())).orElse(BigDecimal.ZERO));
        result.put("pointsBalance", pointsAccountRepository.findFirstByUserIdAndRoleOrderByIdDesc(id, UserRole.USER)
                .map(account -> (long) integerValue(account.getBalance())).orElse(0L));
        putWorkerStats(result, id);
        result.put("skillDetails", skillDetails(user.getSkills()));
        return Result.success(result);
    }

    @Operation(summary = "零工积分流水", description = "查询零工 USER 身份积分流水及余额汇总")
    @GetMapping("/{id}/points/flows")
    public Result<Map<String, Object>> workerPointFlows(@PathVariable Long id,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         Authentication authentication) {
        requireAdmin(authentication);
        requireWorker(id);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize(size), Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<PointsFlow> source = pointsFlowRepository.findByUserIdAndRoleOrderByTimestampDesc(id, UserRole.USER, pageable);
        Page<JSONObject> records = source.map(this::workerPointRecord);
        long balance = pointsAccountRepository.findFirstByUserIdAndRoleOrderByIdDesc(id, UserRole.USER).map(a -> (long) integerValue(a.getBalance())).orElse(0L);
        Map<String, Object> data = new LinkedHashMap<>(); data.put("records", records.getContent()); data.put("currentBalance", balance);
        data.put("totalEarned", sumPoints(id, true)); data.put("totalConsumed", sumPoints(id, false));
        return Result.success(data, records.getNumber(), records.getTotalElements());
    }

    @Operation(summary = "零工奖励金流水", description = "查询零工奖励金流水及余额汇总")
    @GetMapping("/{id}/reward/flows")
    public Result<Map<String, Object>> workerRewardFlows(@PathVariable Long id,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          Authentication authentication) {
        requireAdmin(authentication);
        requireWorker(id);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize(size));
        Page<RewardFlow> source = rewardFlowRepository.findByUserIdOrderByCreatedAtDescIdDesc(id, pageable);
        Page<JSONObject> records = source.map(this::workerRewardRecord);
        BigDecimal balance = rewardAccountRepository.findFirstByUserIdAndRoleOrderByIdDesc(id, UserRole.USER)
                .or(() -> rewardAccountRepository.findFirstByUserIdOrderByIdDesc(id))
                .map(a -> moneyValue(a.getBalance())).orElse(BigDecimal.ZERO);
        Map<String, Object> data = new LinkedHashMap<>(); data.put("records", records.getContent()); data.put("currentBalance", balance);
        data.put("totalEarned", moneyValue(rewardFlowRepository.sumByUserIdAndType(id, "INCOME")));
        data.put("totalConsumed", moneyValue(rewardFlowRepository.sumByUserIdAndType(id, "EXPENSE")));
        return Result.success(data, records.getNumber(), records.getTotalElements());
    }

    /** 老板详情-老板身份积分明细分页。 */
    @Operation(summary = "老板积分明细分页", description = "按用户 + BOSS 身份查询积分流水；type 支持 ALL、PURCHASE、ADMIN_PURCHASE、CONSUME、GIFT、RECEIVED、EXPIRED、REWARD_EXCHANGE")
    @GetMapping("/{id}/points")
    public Result<Page<JSONObject>> pointRecords(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "ALL") String type,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "5") int size) {
        requireBoss(id);
        String normalizedType = normalizeType(type, Set.of("ALL", "PURCHASE", "ADMIN_PURCHASE", "CONSUME",
                "GIFT", "RECEIVED", "EXPIRED", "REWARD_EXCHANGE"), "type");
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize(size), Sort.by(Sort.Direction.DESC, "id"));
        Page<JSONObject> result = pointsFlowRepository
                .findByUserIdAndRoleAndType(id, UserRole.BOSS, normalizedType, pageable)
                .map(this::pointRecord);
        return Result.success(result, result.getNumber(), result.getTotalElements());
    }

    /** 老板详情-奖励金明细分页。 */
    @Operation(summary = "老板奖励金明细分页", description = "按用户查询奖励金流水；type 支持 ALL、INCOME、EXPENSE")
    @GetMapping("/{id}/rewards")
    public Result<Page<JSONObject>> rewardRecords(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "ALL") String type,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "5") int size) {
        requireBoss(id);
        String normalizedType = normalizeType(type, Set.of("ALL", "INCOME", "EXPENSE"), "type");
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize(size), Sort.by(Sort.Direction.DESC, "id"));
        Page<RewardFlow> records = "ALL".equals(normalizedType)
                ? rewardFlowRepository.findByUserIdOrderByCreatedAtDescIdDesc(id, pageable)
                : rewardFlowRepository.findByUserIdAndTypeOrderByCreatedAtDescIdDesc(id, normalizedType, pageable);
        Page<JSONObject> result = records.map(this::rewardRecord);
        return Result.success(result, result.getNumber(), result.getTotalElements());
    }

    /** 老板详情-优惠券分页。 */
    @Operation(summary = "老板优惠券分页", description = "status 支持 ALL、AVAILABLE、HISTORY；AVAILABLE 为未使用且未过期，HISTORY 为已使用、已过期或非未使用")
    @GetMapping("/{id}/coupons")
    public Result<Page<JSONObject>> couponRecords(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "ALL") String status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "5") int size) {
        String normalizedStatus = normalizeType(status, Set.of("ALL", "AVAILABLE", "HISTORY"), "status");
        int safePage = Math.max(page, 0);
        int safePageSize = safeSize(size);
        java.sql.Date today = java.sql.Date.valueOf(LocalDate.now());
        List<AdminCouponRecordRow> rows = userCouponRepository.findAdminCouponRecords(id, normalizedStatus, today,
                safePageSize, Math.multiplyExact(safePage, safePageSize));
        if (rows.isEmpty()) {
            requireBoss(id);
        }
        long total = rows.isEmpty() ? 0L : rows.get(0).getTotalCount();
        List<JSONObject> views = rows.stream().map(this::couponRecord).toList();
        Page<JSONObject> result = new PageImpl<>(views, PageRequest.of(safePage, safePageSize), total);
        return Result.success(result, result.getNumber(), result.getTotalElements());
    }

    private JSONObject couponRecord(AdminCouponRecordRow row) {
        JSONObject item = new JSONObject();
        item.put("id", row.getId());
        String status = row.getStatus();
        if ("UNUSED".equals(status) && row.getExpireAt() != null
                && row.getExpireAt().isBefore(LocalDate.now())) status = "EXPIRED";
        item.put("status", status); item.put("expireAt", row.getExpireAt()); item.put("usedAt", row.getUsedAt());
        item.put("useOrderId", row.getUseOrderId()); item.put("title", row.getTitle()); item.put("type", row.getType());
        item.put("amount", row.getAmount()); item.put("minSpend", row.getMinSpend());
        item.put("discount", row.getDiscount()); item.put("cap", row.getCap());
        return item;
    }

    private Long longValue(Long value) {
        return value == null ? 0L : value;
    }
    private BigDecimal moneyValue(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private String text(String value) { return value == null ? "" : value; }

    private void removeSensitive(JSONObject obj) {
        obj.remove("password"); obj.remove("idCard"); obj.remove("licenseNo"); obj.remove("legalRep");
    }

    private void requireWorker(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("用户不存在: " + id));
        if (!UserRole.isWorkerIdentity(user)) throw new ForbiddenBusinessException("目标用户不是零工账号");
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)
                || user.role() == null || !user.role().startsWith("ADMIN_")) {
            throw new ForbiddenBusinessException("仅管理员可操作");
        }
    }

    private List<String> skillList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split("[,，、]"))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private List<Long> jobCategoryIds(BossOrder order) {
        if (order == null) return List.of();
        List<Long> ids = new ArrayList<>();
        if (order.getJobIds() != null && !order.getJobIds().isBlank()) {
            for (String value : order.getJobIds().split(",")) {
                try { ids.add(Long.valueOf(value.trim())); } catch (NumberFormatException ignored) { }
            }
        }
        if (ids.isEmpty() && order.getJobCategoryId() != null) ids.add(order.getJobCategoryId());
        return ids.stream().distinct().toList();
    }

    private List<JSONObject> skillDetails(String value) {
        return skillList(value).stream().map(skill -> {
            JSONObject item = new JSONObject(); item.put("name", skill); item.put("verified", false); return item;
        }).toList();
    }

    private void putWorkerStats(JSONObject obj, Long userId) {
        List<com.kuaima.app.domain.boss.entity.BaseOrderItem> items = orderItemRepository.findByUserId(userId);
        long totalIncome = walletFlowRepository == null ? 0L : longValue(walletFlowRepository.sumIncomeByUserId(userId));
        BigDecimal rewardBalance = rewardAccountRepository.findFirstByUserIdAndRoleOrderByIdDesc(userId, UserRole.USER)
                .or(() -> rewardAccountRepository.findFirstByUserIdOrderByIdDesc(userId))
                .map(a -> moneyValue(a.getBalance())).orElse(BigDecimal.ZERO);
        long pointsBalance = pointsAccountRepository.findFirstByUserIdAndRoleOrderByIdDesc(userId, UserRole.USER)
                .map(a -> (long) integerValue(a.getBalance())).orElse(0L);
        putWorkerStats(obj, items, totalIncome, rewardBalance, pointsBalance);
    }

    private void putWorkerStats(JSONObject obj,
                                List<com.kuaima.app.domain.boss.entity.BaseOrderItem> items,
                                long totalIncome,
                                BigDecimal rewardBalance,
                                long pointsBalance) {
        long total = items.size();
        long completed = items.stream().filter(i -> "已完成".equals(i.getStatus())).count();
        long canceled = items.stream().filter(i -> "取消报名".equals(i.getStatus()) || "已取消".equals(i.getStatus())).count();
        long hired = items.stream().filter(i -> i.getHireDate() != null || Set.of("已录用", "已到岗", "工作中", "已完成", "待结算", "已结算").contains(i.getStatus())).count();
        long noShow = items.stream().filter(i -> i.getHireDate() != null && i.getWorkDate() == null
                && i.getOrderId() != null && i.getStatus() != null && !"已完成".equals(i.getStatus())).count();
        long early = items.stream().filter(i -> Boolean.TRUE.equals(i.getEarlyLeave())).count();
        int completion = percentage(completed, total), cancellation = percentage(canceled, total);
        int noShowRate = percentage(noShow, hired), earlyRate = percentage(early, hired);
        long monthCompleted = items.stream().filter(i -> "已完成".equals(i.getStatus()) && i.getFinishDate() != null
                && i.getFinishDate().toLocalDate().getYear() == LocalDate.now().getYear()
                && i.getFinishDate().toLocalDate().getMonthValue() == LocalDate.now().getMonthValue()).count();
        obj.put("rewardBalance", rewardBalance);
        obj.put("pointsBalance", pointsBalance);
        obj.put("completedOrders", completed); obj.put("monthCompletedOrders", monthCompleted); obj.put("totalIncome", totalIncome);
        obj.put("completionRate", completion); obj.put("cancellationRate", cancellation); obj.put("noShowRate", noShow); obj.put("earlyLeaveRate", earlyRate);
    }

    private int percentage(long numerator, long denominator) {
        return denominator <= 0 ? 0 : (int) Math.round(numerator * 100.0 / denominator);
    }

    private JSONObject workerPointRecord(PointsFlow flow) {
        JSONObject item = new JSONObject(); item.put("flowNo", flow.getBizNo() == null ? String.valueOf(flow.getId()) : flow.getBizNo());
        item.put("type", text(flow.getBizType())); item.put("changeAmount", flow.getDelta() == null ? 0 : flow.getDelta());
        item.put("balanceAfter", flow.getBalanceAfter() == null ? 0 : flow.getBalanceAfter()); item.put("remark", text(flow.getRemark()));
        item.put("createdAt", flow.getTimestamp() == null ? "" : flow.getTimestamp()); return item;
    }

    private JSONObject workerRewardRecord(RewardFlow flow) {
        JSONObject item = new JSONObject(); item.put("flowNo", flow.getSourceKey() == null ? String.valueOf(flow.getId()) : flow.getSourceKey());
        item.put("title", text(flow.getTitle())); item.put("remark", text(flow.getRemark())); item.put("amount", moneyValue(flow.getAmount()));
        item.put("direction", text(flow.getType())); item.put("createdAt", flow.getCreatedAt() == null ? "" : flow.getCreatedAt()); return item;
    }

    private long sumPoints(Long userId, boolean earned) {
        return pointsFlowRepository.findByUserIdAndRoleOrderByTimestampDesc(userId, UserRole.USER).stream()
                .mapToLong(f -> f.getDelta() == null ? 0 : f.getDelta()).filter(v -> earned ? v > 0 : v < 0)
                .map(v -> earned ? v : -v).sum();
    }

    private Integer integerValue(Integer value) {
        return value == null ? 0 : value;
    }

    private List<JSONObject> pointRecords(Long userId) {
        return pointsFlowRepository
                .findByUserIdAndRoleOrderByTimestampDesc(userId, UserRole.BOSS, PageRequest.of(0, 10))
                .stream().map(this::pointRecord).toList();
    }

    private JSONObject pointRecord(PointsFlow flow) {
        JSONObject item = new JSONObject();
        item.put("id", flow.getId());
        item.put("type", flow.getBizType());
        item.put("points", flow.getDelta());
        item.put("balanceAfter", flow.getBalanceAfter());
        item.put("time", flow.getTimestamp());
        item.put("remark", flow.getRemark());
        return item;
    }

    private List<JSONObject> rewardRecords(Long userId) {
        return rewardFlowRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, 10))
                .stream().map(this::rewardRecord).toList();
    }

    private JSONObject rewardRecord(RewardFlow flow) {
        JSONObject item = new JSONObject();
        item.put("id", flow.getId());
        item.put("type", flow.getType());
        item.put("amount", flow.getAmount());
        item.put("balanceAfter", flow.getBalanceAfter());
        item.put("title", flow.getTitle());
        item.put("remark", flow.getRemark());
        item.put("time", flow.getCreatedAt());
        return item;
    }

    private List<JSONObject> coupons(Long userId) {
        List<UserCoupon> records = userCouponRepository.findByUserId(userId);
        if (records.isEmpty()) return List.of();
        Map<Long, Coupon> coupons = couponRepository.findAllById(records.stream()
                .map(UserCoupon::getCouponId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Coupon::getId, coupon -> coupon));
        return records.stream().map(record -> couponRecord(record, coupons.get(record.getCouponId()))).toList();
    }

    private JSONObject couponRecord(UserCoupon record, Coupon coupon) {
        JSONObject item = new JSONObject();
        item.put("id", record.getId());
        String status = record.getStatus();
        if ("UNUSED".equals(status) && record.getExpireAt() != null
                && record.getExpireAt().toLocalDate().isBefore(LocalDate.now())) {
            status = "EXPIRED";
        }
        item.put("status", status);
        item.put("expireAt", record.getExpireAt());
        item.put("usedAt", record.getUsedAt());
        item.put("useOrderId", record.getUseOrderId());
        item.put("title", coupon == null ? "优惠券" : firstText(coupon.getTitle(), coupon.getName()));
        item.put("type", coupon == null ? null : coupon.getType());
        item.put("amount", coupon == null ? null : coupon.getAmount());
        item.put("minSpend", coupon == null ? null : coupon.getMinSpend());
        item.put("discount", coupon == null ? null : coupon.getDiscount());
        item.put("cap", coupon == null ? null : coupon.getCap());
        return item;
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private User requireBoss(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + id));
        if (!UserRole.isBossIdentity(user)) {
            throw new ForbiddenBusinessException("目标用户不是老板账号");
        }
        return user;
    }

    private String normalizeType(String value, Set<String> allowed, String fieldName) {
        String normalized = value == null ? "ALL" : value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(fieldName + " 参数无效");
        }
        return normalized;
    }

    private int safeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    /** 冻结 */
    @Operation(summary = "冻结用户", description = "将用户 status 置为「冻结」")
    @PutMapping("/{id}/freeze")
    public Result<User> freeze(@PathVariable Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + id));
        u.setStatus("冻结");
        return Result.success(userRepository.save(u));
    }

    /** 解冻 */
    @Operation(summary = "解冻用户", description = "将用户 status 置为「正常」")
    @PutMapping("/{id}/unfreeze")
    public Result<User> unfreeze(@PathVariable Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + id));
        u.setStatus("正常");
        return Result.success(userRepository.save(u));
    }

    /** 批量冻结 */
    @Operation(summary = "批量冻结用户", description = "按 ids 数组批量将用户 status 置为「冻结」")
    @PutMapping("/freeze/batch")
    public Result<Void> freezeBatch(@RequestParam java.util.List<Long> ids) {
        for (Long id : ids) {
            userRepository.findById(id).ifPresent(u -> {
                u.setStatus("冻结");
                userRepository.save(u);
            });
        }
        return Result.success();
    }

    /** 批量解冻 */
    @Operation(summary = "批量解冻用户", description = "按 ids 数组批量将用户 status 置为「正常」")
    @PutMapping("/unfreeze/batch")
    public Result<Void> unfreezeBatch(@RequestParam java.util.List<Long> ids) {
        for (Long id : ids) {
            userRepository.findById(id).ifPresent(u -> {
                u.setStatus("正常");
                userRepository.save(u);
            });
        }
        return Result.success();
    }

    /** 企业认证审核通过 */
    @Operation(summary = "企业认证审核通过", description = "将雇主 enterpriseStatus 置为 APPROVED、certStatus 置为「已通过」")
    @PutMapping("/{id}/enterprise/pass")
    public Result<User> enterprisePass(@PathVariable Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + id));
        u.setEnterpriseStatus(CertificationStatus.APPROVED);
        u.setCertStatus("已通过");
        u.setCertType("ENTERPRISE");
        com.kuaima.app.domain.user.constant.UserBusinessCode.ensureBoss(u);
        EnterpriseCode.ensure(u);
        userRepository.save(u);
        syncEnterpriseRecord(u.getId(), "已通过", null);
        return Result.success(u);
    }

    /** 用户维度审核后，同步最新企业认证记录，保证认证列表状态一致。 */
    private void syncEnterpriseRecord(Long userId, String status, String reason) {
        certificationRepository.findTopByUserIdAndTypeOrderByIdDesc(userId, "ENTERPRISE")
                .ifPresent(record -> {
                    record.setStatus(status);
                    record.setRejectReason(reason);
                    record.setAuditTime(java.time.LocalDateTime.now());
                    certificationRepository.save(record);
                });
    }

    /** 企业认证审核拒绝 */
    @Operation(summary = "企业认证审核拒绝", description = "将雇主 enterpriseStatus 置为 REJECTED、certStatus 置为「已拒绝」")
    @PutMapping("/{id}/enterprise/reject")
    public Result<User> enterpriseReject(@PathVariable Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + id));
        u.setEnterpriseStatus(CertificationStatus.REJECTED);
        u.setCertStatus("已拒绝");
        u.setCertType("ENTERPRISE");
        userRepository.save(u);
        syncEnterpriseRecord(u.getId(), "已拒绝", "管理员审核拒绝");
        return Result.success(u);
    }
}
