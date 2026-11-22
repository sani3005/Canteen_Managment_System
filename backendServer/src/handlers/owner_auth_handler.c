#include "handlers.h"
#include "db.h"
#include "utils.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define PASSWORD_ITERATIONS 10000

static void bytes_to_hex(const unsigned char *bytes, size_t count, char *out) {
    static const char digits[] = "0123456789abcdef";
    for (size_t i = 0; i < count; i++) {
        out[i * 2] = digits[bytes[i] >> 4];
        out[i * 2 + 1] = digits[bytes[i] & 0x0f];
    }
    out[count * 2] = '\0';
}

static int hex_char_value(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static int constant_time_equal(const char *a, const char *b) {
    size_t a_len = strlen(a);
    size_t b_len = strlen(b);
    size_t len = a_len > b_len ? a_len : b_len;
    unsigned char difference = (unsigned char)(a_len != b_len);
    for (size_t i = 0; i < len; i++) {
        unsigned char ac = i < a_len ? (unsigned char)a[i] : 0;
        unsigned char bc = i < b_len ? (unsigned char)b[i] : 0;
        difference |= ac ^ bc;
    }
    return difference == 0;
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

    char salt[33];
    char digest[33];
    bytes_to_hex(salt_bytes, sizeof(salt_bytes), salt);
    derive_password_hash(password, salt, digest);
    snprintf(out, out_size, "pbkdf2-md5$%d$%s$%s",
             PASSWORD_ITERATIONS, salt, digest);
    return 1;
}

static int verify_password(const char *password, const char *stored) {
    if (!stored || stored[0] == '\0') return 0;
    if (strncmp(stored, "pbkdf2-md5$", 11) != 0) {
        return constant_time_equal(password, stored);
    }

    char copy[256];
    if (strlen(stored) >= sizeof(copy)) return 0;
    snprintf(copy, sizeof(copy), "%s", stored);

    char *algorithm = strtok(copy, "$");
    char *iterations_text = strtok(NULL, "$");
    char *salt = strtok(NULL, "$");
    char *expected = strtok(NULL, "$");
    if (!algorithm || !iterations_text || !salt || !expected ||
        strcmp(algorithm, "pbkdf2-md5") != 0 ||
        strlen(salt) != 32 || strlen(expected) != 32) {
        return 0;
    }
    int iterations = atoi(iterations_text);
    if (iterations < 1 || iterations > 100000) return 0;

    char current[33];
    mg_md5(current, password, salt, NULL);
    for (int i = 1; i < iterations; i++) {
        char next[33];
        mg_md5(next, current, salt, NULL);
        memcpy(current, next, sizeof(current));
    }
    for (size_t i = 0; i < strlen(expected); i++) {
        if (hex_char_value(expected[i]) < 0) return 0;
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

static int generate_owner_token(char token[65]) {
    unsigned char bytes[32];
    if (!generate_secure_random(bytes, sizeof(bytes))) return 0;
    bytes_to_hex(bytes, sizeof(bytes), token);
    return 1;
}

void handle_owner_register(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    (void)auth;
    cJSON *root = cJSON_Parse(body);
    if (!root) { send_error(conn, 400, "Invalid JSON in request body"); return; }

    cJSON *full_name = cJSON_GetObjectItemCaseSensitive(root, "full_name");
    cJSON *email = cJSON_GetObjectItemCaseSensitive(root, "email");
    cJSON *password = cJSON_GetObjectItemCaseSensitive(root, "password");
    cJSON *canteen_name = cJSON_GetObjectItemCaseSensitive(root, "canteen_name");
    cJSON *location = cJSON_GetObjectItemCaseSensitive(root, "location");

    if (!cJSON_IsString(full_name) || full_name->valuestring[0] == '\0' ||
        strlen(full_name->valuestring) > 100 ||
        !cJSON_IsString(email) || email->valuestring[0] == '\0' ||
        strlen(email->valuestring) > 150 || !strchr(email->valuestring, '@') ||
        email->valuestring[0] == '@' ||
        email->valuestring[strlen(email->valuestring) - 1] == '@' ||
        !cJSON_IsString(password) || strlen(password->valuestring) < 8 ||
        strlen(password->valuestring) > 128 ||
        !cJSON_IsString(canteen_name) || canteen_name->valuestring[0] == '\0' ||
        strlen(canteen_name->valuestring) > 100 ||
        !cJSON_IsString(location) || location->valuestring[0] == '\0' ||
        strlen(location->valuestring) > 255) {
        cJSON_Delete(root);
        send_error(conn, 400, "Provide full_name, email, password (8-128 characters), canteen_name, and location");
        return;
    }

    char normalized_email[151];
    normalize_email(email->valuestring, normalized_email, sizeof(normalized_email));
    char safe_name[202], safe_email[302], safe_canteen_name[202], safe_location[512];
    db_escape(safe_name, sizeof(safe_name), full_name->valuestring);
    db_escape(safe_email, sizeof(safe_email), normalized_email);
    db_escape(safe_canteen_name, sizeof(safe_canteen_name), canteen_name->valuestring);
    db_escape(safe_location, sizeof(safe_location), location->valuestring);

    char password_hash[256];
    if (!create_password_hash(password->valuestring, password_hash, sizeof(password_hash))) {
        cJSON_Delete(root);
        send_error(conn, 500, "Could not securely generate a password hash");
        return;
    }
    char safe_password_hash[512];
    db_escape(safe_password_hash, sizeof(safe_password_hash), password_hash);
    cJSON_Delete(root);

    MYSQL_RES *existing = db_query(
        "SELECT user_id FROM users WHERE username = '%s' LIMIT 1;",
        safe_email);
    if (!existing) { send_error(conn, 500, "Database error checking owner account"); return; }
    int already_exists = mysql_fetch_row(existing) != NULL;
    mysql_free_result(existing);
    if (already_exists) {
        send_error(conn, 409, "An owner account already exists for this email");
        return;
    }

    char token[65];
    if (!generate_owner_token(token)) {
        send_error(conn, 500, "Could not securely generate an owner token");
        return;
    }

    char code[32];
    unsigned long long canteen_id = 0;
    char join_code[32];
    if (db_execute("START TRANSACTION;") < 0) {
        send_error(conn, 500, "Could not start registration transaction");
        return;
    }

    for (int attempt = 0; attempt < 20 && !canteen_id; attempt++) {
        generate_join_code(code, sizeof(code));
        MYSQL_RES *res = db_query(
            "SELECT canteen_id FROM canteens WHERE join_code = '%s' LIMIT 1;",
            code);
        if (!res) { db_execute("ROLLBACK;"); send_error(conn, 500, "Failed to check join-code uniqueness"); return; }
        MYSQL_ROW row = mysql_fetch_row(res);
        int in_use = row != NULL;
        mysql_free_result(res);
        if (in_use) continue;

        char safe_code[64];
        db_escape(safe_code, sizeof(safe_code), code);
        int affected = db_execute(
            "INSERT INTO canteens (name, owner_name, location, photo_url, join_code) "
            "VALUES ('%s', '%s', '%s', NULL, '%s');",
            safe_canteen_name, safe_name, safe_location, safe_code);
        if (affected <= 0) {
            db_execute("ROLLBACK;");
            send_error(conn, 500, "Failed to create canteen");
            return;
        }
        canteen_id = db_last_insert_id();
        snprintf(join_code, sizeof(join_code), "%s", code);
    }
    if (!canteen_id) {
        db_execute("ROLLBACK;");
        send_error(conn, 500, "Could not generate a unique join code");
        return;
    }

    int affected = db_execute(
        "INSERT INTO users (username, password_hash, role, canteen_id, token, token_expires_at) "
        "VALUES ('%s', '%s', 'owner', %llu, '%s', DATE_ADD(NOW(), INTERVAL 30 DAY));",
        safe_email, safe_password_hash, canteen_id, token);
    if (affected <= 0) {
        db_execute("ROLLBACK;");
        send_error(conn, 500, "Failed to create owner account");
        return;
    }
    if (db_execute("COMMIT;") < 0) {
        db_execute("ROLLBACK;");
        send_error(conn, 500, "Failed to complete owner registration");
        return;
    }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "canteen_id", (double)canteen_id);
    cJSON_AddStringToObject(data, "join_code", join_code);
    cJSON_AddStringToObject(data, "token", token);
    cJSON_AddStringToObject(data, "token_type", "Bearer");
    send_success(conn, 201, "Canteen and owner account created successfully", data);
}

void handle_owner_login(struct mg_connection *conn, const AuthContext *auth, const char *body) {
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
    char login_password[129];
    snprintf(login_password, sizeof(login_password), "%s", password->valuestring);
    char safe_email[302];
    db_escape(safe_email, sizeof(safe_email), normalized_email);
    cJSON_Delete(root);

    MYSQL_RES *res = db_query(
        "SELECT u.user_id, u.password_hash, u.canteen_id, c.name, c.owner_name, c.location, c.join_code "
        "FROM users u JOIN canteens c ON c.canteen_id = u.canteen_id "
        "WHERE u.username = '%s' AND u.role = 'owner' LIMIT 1;",
        safe_email);
    if (!res) { send_error(conn, 500, "Database error verifying owner login"); return; }
    MYSQL_ROW row = mysql_fetch_row(res);
    if (!row) {
        mysql_free_result(res);
        send_error(conn, 401, "Invalid email or password");
        return;
    }

    int user_id = row[0] ? atoi(row[0]) : 0;
    char password_hash_copy[256];
    snprintf(password_hash_copy, sizeof(password_hash_copy), "%s",
             row[1] ? row[1] : "");
    int canteen_id = row[2] ? atoi(row[2]) : 0;
    char canteen_name[101];
    char owner_name[101];
    char location[256];
    char join_code[7];
    snprintf(canteen_name, sizeof(canteen_name), "%s", row[3] ? row[3] : "");
    snprintf(owner_name, sizeof(owner_name), "%s", row[4] ? row[4] : "");
    snprintf(location, sizeof(location), "%s", row[5] ? row[5] : "");
    snprintf(join_code, sizeof(join_code), "%s", row[6] ? row[6] : "");
    mysql_free_result(res);

    if (!verify_password(login_password, password_hash_copy)) {
        send_error(conn, 401, "Invalid email or password");
        return;
    }
    if (user_id <= 0 || canteen_id <= 0) {
        send_error(conn, 403, "Owner account is not assigned to a canteen");
        return;
    }

    char token[65];
    if (!generate_owner_token(token)) {
        send_error(conn, 500, "Could not securely generate an owner token");
        return;
    }
    int updated = db_execute(
        "UPDATE users SET token = '%s', token_expires_at = DATE_ADD(NOW(), INTERVAL 30 DAY) "
        "WHERE user_id = %d AND role = 'owner';",
        token, user_id);
    if (updated != 1) {
        send_error(conn, 500, "Failed to create owner session");
        return;
    }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "canteen_id", canteen_id);
    cJSON_AddStringToObject(data, "name", canteen_name);
    cJSON_AddStringToObject(data, "owner_name", owner_name);
    cJSON_AddStringToObject(data, "location", location);
    cJSON_AddStringToObject(data, "join_code", join_code);
    cJSON_AddStringToObject(data, "token", token);
    cJSON_AddStringToObject(data, "token_type", "Bearer");
    send_success(conn, 200, "Login successful", data);
}
