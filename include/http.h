#ifndef HTTP_H
#define HTTP_H

#include <winsock2.h>
#include <windows.h>
#include <stdbool.h>
#include <stddef.h>

/* Simple, clean representation of an incoming HTTP request */
typedef struct {
    char method[16];        /* GET, POST, PUT, DELETE, PATCH, OPTIONS */
    char uri[512];          /* Full URI e.g. /api/owner/menu?id=5 */
    char path[256];         /* Path part e.g. /api/owner/menu */
    char query[256];        /* Query string e.g. id=5 */
    char auth_token[128];   /* Extracted token from Authorization header */
    char *body;             /* Pointer to request body */
    size_t body_len;        /* Length of body */
    SOCKET client_sock;     /* Client socket */
} HttpRequest;

/* Route handler function signature */
typedef void (*HttpRouteHandler)(HttpRequest *req);

/* Start the lightweight HTTP server */
int http_server_start(int port, HttpRouteHandler handler);

/* Send standard JSON response with CORS headers */
void http_send_json(SOCKET sock, int status_code, const char *json_str);

/* Send 204 No Content for OPTIONS preflight */
void http_send_cors_options(SOCKET sock);

#endif /* HTTP_H */
