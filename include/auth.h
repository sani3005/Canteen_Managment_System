#ifndef AUTH_H
#define AUTH_H

#include "http.h"
#include <stdbool.h>

/* Authenticated user context */
typedef struct {
    int user_id;
    int canteen_id;
    char username[64];
    char role[16];
    bool is_authenticated;
} AuthContext;

/* Extract Bearer token from HttpRequest and validate owner privileges.
   Returns 1 if valid owner, 0 if unauthorized/forbidden */
int auth_validate_owner(const HttpRequest *req, AuthContext *out_ctx);

#endif /* AUTH_H */
