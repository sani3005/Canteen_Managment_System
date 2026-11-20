/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
/* auth.h - checks the "Authorization: Bearer <token>" header */
#ifndef AUTH_H
#define AUTH_H

#include "civetweb.h"

typedef struct {
    int  user_id;
    int  canteen_id;
    char username[64];
} AuthContext;

typedef enum {
    AUTH_OK,          /* valid owner token                      */
    AUTH_MISSING,     /* no token sent  -> HTTP 401             */
    AUTH_FORBIDDEN    /* bad token / not an owner -> HTTP 403   */
} AuthResult;

AuthResult auth_check_owner(struct mg_connection *conn, AuthContext *out);

#endif
