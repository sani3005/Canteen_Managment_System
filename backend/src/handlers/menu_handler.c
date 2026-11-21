#include "handlers.h"
#include "db.h"
#include "utils.h"
#include "cJSON.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void handle_get_menu(HttpRequest *req, const AuthContext *ctx) {
    MYSQL_RES *res = db_query(
        "SELECT item_id, name, description, price, is_available, category, updated_at "
        "FROM menu_items WHERE canteen_id = %d ORDER BY category, name;",
        ctx->canteen_id
    );
    
    if (!res) {
        send_error(req->client_sock, 500, "Database query error for menu items");
        return;
    }
    
    cJSON *items_array = cJSON_CreateArray();
    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        cJSON *item = cJSON_CreateObject();
        cJSON_AddNumberToObject(item, "item_id", row[0] ? atoi(row[0]) : 0);
        cJSON_AddStringToObject(item, "name", row[1] ? row[1] : "");
        cJSON_AddStringToObject(item, "description", row[2] ? row[2] : "");
        cJSON_AddNumberToObject(item, "price", row[3] ? atof(row[3]) : 0.0);
        cJSON_AddBoolToObject(item, "is_available", (row[4] && atoi(row[4]) == 1) ? true : false);
        cJSON_AddStringToObject(item, "category", row[5] ? row[5] : "General");
        cJSON_AddStringToObject(item, "updated_at", row[6] ? row[6] : "");
        cJSON_AddItemToArray(items_array, item);
    }
    mysql_free_result(res);
    
    cJSON *data = cJSON_CreateObject();
    cJSON_AddItemToObject(data, "items", items_array);
    cJSON_AddNumberToObject(data, "count", cJSON_GetArraySize(items_array));
    
    send_success_json(req->client_sock, 200, "Menu items retrieved", data);
}

void handle_post_menu_item(HttpRequest *req, const AuthContext *ctx) {
    cJSON *root = cJSON_Parse(req->body);
    if (!root) {
        send_error(req->client_sock, 400, "Invalid JSON in request body");
        return;
    }
    
    cJSON *name_obj = cJSON_GetObjectItemCaseSensitive(root, "name");
    cJSON *price_obj = cJSON_GetObjectItemCaseSensitive(root, "price");
    if (!name_obj || !cJSON_IsString(name_obj) || !price_obj || !cJSON_IsNumber(price_obj)) {
        cJSON_Delete(root);
        send_error(req->client_sock, 400, "Fields 'name' (string) and 'price' (number) are required");
        return;
    }
    
    cJSON *desc_obj = cJSON_GetObjectItemCaseSensitive(root, "description");
    cJSON *cat_obj = cJSON_GetObjectItemCaseSensitive(root, "category");
    cJSON *avail_obj = cJSON_GetObjectItemCaseSensitive(root, "is_available");
    
    const char *raw_name = name_obj->valuestring;
    const char *raw_desc = (desc_obj && cJSON_IsString(desc_obj)) ? desc_obj->valuestring : "";
    const char *raw_cat = (cat_obj && cJSON_IsString(cat_obj)) ? cat_obj->valuestring : "General";
    double price = price_obj->valuedouble;
    int is_available = (avail_obj && cJSON_IsBool(avail_obj)) ? (cJSON_IsTrue(avail_obj) ? 1 : 0) : 1;
    
    char esc_name[256], esc_desc[2048], esc_cat[128];
    db_escape_string(esc_name, raw_name, (unsigned long)strlen(raw_name));
    db_escape_string(esc_desc, raw_desc, (unsigned long)strlen(raw_desc));
    db_escape_string(esc_cat, raw_cat, (unsigned long)strlen(raw_cat));
    
    int affected = db_execute(
        "INSERT INTO menu_items (canteen_id, name, description, price, is_available, category) "
        "VALUES (%d, '%s', '%s', %.2f, %d, '%s');",
        ctx->canteen_id, esc_name, esc_desc, price, is_available, esc_cat
    );
    
    cJSON_Delete(root);
    
    if (affected <= 0) {
        send_error(req->client_sock, 500, "Failed to insert menu item into database");
        return;
    }
    
    unsigned long long new_id = db_last_insert_id();
    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "item_id", (double)new_id);
    
    send_success_json(req->client_sock, 201, "Menu item added successfully", data);
}

