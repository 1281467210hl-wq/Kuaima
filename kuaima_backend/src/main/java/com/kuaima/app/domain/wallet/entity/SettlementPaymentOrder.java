package com.kuaima.app.domain.wallet.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** 老板通过微信支付一笔或多笔工资结算的支付单。 */
@Entity
@Table(name = "settlement_payment_order", uniqueConstraints = {
        @UniqueConstraint(name = "uk_settlement_payment_no", columnNames = "payment_no"),
        @UniqueConstraint(name = "uk_settlement_payment_idempotency", columnNames = "idempotency_key")
}, indexes = {
        @Index(name = "idx_settlement_payment_boss", columnList = "boss_id,created_at"),
        @Index(name = "idx_settlement_payment_status", columnList = "status,created_at")
})
@Getter
@Setter
public class SettlementPaymentOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_no", nullable = false, length = 48)
    private String paymentNo;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "boss_id", nullable = false)
    private Long bossId;

    @Column(name = "settlement_ids", nullable = false, length = 2000)
    private String settlementIds;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Column(name = "coupon_deduct_amount", precision = 18, scale = 2)
    private BigDecimal couponDeductAmount = BigDecimal.ZERO;

    @Column(name = "points_used")
    private Integer pointsUsed = 0;

    @Column(name = "points_deduct_amount", precision = 18, scale = 2)
    private BigDecimal pointsDeductAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "pay_params", length = 4000)
    private String payParams;

    @Column(name = "wechat_transaction_id", length = 64)
    private String wechatTransactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
