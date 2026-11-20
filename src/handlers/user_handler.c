#include "handlers.h"
#include "db.h"
#include "utils.h"
#include "cJSON.h"
#include <bcrypt.h>
#include <limits.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MAX_ORDER_ITEMS 50
#define MAX_ITEM_QUANTITY 99
#define MAX_ORDER_TOTAL_CENTS 9999999999LL

typedef struct {
    int item_id;
    int quantity;
    long long unit_price_cents;
    char name[512];
} UserOrderItem;

static void rollback_order_transaction(void) {
    if (db_execute("ROLLBACK;") < 0) {
        fprintf(stderr, "[ORDER ERROR] Failed to roll back order transaction\n");
    }
}

static int generate_pickup_otp(char *otp, size_t otp_size) {
    ULONG random_value = 0;
    if (BCryptGenRandom(NULL, (PUCHAR)&random_value, sizeof(random_value),
                        BCRYPT_USE_SYSTEM_PREFERRED_RNG) != 0) {
        return 0;
    }
    snprintf(otp, otp_size, "%06lu", (unsigned long)(random_value % 1000000UL));
    return 1;
}

void handle_get_user_menu(HttpRequest *req, const AuthContext *ctx) {
    MYSQL_RES *res = db_query(
        "SELECT item_id, name, description, price, category "
        "FROM menu_items "
        "WHERE canteen_id = %d AND is_available = 1 "
        "ORDER BY category, name;",
        ctx->canteen_id
    );

    if (!res) {
        send_error(req->client_sock, 500, "Database error retrieving menu items");
        return;
    }

    cJSON *items = cJSON_CreateArray();
    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        cJSON *item = cJSON_CreateObject();
        cJSON_AddNumberToObject(item, "item_id", row[0] ? atoi(row[0]) : 0);
        cJSON_AddStringToObject(item, "name", row[1] ? row[1] : "");
        cJSON_AddStringToObject(item, "description", row[2] ? row[2] : "");
        cJSON_AddNumberToObject(item, "price", row[3] ? atof(row[3]) : 0.0);
        cJSON_AddStringToObject(item, "category", row[4] ? row[4] : "General");
        cJSON_AddBoolToObject(item, "is_available", true);
        cJSON_AddItemToArray(items, item);
    }
    mysql_free_result(res);

    cJSON *data = cJSON_CreateObject();
    cJSON_AddItemToObject(data, "items", items);
    cJSON_AddNumberToObject(data, "count", cJSON_GetArraySize(items));
    send_success_json(req->client_sock, 200, "Available menu items retrieved", data);
}

