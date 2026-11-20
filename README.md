
# Student registration, login, and ordering API

## Register

```http
POST /api/user/register
Content-Type: application/json
```

```json
{
  "username": "student_robin",
  "password": "choose-a-password",
  "join_code": "CANT-9021"
}
```

Registration stores a student account associated with the canteen. Usernames
must be 3-50 letters, numbers, dots, underscores, or hyphens; passwords must be
8-128 characters. Passwords are stored as salted PBKDF2-SHA256 hashes.

## Log in

```http
POST /api/user/login
Content-Type: application/json
```

```json
{
  "username": "student_robin",
  "password": "choose-a-password"
}
```

Use the returned token as `Authorization: Bearer <token>` on student menu,
order, and order-status requests.

## View the available menu

```http
GET /api/user/menu
```

Returns available items for the student's associated canteen.

## Place an order and generate its bill

```http
POST /api/user/orders
Content-Type: application/json
```

```json
{
  "order_type": "dine_in",
  "items": [
    { "item_id": 1, "quantity": 2 },
    { "item_id": 4, "quantity": 1 }
  ]
}
```

`order_type` may be `dine_in` or `parcel` and defaults to `dine_in`. The server
checks that all items are available in the student's canteen, calculates the
itemized bill from current menu prices, and creates the order. The response
includes the unique `order_id` to show the canteen and the bill total. Item
quantities must be from 1 to 99; duplicate item IDs are not allowed in one
request.

## Check order status and pickup code

```http
GET /api/user/orders?id=<order_id>
```

Only the student who placed the order can view it. The response includes the
pickup OTP when the order is ready; show the order ID to the canteen and provide
the OTP when collecting it.
