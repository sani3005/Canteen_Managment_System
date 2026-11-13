-- =======================================================
-- Canteen Management System - Database Schema & Seed Data
-- Target Database: MySQL 8.0 
-- =======================================================

CREATE DATABASE IF NOT EXISTS canteen_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE canteen_db;

-- 1. Canteens
CREATE TABLE IF NOT EXISTS canteens (
    canteen_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    join_code VARCHAR(20) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 2. Users (Owner & Student)
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('owner', 'student') NOT NULL DEFAULT 'student',
    canteen_id INT NULL,
    token VARCHAR(64) NULL UNIQUE,
    token_expires_at DATETIME NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (canteen_id) REFERENCES canteens(canteen_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 3. Menu Items
CREATE TABLE IF NOT EXISTS menu_items (
    item_id INT AUTO_INCREMENT PRIMARY KEY,
    canteen_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    is_available TINYINT(1) NOT NULL DEFAULT 1,
    category VARCHAR(50) DEFAULT 'General',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (canteen_id) REFERENCES canteens(canteen_id) ON DELETE CASCADE,
    INDEX idx_canteen_avail (canteen_id, is_available)
) ENGINE=InnoDB;

-- 4. Orders
CREATE TABLE IF NOT EXISTS orders (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    canteen_id INT NOT NULL,
    student_id INT NOT NULL,
    order_type ENUM('dine_in', 'parcel') NOT NULL DEFAULT 'dine_in',
    is_preorder TINYINT(1) NOT NULL DEFAULT 0,
    scheduled_time DATETIME NULL,
    status ENUM('pending', 'preparing', 'ready', 'completed', 'cancelled') NOT NULL DEFAULT 'pending',
    otp_code VARCHAR(6) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    FOREIGN KEY (canteen_id) REFERENCES canteens(canteen_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_queue (canteen_id, status, scheduled_time, created_at)
) ENGINE=InnoDB;

-- 5. Order Items
CREATE TABLE IF NOT EXISTS order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    item_id INT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(10, 2) NOT NULL,
    subtotal DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES menu_items(item_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =======================================================
-- Seed Initial Data for Testing Owner Backend
-- =======================================================

-- Canteen
INSERT INTO canteens (canteen_id, name, join_code)
VALUES (1, 'Main Campus Cafeteria', 'CANT-9021')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Owner User (token: "owner_secret_token_123")
INSERT INTO users (user_id, username, password_hash, role, canteen_id, token, token_expires_at)
VALUES (1, 'canteen_owner', 'hashed_pw_owner', 'owner', 1, 'owner_secret_token_123', '2030-01-01 00:00:00')
ON DUPLICATE KEY UPDATE token=VALUES(token), canteen_id=VALUES(canteen_id);

-- Student User
INSERT INTO users (user_id, username, password_hash, role, canteen_id, token)
VALUES (2, 'student_robin', 'hashed_pw_student', 'student', 1, 'student_token_abc')
ON DUPLICATE KEY UPDATE canteen_id=VALUES(canteen_id);

-- Menu Items
INSERT INTO menu_items (item_id, canteen_id, name, description, price, is_available, category)
VALUES 
    (1, 1, 'Special Veg Thali', 'Rice, Dal Makhani, 2 Roti, Paneer Sabzi, Salad', 120.00, 1, 'Meals'),
    (2, 1, 'Paneer Tikka Roll', 'Grilled paneer cubes rolled in soft paratha with mint mayo', 70.00, 1, 'Snacks'),
    (3, 1, 'Cold Coffee Frappe', 'Chilled espresso blended with milk and chocolate drizzle', 50.00, 0, 'Beverages'),
    (4, 1, 'Aloo Samosa (2 pcs)', 'Crisp golden samosas served with sweet & tamarind chutney', 30.00, 1, 'Snacks'),
    (5, 1, 'Masala Dosa', 'Crispy fermented crepe served with sambar and coconut chutney', 60.00, 1, 'South Indian')
ON DUPLICATE KEY UPDATE name=VALUES(name), price=VALUES(price);

-- Active Live Queue Orders (for testing owner live order endpoints & OTP)
-- Order 1: Immediate, Dine-in, Pending (OTP: 412356)
INSERT INTO orders (order_id, canteen_id, student_id, order_type, is_preorder, scheduled_time, status, otp_code, total_amount, created_at)
VALUES (1, 1, 2, 'dine_in', 0, NULL, 'pending', '412356', 120.00, NOW() - INTERVAL 15 MINUTE)
ON DUPLICATE KEY UPDATE status=VALUES(status);

INSERT INTO order_items (order_id, item_id, quantity, unit_price, subtotal)
VALUES (1, 1, 1, 120.00, 120.00)
ON DUPLICATE KEY UPDATE quantity=VALUES(quantity);

-- Order 2: Immediate, Parcel, Preparing (OTP: 589123)
INSERT INTO orders (order_id, canteen_id, student_id, order_type, is_preorder, scheduled_time, status, otp_code, total_amount, created_at)
VALUES (2, 1, 2, 'parcel', 0, NULL, 'preparing', '589123', 140.00, NOW() - INTERVAL 10 MINUTE)
ON DUPLICATE KEY UPDATE status=VALUES(status);

INSERT INTO order_items (order_id, item_id, quantity, unit_price, subtotal)
VALUES (2, 2, 2, 70.00, 140.00)
ON DUPLICATE KEY UPDATE quantity=VALUES(quantity);

-- Order 3: Pre-order, Parcel, Ready (OTP: 839201)
INSERT INTO orders (order_id, canteen_id, student_id, order_type, is_preorder, scheduled_time, status, otp_code, total_amount, created_at)
VALUES (3, 1, 2, 'parcel', 1, NOW() + INTERVAL 20 MINUTE, 'ready', '839201', 90.00, NOW() - INTERVAL 30 MINUTE)
ON DUPLICATE KEY UPDATE status=VALUES(status);

INSERT INTO order_items (order_id, item_id, quantity, unit_price, subtotal)
VALUES 
    (3, 4, 1, 30.00, 30.00),
    (3, 5, 1, 60.00, 60.00)
ON DUPLICATE KEY UPDATE quantity=VALUES(quantity);

-- Completed Orders for Analytics Seed
INSERT INTO orders (order_id, canteen_id, student_id, order_type, is_preorder, status, otp_code, total_amount, created_at, completed_at)
VALUES 
    (101, 1, 2, 'dine_in', 0, 'completed', '111111', 240.00, NOW() - INTERVAL 4 HOUR, NOW() - INTERVAL 3 HOUR),
    (102, 1, 2, 'parcel',  0, 'completed', '222222', 150.00, NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 2 HOUR),
    (103, 1, 2, 'dine_in', 0, 'completed', '333333', 180.00, NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 1 HOUR),
    (104, 1, 2, 'parcel',  1, 'completed', '444444', 300.00, NOW() - INTERVAL 1 HOUR, NOW() - INTERVAL 30 MINUTE)
ON DUPLICATE KEY UPDATE status=VALUES(status);

INSERT INTO order_items (order_id, item_id, quantity, unit_price, subtotal)
VALUES 
    (101, 1, 2, 120.00, 240.00),
    (102, 4, 5, 30.00, 150.00),
    (103, 5, 3, 60.00, 180.00),
    (104, 2, 3, 70.00, 210.00),
    (104, 4, 3, 30.00, 90.00)
ON DUPLICATE KEY UPDATE quantity=VALUES(quantity);
