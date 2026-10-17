-- 招工工作时间中的休息配置；生产环境执行一次，重复执行不会重复加列
-- 2026-10-16
SET @db_name = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'boss_order' AND column_name = 'rest_mode') = 0,
  'ALTER TABLE `boss_order` ADD COLUMN `rest_mode` VARCHAR(10) NULL COMMENT ''休息模式：none无休息/rest有休息''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'boss_order' AND column_name = 'rest_slots') = 0,
  'ALTER TABLE `boss_order` ADD COLUMN `rest_slots` VARCHAR(2000) NULL COMMENT ''固定休息时段 JSON''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'boss_order' AND column_name = 'rest_minutes') = 0,
  'ALTER TABLE `boss_order` ADD COLUMN `rest_minutes` INT NULL COMMENT ''休息总时长（分钟）''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'boss_order' AND column_name = 'rest_checkin') = 0,
  'ALTER TABLE `boss_order` ADD COLUMN `rest_checkin` TINYINT(1) NULL COMMENT ''是否需要休息打卡''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'boss_order' AND column_name = 'rest_checkin_count') = 0,
  'ALTER TABLE `boss_order` ADD COLUMN `rest_checkin_count` INT NULL COMMENT ''休息打卡次数''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `boss_order`
SET `rest_mode` = 'none', `rest_slots` = '[]', `rest_minutes` = 0,
    `rest_checkin` = 0, `rest_checkin_count` = 0
WHERE `rest_mode` IS NULL;
