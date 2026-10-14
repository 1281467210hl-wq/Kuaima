package com.kuaima.app.domain.recruit.entity;

import java.time.LocalDateTime;

import com.kuaima.app.domain.base.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 岗位投递记录（admin 智能招聘-候选人视角）：一次「简历投递到岗位」的流转。
 * 状态机：PENDING 待筛选 → PREINTERVIEW 待面试 → INTERVIEWING 面试中 → HIRED 已录用 / REJECTED 已拒绝。
 */
@Entity
@Table(name = "position_application", indexes = {
        @Index(name = "idx_application_position", columnList = "position_id"),
        @Index(name = "idx_application_resume", columnList = "resume_id"),
        @Index(name = "idx_application_boss_status", columnList = "boss_id,status"),
        @Index(name = "idx_application_status", columnList = "status")
})
@Getter
@Setter
public class PositionApplication extends BaseEntity {

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    /** 投递人（零工用户）id */
    @Column(name = "user_id")
    private Long userId;

    /** 岗位所属老板 id */
    @Column(name = "boss_id")
    private Long bossId;

    /** PENDING 待筛选 / INTERVIEWING 面试中 / HIRED 已录用 / REJECTED 已拒绝 */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "apply_time")
    private LocalDateTime applyTime;

    @Column(name = "interview_time")
    private LocalDateTime interviewTime;

    /** 面试地点（boss 端面试安排录入） */
    @Column(name = "interview_location", length = 200)
    private String interviewLocation;

    /** 面试官（boss 端面试安排录入） */
    @Column(name = "interviewer", length = 100)
    private String interviewer;

    @Column(name = "decision_time")
    private LocalDateTime decisionTime;

    @Column(length = 200)
    private String remark;
}
