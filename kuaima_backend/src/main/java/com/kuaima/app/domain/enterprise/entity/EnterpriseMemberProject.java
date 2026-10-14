package com.kuaima.app.domain.enterprise.entity;

import java.time.LocalDate;

import com.kuaima.app.domain.base.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 企业成员参与项目的关系：enterprise_member × project 的关联表。 */
@Entity
@Table(name = "enterprise_member_project", indexes = {
        @Index(name = "idx_emp_member", columnList = "member_id"),
        @Index(name = "idx_emp_project", columnList = "project_id")
})
@Getter
@Setter
public class EnterpriseMemberProject extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 项目中担任的角色，如 项目负责人、人事主管。 */
    @Column(name = "role_in_project", length = 50)
    private String roleInProject;

    /** 加入项目日期。 */
    @Column(name = "joined_date")
    private LocalDate joinedDate;

    /** ACTIVE 参与中 / LEFT 已退出。 */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}
