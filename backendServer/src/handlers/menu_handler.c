/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* menu_handler.c - Module 2: menu management */
#include "handlers.h"
#include "db.h"
#include "utils.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Does this item exist in the owner's canteen? (1 = yes) */
static int item_exists(int item_id, int canteen_id) {
    MYSQL_RES *res = db_query("SELECT 1 FROM menu_items WHERE item_id = %d AND canteen_id = %d LIMIT 1;",
                              item_id, canteen_id);
    if (!res) return 0;
    int found = (mysql_fetch_row(res) != NULL);
    mysql_free_result(res);
    return found;
}

/* GET /api/owner/menu */
void handle_get_menu(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    MYSQL_RES *res = db_query(
        "SELECT item_id, name, description, price, is_available, category, updated_at "
        "FROM menu_items WHERE canteen_id = %d ORDER BY category, name;",
        auth->canteen_id);
    if (!res) { send_error(conn, 500, "Database query error for menu items"); return; }

    cJSON *items = cJSON_CreateArray();
    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res))) {
        cJSON *item = cJSON_CreateObject();
        /* MySQL gives every column as text and NULL as a NULL pointer,
         * so each value is checked (row[i] ? ... : default) before use. */
        cJSON_AddNumberToObject(item, "item_id",      atoi(row[0]));
        cJSON_AddStringToObject(item, "name",         row[1] ? row[1] : "");
        cJSON_AddStringToObject(item, "description",  row[2] ? row[2] : "");
        cJSON_AddNumberToObject(item, "price",        row[3] ? atof(row[3]) : 0.0);
        cJSON_AddBoolToObject  (item, "is_available", row[4] && atoi(row[4]) == 1);
        cJSON_AddStringToObject(item, "category",     row[5] ? row[5] : "General");
        cJSON_AddStringToObject(item, "updated_at",   row[6] ? row[6] : "");
        cJSON_AddItemToArray(items, item);
    }
    mysql_free_result(res);

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "count", cJSON_GetArraySize(items));
    cJSON_AddItemToObject(data, "items", items);
    send_success(conn, 200, "Menu items retrieved", data);
}

/* POST /api/owner/menu/item   body: {"name":"..","price":90,"description":"..","category":"..","is_available":true} */
void handle_post_menu_item(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    cJSON *root = cJSON_Parse(body);
    if (!root) { send_error(conn, 400, "Invalid JSON in request body"); return; }

    cJSON *name  = cJSON_GetObjectItemCaseSensitive(root, "name");
    cJSON *price = cJSON_GetObjectItemCaseSensitive(root, "price");
    if (!cJSON_IsString(name) || !cJSON_IsNumber(price)) {
        cJSON_Delete(root);
        send_error(conn, 400, "Fields 'name' (string) and 'price' (number) are required");
        return;
    }

    cJSON *desc  = cJSON_GetObjectItemCaseSensitive(root, "description");
    cJSON *cat   = cJSON_GetObjectItemCaseSensitive(root, "category");
    cJSON *avail = cJSON_GetObjectItemCaseSensitive(root, "is_available");

    /* Escape every text value before putting it into SQL (prevents SQL injection) */
    char safe_name[256], safe_desc[2048], safe_cat[128];
    db_escape(safe_name, sizeof(safe_name), name->valuestring);
    db_escape(safe_desc, sizeof(safe_desc), cJSON_IsString(desc) ? desc->valuestring : "");
    db_escape(safe_cat,  sizeof(safe_cat),  cJSON_IsString(cat)  ? cat->valuestring  : "General");
    int is_available = cJSON_IsBool(avail) ? cJSON_IsTrue(avail) : 1;

    int affected = db_execute(
        "INSERT INTO menu_items (canteen_id, name, description, price, is_available, category) "
        "VALUES (%d, '%s', '%s', %.2f, %d, '%s');",
        auth->canteen_id, safe_name, safe_desc, price->valuedouble, is_available, safe_cat);
    cJSON_Delete(root);

    if (affected <= 0) { send_error(conn, 500, "Failed to insert menu item"); return; }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "item_id", (double)db_last_insert_id());
    send_success(conn, 201, "Menu item added successfully", data);
}

