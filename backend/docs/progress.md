# AI 영양제 추천 진행 기록

> 성능·부하 측정 자료(`docs/perf/`, `perf/`), 부하·평가 테스트 코드(`perf`·`eval` 태그, `perfTest`·`evalRun` 태스크)는 저장소에서 뺐다 (로컬에서만 사용). 아래에 나오는 해당 경로·명령은 작업 기록용이다.

## 밤새 실행 요약 (2026-10-21)

**실제 Claude API는 한 번도 호출하지 않았다** (예산 0달러). 품질·비용의 실제 수치는 전부 "실제 측정 대기"이고, 명령은 `perf/TODO-live-runs.md`에 STEP 순서로 있다. 최종 정리: [perf/SUMMARY.md](perf/SUMMARY.md).

### 1. 완료한 STEP과 커밋 (브랜치 `feat/recommendation-pipeline`, push 안 함)

| STEP | 커밋 | 비고 |
| --- | --- | --- |
| 프롬프트 | 1acb00a [DOCS] AI 추천 작업 프롬프트 추가 | |
| 02 설문 API | ed5c6ee | 테스트 19개, 선택지 N+1 제거 (SQL 20 → 7) |
| 03 LLM 연결 + V0 | c1dd2a2 | WireMock으로 HTTP 형식 검증, 실제 3회 호출 대기 |
| 04 측정 인프라·평가셋 | 108ffb4 | 평가셋 32케이스(검토 전), evalRun, Fake 러너 검증 |
| 05 V1 | 0ab6f63 | tool_choice 강제 대신 auto + strict (Opus 5.5 제약) |
| 06 V2 | 550ced1 | 기준표 6행만 (나머지 TODO) |
| 07 V3·V4 | faf9288 | 규칙 27개 각각 테스트 |
| 08 V6·V7·V8 | 51270f8 | in-JVM 부하로 캐시 효과 실측 (k6 대체) |
| 09 V9 | 784dc61 | 3구조 × 3시나리오 in-JVM 3회. STEP 10 코드 일부가 같이 들어감 |
| 10 V10 | c854b33 | 장애 주입 8시나리오 in-JVM |
| 11 최종 정리 | 이 요약과 같은 커밋 | SUMMARY.md 80개 수치 results.csv 대조 일치, 차트 3개 |

모든 STEP에서 `./gradlew test` 통과 후 커밋했다.

### 2. 막힌 STEP과 이유

- 막혀서 멈춘 STEP은 없다. 다만 아래는 환경 때문에 **대체 방식**으로 했다
  - 실제 API 측정 전부 (STEP 03~08 품질·비용·토큰): 예산 0달러 → Fake로 러너·파이프라인 동작만 확인, 실제 측정 대기
  - k6 부하 테스트 (STEP 08~10): k6 미설치 → 스크립트만 만들고, 대신 실제 Tomcat + WireMock(3초) in-JVM 부하 테스트로 측정
  - rate limit 확인 (STEP 09): 콘솔 접근 불가 → 워커 수 기본 4, 측정은 20
  - 스레드 수 측정 (STEP 09): 같은 JVM 측정 방식 문제로 실패, 비교에 쓰지 않음
- Redis: Docker로 `redis:7-alpine`을 잠깐 띄워 Redis 캐시·큐 실연결 테스트를 통과시킨 뒤 컨테이너를 내렸다 (기본값은 메모리라 Redis 없이도 앱·테스트가 돈다)

### 3. 팀과 협의할 것

| 대상 | 내용 |
| --- | --- |
| 채원 (화면) | 설문 API 명세 `docs/api/survey-api.md`, 추천 API 명세 `docs/api/recommendation-api.md` (200/202 + 폴링, items/excludedItems, warnings, fallback notice) |
| 팀 전체 | 추가 라이브러리: H2(testRuntimeOnly), WireMock 3.13.2(testImplementation), spring-boot-starter-actuator + micrometer-registry-prometheus, spring-boot-starter-data-redis, resilience4j-spring-boot3 2.4.0 |
| 팀 전체 | 스키마 변경 SQL (`docs/sql/`, 목록 `docs/schema-changes.md`): llm_call_logs, ingredient_standards, recommendation_rules 새 테이블 / ai_recommendation_items 컬럼 8개 / ai_recommendations 컬럼 4개 + 시드 SQL |
| 응진 (알림) | `notifications.dedup_key` 유니크 제약 제안 `docs/sql/STEP09_proposal_notification_dedup.sql` (적용 안 함) |
| 주연 (user) | `User.agreeSensitiveConsent()` 메서드 추가, user 도메인에 새 클래스(UserConsentController/Service) 추가 |
| 팀 전체 | `ErrorCode.INVALID_INPUT`이 502로 되어 있음, `GlobalExceptionHandler`가 파싱 오류를 500으로 처리, `spring.jpa.open-in-view`(LLM 대기 중 커넥션 점유 가능), 루트 README는 건드리지 않고 `backend/README.md`를 새로 만듦 |

