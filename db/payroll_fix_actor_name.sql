-- =============================================================================
-- 修复 payroll_order.creator / review_by 显示为登录用户名（如 admin）而非中文姓名
--
-- 现象：后台「企业工作台 → 闪电发薪」列表「制单人员」列显示 admin，
--       预期显示管理员中文名（超级管理员）。
--
-- 根因：payroll_fix_actor_dirty.sql 清理 LoginUser 脏值时回填的是 username，
--       落到库里就成了 'admin'；而 operator() 现在写入的是 admin_user.name。
--
-- 处理：把等于某个管理员 username 的 creator / review_by 改写成该管理员的 name。
-- 幂等：改完即为中文名，不再等于 username，可重复执行。
-- 回滚：见 payroll_fix_actor_dirty.sql 的备份表 payroll_order_actor_bak_20261008。
-- =============================================================================

UPDATE payroll_order o
JOIN admin_user a ON o.creator = a.username
SET o.creator = a.name
WHERE a.name IS NOT NULL AND a.name <> '';

UPDATE payroll_order o
JOIN admin_user a ON o.review_by = a.username
SET o.review_by = a.name
WHERE a.name IS NOT NULL AND a.name <> '';

-- 校验：creator / review_by 应为中文姓名，不应再出现 admin 等用户名
SELECT id, title, creator, review_by FROM payroll_order ORDER BY id;
