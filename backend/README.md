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

### 시드 (로컬 MySQL에 한 번)

앱 시작 시 자동으로 넣지 않는다. 새 DB는 `schema.sql`(설문 v1·약물 예시 포함) 실행 후 시드 2개를 순서대로 실행 (모두 예시 데이터):

```text
schema.sql
docs/sql/STEP06_seed_ingredient_standards.sql
docs/sql/STEP07_seed_recommendation_rules.sql
```

`schema.sql`을 쓰지 않는 경우에만 위 STEP06·STEP07 앞에 `docs/sql/STEP02_seed_survey_v1.sql`, `docs/sql/STEP02_seed_drugs.sql`을 먼저 실행한다 (`schema.sql`과 같이 실행하면 PK 중복).

이미 예전 STEP02·STEP07 시드를 넣은 DB라면 `docs/sql/STEP12_seed_unknown_drug_classes.sql`만 추가로 실행한다 (약 이름 모름 항목·규칙).

**기존 팀원 DB**(예전 `schema.sql`로 만든 DB)는 `docs/sql/STEP13_ai_survey_setup.sql` 하나만 실행하면 된다 (MySQL Workbench 가능). AI 테이블·컬럼과 PK AUTO_INCREMENT를 맞추고, 예전 예시 설문(CONCERN 등)과 그 응답·추천을 지운 뒤 STEP02·06·07(STEP12 포함) 시드를 넣는다. STEP02·06·07·12 시드를 따로 실행할 필요 없고, 여러 번 실행해도 안전하다. 다른 팀원 테이블 데이터는 바꾸지 않는다.

V2 회원 맞춤 질문(STEP 14)을 쓰려면 기존 DB에 이어서 `docs/sql/STEP14_personalized_questions.sql`을 실행한다. **순서: STEP13 → STEP14** (맞춤 질문 회차 테이블 2개, `ai_recommendations.response_id` NULL 허용·`session_id`, `llm_call_logs.session_id`). 여러 번 실행해도 안전하다. 새 DB는 `schema.sql`에 이미 들어 있다.

테이블·컬럼은 `ddl-auto: update`로 자동 반영된다. 공유 DB용 DDL은 `docs/sql/STEPxx_*.sql`, 변경 목록은 [docs/schema-changes.md](docs/schema-changes.md).

### 서버

```powershell
# 기본: LLM = Fake (API 키 없이 동작, 고정 응답)
.\gradlew.bat bootRun

# 실제 OpenAI API (application-local-llm.yml)
.\gradlew.bat bootRun --args='--spring.profiles.active=local-llm --recommendation.pipeline-version=V4'
```

주요 설정 (기본값은 `src/main/resources/config/recommendation-defaults.properties`와 각 `*Properties` 클래스):

| 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `llm.provider` | fake | `spring-ai`면 실제 API (`local-llm` 프로필). 호출은 `LlmClient` → `SpringAiLlmClient` → Spring AI `ChatModel`(지금은 OpenAI) |
| `spring.ai.model.chat` | none | `openai`면 Spring AI가 ChatModel을 만든다 (`local-llm` 프로필). 연결 설정은 `spring.ai.openai.*` (키 `${OPENAI_API_KEY}`, 재시도 0, 타임아웃 = `llm.timeout-ms`) |
| `llm.model` / `llm.small-model` | gpt-6.1-sol / gpt-6-luna | 모델 ID는 OpenAI 모델 표 기준 |
| `spring.ai.openai.chat.reasoning-effort` | (모델 기본값 medium) | low·medium·high·xhigh·max |
| `recommendation.pipeline-version` | V0 | V0~V4, V6~V8, RULE_ONLY |
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
