-- =====================================================================
-- 快马日结 - 企业成员/入企申请模块数据库脚本
-- 适用于 MySQL 8.0+
-- 说明: 企业主体、企业成员、入企邀请、入企申请
-- =====================================================================

-- =====================================================================
-- 表: enterprise (企业主体)
-- =====================================================================
CREATE TABLE IF NOT EXISTS `enterprise` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `date` DATE COMMENT '创建日期',
    `create_by` BIGINT COMMENT '创建人ID',
    `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `company_code` VARCHAR(32) NOT NULL COMMENT '企业唯一编码',
    `company_name` VARCHAR(120) NOT NULL COMMENT '企业名称',
    `license_no` VARCHAR(50) DEFAULT NULL COMMENT '营业执照号',
    `legal_rep` VARCHAR(50) DEFAULT NULL COMMENT '法人代表',
    `industry` VARCHAR(50) DEFAULT NULL COMMENT '所属行业',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE正常/FROZEN冻结',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enterprise_code` (`company_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业主体';

-- =====================================================================
-- 表: enterprise_member (企业成员)
-- =====================================================================
CREATE TABLE IF NOT EXISTS `enterprise_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `date` DATE COMMENT '创建日期',
    `create_by` BIGINT COMMENT '创建人ID',
    `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `enterprise_id` BIGINT NOT NULL COMMENT '关联 enterprise.id',
    `user_id` BIGINT NOT NULL COMMENT '关联 user.id',
    `member_role` VARCHAR(20) NOT NULL DEFAULT 'STAFF' COMMENT '角色:OWNER超管/ADMIN管理员/STAFF成员',
    `title` VARCHAR(64) DEFAULT NULL COMMENT '职务/岗位',
    `invited_by` BIGINT DEFAULT NULL COMMENT '邀请人 enterprise_member.id',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE在职/LEFT已离开',
    PRIMARY KEY (`id`),
    KEY `idx_em_enterprise` (`enterprise_id`),
    KEY `idx_em_user` (`user_id`),
    KEY `idx_em_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业成员';

-- =====================================================================
-- 表: enterprise_invite (企业邀请记录)
-- =====================================================================
CREATE TABLE IF NOT EXISTS `enterprise_invite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `date` DATE COMMENT '创建日期',
    `create_by` BIGINT COMMENT '创建人ID',
    `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `enterprise_id` BIGINT NOT NULL COMMENT '关联 enterprise.id',
    `inviter_id` BIGINT NOT NULL COMMENT '邀请人 user.id',
    `phone` VARCHAR(20) NOT NULL COMMENT '被邀请人手机号',
    `name` VARCHAR(64) DEFAULT NULL COMMENT '被邀请人姓名',
    `invite_role` VARCHAR(30) NOT NULL DEFAULT 'STAFF' COMMENT '邀请角色',
    `invite_code` VARCHAR(40) NOT NULL COMMENT '邀请码',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING待接受/ACCEPTED已接受/EXPIRED已失效',
    `accepted_at` DATETIME DEFAULT NULL COMMENT '接受时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ei_code` (`invite_code`),
    KEY `idx_ei_enterprise_status` (`enterprise_id`, `status`),
    KEY `idx_ei_inviter` (`inviter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业邀请记录';

-- =====================================================================
-- 表: enterprise_join_apply (入企申请)
-- =====================================================================
CREATE TABLE IF NOT EXISTS `enterprise_join_apply` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `date` DATE COMMENT '创建日期',
    `create_by` BIGINT COMMENT '创建人ID',
    `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `enterprise_id` BIGINT NOT NULL COMMENT '关联 enterprise.id',
    `user_id` BIGINT DEFAULT NULL COMMENT '申请人 user.id（如有）',
    `name` VARCHAR(50) NOT NULL COMMENT '申请人姓名',
    `phone` VARCHAR(20) NOT NULL COMMENT '申请人手机号',
    `note` VARCHAR(500) DEFAULT NULL COMMENT '申请留言',
    `apply_role` VARCHAR(30) NOT NULL DEFAULT 'STAFF' COMMENT '申请角色',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING待处理/AGREED已同意/REFUSED已拒绝',
    `source` VARCHAR(64) DEFAULT '成员邀请链接' COMMENT '申请来源',
    `handled_by` BIGINT DEFAULT NULL COMMENT '处理人 user.id',
    PRIMARY KEY (`id`),
    KEY `idx_eja_enterprise_status` (`enterprise_id`, `status`),
    KEY `idx_eja_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入企申请';

-- =====================================================================
-- 示例数据
--   逐条按自然键判断「不存在才插」，可重复执行。
--   说明：早前版本这里是无守卫的 INSERT，enterprise 带唯一键 uk_enterprise_code，
--   第二次起就在此行报 Duplicate entry 并中断脚本，导致后面 enterprise_member /
--   enterprise_invite 的示例数据从未被执行过。
-- =====================================================================
DROP PROCEDURE IF EXISTS sp_enterprise_schema;

DELIMITER $$
CREATE PROCEDURE sp_enterprise_schema()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM `enterprise` WHERE `company_code` = 'QS001') THEN
        INSERT INTO `enterprise` (`company_code`, `company_name`, `legal_rep`, `industry`, `status`)
        VALUES ('QS001', '晴时科技', '张晴', '物流仓储', 'ACTIVE');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `enterprise_member`
                   WHERE `enterprise_id` = 1 AND `user_id` = 1001 AND `member_role` = 'OWNER') THEN
        INSERT INTO `enterprise_member` (`enterprise_id`, `user_id`, `member_role`, `title`, `status`,
                                         `portal_enabled`, `permission_version`)
        VALUES (1, 1001, 'OWNER', '创始人', 'ACTIVE', 1, 1);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `enterprise_invite` WHERE `invite_code` = 'QSINV20260001') THEN
        INSERT INTO `enterprise_invite` (`enterprise_id`, `inviter_id`, `phone`, `name`, `invite_role`, `invite_code`, `status`)
        VALUES (1, 1001, '13800000001', '新成员', 'STAFF', 'QSINV20260001', 'PENDING');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM `enterprise_invite` WHERE `invite_code` = 'QSINV20260002') THEN
        INSERT INTO `enterprise_invite` (`enterprise_id`, `inviter_id`, `phone`, `name`, `invite_role`, `invite_code`, `status`)
        VALUES (1, 1001, '13800000002', NULL, 'STAFF', 'QSINV20260002', 'ACCEPTED');
    END IF;
END$$
DELIMITER ;

CALL sp_enterprise_schema();
DROP PROCEDURE IF EXISTS sp_enterprise_schema;
