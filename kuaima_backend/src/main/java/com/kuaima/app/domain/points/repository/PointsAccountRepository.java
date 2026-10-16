package com.kuaima.app.domain.points.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import com.kuaima.app.domain.points.entity.PointsAccount;

public interface PointsAccountRepository extends JpaRepository<PointsAccount, Long> {

    /** 按用户 + 身份查积分账户。 */
    Optional<PointsAccount> findByUserIdAndRole(Long userId, String role);
    /** 兼容历史重复数据：按主键倒序取指定身份最新积分账户。 */
    Optional<PointsAccount> findFirstByUserIdAndRoleOrderByIdDesc(Long userId, String role);

    List<PointsAccount> findByUserIdInAndRole(Collection<Long> userIds, String role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from PointsAccount a where a.userId = :userId and a.role = :role")
    Optional<PointsAccount> findByUserIdAndRoleForUpdate(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("role") String role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select a from PointsAccount a where a.userId = :userId and a.role = :role order by a.id desc")
    List<PointsAccount> findAllByUserIdAndRoleForUpdate(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("role") String role);
}
