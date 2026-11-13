#ifndef HANDLERS_H
#define HANDLERS_H

#include "http.h"
#include "auth.h"

/* Module 1: Canteen & Invite Code Management */
void handle_get_canteen(HttpRequest *req, const AuthContext *ctx);
void handle_post_join_code(HttpRequest *req, const AuthContext *ctx);

/* Module 2: Menu Management (Full Access) */
void handle_get_menu(HttpRequest *req, const AuthContext *ctx);
void handle_post_menu_item(HttpRequest *req, const AuthContext *ctx);
void handle_put_menu_item(HttpRequest *req, const AuthContext *ctx);
void handle_delete_menu_item(HttpRequest *req, const AuthContext *ctx);
void handle_patch_stock_toggle(HttpRequest *req, const AuthContext *ctx);

/* Module 3: Live Order Queue & Scheduling */
void handle_get_live_orders(HttpRequest *req, const AuthContext *ctx);

/* Module 4: Status Progression & OTP Handshake */
void handle_patch_order_status(HttpRequest *req, const AuthContext *ctx);
void handle_post_verify_otp(HttpRequest *req, const AuthContext *ctx);

/* Module 5: Sales & Order Analytics */
void handle_get_analytics_summary(HttpRequest *req, const AuthContext *ctx);
void handle_get_analytics_rush_hours(HttpRequest *req, const AuthContext *ctx);
void handle_get_analytics_top_dishes(HttpRequest *req, const AuthContext *ctx);

#endif /* HANDLERS_H */
