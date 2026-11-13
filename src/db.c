#include "db.h"
#include <stdarg.h>

static MYSQL *g_conn = NULL;
static DBConfig g_config;
static bool g_initialized = false;

int db_init(const DBConfig *config) {
    if (!config) return 0;
    memcpy(&g_config, config, sizeof(DBConfig));
    
    if (g_conn) {
        mysql_close(g_conn);
        g_conn = NULL;
    }
    
    g_conn = mysql_init(NULL);
    if (!g_conn) {
        fprintf(stderr, "[DB ERROR] mysql_init failed\n");
        return 0;
    }
    
    /* Enable auto-reconnect */
    bool reconnect = true;
    mysql_options(g_conn, MYSQL_OPT_RECONNECT, &reconnect);
    
    /* Connect to MySQL server */
    if (!mysql_real_connect(g_conn,
                           g_config.host,
                           g_config.user,
                           g_config.password,
                           g_config.database,
                           g_config.port,
                           NULL,
                           0)) {
        fprintf(stderr, "[DB ERROR] Connection failed: %s (Error %u)\n",
                mysql_error(g_conn), mysql_errno(g_conn));
        mysql_close(g_conn);
        g_conn = NULL;
        return 0;
    }
    
    printf("[DB SUCCESS] Connected to MySQL database '%s' at %s:%d\n",
           g_config.database, g_config.host, g_config.port);
    g_initialized = true;
    return 1;
}

void db_close(void) {
    if (g_conn) {
        mysql_close(g_conn);
        g_conn = NULL;
    }
    g_initialized = false;
}

MYSQL *db_get_connection(void) {
    if (!g_initialized) {
        return NULL;
    }
    
    /* Check liveness and reconnect if dropped */
    if (g_conn && mysql_ping(g_conn) != 0) {
        fprintf(stderr, "[DB WARN] Connection ping failed, attempting reconnect...\n");
        db_init(&g_config);
    }
    return g_conn;
}

unsigned long db_escape_string(char *to, const char *from, unsigned long length) {
    MYSQL *conn = db_get_connection();
    if (!conn || !to || !from) return 0;
    return mysql_real_escape_string(conn, to, from, length);
}

int db_execute(const char *sql_format, ...) {
    MYSQL *conn = db_get_connection();
    if (!conn) {
        fprintf(stderr, "[DB ERROR] db_execute: No active DB connection\n");
        return -1;
    }
    
    char query_buffer[4096];
    va_list args;
    va_start(args, sql_format);
    vsnprintf(query_buffer, sizeof(query_buffer), sql_format, args);
    va_end(args);
    
    if (mysql_query(conn, query_buffer) != 0) {
        fprintf(stderr, "[DB ERROR] Query failed: %s\nError: %s\n",
                query_buffer, mysql_error(conn));
        return -1;
    }
    
    return (int)mysql_affected_rows(conn);
}

MYSQL_RES *db_query(const char *sql_format, ...) {
    MYSQL *conn = db_get_connection();
    if (!conn) {
        fprintf(stderr, "[DB ERROR] db_query: No active DB connection\n");
        return NULL;
    }
    
    char query_buffer[4096];
    va_list args;
    va_start(args, sql_format);
    vsnprintf(query_buffer, sizeof(query_buffer), sql_format, args);
    va_end(args);
    
    if (mysql_query(conn, query_buffer) != 0) {
        fprintf(stderr, "[DB ERROR] Query failed: %s\nError: %s\n",
                query_buffer, mysql_error(conn));
        return NULL;
    }
    
    return mysql_store_result(conn);
}

unsigned long long db_last_insert_id(void) {
    MYSQL *conn = db_get_connection();
    if (!conn) return 0;
    return mysql_insert_id(conn);
}
