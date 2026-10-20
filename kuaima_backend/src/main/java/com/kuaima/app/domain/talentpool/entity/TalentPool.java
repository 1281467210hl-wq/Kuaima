package com.kuaima.app.domain.talentpool.entity;

import java.math.BigDecimal;

import com.kuaima.app.domain.base.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "talent_pool", indexes = {
        @Index(name = "idx_tp_boss", columnList = "boss_id"),
        @Index(name = "idx_tp_worker", columnList = "worker_id"),
        @Index(name = "idx_tp_type", columnList = "type"),
        @Index(name = "idx_tp_favorite", columnList = "favorite")
}, uniqueConstraints = @UniqueConstraint(
        name = "uk_talent_pool_boss_worker",
        columnNames = {"boss_id", "worker_id"}
))
@Setter
@Getter
public class TalentPool extends BaseEntity {

    @Column(name = "boss_id", nullable = false, columnDefinition = "BIGINT COMMENT '老板用户 id'")
    private Long bossId;

    @Column(name = "worker_id", columnDefinition = "BIGINT COMMENT '关联零工用户 id'")
    private Long workerId;

    @Column(name = "`name`", nullable = false, columnDefinition = "VARCHAR(50) COMMENT '零工姓名/昵称'")
    private String name;

    @Column(columnDefinition = "VARCHAR(20) COMMENT '联系电话（脱敏）'")
    private String phone;

    @Column(name = "avatar_color", columnDefinition = "VARCHAR(60) COMMENT '头像渐变色'")
    private String avatarColor;

    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'new' COMMENT '类型:skilled熟练工/new新零工'")
    private String type;

    @Column(columnDefinition = "VARCHAR(50) COMMENT '经验描述'")
    private String experience;

    @Column(name = "work_years", columnDefinition = "INT COMMENT '工作年限（年）'")
    private Integer workYears;

    @Column(columnDefinition = "VARCHAR(50) COMMENT '工种类别'")
    private String category;

    @Column(name = "good_rate", columnDefinition = "VARCHAR(10) COMMENT '好评率'")
    private String goodRate;

    @Column(precision = 3, scale = 1, columnDefinition = "DECIMAL(3,1) COMMENT '综合评分'")
    private BigDecimal rating;

    @Column(name = "completed_orders", nullable = false, columnDefinition = "INT DEFAULT 0 COMMENT '完成订单数'")
    private Integer completedOrders;

    @Column(name = "arrival_rate", columnDefinition = "VARCHAR(10) COMMENT '到岗率'")
    private String arrivalRate;

    @Column(columnDefinition = "VARCHAR(200) COMMENT '技能标签，逗号分隔'")
    private String skills;

    @Column(columnDefinition = "VARCHAR(100) COMMENT '常驻区域'")
    private String region;

    @Column(name = "available_time", columnDefinition = "VARCHAR(100) COMMENT '可工作时间'")
    private String availableTime;

    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0 COMMENT '是否收藏'")
    private Boolean favorite;

    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'auto' COMMENT '来源:auto自动加入/order订单加入/qr扫码加入/manual手动加入'")
    private String source;
}
