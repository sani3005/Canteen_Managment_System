#include "handlers.h"
#include "db.h"
#include "utils.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MAX_ORDER_ITEMS 50
#define PASSWORD_ITERATIONS 10000

typedef struct {
    int item_id;
    int quantity;
    long long unit_price_cents;
} StudentOrderItem;

static void bytes_to_hex(const unsigned char *bytes, size_t count, char *out) {
    static const char digits[] = "0123456789abcdef";
    for (size_t i = 0; i < count; i++) {
        out[i * 2] = digits[bytes[i] >> 4];
        out[i * 2 + 1] = digits[bytes[i] & 0x0f];
    }
    out[count * 2] = '\0';
}

static void derive_password_hash(const char *password, const char *salt, char *digest) {
    char current[33];
    mg_md5(current, password, salt, NULL);
    for (int i = 1; i < PASSWORD_ITERATIONS; i++) {
        char next[33];
        mg_md5(next, current, salt, NULL);
        memcpy(current, next, sizeof(current));
    }
    memcpy(digest, current, 33);
}

static int create_password_hash(const char *password, char *out, size_t out_size) {
    unsigned char salt_bytes[16];
    if (!generate_secure_random(salt_bytes, sizeof(salt_bytes))) return 0;
    char salt[33], digest[33];
    bytes_to_hex(salt_bytes, sizeof(salt_bytes), salt);
    derive_password_hash(password, salt, digest);
    snprintf(out, out_size, "pbkdf2-md5$%d$%s$%s", PASSWORD_ITERATIONS, salt, digest);
    return 1;
}

static int constant_time_equal(const char *a, const char *b) {
    size_t a_len = strlen(a), b_len = strlen(b);
    size_t len = a_len > b_len ? a_len : b_len;
    unsigned char difference = (unsigned char)(a_len != b_len);
    for (size_t i = 0; i < len; i++) {
        unsigned char ac = i < a_len ? (unsigned char)a[i] : 0;
        unsigned char bc = i < b_len ? (unsigned char)b[i] : 0;
        difference |= ac ^ bc;
    }
    return difference == 0;
}

static int verify_password(const char *password, const char *stored) {
    if (!stored || strncmp(stored, "pbkdf2-md5$", 11) != 0) return 0;
    char copy[256];
    if (strlen(stored) >= sizeof(copy)) return 0;
    snprintf(copy, sizeof(copy), "%s", stored);
    char *algorithm = strtok(copy, "$");
    char *iterations_text = strtok(NULL, "$");
    char *salt = strtok(NULL, "$");
    char *expected = strtok(NULL, "$");
    if (!algorithm || !iterations_text || !salt || !expected ||
        strcmp(algorithm, "pbkdf2-md5") != 0 ||
        strlen(salt) != 32 || strlen(expected) != 32) return 0;
    int iterations = atoi(iterations_text);
    if (iterations < 1 || iterations > 100000) return 0;
    char current[33];
    mg_md5(current, password, salt, NULL);
    for (int i = 1; i < iterations; i++) {
        char next[33];
        mg_md5(next, current, salt, NULL);
        memcpy(current, next, sizeof(current));
    }
    return constant_time_equal(current, expected);
}

static void normalize_email(const char *email, char *out, size_t out_size) {
    size_t i = 0;
    for (; i < out_size - 1 && email[i] != '\0'; i++) {
        char c = email[i];
        if (c >= 'A' && c <= 'Z') c = (char)(c - 'A' + 'a');
        out[i] = c;
    }
    out[i] = '\0';
}

