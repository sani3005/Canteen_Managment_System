#!/bin/sh
# Quick API test (Linux / Mac / Git Bash). Start the server first.
BASE=http://127.0.0.1:8080
TOKEN=owner_secret_token_123
AUTH="Authorization: Bearer $TOKEN"
JSON="Content-Type: application/json"

echo "1. Health";            curl -s $BASE/api/health; echo
echo "2. No token (401)";    curl -s $BASE/api/owner/canteen; echo
echo "3. Canteen";           curl -s -H "$AUTH" $BASE/api/owner/canteen; echo
echo "4. New join code";     curl -s -X POST -H "$AUTH" $BASE/api/owner/canteen/join-code; echo
echo "5. Menu";              curl -s -H "$AUTH" $BASE/api/owner/menu; echo
echo "6. Add item";          curl -s -X POST -H "$AUTH" -H "$JSON" -d '{"name":"Butter Pav Bhaji","price":90,"category":"Fast Food"}' $BASE/api/owner/menu/item; echo
echo "7. Toggle stock";      curl -s -X PATCH -H "$AUTH" -H "$JSON" -d '{"item_id":1}' $BASE/api/owner/menu/item/toggle-stock; echo
echo "8. Live orders";       curl -s -H "$AUTH" $BASE/api/owner/orders/live; echo
echo "9. Status -> ready";   curl -s -X PATCH -H "$AUTH" -H "$JSON" -d '{"order_id":1,"status":"ready"}' $BASE/api/owner/orders/status; echo
echo "10. Verify OTP";       curl -s -X POST -H "$AUTH" -H "$JSON" -d '{"order_id":1,"otp":"412356"}' $BASE/api/owner/orders/verify-otp; echo
echo "11. Summary";          curl -s -H "$AUTH" $BASE/api/owner/analytics/summary; echo
echo "12. Rush hours";       curl -s -H "$AUTH" $BASE/api/owner/analytics/rush-hours; echo
echo "13. Top dishes";       curl -s -H "$AUTH" $BASE/api/owner/analytics/top-dishes; echo
