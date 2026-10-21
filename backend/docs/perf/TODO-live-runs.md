# 실제 API로 직접 실행할 명령 (실제 측정 대기)

밤새 실행은 실제 API 예산 0달러라 Claude API를 한 번도 부르지 않았다. 아래 명령을 **STEP 순서대로** 직접 실행하면 각 리포트의 "실제 측정 대기" 칸을 채울 수 있다.

## 공통 준비

1. Anthropic Console에서 API 키 발급 → PowerShell: `setx ANTHROPIC_API_KEY "발급받은-키"` → 터미널·IntelliJ 다시 열기
2. 모든 명령은 **`backend` 폴더**에서 실행한다 (`cd backend`). Git Bash면 `sh ./gradlew`, PowerShell이면 `.\gradlew.bat`
3. 단가: [pricing.md](pricing.md) (2026-10-21 확인). 비용은 모델에 따라 크게 다르다 (기본 `claude-opus-5-5`).
4. `evalRun`은 `-Pconfirm=true`가 없으면 예상 호출 수·비용만 출력하고 끝난다. 먼저 confirm 없이 돌려 예상치를 확인한다.

## STEP 03 - 연결 확인

| # | 명령 | 예상 호출 수 | 확인할 것 |
| --- | --- | ---: | --- |
| 03-1 | `./gradlew liveTest` | 1 | 연결, 토큰 수 출력 |
| 03-2 | 서버 실행 `./gradlew bootRun --args='--spring.profiles.active=local-llm --recommendation.pipeline-version=V0'` 후 설문 제출 → `POST /api/v1/recommendations` 3회 | 3 | `llm_call_logs`의 latency_ms, input/output_tokens → `STEP03-report.md` 표 |

03-2 전에 로컬 MySQL에 시드를 한 번 넣는다: `docs/sql/STEP02_seed_survey_v1.sql`, `docs/sql/STEP02_seed_drugs.sql`
(STEP 06 이후는 `STEP06_seed_ingredient_standards.sql`, STEP 07 이후는 `STEP07_seed_recommendation_rules.sql`도).

확인 쿼리:

```sql
SELECT pipeline_version, model, latency_ms, input_tokens, output_tokens, outcome
FROM llm_call_logs ORDER BY llm_call_log_id DESC LIMIT 3;
```

## STEP 04 - V0 기준선

먼저 평가셋(`src/test/resources/eval/cases.json`)을 검토하고 `meta.reviewed`를 `true`로 바꾼 뒤 실행하는 것을 권장한다.

| # | 명령 | 예상 호출 수 | 확인할 것 |
| --- | --- | ---: | --- |
| 04-0 | `./gradlew evalRun -PpipelineVersion=V0` | 0 | 예상 호출 수·비용 출력만 |
| 04-1 | `./gradlew evalRun -PpipelineVersion=V0 -Pconfirm=true` | 32 | results.csv `04,V0` 줄 |
| 04-2 | `./gradlew evalRun -PpipelineVersion=V0 -Pmode=repro -Pconfirm=true` | 50 | results.csv `04,V0-repro` 줄 |

결과 상세: `docs/perf/runs/{eval_run_id}.json` (케이스별 추천 성분·용량·실패 유형)

## STEP 05 - V1 출력 형식 강제

| # | 명령 | 예상 호출 수 | 확인할 것 |
| --- | --- | ---: | --- |
| 05-1 | `./gradlew evalRun -PpipelineVersion=V1 -Pconfirm=true` | 32 ~ 64 (재요청 포함) | parse_fail_rate, note의 repair_calls, avg_input_tokens (V0 대비) |
| 05-2 | `./gradlew evalRun -PpipelineVersion=V1 -Pmode=repro -Pconfirm=true` | 50 ~ 100 | reproducibility_set / dose |

Opus 5.5에서 `tool_choice` 강제가 400이면 정상 동작(기본값 auto)이다. 다른 모델로 강제 비교를 하려면 `--llm.anthropic.force-tool-choice=true`.

