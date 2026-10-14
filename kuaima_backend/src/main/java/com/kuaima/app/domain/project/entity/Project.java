package com.kuaima.app.domain.project.entity;

import java.time.LocalDate;

import com.kuaima.app.domain.base.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目（老板端项目管理核心实体）。 */
@Entity
@Table(name = "project", indexes = {
        @Index(name = "idx_project_status", columnList = "`status`"),
        @Index(name = "idx_project_company", columnList = "company_name")
})
@Setter
@Getter
public class Project extends BaseEntity {

    @Column(name = "idempotency_key", unique = true, length = 64,
            comment = "创建幂等键（客户端每次创建意图生成一个 UUID），防重复提交；可空")
    private String idempotencyKey;

    @Column(comment = "项目编号，如 PRJ-2026-001，为空时接口按 PRJ-{id} 兜底")
    private String code;

    @Column(comment = "项目名称")
    private String name;

    @Column(name = "company_name", comment = "用工企业")
    private String companyName;

    @Column(name = "enterprise_id", comment = "所属企业")
    private Long enterpriseId;

    @Column(comment = "负责人姓名")
    private String leaderName;

    @Column(comment = "负责人电话")
    private String leaderPhone;

    @Column(name = "establish_date", comment = "立项日期")
    private LocalDate establishDate;

    @Column(comment = "项目地点")
    private String location;

    @Column(name = "`status`", comment = "状态:active进行中/archived已归档/deleted已删除")
    private String status = "active";

    @Column(name = "payroll_cycle", comment = "发薪周期，如 月结")
    private String payrollCycle;

    @Column(name = "settle_type", comment = "结算方式，如 按天结算")
    private String settleType;

    @Column(comment = "结算单价（分），如 180元/天=18000")
    private Long dailyWage;

    @Column(name = "location_checkin", comment = "是否开启定位打卡")
    private Boolean locationCheckin = true;

    @Column(name = "checkin_radius", comment = "打卡有效范围（米）")
    private Integer checkinRadius = 200;

    @Column(name = "sign_code_expire", comment = "签到码有效期（分钟）")
    private Integer signCodeExpire = 60;

    @Column(name = "checkin_time", comment = "规定签到时间，HH:mm，如 08:00")
    private String checkinTime;

    @Column(name = "checkout_time", comment = "规定签退时间，HH:mm，如 18:00")
    private String checkoutTime;

    @Column(name = "late_auto", comment = "是否迟到自动判定")
    private Boolean lateAuto = true;

    @Column(name = "late_threshold", comment = "迟到阈值（分钟）")
    private Integer lateThreshold = 10;

    @Column(name = "salary_remind", comment = "发薪提醒开关")
    private Boolean salaryRemind = false;
}
