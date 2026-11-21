/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* routes.c - the route table: which URL + method calls which handler.
 *
 * CivetWeb calls api_handler() for every URL starting with /api/.
 * It looks the request up in the table below, checks the required bearer token
 * (if the route needs it), and then calls the matching handler.
 */
#include "routes.h"
#include "handlers.h"
#include "auth.h"
#include "utils.h"
#include <stdlib.h>
#include <string.h>

typedef void (*HandlerFn)(struct mg_connection *, const AuthContext *, const char *);

typedef struct {
    const char *method;
    const char *path;
    HandlerFn   handler;
    int         auth_role;   /* 0 = public, 1 = owner, 2 = student */
} Route;

static const Route routes[] = {
    /* public */
    { "GET",    "/api/health",                      handle_health,                    0 },
    { "POST",   "/api/student/register",            handle_student_register,           0 },

    /* Students */
    { "GET",    "/api/student/menu",                handle_get_student_menu,           2 },
    { "POST",   "/api/student/orders",              handle_post_student_order,         2 },
    { "GET",    "/api/student/orders",              handle_get_student_orders,         2 },

    /* Module 1: canteen */
    { "GET",    "/api/owner/canteen",               handle_get_canteen,               1 },
    { "POST",   "/api/owner/canteen/join-code",     handle_post_join_code,            1 },

    /* Module 2: menu */
    { "GET",    "/api/owner/menu",                  handle_get_menu,                  1 },
    { "POST",   "/api/owner/menu/item",             handle_post_menu_item,            1 },
    { "PUT",    "/api/owner/menu/item",             handle_put_menu_item,             1 },
    { "DELETE", "/api/owner/menu/item",             handle_delete_menu_item,          1 },
    { "PATCH",  "/api/owner/menu/item/toggle-stock",handle_patch_stock_toggle,        1 },

    /* Module 3: live orders */
    { "GET",    "/api/owner/orders/live",           handle_get_live_orders,           1 },

    /* Module 4: status + OTP */
    { "PATCH",  "/api/owner/orders/status",         handle_patch_order_status,        1 },
    { "POST",   "/api/owner/orders/verify-otp",     handle_post_verify_otp,           1 },

    /* Module 5: analytics */
    { "GET",    "/api/owner/analytics/summary",     handle_get_analytics_summary,     1 },
    { "GET",    "/api/owner/analytics/rush-hours",  handle_get_analytics_rush_hours,  1 },
    { "GET",    "/api/owner/analytics/top-dishes",  handle_get_analytics_top_dishes,  1 },
};

#define ROUTE_COUNT ((int)(sizeof(routes) / sizeof(routes[0])))

int api_handler(struct mg_connection *conn, void *cbdata) {
    (void)cbdata;
    const struct mg_request_info *ri = mg_get_request_info(conn);

    /* Browsers send OPTIONS first (CORS preflight) */
    if (strcmp(ri->request_method, "OPTIONS") == 0) {
        send_cors_preflight(conn);
        return 1;
    }

    /* Find the route: same path AND same method */
    int path_exists = 0;
    const Route *match = NULL;
    for (int i = 0; i < ROUTE_COUNT; i++) {
        if (strcmp(ri->local_uri, routes[i].path) != 0) continue;
        path_exists = 1;
        if (strcmp(ri->request_method, routes[i].method) == 0) {
            match = &routes[i];
            break;
        }
    }

    if (!path_exists) { send_error(conn, 404, "Endpoint not found"); return 1; }
    if (!match)       { send_error(conn, 405, "Method not allowed"); return 1; }

    /* Check the required owner or student token */
    AuthContext auth;
    memset(&auth, 0, sizeof(auth));
    if (match->auth_role != 0) {
        AuthResult r = match->auth_role == 1
            ? auth_check_owner(conn, &auth)
            : auth_check_student(conn, &auth);
        if (r == AUTH_MISSING && match->auth_role == 2) {
            send_error(conn, 401, "Missing token. Send header 'Authorization: Bearer <student-token>'");
            return 1;
        }
        if (r == AUTH_MISSING) {
            send_error(conn, 401, "Missing token. Send header 'Authorization: Bearer <token>'");
            return 1;
        }
        if (r == AUTH_FORBIDDEN) {
            send_error(conn, 403, match->auth_role == 1
                ? "Forbidden: invalid token or owner role required"
                : "Forbidden: invalid student token");
            return 1;
        }
    }

    /* Run the handler */
    char *body = read_body(conn);
    match->handler(conn, &auth, body);
    free(body);
    return 1;
}
