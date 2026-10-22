# AI 추천 안전장치 정리

AI 추천(설문 → GPT → 추천 응답)에 들어 있는 안전장치를 한곳에 모은 문서다. 2026-10-22 기준.
연동 검증 결과는 [llm-integration-report.md](llm-integration-report.md), 단계별 측정값은 [perf/SUMMARY.md](perf/SUMMARY.md).

- 파일 경로는 `backend/src/main/java/com/ucamp/app/` 기준 (그 밖의 경로는 따로 적음)
- "지금"은 이 문서 작성 시점에 쓰는 실행 설정: `local-llm` 프로필, `recommendation.pipeline-version=V4`, `api-mode=sync`, `llm.resilience.enabled=false`

## 1. 한눈에 보기

| 분류 | 장치 | 막는 문제 | 지금 | 켜는 법 |
| --- | --- | --- | --- | --- |
| 형식 | 구조화 출력 (json_schema strict) | GPT 답의 형식 깨짐 | 켜짐 | V1 이상 |
| 형식 | 서버 검증 + 1회 재요청 | 잘린 답, 중복 성분, 순서 오류 | 켜짐 | V1 이상 |
| 내용 | 허용 성분 코드만 (enum) | 목록에 없는 성분 | 켜짐 | V1 이상 |
| 내용 | 용량은 서버 계산 | GPT의 용량 오류, 상한 초과 | 켜짐 | V2 이상 |
| 내용 | 사후 규칙 검증 | 금지 성분 추천 (최종 차단) | 켜짐 | V3 이상 |
| 내용 | 사전 금지 목록 | 금지 성분 추천 (사전 안내) | 켜짐 | V4 이상 |
| 비용·성능 | 결과 캐시 | 같은 조건의 반복 호출 | 꺼짐 | V6 |
| 비용·성능 | 프롬프트 캐싱 | 고정 프롬프트의 입력 비용 | 꺼짐 | V7 |
| 비용·성능 | 모델 라우팅 | 단순 케이스에 비싼 모델 사용 | 꺼짐 | V8 |
| 비용·성능 | 알림 진입 재사용 | 프로필 그대로인데 다시 호출 | 켜짐 | 항상 |
| 비용·성능 | 출력 토큰 한도 | 출력 폭주, rate limit 낭비 | 2,000 | `llm.max-tokens` |
| 트래픽 | 큐 + 워커 | 몰림 시 스레드 고갈, OpenAI 429 | 꺼짐 (sync) | `recommendation.api-mode=queue` |
| 장애 | 타임아웃 | 응답 없는 호출에 묶임 | 30초 | `llm.timeout-ms` |
| 장애 | 재시도 / 서킷 / Bulkhead / 폴백 | 일시 오류, 장기 장애 | 꺼짐 | `llm.resilience.enabled=true` |
| 관측 | 호출 기록, 단계별 지연 | 원인 추적, 비용 계산 | 켜짐 | 항상 |

## 2. 형식 안전장치

GPT 답이 정해진 JSON 구조로 오도록 강제하고, 그래도 깨지면 서버에서 걸러낸다.

| 단계 | 하는 일 | 파일 |
| --- | --- | --- |
| 스키마 정의 | 필드, 타입, 성분 코드 enum, `required`, `additionalProperties: false` | `domain/recommendation/pipeline/RecommendationToolSchema.java` |
| 스키마 전달 | Spring AI 공통 `outputSchema` | `infra/llm/SpringAiLlmClient.java` (`options()`) |
| 강제 켜기 | OpenAI `response_format: { type: json_schema, strict: true }` | `infra/llm/OpenAiProviderSupport.java` (`structuredOutput()`) |
| 파싱 | 응답 문자열 → `JsonNode`. 실패하면 null로 넘겨 재요청 | `infra/llm/SpringAiLlmClient.java` (`parse()`) |
| 검증 | 성분 코드, 중복, priority 연속, amount/unit 짝 | `domain/recommendation/pipeline/RecommendationOutputValidator.java` |
| 재요청 | 틀린 이유를 붙여 1회 재요청. 또 실패하면 실패 처리 | `domain/recommendation/pipeline/StructuredPipeline.java`, `src/main/resources/prompts/common/repair.txt` |

- strict 모드는 OpenAI가 답을 생성하는 단계에서 스키마에 맞지 않는 토큰을 막는다. 프롬프트의 "JSON으로 답해라"는 안내일 뿐 강제가 아니다.
- strict로도 막지 못하는 것: 출력 한도에 걸려 잘린 답, 답변 거부, 스키마로 표현할 수 없는 규칙(중복·연속성). 그래서 서버 검증을 남겨 둔다.
- 실패 기록: `llm_call_logs.outcome` = `VALIDATION_FAIL` / `PARSE_FAIL`, 재요청은 `call_tag = RECOMMEND_REPAIR`.

