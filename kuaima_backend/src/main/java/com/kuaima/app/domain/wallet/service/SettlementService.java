package com.kuaima.app.domain.wallet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Optional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.message.constant.BizType;
import com.kuaima.app.domain.message.constant.MessageType;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.points.entity.PointsAccount;
import com.kuaima.app.domain.points.entity.PointsFlow;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.points.repository.PointsFlowRepository;
import com.kuaima.app.domain.coupon.entity.Coupon;
import com.kuaima.app.domain.coupon.entity.UserCoupon;
import com.kuaima.app.domain.coupon.repository.CouponRepository;
import com.kuaima.app.domain.coupon.repository.UserCouponRepository;
import com.kuaima.app.domain.user.service.CreditScoreService;
import com.kuaima.app.domain.wallet.constant.SettlementStatus;
import com.kuaima.app.domain.wallet.entity.Settlement;
import com.kuaima.app.domain.wallet.entity.Wallet;
import com.kuaima.app.domain.wallet.entity.SettlementPaymentOrder;
import com.kuaima.app.domain.wallet.model.PendingSettlementModels.PendingSettlementItem;
import com.kuaima.app.domain.wallet.model.PendingSettlementModels.PendingSettlementOrder;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;
import com.kuaima.app.domain.wallet.repository.SettlementPaymentOrderRepository;
import com.kuaima.app.admin.entity.AdminSetting;
import com.kuaima.app.admin.repository.AdminSettingRepository;
import com.kuaima.app.wechat.service.WechatPayService;

import jakarta.persistence.EntityNotFoundException;

/**
 * 结算服务：老板对"已完成"的报名记录发起结算 -> 系统按 订单工资 × 工作天数 自动算工资(分)，
 * 并叠加平台服务费 -> 生成"待支付"结算单 -> 老板模拟支付成功后工资入零工钱包。
 * 服务费默认 0（费率规则待定，见 suma.settle.service-fee-rate）。
 */
@Service
public class SettlementService {

    public static final String PAYMENT_PENDING = "PENDING";
    public static final String PAYMENT_PAID = "PAID";

    private final SettlementRespository settlementRepository;
    private final BossOrderRespository orderRepository;
    private final BaseOrderItemRespository itemRepository;
    private final WalletService walletService;
    private final MessageService messageService;
    private final UserRepository userRepository;
    private final AdminSettingRepository adminSettingRepository;
    private final CreditScoreService creditScoreService;
    private final SettlementPaymentOrderRepository paymentOrders;
    private final WechatPayService wechatPay;
    private final PointsAccountRepository pointsAccounts;
    private final PointsFlowRepository pointsFlows;
    private final UserCouponRepository userCoupons;
    private final CouponRepository coupons;

    /** 平台服务费率(% of wage)，规则待定，默认 0 */
    @Value("${kuaima.settle.service-fee-rate:0}")
    private BigDecimal serviceFeeRate;

    public SettlementService(SettlementRespository settlementRepository,
                             BossOrderRespository orderRepository,
                            BaseOrderItemRespository itemRepository,
                            WalletService walletService,
                             MessageService messageService,
                             UserRepository userRepository) {
        this(settlementRepository, orderRepository, itemRepository, walletService, messageService, userRepository, null, null);
    }

    public SettlementService(SettlementRespository settlementRepository,
                             BossOrderRespository orderRepository,
                             BaseOrderItemRespository itemRepository,
                             WalletService walletService,
                             MessageService messageService,
                             UserRepository userRepository,
                             AdminSettingRepository adminSettingRepository) {
        this(settlementRepository, orderRepository, itemRepository, walletService, messageService, userRepository,
                adminSettingRepository, null, null, null);
    }

    public SettlementService(SettlementRespository settlementRepository,
                             BossOrderRespository orderRepository,
                             BaseOrderItemRespository itemRepository,
                             WalletService walletService,
                             MessageService messageService,
                             UserRepository userRepository,
                             AdminSettingRepository adminSettingRepository,
                             CreditScoreService creditScoreService) {
        this(settlementRepository, orderRepository, itemRepository, walletService, messageService, userRepository,
                adminSettingRepository, creditScoreService, null, null);
    }

    public SettlementService(SettlementRespository settlementRepository,
                             BossOrderRespository orderRepository,
                             BaseOrderItemRespository itemRepository,
                             WalletService walletService,
                             MessageService messageService,
                             UserRepository userRepository,
                             AdminSettingRepository adminSettingRepository,
                             CreditScoreService creditScoreService,
                             SettlementPaymentOrderRepository paymentOrders,
                             WechatPayService wechatPay) {
        this(settlementRepository, orderRepository, itemRepository, walletService, messageService, userRepository,
                adminSettingRepository, creditScoreService, paymentOrders, wechatPay, null);
    }

    public SettlementService(SettlementRespository settlementRepository,
                             BossOrderRespository orderRepository,
                             BaseOrderItemRespository itemRepository,
                             WalletService walletService,
                             MessageService messageService,
                             UserRepository userRepository,
                             AdminSettingRepository adminSettingRepository,
                             CreditScoreService creditScoreService,
                             SettlementPaymentOrderRepository paymentOrders,
                             WechatPayService wechatPay,
                             PointsAccountRepository pointsAccounts) {
        this(settlementRepository, orderRepository, itemRepository, walletService, messageService, userRepository,
                adminSettingRepository, creditScoreService, paymentOrders, wechatPay, pointsAccounts, null, null);
    }

