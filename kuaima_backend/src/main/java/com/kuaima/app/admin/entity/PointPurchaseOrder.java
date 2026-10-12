package com.kuaima.app.admin.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "point_purchase_order", indexes = {
        @Index(name = "idx_point_purchase_boss", columnList = "boss_id"),
        @Index(name = "idx_point_purchase_status", columnList = "status"),
        @Index(name = "idx_point_purchase_method", columnList = "pay_method"),
        @Index(name = "idx_point_purchase_time", columnList = "purchase_time")
})
@Getter @Setter
public class PointPurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    private String orderNo;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;
    @Column(name = "boss_id", nullable = false)
    private Long bossId;
    @Column(length = 100)
    private String bossName;
    @Column(length = 150)
    private String companyName;
    @Column(nullable = false)
    private Long points;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "pay_method", nullable = false, length = 20)
    private String payMethod;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "purchase_time", nullable = false)
    private LocalDateTime purchaseTime;
    @Column(length = 500)
    private String remark;
    private Long operatorId;
    @Column(length = 100)
    private String operatorName;
    private LocalDateTime operatorTime;
    private Boolean pointsGranted = false;
    @Column(name = "wechat_transaction_id", length = 64)
    private String wechatTransactionId;
    @Column(name = "wechat_code_url", length = 512)
    private String wechatCodeUrl;
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
