#ifndef DB_H
#define DB_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

#ifdef _WIN32
#include <windows.h>
#endif

/* Include MySQL C API */
#if defined(__has_include)
  #if __has_include(<mysql.h>)
    #include <mysql.h>
  #elif __has_include(<mysql/mysql.h>)
    #include <mysql/mysql.h>
  #else
    #include <mysql.h>
  #endif
#else
  #include <mysql.h>
#endif

/* Database configuration structure */
typedef struct {
    char host[128];
    int port;
    char user[64];
    char password[64];
    char database[64];
} DBConfig;

/* Initialize global DB connection */
int db_init(const DBConfig *config);

/* Close DB connection */
void db_close(void);

/* Get active MySQL handle (reconnects if disconnected) */
MYSQL *db_get_connection(void);

/* Escape SQL string to prevent injection */
unsigned long db_escape_string(char *to, const char *from, unsigned long length);

/* Execute query that returns no result set (INSERT, UPDATE, DELETE) */
int db_execute(const char *sql_format, ...);

/* Execute query and return MYSQL_RES* (caller must call mysql_free_result) */
MYSQL_RES *db_query(const char *sql_format, ...);

/* Get last inserted auto-increment ID */
unsigned long long db_last_insert_id(void);

#endif /* DB_H */