/* PUT /api/owner/menu/item   body: {"item_id":1, ...any fields to change...} */
void handle_put_menu_item(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    cJSON *root = cJSON_Parse(body);
    if (!root) { send_error(conn, 400, "Invalid JSON in request body"); return; }

    cJSON *id = cJSON_GetObjectItemCaseSensitive(root, "item_id");
    if (!cJSON_IsNumber(id)) {
        cJSON_Delete(root);
        send_error(conn, 400, "'item_id' is required");
        return;
    }
    int item_id = id->valueint;

    if (!item_exists(item_id, auth->canteen_id)) {
        cJSON_Delete(root);
        send_error(conn, 404, "Menu item not found");
        return;
    }

    cJSON *name  = cJSON_GetObjectItemCaseSensitive(root, "name");
    cJSON *desc  = cJSON_GetObjectItemCaseSensitive(root, "description");
    cJSON *cat   = cJSON_GetObjectItemCaseSensitive(root, "category");
    cJSON *price = cJSON_GetObjectItemCaseSensitive(root, "price");

    /* Empty string / -1 means "leave this field unchanged" */
    char safe_name[256], safe_desc[2048], safe_cat[128];
    db_escape(safe_name, sizeof(safe_name), cJSON_IsString(name) ? name->valuestring : "");
    db_escape(safe_desc, sizeof(safe_desc), cJSON_IsString(desc) ? desc->valuestring : "");
    db_escape(safe_cat,  sizeof(safe_cat),  cJSON_IsString(cat)  ? cat->valuestring  : "");
    double new_price = cJSON_IsNumber(price) ? price->valuedouble : -1.0;
    cJSON_Delete(root);

    int affected = db_execute(
        "UPDATE menu_items SET "
        "name = CASE WHEN '%s' != '' THEN '%s' ELSE name END, "
        "description = CASE WHEN '%s' != '' THEN '%s' ELSE description END, "
        "category = CASE WHEN '%s' != '' THEN '%s' ELSE category END, "
        "price = CASE WHEN %.2f >= 0 THEN %.2f ELSE price END "
        "WHERE item_id = %d AND canteen_id = %d;",
        safe_name, safe_name, safe_desc, safe_desc, safe_cat, safe_cat,
        new_price, new_price, item_id, auth->canteen_id);
    if (affected < 0) { send_error(conn, 500, "Failed to update menu item"); return; }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "item_id", item_id);
    send_success(conn, 200, "Menu item updated successfully", data);
}

/* DELETE /api/owner/menu/item?id=5   (or body {"item_id":5}) */
void handle_delete_menu_item(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    int item_id = 0;
    char id_text[32];

    if (get_query_param(conn, "id", id_text, sizeof(id_text))) {
        item_id = atoi(id_text);
    } else if (body[0] != '\0') {
        cJSON *root = cJSON_Parse(body);
        cJSON *id = root ? cJSON_GetObjectItemCaseSensitive(root, "item_id") : NULL;
        if (cJSON_IsNumber(id)) item_id = id->valueint;
        cJSON_Delete(root);
    }
    if (item_id <= 0) {
        send_error(conn, 400, "Give 'id' in the URL (?id=5) or 'item_id' in the body");
        return;
    }

    int affected = db_execute("DELETE FROM menu_items WHERE item_id = %d AND canteen_id = %d;",
                              item_id, auth->canteen_id);
    if (affected < 0)  { send_error(conn, 500, "Failed to delete menu item"); return; }
    if (affected == 0) { send_error(conn, 404, "Menu item not found"); return; }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "deleted_item_id", item_id);
    send_success(conn, 200, "Menu item deleted successfully", data);
}

/* PATCH /api/owner/menu/item/toggle-stock
 * body: {"item_id":1}                      -> flips availability
 *       {"item_id":1,"is_available":true}  -> sets it directly */
void handle_patch_stock_toggle(struct mg_connection *conn, const AuthContext *auth, const char *body) {
    cJSON *root = cJSON_Parse(body);
    if (!root) { send_error(conn, 400, "Invalid JSON in request body"); return; }

    cJSON *id = cJSON_GetObjectItemCaseSensitive(root, "item_id");
    if (!cJSON_IsNumber(id)) {
        cJSON_Delete(root);
        send_error(conn, 400, "'item_id' is required");
        return;
    }
    int item_id = id->valueint;

    if (!item_exists(item_id, auth->canteen_id)) {
        cJSON_Delete(root);
        send_error(conn, 404, "Menu item not found");
        return;
    }

    cJSON *avail = cJSON_GetObjectItemCaseSensitive(root, "is_available");
    int affected;
    if (cJSON_IsBool(avail)) {
        affected = db_execute("UPDATE menu_items SET is_available = %d WHERE item_id = %d AND canteen_id = %d;",
                              cJSON_IsTrue(avail) ? 1 : 0, item_id, auth->canteen_id);
    } else {
        affected = db_execute("UPDATE menu_items SET is_available = 1 - is_available "
                              "WHERE item_id = %d AND canteen_id = %d;", item_id, auth->canteen_id);
    }
    cJSON_Delete(root);
    if (affected < 0) { send_error(conn, 500, "Database update error"); return; }

    /* read back the new value */
    int now_available = 0;
    MYSQL_RES *res = db_query("SELECT is_available FROM menu_items WHERE item_id = %d AND canteen_id = %d;",
                              item_id, auth->canteen_id);
    if (res) {
        MYSQL_ROW row = mysql_fetch_row(res);
        if (row && row[0]) now_available = atoi(row[0]);
        mysql_free_result(res);
    }

    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "item_id", item_id);
    cJSON_AddBoolToObject(data, "is_available", now_available == 1);
    send_success(conn, 200, "Stock availability updated", data);
}
