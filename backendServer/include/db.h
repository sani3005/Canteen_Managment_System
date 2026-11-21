/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* db.h - small MySQL helper (one shared connection) */
#ifndef DB_H
#define DB_H

#include <stddef.h>
#ifdef _WIN32
#include <winsock2.h>
#endif
#include <mysql.h>

typedef struct {
    char host[128];
    int  port;
    char user[64];
    char password[64];
    char database[64];
} DBConfig;

int   db_init(const DBConfig *cfg);   /* connect, returns 1 on success */
void  db_close(void);

/* Safely escape a string for use inside '...' in SQL (output is cut if too long) */
void  db_escape(char *out, size_t out_size, const char *in);

/* INSERT / UPDATE / DELETE: returns affected rows, or -1 on error */
int   db_execute(const char *sql_format, ...);

/* SELECT: returns result (caller must mysql_free_result), or NULL on error */
MYSQL_RES *db_query(const char *sql_format, ...);

unsigned long long db_last_insert_id(void);

#endif
