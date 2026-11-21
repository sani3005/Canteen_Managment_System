#include "http.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void http_send_json(SOCKET sock, int status_code, const char *json_str) {
    if (sock == INVALID_SOCKET) return;
    
    const char *status_text = "OK";
    if (status_code == 201) status_text = "Created";
    else if (status_code == 204) status_text = "No Content";
    else if (status_code == 400) status_text = "Bad Request";
    else if (status_code == 401) status_text = "Unauthorized";
    else if (status_code == 403) status_text = "Forbidden";
    else if (status_code == 404) status_text = "Not Found";
    else if (status_code == 409) status_text = "Conflict";
    else if (status_code == 500) status_text = "Internal Server Error";

    size_t body_len = json_str ? strlen(json_str) : 0;
    char header_buf[1024];
    int header_len = snprintf(header_buf, sizeof(header_buf),
        "HTTP/1.1 %d %s\r\n"
        "Content-Type: application/json; charset=utf-8\r\n"
        "Content-Length: %lu\r\n"
        "Access-Control-Allow-Origin: *\r\n"
        "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS\r\n"
        "Access-Control-Allow-Headers: Content-Type, Authorization\r\n"
        "Connection: close\r\n\r\n",
        status_code, status_text, (unsigned long)body_len
    );

    send(sock, header_buf, header_len, 0);
    if (body_len > 0) {
        send(sock, json_str, (int)body_len, 0);
    }
}

void http_send_cors_options(SOCKET sock) {
    const char *resp =
        "HTTP/1.1 204 No Content\r\n"
        "Access-Control-Allow-Origin: *\r\n"
        "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS\r\n"
        "Access-Control-Allow-Headers: Content-Type, Authorization\r\n"
        "Access-Control-Max-Age: 86400\r\n"
        "Content-Length: 0\r\n"
        "Connection: close\r\n\r\n";
    send(sock, resp, (int)strlen(resp), 0);
}

static void parse_request(char *raw, SOCKET sock, HttpRequest *req) {
    memset(req, 0, sizeof(HttpRequest));
    req->client_sock = sock;

    /* 1. Parse Request Line: METHOD URI */
    char proto[32] = {0};
    if (sscanf(raw, "%15s %511s %31s", req->method, req->uri, proto) < 2) {
        return;
    }

    /* 2. Separate Path and Query string */
    char *qmark = strchr(req->uri, '?');
    if (qmark) {
        size_t path_len = (size_t)(qmark - req->uri);
        if (path_len >= sizeof(req->path)) path_len = sizeof(req->path) - 1;
        strncpy(req->path, req->uri, path_len);
        req->path[path_len] = '\0';
        snprintf(req->query, sizeof(req->query), "%s", qmark + 1);
    } else {
        snprintf(req->path, sizeof(req->path), "%s", req->uri);
        req->query[0] = '\0';
    }

    /* 3. Extract Authorization header */
    char *auth_ptr = strstr(raw, "\r\nAuthorization: ");
    if (!auth_ptr) auth_ptr = strstr(raw, "\r\nauthorization: ");
    if (auth_ptr) {
        auth_ptr += 17; /* Skip "\r\nAuthorization: " */
        if (strncmp(auth_ptr, "Bearer ", 7) == 0) {
            auth_ptr += 7;
            char *line_end = strstr(auth_ptr, "\r\n");
            size_t token_len = line_end ? (size_t)(line_end - auth_ptr) : strlen(auth_ptr);
            if (token_len >= sizeof(req->auth_token)) token_len = sizeof(req->auth_token) - 1;
            strncpy(req->auth_token, auth_ptr, token_len);
            req->auth_token[token_len] = '\0';
        }
    }

    /* 4. Find Body payload (after \r\n\r\n) */
    char *body_marker = strstr(raw, "\r\n\r\n");
    if (body_marker) {
        req->body = body_marker + 4;
        req->body_len = strlen(req->body);
    } else {
        req->body = "";
        req->body_len = 0;
    }
}

int http_server_start(int port, HttpRouteHandler handler) {
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        fprintf(stderr, "[HTTP ERROR] WSAStartup failed: %d\n", WSAGetLastError());
        return 0;
    }

    SOCKET server_sock = socket(AF_INET, SOCK_STREAM, 0);
    if (server_sock == INVALID_SOCKET) {
        fprintf(stderr, "[HTTP ERROR] socket creation failed: %d\n", WSAGetLastError());
        WSACleanup();
        return 0;
    }

    /* Allow fast port reuse */
    int opt = 1;
    setsockopt(server_sock, SOL_SOCKET, SO_REUSEADDR, (const char *)&opt, sizeof(opt));

    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = INADDR_ANY;
    server_addr.sin_port = htons((unsigned short)port);

    if (bind(server_sock, (struct sockaddr *)&server_addr, sizeof(server_addr)) == SOCKET_ERROR) {
        fprintf(stderr, "[HTTP ERROR] bind failed on port %d: %d\n", port, WSAGetLastError());
        closesocket(server_sock);
        WSACleanup();
        return 0;
    }

    if (listen(server_sock, SOMAXCONN) == SOCKET_ERROR) {
        fprintf(stderr, "[HTTP ERROR] listen failed: %d\n", WSAGetLastError());
        closesocket(server_sock);
        WSACleanup();
        return 0;
    }

    printf("[SERVER] Lightweight C HTTP server listening on http://0.0.0.0:%d\n", port);
    printf("[SERVER] Ready to receive owner requests. Press Ctrl+C to exit.\n");

    /* Client connection loop */
    while (1) {
        struct sockaddr_in client_addr;
        int client_len = sizeof(client_addr);
        SOCKET client_sock = accept(server_sock, (struct sockaddr *)&client_addr, &client_len);
        if (client_sock == INVALID_SOCKET) {
            continue;
        }

        char buffer[16384];
        int bytes_received = recv(client_sock, buffer, sizeof(buffer) - 1, 0);
        if (bytes_received > 0) {
            buffer[bytes_received] = '\0';
            HttpRequest req;
            parse_request(buffer, client_sock, &req);
            
            if (strcmp(req.method, "OPTIONS") == 0) {
                http_send_cors_options(client_sock);
            } else if (handler) {
                handler(&req);
            }
        }
        closesocket(client_sock);
    }

    closesocket(server_sock);
    WSACleanup();
    return 1;
}
