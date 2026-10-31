#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>

#include "http.h"
#include "db.h"
#include "auth.h"
#include "utils.h"
#include "handlers.h"

static void route_request(HttpRequest *req) {
    /* 1. Public Health Check */
    if (strcmp(req->path, "/api/health") == 0 && strcmp(req->method, "GET") == 0) {
        send_json(req->client_sock, 200, "{\"status\":\"ok\",\"service\":\"canteen_owner_backend\",\"language\":\"C\"}");
        return;
    }

    /* 2. Owner Endpoints Guard */
    if (strncmp(req->path, "/api/owner/", 11) == 0) {
        AuthContext auth_ctx;
        if (!auth_validate_owner(req, &auth_ctx)) {
            if (req->auth_token[0] == '\0') {
                send_error(req->client_sock, 401, "Missing authorization token. Provide 'Authorization: Bearer <token>' header");
            } else {
                send_error(req->client_sock, 403, "Forbidden: Invalid token or owner role required");
            }
            return;
        }

        /* Module 1: Canteen & Join Code Management */
        if (strcmp(req->path, "/api/owner/canteen") == 0 && strcmp(req->method, "GET") == 0) {
            handle_get_canteen(req, &auth_ctx);
            return;
        }
        if (strcmp(req->path, "/api/owner/canteen/join-code") == 0 && strcmp(req->method, "POST") == 0) {
            handle_post_join_code(req, &auth_ctx);
            return;
        }

        /* Module 2: Menu Management (Full Access) */
        if (strcmp(req->path, "/api/owner/menu") == 0 && strcmp(req->method, "GET") == 0) {
            handle_get_menu(req, &auth_ctx);
            return;
        }
        if (strcmp(req->path, "/api/owner/menu/item") == 0) {
            if (strcmp(req->method, "POST") == 0) {
                handle_post_menu_item(req, &auth_ctx);
                return;
            }
            if (strcmp(req->method, "PUT") == 0) {
                handle_put_menu_item(req, &auth_ctx);
                return;
            }
            if (strcmp(req->method, "DELETE") == 0) {
                handle_delete_menu_item(req, &auth_ctx);
                return;
            }
        }
        if (strcmp(req->path, "/api/owner/menu/item/toggle-stock") == 0 && strcmp(req->method, "PATCH") == 0) {
            handle_patch_stock_toggle(req, &auth_ctx);
            return;
        }

        /* Module 3: Live Order Queue & Scheduling */
        if (strcmp(req->path, "/api/owner/orders/live") == 0 && strcmp(req->method, "GET") == 0) {
            handle_get_live_orders(req, &auth_ctx);
            return;
        }

        /* Module 4: Status Progression & OTP Handshake */
        if (strcmp(req->path, "/api/owner/orders/status") == 0 && strcmp(req->method, "PATCH") == 0) {
            handle_patch_order_status(req, &auth_ctx);
            return;
        }
        if (strcmp(req->path, "/api/owner/orders/verify-otp") == 0 && strcmp(req->method, "POST") == 0) {
            handle_post_verify_otp(req, &auth_ctx);
            return;
        }

        /* Module 5: Sales & Order Analytics */
        if (strcmp(req->path, "/api/owner/analytics/summary") == 0 && strcmp(req->method, "GET") == 0) {
            handle_get_analytics_summary(req, &auth_ctx);
            return;
        }
        if (strcmp(req->path, "/api/owner/analytics/rush-hours") == 0 && strcmp(req->method, "GET") == 0) {
            handle_get_analytics_rush_hours(req, &auth_ctx);
            return;
        }
        if (strcmp(req->path, "/api/owner/analytics/top-dishes") == 0 && strcmp(req->method, "GET") == 0) {
            handle_get_analytics_top_dishes(req, &auth_ctx);
            return;
        }

        send_error(req->client_sock, 404, "Endpoint not found");
        return;
    }

    send_error(req->client_sock, 404, "Not Found");
}

