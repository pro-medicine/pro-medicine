# STEP 04. 측정 인프라 + 평가셋 + V0 기준선 측정

> 먼저 `docs/prompts/00_COMMON.md`, `docs/prompts/reference/measurement.md`를 읽고 따른다. STEP 03이 끝난 상태에서 시작한다.

## 목표

이후 모든 STEP이 같은 방식으로 Before/After를 잴 수 있는 도구를 만들고, V0의 기준 수치를 남긴다.
**이 STEP의 결과물이 프로젝트에서 가장 중요하다.** 측정이 믿을 만해야 이후 개선 수치도 의미가 있다.

## 1. 단계별 지연 측정 (서버)

- Micrometer + Actuator(`/actuator/prometheus`)를 켠다.
- 추천 처리 단계마다 `Timer`를 단다. 이름은 `recommendation.stage`, 태그는 `stage`, `pipeline_version`.
  - `assemble`(입력 조립), `llm`(LLM 호출), `parse`(파싱), 이후 STEP에서 `calc`, `rule_pre`, `rule_post`, `cache`가 추가된다
- 전체 처리 시간은 `recommendation.total` Timer.
- 단계 측정은 AOP나 헬퍼(`StageTimer.record("llm", () -> ...)`)로 한 곳에서 처리한다. 서비스 코드에 측정 코드를 흩어 놓지 않는다.
- Grafana 대시보드는 선택이다. 수치는 Prometheus 쿼리나 Actuator 결과로도 충분하다.

## 2. 평가셋

- `reference/measurement.md` 3번 형식대로 `src/test/resources/eval/cases.json`을 만든다.
- 기대값은 `reference/domain-rules.md`의 규칙·기준값에서만 도출하고, 케이스마다 `rationale`에 적용한 규칙 ID를 적는다.
- 최소 30케이스. 구성 비율은 measurement.md를 따른다.
- 파일 형식은 `{ "meta": { "version", "reviewed", "reproCaseIds" }, "cases": [...] }` (measurement.md 3번).
- `ingredient-aliases.json`: LLM이 다양한 이름("비타민C", "Vitamin C", "아스코르브산")으로 답해도 성분 코드로 바꾸는 별칭 표.

## 3. 평가 러너

- 위치: `src/test/java/.../eval/EvalRunner` (또는 별도 sourceSet `eval`). 일반 테스트와 분리한다.
- Gradle 태스크: `./gradlew evalRun -PpipelineVersion=V0 -Pcases=30 -Prepeats=1 -Pconfirm=true`
  - 속성 이름은 `pipelineVersion`이다. `version`은 Gradle 예약 속성이라 쓰지 않는다.
  - `-Pconfirm=true`가 없으면 예상 호출 수와 예상 비용만 출력하고 끝낸다.
  - `-Pllm=fake`면 FakeLlmClient로 돌린다 (러너 자체 검증용).
- 동작:
  1. 케이스를 `RecommendationInput`으로 변환해 지정한 버전의 파이프라인을 직접 실행한다 (HTTP 거치지 않음)
  2. `eval_run_id`를 만들어 `llm_call_logs`와 연결한다
  3. measurement.md의 품질 지표를 계산한다
  4. `llm_call_logs`에서 토큰·지연을 집계한다
  5. `docs/perf/results.csv`에 한 줄 추가하고, 케이스별 상세 결과는 `docs/perf/runs/{eval_run_id}.json`에 저장한다
- 재현성 측정: `./gradlew evalRun -PpipelineVersion=V0 -Pmode=repro -Pconfirm=true` → `meta.reproCaseIds` 10케이스 × 5회

## 4. 러너 검증 (실제 API 쓰기 전)

- `-Pllm=fake`로 정답을 그대로 돌려주는 Fake를 넣었을 때 recall=1, violation_rate=0이 나오는지 테스트한다.
- 일부러 틀린 답을 주는 Fake로 지표가 예상대로 나빠지는지 테스트한다.
- 지표 계산 함수 단위 테스트 (빈 추천, 중복 성분, 별칭 매핑)

## 5. V0 기준선 측정

1. `./gradlew evalRun -PpipelineVersion=V0 -Pconfirm=true` (전체 케이스 1회)
2. `./gradlew evalRun -PpipelineVersion=V0 -Pmode=repro -Pconfirm=true`
3. 결과를 `docs/perf/STEP04-report.md`에 정리. 특히 아래를 꼭 적는다
   - parse_fail_rate가 얼마인지, 실패한 응답이 어떤 모양이었는지 (본문 저장 없이 유형만)
   - violation_rate: 흡연자에게 베타카로틴, 혈압약 복용자에게 칼륨을 추천한 케이스 수
   - 같은 케이스에서 용량이 실행마다 얼마나 흔들렸는지
   - 평가셋 검토 여부 (`reviewed`)

## 6. 완료 조건

- `./gradlew test` 통과 (러너 단위 테스트 포함, 실제 API 호출 없음)
- `docs/perf/results.csv`에 V0 줄 2개 (전체, 재현성)
- `docs/perf/STEP04-report.md`, `docs/progress.md` 기록
- 사용자에게 평가셋 검토를 요청하는 문장을 progress에 남긴다
