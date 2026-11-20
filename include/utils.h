/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* utils.h - helpers for reading requests and sending JSON replies (CivetWeb) */
#ifndef UTILS_H
#define UTILS_H

#include <stddef.h>
#include "civetweb.h"
#include "cJSON.h"

/* Send any JSON text with the given HTTP status */
void send_json(struct mg_connection *conn, int status, const char *json_text);

/* {"status":"error","message":"..."} */
void send_error(struct mg_connection *conn, int status, const char *message);

/* {"status":"success","message":"...","data":{...}}  (frees 'data' for you) */
void send_success(struct mg_connection *conn, int status, const char *message, cJSON *data);

/* Answer a browser's CORS preflight (OPTIONS) request */
void send_cors_preflight(struct mg_connection *conn);

/* Read the request body into a new string. Caller must free(). Never returns NULL. */
char *read_body(struct mg_connection *conn);

/* Read ?name=value from the URL. Returns 1 if found. */
int get_query_param(struct mg_connection *conn, const char *name, char *out, size_t out_size);

/* Make a join code like CANT-7K2Q */
void generate_join_code(char *out, size_t out_size);

#endif
