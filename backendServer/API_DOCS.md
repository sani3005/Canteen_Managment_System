# Canteen Management System - Backend API Documentation

This document describes the HTTP API exposed by the Canteen Management System backend only. It is intended for frontend developers.

## Base URL

The backend listens on `0.0.0.0:8080` by default, with `0.0.0.0` exposing it on all IPv4 network interfaces.
The port can be changed with the `PORT` environment variable or the `PORT` setting in `.env`.

```text
http://localhost:8080
```

All routes below are relative to this base URL.

## Request and Response Conventions

- Use `Content-Type: application/json` for JSON request bodies.
- Every JSON success response has this envelope:

```json
{
  "status": "success",
  "message": "Human-readable description",
  "data": {}
}
```

- Every JSON error response has this envelope:

```json
{
  "status": "error",
  "message": "Human-readable description"
}
```

- HTTP status codes include `200`, `201`, `400`, `401`, `403`, `404`, `405`, `409`, and `500`.
- Unknown routes return `404 {"status":"error","message":"Endpoint not found"}`.
- An existing route with an unsupported method returns `405 {"status":"error","message":"Method not allowed"}`.
- All responses include permissive CORS headers. `OPTIONS` requests receive `204 No Content` and do not have a JSON body.

## Authentication

There is no login or password endpoint.

Protected owner endpoints require the owner's bearer token:

```http
Authorization: Bearer <owner_token>
```

Protected student endpoints require the 64-character hexadecimal bearer token returned by student registration:

```http
Authorization: Bearer <student_token>
```

The bearer prefix is mandatory. Missing or malformed authentication returns `401`. A well-formed but invalid or wrong-role token returns `403`.

For local development, `schema.sql` seeds:

```text
Owner token: owner_secret_token_123
Student token: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

Replace local seed credentials before deployment.

## Endpoint Summary

| Access | Method | Path | Purpose |
|---|---|---|---|
| Public | `GET` | `/api/health` | Backend health check |
| Public | `POST` | `/api/student/register` | Register a student and create an access token |
| Student | `GET` | `/api/student/menu` | Get the student's canteen menu |
| Student | `POST` | `/api/student/orders` | Place an order |
| Student | `GET` | `/api/student/orders` | Get the student's orders |
| Owner | `GET` | `/api/owner/canteen` | Get canteen details |
| Owner | `POST` | `/api/owner/canteen/join-code` | Generate a new join code |
| Owner | `GET` | `/api/owner/menu` | Get owner view of the menu |
| Owner | `POST` | `/api/owner/menu/item` | Create a menu item |
| Owner | `PUT` | `/api/owner/menu/item` | Update an existing menu item |
| Owner | `DELETE` | `/api/owner/menu/item` | Delete a menu item |
| Owner | `PATCH` | `/api/owner/menu/item/toggle-stock` | Set or toggle menu-item availability |
| Owner | `GET` | `/api/owner/orders/live` | Get active orders |
| Owner | `PATCH` | `/api/owner/orders/status` | Change an order status |
| Owner | `POST` | `/api/owner/orders/verify-otp` | Verify pickup OTP and complete an order |
| Owner | `GET` | `/api/owner/analytics/summary` | Get sales summary |
| Owner | `GET` | `/api/owner/analytics/rush-hours` | Get busiest completed-order hours |
| Owner | `GET` | `/api/owner/analytics/top-dishes` | Get the top 10 dishes |

---

# Public Endpoints

## `GET /api/health`

Health check. Requires no authentication.

### Response

`200 OK`

```json
{
  "status": "ok",
  "service": "canteen_owner_backend",
  "language": "C",
  "server": "CivetWeb"
}
```

This endpoint does not use the normal `success` response envelope.

---

## `POST /api/student/register`

Register a student for a canteen identified by its join code. The backend creates a 64-character hexadecimal bearer token and returns it once.

### Request Body

```json
{
  "full_name": "Sam Student",
  "email": "sam@example.edu",
  "join_code": "CANT-9021",
  "phone": "9876543210",
  "student_number": "STU-2026"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `full_name` | string | Yes | Not empty; maximum 100 characters |
| `email` | string | Yes | Maximum 150 characters; must contain `@` and cannot start or end with `@` |
| `join_code` | string | Yes | Maximum 20 characters; must identify an existing canteen |
| `phone` | string | No | Maximum 30 characters |
| `student_number` | string | No | Maximum 50 characters |

### Success Response

`201 Created`

```json
{
  "status": "success",
  "message": "Student registered for canteen",
  "data": {
    "student_id": 2,
    "canteen_id": 1,
    "token": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "token_type": "Bearer"
  }
}
```

Use the token with `Authorization: Bearer <token>`.

### Possible Errors

| Status | Message |
|---|---|
| `400` | Invalid JSON in request body |
| `400` | Provide valid full_name, email, and join_code; phone and student_number are optional |
| `400` | Invalid canteen join_code |
| `409` | This email is already registered for the selected canteen |
| `500` | Registration or database failure |

---

# Student Endpoints

All routes in this section require a valid student bearer token.

## `GET /api/student/menu`

Returns menu items belonging to the student's canteen, ordered by category and name.

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Canteen menu retrieved",
  "data": {
    "canteen_id": 1,
    "count": 5,
    "items": [
      {
        "item_id": 1,
        "name": "Special Veg Thali",
        "description": "Rice, Dal Makhani, 2 Roti, Paneer Sabzi, Salad",
        "price": 120,
        "is_available": true,
        "category": "Meals"
      }
    ]
  }
}
```

---

## `POST /api/student/orders`

Creates an immediate dine-in or parcel order. The backend validates stock, calculates prices, and generates a pickup OTP.

### Request Body

```json
{
  "order_type": "parcel",
  "items": [
    {
      "item_id": 1,
      "quantity": 2
    },
    {
      "item_id": 4,
      "quantity": 1
    }
  ]
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `order_type` | string | No | `dine_in` or `parcel`; defaults to `dine_in` |
| `items` | array | Yes | 1 to 50 entries |
| `items[].item_id` | integer | Yes | Positive integer; item must belong to the student's canteen and be available |
| `items[].quantity` | integer | Yes | From 1 through 100 |

Do not repeat an `item_id`; use `quantity` for additional units.

### Success Response

`201 Created`

```json
{
  "status": "success",
  "message": "Order placed",
  "data": {
    "order_id": 105,
    "status": "pending",
    "order_type": "parcel",
    "total_amount": 270,
    "message": "The pickup OTP will appear when the order is ready"
  }
}
```

The pickup OTP is not returned when placing the order. It is returned only in `GET /api/student/orders` after status becomes `ready`.

### Possible Errors

| Status | Message |
|---|---|
| `400` | Invalid JSON in request body |
| `400` | Provide 1 to 50 items and order_type 'dine_in' or 'parcel' |
| `400` | Each item needs a positive integer item_id and quantity from 1 to 100 |
| `400` | Do not repeat item_id values; use quantity instead |
| `400` | An item does not exist in this canteen or is out of stock |
| `500` | Transaction or database failure |

---

## `GET /api/student/orders`

Returns only the authenticated student's orders, newest first.

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Student orders retrieved",
  "data": {
    "count": 2,
    "orders": [
      {
        "order_id": 3,
        "order_type": "parcel",
        "status": "ready",
        "total_amount": 90,
        "created_at": "2026-11-21 18:30:00",
        "pickup_otp": "839201"
      },
      {
        "order_id": 1,
        "order_type": "dine_in",
        "status": "pending",
        "total_amount": 120,
        "created_at": "2026-11-21 18:15:00"
      }
    ]
  }
}
```

`pickup_otp` is present only while `status` is `ready`. It is omitted for pending, preparing, completed, and cancelled orders.

Valid order statuses are `pending`, `preparing`, `ready`, `completed`, and `cancelled`.

---

# Owner Canteen Endpoints

All routes in this section require an owner bearer token.

## `GET /api/owner/canteen`

Returns the canteen assigned to the authenticated owner.

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Canteen retrieved successfully",
  "data": {
    "canteen_id": 1,
    "name": "Main Campus Cafeteria",
    "join_code": "CANT-9021",
    "created_at": "2026-11-21 12:00:00"
  }
}
```

