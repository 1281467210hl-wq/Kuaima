package com.kuaima.app.domain.project.entity;

import java.time.LocalDate;

import com.kuaima.app.domain.base.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目成员（员工/零工在某项目中的记录）。 */
@Entity
@Table(name = "project_member", indexes = {
        @Index(name = "idx_pm_project", columnList = "project_id"),
        @Index(name = "idx_pm_status", columnList = "`status`"),
        @Index(name = "idx_pm_user", columnList = "user_id")
})
@Setter
@Getter
public class ProjectMember extends BaseEntity {

    @Column(name = "project_id", comment = "关联 project.id")
    private Long projectId;

    @Column(name = "user_id", comment = "关联用户/员工 id（外部零工可空）")
    private Long userId;

    @Column(comment = "成员姓名")
    private String name;

    @Column(comment = "手机号")
    private String phone;

    @Column(name = "`role`", comment = "职位/岗位，如 分拣员、装车员")
    private String role;

    @Column(comment = "日薪，来源岗位薪资范围 salaryRange，如 200元/天")
    private String salary;

    @Column(name = "`status`", comment = "状态:active在职/temp临时/left已离职")
    private String status = "temp";

    @Column(name = "employment_type", comment = "用工类型:fulltime正式工/temp临时工，为空按 status 推断")
    private String employmentType;

    @Column(name = "leave_date", comment = "离职日期")
    private LocalDate leaveDate;

    @Column(name = "join_date", comment = "入职日期")
    private LocalDate joinDate;
}
