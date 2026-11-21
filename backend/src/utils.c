#include "utils.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

void send_json(SOCKET sock, int status_code, const char *json_str) {
    http_send_json(sock, status_code, json_str);
}

void send_error(SOCKET sock, int status_code, const char *error_message) {
    cJSON *root = cJSON_CreateObject();
    if (!root) {
        send_json(sock, status_code, "{\"status\":\"error\",\"message\":\"Internal allocation error\"}");
        return;
    }
    cJSON_AddStringToObject(root, "status", "error");
    cJSON_AddStringToObject(root, "message", error_message ? error_message : "Unknown error");
    
    char *json_str = cJSON_PrintUnformatted(root);
    send_json(sock, status_code, json_str);
    
    free(json_str);
    cJSON_Delete(root);
}

void send_success_json(SOCKET sock, int status_code, const char *message, cJSON *data) {
    cJSON *root = cJSON_CreateObject();
    if (!root) {
        if (data) cJSON_Delete(data);
        send_error(sock, 500, "Internal allocation error");
        return;
    }
    cJSON_AddStringToObject(root, "status", "success");
    if (message) {
        cJSON_AddStringToObject(root, "message", message);
    }
    if (data) {
        cJSON_AddItemToObject(root, "data", data);
    }
    
    char *json_str = cJSON_PrintUnformatted(root);
    send_json(sock, status_code, json_str);
    
    free(json_str);
    cJSON_Delete(root);
}

void generate_join_code(char *out_code, size_t max_len) {
    if (!out_code || max_len < 10) return;
    
    static int seeded = 0;
    if (!seeded) {
        srand((unsigned int)time(NULL));
        seeded = 1;
    }
    
    const char charset[] = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    int charset_size = (int)(sizeof(charset) - 1);
    
    char random_suffix[5];
    for (int i = 0; i < 4; i++) {
        random_suffix[i] = charset[rand() % charset_size];
    }
    random_suffix[4] = '\0';
    
    snprintf(out_code, max_len, "CANT-%s", random_suffix);
}

int get_query_param(const HttpRequest *req, const char *param_name, char *out_buf, size_t buf_len) {
    if (!req || !param_name || !out_buf || buf_len == 0) return 0;
    if (req->query[0] == '\0') return 0;

    char search_pattern[64];
    snprintf(search_pattern, sizeof(search_pattern), "%s=", param_name);
    
    const char *start = NULL;
    if (strncmp(req->query, search_pattern, strlen(search_pattern)) == 0) {
        start = req->query + strlen(search_pattern);
    } else {
        char mid_pattern[68];
        snprintf(mid_pattern, sizeof(mid_pattern), "&%s=", param_name);
        char *found = strstr(req->query, mid_pattern);
        if (found) {
            start = found + strlen(mid_pattern);
        }
    }

    if (!start) return 0;

    char *amp = strchr(start, '&');
    size_t val_len = amp ? (size_t)(amp - start) : strlen(start);
    if (val_len >= buf_len) val_len = buf_len - 1;

    strncpy(out_buf, start, val_len);
    out_buf[val_len] = '\0';
    return 1;
}
