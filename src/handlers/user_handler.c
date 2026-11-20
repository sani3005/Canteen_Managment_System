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
#define PASSWORD_SALT_BYTES 16
#define PASSWORD_HASH_BYTES 32
#define PASSWORD_ITERATIONS 210000
#define USER_TOKEN_BYTES 32

typedef struct {
    int item_id;
    int quantity;
    long long unit_price_cents;
    char name[512];
} UserOrderItem;

static const char HEX_DIGITS[] = "0123456789abcdef";

static void bytes_to_hex(const unsigned char *bytes, size_t byte_count, char *hex) {
    for (size_t i = 0; i < byte_count; i++) {
        hex[i * 2] = HEX_DIGITS[bytes[i] >> 4];
        hex[i * 2 + 1] = HEX_DIGITS[bytes[i] & 0x0f];
    }
    hex[byte_count * 2] = '\0';
}

static int hex_to_bytes(const char *hex, size_t byte_count, unsigned char *bytes) {
    for (size_t i = 0; i < byte_count; i++) {
        int high = hex[i * 2] >= '0' && hex[i * 2] <= '9' ? hex[i * 2] - '0' :
                   hex[i * 2] >= 'a' && hex[i * 2] <= 'f' ? hex[i * 2] - 'a' + 10 :
                   hex[i * 2] >= 'A' && hex[i * 2] <= 'F' ? hex[i * 2] - 'A' + 10 : -1;
        int low = hex[i * 2 + 1] >= '0' && hex[i * 2 + 1] <= '9' ? hex[i * 2 + 1] - '0' :
                  hex[i * 2 + 1] >= 'a' && hex[i * 2 + 1] <= 'f' ? hex[i * 2 + 1] - 'a' + 10 :
                  hex[i * 2 + 1] >= 'A' && hex[i * 2 + 1] <= 'F' ? hex[i * 2 + 1] - 'A' + 10 : -1;
        if (high < 0 || low < 0) return 0;
        bytes[i] = (unsigned char)((high << 4) | low);
    }
    return 1;
}

static int derive_password_hash(const char *password, const unsigned char *salt,
                                unsigned char *hash) {
    BCRYPT_ALG_HANDLE algorithm = NULL;
    NTSTATUS status = BCryptOpenAlgorithmProvider(
        &algorithm, BCRYPT_SHA256_ALGORITHM, NULL, BCRYPT_ALG_HANDLE_HMAC_FLAG);
    if (status < 0) return 0;

    status = BCryptDeriveKeyPBKDF2(
        algorithm, (PUCHAR)password, (ULONG)strlen(password),
        (PUCHAR)salt, PASSWORD_SALT_BYTES, PASSWORD_ITERATIONS,
        hash, PASSWORD_HASH_BYTES, 0);
    BCryptCloseAlgorithmProvider(algorithm, 0);
    return status >= 0;
}

static int hash_password(const char *password, char *encoded, size_t encoded_size) {
    unsigned char salt[PASSWORD_SALT_BYTES];
    unsigned char hash[PASSWORD_HASH_BYTES];
    char salt_hex[PASSWORD_SALT_BYTES * 2 + 1];
    char hash_hex[PASSWORD_HASH_BYTES * 2 + 1];

    if (BCryptGenRandom(NULL, salt, sizeof(salt), BCRYPT_USE_SYSTEM_PREFERRED_RNG) < 0 ||
        !derive_password_hash(password, salt, hash)) {
        return 0;
    }
    bytes_to_hex(salt, sizeof(salt), salt_hex);
    bytes_to_hex(hash, sizeof(hash), hash_hex);
    int written = snprintf(encoded, encoded_size, "pbkdf2_sha256$%u$%s$%s",
                           PASSWORD_ITERATIONS, salt_hex, hash_hex);
    return written > 0 && (size_t)written < encoded_size;
}

static int verify_password(const char *password, const char *encoded) {
    static const char prefix[] = "pbkdf2_sha256$210000$";
    if (strlen(encoded) != (sizeof(prefix) - 1) + PASSWORD_SALT_BYTES * 2 + 1 +
                           PASSWORD_HASH_BYTES * 2) {
        return 0;
    }
    unsigned char salt[PASSWORD_SALT_BYTES];
    unsigned char expected[PASSWORD_HASH_BYTES];
    unsigned char actual[PASSWORD_HASH_BYTES];
    const char *salt_hex = encoded + sizeof(prefix) - 1;
    const char *separator = strchr(salt_hex, '$');
    if (strncmp(encoded, prefix, sizeof(prefix) - 1) != 0 ||
        !separator || (size_t)(separator - salt_hex) != PASSWORD_SALT_BYTES * 2 ||
        strlen(separator + 1) != PASSWORD_HASH_BYTES * 2 ||
        !hex_to_bytes(salt_hex, PASSWORD_SALT_BYTES, salt) ||
        !hex_to_bytes(separator + 1, PASSWORD_HASH_BYTES, expected) ||
        !derive_password_hash(password, salt, actual)) {
        return 0;
    }

    unsigned char difference = 0;
    for (size_t i = 0; i < sizeof(actual); i++) {
        difference |= actual[i] ^ expected[i];
    }
    return difference == 0;
}

