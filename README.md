# Canteen Owner Backend (Pure C + Windows Sockets + MySQL)

A lightweight, transparent REST API backend built **strictly in pure C** using standard Windows Sockets (`winsock2.h`) for HTTP routing, **cJSON** for serialization, and the **MySQL C API** (`mysql.h` / `libmysql`) for persistence.

Designed exclusively for the **Canteen Owner** dashboard with strict role-based access control, OTP order verification, live queue prioritization, and aggregated analytics.

---

## Architecture & Project Structure

```
cateen_owner_backend/
├── build.bat                   # Compilation script with 64-bit GCC and MySQL
├── Makefile                    # Makefile for GNU make
├── init_db.bat                 # One-click database initializer & seeder
├── test_api.bat                # Automated curl test suite for all endpoints
├── schema.sql                  # MySQL tables & initial seed data
├── .env                        # Active MySQL credentials
├── server.exe                  # Compiled standalone 64-bit binary
├── libmysql.dll                # MySQL client runtime library
├── include/
│   ├── http.h                  # Clean ~35-line HTTP request & server interface
│   ├── cJSON.h                 # cJSON library header
│   ├── db.h                    # MySQL interface & query wrapper
│   ├── auth.h                  # Token validation & owner access control
│   ├── handlers.h              # Route handler function declarations
│   └── utils.h                 # JSON response builder & code generator
├── src/
│   ├── main.c                  # Server entry point & .env loader
│   ├── http_server.c           # ~140-line pure C socket server (replaces 36,000-line Mongoose)
│   ├── cJSON.c                 # cJSON implementation
│   ├── db.c                    # MySQL connection & statement executor
│   ├── auth.c                  # Bearer token extractor & owner check
│   ├── utils.c                 # JSON formatters & join code generator
│   └── handlers/
│       ├── canteen_handler.c   # Module 1: Canteen metadata & join codes
│       ├── menu_handler.c      # Module 2: Menu CRUD & stock toggle
│       ├── order_handler.c     # Module 3: Live queue & scheduling logic
│       ├── otp_handler.c       # Module 4: Status progression & OTP handshake
│       └── analytics_handler.c # Module 5: Revenue, rush hours & top dishes
└── tools/
    └── w64devkit/              # Self-contained 64-bit MinGW-w64 toolchain
```

---

## Quick Start Guide

### Step 1: Configure Database Credentials
Create a `.env` file in the project folder (or copy from `.env.example`):
```ini
DB_HOST=127.0.0.1
DB_PORT=3307
DB_USER=root
DB_PASS=your_mysql_password
DB_NAME=canteen_db
PORT=8080
```
*(Note: If MySQL 8.0 is running on port 3306 or 3307, set `DB_PORT` accordingly.)*

### Step 2: Initialize Database
Run the initialization script in Command Prompt or PowerShell:
```cmd
init_db.bat
```
This executes `schema.sql`, creating the database, tables, and pre-loading test data:
* Default Owner Token: `owner_secret_token_123`
* Default Canteen ID: `1`

### Step 3: Build & Run
To compile the C source code into `server.exe`:
```cmd
build.bat
```
To start the backend server:
```cmd
server.exe
```
You will see:
```
====================================================
  CANTEEN OWNER BACKEND (C LANGUAGE)
====================================================
[CONFIG] Loaded settings from .env
[CONFIG] DB Host: 127.0.0.1:3307
[CONFIG] DB User: root
[CONFIG] DB Name: canteen_db
[DB SUCCESS] Connected to MySQL database 'canteen_db' at 127.0.0.1:3307
[SERVER] Canteen Owner REST API listening on http://0.0.0.0:8080
[SERVER] Ready to receive owner requests. Press Ctrl+C to exit.
```

### Step 4: Run API Tests
In a separate terminal window, run:
```cmd
test_api.bat
```
This tests all 5 modules with `curl` and prints formatted JSON outputs.

---

## Owner API Endpoints Reference

All owner endpoints require the HTTP header:
`Authorization: Bearer owner_secret_token_123`

### 1. Canteen & Invite Code Management
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/owner/canteen` | Returns canteen metadata & active join code. |
| `POST` | `/api/owner/canteen/join-code` | Regenerates a fresh join code (e.g. `CANT-9021`). |

### 2. Menu Management (Full Access)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/owner/menu` | Lists all menu items (available & out of stock). |
| `POST` | `/api/owner/menu/item` | Adds a new dish (`name`, `price`, `category`, `description`, `is_available`). |
| `PUT` | `/api/owner/menu/item` | Edits dish details (`item_id`, `name`, `price`, etc.). |
| `PATCH` | `/api/owner/menu/item/toggle-stock` | Flips an item between available and sold out. |
| `DELETE` | `/api/owner/menu/item?id=<id>` | Deletes an item from the menu. |

### 3. Live Order Queue & Scheduling
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/owner/orders/live` | Returns active orders sorted by priority: immediate orders FIFO, pre-orders by scheduled delivery time. Displays Dine-in vs Parcel flags. |

### 4. Status Progression & OTP Handshake
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `PATCH` | `/api/owner/orders/status` | Advances kitchen status: `pending` $\rightarrow$ `preparing` $\rightarrow$ `ready`. *(Rejects direct completion)*. |
| `POST` | `/api/owner/orders/verify-otp` | Owner submits student's 6-digit OTP (`order_id`, `otp`). Validates match, sets status to `completed`, and records `completed_at`. |

### 5. Sales & Order Analytics
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/owner/analytics/summary` | Total all-time revenue, total orders, today's revenue, and Dine-in vs Parcel counts. |
| `GET` | `/api/owner/analytics/rush-hours` | Hourly breakdown of orders to identify peak kitchen congestion times. |
| `GET` | `/api/owner/analytics/top-dishes` | Best-selling dishes ranked by total units sold and revenue generated. |
