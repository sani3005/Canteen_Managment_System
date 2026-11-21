CC = tools/w64devkit/bin/gcc.exe
CFLAGS = -O2 -Wall -Wextra -Wno-unused-parameter -Iinclude -I"C:/Program Files/MySQL/MySQL Server 8.0/include"
LDFLAGS = -L"C:/Program Files/MySQL/MySQL Server 8.0/lib" -lmysql -lws2_32
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
       src/handlers/analytics_handler.c

all: copy_dll $(TARGET)

copy_dll:
	@if not exist libmysql.dll copy "C:\Program Files\MySQL\MySQL Server 8.0\lib\libmysql.dll" .

$(TARGET): $(SRCS)
	$(CC) $(CFLAGS) $(SRCS) $(LDFLAGS) -o $(TARGET)

clean:
	del /f $(TARGET)
