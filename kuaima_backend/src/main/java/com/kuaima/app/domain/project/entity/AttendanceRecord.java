package com.kuaima.app.domain.project.entity;

import java.time.LocalDate;
import java.util.Date;

import com.kuaima.app.domain.base.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 考勤打卡记录。 */
@Entity
@Table(name = "attendance_record", indexes = {
        @Index(name = "idx_att_project_date", columnList = "project_id,attend_date"),
        @Index(name = "idx_att_member", columnList = "member_id"),
        @Index(name = "idx_att_status", columnList = "`status`")
})
@Setter
@Getter
public class AttendanceRecord extends BaseEntity {

    @Column(name = "project_id", comment = "关联 project.id")
    private Long projectId;

    @Column(name = "member_id", comment = "关联 project_member.id")
    private Long memberId;

    @Column(name = "user_id", comment = "关联用户/员工 id")
    private Long userId;

    @Column(comment = "打卡人姓名")
    private String name;

    @Column(name = "attend_date", comment = "考勤日期")
    private LocalDate attendDate;

    @Column(name = "sign_in_time", comment = "签到时间")
    private Date signInTime;

    @Column(name = "sign_out_time", comment = "签退时间")
    private Date signOutTime;

    @Column(name = "sign_in_location", comment = "签到地点")
    private String signInLocation;

    @Column(name = "sign_out_location", comment = "签退地点")
    private String signOutLocation;

    @Column(name = "`status`", comment = "状态:on出勤/late迟到/absent缺卡/leave请假")
    private String status = "on";

    @Column(name = "late_minutes", comment = "迟到分钟数")
    private Integer lateMinutes = 0;
}
