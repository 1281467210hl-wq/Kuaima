package com.kuaima.app.domain.wallet.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class SettlementPaymentModels {
    private SettlementPaymentModels() {}

    public record WechatPayRequest(List<Long> settlementIds, Long userCouponId, Integer pointsToUse) {}

    public record SettlementFeePreview(List<Long> settlementIds, BigDecimal amount,
                                       BigDecimal orderAmount, BigDecimal serviceFee,
                                       Integer pointsAvailable, Integer pointsDeductRate,
                                       BigDecimal pointsDeductAmount, Boolean couponAvailable,
                                       String couponName, List<Map<String, Object>> couponOptions,
                                       Integer pointsUsed, BigDecimal couponDeductAmount,
                                       BigDecimal payableAmount) {}

    public record WechatPayView(String paymentNo, List<Long> settlementIds, BigDecimal amount,
                                BigDecimal orderAmount, BigDecimal serviceFee,
                                Integer pointsAvailable, Integer pointsDeductRate,
                                BigDecimal pointsDeductAmount, Boolean couponAvailable,
                                String couponName, Integer pointsUsed, BigDecimal couponDeductAmount,
                                BigDecimal payableAmount, String status, Map<String, Object> payParams,
                                LocalDateTime createdAt, LocalDateTime paidAt) {}
}
