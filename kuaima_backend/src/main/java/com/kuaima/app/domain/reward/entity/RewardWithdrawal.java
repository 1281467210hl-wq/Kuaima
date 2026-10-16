package com.kuaima.app.domain.reward.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reward_withdrawal", indexes = @Index(name = "idx_reward_withdrawal_user_applied", columnList = "user_id,applied_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_reward_withdrawal_key", columnNames = "idempotency_key"))
@Getter @Setter
public class RewardWithdrawal {
    public void setAmount(Long value) { this.amount = value == null ? null : BigDecimal.valueOf(value); }
    public void setAmount(long value) { this.amount = BigDecimal.valueOf(value); }
    public void setAmount(BigDecimal value) { this.amount = value; }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 20) private String role = "USER";
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 20) private String channel = "WECHAT";
    @Column(name = "payee_account", length = 128) private String payeeAccount;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "merchant_batch_no", length = 64) private String merchantBatchNo;
    @Column(name = "merchant_detail_no", length = 64) private String merchantDetailNo;
    @Column(name = "wechat_transfer_no", length = 64) private String wechatTransferNo;
    @Column(name = "transfer_response", columnDefinition = "TEXT") private String transferResponse;
    @Column(name = "applied_at", nullable = false) private LocalDateTime appliedAt;
    @Column(name = "paid_at") private LocalDateTime paidAt;
    @Column(name = "failure_reason", length = 500) private String failureReason;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }
}
