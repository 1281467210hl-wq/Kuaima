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

/** 项目签到码（后台生成，员工扫码签到用；ddl-auto=update 自动建表）。 */
@Entity
@Table(name = "project_attendance_code", indexes = {
        @Index(name = "idx_pac_project", columnList = "project_id"),
        @Index(name = "idx_pac_status", columnList = "`status`")
})
@Setter
@Getter
public class ProjectAttendanceCode extends BaseEntity {

    @Column(name = "project_id", comment = "关联 project.id")
    private Long projectId;

    @Column(comment = "签到码（随机串）")
    private String code;

    /** 注意：BaseEntity 已占用 date 列（创建日期），业务日期用 attend_date。 */
    @Column(name = "attend_date", comment = "签到码适用日期")
    private LocalDate attendDate;

    @Column(name = "qr_content", comment = "二维码编码内容（签到链接）")
    private String qrContent;

    @Column(name = "expire_minutes", comment = "有效期（分钟）")
    private Integer expireMinutes;

    @Column(name = "expire_time", comment = "失效时间点")
    private Date expireTime;

    @Column(name = "`status`", comment = "状态:active有效/expired已失效")
    private String status = "active";
}