    public SettlementService(SettlementRespository settlementRepository, BossOrderRespository orderRepository,
                             BaseOrderItemRespository itemRepository, WalletService walletService,
                             MessageService messageService, UserRepository userRepository,
                             AdminSettingRepository adminSettingRepository, CreditScoreService creditScoreService,
                             SettlementPaymentOrderRepository paymentOrders, WechatPayService wechatPay,
                             PointsAccountRepository pointsAccounts, UserCouponRepository userCoupons,
                             CouponRepository coupons) {
        this(settlementRepository, orderRepository, itemRepository, walletService, messageService, userRepository,
                adminSettingRepository, creditScoreService, paymentOrders, wechatPay, pointsAccounts,
                userCoupons, coupons, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SettlementService(SettlementRespository settlementRepository, BossOrderRespository orderRepository,
                             BaseOrderItemRespository itemRepository, WalletService walletService,
                             MessageService messageService, UserRepository userRepository,
                             AdminSettingRepository adminSettingRepository, CreditScoreService creditScoreService,
                             SettlementPaymentOrderRepository paymentOrders, WechatPayService wechatPay,
                             PointsAccountRepository pointsAccounts, UserCouponRepository userCoupons,
                             CouponRepository coupons, PointsFlowRepository pointsFlows) {
        this.settlementRepository = settlementRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.walletService = walletService;
        this.messageService = messageService;
        this.userRepository = userRepository;
        this.adminSettingRepository = adminSettingRepository;
        this.creditScoreService = creditScoreService;
        this.paymentOrders = paymentOrders;
        this.wechatPay = wechatPay;
        this.pointsAccounts = pointsAccounts;
        this.pointsFlows = pointsFlows;
        this.userCoupons = userCoupons;
        this.coupons = coupons;
    }

    /**
     * 创建结算单：报名记录必须处于「待结算」（历史兼容「已完成」）；
     * 结算单创建成功后将报名记录推进为「已完成」（老板点击"去结算"后岗位状态变为已完成）。
     * 同一报名记录不允许重复结算。
     *
     * @param workDays 实际工作天数，为空时由 到岗日~完成日 自动推导（最小 1 天）
     */
    @Transactional
    public Settlement createSettlement(Long itemId, Integer workDays) {
        BaseOrderItem item = getItemOrThrow(itemId);
        if (!BossStatus.ITEM_PENDING_SETTLE.equals(item.getStatus())
                && !BossStatus.ITEM_FINISHED.equals(item.getStatus())) {
            throw new IllegalStateException("仅待结算的报名记录可以发起结算");
        }
        if (settlementRepository.existsByItemIdAndStatusIn(itemId,
                List.of(SettlementStatus.PENDING, SettlementStatus.PAID))) {
            throw new IllegalStateException("该报名记录已存在待支付/已支付的结算单，请勿重复结算");
        }
        BossOrder order = orderRepository.findById(item.getOrderId())
                .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + item.getOrderId()));
        if (order.getSalary() == null || order.getSalary().signum() <= 0) {
            throw new IllegalStateException("订单工资未设置，无法结算");
        }
        int days = resolveWorkDays(item, workDays);
        BigDecimal wage = order.getSalary().multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal serviceFee = calculateServiceFee(wage);
        BigDecimal total = wage.add(serviceFee);

        Settlement s = new Settlement();
        s.setItemId(itemId);
        s.setOrderId(item.getOrderId());
        s.setWorkerId(item.getUserId());
        s.setWorkDays(days);
        s.setWage(wage);
        s.setServiceFee(serviceFee);
        s.setTotalAmount(total);
        s.setStatus(SettlementStatus.PENDING);
        Settlement saved = settlementRepository.save(s);

        if (!BossStatus.ITEM_FINISHED.equals(item.getStatus())) {
            item.setStatus(BossStatus.ITEM_FINISHED);
            itemRepository.save(item);
        }
        return saved;
    }

    /**
     * 模拟支付：待支付 -> 已支付；工资入零工钱包并记流水；服务费归平台（记录在结算单上）。
     */
    @Transactional
    public Settlement mockPay(Long settlementId) {
        Settlement s = getSettlementOrThrow(settlementId);
        if (SettlementStatus.PAID.equals(s.getStatus())) {
            // 支付回调/前端重试必须幂等：不重复入账，只补偿历史状态同步。
            synchronizePaidItemAndOrder(s);
            return s;
        }
        if (!SettlementStatus.PENDING.equals(s.getStatus())) {
            throw new IllegalStateException("仅待支付的结算单可以支付");
        }
        return completePaidSettlement(s, "MOCK" + System.currentTimeMillis(), null);
    }

