CC = gcc.exe
CFLAGS = -O2 -Wall -Wextra -Wno-unused-parameter -Iinclude -I"C:/Program Files/MySQL/MySQL Server 9.4/include"
LDFLAGS = -L"C:/Program Files/MySQL/MySQL Server 9.4/lib" -lmysql -lws2_32 -lbcrypt
TARGET = server.exe

SRCS = src/main.c \
       src/http_server.c \
       src/db.c \
       src/auth.c \
       src/utils.c \
       src/cJSON.c \
       src/handlers/canteen_handler.c \
       src/handlers/menu_handler.c \
       src/handlers/order_handler.c \
       src/handlers/otp_handler.c \
       src/handlers/analytics_handler.c \
       src/handlers/user_handler.c

all: copy_dll $(TARGET)

copy_dll:
	@if not exist libmysql.dll copy "C:\Program Files\MySQL\MySQL Server 9.4\lib\libmysql.dll" .
	@if not exist libssl-3-x64.dll copy "C:\Program Files\MySQL\MySQL Server 9.4\bin\libssl-3-x64.dll" .
	@if not exist libcrypto-3-x64.dll copy "C:\Program Files\MySQL\MySQL Server 9.4\bin\libcrypto-3-x64.dll" .

$(TARGET): $(SRCS)
	$(CC) $(CFLAGS) $(SRCS) $(LDFLAGS) -o $(TARGET)

clean:
	del /f $(TARGET)
