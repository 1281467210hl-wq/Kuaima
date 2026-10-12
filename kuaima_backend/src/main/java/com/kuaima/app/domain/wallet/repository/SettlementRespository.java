package com.kuaima.app.domain.wallet.repository;

import java.util.List;
import java.util.Collection;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import com.kuaima.app.domain.wallet.entity.Settlement;

public interface SettlementRespository extends JpaRepository<Settlement, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.id = :id")
    java.util.Optional<Settlement> findByIdForUpdate(@Param("id") Long id);

    /** 后台结算管理组合筛选。 */
    @Query("""
            select s from Settlement s
            where (:status is null or s.status = :status)
              and (:startTime is null or s.timestamp >= :startTime)
              and (:endTime is null or s.timestamp <= :endTime)
            """)
    Page<Settlement> search(@Param("status") String status,
                            @Param("startTime") Timestamp startTime,
                            @Param("endTime") Timestamp endTime,
                            Pageable pageable);

    long countByStatus(String status);

    @Query("select coalesce(sum(s.wage),0) from Settlement s where s.status = :status")
    Long sumWageByStatus(@Param("status") String status);

    /** 累计营收：已支付结算单的老板实付总额（元）。 */
    @Query("select coalesce(sum(s.totalAmount),0) from Settlement s where s.status = '已支付'")
    java.math.BigDecimal sumPaidTotalAmount();

    @Query("select coalesce(sum(s.wage),0) from Settlement s where s.status = :status and s.payTime >= :startTime and s.payTime < :endTime")
    Long sumWageByStatusAndPayTimeBetween(@Param("status") String status,
                                          @Param("startTime") java.time.LocalDateTime startTime,
                                          @Param("endTime") java.time.LocalDateTime endTime);

    /** 某订单的结算单（最新在前） */
    List<Settlement> findByOrderIdOrderByIdDesc(Long orderId);

    List<Settlement> findByItemIdInOrderByIdDesc(Collection<Long> itemIds);

    List<Settlement> findByOrderIdIn(Collection<Long> orderIds);

    /** 某零工的结算单（最新在前） */
    List<Settlement> findByWorkerIdOrderByIdDesc(Long workerId);

    @Query("select coalesce(sum(s.wage),0) from Settlement s where s.workerId = :workerId and s.status = '已支付'")
    Long sumPaidWageByWorkerId(@Param("workerId") Long workerId);

    /** 已形成结算记录的实际总工作天数。 */
    @Query("select coalesce(sum(s.workDays),0) from Settlement s where s.workerId = :workerId and s.status in ('待支付','已支付')")
    Long sumWorkDaysByWorkerId(@Param("workerId") Long workerId);

    /** 通过早退流程结束的实际工作天数。 */
    @Query("select coalesce(sum(s.workDays),0) from Settlement s join BaseOrderItem i on s.itemId = i.id where s.workerId = :workerId and i.earlyLeave = true and s.status in ('待支付','已支付')")
    Long sumEarlyLeaveWorkDaysByWorkerId(@Param("workerId") Long workerId);

    /** 某报名记录是否存在待支付/已支付的结算单（防重复结算） */
    boolean existsByItemIdAndStatusIn(Long itemId, java.util.Collection<String> statuses);

    /** 老板名下最新结算记录（通过订单归属隔离）。 */
    @Query("select s from Settlement s join BossOrder o on s.orderId = o.id where o.createBy = :bossId order by s.id desc")
    Page<Settlement> findByBossId(@Param("bossId") Long bossId, Pageable pageable);

    @Query("""
            select s from Settlement s join BaseOrderItem i on s.itemId = i.id
            where s.status = '待支付' and i.finishAt is not null
              and i.finishAt >= :effectiveFrom and i.finishAt <= :cutoff
            """)
    List<Settlement> findPendingFinishedBetween(@Param("effectiveFrom") LocalDateTime effectiveFrom,
                                                @Param("cutoff") LocalDateTime cutoff);
}
