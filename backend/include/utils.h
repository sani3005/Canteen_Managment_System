#ifndef UTILS_H
#define UTILS_H

#include "http.h"
#include "cJSON.h"
#include <stddef.h>

/* Send a raw JSON HTTP response with CORS headers */
void send_json(SOCKET sock, int status_code, const char *json_str);

/* Send an error response formatted as {"status": "error", "message": "..."} */
void send_error(SOCKET sock, int status_code, const char *error_message);

/* Send a success response formatted as {"status": "success", "message": "...", "data": ...}
   Note: takes ownership of cJSON *data (will free it) */
void send_success_json(SOCKET sock, int status_code, const char *message, cJSON *data);

/* Generate a unique join code e.g. CANT-9021 */
void generate_join_code(char *out_code, size_t max_len);

/* Extract a query parameter from HTTP request into buffer.
   Returns 1 if found, 0 if not found */
int get_query_param(const HttpRequest *req, const char *param_name, char *out_buf, size_t buf_len);

#endif /* UTILS_H */