### 4. 아침에 할 일

1. **평가셋 검토**: `backend/src/test/resources/eval/cases.json` 확인 후 `meta.reviewed`를 `true`로. 특히 `meta.goalMayInclude`(원하는 효과 → 성분)는 출처가 없다
2. **`reference/domain-rules.md` 2번 표의 TODO 수치 입력** (2025 한국인 영양소 섭취기준 원문) → `docs/sql/STEP06_seed_ingredient_standards.sql`과 테스트 픽스처 `src/test/resources/sql/seed-ingredient-standards.sql`에 행 추가
3. **실제 API 실행**: `ANTHROPIC_API_KEY` 설정 후 `backend/docs/perf/TODO-live-runs.md`의 명령을 STEP 순서대로 (먼저 confirm 없이 예상 비용 확인). 사용할 모델도 결정 (기본 claude-opus-5-5)
4. Anthropic Console에서 rate limit 확인 → `recommendation.worker.concurrency`, `llm.resilience.bulkhead-max-concurrent` 조정
5. (선택) k6 설치 후 WireMock 부하 테스트 (TODO 08-4, 09-1, 10-1)

---

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

- (STEP 04) Actuator + `micrometer-registry-prometheus` implementation 추가 (Spring Boot BOM 버전). 이유: 단계별 지연 Timer와 /actuator/prometheus.
- (STEP 04) 노출 설정은 application.yml 대신 `resources/config/recommendation-defaults.properties`(@PropertySource, 우선순위가 application.yml보다 낮음)에 두었다.
- (STEP 04) 평가 러너는 별도 sourceSet 대신 `@Tag("eval")` 테스트 + `evalRun` Gradle 태스크로 분리. 실제 API(`-Pllm=anthropic`, 기본)만 `-Pconfirm=true`를 요구하고, Fake 모드는 비용이 없어 confirm 없이 실행된다.
- (STEP 04) Fake 실행은 두 종류: `fake`(정답, 러너 검증), `fake-noisy`(시드 고정 오답, 규칙 검증 동작 확인). results.csv label에 fake가 붙고 note에 "실제 측정값 아님"을 적는다.
- (STEP 04) 지표 해석: precision은 합산, dose_error 분모는 추천된 항목만, reproducibility는 첫 결과 포함 (STEP04-report.md "지표 계산에서 정한 것").

- (STEP 05) V1은 `tool_choice` 강제 대신 auto + strict 도구 + 프롬프트 지시 + "도구 미호출도 검증 실패로 재요청"으로 구현 (Opus 5.5/Sonnet 5.5가 강제 tool_choice를 400으로 거부). 강제가 되는 모델은 `llm.anthropic.force-tool-choice=true`.
- (STEP 05) 재요청은 이전 대화(assistant tool_use) 없이 원래 사용자 메시지 + 실패 이유를 붙인 단일 메시지로 보낸다 (구조 단순화). 재요청도 실패하면 재요청 로그 행을 PARSE_FAIL로 바꾼다.
- (STEP 05) V1~V8은 `StructuredPipeline` 하나에 버전별 Options·훅(extraUserText, postProcess, chooseModel, promptCache)을 두고, 버전마다 작은 클래스(V1Pipeline …)를 빈으로 둔다.

