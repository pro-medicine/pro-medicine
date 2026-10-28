# STEP 10. V10 - 장애 대응 (타임아웃 · 재시도 · 서킷 브레이커 · 폴백)

> 먼저 `docs/prompts/00_COMMON.md`, `docs/prompts/reference/measurement.md`를 읽고 따른다. STEP 09가 끝난 상태에서 시작한다.

## 목표

LLM API가 느리거나 실패해도 서비스가 버티게 한다. 장애를 일부러 주입하고 Before/After를 잰다.

## 1. 구현 (Resilience4j)

타임아웃·재시도·서킷 브레이커·Bulkhead는 `LlmClient` 데코레이터로 붙인다. 폴백은 결과 형태가 달라 LlmClient에서 할 수 없으므로, `RecommendationService`에서 `CallNotPermittedException`(서킷 OPEN)이나 재시도 소진 예외를 잡아 `RuleOnlyPipeline`을 호출한다. 각 파이프라인 코드는 바꾸지 않는다.

| 장치 | 설정 (시작값, 측정 후 조정) |
| --- | --- |
| 타임아웃 | 15초 |
| 재시도 | 최대 2회, 지수 백오프 + 지터. **429, 5xx, 타임아웃만** 재시도. 4xx(400, 401)는 재시도하지 않음. 429의 `retry-after` 헤더가 있으면 따른다 |
| 서킷 브레이커 | 최근 20건 중 실패율 50% 이상이면 OPEN, 30초 후 HALF_OPEN |
| 동시 호출 제한 | Bulkhead. STEP 09의 워커 동시 처리 수와 같은 값 |
| 폴백 | 서킷 OPEN이거나 재시도 소진 시 규칙 기반 추천 |

### 규칙 기반 폴백 (`RuleOnlyPipeline`)

- LLM 없이 조건 코드 → `ADJUST` 규칙과 `R-05`, `R-06`의 대상 성분만 추천하고 (R-23처럼 "확인"을 위한 ASK 규칙은 추천이 아니라 경고로만 쓴다), 용량은 기준표로 계산, 사후 규칙 검증 적용
- 규칙으로 설명할 수 없는 성분은 넣지 않는다 (지어내지 않음)
- 결과에 `fallback = true`와 "AI 맞춤 추천을 잠시 사용할 수 없어 기본 안내를 보여드려요" 문구
- `ai_recommendations.model_name = "RULE_ONLY"`로 저장

- 네트워크 재시도는 `llm_call_logs.attempt_no`로, 서킷 상태 변화와 폴백은 Micrometer Counter로 남긴다

## 2. 장애 주입 (WireMock)

| 시나리오 | 내용 |
| --- | --- |
| normal | 정상, 지연 3초 |
| slow | 20% 요청이 20초 지연 |
| flaky | 타임아웃 10%, 5xx 5%, 429 5% |
| outage | 60초 동안 100% 5xx 후 정상 복구 |

- 각 시나리오를 WireMock 매핑 파일로 `perf/wiremock/`에 둔다
- k6로 같은 부하를 V9-queue(장애 대응 없음)와 V10에 보낸다

## 3. 측정

`docs/perf/STEP10-report.md`

| 지표 | 시나리오 | V9-queue | V10 |
| --- | --- | --- | --- |
| 최종 성공률 (폴백 포함 / 제외) | flaky | | |
| p95 | slow | | |
| 재시도로 늘어난 LLM 호출 수 (attempt_no ≥ 2) | flaky | - | |
| 서킷 OPEN까지 걸린 시간, 복구 후 정상화까지 걸린 시간 | outage | - | |
| 폴백 응답 비율 | outage | - | |
| 장애 중 스레드·큐 적체 | outage | | |

- 재시도가 비용과 rate limit에 주는 영향을 적는다 (재시도가 많을수록 429가 늘어나는지)
- `results.csv`에 시나리오별 한 줄씩

## 4. 완료 조건

- `./gradlew test` 통과 (재시도 대상/비대상 상태 코드, 서킷 상태 전이, 폴백 테스트)
- WireMock 매핑, k6 스크립트, 리포트, progress 기록
