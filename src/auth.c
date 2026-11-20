#include "auth.h"
#include "db.h"
#include <stdio.h>
#include <string.h>

static int auth_validate_role(const HttpRequest *req, AuthContext *out_ctx, const char *required_role) {
    if (!req || !out_ctx) return 0;
    
    memset(out_ctx, 0, sizeof(AuthContext));
    
    /* Check if token was provided in Authorization header */
    if (req->auth_token[0] == '\0') {
        return 0;
    }
    
    char escaped_token[sizeof(req->auth_token) * 2];
    db_escape_string(escaped_token, req->auth_token, (unsigned long)strlen(req->auth_token));
    
    /* Verify token and role in MySQL */
    MYSQL_RES *res = db_query(
        "SELECT user_id, canteen_id, username, role FROM users "
        "WHERE token = '%s' AND (token_expires_at IS NULL OR token_expires_at > NOW()) LIMIT 1;",
        escaped_token
    );
    
    if (!res) {
        return 0;
    }
    
    MYSQL_ROW row = mysql_fetch_row(res);
    if (!row) {
        mysql_free_result(res);
        return 0;
    }
    
    const char *role = row[3] ? row[3] : "";
    if (strcmp(role, required_role) != 0) {
        mysql_free_result(res);
        return 0;
    }
    
    out_ctx->user_id = row[0] ? atoi(row[0]) : 0;
    out_ctx->canteen_id = row[1] ? atoi(row[1]) : 0;
    snprintf(out_ctx->username, sizeof(out_ctx->username), "%s", row[2] ? row[2] : "");
    snprintf(out_ctx->role, sizeof(out_ctx->role), "%s", role);
    out_ctx->is_authenticated = true;
    
    mysql_free_result(res);
    return 1;
}

int auth_validate_owner(const HttpRequest *req, AuthContext *out_ctx) {
    return auth_validate_role(req, out_ctx, "owner");
}

int auth_validate_student(const HttpRequest *req, AuthContext *out_ctx) {
    return auth_validate_role(req, out_ctx, "student");
}
