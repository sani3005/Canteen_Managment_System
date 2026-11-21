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
        "SELECT canteen_id, name, join_code, created_at FROM canteens WHERE canteen_id = %d LIMIT 1;",
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
    cJSON_AddStringToObject(data, "join_code",  row[2] ? row[2] : "");
    cJSON_AddStringToObject(data, "created_at", row[3] ? row[3] : "");
    mysql_free_result(res);

    send_success(conn, 200, "Canteen retrieved successfully", data);
}

/* POST /api/owner/canteen/join-code  -> makes a new random code */
void handle_post_join_code(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    char code[32];
    int affected = -1;

    /* join_code must be unique, so try a few times if a code is already taken */
    for (int attempt = 0; attempt < 5 && affected < 0; attempt++) {
        generate_join_code(code, sizeof(code));
        affected = db_execute("UPDATE canteens SET join_code = '%s' WHERE canteen_id = %d;",
                              code, auth->canteen_id);
    }
    if (affected < 0) { send_error(conn, 500, "Failed to update join code"); return; }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddStringToObject(data, "join_code", code);
    cJSON_AddNumberToObject(data, "canteen_id", auth->canteen_id);
    send_success(conn, 200, "Join code regenerated successfully", data);
}
