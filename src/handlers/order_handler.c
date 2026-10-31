#include "handlers.h"
#include "db.h"
#include "utils.h"
#include "cJSON.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void handle_get_live_orders(HttpRequest *req, const AuthContext *ctx) {
    /* Query active orders: separate immediate orders from pre-orders, sorted by priority */
    MYSQL_RES *res = db_query(
        "SELECT o.order_id, o.student_id, COALESCE(u.username, 'Student'), "
        "       o.order_type, o.is_preorder, o.scheduled_time, o.status, "
        "       o.total_amount, o.created_at "
        "FROM orders o "
        "LEFT JOIN users u ON o.student_id = u.user_id "
        "WHERE o.canteen_id = %d AND o.status IN ('pending', 'preparing', 'ready') "
        "ORDER BY o.is_preorder ASC, "
        "         CASE WHEN o.is_preorder = 1 THEN o.scheduled_time ELSE o.created_at END ASC;",
        ctx->canteen_id
    );
    
    if (!res) {
        send_error(req->client_sock, 500, "Database error retrieving active orders");
        return;
    }
    
    cJSON *immediate_orders = cJSON_CreateArray();
    cJSON *pre_orders = cJSON_CreateArray();
    
    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        int order_id = row[0] ? atoi(row[0]) : 0;
        int student_id = row[1] ? atoi(row[1]) : 0;
        const char *student_name = row[2] ? row[2] : "Unknown";
        const char *order_type = row[3] ? row[3] : "dine_in";
        int is_preorder = row[4] ? atoi(row[4]) : 0;
        const char *scheduled_time = row[5] ? row[5] : "";
        const char *status = row[6] ? row[6] : "pending";
        double total_amount = row[7] ? atof(row[7]) : 0.0;
        const char *created_at = row[8] ? row[8] : "";
        
        cJSON *order_obj = cJSON_CreateObject();
        cJSON_AddNumberToObject(order_obj, "order_id", order_id);
        cJSON_AddNumberToObject(order_obj, "student_id", student_id);
        cJSON_AddStringToObject(order_obj, "student_name", student_name);
        cJSON_AddStringToObject(order_obj, "order_type", order_type);
        cJSON_AddStringToObject(order_obj, "packaging_label", strcmp(order_type, "parcel") == 0 ? "Parcel" : "Dine-in");
        cJSON_AddBoolToObject(order_obj, "is_preorder", is_preorder == 1);
        cJSON_AddStringToObject(order_obj, "scheduled_time", scheduled_time);
        cJSON_AddStringToObject(order_obj, "status", status);
        cJSON_AddNumberToObject(order_obj, "total_amount", total_amount);
        cJSON_AddStringToObject(order_obj, "created_at", created_at);
        
        /* Query ordered items for this order ticket */
        cJSON *items_arr = cJSON_CreateArray();
        MYSQL_RES *items_res = db_query(
            "SELECT m.name, oi.quantity, oi.unit_price, oi.subtotal "
            "FROM order_items oi "
            "JOIN menu_items m ON oi.item_id = m.item_id "
            "WHERE oi.order_id = %d;",
            order_id
        );
        
        if (items_res) {
            MYSQL_ROW item_row;
            while ((item_row = mysql_fetch_row(items_res))) {
                cJSON *item = cJSON_CreateObject();
                cJSON_AddStringToObject(item, "name", item_row[0] ? item_row[0] : "");
                cJSON_AddNumberToObject(item, "quantity", item_row[1] ? atoi(item_row[1]) : 1);
                cJSON_AddNumberToObject(item, "unit_price", item_row[2] ? atof(item_row[2]) : 0.0);
                cJSON_AddNumberToObject(item, "subtotal", item_row[3] ? atof(item_row[3]) : 0.0);
                cJSON_AddItemToArray(items_arr, item);
            }
            mysql_free_result(items_res);
        }
        
        cJSON_AddItemToObject(order_obj, "items", items_arr);
        
        if (is_preorder) {
            cJSON_AddItemToArray(pre_orders, order_obj);
        } else {
            cJSON_AddItemToArray(immediate_orders, order_obj);
        }
    }
    
    mysql_free_result(res);
    
    cJSON *data = cJSON_CreateObject();
    cJSON_AddItemToObject(data, "immediate_orders", immediate_orders);
    cJSON_AddItemToObject(data, "pre_orders", pre_orders);
    cJSON_AddNumberToObject(data, "total_immediate", cJSON_GetArraySize(immediate_orders));
    cJSON_AddNumberToObject(data, "total_preorders", cJSON_GetArraySize(pre_orders));
    cJSON_AddNumberToObject(data, "total_active", cJSON_GetArraySize(immediate_orders) + cJSON_GetArraySize(pre_orders));
    
    send_success_json(req->client_sock, 200, "Live order queue retrieved", data);
}
