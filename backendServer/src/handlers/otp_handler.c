/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* otp_handler.c - Module 4: order status changes and OTP handshake */
#include "handlers.h"
#include "db.h"
#include "utils.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* PATCH /api/owner/orders/status   body: {"order_id":1,"status":"preparing"}
 * Allowed: pending, preparing, ready, cancelled.
 * "completed" is NOT allowed here - it needs the student's OTP (see below). */
void handle_patch_order_status(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    cJSON *root = cJSON_Parse(body);
    if (!root) { send_error(conn, 400, "Invalid JSON in request body"); return; }

    cJSON *id     = cJSON_GetObjectItemCaseSensitive(root, "order_id");
    cJSON *status = cJSON_GetObjectItemCaseSensitive(root, "status");
    if (!cJSON_IsNumber(id) || !cJSON_IsString(status)) {
        cJSON_Delete(root);
        send_error(conn, 400, "Fields 'order_id' (number) and 'status' (string) are required");
        return;
    }

    int order_id = id->valueint;
    char new_status[32];
    snprintf(new_status, sizeof(new_status), "%s", status->valuestring);
    cJSON_Delete(root);

    if (strcmp(new_status, "completed") == 0) {
        send_error(conn, 400, "Completing an order needs the student's OTP: use /api/owner/orders/verify-otp");
        return;
    }
    if (strcmp(new_status, "pending") != 0 && strcmp(new_status, "preparing") != 0 &&
        strcmp(new_status, "ready") != 0   && strcmp(new_status, "cancelled") != 0) {
        send_error(conn, 400, "Invalid status. Allowed: pending, preparing, ready, cancelled");
        return;
    }

    /* new_status is one of the 4 words above, so it is safe to put in SQL */
    int affected = db_execute(
        "UPDATE orders SET status = '%s' "
        "WHERE order_id = %d AND canteen_id = %d AND status NOT IN ('completed', 'cancelled');",
        new_status, order_id, auth->canteen_id);

    if (affected < 0)  { send_error(conn, 500, "Failed to update order status"); return; }
    if (affected == 0) { send_error(conn, 404, "Order not found, or it is already completed/cancelled"); return; }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "order_id", order_id);
    cJSON_AddStringToObject(data, "new_status", new_status);
    send_success(conn, 200, "Order status updated", data);
}

/* POST /api/owner/orders/verify-otp
 * The student shows a 6-digit OTP when collecting food. If it matches,
 * the order is marked completed. */
void handle_post_verify_otp(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    cJSON *root = cJSON_Parse(body);
    if (!root) { send_error(conn, 400, "Invalid JSON in request body"); return; }

    cJSON *id  = cJSON_GetObjectItemCaseSensitive(root, "order_id");
    cJSON *otp = cJSON_GetObjectItemCaseSensitive(root, "otp");
    if (!cJSON_IsNumber(id) || (!cJSON_IsString(otp) && !cJSON_IsNumber(otp))) {
        cJSON_Delete(root);
        send_error(conn, 400, "Fields 'order_id' and 'otp' (6-digit string or number) are required");
        return;
    }

    int order_id = id->valueint;
    char entered_otp[16];
    if (cJSON_IsString(otp)) snprintf(entered_otp, sizeof(entered_otp), "%s", otp->valuestring);
    else                     snprintf(entered_otp, sizeof(entered_otp), "%06d", otp->valueint);
    cJSON_Delete(root);

    /* Get the real OTP and current status from the database */
    MYSQL_RES *res = db_query(
        "SELECT status, otp_code, total_amount FROM orders WHERE order_id = %d AND canteen_id = %d LIMIT 1;",
        order_id, auth->canteen_id);
    if (!res) { send_error(conn, 500, "Database query error"); return; }

    MYSQL_ROW row = mysql_fetch_row(res);
    if (!row) {
        mysql_free_result(res);
        send_error(conn, 404, "Order not found for this canteen");
        return;
    }

    char status[32], real_otp[16];
    snprintf(status,   sizeof(status),   "%s", row[0] ? row[0] : "");
    snprintf(real_otp, sizeof(real_otp), "%s", row[1] ? row[1] : "");
    double total = row[2] ? atof(row[2]) : 0.0;
    mysql_free_result(res);

    if (strcmp(status, "completed") == 0) { send_error(conn, 409, "Order is already completed"); return; }
    if (strcmp(status, "cancelled") == 0) { send_error(conn, 400, "Order was cancelled"); return; }
    if (strcmp(real_otp, entered_otp) != 0) { send_error(conn, 400, "Invalid OTP code"); return; }

    int affected = db_execute(
        "UPDATE orders SET status = 'completed', completed_at = NOW() WHERE order_id = %d AND canteen_id = %d;",
        order_id, auth->canteen_id);
    if (affected <= 0) { send_error(conn, 500, "Failed to mark order as completed"); return; }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "order_id", order_id);
    cJSON_AddStringToObject(data, "status", "completed");
    cJSON_AddNumberToObject(data, "total_amount", total);
    send_success(conn, 200, "OTP verified. Order marked as completed.", data);
}