static int valid_username(const char *username) {
    size_t length = strlen(username);
    if (length < 3 || length > 50) return 0;
    for (size_t i = 0; i < length; i++) {
        char ch = username[i];
        if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') ||
              (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' || ch == '.')) {
            return 0;
        }
    }
    return 1;
}

static int escape_sql_string(const char *input, char *output) {
    unsigned long input_length = (unsigned long)strlen(input);
    output[0] = '\0';
    unsigned long escaped_length = db_escape_string(output, input, input_length);
    return escaped_length > 0 && escaped_length <= input_length * 2;
}

static void rollback_order_transaction(void) {
    if (db_execute("ROLLBACK;") < 0) {
        fprintf(stderr, "[ORDER ERROR] Failed to roll back order transaction\n");
    }
}

void handle_post_user_register(HttpRequest *req) {
        cJSON *root = cJSON_Parse(req->body);
        if (!root) {
            send_error(req->client_sock, 400, "Invalid JSON in request body");
            return;
        }

        cJSON *username_obj = cJSON_GetObjectItemCaseSensitive(root, "username");
        cJSON *password_obj = cJSON_GetObjectItemCaseSensitive(root, "password");
        cJSON *join_code_obj = cJSON_GetObjectItemCaseSensitive(root, "join_code");
        if (!cJSON_IsString(username_obj) || !cJSON_IsString(password_obj) ||
            !cJSON_IsString(join_code_obj)) {
            cJSON_Delete(root);
            send_error(req->client_sock, 400, "Fields 'username', 'password', and 'join_code' are required");
            return;
        }

        const char *username = username_obj->valuestring;
        const char *password = password_obj->valuestring;
        const char *join_code = join_code_obj->valuestring;
        if (!valid_username(username)) {
            cJSON_Delete(root);
            send_error(req->client_sock, 400, "Username must be 3-50 characters using letters, numbers, '.', '_' or '-'");
            return;
        }
        if (strlen(password) < 8 || strlen(password) > 128) {
            cJSON_Delete(root);
            send_error(req->client_sock, 400, "Password must be between 8 and 128 characters");
            return;
        }
        if (strlen(join_code) == 0 || strlen(join_code) > 20) {
            cJSON_Delete(root);
            send_error(req->client_sock, 400, "A valid canteen join_code is required");
            return;
        }

        char saved_username[51];
        snprintf(saved_username, sizeof(saved_username), "%s", username);
        char escaped_username[101];
        char escaped_join_code[41];
        if (!escape_sql_string(username, escaped_username) ||
            !escape_sql_string(join_code, escaped_join_code)) {
            cJSON_Delete(root);
            send_error(req->client_sock, 500, "Could not safely prepare registration data");
            return;
        }

        MYSQL_RES *res = db_query("SELECT canteen_id FROM canteens WHERE join_code = '%s' LIMIT 1;",
                                  escaped_join_code);
        if (!res) {
            cJSON_Delete(root);
            send_error(req->client_sock, 500, "Database error validating canteen join code");
            return;
        }
        MYSQL_ROW row = mysql_fetch_row(res);
        if (!row || !row[0]) {
            mysql_free_result(res);
            cJSON_Delete(root);
            send_error(req->client_sock, 404, "Canteen join code was not found");
            return;
        }
        int canteen_id = atoi(row[0]);
        mysql_free_result(res);

        res = db_query("SELECT user_id FROM users WHERE username = '%s' LIMIT 1;", escaped_username);
        if (!res) {
            cJSON_Delete(root);
            send_error(req->client_sock, 500, "Database error checking username");
            return;
        }
        row = mysql_fetch_row(res);
        if (row) {
            mysql_free_result(res);
            cJSON_Delete(root);
            send_error(req->client_sock, 409, "Username is already registered");
            return;
        }
        mysql_free_result(res);

        char password_hash[128];
        if (!hash_password(password, password_hash, sizeof(password_hash))) {
            cJSON_Delete(root);
            send_error(req->client_sock, 500, "Could not securely hash password");
            return;
        }
        cJSON_Delete(root);

        int affected = db_execute(
            "INSERT INTO users (username, password_hash, role, canteen_id) "
            "VALUES ('%s', '%s', 'student', %d);",
            escaped_username, password_hash, canteen_id
        );
        if (affected != 1) {
            send_error(req->client_sock, 409, "Registration failed; username may already be registered");
            return;
        }

        cJSON *data = cJSON_CreateObject();
        cJSON_AddNumberToObject(data, "user_id", (double)db_last_insert_id());
        cJSON_AddStringToObject(data, "username", saved_username);
        cJSON_AddNumberToObject(data, "canteen_id", canteen_id);
        send_success_json(req->client_sock, 201, "Registration successful; log in to place orders", data);
    }

