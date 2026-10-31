#include "handlers.h"
#include "db.h"
#include "utils.h"
#include "cJSON.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void handle_get_analytics_summary(HttpRequest *req, const AuthContext *ctx) {
    MYSQL_RES *res = db_query(
        "SELECT "
        "  COALESCE(SUM(total_amount), 0.00) AS all_time_revenue, "
        "  COUNT(*) AS all_time_orders, "
        "  COALESCE(SUM(CASE WHEN DATE(completed_at) = CURDATE() THEN total_amount ELSE 0.00 END), 0.00) AS today_revenue, "
        "  COUNT(CASE WHEN DATE(completed_at) = CURDATE() THEN 1 END) AS today_orders, "
        "  COUNT(CASE WHEN order_type = 'dine_in' THEN 1 END) AS dine_in_count, "
        "  COUNT(CASE WHEN order_type = 'parcel' THEN 1 END) AS parcel_count "
        "FROM orders "
        "WHERE canteen_id = %d AND status = 'completed';",
        ctx->canteen_id
    );
    
    if (!res) {
        send_error(req->client_sock, 500, "Database error retrieving sales analytics");
        return;
    }
    
    MYSQL_ROW row = mysql_fetch_row(res);
    double all_time_rev = (row && row[0]) ? atof(row[0]) : 0.0;
    int all_time_ord = (row && row[1]) ? atoi(row[1]) : 0;
    double today_rev = (row && row[2]) ? atof(row[2]) : 0.0;
    int today_ord = (row && row[3]) ? atoi(row[3]) : 0;
    int dine_in_cnt = (row && row[4]) ? atoi(row[4]) : 0;
    int parcel_cnt = (row && row[5]) ? atoi(row[5]) : 0;
    
    mysql_free_result(res);
    
    double aov = all_time_ord > 0 ? (all_time_rev / (double)all_time_ord) : 0.0;
    
    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "all_time_revenue", all_time_rev);
    cJSON_AddNumberToObject(data, "all_time_orders", all_time_ord);
    cJSON_AddNumberToObject(data, "today_revenue", today_rev);
    cJSON_AddNumberToObject(data, "today_orders", today_ord);
    cJSON_AddNumberToObject(data, "average_order_value", aov);
    
    cJSON *breakdown = cJSON_CreateObject();
    cJSON_AddNumberToObject(breakdown, "dine_in_orders", dine_in_cnt);
    cJSON_AddNumberToObject(breakdown, "parcel_orders", parcel_cnt);
    cJSON_AddItemToObject(data, "packaging_breakdown", breakdown);
    
    send_success_json(req->client_sock, 200, "Sales and volume summary retrieved", data);
}

void handle_get_analytics_rush_hours(HttpRequest *req, const AuthContext *ctx) {
    MYSQL_RES *res = db_query(
        "SELECT HOUR(created_at) AS hr, "
        "       COUNT(*) AS order_count, "
        "       COALESCE(SUM(total_amount), 0.00) AS hourly_revenue "
        "FROM orders "
        "WHERE canteen_id = %d AND status = 'completed' "
        "GROUP BY HOUR(created_at) "
        "ORDER BY order_count DESC;",
        ctx->canteen_id
    );
    
    if (!res) {
        send_error(req->client_sock, 500, "Database error retrieving rush hours");
        return;
    }
    
    cJSON *hours_array = cJSON_CreateArray();
    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        int hour = row[0] ? atoi(row[0]) : 0;
        int count = row[1] ? atoi(row[1]) : 0;
        double rev = row[2] ? atof(row[2]) : 0.0;
        
        char hour_label[32];
        snprintf(hour_label, sizeof(hour_label), "%02d:00 - %02d:00", hour, (hour + 1) % 24);
        
        cJSON *entry = cJSON_CreateObject();
        cJSON_AddNumberToObject(entry, "hour", hour);
        cJSON_AddStringToObject(entry, "time_window", hour_label);
        cJSON_AddNumberToObject(entry, "completed_orders", count);
        cJSON_AddNumberToObject(entry, "revenue", rev);
        cJSON_AddItemToArray(hours_array, entry);
    }
    mysql_free_result(res);
    
    cJSON *data = cJSON_CreateObject();
    cJSON_AddItemToObject(data, "peak_rush_hours", hours_array);
    send_success_json(req->client_sock, 200, "Peak rush hours retrieved", data);
}

void handle_get_analytics_top_dishes(HttpRequest *req, const AuthContext *ctx) {
    MYSQL_RES *res = db_query(
        "SELECT m.item_id, m.name, m.category, "
        "       COALESCE(SUM(oi.quantity), 0) AS total_sold, "
        "       COALESCE(SUM(oi.subtotal), 0.00) AS total_revenue "
        "FROM order_items oi "
        "JOIN orders o ON oi.order_id = o.order_id "
        "JOIN menu_items m ON oi.item_id = m.item_id "
        "WHERE o.canteen_id = %d AND o.status = 'completed' "
        "GROUP BY m.item_id, m.name, m.category "
        "ORDER BY total_sold DESC "
        "LIMIT 10;",
        ctx->canteen_id
    );
    
    if (!res) {
        send_error(req->client_sock, 500, "Database error retrieving top dishes");
        return;
    }
    
    cJSON *dishes_array = cJSON_CreateArray();
    MYSQL_ROW row;
    int rank = 1;
    while ((row = mysql_fetch_row(res))) {
        cJSON *entry = cJSON_CreateObject();
        cJSON_AddNumberToObject(entry, "rank", rank++);
        cJSON_AddNumberToObject(entry, "item_id", row[0] ? atoi(row[0]) : 0);
        cJSON_AddStringToObject(entry, "name", row[1] ? row[1] : "");
        cJSON_AddStringToObject(entry, "category", row[2] ? row[2] : "General");
        cJSON_AddNumberToObject(entry, "total_units_sold", row[3] ? atoi(row[3]) : 0);
        cJSON_AddNumberToObject(entry, "total_revenue", row[4] ? atof(row[4]) : 0.0);
        cJSON_AddItemToArray(dishes_array, entry);
    }
    mysql_free_result(res);
    
    cJSON *data = cJSON_CreateObject();
    cJSON_AddItemToObject(data, "top_selling_dishes", dishes_array);
    send_success_json(req->client_sock, 200, "Top selling dishes retrieved", data);
}
