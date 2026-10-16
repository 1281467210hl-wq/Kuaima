SET @boss_order_item_duplicate_count := (
    SELECT COUNT(*)
    FROM (
        SELECT order_id, user_id
        FROM boss_order_item
        GROUP BY order_id, user_id
        HAVING COUNT(*) > 1
    ) duplicate_rows
);

SET @boss_order_item_unique_statement := IF(
    @boss_order_item_duplicate_count = 0
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'boss_order_item'
          AND INDEX_NAME = 'uk_boss_order_item_order_user'
    ),
    'ALTER TABLE boss_order_item ADD UNIQUE KEY uk_boss_order_item_order_user (order_id, user_id)',
    'SELECT order_id, user_id, COUNT(*) AS record_count
       FROM boss_order_item
      GROUP BY order_id, user_id
     HAVING COUNT(*) > 1'
);

PREPARE boss_order_item_unique_statement FROM @boss_order_item_unique_statement;
EXECUTE boss_order_item_unique_statement;
DEALLOCATE PREPARE boss_order_item_unique_statement;
