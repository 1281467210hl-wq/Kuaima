package com.kuaima.app.domain.recruit.entity;

import com.kuaima.app.domain.base.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 老板绑定的抖音直播账号（直播橱窗选择账号时使用）。 */
@Entity
@Table(name = "boss_douyin_account", indexes = {
        @Index(name = "idx_douyin_boss", columnList = "boss_id")
})
@Getter
@Setter
public class DouyinAccount extends BaseEntity {

    @Column(name = "boss_id", nullable = false)
    private Long bossId;

    @Column(nullable = false, length = 50)
    private String name;

    /** 抖音号 / 抖音 ID */
    @Column(name = "douyin_id", length = 100)
    private String douyinId;

    /** 负责该账号的主播（选填） */
    @Column(name = "anchor_name", length = 50)
    private String anchorName;

    /** 是否已完成官方授权；当前为手动绑定，默认已绑定 */
    @Column(nullable = false)
    private Boolean authorized = true;
}