void handle_student_register(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    (void)auth;
    cJSON *root = cJSON_Parse(body);
    if (!root) { send_error(conn, 400, "Invalid JSON in request body"); return; }

    cJSON *name = cJSON_GetObjectItemCaseSensitive(root, "full_name");
    cJSON *email = cJSON_GetObjectItemCaseSensitive(root, "email");
    cJSON *join_code = cJSON_GetObjectItemCaseSensitive(root, "join_code");
    cJSON *phone = cJSON_GetObjectItemCaseSensitive(root, "phone");
    cJSON *password = cJSON_GetObjectItemCaseSensitive(root, "password");

    if (!cJSON_IsString(name) || !cJSON_IsString(email) || !cJSON_IsString(join_code) ||
        name->valuestring[0] == '\0' || strlen(name->valuestring) > 100 ||
        strlen(email->valuestring) > 150 || !strchr(email->valuestring, '@') ||
        email->valuestring[0] == '@' || email->valuestring[strlen(email->valuestring) - 1] == '@' ||
        strlen(join_code->valuestring) != 6 ||
        (phone && (!cJSON_IsString(phone) || strlen(phone->valuestring) > 30)) ||
        !cJSON_IsString(password) || strlen(password->valuestring) < 4 ||
        strlen(password->valuestring) > 128) {
        cJSON_Delete(root);
        send_error(conn, 400, "Provide valid full_name, email, password, and a 6-character join_code; phone is optional");
        return;
    }

    char normalized_join_code[7];
    for (int i = 0; i < 6; i++) {
        char c = join_code->valuestring[i];
        if ((c >= 'a' && c <= 'z')) c = (char)(c - 'a' + 'A');
        if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z'))) {
            cJSON_Delete(root);
            send_error(conn, 400, "join_code must contain exactly 6 letters or digits");
            return;
        }
        normalized_join_code[i] = c;
    }
    normalized_join_code[6] = '\0';

    char normalized_email[151];
    normalize_email(email->valuestring, normalized_email, sizeof(normalized_email));
    char safe_join_code[64], safe_email[320], safe_name[256];
    char safe_phone[64], safe_password_hash[512];
    db_escape(safe_join_code, sizeof(safe_join_code), normalized_join_code);
    db_escape(safe_email, sizeof(safe_email), normalized_email);
    db_escape(safe_name, sizeof(safe_name), name->valuestring);
    db_escape(safe_phone, sizeof(safe_phone), cJSON_IsString(phone) ? phone->valuestring : "");
    char password_hash[256];
    if (!create_password_hash(password->valuestring, password_hash, sizeof(password_hash))) {
        cJSON_Delete(root);
        send_error(conn, 500, "Could not securely generate a password hash");
        return;
    }
    db_escape(safe_password_hash, sizeof(safe_password_hash), password_hash);
    cJSON_Delete(root);

    MYSQL_RES *res = db_query("SELECT canteen_id FROM canteens WHERE join_code = '%s' LIMIT 1;",
                              safe_join_code);
    if (!res) { send_error(conn, 500, "Database error checking canteen join code"); return; }
    MYSQL_ROW row = mysql_fetch_row(res);
    if (!row) {
        mysql_free_result(res);
        send_error(conn, 400, "Invalid canteen join_code");
        return;
    }
    int canteen_id = row[0] ? atoi(row[0]) : 0;
    mysql_free_result(res);
    if (canteen_id <= 0) { send_error(conn, 500, "Invalid canteen configuration"); return; }

    res = db_query("SELECT student_id FROM students WHERE canteen_id = %d AND email = '%s' LIMIT 1;",
                   canteen_id, safe_email);
    if (!res) { send_error(conn, 500, "Database error checking student registration"); return; }
    int already_registered = mysql_fetch_row(res) != NULL;
    mysql_free_result(res);
    if (already_registered) {
        send_error(conn, 409, "This email is already registered for the selected canteen");
        return;
    }

    unsigned char random_bytes[32];
    if (!generate_secure_random(random_bytes, sizeof(random_bytes))) {
        send_error(conn, 500, "Could not securely generate a student token");
        return;
    }
    char token[65];
    bytes_to_hex(random_bytes, sizeof(random_bytes), token);

    int affected = db_execute(
        "INSERT INTO students (canteen_id, full_name, email, password_hash, phone, access_token, token_expires_at) "
        "VALUES (%d, '%s', '%s', '%s', NULLIF('%s', ''), '%s', DATE_ADD(NOW(), INTERVAL 30 DAY));",
        canteen_id, safe_name, safe_email, safe_password_hash, safe_phone, token);
    if (affected <= 0) { send_error(conn, 500, "Failed to register student"); return; }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "student_id", (double)db_last_insert_id());
    cJSON_AddNumberToObject(data, "canteen_id", canteen_id);
    cJSON_AddStringToObject(data, "token", token);
    cJSON_AddStringToObject(data, "token_type", "Bearer");
    send_success(conn, 201, "Student registered for canteen", data);
}

