
# Student ordering API

The backend exposes student-only endpoints. Include the student's token in the
Authorization header using the Bearer scheme.

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
itemized bill from current menu prices, creates the order, and returns the
total and pickup OTP. Item quantities must be from 1 to 99; duplicate item IDs
are not allowed in one request.
