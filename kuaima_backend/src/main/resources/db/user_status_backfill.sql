-- 修复历史用户 status 为 NULL 的问题：
-- User 实体的 status 无默认值，早期注册代码漏设，
-- 导致后台零工列表按「正常」筛选时查不到这些用户。
UPDATE sys_user
SET status = '正常'
WHERE status IS NULL OR status = '';
