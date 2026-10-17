package com.kuaima.app.domain.wallet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kuaima.app.domain.wallet.entity.SettlementPaymentOrder;

import jakarta.persistence.LockModeType;

public interface SettlementPaymentOrderRepository extends JpaRepository<SettlementPaymentOrder, Long> {
    Optional<SettlementPaymentOrder> findByIdempotencyKey(String idempotencyKey);
    Optional<SettlementPaymentOrder> findByPaymentNo(String paymentNo);
    Optional<SettlementPaymentOrder> findByPaymentNoAndBossId(String paymentNo, Long bossId);
    List<SettlementPaymentOrder> findByBossIdOrderByCreatedAtDescIdDesc(Long bossId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SettlementPaymentOrder o where o.paymentNo = :paymentNo")
    Optional<SettlementPaymentOrder> findByPaymentNoForUpdate(@Param("paymentNo") String paymentNo);
}
