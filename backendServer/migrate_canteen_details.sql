USE canteen_db;

SET @add_owner_name := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE canteens ADD COLUMN owner_name VARCHAR(100) NOT NULL DEFAULT \'\' AFTER name',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'canteens' AND COLUMN_NAME = 'owner_name'
);
PREPARE stmt FROM @add_owner_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_location := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE canteens ADD COLUMN location VARCHAR(255) NOT NULL DEFAULT \'\' AFTER owner_name',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'canteens' AND COLUMN_NAME = 'location'
);
PREPARE stmt FROM @add_location;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_photo_url := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE canteens ADD COLUMN photo_url VARCHAR(2048) NULL AFTER location',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'canteens' AND COLUMN_NAME = 'photo_url'
);
PREPARE stmt FROM @add_photo_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE canteens c
JOIN users u ON u.canteen_id = c.canteen_id AND u.role = 'owner'
SET c.owner_name = u.username
WHERE c.owner_name IS NULL OR c.owner_name = '';

UPDATE canteens
SET join_code = LEFT(MD5(CONCAT(canteen_id, '-', COALESCE(join_code, ''))), 6)
WHERE join_code IS NULL OR CHAR_LENGTH(join_code) <> 6;

ALTER TABLE canteens MODIFY join_code VARCHAR(6) NOT NULL;