void handle_post_user_order(HttpRequest *req, const AuthContext *ctx) {
    cJSON *root = cJSON_Parse(req->body);
    if (!root) {
        send_error(req->client_sock, 400, "Invalid JSON in request body");
        return;
    }

    cJSON *items_obj = cJSON_GetObjectItemCaseSensitive(root, "items");
    cJSON *order_type_obj = cJSON_GetObjectItemCaseSensitive(root, "order_type");
    if (!cJSON_IsArray(items_obj) || cJSON_GetArraySize(items_obj) < 1 ||
        cJSON_GetArraySize(items_obj) > MAX_ORDER_ITEMS) {
        cJSON_Delete(root);
        send_error(req->client_sock, 400, "'items' must be an array containing 1 to 50 items");
        return;
    }

    char order_type[16] = "dine_in";
    if (order_type_obj) {
        if (!cJSON_IsString(order_type_obj) ||
            (strcmp(order_type_obj->valuestring, "dine_in") != 0 &&
             strcmp(order_type_obj->valuestring, "parcel") != 0)) {
            cJSON_Delete(root);
            send_error(req->client_sock, 400, "'order_type' must be 'dine_in' or 'parcel'");
            return;
        }
        snprintf(order_type, sizeof(order_type), "%s", order_type_obj->valuestring);
    }

    UserOrderItem items[MAX_ORDER_ITEMS];
    int item_count = cJSON_GetArraySize(items_obj);
    for (int i = 0; i < item_count; i++) {
        cJSON *item_obj = cJSON_GetArrayItem(items_obj, i);
        cJSON *id_obj = item_obj ? cJSON_GetObjectItemCaseSensitive(item_obj, "item_id") : NULL;
        cJSON *quantity_obj = item_obj ? cJSON_GetObjectItemCaseSensitive(item_obj, "quantity") : NULL;
        if (!cJSON_IsNumber(id_obj) || !cJSON_IsNumber(quantity_obj) ||
            id_obj->valuedouble < 1 || id_obj->valuedouble > INT_MAX ||
            quantity_obj->valuedouble < 1 || quantity_obj->valuedouble > MAX_ITEM_QUANTITY ||
            id_obj->valuedouble != (double)(int)id_obj->valuedouble ||
            quantity_obj->valuedouble != (double)(int)quantity_obj->valuedouble) {
            cJSON_Delete(root);
            send_error(req->client_sock, 400, "Each item requires a positive integer item_id and quantity from 1 to 99");
            return;
        }

        items[i].item_id = id_obj->valueint;
        items[i].quantity = quantity_obj->valueint;
        items[i].unit_price_cents = 0;
        items[i].name[0] = '\0';
        for (int j = 0; j < i; j++) {
            if (items[j].item_id == items[i].item_id) {
                cJSON_Delete(root);
                send_error(req->client_sock, 400, "Duplicate item_id values are not allowed");
                return;
            }
        }
    }
    cJSON_Delete(root);

    char otp[7];
    if (!generate_pickup_otp(otp, sizeof(otp))) {
        send_error(req->client_sock, 500, "Could not generate pickup verification code");
        return;
    }

    if (db_execute("START TRANSACTION;") < 0) {
        send_error(req->client_sock, 500, "Could not start order transaction");
        return;
    }

    long long total_cents = 0;
    for (int i = 0; i < item_count; i++) {
        MYSQL_RES *res = db_query(
            "SELECT name, price, is_available FROM menu_items "
            "WHERE item_id = %d AND canteen_id = %d LIMIT 1 FOR UPDATE;",
            items[i].item_id, ctx->canteen_id
        );
        if (!res) {
            rollback_order_transaction();
            send_error(req->client_sock, 500, "Database error validating menu items");
            return;
        }

        MYSQL_ROW row = mysql_fetch_row(res);
        if (!row) {
            mysql_free_result(res);
            rollback_order_transaction();
            send_error(req->client_sock, 404, "One or more menu items were not found in your canteen");
            return;
        }

        if (!row[2] || atoi(row[2]) != 1) {
            mysql_free_result(res);
            rollback_order_transaction();
            send_error(req->client_sock, 409, "One or more selected menu items are unavailable");
            return;
        }

        char *price_end = NULL;
        double unit_price = row[1] ? strtod(row[1], &price_end) : -1.0;
        if (!row[1] || !price_end || price_end == row[1] || *price_end != '\0' || !isfinite(unit_price) ||
            unit_price < 0.0 || unit_price > 99999999.99) {
            mysql_free_result(res);
            rollback_order_transaction();
            send_error(req->client_sock, 500, "Invalid menu item price in database");
            return;
        }

        items[i].unit_price_cents = (long long)(unit_price * 100.0 + 0.5);
        snprintf(items[i].name, sizeof(items[i].name), "%s", row[0] ? row[0] : "");
        mysql_free_result(res);

        long long subtotal_cents = items[i].unit_price_cents * items[i].quantity;
        if (subtotal_cents > MAX_ORDER_TOTAL_CENTS - total_cents) {
            rollback_order_transaction();
            send_error(req->client_sock, 400, "Order total exceeds the supported amount");
            return;
        }
        total_cents += subtotal_cents;
    }

    int order_rows = db_execute(
        "INSERT INTO orders (canteen_id, student_id, order_type, is_preorder, status, otp_code, total_amount) "
        "VALUES (%d, %d, '%s', 0, 'pending', '%s', %.2f);",
        ctx->canteen_id, ctx->user_id, order_type, otp, total_cents / 100.0
    );
    if (order_rows != 1) {
        rollback_order_transaction();
        send_error(req->client_sock, 500, "Failed to create order");
        return;
    }

    unsigned long long order_id = db_last_insert_id();
    if (order_id == 0) {
        rollback_order_transaction();
        send_error(req->client_sock, 500, "Could not retrieve created order identifier");
        return;
    }

    for (int i = 0; i < item_count; i++) {
        long long subtotal_cents = items[i].unit_price_cents * items[i].quantity;
        int item_rows = db_execute(
            "INSERT INTO order_items (order_id, item_id, quantity, unit_price, subtotal) "
            "VALUES (%llu, %d, %d, %.2f, %.2f);",
            order_id, items[i].item_id, items[i].quantity,
            items[i].unit_price_cents / 100.0, subtotal_cents / 100.0
        );
        if (item_rows != 1) {
            rollback_order_transaction();
            send_error(req->client_sock, 500, "Failed to save order items");
            return;
        }
    }

    if (db_execute("COMMIT;") < 0) {
        rollback_order_transaction();
        send_error(req->client_sock, 500, "Failed to finalize order");
        return;
    }

    cJSON *bill_items = cJSON_CreateArray();
    for (int i = 0; i < item_count; i++) {
        long long subtotal_cents = items[i].unit_price_cents * items[i].quantity;
        cJSON *bill_item = cJSON_CreateObject();
        cJSON_AddNumberToObject(bill_item, "item_id", items[i].item_id);
        cJSON_AddStringToObject(bill_item, "name", items[i].name);
        cJSON_AddNumberToObject(bill_item, "quantity", items[i].quantity);
        cJSON_AddNumberToObject(bill_item, "unit_price", items[i].unit_price_cents / 100.0);
        cJSON_AddNumberToObject(bill_item, "subtotal", subtotal_cents / 100.0);
        cJSON_AddItemToArray(bill_items, bill_item);
    }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "order_id", (double)order_id);
    cJSON_AddStringToObject(data, "status", "pending");
    cJSON_AddStringToObject(data, "order_type", order_type);
    cJSON_AddItemToObject(data, "items", bill_items);
    cJSON_AddNumberToObject(data, "total_amount", total_cents / 100.0);
    cJSON_AddStringToObject(data, "pickup_otp", otp);
    send_success_json(req->client_sock, 201, "Order placed and bill generated", data);
}
