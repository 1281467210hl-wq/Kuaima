package com.kuaima.app.domain.project.entity;

import com.kuaima.app.domain.base.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 驻场人员（项目负责人/项目助理，拥有项目最高管理权限）。 */
@Entity
@Table(name = "project_onsite_staff", indexes = {
        @Index(name = "idx_pos_project", columnList = "project_id"),
        @Index(name = "idx_pos_user", columnList = "user_id")
})
@Setter
@Getter
public class ProjectOnsiteStaff extends BaseEntity {

    @Column(comment = "关联 project.id")
    private Long projectId;

    @Column(comment = "关联员工 id")
    private Long userId;

    @Column(comment = "驻场人员姓名")
    private String name;

    @Column(comment = "手机号")
    private String phone;

    @Column(comment = "所属公司")
    private String company;

    @Column(comment = "驻场角色:leader项目负责人/assistant项目助理")
    private String onsiteRole = "assistant";

    @Column(comment = "驻场岗位，如 项目助理/安全督导")
    private String position;

    @Column(name = "start_date", comment = "驻场开始日期")
    private java.time.LocalDate startDate;

    @Column(name = "end_date", comment = "驻场结束日期")
    private java.time.LocalDate endDate;

    @Column(name = "onsite_status", comment = "驻场状态:ongoing驻场中/ended已结束")
    private String onsiteStatus = "ongoing";

    @Column(comment = "驻场天数")
    private Integer onsiteDays = 0;
}
