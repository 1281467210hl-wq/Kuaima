package com.kuaima.app.domain.enterprise.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kuaima.app.domain.enterprise.entity.EnterpriseMember;

/**
 * 后台-企业成员专用查询仓储（admin 企业成员页使用，不与老板端共用查询定义）。
 * 与 EnterpriseMemberRepository 指向同一实体，仅补充后台分页/联表筛选查询。
 */
public interface AdminEnterpriseMemberRepository extends JpaRepository<EnterpriseMember, Long> {

    /**
     * 成员分页查询：状态/角色精确匹配，roleId 按 employee_role 绑定 id 精确匹配（自定义角色筛选），
     * company 按所属企业名称过滤，keyword 关联 sys_user 的昵称/账号/手机号及成员职位。
     */
    @Query("""
            select m from EnterpriseMember m
            where m.enterpriseId = :enterpriseId
              and (:status is null or m.status = :status)
              and (:memberRole is null or m.memberRole = :memberRole)
              and (:roleId is null or m.roleId = :roleId)
              and (:company is null or exists (
                       select 1 from Enterprise e
                       where e.id = m.enterpriseId and e.companyName = :company))
              and (:keyword is null
                   or m.title like %:keyword%
                   or exists (select 1 from User u where u.id = m.userId
                              and (u.nickname like %:keyword% or u.username like %:keyword%
                                   or u.phone like %:keyword%)))
            order by m.id desc
            """)
    Page<EnterpriseMember> search(@Param("enterpriseId") Long enterpriseId,
                                  @Param("status") String status,
                                  @Param("memberRole") String memberRole,
                                  @Param("roleId") Long roleId,
                                  @Param("company") String company,
                                  @Param("keyword") String keyword,
                                  Pageable pageable);

    List<EnterpriseMember> findByEnterpriseIdAndStatus(Long enterpriseId, String status);
}
