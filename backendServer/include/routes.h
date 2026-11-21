/*
 * Name    : Renuka Veer
 * Roll No : 2639
 */
#ifndef ROUTES_H
#define ROUTES_H
#include "civetweb.h"

/* The single CivetWeb callback that serves every /api/ URL */
int api_handler(struct mg_connection *conn, void *cbdata);

#endif
