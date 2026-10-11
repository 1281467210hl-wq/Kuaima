package com.kuaima.app.domain.insurance.entity;

import java.time.LocalDateTime;

import com.kuaima.app.domain.base.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 平台与保险公司合作的意外险产品配置；tiers 存档位 JSON 数组。
 */
@Entity
@Table(name = "insurance_product")
@Getter
@Setter
public class InsuranceProduct extends BaseEntity {

    @Column(length = 50, comment = "保险公司")
    private String carrier;

    @Column(length = 50, unique = true, comment = "产品编码")
    private String code;

    @Column(nullable = false, comment = "启用状态")
    private Boolean enabled = true;

    @Column(columnDefinition = "TEXT", comment = "保险档位 JSON：保额/身故残疾/意外医疗/积分等")
    private String tiers;

    @Column(length = 255, comment = "备注")
    private String remark;

    @Column(comment = "创建时间")
    private LocalDateTime createTime;

    @Column(comment = "更新时间")
    private LocalDateTime updateTime;
}
