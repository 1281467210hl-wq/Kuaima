package com.kuaima.app.admin.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.kuaima.app.domain.boss.entity.BossMerchantAccount;

/**
 * 后台商户账户查询/维护仓储（与 domain.boss 的 BossMerchantAccountRepository 同实体、互不改动）。
 * 余额 balance 以「分」存储。
 */
public interface AdminBossMerchantAccountRepository extends JpaRepository<BossMerchantAccount, Long>,
        JpaSpecificationExecutor<BossMerchantAccount> {

    /** 某老板下未软删除的账户，用于默认标记互斥与商户号重复校验。 */
    List<BossMerchantAccount> findByBossIdAndStatusNotOrderByIdDesc(Long bossId, String status);

    /** 商户号是否已被其他有效账户占用（deleted 之外唯一）。 */
    List<BossMerchantAccount> findByMerchantNoAndStatusNot(String merchantNo, String status);

    /** 全平台有效账户余额合计（单位：分，排除软删除）。 */
    @Query("select coalesce(sum(a.balance), 0) from BossMerchantAccount a where a.status <> 'deleted'")
    Long sumBalanceExcludingDeleted();

    /** 某老板有效账户余额合计（单位：分，排除软删除）。 */
    @Query("select coalesce(sum(a.balance), 0) from BossMerchantAccount a "
            + "where a.bossId = :bossId and a.status <> 'deleted'")
    Long sumBalanceByBossId(Long bossId);
}

