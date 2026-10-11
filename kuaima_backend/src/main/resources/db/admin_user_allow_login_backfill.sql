-- 修复 allow_login / receive_notifications 自动加列后存量行为 0 的问题：
-- Hibernate ddl-auto=update 新增 NOT NULL 布尔列时未带默认值，MySQL 会给存量行填充 0，
-- 导致历史管理员（含超级管理员）被误判为「禁止登录」。
UPDATE admin_user
SET allow_login = TRUE, receive_notifications = TRUE
WHERE allow_login = FALSE OR allow_login IS NULL
   OR receive_notifications = FALSE OR receive_notifications IS NULL;

-- 超级管理员是后台兜底账号，强制保持可登录。
UPDATE admin_user SET allow_login = TRUE WHERE role = 'SUPER_ADMIN';

-- 补齐列默认值，后续手工插数据也不会误伤。
ALTER TABLE admin_user MODIFY COLUMN allow_login BIT NOT NULL DEFAULT 1;
ALTER TABLE admin_user MODIFY COLUMN receive_notifications BIT NOT NULL DEFAULT 1;
