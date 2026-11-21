/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* db.c - MySQL helper functions */
#include "db.h"
#include <stdio.h>
#include <string.h>
#include <stdarg.h>

/* One shared connection for the whole program (g_ = global).
 * Safe because the server handles one request at a time. */
static MYSQL   *g_conn = NULL;
static DBConfig g_cfg;
static int      g_ready = 0;

/* Open a fresh connection using the saved settings */
static int connect_now(void) {
    if (g_conn) { mysql_close(g_conn); g_conn = NULL; }

    g_conn = mysql_init(NULL);
    if (!g_conn) return 0;

    if (!mysql_real_connect(g_conn, g_cfg.host, g_cfg.user, g_cfg.password,
                            g_cfg.database, (unsigned int)g_cfg.port, NULL, 0)) {
        fprintf(stderr, "[DB] Connection failed: %s\n", mysql_error(g_conn));
        mysql_close(g_conn);
        g_conn = NULL;
        return 0;
    }
    return 1;
}

int db_init(const DBConfig *cfg) {
    g_cfg = *cfg;
    g_ready = connect_now();
    if (g_ready) printf("[DB] Connected to '%s' at %s:%d\n", g_cfg.database, g_cfg.host, g_cfg.port);
    return g_ready;
}

void db_close(void) {
    if (g_conn) mysql_close(g_conn);
    g_conn = NULL;
    g_ready = 0;
}

/* Returns the connection, reconnecting if MySQL dropped it */
static MYSQL *get_conn(void) {
    if (!g_conn || mysql_ping(g_conn) != 0) {
        if (!connect_now()) return NULL;
        g_ready = 1;
    }
    return g_conn;
}

void db_escape(char *out, size_t out_size, const char *in) {
    MYSQL *c = get_conn();
    if (out_size == 0) return;
    out[0] = '\0';
    if (!c || !in || out_size < 2) return;

    /* worst case every character doubles, so only take what fits */
    size_t n = strlen(in);
    size_t max_in = (out_size - 1) / 2;
    if (n > max_in) n = max_in;
    mysql_real_escape_string(c, out, in, (unsigned long)n);
}

/* Build the SQL text and run it. Returns 1 if the query ran OK. */
static int run_sql(MYSQL *c, const char *fmt, va_list args) {
    char sql[4096];
    vsnprintf(sql, sizeof(sql), fmt, args);
    if (mysql_query(c, sql) != 0) {
        fprintf(stderr, "[DB] Query failed: %s\n", mysql_error(c));
        return 0;
    }
    return 1;
}

int db_execute(const char *sql_format, ...) {
    MYSQL *c = get_conn();
    if (!c) return -1;
    va_list args;
    va_start(args, sql_format);
    int ok = run_sql(c, sql_format, args);
    va_end(args);
    return ok ? (int)mysql_affected_rows(c) : -1;
}

MYSQL_RES *db_query(const char *sql_format, ...) {
    MYSQL *c = get_conn();
    if (!c) return NULL;
    va_list args;
    va_start(args, sql_format);
    int ok = run_sql(c, sql_format, args);
    va_end(args);
    return ok ? mysql_store_result(c) : NULL;
}

unsigned long long db_last_insert_id(void) {
    return g_conn ? mysql_insert_id(g_conn) : 0;
}
