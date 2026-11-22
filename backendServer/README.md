# Canteen Management API (C + CivetWeb + MySQL)

Renuka Veer, Roll No 2639

A REST API for canteen owners and students, written in C. **CivetWeb** handles the web server part
(sockets, HTTP parsing), so this project only contains the canteen logic.
Works on Windows, Linux and Mac.

## 1. What you need
1. A C compiler (`gcc`). Windows: MinGW-w64 or w64devkit.
2. MySQL (server + C client library / headers). The Makefile is configured for MySQL 9.4 on this Windows setup.

CivetWeb (web server library) and cJSON (JSON library) are already included in this
project, so there is nothing else to download.

## 2. Setup
```
mysql -u root -p < schema.sql        # creates canteen_db + sample data
copy .env.example .env               # (Linux/Mac: cp)  then edit DB_PASS and DB_PORT
make                                 # builds server (server.exe on Windows)
./server                             # Windows: server.exe
```

For an existing database created using the previous schema, run
`mysql -u root -p < migrate_students.sql` to create the separate student table and
move the orders' student foreign key to it. Back up the database before migrating.
The server binds to `0.0.0.0:8080`, which makes it reachable on the machine's network interfaces.
Open http://localhost:8080/api/health on this machine, or http://&lt;this-PC-IP-address&gt;:8080/api/health
from another device on the same network. Press Enter in the console to stop.

Linux needs: `sudo apt install build-essential libmysqlclient-dev`
Windows: if MySQL is not in `C:/Program Files/MySQL/MySQL Server 9.4`, run
`make MYSQL_DIR="C:/your/path"`. Copy `libmysql.dll` next to `server.exe`.

## 3. Folder guide
| File | What it does |
|------|--------------|
| `src/main.c` | Reads `.env`, connects to MySQL, starts CivetWeb |
| `src/routes.c` | **The route table**: URL + method -> handler function. Checks owner or student tokens |
| `src/auth.c` | Checks `Authorization: Bearer <token>` against the `users` table |
| `src/db.c` | MySQL helper: `db_query`, `db_execute`, `db_escape` |
| `src/utils.c` | `send_success`, `send_error`, `read_body`, `get_query_param` |
| `src/handlers/canteen_handler.c` | Canteen info, new join code, health check |
| `src/handlers/menu_handler.c` | Menu: list, add, edit, delete, stock toggle |
| `src/handlers/order_handler.c` | Live order queue |
| `src/handlers/otp_handler.c` | Order status changes + OTP verification |
| `src/handlers/analytics_handler.c` | Revenue summary, rush hours, top dishes |
| `src/handlers/student_handler.c` | Student registration, canteen menu, orders, and pickup status |
| `civetweb/` | The CivetWeb library (third-party, don't edit) |
| `src/cJSON.c`, `include/cJSON.h` | The cJSON library (third-party, don't edit) |
| `schema.sql` | Creates the database tables and sample data |
| `.env.example` | Template for your `.env` settings file |
| `test_api.sh` / `test_api.bat` | Calls every endpoint so you can check it works |

**Where to start reading the code:** `src/main.c` (how the program starts), then
`src/routes.c` (the list of endpoints), then any one file in `src/handlers/`.

## 4. How a request flows
1. CivetWeb receives the request and calls `api_handler()` in `routes.c`.
2. `api_handler` finds the matching row in the route table (404 / 405 if none).
3. If the route is protected, `auth_check_owner()` or `auth_check_student()` checks its bearer token (401 / 403 if missing or invalid).
4. The handler runs, talks to MySQL, and replies with `send_success()` or `send_error()`.

Owners authenticate through `users`; student registration creates a separate record in
`students` and returns a random bearer token tied to that student's canteen. Send student
tokens as `Authorization: Bearer <token>` for the student menu and order endpoints.

## 5. Add a new endpoint (3 steps)
1. Write `void handle_xxx(struct mg_connection *conn, const AuthContext *auth, const char *body)` in a handler file.
2. Declare it in `include/handlers.h`.
3. Add one line to the table in `src/routes.c`.

## 6. Endpoints
Send the header `Authorization: Bearer owner_secret_token_123` (from the sample data).

Owner requests use `Authorization: Bearer <owner-token>`. Student requests use the token
returned by registration as `Authorization: Bearer <student-token>`.

| Method | URL | Purpose |
|--------|-----|---------|
| GET | /api/health | Health check (no token) |
| POST | /api/student/register | Register with `{"full_name":"Sam Student","email":"sam@example.edu","join_code":"CANT-9021","phone":"...","student_number":"..."}`. Optional phone and student_number. Returns a student bearer token. |
| GET | /api/student/menu | Student's canteen menu and stock availability. Requires student token. |
| POST | /api/student/orders | Place an order with `{"order_type":"parcel","items":[{"item_id":1,"quantity":2}]}`. Requires student token; price is calculated by the server. |
| GET | /api/student/orders | Student's own orders and status; pickup OTP is included when an order is ready. Requires student token. |
| GET | /api/owner/canteen | Canteen details + join code |
| POST | /api/owner/canteen/join-code | Make a new join code |
| GET | /api/owner/menu | List menu |
| POST | /api/owner/menu/item | Add item `{"name","price","description","category","is_available"}` |
| PUT | /api/owner/menu/item | Edit item `{"item_id", ...fields}` |
| DELETE | /api/owner/menu/item?id=5 | Delete item |
| PATCH | /api/owner/menu/item/toggle-stock | `{"item_id":1}` flips availability |
| GET | /api/owner/orders/live | Pending/preparing/ready orders (immediate vs pre-orders) |
| PATCH | /api/owner/orders/status | `{"order_id":1,"status":"preparing"}` |
| POST | /api/owner/orders/verify-otp | `{"order_id":1,"otp":"412356"}` completes the order |
| GET | /api/owner/analytics/summary | Revenue, order counts, average order |
| GET | /api/owner/analytics/rush-hours | Busiest hours |
| GET | /api/owner/analytics/top-dishes | Top 10 dishes |

Student flow: register using the join code shown by the canteen, save the returned token
securely, call `GET /api/student/menu`, and then place orders with that token. Registration
creates a canteen-scoped record in `students`; a token cannot access another canteen.

Test owner endpoints: `./test_api.sh` (Linux/Mac/Git Bash) or `test_api.bat` (Windows).

## 7. Notes
- The server uses one worker thread (`num_threads` in `main.c`) so the single MySQL connection
  is never shared by two requests at once. This keeps the code simple.
- Registration returns a 256-bit bearer token once. Students should keep it private; it is their API credential.
- The sample student token in `schema.sql` is for local testing only. Replace it before exposing the API publicly.