---

## `POST /api/owner/canteen/join-code`

Generates a new random join code for the owner's canteen. This endpoint has no request body.

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Join code regenerated successfully",
  "data": {
    "join_code": "CANT-AB12",
    "canteen_id": 1
  }
}
```

The old join code is replaced and can no longer be used for registration.

---

# Owner Menu Endpoints

All routes in this section require an owner bearer token.

## `GET /api/owner/menu`

Returns every menu item in the owner's canteen, ordered by category and name.

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Menu items retrieved",
  "data": {
    "count": 5,
    "items": [
      {
        "item_id": 1,
        "name": "Special Veg Thali",
        "description": "Rice, Dal Makhani, 2 Roti, Paneer Sabzi, Salad",
        "price": 120,
        "is_available": true,
        "category": "Meals",
        "updated_at": "2026-11-21 12:00:00"
      }
    ]
  }
}
```

---

## `POST /api/owner/menu/item`

Creates a menu item.

### Request Body

```json
{
  "name": "Butter Pav Bhaji",
  "price": 90,
  "description": "Pav bhaji with butter",
  "category": "Fast Food",
  "is_available": true
}
```

| Field | Type | Required | Default |
|---|---|---|---|
| `name` | string | Yes | None |
| `price` | number | Yes | None |
| `description` | string | No | Empty string |
| `category` | string | No | `General` |
| `is_available` | boolean | No | `true` |

### Response

`201 Created`

```json
{
  "status": "success",
  "message": "Menu item added successfully",
  "data": {
    "item_id": 6
  }
}
```

---

## `PUT /api/owner/menu/item`

Updates existing fields of a menu item. `item_id` is required. Empty strings leave string fields unchanged, and a negative price leaves price unchanged.

### Request Body

```json
{
  "item_id": 6,
  "name": "Special Butter Pav Bhaji",
  "price": 95,
  "description": "Pav bhaji with extra butter",
  "category": "Fast Food"
}
```

| Field | Type | Required |
|---|---|---|
| `item_id` | number | Yes |
| `name` | string | No |
| `description` | string | No |
| `category` | string | No |
| `price` | number | No |

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Menu item updated successfully",
  "data": {
    "item_id": 6
  }
}
```

`404` is returned when the item does not exist in the owner's canteen.

---

## `DELETE /api/owner/menu/item`

Deletes a menu item. The item ID can be supplied as a query parameter or in the JSON body.

### Request Using Query Parameter

```http
DELETE /api/owner/menu/item?id=6
Authorization: Bearer <owner_token>
```

### Request Using JSON Body

```json
{
  "item_id": 6
}
```

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Menu item deleted successfully",
  "data": {
    "deleted_item_id": 6
  }
}
```

The query parameter takes precedence over the body.

---

## `PATCH /api/owner/menu/item/toggle-stock`

Sets availability directly or toggles it when `is_available` is omitted.

### Set Availability

```json
{
  "item_id": 1,
  "is_available": false
}
```

### Toggle Availability

```json
{
  "item_id": 1
}
```

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Stock availability updated",
  "data": {
    "item_id": 1,
    "is_available": false
  }
}
```

---

# Owner Order Endpoints

All routes in this section require an owner bearer token.

## `GET /api/owner/orders/live`

Returns active orders in two groups:

- `pending`
- `preparing`
- `ready`

Cancelled and completed orders are excluded.

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Live order queue retrieved",
  "data": {
    "immediate_orders": [
      {
        "order_id": 1,
        "student_id": 2,
        "student_name": "Robin Student",
        "order_type": "dine_in",
        "packaging_label": "Dine-in",
        "is_preorder": false,
        "scheduled_time": "",
        "status": "pending",
        "total_amount": 120,
        "created_at": "2026-11-21 18:15:00",
        "items": [
          {
            "name": "Special Veg Thali",
            "quantity": 1,
            "unit_price": 120,
            "subtotal": 120
          }
        ]
      }
    ],
    "pre_orders": [],
    "total_immediate": 1,
    "total_preorders": 0,
    "total_active": 1
  }
}
```

Immediate orders are sorted by creation time. Pre-orders are sorted by scheduled time.