    @Transactional
    public Settlement mockPayForBoss(Long settlementId, Long bossId) {
        Settlement settlement = getSettlementOrThrow(settlementId);
        BossOrder order = orderRepository.findById(settlement.getOrderId())
                .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + settlement.getOrderId()));
        if (!bossId.equals(order.getCreateBy())) throw new com.kuaima.app.common.ForbiddenBusinessException("无权支付该结算单");
        return mockPay(settlementId);
    }

    /** 创建微信 JSAPI 结算支付单；一笔微信支付可覆盖多条零工结算记录。 */
    @Transactional
    public com.kuaima.app.domain.wallet.model.SettlementPaymentModels.WechatPayView createWechatPayment(
            Long bossId, List<Long> settlementIds, String idempotencyKey) {
        return createWechatPayment(bossId, settlementIds, null, idempotencyKey);
    }

    @Transactional
    public com.kuaima.app.domain.wallet.model.SettlementPaymentModels.WechatPayView createWechatPayment(
            Long bossId, List<Long> settlementIds, Long userCouponId, String idempotencyKey) {
        return createWechatPayment(bossId, settlementIds, userCouponId, 0, idempotencyKey);
    }

    @Transactional
    public com.kuaima.app.domain.wallet.model.SettlementPaymentModels.WechatPayView createWechatPayment(
            Long bossId, List<Long> settlementIds, Long userCouponId, Integer pointsToUse, String idempotencyKey) {
        if (paymentOrders == null || wechatPay == null) throw new IllegalStateException("微信结算支付服务未配置");
        if (bossId == null) throw new IllegalArgumentException("老板用户ID不能为空");
        if (!org.springframework.util.StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("Idempotency-Key 不能为空");
        }
        String key = idempotencyKey.trim();
        SettlementPaymentOrder old = paymentOrders.findByIdempotencyKey(key).orElse(null);
        if (old != null) {
            if (!bossId.equals(old.getBossId())) throw new com.kuaima.app.common.ForbiddenBusinessException("幂等键已被其他用户使用");
            if (!Objects.equals(old.getUserCouponId(), userCouponId)) {
                throw new IllegalArgumentException("幂等键已绑定其他优惠券");
            }
            if (!Objects.equals(old.getPointsUsed(), normalizeRequestedPoints(pointsToUse))) {
                throw new IllegalArgumentException("幂等键已绑定其他积分抵扣数量");
            }
            return paymentView(old);
        }
        List<Long> ids = settlementIds == null ? List.of() : settlementIds.stream()
                .filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) throw new IllegalArgumentException("settlementIds 不能为空");
        if (ids.size() > 100) throw new IllegalArgumentException("单次最多支付100笔结算单");

        List<Settlement> settlements = new ArrayList<>();
        BigDecimal amount = BigDecimal.ZERO.setScale(2);
        for (Long id : ids) {
            Settlement settlement = settlementRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("结算单不存在: " + id));
            BossOrder order = orderRepository.findById(settlement.getOrderId())
                    .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + settlement.getOrderId()));
            if (!bossId.equals(order.getCreateBy())) throw new com.kuaima.app.common.ForbiddenBusinessException("无权支付该结算单");
            if (!SettlementStatus.PENDING.equals(settlement.getStatus())) throw new IllegalStateException("仅待支付的结算单可以发起微信支付");
            if (settlement.getTotalAmount() == null || settlement.getTotalAmount().signum() <= 0) {
                throw new IllegalStateException("结算金额无效: " + id);
            }
            settlements.add(settlement);
            amount = amount.add(settlement.getTotalAmount());
        }
        SettlementPaymentOrder payment = new SettlementPaymentOrder();
        payment.setPaymentNo("SP" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        payment.setIdempotencyKey(key);
        payment.setBossId(bossId);
        payment.setSettlementIds(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        CouponCalculation coupon = calculateCoupon(bossId, userCouponId, amount);
        PointsCalculation points = calculatePoints(bossId, pointsToUse,
                amount.subtract(coupon.deductAmount()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        BigDecimal payableAmount = amount
                .subtract(coupon.deductAmount())
                .subtract(points.deductAmount())
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        payment.setUserCouponId(userCouponId);
        payment.setCouponDeductAmount(coupon.deductAmount());
        payment.setPointsUsed(points.used());
        payment.setPointsDeductAmount(points.deductAmount());
        payment.setAmount(payableAmount);
        payment.setStatus(PAYMENT_PENDING);
        payment.setCreatedAt(LocalDateTime.now());
        payment = paymentOrders.save(payment);

        if (payableAmount.signum() == 0) {
            payment = finalizePaidPayment(payment, "POINTS-" + payment.getPaymentNo());
            return paymentView(payment);
        }

        User payer = userRepository.findById(bossId)
                .orElseThrow(() -> new EntityNotFoundException("老板用户不存在: " + bossId));
        if (!org.springframework.util.StringUtils.hasText(payer.getOpenid())) throw new IllegalArgumentException("当前老板未绑定微信 openid");

        JSONObject payParams = wechatPay.prepay("快马日结订单结算", payment.getPaymentNo(), payment.getAmount(), payer.getOpenid());
        payment.setPayParams(payParams.toJSONString());
        payment = paymentOrders.save(payment);
        return paymentView(payment);
    }

    @Transactional(readOnly = true)
    public com.kuaima.app.domain.wallet.model.SettlementPaymentModels.SettlementFeePreview previewWechatPayment(
            Long bossId, List<Long> settlementIds) {
        return previewWechatPayment(bossId, settlementIds, null);
    }

    @Transactional(readOnly = true)
    public com.kuaima.app.domain.wallet.model.SettlementPaymentModels.SettlementFeePreview previewWechatPayment(
            Long bossId, List<Long> settlementIds, Long userCouponId) {
        return previewWechatPayment(bossId, settlementIds, userCouponId, 0);
    }

    @Transactional(readOnly = true)
    public com.kuaima.app.domain.wallet.model.SettlementPaymentModels.SettlementFeePreview previewWechatPayment(
            Long bossId, List<Long> settlementIds, Long userCouponId, Integer pointsToUse) {
        List<Long> ids = settlementIds == null ? List.of() : settlementIds.stream()
                .filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) throw new IllegalArgumentException("settlementIds 不能为空");
        if (ids.size() > 100) throw new IllegalArgumentException("单次最多查询100笔结算单");
        BigDecimal orderAmount = BigDecimal.ZERO;
        BigDecimal serviceFee = BigDecimal.ZERO;
        for (Long id : ids) {
            Settlement settlement = settlementRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("结算单不存在: " + id));
            BossOrder order = orderRepository.findById(settlement.getOrderId())
                    .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + settlement.getOrderId()));
            if (!bossId.equals(order.getCreateBy())) throw new com.kuaima.app.common.ForbiddenBusinessException("无权查看该结算单");
            if (!SettlementStatus.PENDING.equals(settlement.getStatus())) throw new IllegalStateException("仅待支付的结算单可以预览");
            orderAmount = orderAmount.add(value(settlement.getWage()));
            serviceFee = serviceFee.add(value(settlement.getServiceFee()));
        }
        BigDecimal total = orderAmount.add(serviceFee).setScale(2, RoundingMode.HALF_UP);
        CouponCalculation coupon = calculateCoupon(bossId, userCouponId, total);
        PointsCalculation pointCalculation = calculatePoints(bossId, pointsToUse,
                total.subtract(coupon.deductAmount()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        BigDecimal payable = total
                .subtract(coupon.deductAmount())
                .subtract(pointCalculation.deductAmount())
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return new com.kuaima.app.domain.wallet.model.SettlementPaymentModels.SettlementFeePreview(
                ids, payable, orderAmount.setScale(2, RoundingMode.HALF_UP), serviceFee.setScale(2, RoundingMode.HALF_UP),
                pointCalculation.available(), 100, pointCalculation.deductAmount(), coupon.available(),
                coupon.name(), coupon.options(), pointCalculation.used(), coupon.deductAmount(), payable);
    }

    private CouponCalculation calculateCoupon(Long bossId, Long userCouponId, BigDecimal total) {
        if (userCoupons == null || coupons == null) return new CouponCalculation(false, null, BigDecimal.ZERO.setScale(2));
        List<Map<String, Object>> availableOptions = new ArrayList<>();
        for (UserCoupon candidate : userCoupons.findByUserIdAndStatus(bossId, "UNUSED")) {
            if (candidate.getExpireAt() != null && candidate.getExpireAt().toLocalDate().isBefore(java.time.LocalDate.now())) continue;
            Coupon definition = coupons.findById(candidate.getCouponId()).orElse(null);
            if (definition == null) continue;
            if (!isCouponUsable(definition)) continue;
            BigDecimal threshold = definition.getThreshold() != null ? definition.getThreshold() : Optional.ofNullable(definition.getMinSpend()).orElse(BigDecimal.ZERO);
            if (total.compareTo(threshold) < 0) continue;
            BigDecimal deduct = couponDeduct(definition, total);
            String name = StringUtils.hasText(definition.getName()) ? definition.getName() : definition.getTitle();
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("userCouponId", candidate.getId()); option.put("couponId", definition.getId());
            option.put("name", name); option.put("amount", value(definition.getAmount())); option.put("threshold", threshold);
            option.put("deductAmount", deduct); option.put("expireAt", candidate.getExpireAt());
            availableOptions.add(option);
        }
        if (userCouponId == null) return new CouponCalculation(!availableOptions.isEmpty(), null, availableOptions, BigDecimal.ZERO.setScale(2));
        UserCoupon record = userCoupons.findById(userCouponId).orElseThrow(() -> new EntityNotFoundException("优惠券不存在"));
        if (!Objects.equals(record.getUserId(), bossId)) throw new com.kuaima.app.common.ForbiddenBusinessException("无权使用该优惠券");
        if (!"UNUSED".equals(record.getStatus())) throw new IllegalArgumentException("优惠券已使用或不可用");
        if (record.getExpireAt() != null && record.getExpireAt().toLocalDate().isBefore(java.time.LocalDate.now())) throw new IllegalArgumentException("优惠券已过期");
        Coupon coupon = coupons.findById(record.getCouponId()).orElseThrow(() -> new EntityNotFoundException("优惠券不存在"));
        if (!isCouponUsable(coupon)) throw new IllegalArgumentException("优惠券当前不可用");
        BigDecimal threshold = coupon.getThreshold() != null ? coupon.getThreshold() : Optional.ofNullable(coupon.getMinSpend()).orElse(BigDecimal.ZERO);
        if (total.compareTo(threshold) < 0) throw new IllegalArgumentException("结算金额未达到优惠券使用门槛");
        BigDecimal deduct = couponDeduct(coupon, total);
        String name = StringUtils.hasText(coupon.getName()) ? coupon.getName() : coupon.getTitle();
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("userCouponId", record.getId()); option.put("couponId", coupon.getId());
        option.put("name", name); option.put("amount", value(coupon.getAmount())); option.put("threshold", threshold);
        option.put("deductAmount", deduct); option.put("expireAt", record.getExpireAt());
        return new CouponCalculation(true, name, availableOptions, deduct);
    }

    private boolean isCouponUsable(Coupon coupon) {
        return coupon != null
                && !Boolean.TRUE.equals(coupon.getDeleted())
                && !Boolean.TRUE.equals(coupon.getStopped())
                && (coupon.getStatus() == null || "ISSUED".equalsIgnoreCase(coupon.getStatus()));
    }

    private BigDecimal couponDeduct(Coupon coupon, BigDecimal total) {
        return "DISCOUNT".equalsIgnoreCase(coupon.getType()) && coupon.getDiscount() != null
                ? total.multiply(BigDecimal.ONE.subtract(coupon.getDiscount().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))).setScale(2, RoundingMode.HALF_UP)
                : value(coupon.getAmount()).min(total).setScale(2, RoundingMode.HALF_UP);
    }

    private record CouponCalculation(boolean available, String name, List<Map<String, Object>> options, BigDecimal deductAmount) {
        CouponCalculation(boolean available, String name, BigDecimal deductAmount) {
            this(available, name, List.of(), deductAmount);
        }
    }

    private record PointsCalculation(int available, int used, BigDecimal deductAmount) {}

    private PointsCalculation calculatePoints(Long bossId, Integer requestedPoints, BigDecimal payableAmount) {
        int requested = normalizeRequestedPoints(requestedPoints);
        int available = pointsAccounts == null ? 0 : pointsAccounts.findByUserIdAndRole(bossId, UserRole.BOSS)
                .map(account -> account.getBalance() == null ? 0 : account.getBalance()).orElse(0);
        int maxForPayment = payableAmount == null ? 0 : payableAmount
                .movePointRight(2).setScale(0, RoundingMode.DOWN).intValueExact();
        int used = Math.min(requested, Math.min(available, maxForPayment));
        return new PointsCalculation(available, used, BigDecimal.valueOf(used, 2));
    }

    private Integer normalizeRequestedPoints(Integer points) {
        if (points == null) return 0;
        if (points < 0) throw new IllegalArgumentException("积分抵扣数量不能小于0");
        return points;
    }

    @Transactional(readOnly = true)
    public com.kuaima.app.domain.wallet.model.SettlementPaymentModels.WechatPayView getWechatPayment(Long bossId, String paymentNo) {
        if (paymentOrders == null) throw new IllegalStateException("微信结算支付服务未配置");
        SettlementPaymentOrder payment = paymentOrders.findByPaymentNoAndBossId(paymentNo, bossId)
                .orElseThrow(() -> new EntityNotFoundException("结算支付单不存在"));
        return paymentView(payment);
    }

    @Transactional(readOnly = true)
    public SettlementPaymentOrder findWechatPayment(String paymentNo) {
        if (paymentOrders == null) throw new IllegalStateException("微信结算支付服务未配置");
        return paymentOrders.findByPaymentNo(paymentNo).orElseThrow(() -> new EntityNotFoundException("结算支付单不存在"));
    }

    /** 微信回调确认支付；支付单行锁保证重复通知不会重复给零工钱包入账。 */
    @Transactional
    public SettlementPaymentOrder markWechatPaymentPaid(String paymentNo, String transactionId) {
        SettlementPaymentOrder payment = paymentOrders.findByPaymentNoForUpdate(paymentNo)
                .orElseThrow(() -> new EntityNotFoundException("结算支付单不存在"));
        if (PAYMENT_PAID.equals(payment.getStatus())) return payment;
        if (!PAYMENT_PENDING.equals(payment.getStatus())) throw new IllegalStateException("当前结算支付单状态不能确认支付");
        return finalizePaidPayment(payment, transactionId);
    }

    private SettlementPaymentOrder finalizePaidPayment(SettlementPaymentOrder payment, String transactionId) {
        for (Long settlementId : parseSettlementIds(payment.getSettlementIds())) {
            Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                    .orElseThrow(() -> new EntityNotFoundException("结算单不存在: " + settlementId));
            if (!SettlementStatus.PAID.equals(settlement.getStatus())) {
                if (!SettlementStatus.PENDING.equals(settlement.getStatus())) throw new IllegalStateException("结算单状态已变化: " + settlementId);
                completePaidSettlement(settlement, transactionId, transactionId);
            }
        }
        payment.setStatus(PAYMENT_PAID);
        redeemCoupon(payment);
        deductPoints(payment);
        payment.setWechatTransactionId(transactionId);
        payment.setPaidAt(LocalDateTime.now());
        return paymentOrders.save(payment);
    }

    private void redeemCoupon(SettlementPaymentOrder payment) {
        if (payment.getUserCouponId() == null || userCoupons == null || coupons == null) return;
        UserCoupon record = userCoupons.findByIdForUpdate(payment.getUserCouponId()).orElseThrow(() -> new EntityNotFoundException("优惠券不存在"));
        if ("USED".equals(record.getStatus())) return;
        if (!"UNUSED".equals(record.getStatus())) throw new IllegalStateException("优惠券状态不能核销");
        Coupon coupon = coupons.findByIdForUpdate(record.getCouponId()).orElseThrow(() -> new EntityNotFoundException("优惠券不存在"));
        if (!isCouponUsable(coupon)) throw new IllegalStateException("优惠券当前不可用");
        record.setStatus("USED");
        record.setUsedAt(java.sql.Timestamp.valueOf(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))));
        Long firstOrderId = parseSettlementIds(payment.getSettlementIds()).stream()
                .findFirst()
                .flatMap(id -> settlementRepository.findById(id).map(Settlement::getOrderId))
                .orElse(null);
        record.setUseOrderId(firstOrderId);
        userCoupons.save(record);
        coupon.setUsed((coupon.getUsed() == null ? 0 : coupon.getUsed()) + 1);
        coupons.save(coupon);
    }

    private void deductPoints(SettlementPaymentOrder payment) {
        int pointsUsed = payment.getPointsUsed() == null ? 0 : payment.getPointsUsed();
        if (pointsUsed <= 0) return;
        if (pointsAccounts == null) throw new IllegalStateException("积分账户服务未配置");
        PointsAccount account = pointsAccounts.findByUserIdAndRoleForUpdate(payment.getBossId(), UserRole.BOSS)
                .orElseThrow(() -> new EntityNotFoundException("老板积分账户不存在"));
        int balance = account.getBalance() == null ? 0 : account.getBalance();
        if (balance < pointsUsed) throw new IllegalStateException("积分余额不足，无法确认结算支付");
        account.setBalance(balance - pointsUsed);
        pointsAccounts.save(account);
        if (pointsFlows == null) throw new IllegalStateException("积分流水服务未配置");
        PointsFlow flow = new PointsFlow();
        flow.setUserId(payment.getBossId());
        flow.setRole(UserRole.BOSS);
        flow.setDelta(-pointsUsed);
        flow.setBizType("SETTLEMENT_DEDUCT");
        flow.setBalanceAfter(balance - pointsUsed);
        flow.setBizNo(payment.getPaymentNo());
        flow.setRemark("结算支付积分抵扣");
        pointsFlows.save(flow);
    }

    public int expectedWechatPaymentFen(SettlementPaymentOrder payment) {
        return payment.getAmount().movePointRight(2).intValueExact();
    }

    private Settlement completePaidSettlement(Settlement settlement, String payNo, String wechatTransactionId) {
        settlement.setStatus(SettlementStatus.PAID);
        settlement.setPayNo(payNo);
        settlement.setWechatTransactionId(wechatTransactionId);
        settlement.setPayTime(LocalDateTime.now());
        settlementRepository.save(settlement);
        synchronizePaidItemAndOrder(settlement);
        applyBossCredit(settlement);
        walletService.credit(settlement.getWorkerId(), settlement.getWage(), WalletService.BIZ_WAGE,
                settlement.getId(), "工资结算 orderId=" + settlement.getOrderId() + " 天数=" + settlement.getWorkDays());
        messageService.sendToUser(settlement.getWorkerId(), UserRole.USER, MessageType.SETTLE_PAID, "工资已到账",
                "您的工资 " + settlement.getWage().stripTrailingZeros().toPlainString() + " 元已到账，可在钱包中查看或提现。",
                BizType.SETTLE, settlement.getId(), Map.of("wage", settlement.getWage(), "orderId", settlement.getOrderId()));
        return settlement;
    }

    private com.kuaima.app.domain.wallet.model.SettlementPaymentModels.WechatPayView paymentView(SettlementPaymentOrder payment) {
        Map<String, Object> params = Map.of();
        if (org.springframework.util.StringUtils.hasText(payment.getPayParams())) {
            params = new LinkedHashMap<>(JSON.parseObject(payment.getPayParams()));
        }
        List<Long> ids = parseSettlementIds(payment.getSettlementIds());
        BigDecimal orderAmount = BigDecimal.ZERO.setScale(2);
        BigDecimal serviceFee = BigDecimal.ZERO.setScale(2);
        for (Long id : ids) {
            Settlement settlement = settlementRepository.findById(id).orElse(null);
            if (settlement == null) continue;
            orderAmount = orderAmount.add(value(settlement.getWage()));
            serviceFee = serviceFee.add(value(settlement.getServiceFee()));
        }
        orderAmount = orderAmount.setScale(2, RoundingMode.HALF_UP);
        serviceFee = serviceFee.setScale(2, RoundingMode.HALF_UP);
        Integer pointsAvailable = pointsAccounts == null ? 0 : pointsAccounts
                .findByUserIdAndRole(payment.getBossId(), UserRole.BOSS)
                .map(a -> a.getBalance() == null ? 0 : a.getBalance()).orElse(0);
        BigDecimal total = orderAmount.add(serviceFee).setScale(2, RoundingMode.HALF_UP);
        String couponName = null;
        boolean couponAvailable = false;
        BigDecimal couponDeductAmount = value(payment.getCouponDeductAmount()).setScale(2, RoundingMode.HALF_UP);
        if (payment.getUserCouponId() != null && userCoupons != null && coupons != null) {
            UserCoupon record = userCoupons.findById(payment.getUserCouponId()).orElse(null);
            if (record != null) {
                Coupon coupon = coupons.findById(record.getCouponId()).orElse(null);
                if (coupon != null) {
                    couponName = StringUtils.hasText(coupon.getName()) ? coupon.getName() : coupon.getTitle();
                    couponAvailable = couponDeductAmount.signum() > 0 || PAYMENT_PENDING.equals(payment.getStatus());
                }
            }
        }
        return new com.kuaima.app.domain.wallet.model.SettlementPaymentModels.WechatPayView(
                payment.getPaymentNo(), ids, payment.getAmount(), orderAmount, serviceFee,
                pointsAvailable, 100, value(payment.getPointsDeductAmount()).setScale(2, RoundingMode.HALF_UP),
                couponAvailable, couponName, payment.getPointsUsed() == null ? 0 : payment.getPointsUsed(),
                couponDeductAmount, payment.getAmount(),
                payment.getStatus(), params, payment.getCreatedAt(), payment.getPaidAt());
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<Long> parseSettlementIds(String value) {
        if (!org.springframework.util.StringUtils.hasText(value)) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).toList();
    }

    private void applyBossCredit(Settlement settlement) {
        if (creditScoreService == null || settlement == null || settlement.getOrderId() == null) return;
        BossOrder order = orderRepository.findById(settlement.getOrderId()).orElse(null);
        if (order == null || order.getCreateBy() == null) return;
        if (settlement.getWage() != null && settlement.getWage().compareTo(new BigDecimal("20")) > 0) {
            creditScoreService.adjust(order.getCreateBy(), CreditScoreService.BOSS_CREDIT, 3,
                    "BOSS_ORDER_SETTLED", "SETTLEMENT", "BOSS_ORDER_SETTLED:" + settlement.getId(),
                    "订单结算成功且金额大于20元");
            if (bossCompletionRateAtLeast90(order.getCreateBy(), settlement.getPayTime())) {
                creditScoreService.adjust(order.getCreateBy(), CreditScoreService.BOSS_CREDIT, 3,
                        "BOSS_COMPLETION_RATE_90D", "SETTLEMENT",
                        "BOSS_COMPLETION_RATE_90D:" + settlement.getId(), "近90天完单率达到90%，完成订单额外加分");
            }
        }
        BaseOrderItem item = itemRepository.findById(settlement.getItemId()).orElse(null);
        if (item != null && item.getFinishAt() != null && settlement.getPayTime() != null
                && !settlement.getPayTime().isBefore(item.getFinishAt())
                && !settlement.getPayTime().isAfter(item.getFinishAt().plusHours(1))) {
            creditScoreService.adjust(order.getCreateBy(), CreditScoreService.BOSS_CREDIT, 2,
                    "BOSS_SETTLE_WITHIN_1H", "SETTLEMENT", "BOSS_SETTLE_WITHIN_1H:" + settlement.getId(),
                    "完工后1小时内完成结算");
        }
    }

    private boolean bossCompletionRateAtLeast90(Long bossId, LocalDateTime now) {
        if (bossId == null || now == null) return false;
        LocalDateTime start = now.minusDays(90);
        int total = 0;
        int completed = 0;
        for (BaseOrderItem item : itemRepository.findByBossIdJoinOrder(bossId)) {
            if (item.getHireDate() == null) continue;
            LocalDateTime occurred = item.getFinishAt() != null
                    ? item.getFinishAt() : item.getHireDate().toLocalDate().atStartOfDay();
            if (occurred.isBefore(start) || occurred.isAfter(now)) continue;
            total++;
            if (BossStatus.ITEM_FINISHED.equals(item.getStatus())
                    || settlementRepository.existsByItemIdAndStatusIn(item.getId(), List.of(SettlementStatus.PAID))) {
                completed++;
            }
        }
        return total > 0 && completed * 100 >= total * 90;
    }

    private void synchronizePaidItemAndOrder(Settlement settlement) {
        BaseOrderItem paidItem = itemRepository.findById(settlement.getItemId()).orElse(null);
        if (paidItem != null && (BossStatus.ITEM_ON_WORK.equals(paidItem.getStatus())
                || BossStatus.ITEM_PENDING_SETTLE.equals(paidItem.getStatus()))) {
            paidItem.setStatus(BossStatus.ITEM_FINISHED);
            itemRepository.save(paidItem);
        }
        markOrderCompletedIfSettled(settlement.getOrderId());
    }

    /** 所有有效录取人员均已完成且结算单全部支付后，订单自动变为已完成。 */
    private void markOrderCompletedIfSettled(Long orderId) {
        BossOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !BossStatus.ORDER_PENDING_SETTLE.equals(order.getOrderStatus())) {
            return;
        }
        List<BaseOrderItem> activeItems = itemRepository.findByOrderId(orderId).stream()
                .filter(i -> BossStatus.ITEM_HIRED.equals(i.getStatus())
                        || BossStatus.ITEM_ON_WORK.equals(i.getStatus())
                        || BossStatus.ITEM_PENDING_SETTLE.equals(i.getStatus())
                        || BossStatus.ITEM_FINISHED.equals(i.getStatus()))
                .toList();
        if (activeItems.isEmpty() || activeItems.stream().anyMatch(i ->
                !BossStatus.ITEM_FINISHED.equals(i.getStatus()))) {
            return;
        }
        List<Settlement> settlements = settlementRepository.findByOrderIdOrderByIdDesc(orderId);
        boolean allPaid = activeItems.stream().allMatch(item -> settlements.stream().anyMatch(settlement ->
                item.getId().equals(settlement.getItemId())
                        && SettlementStatus.PAID.equals(settlement.getStatus())));
        if (allPaid) {
            order.setOrderStatus(BossStatus.ORDER_COMPLETED);
            orderRepository.save(order);
        }
    }

    /** 按订单查结算单 */
    public List<Settlement> listByOrder(Long orderId) {
        return settlementRepository.findByOrderIdOrderByIdDesc(orderId);
    }

    /** 按零工查结算单 */
    public List<Settlement> listByWorker(Long userId) {
        return settlementRepository.findByWorkerIdOrderByIdDesc(userId);
    }

    /** 零工钱包查询（复用钱包服务） */
    public Wallet getWorkerWallet(Long userId) {
        return walletService.getOrCreateWallet(userId);
    }

    /** 结算单详情 */
    public Settlement getSettlementDetail(Long id) {
        return getSettlementOrThrow(id);
    }

    /** 查询老板全部岗位的待结算报名聚合数据。 */
    @Transactional(readOnly = true)
    public List<PendingSettlementOrder> listPendingByBoss(Long bossId) {
        if (bossId == null) {
            throw new IllegalArgumentException("老板用户ID不能为空");
        }
        List<BossOrder> orders = orderRepository.findByCreateByOrderByIdDesc(bossId);
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, BossOrder> ordersById = orders.stream()
                .collect(java.util.stream.Collectors.toMap(BossOrder::getId, item -> item));
        List<BaseOrderItem> allItems = itemRepository.findByBossIdJoinOrder(bossId);
        List<Long> itemIds = allItems.stream().map(BaseOrderItem::getId).filter(Objects::nonNull).toList();
        Map<Long, List<Settlement>> settlementsByItem = itemIds.isEmpty()
                ? Map.of()
                : settlementRepository.findByItemIdInOrderByIdDesc(itemIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(Settlement::getItemId));
        Map<Long, User> usersById = loadUsers(allItems);
        Map<Long, List<PendingSettlementItem>> pendingByOrder = new LinkedHashMap<>();

        for (BaseOrderItem item : allItems) {
            BossOrder order = ordersById.get(item.getOrderId());
            if (order == null) continue;
            List<Settlement> itemSettlements = settlementsByItem.getOrDefault(item.getId(), List.of());
            Settlement pending = itemSettlements.stream()
                    .filter(s -> SettlementStatus.PENDING.equals(s.getStatus()))
                    .findFirst().orElse(null);
            boolean paid = itemSettlements.stream().anyMatch(s -> SettlementStatus.PAID.equals(s.getStatus()));
            if (paid && pending == null) continue;
            if (pending == null
                    && !BossStatus.ITEM_PENDING_SETTLE.equals(item.getStatus())
                    && !BossStatus.ITEM_FINISHED.equals(item.getStatus())) continue;
            PendingSettlementItem view = pending == null
                    ? estimateItem(item, order, usersById.get(item.getUserId()))
                    : toPendingItem(pending, usersById.get(item.getUserId()));
            pendingByOrder.computeIfAbsent(order.getId(), ignored -> new ArrayList<>()).add(view);
        }

        return orders.stream()
                .filter(order -> pendingByOrder.containsKey(order.getId()))
                .map(order -> toPendingOrder(order, pendingByOrder.get(order.getId())))
                .toList();
    }

    private Map<Long, User> loadUsers(List<BaseOrderItem> items) {
        List<Long> userIds = items.stream().map(BaseOrderItem::getUserId)
                .filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) return Map.of();
        return userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, item -> item));
    }

    private PendingSettlementItem estimateItem(BaseOrderItem item, BossOrder order, User worker) {
        if (order.getSalary() == null || order.getSalary().signum() <= 0) {
            throw new IllegalStateException("订单工资未设置，无法计算待结算金额: " + order.getId());
        }
        int days = resolveWorkDays(item, null);
        BigDecimal wage = order.getSalary().multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = wage.add(calculateServiceFee(wage));
        return new PendingSettlementItem(item.getId(), item.getUserId(), workerName(worker), null, null,
                total, toFen(total), days);
    }

    private PendingSettlementItem toPendingItem(Settlement settlement, User worker) {
        BigDecimal amount = settlement.getTotalAmount() == null ? BigDecimal.ZERO : settlement.getTotalAmount();
        return new PendingSettlementItem(settlement.getItemId(), settlement.getWorkerId(), workerName(worker),
                settlement.getId(), settlement.getStatus(), amount, toFen(amount),
                settlement.getWorkDays());
    }

    private PendingSettlementOrder toPendingOrder(BossOrder order, List<PendingSettlementItem> items) {
        BigDecimal amount = items.stream().map(PendingSettlementItem::amount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> workers = items.stream().map(PendingSettlementItem::workerName)
                .filter(org.springframework.util.StringUtils::hasText).distinct().toList();
        boolean hasUncreated = items.stream().anyMatch(item -> item.settlementId() == null);
        boolean hasPending = items.stream().anyMatch(item -> item.settlementId() != null);
        String status = hasUncreated && hasPending ? "partial" : "waiting";
        return new PendingSettlementOrder(order.getId(), order.getId(), order.getDate(),
                org.springframework.util.StringUtils.hasText(order.getOrderTitle())
                        ? order.getOrderTitle() : order.getPostion(),
                amount, toFen(amount), items.size(), workers, status,
                "partial".equals(status) ? "部分结算" : "待结算", items);
    }

    private Long toFen(BigDecimal amount) {
        return amount == null ? null : amount.movePointRight(2).longValueExact();
    }

    private String workerName(User worker) {
        if (worker == null) return "零工";
        if (org.springframework.util.StringUtils.hasText(worker.getNickname())) return worker.getNickname();
        if (org.springframework.util.StringUtils.hasText(worker.getRealName())) return worker.getRealName();
        return "零工#" + worker.getId();
    }


    // ==================== 内部方法 ====================

    private int resolveWorkDays(BaseOrderItem item, Integer workDays) {
        if (workDays != null && workDays > 0) {
            return workDays;
        }
        Date work = item.getWorkDate();
        Date finish = item.getFinishDate();
        if (work != null && finish != null && !finish.before(work)) {
            long span = ChronoUnit.DAYS.between(work.toLocalDate(), finish.toLocalDate()) + 1;
            return Math.max(1, (int) span);
        }
        return 1;
    }

    private BigDecimal calculateServiceFee(BigDecimal wage) {
        BigDecimal rate = configuredServiceFeeRate();
        if (rate.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return wage
                .multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** 后台规则优先；未配置时兼容 kuaima.settle.service-fee-rate。 */
    private BigDecimal configuredServiceFeeRate() {
        BigDecimal fallback = serviceFeeRate == null ? BigDecimal.ZERO : serviceFeeRate.max(BigDecimal.ZERO);
        if (adminSettingRepository == null) return fallback;
        boolean enabled = adminSettingRepository.findById("rules.platformFeeEnabled")
                .map(AdminSetting::getSettingValue)
                .map(String::trim)
                .map(Boolean::parseBoolean)
                .orElse(fallback.signum() > 0);
        if (!enabled) return BigDecimal.ZERO;
        return adminSettingRepository.findById("rules.feeRate")
                .map(AdminSetting::getSettingValue)
                .map(String::trim)
                .map(this::parseRate)
                .orElse(fallback);
    }

    private BigDecimal parseRate(String value) {
        BigDecimal fallback = serviceFeeRate == null ? BigDecimal.ZERO : serviceFeeRate.max(BigDecimal.ZERO);
        try { return new BigDecimal(value).max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)); }
        catch (RuntimeException e) { return fallback; }
    }

    private Settlement getSettlementOrThrow(Long id) {
        return settlementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("结算单不存在: " + id));
    }

    private BaseOrderItem getItemOrThrow(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("报名记录不存在: " + id));
    }
}