## 3. 내용 안전장치 (넣으면 안 되는 성분·용량)

```text
설문 답변 → ① 조건 코드 → ② 사전 금지 목록(프롬프트) → GPT → ③ enum·검증 → ④ 사후 규칙(최종 차단) → 저장
```

| 단계 | 하는 일 | 파일 |
| --- | --- | --- |
| ① 조건 코드 | 설문 답변 → 규칙용 코드 (흡연, 임신, 질환, 복용 약 계열) | `domain/recommendation/rule/ConditionCodeResolver.java` |
| ② 사전 금지 목록 | 걸리는 EXCLUDE 규칙의 성분을 "추천하지 마라" 목록으로 프롬프트에 추가 | `rule/RuleEngine.java` (`preFilter()`), `pipeline/V4Pipeline.java`, `prompts/v4/banned.txt` |
| ③ 허용 성분 | 스키마 enum으로 14개 성분 코드만 허용 | `pipeline/RecommendationToolSchema.java` |
| ③ 용량 차단 | V2부터 스키마에 `amount` 필드가 없음 → GPT가 용량을 줄 수 없음 | 같은 파일 (`tool(false)`) |
| 용량 계산 | 기준표 − 현재 섭취량. 상한섭취량 초과 시 상한에 맞춤 | `domain/recommendation/dose/DoseCalculator.java` |
| ④ 사후 규칙 | GPT가 금지 성분을 넣어도 `excludedItems`로 옮김. 나머지에 경고 추가 | `rule/RuleEngine.java` (`applyPost()`), `pipeline/V3Pipeline.java` |
| 응답 분리 | `items`(최종 추천) / `excludedItems`(제외 성분과 이유) | `domain/recommendation/dto/RecommendationResponse.java` |

`rule/`, `pipeline/`, `dose/`, `dto/`는 `domain/recommendation/` 아래.

규칙 데이터: DB `recommendation_rules` (27행, 시드 `backend/docs/sql/STEP07_seed_recommendation_rules.sql`, 원본 `backend/docs/supplement-recommendation/reference/domain-rules.md` 3번 표).

| action | 동작 |
| --- | --- |
| EXCLUDE | 추천에서 제외 (`excludedItems`) |
| WARN | 경고 문구 추가 |
| TIMING | 복용 시간 안내 추가 |
| ASK | 의사·약사 확인 안내 추가 |
| ADJUST | 용량 조정 (기준표 조건 행으로 반영, 규칙 ID만 기록) |

- 비회원: `guest_excluded` 규칙은 비회원에게만 적용된다. 임신·복용 약을 묻지 않으므로 칼륨, 세인트존스워트, 비타민 A를 미리 막는다.
- 규칙은 서비스 공개 전에 약사 검토가 필요하다.

## 4. 비용·성능 장치

| 장치 | 하는 일 | 파일 | 설정 | 측정 (WireMock 기준) |
| --- | --- | --- | --- | --- |
| 결과 캐시 (V6) | 같은 조건이면 저장된 결과 재사용 (메모리 또는 Redis, 기본 7일) | `domain/recommendation/cache/ResultCacheService.java`, `CacheKeyFactory.java` | `pipeline-version=V6`, `recommendation.cache.store`, `recommendation.cache.ttl` | 2,000건 호출 2000 → 809, TPS 15 → 28 |
| 프롬프트 캐싱 (V7) | 고정 프롬프트 앞부분을 OpenAI가 캐시 (`prompt_cache_key`) | `pipeline/V7Pipeline.java`, `OpenAiProviderSupport.promptCache()` | `V7` | 효과 없을 가능성 큼: 프롬프트 약 900토큰 < 최소 1,024토큰 |
| 모델 라우팅 (V8) | 규칙이 적게 걸리는 단순 케이스는 `gpt-6-luna` | `pipeline/ModelRouter.java`, `V8Pipeline.java` | `V8`, `llm.small-model` | 평가셋 32건 중 13건이 작은 모델 (품질 미측정) |
| 알림 진입 재사용 | 프로필이 그대로면 저장된 추천 반환 | `domain/recommendation/service/RecommendationReuseService.java` | 항상 | 프로필 변경 30% 가정 시 호출 70% 감소 |
| 출력 토큰 한도 | 출력 최대 길이. OpenAI rate limit 계산에도 쓰임 (5장) | `infra/llm/LlmProperties.java`, `application-local-llm.yml` | `llm.max-tokens=2000` | 실제 출력 약 300토큰 |

