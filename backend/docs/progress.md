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
