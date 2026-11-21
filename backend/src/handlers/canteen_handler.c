#include "handlers.h"
#include "db.h"
#include "utils.h"
#include <stdio.h>
#include <string.h>

void handle_get_canteen(HttpRequest *req, const AuthContext *ctx) {
    MYSQL_RES *res = db_query(
        "SELECT canteen_id, name, join_code, created_at FROM canteens WHERE canteen_id = %d LIMIT 1;",
        ctx->canteen_id
    );
    
    if (!res) {
        send_error(req->client_sock, 500, "Database query error");
        return;
    }
    
    MYSQL_ROW row = mysql_fetch_row(res);
    if (!row) {
        mysql_free_result(res);
        send_error(req->client_sock, 404, "Canteen not found for this owner");
        return;
    }
    
    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "canteen_id", row[0] ? atoi(row[0]) : 0);
    cJSON_AddStringToObject(data, "name", row[1] ? row[1] : "");
    cJSON_AddStringToObject(data, "join_code", row[2] ? row[2] : "");
    cJSON_AddStringToObject(data, "created_at", row[3] ? row[3] : "");
    
    mysql_free_result(res);
    send_success_json(req->client_sock, 200, "Canteen retrieved successfully", data);
}

void handle_post_join_code(HttpRequest *req, const AuthContext *ctx) {
    char new_code[32];
    generate_join_code(new_code, sizeof(new_code));
    
    char escaped_code[64];
    db_escape_string(escaped_code, new_code, (unsigned long)strlen(new_code));
    
    int affected = db_execute(
        "UPDATE canteens SET join_code = '%s' WHERE canteen_id = %d;",
        escaped_code, ctx->canteen_id
    );
    
    if (affected < 0) {
        send_error(req->client_sock, 500, "Failed to update canteen join code in database");
        return;
    }
    
    cJSON *data = cJSON_CreateObject();
    cJSON_AddStringToObject(data, "join_code", new_code);
    cJSON_AddNumberToObject(data, "canteen_id", ctx->canteen_id);
    
    send_success_json(req->client_sock, 200, "Join code regenerated successfully", data);
}
