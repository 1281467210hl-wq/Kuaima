-- 人才库按「老板 + 零工」唯一，防止同一零工报名同一老板的多个岗位后重复入库。
-- worker_id 为空的历史手工记录不参与唯一约束冲突。

DELETE duplicate_row
FROM talent_pool duplicate_row
JOIN talent_pool keep_row
  ON keep_row.boss_id = duplicate_row.boss_id
 AND keep_row.worker_id = duplicate_row.worker_id
 AND keep_row.id < duplicate_row.id
WHERE duplicate_row.worker_id IS NOT NULL;

SET @talent_pool_unique_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'talent_pool'
    AND index_name = 'uk_talent_pool_boss_worker'
);

SET @talent_pool_unique_sql = IF(
  @talent_pool_unique_exists = 0,
  'ALTER TABLE talent_pool ADD UNIQUE KEY uk_talent_pool_boss_worker (boss_id, worker_id)',
  'SELECT 1'
);

PREPARE talent_pool_unique_stmt FROM @talent_pool_unique_sql;
EXECUTE talent_pool_unique_stmt;
DEALLOCATE PREPARE talent_pool_unique_stmt;
