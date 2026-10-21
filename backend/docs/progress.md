# AI 영양제 추천 진행 기록

프롬프트: `backend/docs/supplement-recommendation/` (STEP 01은 팀 PR #11로 대체). 실행 규칙: `RUN_OVERNIGHT.md`.

## 확인한 것

- (STEP 02 시작 시) 엔티티 20개와 `reference/erd.sql` 대조 결과. 테이블·컬럼 이름은 모두 일치. 아래 차이는 고치지 않고 기록만 한다.
  - `health_profiles.birth_year`: ERD `SMALLINT`, 엔티티 `Integer`(Hibernate가 `INT`로 생성)
  - ERD의 `DEFAULT CURRENT_TIMESTAMP`, `DEFAULT FALSE` 같은 DB 기본값은 엔티티에 선언돼 있지 않다 (JPA 감사·생성자에서 값을 채움)
  - `ai_recommendation_items.recommendation_id`: ERD에는 FK가 없지만 엔티티는 `@ManyToOne`이라 `ddl-auto`가 FK를 만든다
  - `notifications.dedup_key` 컬럼은 이미 있다 (유니크 제약은 없음)
- Gradle: `./gradlew`는 Git Bash에서 실행 권한 문제로 바로 안 돼서 `sh ./gradlew`로 실행했다 (Windows에서는 `gradlew.bat`도 가능).
- 공통 `ErrorCode.INVALID_INPUT`이 `HttpStatus.BAD_GATEWAY`(502)로 되어 있다. 기존 값 수정 금지라 그대로 두고, 설문은 새 코드 `S002`(400)를 쓴다. → 팀 협의
- 공통 `GlobalExceptionHandler`는 `BusinessException` 외 모든 예외를 500으로 돌려준다 (JSON 파싱 실패, 타입 변환 실패 포함). → 팀 협의

## 결정한 것

- 테스트 DB: H2(MySQL 모드)를 `testRuntimeOnly`로 추가 (`com.h2database:h2`, 버전은 Spring Boot 3.5.6 BOM 관리). 이유: 실제 MySQL 없이 테스트·측정을 돌리기 위해. 설정은 `src/test/resources/application.yml`만 사용하고 메인 설정은 그대로.
- 기본 `test` 태스크에서 `live`, `perf`, `eval` 태그를 제외하고, 성능 측정은 `./gradlew perfTest`로 분리.
- 임시 인증: `global/auth/CurrentUserResolver` + `@CurrentUserId` (X-User-Id 헤더). 헤더 형식이 틀리면 401(`E003`).
- 민감정보 동의 API는 `user` 도메인에 **새 클래스**(`UserConsentController`, `UserConsentService`)로 추가했다. 기존 `UserService`·`UserController`는 수정하지 않았고, `User` 엔티티에는 `agreeSensitiveConsent()` 메서드만 추가했다 (허용된 예외).
- 설문 시드는 테스트 픽스처(`src/test/resources/sql/`)와 팀 공유용(`docs/sql/STEP02_seed_*.sql`)에 같은 내용으로 두고, 앱 시작 시 자동으로 넣지 않는다. 로컬 MySQL에서 쓰려면 SQL을 직접 한 번 실행한다.
- 성분 코드 enum `IngredientCode`는 `domain/health/entity`에 두었다 (설문 검증과 추천이 같이 씀).
- `Q-SUPPLEMENTS`의 단위는 성분 기준 단위와 달라도 받는다 (STEP 06에서 단위가 다르면 `intake_status = UNKNOWN`으로 처리하기 때문).
- 출생 연도는 1900~올해 정수만 받는다 (프롬프트에 없는 검증이지만 프로필 오염 방지용 최소 검증).
- (STEP 03) WireMock `org.wiremock:wiremock-standalone:3.13.2` testImplementation 추가. 이유: 실제 API 없이 Anthropic HTTP 형식·오류·타임아웃 테스트, 이후 장애 주입.
- (STEP 03) 기본 모델 `claude-opus-5-5`, 작은 모델 `claude-haiku-5-5` (Anthropic 문서 기본 권장·현재 세대). 단가는 공식 가격표에서 2026-10-21 확인 → `perf/pricing.md`. 최종 모델은 사용자가 결정.
- (STEP 03) 공식 문서상 Opus 5.5·Sonnet 5.5는 강제 `tool_choice`와 `temperature`를 거부한다. 클라이언트는 temperature null이면 보내지 않고, 도구 강제는 `llm.anthropic.force-tool-choice`(기본 false)로 둔다.
- (STEP 03) 설정 기본값(`llm.provider=fake`)은 `@ConfigurationProperties` 기본값으로 두고, 메인 application.yml은 건드리지 않았다. 실제 API용 `application-local-llm.yml`을 새로 추가.
- (STEP 03) V0 파싱은 응답 텍스트의 첫 `{`~마지막 `}`를 Jackson으로 읽는다 ("응답 텍스트에서 JSON을 파싱"을 이렇게 해석). 코드펜스(```json)만 붙은 응답은 성공으로 본다.
- (STEP 03) `llm_call_logs`는 바깥 트랜잭션이 롤백돼도 남도록 REQUIRES_NEW로 저장. `response_id`에 FK는 걸지 않음(기록용).
- (STEP 03) 추천 저장은 LLM 호출과 분리(`RecommendationStore`)해서 LLM 대기 중 DB 트랜잭션을 잡지 않는다.

## 막힌 것

- 없음

## STEP 02 - 설문 API (2026-10-21)
- 한 일:
  - `GET /api/v1/surveys/active`, `POST /api/v1/users/me/sensitive-consent`, `POST /api/v1/surveys/{surveyId}/responses`, `GET /api/v1/drugs?keyword=`, `GET /api/v1/ingredients`
  - 미리 채우기(프로필 컬럼·질환·복용 약), 6개월 지난 프로필 `needsConfirm`, 제출 시 프로필 반영(질환·복용 약 교체), 4번 검증 항목 전부
  - 시드: 설문 v1(14문항, 선택지 37개), 약물 계열별 대표 약 10개 (예시 데이터)
  - 테스트 19개(`SurveyApiTest`) + 측정 테스트(`SurveyApiPerfTest`, `perf` 태그)
  - 프론트 연동용 명세: `backend/docs/api/survey-api.md`
- 측정 결과 요약: [STEP02-report.md](perf/STEP02-report.md). 설문 시작의 선택지 N+1을 고쳐 SQL 20 → 7(회원), 16 → 3(비회원). p95 49.5 → 25.3ms(회원 시작, H2·MockMvc 기준)
- 결정한 것 / 미룬 것:
  - `status = IN_PROGRESS`는 쓰지 않음 (제출 시점에만 행 생성)
  - 제출(회원) SQL 28개는 행별 INSERT라 남겨둠 (IDENTITY 키라 배치 불가)
  - 비회원 응답의 가입 후 연결은 범위 밖
- 다음 STEP 전에 사용자가 할 일: 없음 (실제 API 예산 0원이라 STEP 03 수동 호출은 `perf/TODO-live-runs.md`로 넘긴다)

## STEP 03 - LLM 연결 + V0 (2026-10-21)
- 한 일:
  - `infra/llm`: LlmClient / AnthropicLlmClient(RestClient) / FakeLlmClient / LoggingLlmClient, `llm_call_logs` 테이블
  - 추천 파이프라인 구조(`RecommendationPipeline`, `RecommendationInput`, `RecommendationResult`, `PipelineRegistry`), `V0Pipeline`(prompts/v0, v0.1), `RecommendationInputAssembler`
  - `POST /api/v1/recommendations`, `GET /api/v1/recommendations/{id}`, 설정 `recommendation.pipeline-version`(기본 V0)
  - `./gradlew liveTest`(실제 API, @Tag("live")) 분리
  - 테스트: AnthropicLlmClientTest(WireMock 6), LoggingLlmClientTest 3, V0PipelineTest 3, RecommendationApiTest 4 (입력 조립 회원/비회원 포함)
- 측정 결과 요약: [STEP03-report.md](perf/STEP03-report.md) — 실제 API 3회 호출은 **실제 측정 대기**([TODO-live-runs.md](perf/TODO-live-runs.md) 03-1, 03-2). 단가는 [pricing.md](perf/pricing.md)
- 결정한 것 / 미룬 것:
  - 비회원도 자기 responseId로 추천을 만들 수 있다. 남의 responseId로 호출하는 가로채기 방지는 범위 밖 (로그인 연동 시 응답 소유자 확인 필요)
  - 재시도·서킷 브레이커는 STEP 10
- 다음 STEP 전에 사용자가 할 일: (실제 측정을 원하면) API 키 설정 후 TODO-live-runs.md 03번
