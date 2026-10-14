package com.kuaima.app.admin.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kuaima.app.domain.payroll.entity.PayrollDetail;

/**
 * 后台转账记录查询用明细仓储（与 domain.payroll 的 PayrollDetailRepository 同实体、互不改动）。
 */
public interface AdminPayrollDetailRepository extends JpaRepository<PayrollDetail, Long>,
        JpaSpecificationExecutor<PayrollDetail> {

    /** 按发薪单聚合明细状态与金额（单位：分），用于派生转账状态、计算收款方展示信息。 */
    @Query("""
            select d.payrollId as payrollId,
                   d.status as status,
                   count(d) as totalCount,
                   coalesce(sum(d.amount), 0) as totalAmount
            from PayrollDetail d
            where d.payrollId in :payrollIds
            group by d.payrollId, d.status
            """)
    List<PayrollStatusAggregate> aggregateStatusByPayrollIds(@Param("payrollIds") Collection<Long> payrollIds);

    /** 当前页转账单的明细人员（姓名/手机号掩码展示用）。 */
    List<PayrollDetail> findByPayrollIdIn(Collection<Long> payrollIds);

    /** 单笔转账单的人员明细，按入库顺序返回。 */
    List<PayrollDetail> findByPayrollIdOrderByIdAsc(Long payrollId);

    /** 明细状态聚合投影。 */
    interface PayrollStatusAggregate {
        Long getPayrollId();

        String getStatus();

        Long getTotalCount();

        Long getTotalAmount();
    }
}
