# Build:  make          (Linux / Mac / Windows with MinGW or w64devkit)
# Clean:  make clean    (deletes the compiled files; 'make' rebuilds them)
#
# -Iinclude / -Icivetweb : where to find our headers and CivetWeb's
# -DNO_SSL -DNO_CGI      : turn off CivetWeb features we don't use (smaller build)
CC      = gcc
CFLAGS  = -O2 -Wall -Wextra -Wno-unused-parameter -Iinclude -Icivetweb -DNO_SSL -DNO_CGI

ifeq ($(OS),Windows_NT)
  # Override MYSQL_DIR when MySQL is installed in a different location.
  MYSQL_DIR ?= C:/Program Files/MySQL/MySQL Server 9.4
  CFLAGS  += -I"$(MYSQL_DIR)/include"
  LDFLAGS  = -L"$(MYSQL_DIR)/lib" -lmysql -lws2_32 -lbcrypt
  TARGET   = server.exe
else
  # mysql_config comes with libmysqlclient-dev (Linux) or mysql-client (Mac, brew)
  CFLAGS  += $(shell mysql_config --cflags 2>/dev/null || mariadb_config --cflags)
  LDFLAGS  = $(shell mysql_config --libs 2>/dev/null || mariadb_config --libs) -lpthread -ldl -lm
  TARGET   = server
endif

# Every .c file in src/ and src/handlers/ is compiled automatically,
# so a new handler file needs no Makefile change.
OBJS = $(patsubst %.c,%.o,$(wildcard src/*.c src/handlers/*.c)) civetweb/civetweb.o

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(OBJS) $(LDFLAGS) -o $(TARGET)

# CivetWeb is third-party code, so hide its warnings
civetweb/civetweb.o: civetweb/civetweb.c
	$(CC) $(CFLAGS) -w -c $< -o $@

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(OBJS) $(TARGET)