- (STEP 06) 현재 섭취량이 없는 성분은 UNKNOWN + 권장량 전체(프롬프트대로). 단위가 다르면 변환 없이 UNKNOWN. 같은 성분 여러 항목은 합산. 나이를 모르면(출생 연도 없음) 나이 제한이 없는 행만 쓴다 → 현재 시드는 모두 19+라 용량 없음.
- (STEP 06) 철분 행은 권장량(8mg)은 있고 상한이 TODO라 upper_limit NULL로 넣었다 ("TODO인 값은 행을 만들지 않는다"를 칸 단위로 적용).
- (STEP 06) 기준값 캐시 `StandardsCache`는 ApplicationReadyEvent에서 읽고, 비어 있으면 첫 사용 시 읽는다. `dataVersion()`(행 해시)을 STEP 08 결과 캐시 키에 쓴다.
- (STEP 06) 평가 러너에 over_limit 계산(`DoseService.exceedsUpperLimit`)과 단계별 Timer 값(`runs/*.json`의 stageTimings)을 추가.

- (STEP 07) INGREDIENT 규칙 조건 = 현재 섭취 성분(조건 코드로 포함) + 추천 성분 중 제외되지 않은 것. EXCLUDE를 먼저 적용한 뒤 나머지 규칙을 적용한다. 제외된 성분에도 이유 메시지를 warnings로 남긴다.
- (STEP 07) V3는 V2 프롬프트(v2.1)를 그대로 쓰고, V4는 금지 목록을 사용자 메시지 뒤에 붙인다(STEP 08 캐싱 대비). V4가 V3를 상속해 타입 주입이 모호하므로 파이프라인은 `PipelineRegistry`로 꺼낸다.
- (STEP 07) API 응답을 `items`(최종) / `excludedItems`(제외 + 이유)로 나눴다. 프론트 연동은 `docs/api/recommendation-api.md` 참고 (STEP 09에서 작성).

- (STEP 08) `spring-boot-starter-data-redis` implementation 추가 (Boot BOM 버전). 이유: V6 결과 캐시 저장소. 기본값은 메모리(`recommendation.cache.store=memory`)라 Redis 없이도 앱·테스트가 돈다. Redis 헬스체크와 Redis 저장소 스캔은 defaults properties에서 끔.
- (STEP 08) 결과 캐시 키에 현재 섭취는 성분 코드만 넣고 함량은 뺐다 (용량은 매번 다시 계산하므로). 민감 입력도 캐시한다 (키는 해시, 값은 성분·이유·요약) — 이유 문장에 질환명이 들어갈 수 있어 팀 정책 확인 필요.
- (STEP 08) 평가 러너에 `-Ppasses=2`(같은 JVM에서 연속 실행) 추가. V6 1회차/2회차를 두 줄로 기록.
- (STEP 08) k6 미설치라 k6 스크립트(`perf/k6/`)만 만들고, 대신 in-JVM 부하 테스트(실제 Tomcat + WireMock 3초, `perfTest`)로 V4·V6를 측정했다. results.csv label에 `load-wiremock`과 note에 "k6 대체"를 적었다.
- (STEP 08) 테스트에서 LLM 클라이언트를 WireMock으로 바꿀 때 운영 코드(API 키는 환경변수만)를 건드리지 않으려고 테스트 전용 BeanPostProcessor로 교체한다.
- (STEP 08) **최종 서비스 설정(잠정)**: `recommendation.pipeline-version` 기본값은 아직 V0 그대로 두었다 (팀 공통 기본값을 실측 없이 바꾸지 않음). 실측 전 근거로는 V6(V4 + 결과 캐시)가 품질 변화 없이 LLM 호출을 줄여 1순위 후보다. V7·V8 채택은 실제 토큰·품질 측정(TODO 08-1~3) 후 결정.

