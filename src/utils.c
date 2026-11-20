/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* utils.c - request/response helpers */
#include "utils.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MAX_BODY_SIZE (64 * 1024)

static const char *CORS_HEADERS =
    "Access-Control-Allow-Origin: *\r\n"
    "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS\r\n"
    "Access-Control-Allow-Headers: Content-Type, Authorization\r\n";

static const char *status_text(int code) {
    switch (code) {
        case 200: return "OK";
        case 201: return "Created";
        case 204: return "No Content";
        case 400: return "Bad Request";
        case 401: return "Unauthorized";
        case 403: return "Forbidden";
        case 404: return "Not Found";
        case 405: return "Method Not Allowed";
        case 409: return "Conflict";
        default:  return "Internal Server Error";
    }
}

void send_json(struct mg_connection *conn, int status, const char *json_text) {
    size_t len = strlen(json_text);
    mg_printf(conn,
        "HTTP/1.1 %d %s\r\n"
        "Content-Type: application/json; charset=utf-8\r\n"
        "Content-Length: %lu\r\n"
        "%s"
        "Connection: close\r\n\r\n",
        status, status_text(status), (unsigned long)len, CORS_HEADERS);
    mg_write(conn, json_text, len);
}

void send_error(struct mg_connection *conn, int status, const char *message) {
    cJSON *root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "status", "error");
    cJSON_AddStringToObject(root, "message", message);
    char *text = cJSON_PrintUnformatted(root);
    send_json(conn, status, text);
    free(text);
    cJSON_Delete(root);
}

void send_success(struct mg_connection *conn, int status, const char *message, cJSON *data) {
    cJSON *root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "status", "success");
    cJSON_AddStringToObject(root, "message", message);
    if (data) cJSON_AddItemToObject(root, "data", data);   /* root now owns data */
    char *text = cJSON_PrintUnformatted(root);
    send_json(conn, status, text);
    free(text);
    cJSON_Delete(root);
}

void send_cors_preflight(struct mg_connection *conn) {
    mg_printf(conn,
        "HTTP/1.1 204 No Content\r\n"
        "%s"
        "Access-Control-Max-Age: 86400\r\n"
        "Content-Length: 0\r\n"
        "Connection: close\r\n\r\n", CORS_HEADERS);
}

char *read_body(struct mg_connection *conn) {
    const struct mg_request_info *ri = mg_get_request_info(conn);
    long long len = ri->content_length;
    char *buf;

    if (len <= 0 || len > MAX_BODY_SIZE) {
        buf = (char *)malloc(1);
        buf[0] = '\0';
        return buf;
    }

    buf = (char *)malloc((size_t)len + 1);
    long long got = 0;
    while (got < len) {
        int n = mg_read(conn, buf + got, (size_t)(len - got));
        if (n <= 0) break;
        got += n;
    }
    buf[got] = '\0';
    return buf;
}

int get_query_param(struct mg_connection *conn, const char *name, char *out, size_t out_size) {
    const struct mg_request_info *ri = mg_get_request_info(conn);
    if (!ri->query_string) return 0;
    int n = mg_get_var(ri->query_string, strlen(ri->query_string), name, out, out_size);
    return n >= 0;
}

void generate_join_code(char *out, size_t out_size) {
    const char letters[] = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    char suffix[5];
    for (int i = 0; i < 4; i++) suffix[i] = letters[rand() % (int)(sizeof(letters) - 1)];
    suffix[4] = '\0';
    snprintf(out, out_size, "CANT-%s", suffix);
}
