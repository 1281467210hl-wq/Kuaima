package com.kuaima.app.admin.entity;

import java.time.LocalDateTime;

import com.alibaba.fastjson2.annotation.JSONField;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 后台管理员账号（独立于微信用户）
 */
@Entity
@Table(name = "admin_user")
@Getter
@Setter
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    @JSONField(serialize = false)
    private String password;

    @Column(length = 50)
    private String name;

    /** 头像（Base64 或 URL） */
    @Column(columnDefinition = "TEXT")
    private String avatar;

    /** 角色: SUPER_ADMIN / ADMIN / EDITOR / VIEWER */
    @Column(nullable = false, length = 30)
    private String role;

    @Column(length = 50)
    private String dept;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    /** 备注说明 */
    @Column(length = 255)
    private String remark;

    /** 权限树 JSON 快照，如 {"permUser":{"u1":true,"u2":false}} */
    @Column(columnDefinition = "TEXT")
    private String permissions;

    /** 状态: 启用 / 禁用 */
    @Column(nullable = false, length = 20)
    private String status = "启用";

    /** 是否允许登录；关闭后账号即使启用也不能登录后台 */
    @Column(nullable = false)
    private Boolean allowLogin = true;

    /** 是否接收系统通知 */
    @Column(nullable = false)
    private Boolean receiveNotifications = true;

    /** 创建者管理员 ID（who created this admin account）；新建时由当前登录管理员填入 */
    @Column
    private Long createdBy;

    @Column
    private LocalDateTime lastLoginTime;

    @Column
    private LocalDateTime createTime;

    @Column
    private LocalDateTime updateTime;
}
