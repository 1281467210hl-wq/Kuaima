package com.kuaima.app.domain.reward.repository;

import com.kuaima.app.domain.reward.entity.RewardFlow;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RewardFlowRepository extends JpaRepository<RewardFlow, Long> {
    Optional<RewardFlow> findBySourceKey(String sourceKey);
    /** 兼容唯一索引建立前的重复历史流水，幂等检查不再因重复记录抛异常。 */
    List<RewardFlow> findAllBySourceKeyOrderByIdDesc(String sourceKey);
    Page<RewardFlow> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
    Page<RewardFlow> findByUserIdAndRoleOrderByCreatedAtDescIdDesc(Long userId, String role, Pageable pageable);
    Page<RewardFlow> findByUserIdAndTypeOrderByCreatedAtDescIdDesc(Long userId, String type, Pageable pageable);
    Page<RewardFlow> findByUserIdAndRoleAndTypeOrderByCreatedAtDescIdDesc(Long userId, String role, String type, Pageable pageable);
    Optional<RewardFlow> findByUserIdAndBizTypeAndBizId(Long userId, String bizType, Long bizId);
    Optional<RewardFlow> findByUserIdAndRoleAndBizTypeAndBizId(Long userId, String role, String bizType, Long bizId);
    @Query("select coalesce(sum(f.amount),0) from RewardFlow f where f.userId=:userId and f.type=:type")
    java.math.BigDecimal sumByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);
    @Query("select coalesce(sum(f.amount),0) from RewardFlow f where f.userId=:userId and f.role=:role and f.type=:type")
    java.math.BigDecimal sumByUserIdAndRoleAndType(@Param("userId") Long userId, @Param("role") String role, @Param("type") String type);
}
