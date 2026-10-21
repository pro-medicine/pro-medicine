# STEP04 리포트 - 측정 인프라 + 평가셋 + V0 기준선

## 바꾼 것 (Before → After)

| | Before | After |
| --- | --- | --- |
| 단계별 지연 | 없음 | Micrometer `recommendation.stage{stage, pipeline_version}`(assemble, llm, parse), `recommendation.total`, `/actuator/prometheus` |
| 평가셋 | 없음 | `src/test/resources/eval/cases.json` 32케이스 (규칙 1개 12 / 여러 개 10 / 없음 5 / 비회원 5), 재현성 10케이스 고정 |
| 평가 러너 | 없음 | `./gradlew evalRun` (품질 지표 + `llm_call_logs` 집계 → `results.csv`, `runs/{eval_run_id}.json`) |

## 측정 조건

| 항목 | 값 |
| --- | --- |
| 실제 API 기준선 (V0 전체 1회, 재현성 10×5) | **실제 측정 대기** → [TODO-live-runs.md](TODO-live-runs.md) 04-1, 04-2 |
| 이번에 실행한 것 | 러너 검증용 Fake 실행 4번 (아래) |
| 평가셋 검토 | **reviewed = false** (사용자 검토 전) |
| 모델 | 설정 `claude-opus-5-5` (Fake 실행의 model 칸은 `-fake`) |
| 날짜 | 2026-10-21 |

실행 명령 (`backend` 폴더):

```text
./gradlew evalRun -PpipelineVersion=V0                         # 실제 API: 예상치만 출력 (호출 32회, 약 $1.54 추정)
./gradlew evalRun -PpipelineVersion=V0 -Pllm=fake              # 정답 Fake (러너 검증)
./gradlew evalRun -PpipelineVersion=V0 -Pllm=fake -Pmode=repro
./gradlew evalRun -PpipelineVersion=V0 -Pllm=fake-noisy        # 일부러 틀리는 Fake (지표가 나빠지는지 확인)
./gradlew evalRun -PpipelineVersion=V0 -Pllm=fake-noisy -Pmode=repro
```

## 결과

### V0 실제 기준선 (실제 측정 대기)

| 지표 | V0 전체 | V0 재현성 |
| --- | --- | --- |
| recall / precision | 측정 안 함 | 측정 안 함 |
| violation_rate (= raw, 규칙 없음) | 측정 안 함 | 측정 안 함 |
| parse_fail_rate | 측정 안 함 | 측정 안 함 |
| dose_error_rate | 측정 안 함 | 측정 안 함 |
| reproducibility_set / dose | - | 측정 안 함 |
| llm_p50 / p95, 토큰, 요청당 비용 | 측정 안 함 | 측정 안 함 |

### 러너 검증 (Fake, 실제 측정값 아님) — results.csv 2~5행

| label | recall | precision | violation | dose_error (n) | repro set/dose | parse_fail |
| --- | --- | --- | --- | --- | --- | --- |
| V0-fake | 1.0 | 1.0 | 0.0 | 0.0 (10) | - | 0.0 |
| V0-repro-fake | 1.0 | 1.0 | 0.0 | 0.0 (35) | 1.0 / 1.0 | 0.0 |
| V0-fake-noisy | 0.7692 | 0.9286 | 0.0938 | 0.125 (8) | - | 0.0313 |
| V0-repro-fake-noisy | 0.65 | 0.7931 | 0.24 | 0.28 (25) | 0.5 / 0.48 | 0.2 |

- 정답 Fake에서 recall 1, violation 0, dose_error 0, 재현성 1이 나왔다 → 지표 계산과 별칭 매핑이 기대대로 동작
- 일부러 틀리는 Fake에서는 모든 지표가 나빠졌다. 파싱 실패 유형은 본문 없이 `failureReasons`로 남는다 (예: `NO_JSON_OBJECT=10`)
- 단위 테스트 `EvalMetricsTest`(빈 추천, 중복 성분, 단위 불일치, 재현성, 별칭), `EvalRunnerTest`(정답·오답 Fake, 재현성 10×5)

## 실제 실행 후 이 리포트에 적을 것 (STEP 04 5번)

- parse_fail_rate와 실패 유형: `runs/{eval_run_id}.json`의 `summary.failureReasons` (NO_JSON_OBJECT, INVALID_JSON, MISSING_RECOMMENDATIONS, EMPTY_NAME)
- violation: 흡연자 베타카로틴(E-001, E-011, E-013, E-015, E-020, E-029), 혈압약 칼륨(E-006, E-010, E-016, E-020) 케이스의 `runs[].finalCodes`
- 용량 흔들림: 재현성 실행의 `runs[].doses`를 케이스별로 비교
- 평가셋 검토 여부

## 해석

- 측정 도구는 갖췄지만 V0 기준 수치는 아직 없다. 이후 STEP의 Before/After 비교도 실제 실행이 있어야 의미가 있다.
- 평가셋 기대값은 `domain-rules.md` 규칙·기준값으로만 만들었다 (`gen` 규칙: mustInclude = ADJUST + R-05·R-06, mustExclude = EXCLUDE + 비회원 R-G*, 용량 = 기준값 − 현재 섭취). 원하는 효과(Q-GOAL) → mayInclude 매핑만 근거 출처가 없어 `meta.goalMayInclude`에 따로 적었다. **precision에만 영향**을 주므로 검토 후 고칠 수 있다.

## 지표 계산에서 정한 것

- precision은 케이스 평균이 아니라 전체 실행 합산 (추천 수 − 허용 밖) / 추천 수
- dose_error_rate 분모는 "기대 용량이 있는 성분 중 실제로 추천된 항목". 추천하지 않은 성분은 recall에서 다룬다
- reproducibility는 "첫 결과와 같은 횟수 / 반복 횟수"로 첫 결과를 포함 (5회면 최솟값 0.2)
- 별칭 표에 없는 이름은 허용 밖 성분으로 센다 (precision 하락)

## 한계 / 다음에 할 것

- 실제 API 실행 → TODO 04-1, 04-2
- 평가셋 검토 후 `meta.reviewed = true`
