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

/* GET /api/owner/analytics/rush-hours  (hours 9 through 17) */
void handle_get_analytics_rush_hours(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    MYSQL_RES *res = db_query(
        "SELECT HOUR(COALESCE(completed_at, created_at)), COUNT(*), COALESCE(SUM(total_amount), 0) "
        "FROM orders WHERE canteen_id = %d AND status = 'completed' "
        "AND HOUR(COALESCE(completed_at, created_at)) BETWEEN 9 AND 17 "
        "GROUP BY HOUR(COALESCE(completed_at, created_at));",
        auth->canteen_id);
    if (!res) { send_error(conn, 500, "Database error retrieving rush hours"); return; }

    int counts[18] = {0};
    double revenues[18] = {0};
    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        int hour = row[0] ? atoi(row[0]) : -1;
        if (hour >= 9 && hour <= 17) {
            counts[hour] = row[1] ? atoi(row[1]) : 0;
            revenues[hour] = row[2] ? atof(row[2]) : 0.0;
        }
    }
    mysql_free_result(res);

    cJSON *hours = cJSON_CreateArray();
    for (int hour = 9; hour <= 17; hour++) {
        char label[32];
        snprintf(label, sizeof(label), "%02d:00 - %02d:00", hour, (hour + 1) % 24);

        cJSON *entry = cJSON_CreateObject();
        cJSON_AddNumberToObject(entry, "hour", hour);
        cJSON_AddStringToObject(entry, "time_window", label);
        cJSON_AddNumberToObject(entry, "completed_orders", counts[hour]);
        cJSON_AddNumberToObject(entry, "revenue", revenues[hour]);
        cJSON_AddItemToArray(hours, entry);
    }

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

/* GET /api/owner/analytics/peak-days */
void handle_get_analytics_peak_days(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    MYSQL_RES *res = db_query(
        "SELECT DAYOFWEEK(COALESCE(completed_at, created_at)), COUNT(*), "
        "COALESCE(SUM(total_amount), 0) "
        "FROM orders WHERE canteen_id = %d AND status = 'completed' "
        "GROUP BY DAYOFWEEK(COALESCE(completed_at, created_at));",
        auth->canteen_id);
    if (!res) { send_error(conn, 500, "Database error retrieving peak days"); return; }

    static const char *short_names[] = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    static const char *full_names[] = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    int counts[7] = {0};
    double revenues[7] = {0};

    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        int mysql_day = row[0] ? atoi(row[0]) : 0;
        int index = (mysql_day + 5) % 7;
        if (mysql_day >= 1 && mysql_day <= 7) {
            counts[index] = row[1] ? atoi(row[1]) : 0;
            revenues[index] = row[2] ? atof(row[2]) : 0.0;
        }
    }
    mysql_free_result(res);

    cJSON *peak_days = cJSON_CreateArray();
    for (int i = 0; i < 7; i++) {
        cJSON *entry = cJSON_CreateObject();
        cJSON_AddStringToObject(entry, "day", short_names[i]);
        cJSON_AddStringToObject(entry, "day_full", full_names[i]);
        cJSON_AddNumberToObject(entry, "completed_orders", counts[i]);
        cJSON_AddNumberToObject(entry, "revenue", revenues[i]);
        cJSON_AddItemToArray(peak_days, entry);
    }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddItemToObject(data, "peak_days", peak_days);
    send_success(conn, 200, "Peak days retrieved", data);
}

/* GET /api/owner/analytics/top-dishes-by-day */
void handle_get_analytics_top_dishes_by_day(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    MYSQL_RES *res = db_query(
        "SELECT DAYOFWEEK(COALESCE(o.completed_at, o.created_at)), "
        "m.item_id, m.name, m.category, SUM(oi.quantity), SUM(oi.subtotal) "
        "FROM order_items oi "
        "JOIN orders o ON oi.order_id = o.order_id "
        "JOIN menu_items m ON oi.item_id = m.item_id "
        "WHERE o.canteen_id = %d AND o.status = 'completed' "
        "GROUP BY DAYOFWEEK(COALESCE(o.completed_at, o.created_at)), "
        "m.item_id, m.name, m.category "
        "ORDER BY DAYOFWEEK(COALESCE(o.completed_at, o.created_at)), SUM(oi.quantity) DESC;",
        auth->canteen_id);
    if (!res) { send_error(conn, 500, "Database error retrieving dishes by day"); return; }

    static const char *day_names[] = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    cJSON *by_day = cJSON_CreateObject();
    cJSON *arrays[7];
    int ranks[7] = {0};
    for (int i = 0; i < 7; i++) {
        arrays[i] = cJSON_CreateArray();
        cJSON_AddItemToObject(by_day, day_names[i], arrays[i]);
    }

    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        int mysql_day = row[0] ? atoi(row[0]) : 0;
        int index = (mysql_day + 5) % 7;
        if (mysql_day < 1 || mysql_day > 7 || ranks[index] >= 5) continue;

        cJSON *entry = cJSON_CreateObject();
        cJSON_AddNumberToObject(entry, "rank", ++ranks[index]);
        cJSON_AddNumberToObject(entry, "item_id", row[1] ? atoi(row[1]) : 0);
        cJSON_AddStringToObject(entry, "name", row[2] ? row[2] : "");
        cJSON_AddStringToObject(entry, "category", row[3] ? row[3] : "General");
        cJSON_AddNumberToObject(entry, "total_units_sold", row[4] ? atoi(row[4]) : 0);
        cJSON_AddNumberToObject(entry, "total_revenue", row[5] ? atof(row[5]) : 0.0);
        cJSON_AddItemToArray(arrays[index], entry);
    }
    mysql_free_result(res);

    cJSON *data = cJSON_CreateObject();
    cJSON_AddItemToObject(data, "top_dishes_by_day", by_day);
    send_success(conn, 200, "Top dishes by day retrieved", data);
}
