package com.kuaima.app.domain.wallet.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kuaima.app.domain.wallet.entity.Wallet;

public interface WalletRespository extends JpaRepository<Wallet, Long> {

    /** 按用户查钱包 */
    Optional<Wallet> findByUserId(Long userId);
    Optional<Wallet> findByUserIdAndRole(Long userId, String role);
    /** 兼容历史重复数据：按主键倒序取指定身份最新钱包，避免 Optional 单结果查询抛异常。 */
    Optional<Wallet> findFirstByUserIdAndRoleOrderByIdDesc(Long userId, String role);
    Optional<Wallet> findFirstByUserIdOrderByIdDesc(Long userId);

    List<Wallet> findByUserIdIn(java.util.Collection<Long> userIds);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId=:userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") Long userId);
    /**
     * 历史数据可能在增加 user_id + role 唯一约束前产生重复钱包；加锁后按主键倒序取最新一条，
     * 避免 Optional 单结果查询因重复历史数据直接抛出 NonUniqueResultException。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId=:userId and w.role=:role order by w.id desc")
    List<Wallet> findAllByUserIdAndRoleForUpdate(@Param("userId") Long userId, @Param("role") String role);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId=:userId and w.role=:role")
    Optional<Wallet> findByUserIdAndRoleForUpdate(@Param("userId") Long userId, @Param("role") String role);
}
