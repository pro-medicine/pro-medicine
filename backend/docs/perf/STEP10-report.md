# STEP10 리포트 - V10 장애 대응 (타임아웃 · 재시도 · 서킷 브레이커 · 폴백)

## 바꾼 것 (Before → After)

| 장치 | V9-queue (Before) | V10 |
| --- | --- | --- |
| 타임아웃 | HTTP 읽기 30초 | **15초** (`llm.timeout-ms=15000`) |
| 재시도 | 없음 | 최대 2회(시도 3회), 지수 백오프 500ms×2 + 지터 0.5. **429·5xx·타임아웃·네트워크 오류만**. 400·401 재시도 안 함. 429 `retry-after`를 따름(최대 10초) |
| 서킷 브레이커 | 없음 | 최근 20건 중 실패율 50% 이상 OPEN, 30초 후 HALF_OPEN(5건 시험). 재시도 대상 실패만 실패로 셈 |
| 동시 호출 제한 | 워커 수만 | Bulkhead 20 (= 워커 동시 처리 수) |
| 폴백 | 없음 (FAILED) | 서킷 OPEN·재시도 소진이면 `RuleOnlyPipeline` (규칙 기반 기본 안내, `model_name = RULE_ONLY`, `fallback = true`) |

구현 메모
- Resilience4j(`resilience4j-spring-boot3` 2.4.0)를 `LlmClient` 데코레이터(`ResilientLlmClient`)로 붙였다. 순서: Retry → CircuitBreaker → Bulkhead → LoggingLlmClient → HTTP. 재시도가 기록 데코레이터 바깥이라 **시도마다 `llm_call_logs` 행이 남고 `attempt_no`가 2, 3 …으로 늘어난다**
- 폴백은 결과 형태가 달라 LlmClient에서 하지 않고 `RecommendationService`에서 처리한다. 프롬프트는 `CallNotPermittedException`·재시도 소진 예외를 잡으라고 했는데, 데코레이터가 두 경우를 `LlmUnavailableException(CIRCUIT_OPEN | RETRY_EXHAUSTED | BULKHEAD_FULL)` 하나로 바꿔 던지고 서비스는 그것만 잡는다 (같은 의미, 파이프라인 코드 변경 없음). Bulkhead가 가득 찬 경우도 폴백한다
- `llm.resilience.enabled`(기본 false)로 켠다. 꺼져 있으면 STEP 09 이전과 같다
- Counter: `recommendation.circuit.transition{to}`, `recommendation.fallback{reason}`. Resilience4j 자체 메트릭도 `/actuator/prometheus`에 나온다

## 측정 조건

| 항목 | 값 |
| --- | --- |
| k6 | 미설치 → 스크립트(`perf/k6/spike.js`)와 장애 매핑만, 실행은 [TODO-live-runs.md](TODO-live-runs.md) 10-1 |
| 대체 측정 | in-JVM `FaultLoadPerfTest`: 실제 Tomcat + H2 + 프로세스 안 WireMock, **둘 다 queue 모드, 워커 20**, 파이프라인 V4 |
| 장애 주입 | `perf/wiremock/{normal,slow,flaky,outage}` (`gen-scenarios.js`). WireMock에 확률 응답이 없어 **상태 순환 시나리오로 비율을 정확히** 맞췄다 (요청 순서대로) |
| normal | 항상 3초 |
| slow | 5건 중 1건(20%) 20초, 나머지 3초 |
| flaky | 20건 중 타임아웃 2건(10%, 35초 지연 → V9 30초·V10 15초 모두 초과), 500 1건(5%), 429 1건(5%, retry-after 1) |
| outage | 처음 60초 100% 500, 이후 정상 |
| 부하 | normal·slow·flaky: 30초 동안 200건 / outage: 120초 동안 600건. 큐 접수 후 0.5초 간격 폴링 (최대 300초) |
| 날짜 | 2026-10-21 |

## 결과 — results.csv `10,V9-queue-*`, `10,V10-*` (시나리오별 한 줄)

