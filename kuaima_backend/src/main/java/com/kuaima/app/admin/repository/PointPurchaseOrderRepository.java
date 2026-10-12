package com.kuaima.app.admin.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import com.kuaima.app.admin.entity.PointPurchaseOrder;

public interface PointPurchaseOrderRepository extends JpaRepository<PointPurchaseOrder, Long> {
    Optional<PointPurchaseOrder> findByIdempotencyKey(String key);
    Optional<PointPurchaseOrder> findByOrderNoAndBossId(String orderNo, Long bossId);
    Optional<PointPurchaseOrder> findByOrderNo(String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PointPurchaseOrder o where o.orderNo = :orderNo")
    Optional<PointPurchaseOrder> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Query("""
        select o from PointPurchaseOrder o where
        (:keyword is null or lower(o.orderNo) like lower(concat('%', :keyword, '%'))
          or cast(o.bossId as string) like concat('%', :keyword, '%')
          or exists (select u.id from User u where u.id = o.bossId and
            (lower(coalesce(u.companyCode,'')) like lower(concat('%', :keyword, '%'))
             or lower(coalesce(u.bossCode,'')) like lower(concat('%', :keyword, '%'))))
          or lower(coalesce(o.companyName,'')) like lower(concat('%', :keyword, '%'))
          or lower(coalesce(o.bossName,'')) like lower(concat('%', :keyword, '%')))
        and (:payMethod is null or o.payMethod = :payMethod)
        and (:status is null or o.status = :status)
        and (:fromTime is null or o.purchaseTime >= :fromTime)
        and (:toTime is null or o.purchaseTime < :toTime)
        order by o.purchaseTime desc, o.id desc
        """)
    Page<PointPurchaseOrder> search(@Param("keyword") String keyword,
                                    @Param("payMethod") String payMethod,
                                    @Param("status") String status,
                                    @Param("fromTime") LocalDateTime fromTime,
                                    @Param("toTime") LocalDateTime toTime,
                                    Pageable pageable);

    @Query("select o from PointPurchaseOrder o where (:keyword is null or lower(o.orderNo) like lower(concat('%', :keyword, '%')) or cast(o.bossId as string) like concat('%', :keyword, '%') or exists (select u.id from User u where u.id = o.bossId and (lower(coalesce(u.companyCode,'')) like lower(concat('%', :keyword, '%')) or lower(coalesce(u.bossCode,'')) like lower(concat('%', :keyword, '%')))) or lower(coalesce(o.companyName,'')) like lower(concat('%', :keyword, '%')) or lower(coalesce(o.bossName,'')) like lower(concat('%', :keyword, '%'))) and (:payMethod is null or o.payMethod = :payMethod) and (:status is null or o.status = :status) and (:fromTime is null or o.purchaseTime >= :fromTime) and (:toTime is null or o.purchaseTime < :toTime) order by o.purchaseTime desc, o.id desc")
    List<PointPurchaseOrder> searchAll(@Param("keyword") String keyword,
                                       @Param("payMethod") String payMethod,
                                       @Param("status") String status,
                                       @Param("fromTime") LocalDateTime fromTime,
                                       @Param("toTime") LocalDateTime toTime);

    @Query("select count(o) from PointPurchaseOrder o where o.status = '支付成功' and o.purchaseTime >= :fromTime and o.purchaseTime < :toTime")
    long countPaid(@Param("fromTime") LocalDateTime fromTime, @Param("toTime") LocalDateTime toTime);

    @Query("select coalesce(sum(o.points),0) from PointPurchaseOrder o where o.status = '支付成功' and o.purchaseTime >= :fromTime and o.purchaseTime < :toTime")
    Long sumPaidPoints(@Param("fromTime") LocalDateTime fromTime, @Param("toTime") LocalDateTime toTime);

    @Query("select coalesce(sum(o.amount),0) from PointPurchaseOrder o where o.status = '支付成功' and o.purchaseTime >= :fromTime and o.purchaseTime < :toTime")
    java.math.BigDecimal sumPaidAmount(@Param("fromTime") LocalDateTime fromTime, @Param("toTime") LocalDateTime toTime);
}
