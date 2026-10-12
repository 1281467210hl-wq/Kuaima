package com.kuaima.app.domain.reward.repository;

import com.kuaima.app.domain.reward.entity.RewardAccount;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RewardAccountRepository extends JpaRepository<RewardAccount, Long> {
    Optional<RewardAccount> findByUserId(Long userId);
    Optional<RewardAccount> findByUserIdAndRole(Long userId, String role);
    /** 兼容历史重复数据：按主键倒序取指定身份最新奖励金账户。 */
    Optional<RewardAccount> findFirstByUserIdAndRoleOrderByIdDesc(Long userId, String role);
    Optional<RewardAccount> findFirstByUserIdOrderByIdDesc(Long userId);
    List<RewardAccount> findByUserIdIn(Collection<Long> userIds);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RewardAccount a where a.userId=:userId")
    Optional<RewardAccount> findByUserIdForUpdate(@Param("userId") Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RewardAccount a where a.userId=:userId and a.role=:role")
    Optional<RewardAccount> findByUserIdAndRoleForUpdate(@Param("userId") Long userId, @Param("role") String role);
    /** 兼容历史重复账户：加锁后按主键倒序返回，业务层选最新一条。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RewardAccount a where a.userId=:userId and a.role=:role order by a.id desc")
    List<RewardAccount> findAllByUserIdAndRoleForUpdate(@Param("userId") Long userId, @Param("role") String role);
}