## 5. OpenAI 사용량 제한 (rate limit)

2026-10-22 응답 헤더(`x-ratelimit-limit-*`)로 확인한 우리 계정의 `gpt-6.1-sol` 상한. 계정 등급과 모델마다 다르다.

| 항목 | 1분 상한 |
| --- | --- |
| 요청 수 (RPM) | 5,000 |
| 토큰 수 (TPM) | 1,000,000 |

OpenAI는 TPM을 셀 때 **요청의 출력 한도(`max_completion_tokens`)를 미리 잡아** 계산한다. 그래서 실제 사용량(약 1,300토큰)보다 한도 설정이 처리량을 정한다.

| 출력 한도 | 요청 1건이 차지하는 TPM | 1분 최대 추천 수 |
| --- | --- | --- |
| 8,000 (이전) | 약 9,000 | 약 110 |
| **2,000 (지금)** | 약 3,000 | 약 330 |

- 추천 1건 약 10~13초 → 워커 1개가 1분에 약 5건. 지금 설정이면 동시 호출 약 60개까지 상한 안.
- `gpt-6.1-sol`은 추론 모델이라 보이지 않는 추론 토큰도 출력 한도 안에서 쓴다. 한도를 낮춘 뒤 답이 잘리면(`finish_reason: length`, 검증 실패 증가) 다시 올린다.
- 확인 방법: platform.openai.com → Settings → Limits, 또는 응답 헤더 `x-ratelimit-limit-requests` / `x-ratelimit-limit-tokens` / `x-ratelimit-remaining-*`.

## 6. 트래픽 몰림 대응 (V9)

sync 모드는 요청마다 Tomcat 스레드 1개가 GPT 응답(10~13초)을 기다리며 점유된다.

| 자원 | 몰릴 때 문제 |
| --- | --- |
| Tomcat 스레드 (기본 최대 200) | 바닥나면 AI와 무관한 다른 API(친구, 마이페이지, 지도)도 대기·실패 |
| DB 커넥션 (Hikari 기본 10) | `open-in-view` 기본값(true)이면 요청이 끝날 때까지 커넥션을 쥘 수 있음 → 다른 DB 작업 정지 |
| OpenAI rate limit | 동시 호출이 많으면 429 |

| 구조 | 스레드 부담 | 동시 GPT 호출 | 10초 600건 에러율 (WireMock, 3회) | 설정 |
| --- | --- | --- | --- | --- |
| sync (지금) | 큼 | 스레드 수만큼 | 4~23% | 기본 |
| 가상 스레드 | 작음 | 제한 없음 (최대 314 관측) | 0.2~15% | `spring.threads.virtual.enabled=true` |
| **큐 + 워커** | 작음 (즉시 202) | 워커 수로 제한 | 0% | `recommendation.api-mode=queue`, `recommendation.worker.concurrency` |

- 파일: `domain/recommendation/queue/RecommendationWorker.java`, `InMemoryRecommendationQueue.java` / `RedisRecommendationQueue.java`
- 큐 모드에서는 화면이 202를 받으면 `GET /api/v1/recommendations/{id}`를 `DONE`/`FAILED`까지 1초 간격으로 조회한다 (채원 협의).
- 워커 수는 5장의 rate limit 안에서 정한다. 기본 4.

## 7. 장애 대응 (V10)

| 장치 | 하는 일 | 설정 (`llm.resilience.*`) | 기본값 |
| --- | --- | --- | --- |
| 타임아웃 | 늦은 응답을 끊음 | `llm.timeout-ms` | 30000 (켤 때 15000 권장) |
| 재시도 | 429·5xx·타임아웃·네트워크 오류 재시도, 지수 백오프 + 지터, `retry-after` 존중 | `max-attempts`, `initial-backoff`, `backoff-multiplier`, `jitter`, `max-retry-after` | 3회, 500ms, ×2, 0.5, 10초 |
| 서킷 브레이커 | 실패율이 높으면 일정 시간 호출 차단 | `circuit-window-size`, `circuit-failure-rate-threshold`, `circuit-minimum-calls`, `circuit-open-wait`, `circuit-half-open-calls` | 20, 50%, 20, 30초, 5 |
| Bulkhead | 동시 호출 상한 | `bulkhead-max-concurrent`, `bulkhead-max-wait` | 20, 10초 |
| 규칙 기반 폴백 | GPT를 못 쓰면 규칙만으로 기본 안내 (`modelName: RULE_ONLY`, `fallback: true`, `notice`) | - | - |
| 에러 분류 | OpenAI SDK 예외 → 공통 에러 (HTTP 상태, retry-after, 타임아웃, 네트워크) | - | - |