void handle_student_login(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    (void)auth;
    cJSON *root = cJSON_Parse(body);
    if (!root) { send_error(conn, 400, "Invalid JSON in request body"); return; }

    cJSON *email = cJSON_GetObjectItemCaseSensitive(root, "email");
    cJSON *password = cJSON_GetObjectItemCaseSensitive(root, "password");
    if (!cJSON_IsString(email) || email->valuestring[0] == '\0' ||
        !cJSON_IsString(password) || password->valuestring[0] == '\0') {
        cJSON_Delete(root);
        send_error(conn, 400, "Provide email and password");
        return;
    }

    char normalized_email[151];
    normalize_email(email->valuestring, normalized_email, sizeof(normalized_email));
    char safe_email[320];
    db_escape(safe_email, sizeof(safe_email), normalized_email);
    char login_password[129];
    snprintf(login_password, sizeof(login_password), "%s", password->valuestring);
    cJSON_Delete(root);

    MYSQL_RES *res = db_query(
        "SELECT student_id, canteen_id, full_name, password_hash "
        "FROM students WHERE email = '%s';",
        safe_email);
    if (!res) { send_error(conn, 500, "Database error verifying student login"); return; }

    MYSQL_ROW row;
    int matched_student_id = 0;
    int matched_canteen_id = 0;
    char matched_name[101] = "";
    while ((row = mysql_fetch_row(res)) != NULL) {
        if (row[3] && verify_password(login_password, row[3])) {
            matched_student_id = row[0] ? atoi(row[0]) : 0;
            matched_canteen_id = row[1] ? atoi(row[1]) : 0;
            snprintf(matched_name, sizeof(matched_name), "%s", row[2] ? row[2] : "");
            break;
        }
    }
    mysql_free_result(res);

    if (matched_student_id <= 0 || matched_canteen_id <= 0) {
        send_error(conn, 401, "Invalid email or password");
        return;
    }

    unsigned char random_bytes[32];
    if (!generate_secure_random(random_bytes, sizeof(random_bytes))) {
        send_error(conn, 500, "Could not securely generate a student token");
        return;
    }
    char token[65];
    bytes_to_hex(random_bytes, sizeof(random_bytes), token);
    int updated = db_execute(
        "UPDATE students SET access_token = '%s', token_expires_at = DATE_ADD(NOW(), INTERVAL 30 DAY) "
        "WHERE student_id = %d;",
        token, matched_student_id);
    if (updated != 1) {
        send_error(conn, 500, "Failed to create student session");
        return;
    }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "student_id", matched_student_id);
    cJSON_AddNumberToObject(data, "canteen_id", matched_canteen_id);
    cJSON_AddStringToObject(data, "full_name", matched_name);
    cJSON_AddStringToObject(data, "token", token);
    cJSON_AddStringToObject(data, "token_type", "Bearer");
    send_success(conn, 200, "Student login successful", data);
}

void handle_get_student_menu(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    (void)body;
    MYSQL_RES *res = db_query(
        "SELECT item_id, name, description, price, is_available, category "
        "FROM menu_items WHERE canteen_id = %d ORDER BY category, name;",
        auth->canteen_id);
    if (!res) { send_error(conn, 500, "Database error retrieving menu"); return; }

    cJSON *items = cJSON_CreateArray();
    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        cJSON *item = cJSON_CreateObject();
        cJSON_AddNumberToObject(item, "item_id", row[0] ? atoi(row[0]) : 0);
        cJSON_AddStringToObject(item, "name", row[1] ? row[1] : "");
        cJSON_AddStringToObject(item, "description", row[2] ? row[2] : "");
        cJSON_AddNumberToObject(item, "price", row[3] ? atof(row[3]) : 0.0);
        cJSON_AddBoolToObject(item, "is_available", row[4] && atoi(row[4]) == 1);
        cJSON_AddStringToObject(item, "category", row[5] ? row[5] : "General");
        cJSON_AddItemToArray(items, item);
    }
    mysql_free_result(res);

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "canteen_id", auth->canteen_id);
    cJSON_AddNumberToObject(data, "count", cJSON_GetArraySize(items));
    cJSON_AddItemToObject(data, "items", items);
    send_success(conn, 200, "Canteen menu retrieved", data);
}

