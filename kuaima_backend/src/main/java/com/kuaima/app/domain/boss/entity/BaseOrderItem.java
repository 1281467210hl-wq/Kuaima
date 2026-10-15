package com.kuaima.app.domain.boss.entity;

import java.sql.Date;
import java.time.LocalDateTime;

import com.kuaima.app.domain.base.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "boss_order_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_boss_order_item_order_user",
                columnNames = {"order_id", "user_id"}
        )
)
@Getter
@Setter
public class BaseOrderItem extends BaseEntity {
    
    @Column
    private Long orderId;

    @Column(comment = "报名用户")
    private Long userId;

    @Column(comment = "报名状态:已报名，已录用，已到岗，已完成，取消招工，取消报名")
    private String status;

    @Column(comment = "我要试工：仅月结订单报名时可勾选")
    private Boolean trialRequested;

    @Column(comment = "报名备注")
    private String remark;

    @Column(comment = "报名时间")
    private Date applyDate;

    @Column(name = "apply_at")
    private LocalDateTime applyAt;

    @Column(comment = "录用时间")
    private Date hireDate;

    @Column(name = "hire_at")
    private LocalDateTime hireAt;

    @Column(comment = "到岗时间")
    private Date workDate;

    @Column(name = "work_at")
    private LocalDateTime workAt;

    @Column(comment = "完成时间")
    private Date finishDate;

    @Column(name = "finish_at", comment = "确认完工时间，供24小时结算率精确统计")
    private LocalDateTime finishAt;

    @Column(comment = "取消时间")
    private Date cancelDate;

    @Column(comment = "取消原因")
    private String cancelReason;

    @Column(name = "early_leave", comment = "是否通过早退流程提前完工")
    private Boolean earlyLeave = false;

    /** 报名用户昵称（查询返回字段，不落库）。 */
    @Transient
    private String nickname;


}