- (STEP 09) 큐는 Redis List(LPUSH/BRPOP) + 메모리 구현. 기본은 메모리(`recommendation.queue.store=memory`). 작업 상태 원본은 DB라 워커 시작 시 PENDING 행을 다시 넣는다.
- (STEP 09) 동기/큐는 `recommendation.api-mode=sync|queue`로 고르고 기본은 sync(기존 동작). 응답 코드 DONE=200, 그 외 202.
- (STEP 09) 워커 동시 처리 수 기본값 4. **rate limit은 실제 API를 쓰지 않아 콘솔에서 확인하지 못했다** → 사용자가 확인 후 조정 (TODO 09-0). 측정은 20으로 했다.
- (STEP 09) 알림 일괄 발송은 `domain/recommendation/perf/NotificationSpikeSimulator`(dedup·분산·Sender 인터페이스)로만 만들고 notifications 테이블에는 쓰지 않았다. 유니크 제약은 제안 SQL만.
- (STEP 09) 재사용 판단의 "변경 시각"은 health_profiles.updated_at, user_conditions.created_at, user_medications.updated_at 중 최댓값. 행 삭제만 있는 변경은 못 잡는다 (지금은 설문 제출 때만 삭제됨).
- (STEP 09) in-JVM 부하 테스트에서 같은 JVM의 다른 컨텍스트·WireMock 스레드가 스레드 수에 섞이는 문제를 발견해 포트로 거르도록 고쳤다 (2회차 결과까지는 스레드 수 비교 불가).
- (STEP 09 커밋) STEP 10 장애 대응 코드 일부(ResilientLlmClient, RuleOnlyPipeline 등)가 같은 파일을 고쳐 함께 커밋됐다. STEP 10 커밋에서 측정·문서를 마무리한다.

- (STEP 10) `io.github.resilience4j:resilience4j-spring-boot3:2.4.0` implementation 추가 (2026-03 릴리스). 이유: 재시도·서킷 브레이커·Bulkhead. 설정은 application.yml이 아니라 `llm.resilience.*`(LlmProperties 기본값)으로 코드에서 조립. `llm.resilience.enabled` 기본 false.
- (STEP 10) 서킷 OPEN·재시도 소진·Bulkhead 가득 참을 `LlmUnavailableException` 하나로 바꿔 서비스에서 폴백 (프롬프트의 CallNotPermittedException 직접 처리와 같은 의미).
- (STEP 10) 장애 주입은 WireMock 상태 순환 시나리오로 비율을 정확히 맞췄다 (확률 응답 기능이 없어서). outage 복구는 테스트에서 500 매핑을 지워서 만든다.
- (STEP 10) 측정 결과 slow 시나리오에서 15초 타임아웃이 20초 느린 응답을 실패·재시도로 바꿔 p95가 나빠졌다 → 타임아웃은 실제 API 지연을 잰 뒤 정한다 (TODO 10-0).

- (2026-10-22, 사용자 결정) LLM을 Claude(직접 HTTP)에서 **OpenAI + Spring AI**로 바꿨다. Claude 직접 호출(`AnthropicLlmClient`)은 삭제. 모든 실제 호출은 `LlmClient` → `SpringAiLlmClient` → Spring AI `OpenAiChatModel`(공식 openai-java SDK). 기본 `gpt-6.1-sol`, 작은 모델 `gpt-6-luna` (단가 2026-10-22 확인 → `perf/pricing.md`). 키는 `backend/.env`의 `OPENAI_API_KEY`.
- (2026-10-22, 사용자 결정) Spring Boot 3.5.6 → **4.1.1**. 이유: Spring AI 2.x(정식판, 공식 OpenAI SDK 기반)가 Boot 4 필요. 1.1.x로 만들고 나중에 Jackson 3·Spring AI 2로 두 번 옮기는 비용을 피함. 함께 바뀐 것: Jackson 2 → 3(`tools.jackson.*`, 애너테이션은 `com.fasterxml.jackson.annotation` 그대로), `spring-boot-starter-web` → `-webmvc`, Resilience4j `-spring-boot3` → `-spring-boot4:2.4.0`, 테스트 모듈 `spring-boot-starter-webmvc-test`·`-data-jpa-test`·`-micrometer-metrics-test` (`@AutoConfigureObservability` → `@AutoConfigureMetrics`). **팀 전체 백엔드 버전이 바뀌므로 develop 반영 전 공유 필요.**
- (2026-10-22) `org.springframework.ai:spring-ai-starter-model-openai` (BOM 2.0.1) implementation 추가. 연결은 Spring AI 표준 자동 설정(`spring.ai.*`)에 맡기고, 기본값 `spring.ai.model.chat=none`(그 밖의 embedding·image·audio·moderation도 none)이라 키 없는 팀원·테스트 환경에서도 서버가 뜬다. `local-llm` 프로필에서 `spring.ai.model.chat=openai`, `llm.provider=spring-ai`. 키는 `spring.ai.openai.api-key=${OPENAI_API_KEY:}`, SDK 재시도 0(`spring.ai.openai.max-retries`), 타임아웃은 `spring.ai.openai.chat.timeout=${llm.timeout-ms}ms`.
- (2026-10-22) 구조: `SpringAiLlmClient`는 Spring AI 공통 API(`ChatModel`, `ChatOptions`, `StructuredOutputChatOptions.outputSchema`)만 쓰고, 회사별 차이(출력 토큰 파라미터, strict, prompt_cache_key, SDK 예외 변환, 캐시 토큰 포함 여부)는 `ChatProviderSupport` 인터페이스(지금 구현 `OpenAiProviderSupport`)로 분리. 다른 회사로 바꿀 때 = starter + `spring.ai.*` + 이 빈 하나.
- (2026-10-22) 연동 검증: WireMock으로 설문→추천 전체 흐름(`SpringAiWiringTest`), 실제 API 2회(`liveTest`). 결과는 `docs/llm-integration-report.md`.
- (2026-10-22) 출력 형식 강제를 tool use → structured outputs(`response_format` json_schema strict)로 바꿨다. `gpt-6.1-sol`이 Chat Completions에서 tool calling을 지원하지 않고(모델 문서), Spring AI 2.0은 Responses API를 지원하지 않는다(2.1 마일스톤부터). 응답 JSON은 `LlmResponse.toolInput`에 넣어 파이프라인은 그대로다.
- (2026-10-22) 프롬프트 캐싱: OpenAI는 자동(최소 1,024토큰)이라 V7은 `prompt_cache_key`만 보낸다. `usage.prompt_tokens`에 캐시분이 포함되므로 클라이언트에서 빼서 기록. Fake의 최소 캐시 길이도 512 → 1024 추정 토큰으로 맞췄다.
- (2026-10-22) 부하 테스트 WireMock 매핑(`perf/wiremock/*`)을 OpenAI Chat Completions 형식(`/v1/chat/completions`)으로 다시 만들었다. 이전 results.csv 줄(Claude 형식 WireMock)은 그대로 두고, 재측정 줄은 note로 구분한다.

