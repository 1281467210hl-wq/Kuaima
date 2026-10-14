-- =============================================================
-- 项目签到码表 project_attendance_code（后台 admin 生成签到二维码）
-- 适用：MySQL 8.x / InnoDB / utf8mb4
-- 说明：可由 JPA ddl-auto=update 自动建表；本脚本用于留档/初始化，可重复执行。
-- =============================================================

CREATE TABLE IF NOT EXISTS `project_attendance_code` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id`     BIGINT       NOT NULL COMMENT '关联 project.id',
  `code`           VARCHAR(32)  NOT NULL COMMENT '签到码（随机串）',
  `attend_date`    DATE         DEFAULT NULL COMMENT '签到码适用日期',
  `qr_content`     VARCHAR(255) DEFAULT NULL COMMENT '二维码编码内容（签到链接）',
  `expire_minutes` INT          DEFAULT NULL COMMENT '有效期（分钟）',
  `expire_time`    DATETIME     DEFAULT NULL COMMENT '失效时间点',
  `status`         VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT '状态:active有效/expired已失效',
  `create_by`      BIGINT       DEFAULT NULL COMMENT '创建人',
  `date`           DATE         DEFAULT NULL COMMENT '创建日期',
  `timestamp`      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_pac_project` (`project_id`),
  KEY `idx_pac_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目签到码';

-- 关联：project 表新增所属企业列（实体 Project.java 已加字段，ddl-auto 会自动补列；
-- 手动执行前请确认列不存在，重复执行会报 Duplicate column）
-- ALTER TABLE `project` ADD COLUMN `enterprise_id` BIGINT DEFAULT NULL COMMENT '所属企业';