void handle_post_student_order(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    cJSON *root = cJSON_Parse(body);
    if (!root) { send_error(conn, 400, "Invalid JSON in request body"); return; }

    cJSON *items = cJSON_GetObjectItemCaseSensitive(root, "items");
    cJSON *order_type_json = cJSON_GetObjectItemCaseSensitive(root, "order_type");
    cJSON *is_preorder_json = cJSON_GetObjectItemCaseSensitive(root, "is_preorder");
    cJSON *scheduled_time_json = cJSON_GetObjectItemCaseSensitive(root, "scheduled_time");
    const char *order_type = cJSON_IsString(order_type_json) ? order_type_json->valuestring : "dine_in";
    int is_preorder = cJSON_IsBool(is_preorder_json) ? cJSON_IsTrue(is_preorder_json) : 0;
    const char *scheduled_time = cJSON_IsString(scheduled_time_json) ? scheduled_time_json->valuestring : "";
    if (!cJSON_IsArray(items) || cJSON_GetArraySize(items) < 1 ||
        cJSON_GetArraySize(items) > MAX_ORDER_ITEMS ||
        (strcmp(order_type, "dine_in") != 0 && strcmp(order_type, "parcel") != 0)) {
        cJSON_Delete(root);
        send_error(conn, 400, "Provide 1 to 50 items and order_type 'dine_in' or 'parcel'");
        return;
    }
    if (cJSON_IsBool(is_preorder_json) && cJSON_IsTrue(is_preorder_json) &&
        (scheduled_time[0] == '\0' || strlen(scheduled_time) > 50)) {
        cJSON_Delete(root);
        send_error(conn, 400, "A pre-order requires scheduled_time with at most 50 characters");
        return;
    }
    if (!is_preorder) scheduled_time = "";
    if (strlen(scheduled_time) > 50) {
        cJSON_Delete(root);
        send_error(conn, 400, "scheduled_time must contain at most 50 characters");
        return;
    }
    char safe_scheduled_time[102];
    db_escape(safe_scheduled_time, sizeof(safe_scheduled_time), scheduled_time);

    StudentOrderItem order_items[MAX_ORDER_ITEMS];
    int item_count = cJSON_GetArraySize(items);
    long long total_cents = 0;
    for (int i = 0; i < item_count; i++) {
        cJSON *entry = cJSON_GetArrayItem(items, i);
        cJSON *item_id = cJSON_GetObjectItemCaseSensitive(entry, "item_id");
        cJSON *quantity = cJSON_GetObjectItemCaseSensitive(entry, "quantity");
        if (!cJSON_IsNumber(item_id) || !cJSON_IsNumber(quantity) ||
            item_id->valuedouble <= 0 || item_id->valuedouble != item_id->valueint ||
            quantity->valuedouble < 1 || quantity->valuedouble > 100 ||
            quantity->valuedouble != quantity->valueint) {
            cJSON_Delete(root);
            send_error(conn, 400, "Each item needs a positive integer item_id and quantity from 1 to 100");
            return;
        }
        order_items[i].item_id = item_id->valueint;
        order_items[i].quantity = quantity->valueint;
        for (int previous = 0; previous < i; previous++) {
            if (order_items[previous].item_id == order_items[i].item_id) {
                cJSON_Delete(root);
                send_error(conn, 400, "Do not repeat item_id values; use quantity instead");
                return;
            }
        }

        MYSQL_RES *res = db_query(
            "SELECT price FROM menu_items WHERE item_id = %d AND canteen_id = %d "
            "AND is_available = 1 LIMIT 1;",
            order_items[i].item_id, auth->canteen_id);
        if (!res) {
            cJSON_Delete(root);
            send_error(conn, 500, "Database error validating menu item");
            return;
        }
        MYSQL_ROW row = mysql_fetch_row(res);
        if (!row) {
            mysql_free_result(res);
            cJSON_Delete(root);
            send_error(conn, 400, "An item does not exist in this canteen or is out of stock");
            return;
        }
        double price = row[0] ? atof(row[0]) : 0.0;
        mysql_free_result(res);
        if (price <= 0.0 || price > 99999999.99) {
            cJSON_Delete(root);
            send_error(conn, 400, "A menu item has an invalid price");
            return;
        }
        order_items[i].unit_price_cents = (long long)(price * 100.0 + 0.5);
        total_cents += order_items[i].unit_price_cents * order_items[i].quantity;
        if (total_cents > 9999999999LL) {
            cJSON_Delete(root);
            send_error(conn, 400, "Order total exceeds the supported amount");
            return;
        }
    }
    cJSON_Delete(root);

    unsigned char random_bytes[3];
    if (!generate_secure_random(random_bytes, sizeof(random_bytes))) {
        send_error(conn, 500, "Could not securely generate a pickup OTP");
        return;
    }
    unsigned int otp_value = (((unsigned int)random_bytes[0] << 16) |
                              ((unsigned int)random_bytes[1] << 8) |
                              (unsigned int)random_bytes[2]) % 1000000;
    char otp[7];
    snprintf(otp, sizeof(otp), "%06u", otp_value);

    if (db_execute("START TRANSACTION;") < 0) {
        send_error(conn, 500, "Could not start order transaction");
        return;
    }
    int affected = db_execute(
        "INSERT INTO orders (canteen_id, student_id, order_type, is_preorder, scheduled_time, status, otp_code, total_amount) "
        "VALUES (%d, %d, '%s', %d, NULLIF('%s', ''), 'pending', '%s', %.2f);",
        auth->canteen_id, auth->student_id, order_type, is_preorder,
        safe_scheduled_time, otp, (double)total_cents / 100.0);
    unsigned long long order_id = affected > 0 ? db_last_insert_id() : 0;
    if (!order_id) {
        db_execute("ROLLBACK;");
        send_error(conn, 500, "Failed to create order");
        return;
    }

    for (int i = 0; i < item_count; i++) {
        long long subtotal = order_items[i].unit_price_cents * order_items[i].quantity;
        affected = db_execute(
            "INSERT INTO order_items (order_id, item_id, quantity, unit_price, subtotal) "
            "VALUES (%llu, %d, %d, %.2f, %.2f);",
            order_id, order_items[i].item_id, order_items[i].quantity,
            (double)order_items[i].unit_price_cents / 100.0,
            (double)subtotal / 100.0);
        if (affected <= 0) {
            db_execute("ROLLBACK;");
            send_error(conn, 500, "Failed to save order items");
            return;
        }
    }
    if (db_execute("COMMIT;") < 0) {
        db_execute("ROLLBACK;");
        send_error(conn, 500, "Failed to save order");
        return;
    }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "order_id", (double)order_id);
    cJSON_AddStringToObject(data, "status", "pending");
    cJSON_AddStringToObject(data, "order_type", order_type);
    cJSON_AddNumberToObject(data, "total_amount", (double)total_cents / 100.0);
    cJSON_AddBoolToObject(data, "is_preorder", is_preorder == 1);
    cJSON_AddStringToObject(data, "scheduled_time", is_preorder ? scheduled_time : "");
    cJSON_AddStringToObject(data, "message",
                            is_preorder ? "Pre-order placed successfully"
                                        : "The pickup OTP will appear when the order is ready");
    send_success(conn, 201, "Order placed", data);
}