## 막힌 것

- 없음 (대체 방식으로 진행한 항목은 맨 위 "밤새 실행 요약" 2번)

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

## STEP 04 - 측정 인프라 + 평가셋 + V0 기준선 (2026-10-21)
- 한 일:
  - `StageTimer`(Micrometer): `recommendation.stage` {assemble, llm, parse}, `recommendation.total`, `/actuator/prometheus` 노출 확인 테스트
  - 평가셋 32케이스 `eval/cases.json` (규칙 1개 12, 여러 개 10, 없음 5, 비회원 5, 재현성 10 고정), 별칭 표 `eval/ingredient-aliases.json`
  - 평가 러너 `eval/EvalRunner` + `./gradlew evalRun` (예상 호출 수·비용 출력, `-Pconfirm=true`, `-Pllm=fake|fake-noisy`, `-Pmode=repro`), 결과 `docs/perf/results.csv`(헤더 생성) + `docs/perf/runs/*.json`
  - V0 파싱 실패 유형 기록(NO_JSON_OBJECT 등, 본문 저장 없음)
  - 테스트: EvalMetricsTest 9, EvalRunnerTest 4, prometheus 노출 1
- 측정 결과 요약: [STEP04-report.md](perf/STEP04-report.md). V0 실제 기준선은 **실제 측정 대기**. Fake 러너 검증 4줄(results.csv 2~5행): 정답 Fake recall 1.0 / violation 0.0, 오답 Fake에서 지표 하락 확인
- 결정한 것 / 미룬 것: 효과(Q-GOAL) → mayInclude 매핑은 출처가 없어 검토 대상으로 표시
- 다음 STEP 전에 사용자가 할 일:
  - **평가셋 검토 요청**: `backend/src/test/resources/eval/cases.json`을 검토하고 맞으면 `meta.reviewed`를 `true`로 바꿔 주세요. 특히 `meta.goalMayInclude`(원하는 효과 → 성분)는 근거 출처가 없습니다.
  - 실제 기준선: TODO-live-runs.md 04-1, 04-2

