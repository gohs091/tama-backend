#!/usr/bin/env bash
# 서버에서 한 번만 실행: 컨테이너끼리 통신할 공용 네트워크 생성
#   edge    : Caddy <-> Java 앱
#   backend : Java 앱 <-> MySQL/Redis (외부 접근 불가)
set -euo pipefail
docker network inspect edge >/dev/null 2>&1 || docker network create edge
docker network inspect backend >/dev/null 2>&1 || docker network create backend
echo "networks ready: edge, backend"
