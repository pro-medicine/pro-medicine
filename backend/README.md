# backend - AI 영양제 추천 (설문 · 추천 파이프라인)

팀 공통 README는 저장소 루트 `README.md`. 이 문서는 AI 추천 기능(설문 → 추천) 실행·평가·부하 테스트 방법이다.
단계별 결과는 [docs/perf/SUMMARY.md](docs/perf/SUMMARY.md), 진행 기록은 [docs/progress.md](docs/progress.md).

## 1. 실행

### 필요한 것

| 항목 | 용도 | 비고 |
| --- | --- | --- |
| Java 21 | 빌드·실행 | |
| MySQL | 로컬 DB | `.env`의 `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD` (팀 공통 설정) |
| `ANTHROPIC_API_KEY` 환경변수 | 실제 Claude API | `local-llm` 프로필에서만. 코드·설정 파일에 키를 쓰지 않는다 |
| Redis (선택) | 결과 캐시·큐를 Redis로 쓸 때 | 기본은 메모리. `docker run -d -p 6379:6379 redis:7-alpine` |
| Docker (선택) | Redis, WireMock, k6 | |

### 시드 (로컬 MySQL에 한 번)

앱 시작 시 자동으로 넣지 않는다. 순서대로 실행 (모두 예시 데이터):

```text
docs/sql/STEP02_seed_survey_v1.sql
docs/sql/STEP02_seed_drugs.sql
docs/sql/STEP06_seed_ingredient_standards.sql
docs/sql/STEP07_seed_recommendation_rules.sql
```

테이블·컬럼은 `ddl-auto: update`로 자동 반영된다. 공유 DB용 DDL은 `docs/sql/STEPxx_*.sql`, 변경 목록은 [docs/schema-changes.md](docs/schema-changes.md).

### 서버

```powershell
# 기본: LLM = Fake (API 키 없이 동작, 고정 응답)
.\gradlew.bat bootRun

# 실제 Claude API (application-local-llm.yml)
.\gradlew.bat bootRun --args='--spring.profiles.active=local-llm --recommendation.pipeline-version=V4'
```

주요 설정 (기본값은 `src/main/resources/config/recommendation-defaults.properties`와 각 `*Properties` 클래스):

| 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `llm.provider` | fake | `anthropic`이면 실제 API (`local-llm` 프로필) |
| `llm.model` / `llm.small-model` | claude-opus-5-5 / claude-haiku-5-5 | 단가: [docs/perf/pricing.md](docs/perf/pricing.md) |
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
.\gradlew.bat liveTest      # 실제 Claude API 1회 (비용 발생)
```

## 3. 평가 (품질 지표 → docs/perf/results.csv)

평가셋 `src/test/resources/eval/cases.json` (32케이스, 검토 후 `meta.reviewed = true`).

```powershell
.\gradlew.bat evalRun -PpipelineVersion=V4                       # 예상 호출 수·비용만 출력
.\gradlew.bat evalRun -PpipelineVersion=V4 -Pconfirm=true        # 실제 API 실행
.\gradlew.bat evalRun -PpipelineVersion=V4 -Pmode=repro -Pconfirm=true   # 재현성 10케이스 × 5회
.\gradlew.bat evalRun -PpipelineVersion=V6 -Ppasses=2 -Pconfirm=true     # 결과 캐시 1회차·2회차
.\gradlew.bat evalRun -PpipelineVersion=V4 -Pllm=fake            # 러너 검증 (Fake, 비용 없음)
.\gradlew.bat evalRun -PpipelineVersion=RULE_ONLY                # 규칙 기반 폴백 (LLM 미사용)
```

실제 API로 돌릴 명령 목록: [docs/perf/TODO-live-runs.md](docs/perf/TODO-live-runs.md).

## 4. 부하 테스트 (LLM = WireMock, 비용 없음)

### in-JVM (k6 없이)

```powershell
.\gradlew.bat perfTest --tests '*CacheLoadPerfTest*'      # STEP 08: V4 vs V6 결과 캐시
.\gradlew.bat perfTest --tests '*StructureLoadPerfTest*'  # STEP 09: sync / 가상 스레드 / 큐
.\gradlew.bat perfTest --tests '*FaultLoadPerfTest*'      # STEP 10: 장애 주입 V9-queue vs V10
```

### k6

`perf/k6/cache-load.js`, `perf/k6/spike.js` 머리말에 WireMock·서버 실행 명령이 있다. 요청 세트: `node perf/k6/gen-requests.js`, 장애 매핑: `node perf/wiremock/gen-scenarios.js`.

## 5. 결과 차트

```powershell
node docs/perf/charts/make-charts.js    # results.csv → docs/perf/charts/*.svg
node docs/perf/verify-summary.js        # SUMMARY.md 수치와 results.csv 대조
```