## STEP 05 - V1 출력 형식 강제 (2026-10-21)
- 한 일: `V1Pipeline`(StructuredPipeline), `RecommendationToolSchema`(strict, 성분 enum, amount·unit nullable), `RecommendationOutputValidator`, 1회 재요청(RECOMMEND_REPAIR), 첫 호출 검증 실패 VALIDATION_FAIL 기록, prompts/v1 (v1.1). 테스트: 검증기 6, V1 파이프라인 4(정상, 재요청 성공, 재요청 실패, 도구 미호출)
- 측정 결과 요약: [STEP05-report.md](perf/STEP05-report.md). 실제 API는 **실제 측정 대기**. Fake-noisy에서 V0 parse_fail 0.2 → V1 0.0 (재요청 10/50회, 메커니즘 확인용)
- 결정한 것 / 미룬 것: tool_choice 강제 대신 auto (위 "결정한 것")
- 다음 STEP 전에 사용자가 할 일: `reference/domain-rules.md` 2번 표의 TODO 수치 입력 (STEP 06 기준표 시드). 안 채우면 해당 성분은 용량 없이 추천된다

## STEP 06 - V2 용량 계산 분리 (2026-10-21)
- 한 일: `ingredient_standards` 엔티티·시드(domain-rules.md 출처 6행), `ai_recommendation_items` 용량 컬럼 5개, `DoseCalculator`(순수), `StandardsCache`, `DoseService`, `ConditionCodeResolver`(SMOKING_CURRENT만), `V2Pipeline`(prompts/v2, v2.1, calc Timer), 추천 API 응답에 용량 필드. 테스트: DoseCalculatorTest 10(TEST ONLY 값: standards/test-only-standards.json), V2PipelineTest 1
- 측정 결과 요약: [STEP06-report.md](perf/STEP06-report.md). 실제 API는 **실제 측정 대기**. Fake-noisy 기준 dose_error V1 0.3571 → V2 0.0, calc 단계 평균 0.3~0.6ms
- 결정한 것 / 미룬 것: 위 "결정한 것" STEP 06 항목. 나머지 성분 기준값은 TODO
- 다음 STEP 전에 사용자가 할 일: `reference/domain-rules.md` 2번 TODO 수치 입력 후 `docs/sql/STEP06_seed_ingredient_standards.sql`과 테스트 픽스처에 행 추가

## STEP 07 - V3 · V4 규칙 검증 (2026-10-21)
- 한 일: `recommendation_rules` 엔티티·시드 27행, 추천 항목 컬럼 3개(excluded, applied_rule_ids, warnings), `ConditionCodeResolver` 전체 매핑, `RuleEngine`(순수), `RuleService`(캐시 + Counter `recommendation.rule.hit`), `V3Pipeline`(rule_post), `V4Pipeline`(rule_pre, prompts/v4, v4.1). 테스트: RuleEngineTest(규칙 27개 × 적용/미적용 + 겹침·INGREDIENT·비회원), ConditionCodeResolverTest 5, V3V4PipelineTest 2
- 측정 결과 요약: [STEP07-report.md](perf/STEP07-report.md). 실제 API는 **실제 측정 대기**. Fake-noisy 재현성 기준 violation V2 0.26 → V3 0.0, raw V3 0.26 → V4 0.0(가정 기반), rule 단계 평균 1ms 미만
- 결정한 것 / 미룬 것: 위 "결정한 것" STEP 07 항목
- 다음 STEP 전에 사용자가 할 일: (선택) 규칙·문구 약사 검토

## STEP 08 - 비용 최적화 (2026-10-21)
- 한 일: V6(결과 캐시: `CacheKeyFactory`, `ResultCacheService`, 메모리/Redis 저장소), V7(cache_control), V8(`ModelRouter`), StructuredPipeline에 캐시 흐름, Fake에 프롬프트 캐싱 흉내, k6 스크립트·요청 세트 생성기·WireMock 매핑(`perf/`), in-JVM 부하 테스트. 테스트: CostOptimizationPipelineTest 6, ResultCacheStoreTest 3(Redis 실연결 포함)
- 측정 결과 요약: [STEP08-report.md](perf/STEP08-report.md). 실제 API **실제 측정 대기**. Fake V6 2회차 LLM 호출 0·적중률 1.0, V8 라우팅 작은 모델 13/32. 부하(WireMock 3초, 2,000건): LLM 호출 2,000 → 809, TPS 15.20 → 28.23, p50 3,086 → 45ms, p95 3,611 → 4,464ms(악화)
- 결정한 것 / 미룬 것: 캐시 스탬피드(single-flight), V6 p95 원인, V7 실제 캐시 적용 여부
- 다음 STEP 전에 사용자가 할 일: Anthropic Console에서 rate limit 확인 (STEP 09 워커 동시 처리 수 설정용)

