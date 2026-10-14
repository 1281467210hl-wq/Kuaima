package com.kuaima.app.domain.enterprise.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kuaima.app.domain.enterprise.entity.EnterpriseMemberProject;

/** 企业成员-项目参与关系仓储。 */
public interface EnterpriseMemberProjectRepository extends JpaRepository<EnterpriseMemberProject, Long> {

    List<EnterpriseMemberProject> findByMemberId(Long memberId);

    List<EnterpriseMemberProject> findByMemberIdAndStatus(Long memberId, String status);

    List<EnterpriseMemberProject> findByMemberIdIn(Collection<Long> memberIds);

    Optional<EnterpriseMemberProject> findByMemberIdAndProjectId(Long memberId, Long projectId);
}
