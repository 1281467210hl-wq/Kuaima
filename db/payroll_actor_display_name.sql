-- =============================================================================
-- 修复 payroll_order.creator / review_by：清理 LoginUser 脏值 + 用户名映射为中文名
-- 幂等，可重复执行。执行前先跑第 1 步备份。
-- 配套代码：PayrollController.operator() 已改为优先写 admin_user.name（中文名）。
-- =============================================================================

-- 1. 备份受影响字段（整表快照，回滚用）
CREATE TABLE IF NOT EXISTS payroll_order_actor_bak_20261013 AS
SELECT id, creator, review_by FROM payroll_order;

-- 2. 脏值 'LoginUser[id=1, username=admin, role=...]' -> 'admin'
UPDATE payroll_order
SET creator = TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(creator, 'username=', -1), ',', 1))
WHERE creator LIKE 'LoginUser%' AND creator LIKE '%username=%';

UPDATE payroll_order
SET review_by = TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(review_by, 'username=', -1), ',', 1))
WHERE review_by LIKE 'LoginUser%' AND review_by LIKE '%username=%';

-- 3. 用户名 -> admin_user.name 中文名（name 为空的账号保持用户名）
UPDATE payroll_order o
JOIN admin_user u ON u.username = o.creator
SET o.creator = u.name
WHERE u.name IS NOT NULL AND u.name <> '';

UPDATE payroll_order o
JOIN admin_user u ON u.username = o.review_by
SET o.review_by = u.name
WHERE u.name IS NOT NULL AND u.name <> '';

-- 4. 校验：两个 SELECT 都应为 0 行
SELECT COUNT(*) AS dirty_left FROM payroll_order
WHERE creator LIKE 'LoginUser%' OR review_by LIKE 'LoginUser%';

SELECT id, creator, review_by FROM payroll_order
WHERE creator IN (SELECT username FROM admin_user)
   OR review_by IN (SELECT username FROM admin_user);
