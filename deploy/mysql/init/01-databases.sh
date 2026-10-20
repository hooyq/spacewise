#!/bin/bash
# 首次初始化时由 MySQL 镜像执行（仅当数据卷为空）。
# 每个服务一个库、一个同名账号，账号只对自己的库有权限（ADR-0003）。
# 修改本文件对已有数据卷无效，需要 docker compose down -v 重建（会清空数据）。
set -euo pipefail

create_service_db() {
  local name="$1" password="$2"
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE DATABASE IF NOT EXISTS ${name}_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${name}'@'%' IDENTIFIED BY '${password}';
GRANT ALL PRIVILEGES ON ${name}_db.* TO '${name}'@'%';
SQL
  echo "created database ${name}_db and user ${name}"
}

create_service_db seat     "$SEAT_DB_PASSWORD"
create_service_db room     "$ROOM_DB_PASSWORD"
create_service_db repair   "$REPAIR_DB_PASSWORD"
create_service_db platform "$PLATFORM_DB_PASSWORD"