static void load_env_file(const char *filename, DBConfig *cfg, int *http_port) {
    FILE *fp = fopen(filename, "r");
    if (!fp) return;

    char line[512];
    while (fgets(line, sizeof(line), fp)) {
        char *nl = strchr(line, '\r');
        if (nl) *nl = '\0';
        nl = strchr(line, '\n');
        if (nl) *nl = '\0';

        char *p = line;
        while (*p == ' ' || *p == '\t') p++;
        if (*p == '#' || *p == '\0') continue;

        char *eq = strchr(p, '=');
        if (!eq) continue;
        *eq = '\0';
        char *key = p;
        char *val = eq + 1;

        char *end = key + strlen(key) - 1;
        while (end > key && (*end == ' ' || *end == '\t')) {
            *end = '\0';
            end--;
        }

        while (*val == ' ' || *val == '\t') val++;
        if (*val == '"' || *val == '\'') {
            char quote = *val;
            val++;
            char *qend = strchr(val, quote);
            if (qend) *qend = '\0';
        }

        if (strcmp(key, "DB_HOST") == 0) snprintf(cfg->host, sizeof(cfg->host), "%s", val);
        else if (strcmp(key, "DB_PORT") == 0) cfg->port = atoi(val);
        else if (strcmp(key, "DB_USER") == 0) snprintf(cfg->user, sizeof(cfg->user), "%s", val);
        else if (strcmp(key, "DB_PASS") == 0) snprintf(cfg->password, sizeof(cfg->password), "%s", val);
        else if (strcmp(key, "DB_NAME") == 0) snprintf(cfg->database, sizeof(cfg->database), "%s", val);
        else if (strcmp(key, "PORT") == 0 && http_port) *http_port = atoi(val);
    }
    fclose(fp);
    printf("[CONFIG] Loaded settings from %s\n", filename);
}

int main(int argc, char *argv[]) {
    (void)argc;
    (void)argv;

    /* Database configuration defaults */
    DBConfig db_cfg;
    snprintf(db_cfg.host, sizeof(db_cfg.host), "127.0.0.1");
    db_cfg.port = 3307;
    snprintf(db_cfg.user, sizeof(db_cfg.user), "root");
    snprintf(db_cfg.password, sizeof(db_cfg.password), "root");
    snprintf(db_cfg.database, sizeof(db_cfg.database), "canteen_db");
    int http_port = 8080;

    /* Load configuration from .env */
    load_env_file(".env", &db_cfg, &http_port);

    /* Environment variables take highest precedence */
    const char *env_host = getenv("DB_HOST");
    const char *env_port = getenv("DB_PORT");
    const char *env_user = getenv("DB_USER");
    const char *env_pass = getenv("DB_PASS");
    const char *env_name = getenv("DB_NAME");
    const char *env_http_port = getenv("PORT");

    if (env_host) snprintf(db_cfg.host, sizeof(db_cfg.host), "%s", env_host);
    if (env_port) db_cfg.port = atoi(env_port);
    if (env_user) snprintf(db_cfg.user, sizeof(db_cfg.user), "%s", env_user);
    if (env_pass) snprintf(db_cfg.password, sizeof(db_cfg.password), "%s", env_pass);
    if (env_name) snprintf(db_cfg.database, sizeof(db_cfg.database), "%s", env_name);
    if (env_http_port) http_port = atoi(env_http_port);

    printf("====================================================\n");
    printf("  CANTEEN OWNER BACKEND (PURE C SOCKET SERVER)\n");
    printf("====================================================\n");
    printf("[CONFIG] DB Host: %s:%d\n", db_cfg.host, db_cfg.port);
    printf("[CONFIG] DB User: %s\n", db_cfg.user);
    printf("[CONFIG] DB Name: %s\n", db_cfg.database);

    /* Connect to MySQL */
    if (!db_init(&db_cfg)) {
        fprintf(stderr, "[WARN] Could not connect to MySQL at %s:%d (Check credentials or DB_PASS)\n",
                db_cfg.host, db_cfg.port);
        fprintf(stderr, "[INFO] Continuing server startup so API routes are active...\n");
    }

    /* Start HTTP server (blocking loop) */
    http_server_start(http_port, route_request);

    db_close();
    return 0;
}
