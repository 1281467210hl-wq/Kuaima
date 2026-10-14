-- =====================================================================
-- 快马日结 - 后台「闪电发薪-商户账户」扩展表
-- 适用于 MySQL 8.0+
-- 背景：admin-web「余额查询」需要「开户银行 / 对公账号 / 冻结金额」，
--       但 boss_merchant_account 只有 boss_id/account_name/subject_name/
--       merchant_no/balance/is_default/status，且该实体不允许改动，
--       因此新建 1:1 扩展表承载这些字段（后端实体：admin/entity/MerchantAccountBank）。
-- 单位：frozen_amount 以「分」存储，与 boss_merchant_account.balance 一致。
-- 说明：应用使用 JPA ddl-auto=update，会按实体自动建表；本脚本用于留档与手工核对，可重复执行。
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_admin_merchant_account_ext_schema;

DELIMITER $$
CREATE PROCEDURE sp_admin_merchant_account_ext_schema()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'boss_merchant_account_bank') THEN
        CREATE TABLE `boss_merchant_account_bank` (
            `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
            `date` DATE COMMENT '创建日期',
            `create_by` BIGINT COMMENT '创建人ID',
            `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
            `account_id` BIGINT NOT NULL COMMENT '关联 boss_merchant_account.id',
            `boss_id` BIGINT COMMENT '所属老板 user.id（冗余，便于按老板查询）',
            `bank_name` VARCHAR(100) DEFAULT NULL COMMENT '开户银行',
            `bank_branch` VARCHAR(100) DEFAULT NULL COMMENT '开户支行',
            `bank_account_no` VARCHAR(64) DEFAULT NULL COMMENT '对公银行账号',
            `frozen_amount` BIGINT DEFAULT 0 COMMENT '冻结金额（分）',
            `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
            PRIMARY KEY (`id`),
            KEY `idx_bma_bank_account` (`account_id`),
            KEY `idx_bma_bank_boss` (`boss_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户账户银行信息（后台维护扩展表）';
    END IF;

    -- 后台软删除状态说明：boss_merchant_account.status 由实体注释的 active/frozen 扩展出 deleted，
    -- 删除接口只置状态不删行，余额与充值流水保留可追溯；此处无需 DDL 变更。
END$$
DELIMITER ;

CALL sp_admin_merchant_account_ext_schema();
DROP PROCEDURE IF EXISTS sp_admin_merchant_account_ext_schema;
