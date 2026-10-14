package com.kuaima.app.admin.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kuaima.app.domain.boss.entity.BossBalanceRechargeOrder;

/**
 * 后台商户账户充值订单查询仓储（与 domain.boss 的 BossBalanceRechargeOrderRepository 同实体）。
 * 注意：boss_balance_recharge_order.amount 以「元」存储，与发薪/余额的「分」不同。
 */
public interface AdminBossRechargeOrderRepository extends JpaRepository<BossBalanceRechargeOrder, Long>,
        JpaSpecificationExecutor<BossBalanceRechargeOrder> {

    /** 某老板累计充值成功金额（单位：元，仅统计 paid/manual_confirmed）。 */
    @Query("""
            select coalesce(sum(o.amount), 0)
            from BossBalanceRechargeOrder o
            where o.bossId = :bossId and o.status in ('paid', 'manual_confirmed')
            """)
    BigDecimal sumPaidAmountByBossId(@Param("bossId") Long bossId);
}
