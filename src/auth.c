/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* auth.c - owner token check against the users table */
#include "auth.h"
#include "db.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

AuthResult auth_check_owner(struct mg_connection *conn, AuthContext *out) {
    memset(out, 0, sizeof(*out));

    /* 1. Read the header: "Authorization: Bearer abc123" */
    const char *header = mg_get_header(conn, "Authorization");
    if (!header || strncmp(header, "Bearer ", 7) != 0 || header[7] == '\0') {
        return AUTH_MISSING;
    }
    const char *token = header + 7;

    /* Escape the token first: it comes from the user, and without this
     * a crafted token could change the SQL query (SQL injection). */
    char safe_token[200];
    db_escape(safe_token, sizeof(safe_token), token);

    /* 2. Look the token up in MySQL */
    MYSQL_RES *res = db_query(
        "SELECT user_id, canteen_id, username, role FROM users "
        "WHERE token = '%s' AND (token_expires_at IS NULL OR token_expires_at > NOW()) LIMIT 1;",
        safe_token);
    if (!res) return AUTH_FORBIDDEN;

    MYSQL_ROW row = mysql_fetch_row(res);
    AuthResult result = AUTH_FORBIDDEN;

    /* 3. Must exist and have role = owner */
    if (row && row[3] && strcmp(row[3], "owner") == 0) {
        out->user_id    = row[0] ? atoi(row[0]) : 0;
        out->canteen_id = row[1] ? atoi(row[1]) : 0;
        snprintf(out->username, sizeof(out->username), "%s", row[2] ? row[2] : "");
        result = AUTH_OK;
    }
    mysql_free_result(res);
    return result;
}

AuthResult auth_check_student(struct mg_connection *conn, AuthContext *out) {
    memset(out, 0, sizeof(*out));
    const char *header = mg_get_header(conn, "Authorization");
    if (!header || strncmp(header, "Bearer ", 7) != 0 || header[7] == '\0') {
        return AUTH_MISSING;
    }

    const char *token = header + 7;
    if (strlen(token) != 64) return AUTH_FORBIDDEN;
    for (size_t i = 0; i < 64; i++) {
        if (!((token[i] >= '0' && token[i] <= '9') ||
              (token[i] >= 'a' && token[i] <= 'f'))) {
            return AUTH_FORBIDDEN;
        }
    }

    char safe_token[129];
    db_escape(safe_token, sizeof(safe_token), token);
    MYSQL_RES *res = db_query(
        "SELECT student_id, canteen_id, full_name FROM students "
        "WHERE access_token = '%s' "
        "AND (token_expires_at IS NULL OR token_expires_at > NOW()) LIMIT 1;",
        safe_token);
    if (!res) return AUTH_FORBIDDEN;

    MYSQL_ROW row = mysql_fetch_row(res);
    AuthResult result = AUTH_FORBIDDEN;
    if (row) {
        out->student_id = row[0] ? atoi(row[0]) : 0;
        out->canteen_id = row[1] ? atoi(row[1]) : 0;
        snprintf(out->username, sizeof(out->username), "%s", row[2] ? row[2] : "");
        result = AUTH_OK;
    }
    mysql_free_result(res);
    return result;
}
