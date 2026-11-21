/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* main.c - Canteen Owner Backend (C + CivetWeb + MySQL)
 *
 * Steps:  1. read settings (.env / environment)
 *         2. connect to MySQL
 *         3. start the CivetWeb server and register /api/
 *         4. wait until Enter is pressed, then shut down
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "civetweb.h"
#include "db.h"
#include "routes.h"

/* Read KEY=VALUE lines from a .env file into the settings */
static void load_env_file(const char *filename, DBConfig *cfg, char *port, size_t port_size)
{
    FILE *fp = fopen(filename, "r");
    if (!fp)
        return;

    char line[512];
    while (fgets(line, sizeof(line), fp))
    {
        line[strcspn(line, "\r\n")] = '\0';
        if (line[0] == '#' || line[0] == '\0')
            continue;

        char *eq = strchr(line, '=');
        if (!eq)
            continue;
        *eq = '\0';
        const char *key = line;
        const char *val = eq + 1;

        if (strcmp(key, "DB_HOST") == 0)
            snprintf(cfg->host, sizeof(cfg->host), "%s", val);
        else if (strcmp(key, "DB_PORT") == 0)
            cfg->port = atoi(val);
        else if (strcmp(key, "DB_USER") == 0)
            snprintf(cfg->user, sizeof(cfg->user), "%s", val);
        else if (strcmp(key, "DB_PASS") == 0)
            snprintf(cfg->password, sizeof(cfg->password), "%s", val);
        else if (strcmp(key, "DB_NAME") == 0)
            snprintf(cfg->database, sizeof(cfg->database), "%s", val);
        else if (strcmp(key, "PORT") == 0)
            snprintf(port, port_size, "%s", val);
    }
    fclose(fp);
    printf("[CONFIG] Loaded %s\n", filename);
}

int main(void)
{
    /* Default settings */
    DBConfig cfg;
    memset(&cfg, 0, sizeof(cfg));
    strcpy(cfg.host, "127.0.0.1");
    cfg.port = 3306;
    strcpy(cfg.user, "root");
    strcpy(cfg.database, "canteen_db");
    char http_port[16] = "8080";

    load_env_file(".env", &cfg, http_port, sizeof(http_port));

    const char *env_port = getenv("PORT");
    if (env_port && env_port[0] != '\0')
        snprintf(http_port, sizeof(http_port), "%s", env_port);

    srand((unsigned int)time(NULL)); /* for join codes */

    if (!db_init(&cfg))
    {
        fprintf(stderr, "[WARN] Could not connect to MySQL. Check .env (DB_HOST, DB_PORT, DB_USER, DB_PASS).\n");
    }

    /* CivetWeb options.
     * listening_ports : the port to listen on (from .env, default 8080).
     * num_threads = 1 : requests are handled one at a time, so the single
     * MySQL connection is never used by two requests at once (keeps code simple). */
    const char *options[] = {
        "listening_ports", http_port,
        "num_threads", "1",
        NULL};

    mg_init_library(0);
    struct mg_callbacks callbacks;
    memset(&callbacks, 0, sizeof(callbacks));

    struct mg_context *server = mg_start(&callbacks, NULL, options);
    if (!server)
    {
        fprintf(stderr, "[ERROR] Could not start server on port %s (is it already in use?)\n", http_port);
        db_close();
        return 1;
    }

    /* Every URL starting with /api/ goes to api_handler() */
    mg_set_request_handler(server, "/api/", api_handler, NULL);

    printf("====================================================\n");
    printf("  Canteen Owner Backend running on http://localhost:%s\n", http_port);
    printf("  Try: http://localhost:%s/api/health\n", http_port);
    printf("  Press Enter to stop.\n");
    printf("====================================================\n");
    getchar(); /* the program waits here until you press Enter */

    mg_stop(server);
    mg_exit_library();
    db_close();
    return 0;
}
