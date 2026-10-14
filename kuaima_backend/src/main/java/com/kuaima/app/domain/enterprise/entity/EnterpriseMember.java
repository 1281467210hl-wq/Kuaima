package com.kuaima.app.domain.enterprise.entity;

import com.kuaima.app.domain.base.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** 用户与企业的成员关系；OWNER 是完成企业认证的账号。 */
@Entity
@Table(name = "enterprise_member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_enterprise_member", columnNames = {"enterprise_id", "user_id"})
})
@Getter
@Setter
public class EnterpriseMember extends BaseEntity {
    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "member_role", nullable = false, length = 30)
    private String memberRole = "STAFF";
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    @Column(columnDefinition = "TEXT")
    private String permissions;
    @Column(name = "portal_enabled", nullable = false)
    private Boolean portalEnabled = false;
    @Column(name = "authorized_by")
    private Long authorizedBy;
    @Column(name = "authorized_at")
    private java.time.LocalDateTime authorizedAt;
    @Column(name = "permission_version", nullable = false)
    private Integer permissionVersion = 1;
    @Column(length = 50)
    private String title;
    /** 备注（老板端成员编辑页维护，最长 500 字）。 */
    @Column(length = 500)
    private String remark;
    @Column(name = "invited_by")
    private Long invitedBy;
    /** 绑定角色 employee_role.id；为空表示仅按 member_role 粗粒度角色授权。 */
    @Column(name = "role_id")
    private Long roleId;
}
