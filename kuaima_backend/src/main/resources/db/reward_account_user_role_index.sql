-- 修复 reward_account 历史索引：
-- 实体唯一键已从 user_id 单列调整为 (user_id, role) 联合唯一，
-- 但 Hibernate ddl-auto=update 只新增 uk_reward_account_user_role，不会删除旧的 uk_reward_account_user，
-- 导致同一用户开通另一角色奖励账户时插入失败（Duplicate entry for key 'uk_reward_account_user'）。
SET @drop_old_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'reward_account'
      AND index_name = 'uk_reward_account_user'
);
SET @drop_old_index_sql = IF(@drop_old_index_exists > 0,
    'ALTER TABLE reward_account DROP INDEX uk_reward_account_user',
    'SELECT 1');
PREPARE drop_old_index_statement FROM @drop_old_index_sql;
EXECUTE drop_old_index_statement;
DEALLOCATE PREPARE drop_old_index_statement;

SET @new_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'reward_account'
      AND index_name = 'uk_reward_account_user_role'
);
SET @new_index_sql = IF(@new_index_exists = 0,
    'ALTER TABLE reward_account ADD UNIQUE KEY uk_reward_account_user_role (user_id, role)',
    'SELECT 1');
PREPARE new_index_statement FROM @new_index_sql;
EXECUTE new_index_statement;
DEALLOCATE PREPARE new_index_statement;