void handle_get_student_orders(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    (void)body;
    MYSQL_RES *res = db_query(
        "SELECT order_id, order_type, status, total_amount, created_at, otp_code, "
        "       is_preorder, scheduled_time "
        "FROM orders WHERE student_id = %d AND canteen_id = %d "
        "ORDER BY created_at DESC;",
        auth->student_id, auth->canteen_id);
    if (!res) { send_error(conn, 500, "Database error retrieving student orders"); return; }

    cJSON *orders = cJSON_CreateArray();
    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        cJSON *order = cJSON_CreateObject();
        cJSON_AddNumberToObject(order, "order_id", row[0] ? atoi(row[0]) : 0);
        cJSON_AddStringToObject(order, "order_type", row[1] ? row[1] : "dine_in");
        cJSON_AddStringToObject(order, "status", row[2] ? row[2] : "pending");
        cJSON_AddNumberToObject(order, "total_amount", row[3] ? atof(row[3]) : 0.0);
        cJSON_AddStringToObject(order, "created_at", row[4] ? row[4] : "");
        cJSON_AddBoolToObject(order, "is_preorder", row[6] ? atoi(row[6]) == 1 : 0);
        cJSON_AddStringToObject(order, "scheduled_time", row[7] ? row[7] : "");
        if (row[2] && strcmp(row[2], "ready") == 0) {
            cJSON_AddStringToObject(order, "pickup_otp", row[5] ? row[5] : "");
        }
        cJSON_AddItemToArray(orders, order);
    }
    mysql_free_result(res);

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "count", cJSON_GetArraySize(orders));
    cJSON_AddItemToObject(data, "orders", orders);
    send_success(conn, 200, "Student orders retrieved", data);
}
