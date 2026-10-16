-- =============================================
-- 快马日结 发薪管理脏数据修复脚本
-- 执行前先：BACKUP DATABASE kuaima
-- =============================================

-- 1. 备份 payroll 相关表（临时表，方便回滚）
CREATE TABLE IF NOT EXISTS payroll_order_bak AS SELECT * FROM payroll_order;
CREATE TABLE IF NOT EXISTS payroll_detail_bak AS SELECT * FROM payroll_detail;

-- 2. 修复 payroll_detail.amount = daily_wage * attend_days
--    daily_wage 存的是分（例：23400 = 234元），DECIMAL → BIGINT 会保留整数值
UPDATE payroll_detail
SET amount = COALESCE(daily_wage, 0) * COALESCE(attend_days, 0)
WHERE amount IS NULL OR amount = 0;

-- 3. 修复 payroll_order.amount = 明细汇总
UPDATE payroll_order po
SET amount = COALESCE((SELECT SUM(pd.amount) FROM payroll_detail pd WHERE pd.payroll_id = po.id), 0);

-- 4. 修复 payroll_order.people_count = 明细条数
UPDATE payroll_order po
SET people_count = (SELECT COUNT(*) FROM payroll_detail pd WHERE pd.payroll_id = po.id);

-- 5. 修复 submit_time（之前没自动补的）
UPDATE payroll_order
SET submit_time = COALESCE(submit_time, review_time, `date`, NOW())
WHERE submit_time IS NULL;

-- =============================================
-- 6. 改列类型为 BIGINT（和 Java Long 实体 + SQL 迁移脚本对齐）
--    MySQL DECIMAL(18,2) → BIGINT 会截断小数部分
--    当前 daily_wage/amount 存的是整数值（分），截断无损失
-- =============================================

-- 先把所有 DECIMAL 列的值 ROUND 一下（防意外有小数）
UPDATE payroll_order  SET amount     = ROUND(amount);
UPDATE payroll_detail SET daily_wage = ROUND(daily_wage);
UPDATE payroll_detail SET amount     = ROUND(amount);

-- 改列类型
ALTER TABLE payroll_order
    MODIFY COLUMN amount BIGINT NOT NULL DEFAULT 0 COMMENT '应发总金额（分）';

ALTER TABLE payroll_detail
    MODIFY COLUMN daily_wage BIGINT DEFAULT 0 COMMENT '日薪（分）',
    MODIFY COLUMN amount     BIGINT NOT NULL DEFAULT 0 COMMENT '应发金额（分）';

-- 7. 验证修复结果
SELECT '=== 修复后 payroll_order ===' AS info;
SELECT id, order_no, amount, people_count, submit_time, status FROM payroll_order ORDER BY id;
SELECT '=== 修复后 payroll_detail ===' AS info;
SELECT id, payroll_id, name, daily_wage, attend_days, amount FROM payroll_detail ORDER BY id;
SELECT '=== 校验 ===' AS info;
SELECT 'order amount ok' AS check_ok, COUNT(*) AS cnt FROM payroll_order WHERE amount = 0;
SELECT 'detail amount ok' AS check_ok, COUNT(*) AS cnt FROM payroll_detail WHERE amount != COALESCE(daily_wage,0)*COALESCE(attend_days,0);
SELECT 'submit_time ok' AS check_ok, COUNT(*) AS cnt FROM payroll_order WHERE submit_time IS NULL;
