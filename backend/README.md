# backend - AI 영양제 추천 (설문 · 추천 파이프라인)

팀 공통 README는 저장소 루트 `README.md`. 이 문서는 AI 추천 기능(설문 → 추천) 실행·테스트 방법이다.
진행 기록은 [docs/progress.md](docs/progress.md).

## 1. 실행

### 필요한 것

| 항목 | 용도 | 비고 |
| --- | --- | --- |
| Java 21 | 빌드·실행 | Spring Boot 4.1.1 (Jackson 3) |
| MySQL | 로컬 DB | `.env`의 `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD` (팀 공통 설정) |
| `OPENAI_API_KEY` | 실제 OpenAI API (Spring AI 2.0.1) | `backend/.env`(Git 제외)에 `OPENAI_API_KEY=...` 한 줄, 또는 OS 환경변수. `bootRun`·`liveTest` 모두 읽는다. `local-llm` 프로필에서만 사용. 코드·yml에 키를 쓰지 않는다 |
| Redis (선택) | 결과 캐시·큐를 Redis로 쓸 때 | 기본은 메모리. `docker run -d -p 6379:6379 redis:7-alpine` |
| Docker (선택) | Redis | |

### DB 준비 (로컬 MySQL)

1. 서버를 한 번 실행해서 테이블을 만든다 (`ddl-auto: update`). 이미 실행해 둔 DB면 건너뛴다
2. `docs/sql/AI_SETUP.sql` 하나를 실행한다 (MySQL Workbench에서 파일을 열어 전체 실행해도 된다)
3. `docs/sql/AI_SETUP_2.sql`을 실행한다 (리뷰 제품 추천 데이터 갱신: 약사 쇼츠 15개 추가, 비약사 채널 제외. 설문 응답·추천 기록은 지우지 않는다)

```powershell
# 각자 PC의 PowerShell (MySQL 클라이언트가 있을 때). Workbench로 실행해도 같다
mysql -u root -p --default-character-set=utf8mb4 yakssok < backend/docs/sql/AI_SETUP.sql
mysql -u root -p --default-character-set=utf8mb4 yakssok < backend/docs/sql/AI_SETUP_2.sql
```

- AI 테이블 16개(설문, 약물, 성분 기준값, 추천 규칙, AI 추천·맞춤 질문 기록, LLM 호출 로그, 리뷰 제품 추천)를 **지우고 다시 만든 뒤** 기준 데이터를 넣는다. 회원 등 예시 데이터는 넣지 않는다
- 넣는 데이터: 설문 v1, 약물 마스터, 성분 기준값(2025 한국인 영양소 섭취기준, 성인), 추천 규칙 81개, 리뷰 제품 87개·언급 227·점수 78
- 다른 팀원 테이블(users, health_profiles, user_*, catalog_products, intake_*, notifications 등)의 데이터는 지우지 않는다. catalog_products에는 리뷰 제품 컬럼·행만 추가된다
- 실행하면 각자 DB에 쌓인 **설문 응답·AI 추천 기록은 지워진다** (회원 계정·복용 약·질환은 남는다)
- 여러 번 실행해도 결과가 같다. 예전 `STEP02~18` 파일은 실행하지 않는다 (`docs/sql/archive/`에 기록만 남김)

테이블·컬럼은 `ddl-auto: update`로 자동 반영된다. 변경 목록은 [docs/schema-changes.md](docs/schema-changes.md).

### 서버

```powershell
# 기본: LLM = Fake (API 키 없이 동작, 고정 응답)
.\gradlew.bat bootRun

# 실제 OpenAI API (application-local-llm.yml)
.\gradlew.bat bootRun --args='--spring.profiles.active=local-llm'
```

주요 설정 (기본값은 `src/main/resources/config/recommendation-defaults.properties`와 각 `*Properties` 클래스):

| 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `llm.provider` | fake | `spring-ai`면 실제 API (`local-llm` 프로필). 호출은 `LlmClient` → `SpringAiLlmClient` → Spring AI `ChatModel`(지금은 OpenAI) |
| `spring.ai.model.chat` | none | `openai`면 Spring AI가 ChatModel을 만든다 (`local-llm` 프로필). 연결 설정은 `spring.ai.openai.*` (키 `${OPENAI_API_KEY}`, 재시도 0, 타임아웃 = `llm.timeout-ms`) |
| `llm.model` / `llm.small-model` | gpt-6.1-sol / gpt-6-luna | 모델 ID는 OpenAI 모델 표 기준 |
| `spring.ai.openai.chat.reasoning-effort` | (모델 기본값 medium) | low·medium·high·xhigh·max |
| `recommendation.pipeline-version` | V4 | V0~V4, V6~V8, RULE_ONLY (2026-10-27 V0 → V4: 설정이 없어도 안전 규칙 적용) |
| `recommendation.api-mode` | sync | `queue`면 202 접수 + 워커 |
| `recommendation.worker.concurrency` | 4 | 큐 워커 = LLM 동시 호출 상한 (계정 rate limit에 맞춤) |
| `recommendation.cache.store` / `recommendation.queue.store` | memory | `redis` |
| `llm.resilience.enabled` | false | 재시도·서킷·Bulkhead·규칙 폴백 (V10). 켤 때 `llm.timeout-ms=15000` 권장 |

API 명세: [docs/api/survey-api.md](docs/api/survey-api.md), [docs/api/recommendation-api.md](docs/api/recommendation-api.md). 단계별 지연: `/actuator/prometheus`의 `recommendation_stage_seconds`.

## 2. 테스트

```powershell
.\gradlew.bat test          # 실제 MySQL·API 없이 H2 + Fake LLM + WireMock
$env:REDIS_HOST="localhost"; .\gradlew.bat test   # Redis 실연결 테스트까지
.\gradlew.bat liveTest      # 실제 OpenAI API 1회 (비용 발생, 1센트 미만)
```
