@echo off
setlocal

echo ========================================================
echo   Canteen Owner Backend - API Endpoint Test Suite
echo ========================================================

set "BASE_URL=http://127.0.0.1:8080"
set "TOKEN=owner_secret_token_123"
set "AUTH_HDR=Authorization: Bearer %TOKEN%"
set "JSON_HDR=Content-Type: application/json"

echo.
echo [TEST 1] Public Health Check
curl -s -X GET "%BASE_URL%/api/health"
echo.

echo.
echo [TEST 2] Access Control: Test Request Without Token (Expected 401)
curl -s -i -X GET "%BASE_URL%/api/owner/canteen" | findstr "HTTP/ 401 Unauthorized error"
echo.

echo.
echo [TEST 3] Module 1: Get Canteen Details ^& Join Code
curl -s -X GET "%BASE_URL%/api/owner/canteen" -H "%AUTH_HDR%"
echo.

echo.
echo [TEST 4] Module 1: Regenerate Canteen Join Code (e.g. CANT-XXXX)
curl -s -X POST "%BASE_URL%/api/owner/canteen/join-code" -H "%AUTH_HDR%"
echo.

echo.
echo [TEST 5] Module 2: Get Menu Items
curl -s -X GET "%BASE_URL%/api/owner/menu" -H "%AUTH_HDR%"
echo.

echo.
echo [TEST 6] Module 2: Add New Menu Item (Butter Pav Bhaji)
curl -s -X POST "%BASE_URL%/api/owner/menu/item" -H "%AUTH_HDR%" -H "%JSON_HDR%" -d "{\"name\":\"Butter Pav Bhaji\",\"description\":\"Spicy mashed vegetable curry with 2 butter toasted pavs\",\"price\":90.00,\"category\":\"Fast Food\",\"is_available\":true}"
echo.

echo.
echo [TEST 7] Module 2: Toggle Stock Availability (Item 1)
curl -s -X PATCH "%BASE_URL%/api/owner/menu/item/toggle-stock" -H "%AUTH_HDR%" -H "%JSON_HDR%" -d "{\"item_id\":1}"
echo.

echo.
echo [TEST 8] Module 3: Live Order Queue (Immediate vs Pre-orders, Dine-in vs Parcel)
curl -s -X GET "%BASE_URL%/api/owner/orders/live" -H "%AUTH_HDR%"
echo.

echo.
echo [TEST 9] Module 4: Kitchen Status Progression (Pending -^> Preparing -^> Ready)
curl -s -X PATCH "%BASE_URL%/api/owner/orders/status" -H "%AUTH_HDR%" -H "%JSON_HDR%" -d "{\"order_id\":1,\"status\":\"ready\"}"
echo.

echo.
echo [TEST 10] Module 4: OTP Verification Handshake (Mark as Completed)
curl -s -X POST "%BASE_URL%/api/owner/orders/verify-otp" -H "%AUTH_HDR%" -H "%JSON_HDR%" -d "{\"order_id\":1,\"otp\":\"412356\"}"
echo.

echo.
echo [TEST 11] Module 5: Sales ^& Order Analytics Summary
curl -s -X GET "%BASE_URL%/api/owner/analytics/summary" -H "%AUTH_HDR%"
echo.

echo.
echo [TEST 12] Module 5: Peak Rush Hours Analytics
curl -s -X GET "%BASE_URL%/api/owner/analytics/rush-hours" -H "%AUTH_HDR%"
echo.

echo.
echo [TEST 13] Module 5: Top Selling Dishes
curl -s -X GET "%BASE_URL%/api/owner/analytics/top-dishes" -H "%AUTH_HDR%"
echo.

echo.
echo ========================================================
echo   Test Suite Completed!
echo ========================================================

endlocal
