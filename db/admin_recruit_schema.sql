-- =====================================================================
-- 快马日结 - admin 智能招聘新表（岗位投递候选人 + 直播橱窗）
-- 场景：admin-web「智能招聘」页候选人 tab（岗位投递视角）与直播橱窗 tab。
--       对应实体 com.kuaima.app.domain.recruit.entity.PositionApplication / LiveShowcase，
--       JPA ddl-auto=update 可自动建表，本脚本用于生产手工留档（幂等，可重复执行）。
-- 适用：MySQL 8.x
-- =====================================================================

-- 岗位投递记录：一次「简历投递到岗位」的流转
CREATE TABLE IF NOT EXISTS `position_application` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `date`            DATE         DEFAULT NULL COMMENT '创建日期(JPA auditing)',
    `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人(JPA auditing)',
    `timestamp`       DATETIME     DEFAULT NULL COMMENT '最后更新时间(JPA @UpdateTimestamp)',
    `position_id`     BIGINT       NOT NULL COMMENT '岗位id(boss_position.id)',
    `resume_id`       BIGINT       NOT NULL COMMENT '简历id(boss_resume.id)',
    `user_id`         BIGINT       DEFAULT NULL COMMENT '投递人(零工用户)id',
    `boss_id`         BIGINT       DEFAULT NULL COMMENT '岗位所属老板id',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING待筛选/INTERVIEWING面试中/HIRED已录用/REJECTED已拒绝',
    `apply_time`      DATETIME     DEFAULT NULL COMMENT '投递时间',
    `interview_time`  DATETIME     DEFAULT NULL COMMENT '面试时间',
    `decision_time`   DATETIME     DEFAULT NULL COMMENT '录用/拒绝决断时间',
    `remark`          VARCHAR(200) DEFAULT NULL COMMENT '备注/拒绝原因',
    PRIMARY KEY (`id`),
    KEY `idx_application_position` (`position_id`),
    KEY `idx_application_resume` (`resume_id`),
    KEY `idx_application_boss_status` (`boss_id`, `status`),
    KEY `idx_application_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位投递记录(admin候选人视角)';

-- 直播带岗橱窗
CREATE TABLE IF NOT EXISTS `live_showcase` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `date`            DATE         DEFAULT NULL COMMENT '创建日期(JPA auditing)',
    `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人(JPA auditing)',
    `timestamp`       DATETIME     DEFAULT NULL COMMENT '最后更新时间(JPA @UpdateTimestamp)',
    `boss_id`         BIGINT       DEFAULT NULL COMMENT '所属老板id',
    `boss_name`       VARCHAR(100) DEFAULT NULL COMMENT '老板/企业名称(冗余展示字段)',
    `title`           VARCHAR(100) NOT NULL COMMENT '直播标题',
    `anchor_name`     VARCHAR(50)  DEFAULT NULL COMMENT '主播名称',
    `douyin_account`  VARCHAR(100) DEFAULT NULL COMMENT '抖音号',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING待开播/LIVE直播中/ENDED已结束',
    `live_time`       DATETIME     DEFAULT NULL COMMENT '计划开播时间(为空前端展示创建时间)',
    PRIMARY KEY (`id`),
    KEY `idx_showcase_boss` (`boss_id`),
    KEY `idx_showcase_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播带岗橱窗';
