package com.kuaima.app.domain.reward.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.kuaima.app.domain.reward.entity.RewardRechargeOrder;
import com.kuaima.app.domain.reward.repository.RewardRechargeOrderRepository;
import com.kuaima.app.domain.reward.repository.RewardAccountRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseMemberRepository;
import com.kuaima.app.admin.repository.AdminSettingRepository;
import com.kuaima.app.admin.entity.AdminSetting;
import com.kuaima.app.wechat.service.WechatPayService;
import com.kuaima.app.wechat.config.WechatPayProperties;

import jakarta.persistence.EntityNotFoundException;

/** 奖励金充值：老板使用微信 JSAPI 支付，支付回调后才入奖励金账户。金额单位为元。 */
@Service
public class RewardRechargeService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RewardRechargeOrderRepository orders;
    private final UserRepository users;
    private final WechatPayService wechatPay;
    private final RewardLedgerService ledger;
    private final EnterpriseMemberRepository members;
    private final AdminSettingRepository settings;
    private final RewardAccountRepository accounts;
    private final WechatPayProperties payProperties;

    @org.springframework.beans.factory.annotation.Autowired
    public RewardRechargeService(RewardRechargeOrderRepository orders, UserRepository users,
            WechatPayService wechatPay, RewardLedgerService ledger,
            EnterpriseMemberRepository members, AdminSettingRepository settings, RewardAccountRepository accounts,
            WechatPayProperties payProperties) {
        this.orders = orders;
        this.users = users;
        this.wechatPay = wechatPay;
        this.ledger = ledger;
        this.members = members;
        this.settings = settings;
        this.accounts = accounts;
        this.payProperties = payProperties;
    }

    /** 兼容已有单元测试构造方式；生产由 Spring 使用完整构造器。 */
    public RewardRechargeService(RewardRechargeOrderRepository orders, UserRepository users,
            WechatPayService wechatPay, RewardLedgerService ledger) {
        this(orders, users, wechatPay, ledger, null, null, null, null);
    }

    public Map<String, Object> create(Long userId, BigDecimal amount, String idempotencyKey) {
        return create(userId, amount, "WECHAT", idempotencyKey);
    }

    public Map<String, Object> create(Long userId, BigDecimal amount, String payMethod, String idempotencyKey) {
        return create(userId, null, amount, payMethod, idempotencyKey);
    }

    public Map<String, Object> create(Long userId, Long enterpriseId, BigDecimal amount, String payMethod, String idempotencyKey) {
        String method = StringUtils.hasText(payMethod) ? payMethod.trim().toUpperCase() : "WECHAT";
        if (!"WECHAT".equals(method)) throw new IllegalArgumentException("支付方式暂未启用");
        if (payProperties != null && !payProperties.isEnabled()) throw new IllegalArgumentException("微信支付尚未启用");
        BigDecimal normalized = normalizeAmount(amount);
        String key = normalizeKey(userId, idempotencyKey);
        RewardRechargeOrder order = orders.findByIdempotencyKey(key).orElse(null);
        if (order != null) return viewWithPay(requireSameRequest(order, userId, normalized, method), userId);
        User user = users.findById(userId).filter(UserRole::isBossIdentity)
                .orElseThrow(() -> new IllegalArgumentException("仅老板身份可以充值奖励金"));
        order = new RewardRechargeOrder();
        order.setOrderNo("BR" + LocalDateTime.now(ZONE).format(ORDER_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        order.setIdempotencyKey(key);
        order.setUserId(userId);
        order.setEnterpriseId(enterpriseId != null ? enterpriseId : members == null ? null
                : members.findFirstByUserIdAndStatusOrderByIdAsc(userId, "ACTIVE").map(m -> m.getEnterpriseId()).orElse(null));
        order.setAmount(normalized);
        order.setBonusAmount(bonusFor(normalized));
        order.setPayMethod(method);
        order.setStatus("PENDING");
        order.setRewardCredited(false);
        order.setCreatedAt(LocalDateTime.now(ZONE));
        order.setExpireAt(LocalDateTime.now(ZONE).plusMinutes(30));
        try {
            order = orders.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            RewardRechargeOrder concurrent = orders.findByIdempotencyKey(key).orElse(null);
            if (concurrent != null) return viewWithPay(requireSameRequest(concurrent, userId, normalized, method), userId);
            throw e;
        }
        return viewWithPay(order, userId, user.getOpenid());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long userId, String orderNo) {
        requireBoss(userId);
        RewardRechargeOrder order = orders.findByOrderNoAndUserId(orderNo, userId)
                .orElseThrow(() -> new EntityNotFoundException("奖励金充值订单不存在"));
        return view(order);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> config(Long userId) {
        requireBoss(userId);
        var account = ledgerAccount(userId);
        List<Map<String, Object>> quick = quickAmounts();
        return Map.of("balance", account, "minAmount", new BigDecimal("0.01"),
                "payMethods", List.of(
                        Map.of("code", "WECHAT", "name", "微信支付", "enabled", payProperties == null || payProperties.isEnabled(), "description", "微信支付，支付完成后自动入账"),
                        Map.of("code", "ENTERPRISE_BANK", "name", "企业银行账户", "enabled", false, "description", "企业银行充值暂未开放")),
                "quickAmounts", quick,
                "tips", List.of("充值后可用于支付零工报酬、购买积分等", "资金安全保障", "支付成功后以后台入账结果为准"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> records(Long userId, int page, int size, String status) {
        requireBoss(userId);
        String normalized = status == null ? "ALL" : status.trim().toUpperCase();
        if (!java.util.Set.of("ALL", "PENDING", "PAID", "FAILED", "CLOSED", "REFUNDED").contains(normalized)) {
            throw new IllegalArgumentException("status 参数无效");
        }
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id"));
        var result = "ALL".equals(normalized)
                ? orders.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable)
                : orders.findByUserIdAndStatusOrderByCreatedAtDescIdDesc(userId, normalized, pageable);
        return Map.of("records", result.map(this::viewRecord).getContent(), "total", result.getTotalElements(),
                "page", result.getNumber(), "size", result.getSize(), "hasMore", result.getNumber() + 1 < result.getTotalPages());
    }

    /** 回调金额校验专用查询，不暴露给前端。 */
    @Transactional(readOnly = true)
    public RewardRechargeOrder detailForCallback(String orderNo) {
        return orders.findByOrderNo(orderNo)
                .orElseThrow(() -> new EntityNotFoundException("奖励金充值订单不存在"));
    }

    @Transactional
    public RewardRechargeOrder markPaidByWechatCallback(String orderNo, String transactionId) {
        RewardRechargeOrder order = orders.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new EntityNotFoundException("奖励金充值订单不存在"));
        if (Boolean.TRUE.equals(order.getRewardCredited())) return order;
        ledger.credit(order.getUserId(), UserRole.BOSS, value(order.getAmount()).add(value(order.getBonusAmount())), "REWARD_RECHARGE", order.getId(),
                "微信充值奖励金", "微信支付奖励金充值", "REWARD_RECHARGE:" + order.getId());
        order.setRewardCredited(true);
        order.setStatus("PAID");
        order.setWechatTransactionId(transactionId);
        order.setPaidAt(LocalDateTime.now(ZONE));
        return orders.save(order);
    }

    private Map<String, Object> viewWithPay(RewardRechargeOrder order, Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        return viewWithPay(order, userId, user.getOpenid());
    }

    private Map<String, Object> viewWithPay(RewardRechargeOrder order, Long userId, String openid) {
        Map<String, Object> result = view(order);
        if ("PENDING".equals(order.getStatus())) {
            if ("PENDING".equals(order.getStatus()) && order.getExpireAt() != null
                    && order.getExpireAt().isBefore(LocalDateTime.now(ZONE))) {
                throw new IllegalArgumentException("充值订单已过期");
            }
            JSONObject params;
            if (StringUtils.hasText(order.getPayParams())) {
                params = JSON.parseObject(order.getPayParams());
            } else {
                params = wechatPay.prepay("快马奖励金充值", order.getOrderNo(), order.getAmount(), openid);
                order.setPayParams(params.toJSONString());
                orders.save(order);
            }
            result.put("payParams", params);
        } else {
            result.put("payParams", Map.of());
        }
        return result;
    }

    private Map<String, Object> view(RewardRechargeOrder order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("amount", order.getAmount());
        result.put("bonusAmount", value(order.getBonusAmount()));
        result.put("payMethod", order.getPayMethod());
        result.put("status", order.getStatus());
        result.put("createdAt", order.getCreatedAt());
        result.put("paidAt", order.getPaidAt());
        result.put("expireAt", order.getExpireAt());
        result.put("failureReason", order.getFailureReason());
        return result;
    }

    private Map<String, Object> viewRecord(RewardRechargeOrder order) {
        Map<String, Object> result = view(order);
        result.put("totalAmount", value(order.getAmount()).add(value(order.getBonusAmount())));
        result.remove("id"); result.remove("payParams");
        return result;
    }

    private BigDecimal bonusFor(BigDecimal amount) {
        List<Map<String, Object>> quick = quickAmounts();
        return quick.stream().filter(x -> new BigDecimal(String.valueOf(x.get("amount"))).compareTo(amount) == 0)
                .map(x -> new BigDecimal(String.valueOf(x.get("bonusAmount")))).findFirst().orElse(BigDecimal.ZERO.setScale(2));
    }

    private List<Map<String, Object>> quickAmounts() {
        String raw = settings == null ? null : settings.findById("reward.recharge.quickAmounts").map(AdminSetting::getSettingValue)
                .orElse("0.01:0,1000:30,2000:80,5000:260,10000:600,20000:1400");
        if (raw == null) raw = "0.01:0,1000:30,2000:80,5000:260,10000:600,20000:1400";
        List<Map<String, Object>> values = new java.util.ArrayList<>();
        for (String pair : raw.split(",")) {
            String[] parts = pair.trim().split(":"); if (parts.length != 2) continue;
            try { values.add(Map.of("amount", new BigDecimal(parts[0]).setScale(2), "bonusAmount", new BigDecimal(parts[1]).setScale(2))); }
            catch (RuntimeException ignored) { }
        }
        return values;
    }

    private BigDecimal ledgerAccount(Long userId) {
        if (accounts == null) return BigDecimal.ZERO.setScale(2);
        return accounts.findFirstByUserIdAndRoleOrderByIdDesc(userId, UserRole.BOSS)
                .map(a -> value(a.getBalance())).orElse(BigDecimal.ZERO.setScale(2));
    }

    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2); }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("amount 不能为空");
        BigDecimal normalized;
        try { normalized = amount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("充值金额最多保留两位小数"); }
        if (normalized.compareTo(new BigDecimal("0.01")) < 0) {
            throw new IllegalArgumentException("充值金额必须大于等于0.01元");
        }
        if (normalized.compareTo(new BigDecimal("100000.00")) > 0) {
            throw new IllegalArgumentException("单次充值金额不能超过100000元");
        }
        return normalized;
    }

    private RewardRechargeOrder requireSameRequest(RewardRechargeOrder order, Long userId, BigDecimal amount, String method) {
        if (!userId.equals(order.getUserId())) throw new IllegalArgumentException("幂等键已被其他用户使用");
        if (order.getAmount().compareTo(amount) != 0) {
            throw new IllegalArgumentException("同一Idempotency-Key不能用于不同充值金额");
        }
        if (order.getPayMethod() != null && !method.equals(order.getPayMethod())) {
            throw new IllegalArgumentException("同一Idempotency-Key不能用于不同支付方式");
        }
        return order;
    }

    private String normalizeKey(Long userId, String key) {
        if (!StringUtils.hasText(key)) throw new IllegalArgumentException("Idempotency-Key 不能为空");
        String value = key.trim();
        if (value.length() > 100) throw new IllegalArgumentException("Idempotency-Key 长度不能超过100");
        return "reward-recharge:" + userId + ":" + value;
    }

    private User requireBoss(Long userId) {
        return users.findById(userId).filter(UserRole::isBossIdentity)
                .orElseThrow(() -> new IllegalArgumentException("当前账号不是有效老板身份"));
    }
}