## STEP 06 - V2 용량 계산 분리

로컬 서버로 확인하려면 먼저 `docs/sql/STEP06_seed_ingredient_standards.sql` 실행 (평가 러너는 H2 테스트 픽스처를 쓰므로 필요 없음).

| # | 명령 | 예상 호출 수 | 확인할 것 |
| --- | --- | ---: | --- |
| 06-1 | `./gradlew evalRun -PpipelineVersion=V2 -Pconfirm=true` | 32 ~ 64 | dose_error_rate, over_limit_rate, avg_output_tokens (V1 대비) |
| 06-2 | `./gradlew evalRun -PpipelineVersion=V2 -Pmode=repro -Pconfirm=true` | 50 ~ 100 | reproducibility_dose |

## STEP 07 - V3 · V4 규칙 검증

로컬 서버로 확인하려면 먼저 `docs/sql/STEP07_seed_recommendation_rules.sql` 실행.

| # | 명령 | 예상 호출 수 | 확인할 것 |
| --- | --- | ---: | --- |
| 07-1 | `./gradlew evalRun -PpipelineVersion=V3 -Pconfirm=true` | 32 ~ 64 | violation_rate = 0, raw_violation_rate |
| 07-2 | `./gradlew evalRun -PpipelineVersion=V3 -Pmode=repro -Pconfirm=true` | 50 ~ 100 | |
| 07-3 | `./gradlew evalRun -PpipelineVersion=V4 -Pconfirm=true` | 32 ~ 64 | raw_violation_rate (V3 대비 감소), avg_input_tokens |
| 07-4 | `./gradlew evalRun -PpipelineVersion=V4 -Pmode=repro -Pconfirm=true` | 50 ~ 100 | |

규칙별 적중 횟수: `docs/perf/runs/{eval_run_id}.json`의 `ruleHits`

## STEP 08 - 비용 최적화

| # | 명령 | 예상 호출 수 | 확인할 것 |
| --- | --- | ---: | --- |
| 08-1 | `./gradlew evalRun -PpipelineVersion=V6 -Ppasses=2 -Pconfirm=true` | 32 ~ 64 (2회차는 0) | pass1/pass2 두 줄, cache_hit_rate, cost_per_req_usd |
| 08-2 | `./gradlew evalRun -PpipelineVersion=V7 -Pconfirm=true` | 32 ~ 64 | avg_cache_read_tokens, note의 avg_cache_creation_tokens (0이면 고정 부분이 최소 길이 미만) |
| 08-3 | `./gradlew evalRun -PpipelineVersion=V8 -Pconfirm=true` | 32 ~ 64 (작은 모델 13케이스) | note의 calls_by_model, recall_by_routed_model, cost_per_req_usd |
| 08-4 | k6 (WireMock, 비용 없음): `perf/k6/cache-load.js` 머리말의 준비 순서대로 V4, V6 서버에 각각 실행 | 0 (WireMock) | TPS, p95, WireMock 호출 수 |

k6 설치: `winget install k6 --source winget`. 08-4는 실제 API를 쓰지 않는다 (k6가 없어서 밤새 실행에서는 in-JVM 대체 측정만 했다).

## STEP 09 - 처리 구조 · 알림 몰림

| # | 명령 | 예상 호출 수 | 확인할 것 |
| --- | --- | ---: | --- |
| 09-0 | Anthropic Console에서 계정 rate limit(분당 요청·토큰) 확인 → `recommendation.worker.concurrency`, `llm.resilience.bulkhead-max-concurrent` 조정 | 0 | progress "결정한 것"에 값 기록 |
| 09-1 | k6 (WireMock, 비용 없음): `perf/k6/spike.js` 머리말 순서대로 sync / vt / queue 서버 × steady / spike-burst / spike-spread | 0 | TPS, p95, 에러율, complete_ms |