## STEP 09 - 처리 구조 개선 + 알림 몰림 (2026-10-21)
- 한 일: 큐 모드(`RecommendationQueue` 메모리/Redis List, `RecommendationWorker`, 202 접수·GET 상태·멱등), ai_recommendations 상태 컬럼 4개, `GET /api/v1/users/me/recommendations/latest`(재사용), `NotificationSpikeSimulator`(dedup·분산), 알림 dedup 유니크 제안 SQL, k6 `spike.js`, in-JVM 구조 비교 부하 테스트, 추천 API 명세 `docs/api/recommendation-api.md`. 테스트: QueueModeTest 3, NotificationAndReuseTest 5(Redis 큐 실연결 포함)
- 측정 결과 요약: [STEP09-report.md](perf/STEP09-report.md). spike-burst(10초 600건, WireMock 3초): 에러율 sync 0.053~0.232 / vt 0.002 / queue 0.0, LLM 동시 호출 최대 sync 50 / vt 256~413 / queue 20, queue 완료 p95 89~102초. 발송 분산(60초)이면 sync·vt 모두 실패 0. 알림 진입 재사용으로 LLM 호출 70% 감소(프로필 변경 30% 가정)
- 결정한 것 / 미룬 것: 위 "결정한 것" STEP 09 항목. 스레드 수 재측정, OSIV 영향 확인
- 다음 STEP 전에 사용자가 할 일: rate limit 확인(TODO 09-0)

## STEP 10 - 장애 대응 (2026-10-21)
- 한 일: `ResilientLlmClient`(Retry → CircuitBreaker → Bulkhead, attempt_no 기록, 429 retry-after), `LlmUnavailableException`, `RuleOnlyPipeline`(규칙 기반 폴백, model_name RULE_ONLY, fallback·notice 응답 필드), Counter(circuit.transition, fallback), WireMock 장애 매핑 생성기(slow·flaky·outage), in-JVM 장애 주입 부하 테스트. 테스트: ResilientLlmClientTest 5(재시도 대상/비대상, 서킷 전이, 4xx 미집계, 소진), FallbackTest 3
- 측정 결과 요약: [STEP10-report.md](perf/STEP10-report.md). flaky 성공률 V9-queue 0.80 → V10 1.00(폴백 제외 0.98, 재시도 호출 +45), outage 0.50 → 1.00(폴백 55%, 서킷 OPEN 2.3초, 복구 후 7.2초에 CLOSED), slow p95 40.6초 → 45.6초(악화). RULE_ONLY 평가 recall 1.0은 설계상 당연(같은 규칙으로 정의된 평가셋)
- 결정한 것 / 미룬 것: 타임아웃·재시도 값은 실제 지연 측정 후 조정
- 다음 STEP 전에 사용자가 할 일: 없음

## STEP 11 - 최종 정리 (2026-10-21)
- 한 일: `docs/perf/SUMMARY.md`(results.csv 출처 표시), `docs/perf/verify-summary.js`(대조 스크립트), `docs/perf/charts/make-charts.js` + SVG 차트 3개(STEP 08 캐시, STEP 09 알림 몰림, STEP 10 장애 성공률), `backend/README.md`(실행·평가·부하 테스트 방법). 루트 README는 팀 공통 파일이라 손대지 않았다
- **대조 결과**: `node docs/perf/verify-summary.js` → 대조한 수치 80개, 불일치 0 (일부러 값을 바꿨을 때 불일치 1로 잡히는 것도 확인). results.csv에 없는 수치(STEP 02, 단계 시간, 서킷 시간 등)는 각 STEPxx-report.md 링크로 출처를 달았다
- STEP 09 리포트에 3회차 결과 추가 (vt 에러율 0.147로 악화, 스레드 수 측정 실패 기록)
- 다음에 사용자가 할 일: 위 "아침에 할 일"
