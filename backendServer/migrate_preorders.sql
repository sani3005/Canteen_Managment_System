-- Run once on a database created before pre-order support.
USE canteen_db;

SET @add_is_preorder := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE orders ADD COLUMN is_preorder TINYINT(1) NOT NULL DEFAULT 0 AFTER order_type',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'is_preorder'
);
PREPARE stmt FROM @add_is_preorder;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_scheduled_time := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE orders ADD COLUMN scheduled_time VARCHAR(50) NULL AFTER is_preorder',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'scheduled_time'
);
PREPARE stmt FROM @add_scheduled_time;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE orders MODIFY COLUMN scheduled_time VARCHAR(50) NULL;
