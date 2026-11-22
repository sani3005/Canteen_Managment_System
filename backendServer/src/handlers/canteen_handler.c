/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* canteen_handler.c - Module 1: canteen details and join code (plus health check) */
#include "handlers.h"
#include "db.h"
#include "utils.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* GET /api/health  (public) */
void handle_health(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    send_json(conn, 200, "{\"status\":\"ok\",\"service\":\"canteen_owner_backend\",\"language\":\"C\",\"server\":\"CivetWeb\"}");
}

/* GET /api/owner/canteen */
void handle_get_canteen(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    MYSQL_RES *res = db_query(
        "SELECT canteen_id, name, owner_name, location, photo_url, join_code, created_at "
        "FROM canteens WHERE canteen_id = %d LIMIT 1;",
        auth->canteen_id);
    if (!res) { send_error(conn, 500, "Database query error"); return; }

    MYSQL_ROW row = mysql_fetch_row(res);
    if (!row) {
        mysql_free_result(res);
        send_error(conn, 404, "Canteen not found for this owner");
        return;
    }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "canteen_id", atoi(row[0]));
    cJSON_AddStringToObject(data, "name",       row[1] ? row[1] : "");
    cJSON_AddStringToObject(data, "owner_name", row[2] ? row[2] : "");
    cJSON_AddStringToObject(data, "location",   row[3] ? row[3] : "");
    cJSON_AddStringToObject(data, "photo_url",  row[4] ? row[4] : "");
    cJSON_AddStringToObject(data, "join_code",  row[5] ? row[5] : "");
    cJSON_AddStringToObject(data, "created_at", row[6] ? row[6] : "");
    mysql_free_result(res);

    send_success(conn, 200, "Canteen retrieved successfully", data);
}

/* POST /api/owner/canteen */
void handle_post_canteen(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    if (auth->canteen_id > 0) {
        send_error(conn, 409, "This owner already has an assigned canteen");
        return;
    }

    cJSON *root = cJSON_Parse(body);
    if (!root) { send_error(conn, 400, "Invalid JSON in request body"); return; }

    cJSON *name = cJSON_GetObjectItemCaseSensitive(root, "name");
    cJSON *owner_name = cJSON_GetObjectItemCaseSensitive(root, "owner_name");
    cJSON *location = cJSON_GetObjectItemCaseSensitive(root, "location");
    cJSON *photo_url = cJSON_GetObjectItemCaseSensitive(root, "photo_url");

    if (!cJSON_IsString(name) || name->valuestring[0] == '\0' || strlen(name->valuestring) > 100 ||
        !cJSON_IsString(owner_name) || owner_name->valuestring[0] == '\0' || strlen(owner_name->valuestring) > 100 ||
        !cJSON_IsString(location) || location->valuestring[0] == '\0' || strlen(location->valuestring) > 255 ||
        (photo_url && !cJSON_IsString(photo_url)) ||
        (cJSON_IsString(photo_url) && strlen(photo_url->valuestring) > 2048)) {
        cJSON_Delete(root);
        send_error(conn, 400, "Provide name, owner_name, and location; photo_url is optional");
        return;
    }

    char safe_name[202], safe_owner_name[202], safe_location[512], safe_photo_url[4100];
    db_escape(safe_name, sizeof(safe_name), name->valuestring);
    db_escape(safe_owner_name, sizeof(safe_owner_name), owner_name->valuestring);
    db_escape(safe_location, sizeof(safe_location), location->valuestring);
    db_escape(safe_photo_url, sizeof(safe_photo_url),
              cJSON_IsString(photo_url) ? photo_url->valuestring : "");
    cJSON_Delete(root);

    char code[32];
    unsigned long long canteen_id = 0;
    for (int attempt = 0; attempt < 20 && !canteen_id; attempt++) {
        generate_join_code(code, sizeof(code));
        MYSQL_RES *res = db_query(
            "SELECT canteen_id FROM canteens WHERE join_code = '%s' LIMIT 1;",
            code);
        if (!res) { send_error(conn, 500, "Failed to check join-code uniqueness"); return; }
        MYSQL_ROW row = mysql_fetch_row(res);
        int in_use = row != NULL;
        mysql_free_result(res);
        if (in_use) continue;

        int affected = db_execute(
            "INSERT INTO canteens (name, owner_name, location, photo_url, join_code) "
            "VALUES ('%s', '%s', '%s', NULLIF('%s', ''), '%s');",
            safe_name, safe_owner_name, safe_location, safe_photo_url, code);
        if (affected < 0) { send_error(conn, 500, "Failed to create canteen"); return; }
        canteen_id = db_last_insert_id();
    }
    if (!canteen_id) { send_error(conn, 500, "Could not generate a unique join code"); return; }

    int assigned = db_execute("UPDATE users SET canteen_id = %llu WHERE user_id = %d AND role = 'owner';",
                              canteen_id, auth->user_id);
    if (assigned != 1) {
        send_error(conn, 500, "Canteen created but could not be assigned to owner");
        return;
    }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "canteen_id", (double)canteen_id);
    cJSON_AddStringToObject(data, "join_code", code);
    send_success(conn, 201, "Canteen created successfully", data);
}