- 파일: `infra/llm/ResilientLlmClient.java`, `domain/recommendation/pipeline/RuleOnlyPipeline.java`, `infra/llm/OpenAiProviderSupport.java` (`translate()`)
- SDK 자체 재시도는 끈다 (`spring.ai.openai.max-retries=0`). 켜 두면 시도 횟수와 로그가 어긋난다.
- 꺼져 있으면 GPT 실패 시 502 `R003`.
- 측정 (WireMock): flaky 성공률 0.8 → 1.0, 60초 전면 장애 0.5 → 1.0 (폴백 포함). 느린 응답(20초)에서는 15초 타임아웃 때문에 p95가 악화됐다.
- `enabled` 하나로 네 장치가 함께 켜진다. 서킷만 따로 끄는 설정은 없다. 서킷 없이 쓰려면 `circuit-failure-rate-threshold=100`으로 사실상 열리지 않게 하거나 코드에 설정을 추가해야 한다.
- 실제 응답 시간이 10~13초라 15초 타임아웃은 여유가 작다. 켤 때 실제 지연을 다시 재고 정한다.

## 8. 관측 장치

| 장치 | 내용 | 위치 |
| --- | --- | --- |
| `llm_call_logs` | 호출마다 모델, 토큰(입력·출력·캐시), 지연, HTTP 상태, 결과, 시도 번호, 파이프라인·프롬프트 버전. 본문은 저장하지 않음 | `infra/llm/LoggingLlmClient.java` |
| 단계별 지연 | assemble(입력 조립), cache, rule_pre, llm, parse, calc(용량), rule_post | `global/metrics/StageTimer.java` → `/actuator/prometheus`의 `recommendation_stage_seconds` |
| 본문 로그 (선택) | 프롬프트·응답 본문을 로컬 파일에만 저장 | `llm.log-body=true` → `build/llm-body/` |

비용 계산: `llm_call_logs`의 토큰 × [perf/pricing.md](perf/pricing.md) 단가. 예) 2026-10-22 실제 호출 1건: 입력 967 × $2/M + 출력 307 × $10/M ≈ $0.005.

## 9. 테스트 장치

| 장치 | 용도 | 위치 |
| --- | --- | --- |
| Fake LLM | 비용 없이 전체 흐름 (기본 설정) | `infra/llm/FakeLlmClient.java` |
| WireMock | 가짜 OpenAI 서버로 정상·재요청·429·500·지연·장애 재현 | `src/test/.../infra/llm/OpenAiStubs.java`, `SpringAiWiringTest.java`, `backend/perf/wiremock/` |
| 실제 API 테스트 | 연결 + 전체 흐름 (최대 3회 호출) | `src/test/.../infra/llm/SpringAiLlmClientLiveTest.java`, `./gradlew liveTest` |
| 평가 러너 | 32케이스 품질 지표 (위반율, 용량 오류율, 재현성). 실행 전 예상 비용 출력 | `src/test/.../eval/EvalRunner.java`, `./gradlew evalRun` |
| 부하·장애 테스트 | 캐시, 구조, 장애 시나리오 | `src/test/.../perf/*LoadPerfTest.java`, `./gradlew perfTest` |

## 10. 남은 빈틈

| 빈틈 | 영향 | 제안 |
| --- | --- | --- |
| `reason`·`summary` 문장 검사 없음 | 설명 문장에 숫자 용량, 금지 성분 이름, 과장 표현이 들어가도 그대로 나감 | `RecommendationOutputValidator`에 숫자+단위 패턴, 금지 성분 이름 검사 추가 → 실패 시 재요청 |
| 응답 숫자 표기 `1E+2` | 100이 지수 표기로 나감 (JSON상 유효하지만 혼동 소지) | `DoseCalculator`의 `stripTrailingZeros()` 결과 표기 수정 |
| 실제 GPT 품질 미측정 | 위반율·용량 오류율은 Fake LLM 값 | 평가셋 검토(`reviewed = true`) 후 `evalRun` (32건 약 $0.16) |
| 타임아웃 기준 | 실제 10~13초, V10 권장 15초 | 실제 지연 분포를 잰 뒤 조정 |
| 출력 한도 2,000 검증 | 추론 토큰 때문에 잘릴 수 있음 | 실제 호출로 `llm_call_logs`의 결과·출력 토큰 확인 |
| 캐시 스탬피드 | 같은 조건 요청이 동시에 오면 캐시 전 중복 호출 | 다음 과제 (single-flight) |
| 규칙 검토 | 규칙 27개 출처는 있으나 약사 검토 전 | 서비스 공개 전 검토 |
