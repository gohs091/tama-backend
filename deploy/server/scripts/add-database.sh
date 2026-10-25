#!/usr/bin/env bash
# 새 Java 앱용 DB와 전용 계정을 만든다. 사용법: ./add-database.sh <db이름> <사용자> <비밀번호>
set -euo pipefail
[ $# -eq 3 ] || { echo "usage: $0 <database> <user> <password>"; exit 1; }
DB="$1"; USER="$2"; PASS="$3"
docker exec -i mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' <<SQL
CREATE DATABASE IF NOT EXISTS \`${DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${USER}'@'%' IDENTIFIED BY '${PASS}';
ALTER USER '${USER}'@'%' IDENTIFIED BY '${PASS}';
GRANT ALL PRIVILEGES ON \`${DB}\`.* TO '${USER}'@'%';
FLUSH PRIVILEGES;
SQL
echo "database '${DB}' / user '${USER}' ready"
