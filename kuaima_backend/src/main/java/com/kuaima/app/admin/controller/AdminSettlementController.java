package com.kuaima.app.admin.controller;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.coupon.entity.Coupon;
import com.kuaima.app.domain.coupon.entity.UserCoupon;
import com.kuaima.app.domain.coupon.repository.CouponRepository;
import com.kuaima.app.domain.coupon.repository.UserCouponRepository;
import com.kuaima.app.domain.message.constant.BizType;
import com.kuaima.app.domain.message.constant.MessageType;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.wallet.constant.SettlementStatus;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.entity.Settlement;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;
import com.kuaima.app.domain.wallet.service.SettlementService;

/**
 * 后台结算管理（列出全部结算单、手动触发结算、批量结算）
 */
@RestController
@RequestMapping("/admin/settlements")
@Tag(name = "后台-结算", description = "结算审批与管理")
public class AdminSettlementController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SettlementRespository settlementRepository;
    private final SettlementService settlementService;
    private final UserRepository userRepository;
    private final BossOrderRespository orderRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final MessageService messageService;

    public AdminSettlementController(SettlementRespository settlementRepository,
                                    SettlementService settlementService,
                                    UserRepository userRepository,
                                    BossOrderRespository orderRepository,
                                    UserCouponRepository userCouponRepository,
                                    CouponRepository couponRepository) {
        this(settlementRepository, settlementService, userRepository, orderRepository,
                userCouponRepository, couponRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminSettlementController(SettlementRespository settlementRepository,
                                    SettlementService settlementService,
                                    UserRepository userRepository,
                                    BossOrderRespository orderRepository,
                                    UserCouponRepository userCouponRepository,
                                    CouponRepository couponRepository,
                                    MessageService messageService) {
        this.messageService = messageService;
        this.settlementRepository = settlementRepository;
        this.settlementService = settlementService;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.userCouponRepository = userCouponRepository;
        this.couponRepository = couponRepository;
    }

    /** 结算统计 */
    @Operation(summary = "结算管理统计", description = "返回待结算数、今日结算金额、已结算笔数、结算成功率和总数")
    public Result<Map<String, Object>> stats() {
        long pending = settlementRepository.countByStatus("待支付");
        long settled = settlementRepository.countByStatus("已支付");
        long failed = settlementRepository.countByStatus("已取消");
        long total = settlementRepository.count();
        LocalDateTime todayStart = LocalDate.now(ZONE).atStartOfDay();
        Long todayAmount = settlementRepository.sumWageByStatusAndPayTimeBetween(
                "已支付", todayStart, todayStart.plusDays(1));
        long finished = settled + failed;
        double successRate = finished == 0 ? 100.0 : settled * 100.0 / finished;
        Map<String, Object> data = new HashMap<>();
        data.put("pendingCount", pending);
        data.put("settledAmount", yuan(todayAmount));
        data.put("settledCount", settled);
        data.put("successRate", BigDecimal.valueOf(successRate).setScale(1, RoundingMode.HALF_UP));
        data.put("totalCount", total);
        return Result.success(data);
    }

    /** 结算单列表（关联雇主名、零工名、订单信息、优惠券抵扣） */
    @Operation(summary = "结算单列表分页", description = "支持状态、结算周期和日期范围过滤；附加雇主、零工、金额、优惠券抵扣等字段")
    @GetMapping
    public Result<Page<JSONObject>> list(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String cycle,
                                         @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
                                         @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Timestamp startTime = timestamp(startDate, cycle, 0);
        Timestamp endTime = timestamp(endDate, cycle, 1);
        Page<Settlement> result = settlementRepository.search(rawStatus(status), startTime, endTime, pageable);

        // 批量查询关联订单
        Set<Long> orderIds = result.stream().map(Settlement::getOrderId).filter(id -> id != null && id > 0).collect(Collectors.toSet());
        Map<Long, BossOrder> orderMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            orderRepository.findAllById(orderIds).forEach(o -> orderMap.put(o.getId(), o));
        }

        // 批量查询关联用户（零工 + 雇主）
        Set<Long> userIds = result.stream().map(Settlement::getWorkerId).filter(id -> id != null && id > 0).collect(Collectors.toSet());
        Set<Long> employerIds = orderMap.values().stream().map(BossOrder::getCreateBy).filter(id -> id != null && id > 0).collect(Collectors.toSet());
        userIds.addAll(employerIds);
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(u -> userMap.put(u.getId(), u));
        }
        Map<Long, UserCoupon> couponRecordMap = couponRecords(orderIds);
        Map<Long, Coupon> couponMap = couponDefinitions(couponRecordMap.values());

        Page<JSONObject> views = result.map(s -> {
            JSONObject obj = (JSONObject) JSON.toJSON(s);

            obj.put("amount", s.getWage() != null ? s.getWage() : java.math.BigDecimal.ZERO);
            obj.put("platformFee", s.getServiceFee() != null ? s.getServiceFee() : java.math.BigDecimal.ZERO);
            obj.put("actualAmount", s.getTotalAmount() != null ? s.getTotalAmount() : java.math.BigDecimal.ZERO);

            // 状态映射
            obj.put("status", mapStatus(s.getStatus()));
            obj.put("time", s.getPayTime() != null ? s.getPayTime().toString() : null);
            obj.put("payMethod", s.getPayNo() == null ? "-" : "微信支付");

            // 关联订单信息
            BossOrder order = orderMap.get(s.getOrderId());
            if (order != null) {
                obj.put("jobTitle", order.getOrderTitle());
                obj.put("postion", order.getPostion());
                obj.put("orderNum", order.getOrderNum());
                User employer = userMap.get(order.getCreateBy());
                if (employer != null) {
                    obj.put("employerName",
                            (employer.getCompanyName() != null && !employer.getCompanyName().isBlank())
                                    ? employer.getCompanyName()
                                    : (employer.getNickname() != null ? employer.getNickname() : employer.getUsername()));
                }
                UserCoupon couponRecord = couponRecordMap.get(order.getId());
                if (couponRecord != null) {
                    Coupon coupon = couponMap.get(couponRecord.getCouponId());
                    if (coupon != null) {
                        obj.put("couponName", firstText(coupon.getTitle(), coupon.getName()));
                        obj.put("couponAmount", couponAmount(coupon, order));
                    }
                }
            }
            obj.put("couponAmount", obj.containsKey("couponAmount") ? obj.get("couponAmount") : 0);

            // 零工名称
            User worker = userMap.get(s.getWorkerId());
            if (worker != null) {
                obj.put("workerName",
                        (worker.getNickname() != null && !worker.getNickname().isBlank())
                                ? worker.getNickname()
                                : worker.getUsername());
            }

            return obj;
        });

        return Result.success(views, page, result.getTotalElements());
    }

    /** GET /admin/settlements/stats 的显式路由，避免与 /{id} 详情冲突。 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> statsEndpoint() {
        return stats();
    }

    private String rawStatus(String displayStatus) {
        if (displayStatus == null || displayStatus.isBlank()) return null;
        return switch (displayStatus) {
            case "待结算" -> "待支付";
            case "已结算" -> "已支付";
            case "结算失败" -> "已取消";
            default -> displayStatus;
        };
    }

    private Timestamp timestamp(LocalDate date, String cycle, int boundary) {
        LocalDate value = date;
        if (value == null && cycle != null && !cycle.isBlank()) {
            value = switch (cycle) {
                case "today" -> LocalDate.now(ZONE);
                case "week" -> LocalDate.now(ZONE).minusDays(LocalDate.now(ZONE).getDayOfWeek().getValue() - 1L);
                case "month" -> LocalDate.now(ZONE).withDayOfMonth(1);
                default -> null;
            };
        }
        if (value == null) return null;
        if (boundary == 0) return Timestamp.valueOf(value.atStartOfDay());
        LocalDate end = value.plusDays(1);
        if (date == null && "week".equals(cycle)) end = value.plusWeeks(1);
        if (date == null && "month".equals(cycle)) end = value.plusMonths(1);
        return Timestamp.valueOf(end.atStartOfDay());
    }

    private Map<Long, UserCoupon> couponRecords(Set<Long> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        return userCouponRepository.findByUseOrderIdIn(orderIds).stream()
                .filter(record -> record.getUseOrderId() != null)
                .collect(Collectors.toMap(UserCoupon::getUseOrderId, record -> record, (first, second) -> first));
    }

    private Map<Long, Coupon> couponDefinitions(java.util.Collection<UserCoupon> records) {
        Set<Long> ids = records.stream().map(UserCoupon::getCouponId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return couponRepository.findAllById(ids).stream().collect(Collectors.toMap(Coupon::getId, coupon -> coupon));
    }

    private BigDecimal couponAmount(Coupon coupon, BossOrder order) {
        if ("DISCOUNT".equalsIgnoreCase(coupon.getType())) {
            BigDecimal base = order.getSalary() == null ? BigDecimal.ZERO : order.getSalary()
                    .multiply(BigDecimal.valueOf(order.getDuration() == null ? 0 : order.getDuration()))
                    .multiply(BigDecimal.valueOf(order.getOrderNum() == null ? 0 : order.getOrderNum()));
            BigDecimal discount = coupon.getDiscount() == null ? BigDecimal.TEN : coupon.getDiscount();
            BigDecimal amount = base.multiply(BigDecimal.ONE.subtract(discount.divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP)));
            if (coupon.getCap() != null && amount.compareTo(coupon.getCap()) > 0) amount = coupon.getCap();
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        return coupon.getAmount() == null ? BigDecimal.ZERO : coupon.getAmount().setScale(2, RoundingMode.HALF_UP);
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private BigDecimal yuan(Long amount) {
        return BigDecimal.valueOf(amount == null ? 0L : amount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** 后端状态 → 前端展示状态 */
    private String mapStatus(String s) {
        if (s == null) return null;
        return switch (s) {
            case "待支付" -> "待结算";
            case "已支付" -> "已结算";
            case "已取消" -> "结算失败";
            default -> s;
        };
    }

    /** 结算单详情 */
    @Operation(summary = "结算单详情", description = "按 id 查询 Settlement 完整信息，附加雇主/零工/订单名称")
    @GetMapping("/{id}")
    public Result<JSONObject> get(@PathVariable Long id) {
        Settlement s = settlementRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("结算单不存在: " + id));
        JSONObject obj = (JSONObject) JSON.toJSON(s);
        obj.put("amount", s.getWage() != null ? s.getWage() : java.math.BigDecimal.ZERO);
        obj.put("platformFee", s.getServiceFee() != null ? s.getServiceFee() : java.math.BigDecimal.ZERO);
        obj.put("actualAmount", s.getTotalAmount() != null ? s.getTotalAmount() : java.math.BigDecimal.ZERO);
        obj.put("time", s.getPayTime() != null ? s.getPayTime().toString() : null);
        obj.put("status", mapStatus(s.getStatus()));

        // 关联订单
        if (s.getOrderId() != null) {
            orderRepository.findById(s.getOrderId()).ifPresent(order -> {
                obj.put("jobTitle", order.getOrderTitle());
                obj.put("postion", order.getPostion());
                obj.put("orderNum", order.getOrderNum());
                if (order.getCreateBy() != null) {
                    userRepository.findById(order.getCreateBy()).ifPresent(employer -> {
                        obj.put("employerName",
                                (employer.getCompanyName() != null && !employer.getCompanyName().isBlank())
                                        ? employer.getCompanyName()
                                        : (employer.getNickname() != null ? employer.getNickname() : employer.getUsername()));
                        obj.put("employerPhone", employer.getPhone());
                    });
                }
            });
        }
        // 零工
        if (s.getWorkerId() != null) {
            userRepository.findById(s.getWorkerId()).ifPresent(worker -> {
                obj.put("workerName",
                        (worker.getNickname() != null && !worker.getNickname().isBlank())
                                ? worker.getNickname()
                                : worker.getUsername());
                obj.put("workerPhone", worker.getPhone());
            });
        }

        return Result.success(obj);
    }

    /** 站内消息提醒老板支付待结算工资 */
    @Operation(summary = "提醒老板结算", description = "仅「待结算」结算单可提醒；向订单发布老板发送一条结算提醒站内消息")
    @PostMapping("/{id}/remind")
    public Result<Map<String, Object>> remind(@PathVariable Long id) {
        if (messageService == null) throw new IllegalStateException("消息服务未配置");
        Settlement s = settlementRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("结算单不存在: " + id));
        if (!SettlementStatus.PENDING.equals(s.getStatus())) {
            throw new IllegalStateException("仅待结算的结算单可以发送提醒");
        }
        BossOrder order = s.getOrderId() == null ? null : orderRepository.findById(s.getOrderId()).orElse(null);
        if (order == null || order.getCreateBy() == null) {
            throw new IllegalStateException("未找到该结算单对应的老板");
        }
        String workerName = s.getWorkerId() == null ? "零工" : userRepository.findById(s.getWorkerId())
                .map(w -> StringUtils.hasText(w.getNickname()) ? w.getNickname() : w.getUsername())
                .filter(StringUtils::hasText).orElse("零工");
        BigDecimal amount = s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO;
        String content = "您发布的「" + order.getOrderTitle() + "」岗位，零工 " + workerName
                + " 的工资尚未结算，需支付 " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + " 元，请尽快完成结算。";
        messageService.sendToUser(order.getCreateBy(), UserRole.BOSS, MessageType.SETTLE_REMIND, "结算提醒",
                content, BizType.SETTLE, s.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("bossId", order.getCreateBy());
        return Result.success(data);
    }

    /** admin 手动结算（模拟支付） */
    @Operation(summary = "手动结算", description = "模拟支付：「待支付 → 已支付」，工资入零工钱包。仅待支付的结算单可以支付")
    @PostMapping("/{id}/pay")
    public Result<Settlement> pay(@PathVariable Long id) {
        return Result.success(settlementService.mockPay(id));
    }

    /** admin 批量手动结算（模拟支付）：接收前端勾选的结算单 ID，逐笔处理，单笔失败不影响其他结算单 */
    @Operation(summary = "批量手动结算", description = "按前端勾选的结算单 ID 批量模拟支付；逐笔处理并返回成功/失败明细")
    @PostMapping("/batch-pay")
    public Result<Map<String, Object>> batchPay(@RequestBody BatchPayRequest request) {
        List<Long> ids = request == null || request.ids() == null ? List.of() : request.ids().stream()
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) throw new IllegalArgumentException("结算单ID列表不能为空");
        if (ids.size() > 100) throw new IllegalArgumentException("单次最多结算100笔");

        List<Map<String, Object>> failures = new ArrayList<>();
        int successCount = 0;
        for (Long id : ids) {
            try {
                settlementService.mockPay(id);
                successCount += 1;
            } catch (Exception error) {
                Map<String, Object> failure = new HashMap<>();
                failure.put("id", id);
                failure.put("reason", error.getMessage() == null || error.getMessage().isBlank() ? "结算失败" : error.getMessage());
                failures.add(failure);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("total", ids.size());
        data.put("successCount", successCount);
        data.put("failedCount", failures.size());
        data.put("failures", failures);
        return Result.success(data);
    }

    public record BatchPayRequest(List<Long> ids) {}
}