| 지표 | 시나리오 | V9-queue | V10 |
| --- | --- | --- | --- |
| 최종 성공률 (폴백 포함 / 제외) | flaky | 0.80 / 0.80 | **1.00 / 0.98** |
| p95 (완료까지) | slow | **40,593 ms** | 45,573 ms |
| p95 (완료까지) | normal | 3,616 ms | 4,142 ms |
| 재시도로 늘어난 LLM 호출 (attempt_no ≥ 2) | flaky | - | 45 (전체 245회, 요청 200건) |
| 429 응답 수 | flaky | 10 | 12 |
| 서킷 OPEN까지 걸린 시간 | outage | - | 2,307 ms (장애 시작부터) |
| 복구 후 정상화 | outage | 첫 정상 결과 3,073 ms | 서킷 CLOSED 7,237 ms, 첫 정상(폴백 아닌) 결과 6,473 ms (장애 종료부터) |
| 최종 성공률 (폴백 포함 / 제외) | outage | 0.50 / 0.50 | **1.00 / 0.45** |
| 폴백 응답 비율 | outage | - | 0.553 |
| LLM 호출 수 | outage | 600 | 296 (서킷 OPEN 동안 호출 안 함) |
| 큐 적체 최대 | outage | 0 | 0 |
| 큐 적체 최대 | slow / flaky | 93 / 66 | 93 / 66 |

원본: `runs/STEP10-{V9-queue,V10}-{normal,slow,flaky,outage}-*.json`. 실행: `./gradlew perfTest --tests '*FaultLoadPerfTest*'`

### 규칙 기반 폴백의 품질 (평가셋, LLM 미사용 → 실제 측정값) — results.csv `10,RULE_ONLY`, `10,RULE_ONLY-repro`

| 지표 | RULE_ONLY | RULE_ONLY-repro |
| --- | --- | --- |
| recall / precision | 1.0 / 1.0 | 1.0 / 1.0 |
| violation_rate | 0.0 | 0.0 |
| dose_error_rate (n) | 0.0 (8) | 0.0 (25) |
| reproducibility_set / dose | - | 1.0 / 1.0 |
| 추천 성분 수 합계 (32케이스) | 16 | - |
| e2e_p95_ms | 3.94 | 2.07 |

**주의: recall 1.0은 설계상 당연한 값이다.** 평가셋의 mustInclude가 "ADJUST 규칙 + R-05·R-06 대상"으로 정의돼 있고 폴백도 같은 규칙으로 고른다. 이 숫자는 "폴백이 안전 규칙과 기준표를 그대로 지킨다"는 확인일 뿐, 추천의 쓸모를 보여주지 않는다. 걸리는 규칙이 없는 일반 케이스 5개(E-023~E-027)에는 아무것도 추천하지 않는다 (정답 Fake의 추천 합계 42개 대비 16개).

## 해석

- **flaky·outage에서 효과가 분명하다.** 재시도로 일시 오류(500, 429, 타임아웃)를 흡수하고(flaky 실패 40건 → 0), 장애가 길면 2.3초 만에 서킷이 열려 LLM을 부르지 않고 폴백으로 바로 응답한다 (outage LLM 호출 600 → 296). 장애가 끝나면 HALF_OPEN 시험으로 약 7초 뒤 정상화
- **slow에서는 오히려 나빠졌다.** 20초 걸리는 느린 응답이 15초 타임아웃에 걸려 실패 → 재시도가 되면서 LLM 호출이 45번 늘고 p95가 5초 길어졌으며, 타임아웃이 쌓여 서킷도 열렸다(76초 시점, 폴백 2%). V9는 30초 타임아웃이라 느려도 성공했다. 타임아웃은 "실제 응답 시간 분포의 p99보다 약간 길게" 정해야 하고, 실제 API 지연을 잰 뒤 조정해야 한다
- **재시도와 rate limit**: flaky에서 429가 10 → 12로 늘었다. 재시도는 같은 계정 한도를 더 쓰므로, 429가 많은 상황에서 재시도 횟수를 늘리면 429가 더 늘어날 수 있다. retry-after를 따르고 서킷이 열리면 호출을 멈추는 것이 그 완충 장치다
- normal에서도 V10 p95가 0.5초 길다 (4,142 vs 3,616). 데코레이터 비용이라기보다 같은 JVM 측정 편차 범위로 보인다 (단계별로 더 확인 필요)

## 한계 / 다음에 할 것

- k6 실행(TODO 10-1), 실제 API 지연 분포 측정 후 타임아웃·재시도 값 조정
- 장애 중 스레드 수는 STEP 09와 같은 측정 문제로 따로 정리하지 않았다 (큐 적체는 위 표)
- 폴백은 규칙으로 설명 가능한 성분만 추천해 일반 케이스에는 빈 결과다. 화면에서 notice와 함께 "설문 다시 하기"를 보여주는 것을 권장
