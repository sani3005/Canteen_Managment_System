/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* analytics_handler.c - Module 5: sales and order analytics (completed orders only) */
#include "handlers.h"
#include "db.h"
#include "utils.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* GET /api/owner/analytics/summary */
void handle_get_analytics_summary(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    MYSQL_RES *res = db_query(
        "SELECT COALESCE(SUM(total_amount), 0), COUNT(*), "
        "  COALESCE(SUM(CASE WHEN DATE(completed_at) = CURDATE() THEN total_amount ELSE 0 END), 0), "
        "  COUNT(CASE WHEN DATE(completed_at) = CURDATE() THEN 1 END), "
        "  COUNT(CASE WHEN order_type = 'dine_in' THEN 1 END), "
        "  COUNT(CASE WHEN order_type = 'parcel' THEN 1 END) "
        "FROM orders WHERE canteen_id = %d AND status = 'completed';",
        auth->canteen_id);
    if (!res) { send_error(conn, 500, "Database error retrieving analytics"); return; }

    MYSQL_ROW row = mysql_fetch_row(res);
    double total_revenue = (row && row[0]) ? atof(row[0]) : 0.0;
    int    total_orders  = (row && row[1]) ? atoi(row[1]) : 0;
    double today_revenue = (row && row[2]) ? atof(row[2]) : 0.0;
    int    today_orders  = (row && row[3]) ? atoi(row[3]) : 0;
    int    dine_in       = (row && row[4]) ? atoi(row[4]) : 0;
    int    parcel        = (row && row[5]) ? atoi(row[5]) : 0;
    mysql_free_result(res);

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "all_time_revenue",    total_revenue);
    cJSON_AddNumberToObject(data, "all_time_orders",     total_orders);
    cJSON_AddNumberToObject(data, "today_revenue",       today_revenue);
    cJSON_AddNumberToObject(data, "today_orders",        today_orders);
    cJSON_AddNumberToObject(data, "average_order_value", total_orders > 0 ? total_revenue / total_orders : 0.0);

    cJSON *split = cJSON_CreateObject();
    cJSON_AddNumberToObject(split, "dine_in_orders", dine_in);
    cJSON_AddNumberToObject(split, "parcel_orders",  parcel);
    cJSON_AddItemToObject(data, "packaging_breakdown", split);

    send_success(conn, 200, "Sales summary retrieved", data);
}

/* GET /api/owner/analytics/rush-hours  (busiest hour first) */
void handle_get_analytics_rush_hours(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    MYSQL_RES *res = db_query(
        "SELECT HOUR(created_at), COUNT(*), COALESCE(SUM(total_amount), 0) "
        "FROM orders WHERE canteen_id = %d AND status = 'completed' "
        "GROUP BY HOUR(created_at) ORDER BY COUNT(*) DESC;",
        auth->canteen_id);
    if (!res) { send_error(conn, 500, "Database error retrieving rush hours"); return; }

    cJSON *hours = cJSON_CreateArray();
    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        int hour = row[0] ? atoi(row[0]) : 0;
        char label[32];
        snprintf(label, sizeof(label), "%02d:00 - %02d:00", hour, (hour + 1) % 24);

        cJSON *entry = cJSON_CreateObject();
        cJSON_AddNumberToObject(entry, "hour", hour);
        cJSON_AddStringToObject(entry, "time_window", label);
        cJSON_AddNumberToObject(entry, "completed_orders", row[1] ? atoi(row[1]) : 0);
        cJSON_AddNumberToObject(entry, "revenue", row[2] ? atof(row[2]) : 0.0);
        cJSON_AddItemToArray(hours, entry);
    }
    mysql_free_result(res);

    cJSON *data = cJSON_CreateObject();
    cJSON_AddItemToObject(data, "peak_rush_hours", hours);
    send_success(conn, 200, "Peak rush hours retrieved", data);
}

/* GET /api/owner/analytics/top-dishes  (top 10 by units sold) */
void handle_get_analytics_top_dishes(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    MYSQL_RES *res = db_query(
        "SELECT m.item_id, m.name, m.category, SUM(oi.quantity), SUM(oi.subtotal) "
        "FROM order_items oi "
        "JOIN orders o ON oi.order_id = o.order_id "
        "JOIN menu_items m ON oi.item_id = m.item_id "
        "WHERE o.canteen_id = %d AND o.status = 'completed' "
        "GROUP BY m.item_id, m.name, m.category "
        "ORDER BY SUM(oi.quantity) DESC LIMIT 10;",
        auth->canteen_id);
    if (!res) { send_error(conn, 500, "Database error retrieving top dishes"); return; }

    cJSON *dishes = cJSON_CreateArray();
    MYSQL_ROW row;
    int rank = 1;
    while ((row = mysql_fetch_row(res))) {
        cJSON *entry = cJSON_CreateObject();
        cJSON_AddNumberToObject(entry, "rank",             rank++);
        cJSON_AddNumberToObject(entry, "item_id",          atoi(row[0]));
        cJSON_AddStringToObject(entry, "name",             row[1] ? row[1] : "");
        cJSON_AddStringToObject(entry, "category",         row[2] ? row[2] : "General");
        cJSON_AddNumberToObject(entry, "total_units_sold", row[3] ? atoi(row[3]) : 0);
        cJSON_AddNumberToObject(entry, "total_revenue",    row[4] ? atof(row[4]) : 0.0);
        cJSON_AddItemToArray(dishes, entry);
    }
    mysql_free_result(res);

    cJSON *data = cJSON_CreateObject();
    cJSON_AddItemToObject(data, "top_selling_dishes", dishes);
    send_success(conn, 200, "Top selling dishes retrieved", data);
}
