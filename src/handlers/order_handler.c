/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* order_handler.c - Module 3: live order queue */
#include "handlers.h"
#include "db.h"
#include "utils.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Build the list of items inside one order */
static cJSON *get_order_items(int order_id) {
    cJSON *items = cJSON_CreateArray();
    MYSQL_RES *res = db_query(
        "SELECT m.name, oi.quantity, oi.unit_price, oi.subtotal "
        "FROM order_items oi JOIN menu_items m ON oi.item_id = m.item_id "
        "WHERE oi.order_id = %d;", order_id);
    if (!res) return items;

    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        cJSON *it = cJSON_CreateObject();
        cJSON_AddStringToObject(it, "name",       row[0] ? row[0] : "");
        cJSON_AddNumberToObject(it, "quantity",   row[1] ? atoi(row[1]) : 1);
        cJSON_AddNumberToObject(it, "unit_price", row[2] ? atof(row[2]) : 0.0);
        cJSON_AddNumberToObject(it, "subtotal",   row[3] ? atof(row[3]) : 0.0);
        cJSON_AddItemToArray(items, it);
    }
    mysql_free_result(res);
    return items;
}

/* GET /api/owner/orders/live
 * Active orders (pending / preparing / ready), split into immediate orders
 * and pre-orders. Pre-orders are sorted by their scheduled time. */
void handle_get_live_orders(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    MYSQL_RES *res = db_query(
        "SELECT o.order_id, o.student_id, COALESCE(u.username, 'Student'), "
        "       o.order_type, o.is_preorder, o.scheduled_time, o.status, "
        "       o.total_amount, o.created_at "
        "FROM orders o LEFT JOIN users u ON o.student_id = u.user_id "
        "WHERE o.canteen_id = %d AND o.status IN ('pending', 'preparing', 'ready') "
        "ORDER BY o.is_preorder ASC, "
        "         CASE WHEN o.is_preorder = 1 THEN o.scheduled_time ELSE o.created_at END ASC;",
        auth->canteen_id);
    if (!res) { send_error(conn, 500, "Database error retrieving active orders"); return; }

    cJSON *immediate = cJSON_CreateArray();
    cJSON *preorders = cJSON_CreateArray();

    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        int order_id    = atoi(row[0]);
        const char *type = row[3] ? row[3] : "dine_in";
        int is_preorder = row[4] ? atoi(row[4]) : 0;

        cJSON *order = cJSON_CreateObject();
        cJSON_AddNumberToObject(order, "order_id",        order_id);
        cJSON_AddNumberToObject(order, "student_id",      row[1] ? atoi(row[1]) : 0);
        cJSON_AddStringToObject(order, "student_name",    row[2] ? row[2] : "Student");
        cJSON_AddStringToObject(order, "order_type",      type);
        cJSON_AddStringToObject(order, "packaging_label", strcmp(type, "parcel") == 0 ? "Parcel" : "Dine-in");
        cJSON_AddBoolToObject  (order, "is_preorder",     is_preorder == 1);
        cJSON_AddStringToObject(order, "scheduled_time",  row[5] ? row[5] : "");
        cJSON_AddStringToObject(order, "status",          row[6] ? row[6] : "pending");
        cJSON_AddNumberToObject(order, "total_amount",    row[7] ? atof(row[7]) : 0.0);
        cJSON_AddStringToObject(order, "created_at",      row[8] ? row[8] : "");
        cJSON_AddItemToObject  (order, "items",           get_order_items(order_id));

        cJSON_AddItemToArray(is_preorder ? preorders : immediate, order);
    }
    mysql_free_result(res);

    int n_imm = cJSON_GetArraySize(immediate);
    int n_pre = cJSON_GetArraySize(preorders);

    cJSON *data = cJSON_CreateObject();
    cJSON_AddItemToObject  (data, "immediate_orders", immediate);
    cJSON_AddItemToObject  (data, "pre_orders",       preorders);
    cJSON_AddNumberToObject(data, "total_immediate",  n_imm);
    cJSON_AddNumberToObject(data, "total_preorders",  n_pre);
    cJSON_AddNumberToObject(data, "total_active",     n_imm + n_pre);
    send_success(conn, 200, "Live order queue retrieved", data);
}