/* PUT /api/owner/canteen */
void handle_put_canteen(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    cJSON *root = cJSON_Parse(body);
    if (!root) { send_error(conn, 400, "Invalid JSON in request body"); return; }

    cJSON *name = cJSON_GetObjectItemCaseSensitive(root, "name");
    cJSON *owner_name = cJSON_GetObjectItemCaseSensitive(root, "owner_name");
    cJSON *location = cJSON_GetObjectItemCaseSensitive(root, "location");
    cJSON *photo_url = cJSON_GetObjectItemCaseSensitive(root, "photo_url");

    if (!cJSON_IsString(name) || name->valuestring[0] == '\0' || strlen(name->valuestring) > 100 ||
        !cJSON_IsString(owner_name) || owner_name->valuestring[0] == '\0' || strlen(owner_name->valuestring) > 100 ||
        !cJSON_IsString(location) || location->valuestring[0] == '\0' || strlen(location->valuestring) > 255 ||
        (photo_url && !cJSON_IsString(photo_url))) {
        cJSON_Delete(root);
        send_error(conn, 400, "Provide name, owner_name, and location; photo_url is optional");
        return;
    }
    if (cJSON_IsString(photo_url) && strlen(photo_url->valuestring) > 2048) {
        cJSON_Delete(root);
        send_error(conn, 400, "photo_url cannot exceed 2048 characters");
        return;
    }

    char safe_name[202], safe_owner_name[202], safe_location[512], safe_photo_url[4100];
    db_escape(safe_name, sizeof(safe_name), name->valuestring);
    db_escape(safe_owner_name, sizeof(safe_owner_name), owner_name->valuestring);
    db_escape(safe_location, sizeof(safe_location), location->valuestring);
    db_escape(safe_photo_url, sizeof(safe_photo_url),
              cJSON_IsString(photo_url) ? photo_url->valuestring : "");
    cJSON_Delete(root);

    int affected = db_execute(
        "UPDATE canteens SET name = '%s', owner_name = '%s', location = '%s', "
        "photo_url = NULLIF('%s', '') WHERE canteen_id = %d;",
        safe_name, safe_owner_name, safe_location, safe_photo_url, auth->canteen_id);
    if (affected < 0) { send_error(conn, 500, "Failed to update canteen details"); return; }

    handle_get_canteen(conn, auth, NULL);
}

/* POST /api/owner/canteen/join-code  -> makes a new random code */
void handle_post_join_code(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    char code[32];
    int updated = 0;

    for (int attempt = 0; attempt < 20 && !updated; attempt++) {
        generate_join_code(code, sizeof(code));
        MYSQL_RES *res = db_query(
            "SELECT canteen_id FROM canteens WHERE join_code = '%s' LIMIT 1;",
            code);
        if (!res) { send_error(conn, 500, "Failed to check join-code uniqueness"); return; }
        MYSQL_ROW row = mysql_fetch_row(res);
        int in_use = row != NULL;
        mysql_free_result(res);
        if (in_use) continue;

        int affected = db_execute(
            "UPDATE canteens SET join_code = '%s' WHERE canteen_id = %d;",
            code, auth->canteen_id);
        if (affected < 0) { send_error(conn, 500, "Failed to update join code"); return; }
        updated = 1;
    }
    if (!updated) { send_error(conn, 500, "Could not generate a unique join code"); return; }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddStringToObject(data, "join_code", code);
    cJSON_AddNumberToObject(data, "canteen_id", auth->canteen_id);
    send_success(conn, 200, "Join code regenerated successfully", data);
}
