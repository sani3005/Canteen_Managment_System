/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* handlers.h - one function per API endpoint.
 * Every handler gets: the connection, who is logged in, and the request body. */
#ifndef HANDLERS_H
#define HANDLERS_H

#include "civetweb.h"
#include "auth.h"

/* Public */
void handle_health(struct mg_connection *conn, const AuthContext *auth, const char *body);
void handle_student_register(struct mg_connection *conn, const AuthContext *auth, const char *body);
void handle_get_student_menu(struct mg_connection *conn, const AuthContext *auth, const char *body);
void handle_post_student_order(struct mg_connection *conn, const AuthContext *auth, const char *body);
void handle_get_student_orders(struct mg_connection *conn, const AuthContext *auth, const char *body);

/* Module 1: Canteen & join code */
void handle_get_canteen(struct mg_connection *conn, const AuthContext *auth, const char *body);
void handle_post_join_code(struct mg_connection *conn, const AuthContext *auth, const char *body);

/* Module 2: Menu */
void handle_get_menu(struct mg_connection *conn, const AuthContext *auth, const char *body);
void handle_post_menu_item(struct mg_connection *conn, const AuthContext *auth, const char *body);
void handle_put_menu_item(struct mg_connection *conn, const AuthContext *auth, const char *body);
void handle_delete_menu_item(struct mg_connection *conn, const AuthContext *auth, const char *body);
void handle_patch_stock_toggle(struct mg_connection *conn, const AuthContext *auth, const char *body);

/* Module 3: Live orders */
void handle_get_live_orders(struct mg_connection *conn, const AuthContext *auth, const char *body);

/* Module 4: Status + OTP */
void handle_patch_order_status(struct mg_connection *conn, const AuthContext *auth, const char *body);
void handle_post_verify_otp(struct mg_connection *conn, const AuthContext *auth, const char *body);

/* Module 5: Analytics */
void handle_get_analytics_summary(struct mg_connection *conn, const AuthContext *auth, const char *body);
void handle_get_analytics_rush_hours(struct mg_connection *conn, const AuthContext *auth, const char *body);
void handle_get_analytics_top_dishes(struct mg_connection *conn, const AuthContext *auth, const char *body);

#endif
