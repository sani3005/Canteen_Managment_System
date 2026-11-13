#include "handlers.h"
#include "db.h"
#include "utils.h"
#include "cJSON.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void handle_patch_order_status(HttpRequest *req, const AuthContext *ctx) {
    cJSON *root = cJSON_Parse(req->body);
    if (!root) {
        send_error(req->client_sock, 400, "Invalid JSON in request body");
        return;
    }
    
    cJSON *id_obj = cJSON_GetObjectItemCaseSensitive(root, "order_id");
    cJSON *status_obj = cJSON_GetObjectItemCaseSensitive(root, "status");
    if (!id_obj || !cJSON_IsNumber(id_obj) || !status_obj || !cJSON_IsString(status_obj)) {
        cJSON_Delete(root);
        send_error(req->client_sock, 400, "Fields 'order_id' (number) and 'status' (string) are required");
        return;
    }
    
    int order_id = id_obj->valueint;
    const char *new_status = status_obj->valuestring;
    
    /* Guard: Completion STRICTLY requires student OTP verification */
    if (strcmp(new_status, "completed") == 0) {
        cJSON_Delete(root);
        send_error(req->client_sock, 400, "Order completion requires student OTP verification via /api/owner/orders/verify-otp");
        return;
    }
    
    /* Only allow pending -> preparing -> ready */
    if (strcmp(new_status, "pending") != 0 &&
        strcmp(new_status, "preparing") != 0 &&
        strcmp(new_status, "ready") != 0 &&
        strcmp(new_status, "cancelled") != 0) {
        cJSON_Delete(root);
        send_error(req->client_sock, 400, "Invalid status. Allowed values: pending, preparing, ready, cancelled");
        return;
    }
    
    char esc_status[32];
    db_escape_string(esc_status, new_status, (unsigned long)strlen(new_status));
    
    int affected = db_execute(
        "UPDATE orders SET status = '%s' WHERE order_id = %d AND canteen_id = %d AND status != 'completed';",
        esc_status, order_id, ctx->canteen_id
    );
    
    cJSON_Delete(root);
    
    if (affected < 0) {
        send_error(req->client_sock, 500, "Failed to update order status in database");
        return;
    }
    
    if (affected == 0) {
        send_error(req->client_sock, 404, "Order not found, already completed, or unauthorized");
        return;
    }
    
    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "order_id", order_id);
    cJSON_AddStringToObject(data, "new_status", new_status);
    
    send_success_json(req->client_sock, 200, "Order status updated", data);
}

void handle_post_verify_otp(HttpRequest *req, const AuthContext *ctx) {
    cJSON *root = cJSON_Parse(req->body);
    if (!root) {
        send_error(req->client_sock, 400, "Invalid JSON in request body");
        return;
    }
    
    cJSON *id_obj = cJSON_GetObjectItemCaseSensitive(root, "order_id");
    cJSON *otp_obj = cJSON_GetObjectItemCaseSensitive(root, "otp");
    if (!id_obj || !cJSON_IsNumber(id_obj) || !otp_obj || (!cJSON_IsString(otp_obj) && !cJSON_IsNumber(otp_obj))) {
        cJSON_Delete(root);
        send_error(req->client_sock, 400, "Fields 'order_id' and 'otp' (6-digit string or number) are required");
        return;
    }
    
    int order_id = id_obj->valueint;
    char input_otp[16];
    if (cJSON_IsString(otp_obj)) {
        snprintf(input_otp, sizeof(input_otp), "%s", otp_obj->valuestring);
    } else {
        snprintf(input_otp, sizeof(input_otp), "%06d", otp_obj->valueint);
    }
    cJSON_Delete(root);
    
    char *trimmed_otp = input_otp;
    while (*trimmed_otp == ' ') trimmed_otp++;
    
    /* Query the stored OTP and current order status */
    MYSQL_RES *res = db_query(
        "SELECT status, otp_code, total_amount FROM orders "
        "WHERE order_id = %d AND canteen_id = %d LIMIT 1;",
        order_id, ctx->canteen_id
    );
    
    if (!res) {
        send_error(req->client_sock, 500, "Database query error for OTP verification");
        return;
    }
    
    MYSQL_ROW row = mysql_fetch_row(res);
    if (!row) {
        mysql_free_result(res);
        send_error(req->client_sock, 404, "Order ticket not found for this canteen");
        return;
    }
    
    const char *current_status = row[0] ? row[0] : "";
    const char *stored_otp = row[1] ? row[1] : "";
    double total_amount = row[2] ? atof(row[2]) : 0.0;
    
    if (strcmp(current_status, "completed") == 0) {
        mysql_free_result(res);
        send_error(req->client_sock, 409, "Order is already completed");
        return;
    }
    
    if (strcmp(current_status, "cancelled") == 0) {
        mysql_free_result(res);
        send_error(req->client_sock, 400, "Order was cancelled and cannot be fulfilled");
        return;
    }
    
    /* Verify OTP match */
    if (strcmp(stored_otp, trimmed_otp) != 0) {
        mysql_free_result(res);
        send_error(req->client_sock, 400, "Invalid OTP code. Handshake failed.");
        return;
    }
    mysql_free_result(res);
    
    /* OTP match is confirmed: Mark status = 'completed' and record timestamp */
    int affected = db_execute(
        "UPDATE orders SET status = 'completed', completed_at = NOW() "
        "WHERE order_id = %d AND canteen_id = %d;",
        order_id, ctx->canteen_id
    );
    
    if (affected <= 0) {
        send_error(req->client_sock, 500, "Failed to mark order as completed");
        return;
    }
    
    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "order_id", order_id);
    cJSON_AddStringToObject(data, "status", "completed");
    cJSON_AddNumberToObject(data, "total_amount", total_amount);
    
    send_success_json(req->client_sock, 200, "OTP verified successfully. Order marked as completed.", data);
}