---

## `PATCH /api/owner/orders/status`

Changes an order to `pending`, `preparing`, `ready`, or `cancelled`. Completed status must be set through OTP verification.

### Request Body

```json
{
  "order_id": 1,
  "status": "preparing"
}
```

| Field | Type | Required |
|---|---|---|
| `order_id` | number | Yes |
| `status` | string | Yes |

Allowed values: `pending`, `preparing`, `ready`, `cancelled`.

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Order status updated",
  "data": {
    "order_id": 1,
    "new_status": "preparing"
  }
}
```

Attempting `completed` returns `400`. A missing, already completed, or already cancelled order returns `404`.

---

## `POST /api/owner/orders/verify-otp`

Verifies the student's six-digit pickup OTP and marks the order completed.

### Request Body

```json
{
  "order_id": 1,
  "otp": "412356"
}
```

`otp` may be sent as a JSON string or number.

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "OTP verified. Order marked as completed.",
  "data": {
    "order_id": 1,
    "status": "completed",
    "total_amount": 120
  }
}
```

### Possible Errors

| Status | Message |
|---|---|
| `400` | Invalid JSON in request body |
| `400` | Fields 'order_id' and 'otp' (6-digit string or number) are required |
| `400` | Invalid OTP code |
| `400` | Order was cancelled |
| `404` | Order not found for this canteen |
| `409` | Order is already completed |

---

# Owner Analytics Endpoints

All routes in this section require an owner bearer token. Analytics include completed orders only.

## `GET /api/owner/analytics/summary`

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Sales summary retrieved",
  "data": {
    "all_time_revenue": 870,
    "all_time_orders": 4,
    "today_revenue": 870,
    "today_orders": 4,
    "average_order_value": 217.5,
    "packaging_breakdown": {
      "dine_in_orders": 2,
      "parcel_orders": 2
    }
  }
}
```

`today_revenue` and `today_orders` use the database server's current date.

---

## `GET /api/owner/analytics/rush-hours`

Returns hours in descending order of completed order count.

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Peak rush hours retrieved",
  "data": {
    "peak_rush_hours": [
      {
        "hour": 12,
        "time_window": "12:00 - 13:00",
        "completed_orders": 2,
        "revenue": 540
      }
    ]
  }
}
```

---

## `GET /api/owner/analytics/top-dishes`

Returns the top 10 dishes by units sold across completed orders.

### Response

`200 OK`

```json
{
  "status": "success",
  "message": "Top selling dishes retrieved",
  "data": {
    "top_selling_dishes": [
      {
        "rank": 1,
        "item_id": 4,
        "name": "Aloo Samosa (2 pcs)",
        "category": "Snacks",
        "total_units_sold": 8,
        "total_revenue": 240
      }
    ]
  }
}
```

---

# Common Authentication Error Responses

Missing bearer token:

```json
{
  "status": "error",
  "message": "Missing token. Send header 'Authorization: ******'"
}
```

Invalid owner token or wrong role:

```json
{
  "status": "error",
  "message": "Forbidden: invalid token or owner role required"
}
```

Invalid student token:

```json
{
  "status": "error",
  "message": "Forbidden: invalid student token"
}
```

# Example Requests

Owner canteen:

```bash
curl -H "Authorization: Bearer owner_secret_token_123" \
  http://localhost:8080/api/owner/canteen
```

Student registration:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"full_name":"Sam Student","email":"sam@example.edu","join_code":"CANT-9021","phone":"9876543210","student_number":"STU-2026"}' \
  http://localhost:8080/api/student/register
```

Place a student order:

```bash
curl -X POST \
  -H "Authorization: Bearer <64-character-student-token>" \
  -H "Content-Type: application/json" \
  -d '{"order_type":"parcel","items":[{"item_id":1,"quantity":2}]}' \
  http://localhost:8080/api/student/orders
```

Verify pickup OTP:

```bash
curl -X POST \
  -H "Authorization: Bearer owner_secret_token_123" \
  -H "Content-Type: application/json" \
  -d '{"order_id":1,"otp":"412356"}' \
  http://localhost:8080/api/owner/orders/verify-otp
```
