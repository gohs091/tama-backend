#!/usr/bin/env bash
# MySQL 전체 백업(압축) + 오래된 백업 삭제. cron 으로 매일 실행하는 것을 권장 (README 참고)
set -euo pipefail
BACKUP_DIR="${BACKUP_DIR:-/opt/backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
mkdir -p "$BACKUP_DIR"
OUT="$BACKUP_DIR/mysql-$(date +%F-%H%M).sql.gz"
docker exec mysql sh -c 'exec mysqldump --all-databases --single-transaction --routines -uroot -p"$MYSQL_ROOT_PASSWORD"' | gzip > "$OUT"
find "$BACKUP_DIR" -name 'mysql-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "backup done: $OUT ($(du -h "$OUT" | cut -f1))"
