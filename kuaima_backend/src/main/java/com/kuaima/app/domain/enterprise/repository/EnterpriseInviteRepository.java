package com.kuaima.app.domain.enterprise.repository;

import com.kuaima.app.domain.enterprise.entity.EnterpriseInvite;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EnterpriseInviteRepository extends JpaRepository<EnterpriseInvite, Long> {

    @Query("select i from EnterpriseInvite i where i.enterpriseId = :enterpriseId" +
            " and (:status is null or i.status = :status)" +
            " order by i.id desc")
    Page<EnterpriseInvite> search(@Param("enterpriseId") Long enterpriseId,
                                   @Param("status") String status,
                                   Pageable pageable);

    Optional<EnterpriseInvite> findByInviteCode(String inviteCode);

    long countByEnterpriseIdAndStatus(Long enterpriseId, String status);

    List<EnterpriseInvite> findByEnterpriseIdAndPhoneAndStatusOrderByIdDesc(
            Long enterpriseId, String phone, String status);

    List<EnterpriseInvite> findByPhoneAndStatusOrderByIdDesc(String phone, String status);

    @Modifying
    @Transactional
    @Query("update EnterpriseInvite i set i.status = 'EXPIRED'" +
            " where i.inviterId = :inviterId and i.status = 'PENDING'")
    int expirePendingInvites(@Param("inviterId") Long inviterId);
}
