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
    owner_name VARCHAR(100) NOT NULL DEFAULT '',
    location VARCHAR(255) NOT NULL DEFAULT '',
    photo_url VARCHAR(2048) NULL,
    join_code VARCHAR(6) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 2. Users (Owner & Student)
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('owner', 'student') NOT NULL DEFAULT 'student',
    canteen_id INT NULL,
    token VARCHAR(64) NULL UNIQUE,
    token_expires_at DATETIME NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (canteen_id) REFERENCES canteens(canteen_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- Student profiles and their canteen-scoped API tokens are separate
-- from owner accounts in users.
CREATE TABLE IF NOT EXISTS students (
    student_id INT AUTO_INCREMENT PRIMARY KEY,
    canteen_id INT NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(30) NULL,
    access_token CHAR(64) NULL UNIQUE,
    token_expires_at DATETIME NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (canteen_id) REFERENCES canteens(canteen_id) ON DELETE CASCADE,
    UNIQUE KEY uq_student_email (canteen_id, email)
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
    FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE,
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

