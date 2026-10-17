package com.kuaima.app.controller.boss;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.admin.entity.PointPurchaseOrder;
import com.kuaima.app.admin.repository.PointPurchaseOrderRepository;
import com.kuaima.app.domain.boss.entity.BossBalanceRechargeOrder;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BossBalanceRechargeOrderRepository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.wallet.entity.Settlement;
import com.kuaima.app.domain.wallet.entity.SettlementPaymentOrder;
import com.kuaima.app.domain.wallet.repository.SettlementPaymentOrderRepository;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/boss/expense-details")
@RequiredArgsConstructor
@Tag(name = "老板-支出明细", description = "购买积分、招工订单结算与账户充值聚合")
public class BossExpenseDetailController {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PointPurchaseOrderRepository pointOrders;
    private final SettlementPaymentOrderRepository settlementPayments;
    private final SettlementRespository settlements;
    private final BossBalanceRechargeOrderRepository rechargeOrders;
    private final BossOrderRespository orders;

    @GetMapping
    @Operation(summary = "支出明细", description = "返回近30天分类支出汇总和最新支出记录；records 包含 points/settle/recharge 三类")
    public Result<Map<String, Object>> list(Authentication authentication) {
        Long bossId = requireBossId(authentication);
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<Map<String, Object>> records = new ArrayList<>();
        pointOrders.findByBossIdOrderByPurchaseTimeDescIdDesc(bossId)
                .forEach(order -> records.add(pointRecord(order)));
        settlementPayments.findByBossIdOrderByCreatedAtDescIdDesc(bossId)
                .forEach(payment -> records.add(settlementRecord(payment)));
        rechargeOrders.findByBossIdOrderByIdDesc(bossId)
                .forEach(order -> records.add(rechargeRecord(order)));
        records.sort((left, right) -> String.valueOf(right.get("sortTime")).compareTo(
                String.valueOf(left.get("sortTime"))));
        Map<String, BigDecimal> summary = records.stream()
                .filter(record -> record.get("sortDateTime") instanceof LocalDateTime time && time.isAfter(since))
                .collect(Collectors.groupingBy(
                        record -> String.valueOf(record.get("type")),
                        Collectors.reducing(BigDecimal.ZERO,
                                record -> new BigDecimal(String.valueOf(record.get("amount"))),
                                BigDecimal::add)));
        BigDecimal points = summary.getOrDefault("points", BigDecimal.ZERO);
        BigDecimal settle = summary.getOrDefault("settle", BigDecimal.ZERO);
        BigDecimal recharge = summary.getOrDefault("recharge", BigDecimal.ZERO);
        List<Map<String, Object>> visibleRecords = records.stream().limit(100)
                .map(record -> (Map<String, Object>) new LinkedHashMap<String, Object>(record))
                .toList();
        visibleRecords.forEach(record -> {
            record.remove("sortTime");
            record.remove("sortDateTime");
        });
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", points.add(settle).add(recharge));
        data.put("points", points);
        data.put("settle", settle);
        data.put("recharge", recharge);
        data.put("records", visibleRecords);
        return Result.success(data);
    }

    private Map<String, Object> pointRecord(PointPurchaseOrder order) {
        return baseRecord(
                "points-" + order.getId(),
                "points",
                "购买积分 · " + order.getPoints() + " 积分套餐",
                "购买 " + order.getPoints() + " 积分套餐，用于发布招工订单",
                order.getAmount(),
                "支付成功".equals(order.getStatus()) ? "done" : "processing",
                order.getPurchaseTime(),
                order.getPurchaseTime(),
                List.of(order.getPoints() + "积分", order.getPayMethod()));
    }

    private Map<String, Object> settlementRecord(SettlementPaymentOrder payment) {
        List<Long> settlementIds = parseIds(payment.getSettlementIds());
        List<Settlement> rows = settlements.findAllById(settlementIds).stream().toList();
        List<BossOrder> orderRows = orders.findAllById(rows.stream()
                .map(Settlement::getOrderId).filter(Objects::nonNull).distinct().toList());
        BossOrder order = orderRows.isEmpty() ? null : orderRows.get(0);
        String position = order == null ? "招工订单" :
                (order.getOrderTitle() != null && !order.getOrderTitle().isBlank()
                        ? order.getOrderTitle() : order.getPostion());
        String date = order == null || order.getStartTime() == null ? "" :
                new java.text.SimpleDateFormat("M月d日").format(order.getStartTime());
        String description = (date.isEmpty() ? position : date + position) + "，" + rows.size() + " 名零工工资结算";
        List<String> tags = new ArrayList<>();
        tags.add(rows.size() + "人");
        if (order != null && order.getOrderNo() != null) tags.add(order.getOrderNo());
        LocalDateTime time = payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
        return baseRecord(
                "settle-" + payment.getId(),
                "settle",
                "招工订单结算 · " + position,
                description,
                payment.getAmount(),
                "PAID".equals(payment.getStatus()) ? "done" : "processing",
                time,
                payment.getCreatedAt(),
                tags);
    }

    private Map<String, Object> rechargeRecord(BossBalanceRechargeOrder order) {
        LocalDateTime payTime = order.getPayTime() == null ? null : order.getPayTime().toLocalDateTime();
        LocalDateTime createdTime = order.getTimestamp() == null ? null : order.getTimestamp().toLocalDateTime();
        LocalDateTime time = payTime != null ? payTime : createdTime;
        String payMethod = switch (order.getPayMethod() == null ? "" : order.getPayMethod()) {
            case "wechat" -> "微信支付";
            case "alipay" -> "支付宝支付";
            default -> order.getPayMethod();
        };
        boolean done = "paid".equals(order.getStatus()) || "manual_confirmed".equals(order.getStatus());
        return baseRecord(
                "recharge-" + order.getId(),
                "recharge",
                "账户充值 · 企业钱包",
                "企业钱包充值，用于订单结算与工资垫付",
                order.getAmount(),
                done ? "done" : "processing",
                time,
                createdTime,
                List.of("企业钱包", payMethod));
    }

    private Map<String, Object> baseRecord(String id, String type, String title, String description,
                                           BigDecimal amount, String status, LocalDateTime time,
                                           LocalDateTime fallbackTime, List<String> tags) {
        LocalDateTime displayTime = time != null ? time : fallbackTime;
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("type", type);
        record.put("title", title);
        record.put("description", description);
        record.put("amount", amount == null ? BigDecimal.ZERO.setScale(2) : amount.setScale(2));
        record.put("status", status);
        record.put("statusText", "done".equals(status) ? "已完成" : "结算中");
        record.put("time", displayTime == null ? "" : displayTime.format(TIME));
        record.put("tags", tags == null ? List.of() : tags);
        record.put("sortTime", displayTime == null ? "" : displayTime.format(TIME));
        record.put("sortDateTime", displayTime);
        return record;
    }

    private List<Long> parseIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).toList();
    }

    private Long requireBossId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user
                && user.id() != null && UserRole.BOSS.equals(user.role())) return user.id();
        throw new ForbiddenBusinessException("当前登录账号不是有效的老板账号");
    }
}
