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
 * 直播带岗橱窗（admin 智能招聘-直播橱窗）。
 * 状态机：PENDING 待开播 / LIVE 直播中 / ENDED 已结束。
 */
@Entity
@Table(name = "live_showcase", indexes = {
        @Index(name = "idx_showcase_boss", columnList = "boss_id"),
        @Index(name = "idx_showcase_status", columnList = "status")
})
@Getter
@Setter
public class LiveShowcase extends BaseEntity {

    @Column(name = "boss_id")
    private Long bossId;

    @Column(name = "boss_name", length = 100)
    private String bossName;

    @Column(nullable = false, length = 100)
    private String title;

    /** 主播名称 */
    @Column(name = "anchor_name", length = 50)
    private String anchorName;

    /** 抖音号 */
    @Column(name = "douyin_account", length = 100)
    private String douyinAccount;

    /** PENDING 待开播 / LIVE 直播中 / ENDED 已结束 */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    /** 计划开播时间；为空时前端展示创建时间 */
    @Column(name = "live_time")
    private LocalDateTime liveTime;
}
