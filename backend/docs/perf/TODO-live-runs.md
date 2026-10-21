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