void handle_put_menu_item(HttpRequest *req, const AuthContext *ctx) {
    cJSON *root = cJSON_Parse(req->body);
    if (!root) {
        send_error(req->client_sock, 400, "Invalid JSON in request body");
        return;
    }
    
    cJSON *id_obj = cJSON_GetObjectItemCaseSensitive(root, "item_id");
    if (!id_obj || !cJSON_IsNumber(id_obj)) {
        cJSON_Delete(root);
        send_error(req->client_sock, 400, "'item_id' is required");
        return;
    }
    int item_id = id_obj->valueint;
    
    cJSON *name_obj = cJSON_GetObjectItemCaseSensitive(root, "name");
    cJSON *desc_obj = cJSON_GetObjectItemCaseSensitive(root, "description");
    cJSON *price_obj = cJSON_GetObjectItemCaseSensitive(root, "price");
    cJSON *cat_obj = cJSON_GetObjectItemCaseSensitive(root, "category");
    
    const char *raw_name = (name_obj && cJSON_IsString(name_obj)) ? name_obj->valuestring : "";
    const char *raw_desc = (desc_obj && cJSON_IsString(desc_obj)) ? desc_obj->valuestring : "";
    const char *raw_cat = (cat_obj && cJSON_IsString(cat_obj)) ? cat_obj->valuestring : "";
    double price = (price_obj && cJSON_IsNumber(price_obj)) ? price_obj->valuedouble : -1.0;
    
    char esc_name[256], esc_desc[2048], esc_cat[128];
    db_escape_string(esc_name, raw_name, (unsigned long)strlen(raw_name));
    db_escape_string(esc_desc, raw_desc, (unsigned long)strlen(raw_desc));
    db_escape_string(esc_cat, raw_cat, (unsigned long)strlen(raw_cat));
    
    int affected = db_execute(
        "UPDATE menu_items SET "
        "name = CASE WHEN '%s' != '' THEN '%s' ELSE name END, "
        "description = CASE WHEN '%s' != '' THEN '%s' ELSE description END, "
        "category = CASE WHEN '%s' != '' THEN '%s' ELSE category END, "
        "price = CASE WHEN %.2f >= 0 THEN %.2f ELSE price END "
        "WHERE item_id = %d AND canteen_id = %d;",
        esc_name, esc_name,
        esc_desc, esc_desc,
        esc_cat, esc_cat,
        price, price,
        item_id, ctx->canteen_id
    );
    
    cJSON_Delete(root);
    
    if (affected < 0) {
        send_error(req->client_sock, 500, "Failed to update menu item in database");
        return;
    }
    
    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "item_id", item_id);
    send_success_json(req->client_sock, 200, "Menu item updated successfully", data);
}

void handle_delete_menu_item(HttpRequest *req, const AuthContext *ctx) {
    int item_id = 0;
    char id_str[32];
    if (get_query_param(req, "id", id_str, sizeof(id_str))) {
        item_id = atoi(id_str);
    } else if (req->body_len > 0) {
        cJSON *root = cJSON_Parse(req->body);
        if (root) {
            cJSON *id_obj = cJSON_GetObjectItemCaseSensitive(root, "item_id");
            if (id_obj && cJSON_IsNumber(id_obj)) {
                item_id = id_obj->valueint;
            }
            cJSON_Delete(root);
        }
    }
    
    if (item_id <= 0) {
        send_error(req->client_sock, 400, "Valid 'id' query parameter or 'item_id' in body required");
        return;
    }
    
    int affected = db_execute(
        "DELETE FROM menu_items WHERE item_id = %d AND canteen_id = %d;",
        item_id, ctx->canteen_id
    );
    
    if (affected < 0) {
        send_error(req->client_sock, 500, "Failed to delete menu item from database");
        return;
    }
    
    if (affected == 0) {
        send_error(req->client_sock, 404, "Menu item not found or unauthorized");
        return;
    }
    
    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "deleted_item_id", item_id);
    send_success_json(req->client_sock, 200, "Menu item deleted successfully", data);
}

void handle_patch_stock_toggle(HttpRequest *req, const AuthContext *ctx) {
    cJSON *root = cJSON_Parse(req->body);
    if (!root) {
        send_error(req->client_sock, 400, "Invalid JSON in request body");
        return;
    }
    
    cJSON *id_obj = cJSON_GetObjectItemCaseSensitive(root, "item_id");
    if (!id_obj || !cJSON_IsNumber(id_obj)) {
        cJSON_Delete(root);
        send_error(req->client_sock, 400, "'item_id' is required");
        return;
    }
    int item_id = id_obj->valueint;
    
    cJSON *avail_obj = cJSON_GetObjectItemCaseSensitive(root, "is_available");
    int affected;
    if (avail_obj && (cJSON_IsBool(avail_obj) || cJSON_IsNumber(avail_obj))) {
        int new_val = cJSON_IsBool(avail_obj) ? (cJSON_IsTrue(avail_obj) ? 1 : 0) : (avail_obj->valueint ? 1 : 0);
        affected = db_execute(
            "UPDATE menu_items SET is_available = %d WHERE item_id = %d AND canteen_id = %d;",
            new_val, item_id, ctx->canteen_id
        );
    } else {
        /* Flip current stock availability */
        affected = db_execute(
            "UPDATE menu_items SET is_available = CASE WHEN is_available = 1 THEN 0 ELSE 1 END "
            "WHERE item_id = %d AND canteen_id = %d;",
            item_id, ctx->canteen_id
        );
    }
    cJSON_Delete(root);
    
    if (affected < 0) {
        send_error(req->client_sock, 500, "Database update error");
        return;
    }
    
    /* Query the new state */
    MYSQL_RES *res = db_query("SELECT is_available FROM menu_items WHERE item_id = %d LIMIT 1;", item_id);
    int current_stock = 0;
    if (res) {
        MYSQL_ROW row = mysql_fetch_row(res);
        if (row && row[0]) current_stock = atoi(row[0]);
        mysql_free_result(res);
    }
    
    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "item_id", item_id);
    cJSON_AddBoolToObject(data, "is_available", current_stock == 1);
    
    send_success_json(req->client_sock, 200, "Stock toggle updated successfully", data);
}
