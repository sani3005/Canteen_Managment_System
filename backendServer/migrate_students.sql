USE canteen_db;

CREATE TABLE IF NOT EXISTS students (
    student_id INT AUTO_INCREMENT PRIMARY KEY,
    canteen_id INT NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NULL,
    password_hash VARCHAR(255) NULL,
    phone VARCHAR(30) NULL,
    access_token CHAR(64) NULL UNIQUE,
    token_expires_at DATETIME NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (canteen_id) REFERENCES canteens(canteen_id) ON DELETE CASCADE,
    UNIQUE KEY uq_student_email (canteen_id, email)
) ENGINE=InnoDB;

SET @add_student_password := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE students ADD COLUMN password_hash VARCHAR(255) NULL AFTER email',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND COLUMN_NAME = 'password_hash'
);
PREPARE add_student_password_stmt FROM @add_student_password;
EXECUTE add_student_password_stmt;
DEALLOCATE PREPARE add_student_password_stmt;

SET @drop_student_number := (
    SELECT IF(COUNT(*) = 0,
        'SELECT 1',
        'ALTER TABLE students DROP COLUMN student_number')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND COLUMN_NAME = 'student_number'
);
PREPARE drop_student_number_stmt FROM @drop_student_number;
EXECUTE drop_student_number_stmt;
DEALLOCATE PREPARE drop_student_number_stmt;
UPDATE students SET password_hash = '' WHERE password_hash IS NULL;
ALTER TABLE students MODIFY password_hash VARCHAR(255) NOT NULL;

INSERT IGNORE INTO students (student_id, canteen_id, full_name)
SELECT user_id, canteen_id, username
FROM users
WHERE role = 'student' AND canteen_id IS NOT NULL;

SET @student_fk := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'student_id'
      AND REFERENCED_TABLE_NAME = 'users'
    LIMIT 1
);
SET @drop_student_fk_sql := IF(
    @student_fk IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE orders DROP FOREIGN KEY `', @student_fk, '`')
);
PREPARE drop_student_fk_stmt FROM @drop_student_fk_sql;
EXECUTE drop_student_fk_stmt;
DEALLOCATE PREPARE drop_student_fk_stmt;

SET @student_fk_exists := (
    SELECT COUNT(*)
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'student_id'
      AND REFERENCED_TABLE_NAME = 'students'
);
SET @add_student_fk_sql := IF(
    @student_fk_exists = 0,
    'ALTER TABLE orders ADD CONSTRAINT fk_orders_student FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE',
    'SELECT 1'
);
PREPARE add_student_fk_stmt FROM @add_student_fk_sql;
EXECUTE add_student_fk_stmt;
DEALLOCATE PREPARE add_student_fk_stmt;
