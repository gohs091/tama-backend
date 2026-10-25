# tama-backend 인수인계

끄적이 키우기(앱인토스 다마고치)의 백엔드. Spring Boot 3.5 / Java 17 / MySQL / Redis.
프론트: [tama-frontend](https://github.com/gohs091/tama-frontend)

## 로컬 실행 (Windows 포함)

필요: JDK 17, Docker Desktop

```bash
docker compose up -d                       # 개발용 MySQL(3307), Redis(6379)
copy src\main\resources\application-local.yml.example src\main\resources\application-local.yml   # 키를 채운다
./gradlew bootRun --args="--spring.profiles.active=local"      # http://localhost:8080
./gradlew test                             # 전체 테스트 (TamagochiApplicationTests 는 위 MySQL/Redis 필요)
```

`application-local.yml` 은 비밀 값(OpenAI 키)이 들어가므로 **저장소에 올리지 않는다**(.gitignore). 새 PC 에서는 예시 파일로 다시 만든다.

## 운영 환경변수 (`deploy/server/apps/tamagochi/.env`)

| 이름 | 설명 |
|---|---|
| `RDS_ENDPOINT` / `RDS_USERNAME` / `RDS_PASSWORD` | MySQL (Docker 구성에서는 `mysql`) |
| `REDIS_HOST` | Redis (Docker 구성에서는 `redis`) |
| `IMAGE_PROVIDER` | `openai`(기본) / `stability`(롤백용) |
| `OPENAI_API_KEY` | 이미지 생성 + 손그림 분석 |
| `ANTHROPIC_API_KEY`, `STABILITY_API_KEY` | 선택 (비워도 기동됨) |

## 핵심 설계

**이미지 생성** (`service/character/ImageGenerationService`)
1. `DoodleSubjectService`: 비전 모델(`gpt-4.1`)로 "손그림이 무엇인가"(subject/kind/parts) 분석. 실패하면 일반 프롬프트.
2. `FusionMode`: 그림 + 팩션 합체 방식을 시드로 선택 — SELF(그림 자체가 몸) / HELD(쥐고 있음) / FEATURE(몸의 일부) / MOUNT(탈것). 유체에서 정하고 성체/완전체는 유체 이미지를 참조해 같은 구도로 성장.
3. 프롬프트: 픽셀 아트 스타일 지시를 **맨 앞**에(뒤에 두면 벡터 그림이 나옴) + 팩션 색상 + 팩션 테마(`getFactionThemeMotifs`, 디지몬 소속 필드 컨셉) + 단계별 반영 강도(유체 20% / 성체 50% / 완전체 80%).
4. `gpt-image-1-mini` 로 생성 → 알파 이진화(`binarizeAlpha`)로 반투명 번짐 제거 → 투명 PNG.
5. `ImageQuotaService`: 유저별 하루 8회 / 전체 2000회 (Redis). 로컬은 `image.quota-enabled=false`.
6. 작업 상태는 비동기 시작 **전에** 저장(`CharacterController`) — 큐 대기 중 'Job not found' 방지.

**생존 규칙** (`domain/tamagotchi/Tamagotchi.applyTime`, 설정 `tamagotchi.vitals.*`)
- 포만감 6분당 1, 파워 12분당 1 감소. 0인 채 4시간이면 사망. 똥 60분마다 +1, 8개면 사망.
- 알은 대상 아님, 수면 중 소진 없음, 사망은 `deadAt` 으로 저장. 서버가 꺼져 있던 시간은 반영하지 않음(`max-tick-gap-minutes`).
- `reset` 은 삭제 후 삽입이 아니라 기존 행을 제자리에서 초기화(유니크 제약 충돌 방지).

## 배포

옛 EC2(`15.164.224.152`)는 죽어 있어서 **한 서버에 Docker Compose 로 여러 Java 앱을 운영하는 방식**으로 옮기는 중이다.
구성/명령은 [`deploy/README.md`](../deploy/README.md). `main` 에 푸시하면 GitHub Actions 가 테스트 → 이미지 빌드/푸시(GHCR) → 서버 교체를 실행한다
(필요 시크릿: `SERVER_HOST`, `SERVER_USER`, `SERVER_SSH_KEY`). 서버/도메인이 준비되기 전에는 마지막 배포 단계가 실패한다.

## TODO

1. 새 서버 생성(탄력적 IP, 도메인, HTTPS) 후 GitHub 시크릿 등록, 프론트 `.env` 주소 교체.
2. 토스 로그인 연동(서버 검증)과 포인트 적립 서버 검증 — 지금은 클라이언트가 보낸 `tossUserId`/금액을 신뢰한다.
3. 이미지 생성 스레드풀(`AsyncConfig`: 코어 2, 큐 20) 확대.
4. 한도 초과(429) 시 프론트 안내 문구, 거부된 요청은 카운트하지 않기.
5. 팩션 테마 확정: `dark-area` 는 사용자가 준 설명(명계, 마왕/마수형)대로 반영, `trash` 는 앱 고유(재활용 쓰레기 콜라주) — 나무위키 설명 확인 필요.
6. 성체/완전체에서 사용자 그림의 가장 눈에 띄는 요소 하나는 남기도록 하는 일반 규칙(네이처/다크 에리어 완전체에서 그림이 밀려나는 현상).
7. 테스트용 `force-evolve` 엔드포인트(인증 없음)는 로컬 작업물로만 있었고 커밋하지 않았다 — 운영에 노출하지 말 것.