void handle_post_user_login(HttpRequest *req) {
        cJSON *root = cJSON_Parse(req->body);
        if (!root) {
            send_error(req->client_sock, 400, "Invalid JSON in request body");
            return;
        }

        cJSON *username_obj = cJSON_GetObjectItemCaseSensitive(root, "username");
        cJSON *password_obj = cJSON_GetObjectItemCaseSensitive(root, "password");
        if (!cJSON_IsString(username_obj) || !cJSON_IsString(password_obj) ||
            !valid_username(username_obj->valuestring) ||
            strlen(password_obj->valuestring) > 128) {
            cJSON_Delete(root);
            send_error(req->client_sock, 400, "Valid 'username' and 'password' fields are required");
            return;
        }

        char escaped_username[101];
        if (!escape_sql_string(username_obj->valuestring, escaped_username)) {
            cJSON_Delete(root);
            send_error(req->client_sock, 500, "Could not safely prepare login data");
            return;
        }
        MYSQL_RES *res = db_query(
            "SELECT user_id, canteen_id, password_hash FROM users "
            "WHERE username = '%s' AND role = 'student' LIMIT 1;",
            escaped_username
        );
        if (!res) {
            cJSON_Delete(root);
            send_error(req->client_sock, 500, "Database error during login");
            return;
        }

        MYSQL_ROW row = mysql_fetch_row(res);
        if (!row || !row[0] || !row[1] || !row[2] ||
            !verify_password(password_obj->valuestring, row[2])) {
            mysql_free_result(res);
            cJSON_Delete(root);
            send_error(req->client_sock, 401, "Invalid username or password");
            return;
        }

        int user_id = atoi(row[0]);
        int canteen_id = atoi(row[1]);
        mysql_free_result(res);
        cJSON_Delete(root);

        unsigned char token_bytes[USER_TOKEN_BYTES];
        char token[USER_TOKEN_BYTES * 2 + 1];
        if (BCryptGenRandom(NULL, token_bytes, sizeof(token_bytes),
                            BCRYPT_USE_SYSTEM_PREFERRED_RNG) < 0) {
            send_error(req->client_sock, 500, "Could not generate login token");
            return;
        }
        bytes_to_hex(token_bytes, sizeof(token_bytes), token);

        int affected = db_execute(
            "UPDATE users SET token = '%s', token_expires_at = DATE_ADD(NOW(), INTERVAL 30 DAY) "
            "WHERE user_id = %d AND role = 'student';",
            token, user_id
        );
        if (affected != 1) {
            send_error(req->client_sock, 500, "Could not create login session");
            return;
        }

        cJSON *data = cJSON_CreateObject();
        cJSON_AddStringToObject(data, "token", token);
        cJSON_AddNumberToObject(data, "user_id", user_id);
        cJSON_AddNumberToObject(data, "canteen_id", canteen_id);
        send_success_json(req->client_sock, 200, "Login successful", data);
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
    send_success_json(req->client_sock, 201, "Order placed and bill generated", data);
}

void handle_get_user_order(HttpRequest *req, const AuthContext *ctx) {
    char order_id_text[32];
    if (!get_query_param(req, "id", order_id_text, sizeof(order_id_text))) {
        send_error(req->client_sock, 400, "A valid order id query parameter is required");
        return;
    }

    char *end = NULL;
    long order_id = strtol(order_id_text, &end, 10);
    if (!end || *end != '\0' || order_id <= 0 || order_id > INT_MAX) {
        send_error(req->client_sock, 400, "A valid positive order id is required");
        return;
    }

    MYSQL_RES *res = db_query(
        "SELECT order_id, status, order_type, total_amount, created_at, otp_code "
        "FROM orders WHERE order_id = %ld AND student_id = %d AND canteen_id = %d LIMIT 1;",
        order_id, ctx->user_id, ctx->canteen_id
    );
    if (!res) {
        send_error(req->client_sock, 500, "Database error retrieving order");
        return;
    }

    MYSQL_ROW row = mysql_fetch_row(res);
    if (!row) {
        mysql_free_result(res);
        send_error(req->client_sock, 404, "Order not found for this student");
        return;
    }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "order_id", row[0] ? atof(row[0]) : 0);
    cJSON_AddStringToObject(data, "status", row[1] ? row[1] : "");
    cJSON_AddStringToObject(data, "order_type", row[2] ? row[2] : "");
    cJSON_AddNumberToObject(data, "total_amount", row[3] ? atof(row[3]) : 0.0);
    cJSON_AddStringToObject(data, "created_at", row[4] ? row[4] : "");
    if (row[1] && strcmp(row[1], "ready") == 0) {
        cJSON_AddStringToObject(data, "pickup_otp", row[5] ? row[5] : "");
    }
    mysql_free_result(res);
    send_success_json(req->client_sock, 200, "Order status retrieved", data);
}
