# 서버 한 대에 Docker Compose 로 여러 Java 앱 운영하기

```
인터넷 ──► Caddy(80/443, 자동 HTTPS) ──► tamagochi (Java)
                                    └──► 다른앱 (Java)      ← sites/ 에 파일 하나 추가
          (edge 네트워크)
          앱들 ──► MySQL / Redis  (backend 네트워크, 외부 접근 불가)
```

서버의 `/opt/servers/` 아래 구조 (이 저장소의 `deploy/server/` 를 그대로 복사한다):

```
/opt/servers/
├─ proxy/                 Caddy (도메인 -> 앱 연결, HTTPS)
│  ├─ Caddyfile
│  ├─ sites/*.caddy       앱마다 파일 하나
│  └─ .env                ACME_EMAIL, TAMA_DOMAIN
├─ infra/                 공용 MySQL + Redis
│  └─ .env                MYSQL_ROOT_PASSWORD
├─ apps/
│  └─ tamagochi/          이 앱 (이미지는 GitHub Actions 가 올린다)
│     └─ .env             DB/Redis/OpenAI 키
└─ scripts/               bootstrap.sh, add-database.sh, backup.sh
```

## 1. 서버 준비 (처음 한 번)

1. **서버 생성**: Ubuntu 22.04 LTS, 2 vCPU / 4GB RAM 이상, 디스크 30GB 이상 (Java 앱 1개당 약 0.5~0.8GB 사용).
2. **고정 IP**: AWS 라면 **탄력적 IP**를 붙인다 (껐다 켜도 주소가 바뀌지 않는다).
3. **방화벽(보안 그룹)**: 인바운드 `22`(SSH, 가능하면 내 IP만), `80`, `443` 만 연다. **3306/6379/8080 은 열지 않는다.**
4. **도메인**: 도메인의 A 레코드를 서버 IP 로 향하게 한다.
   도메인이 없으면 임시로 `15-164-1-2.sslip.io` (IP 의 `.` 를 `-` 로) 를 쓸 수 있다.
5. **Docker 설치**:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER   # 다시 로그인
   ```

## 2. 파일 올리고 기동

```bash
sudo mkdir -p /opt/servers /opt/backups && sudo chown -R $USER /opt/servers /opt/backups
# 이 저장소의 deploy/server/ 내용을 /opt/servers/ 로 복사 (scp, git clone 등)

cd /opt/servers
./scripts/bootstrap.sh                          # 공용 네트워크 edge, backend 생성

# (1) 공용 DB/Redis
cp infra/.env.example infra/.env && vi infra/.env        # MYSQL_ROOT_PASSWORD 설정
(cd infra && docker compose up -d)

# (2) tamagochi 전용 DB/계정 만들기 (비밀번호는 아래 apps/tamagochi/.env 와 같게)
./scripts/add-database.sh tamagotchi tamagotchi '<DB비밀번호>'

# (3) 앱 환경변수
cp apps/tamagochi/.env.example apps/tamagochi/.env && vi apps/tamagochi/.env

# (4) 프록시(HTTPS)
cp proxy/.env.example proxy/.env && vi proxy/.env        # ACME_EMAIL, TAMA_DOMAIN
(cd proxy && docker compose up -d)

# (5) 앱 (첫 이미지는 GitHub Actions 가 한 번 올린 뒤에 받을 수 있다)
(cd apps/tamagochi && docker compose up -d)
```

> 이미지가 **private** 이면 서버에서 한 번 로그인이 필요하다:
> `echo <read:packages 권한 PAT> | docker login ghcr.io -u <GitHub아이디> --password-stdin`
> 이미지에는 비밀 값이 들어 있지 않으므로(환경변수는 서버 `.env`), GitHub 의 패키지 설정에서 **public** 으로 바꿔도 안전하다.

## 3. GitHub Actions 연결

저장소 **Settings → Secrets and variables → Actions** 에 등록:

| 이름 | 값 |
|---|---|
| `SERVER_HOST` | 서버 IP (탄력적 IP) |
| `SERVER_USER` | SSH 사용자 (Ubuntu 라면 `ubuntu`) |
| `SERVER_SSH_KEY` | 서버 접속용 **개인키** 전체 |

이후 `main` 에 푸시하면 테스트 → 이미지 빌드/푸시 → 서버 교체가 자동으로 실행된다. (예전 EC2 용 `EC2_*`, `RDS_*`, `REDIS_HOST`, `*_API_KEY` 시크릿은 더 쓰지 않는다 — 값은 서버의 `.env` 에 둔다.)

## 4. 다른 Java 앱 추가하기

1. `apps/<새앱>/docker-compose.yml` 을 `apps/tamagochi/` 에서 복사해서 `name`, `container_name`, `image` 를 바꾼다.
2. `./scripts/add-database.sh <db> <user> <pw>` 로 DB 를 만든다.
3. `proxy/sites/<새앱>.caddy` 를 만든다:
   ```
   {$NEWAPP_DOMAIN} {
   	reverse_proxy <container_name>:<포트>
   }
   ```
   그리고 `proxy/.env` 에 `NEWAPP_DOMAIN=...` 을 추가한다.
4. `(cd proxy && docker compose up -d --force-recreate)` 로 Caddy 를 다시 읽힌다. 새 앱은 `docker compose up -d`.

## 5. 운영

```bash
docker ps                                   # 전체 상태
docker logs -f tamagochi                    # 앱 로그
docker logs --since 1h tamagochi | grep -E "ERROR|손그림 분석|합체 방식"
(cd /opt/servers/apps/tamagochi && docker compose restart)

# 이전 버전으로 되돌리기 (이미지에 커밋 해시 태그가 붙어 있다)
cd /opt/servers/apps/tamagochi
APP_IMAGE=ghcr.io/gohs091/tama-backend:<커밋해시> docker compose up -d
```

### 백업 (매일 새벽 4시, 14일 보관)
```bash
crontab -e
0 4 * * * /opt/servers/scripts/backup.sh >> /opt/backups/backup.log 2>&1
```
백업 파일은 같은 서버에만 두면 서버가 사라질 때 같이 사라진다. 가끔 다른 곳(S3 등)으로도 복사해 두는 것을 권장한다.

## 6. 장애를 빨리 알기
- 서버가 죽으면 바로 알 수 있게 **업타임 모니터링**(UptimeRobot 등 무료 서비스)을 `https://<도메인>/api/tamagotchi/ping` 같은 주소에 걸어 둔다 (응답이 4xx 여도 서버가 살아 있다는 뜻이다).
- `restart: always` 이므로 서버가 재부팅되거나 컨테이너가 죽어도 자동으로 다시 뜬다.
