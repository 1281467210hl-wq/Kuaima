package com.kuaima.app.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/** 系统设置（KV 存储） */
@Entity
@Table(name = "admin_setting")
@Getter @Setter
public class AdminSetting {
    @Id
    @Column(length = 100)
    private String settingKey;

    @Column(columnDefinition = "TEXT")
    private String settingValue;

    @Column(length = 50)
    private String category;   // platform / notification / security / points

    @Column(length = 200)
    private String description;

    /** 展示地址：数据库仍保存 objectKey，接口返回时填充限时签名 URL。 */
    @Transient
    private String displayValue;
}
