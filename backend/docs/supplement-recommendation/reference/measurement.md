# 측정 기준 (모든 STEP 공통)

## 1. 지표

### 품질 (평가 러너가 측정)

| 지표 | 정의 | 계산 |
| --- | --- | --- |
| recall | 꼭 나와야 할 성분을 맞춘 비율 | 케이스별 (맞춘 mustInclude 수 / mustInclude 수)의 평균. **mustInclude가 빈 케이스는 평균에서 뺀다** |
| precision | 추천한 성분 중 허용된 성분 비율 | (추천 수 − 허용 밖 성분 수) / 추천 수. 허용 = mustInclude + mayInclude |
| violation_rate | 최종 결과에 나오면 안 되는 성분이 포함된 케이스 비율 | 최종 결과(제외 처리 후)에 mustExclude가 하나라도 있는 케이스 수 / 전체 케이스 수 |
| over_limit_rate | 최종 추천량 + 현재 섭취량이 상한을 넘은 항목 비율 | 상한 초과 항목 수 / 용량이 있는 항목 수 (STEP 06부터) |
| dose_error_rate | 기대 용량과 다른 비율 | 기대 용량과 다른 항목 수 / 기대 용량이 있는 항목 수. 같은 단위에서 차이 ±0.5 이내면 일치, 단위가 다르거나 용량이 없으면 오류. 리포트에 분모 n을 적는다. V0부터 계산 |
| reproducibility_set | 같은 입력 반복 시 성분 집합이 같은 비율 | 반복 실행에서 첫 결과와 같은 성분 집합이 나온 횟수 / 반복 횟수, 케이스 평균 |
| reproducibility_dose | 같은 입력 반복 시 성분 집합과 용량이 모두 같은 비율 | 위와 같되 용량까지 같아야 동일 |
| parse_fail_rate | 최종 결과를 해석하지 못한 케이스 비율 | 재요청까지 다 한 뒤에도 결과가 파싱·검증 실패인 케이스 수 / 케이스 수 (호출 수가 아니라 케이스 기준이라 V0와 V1을 비교할 수 있다) |
| raw_violation_rate | 규칙 검증 **전** LLM 출력의 위반 비율 | 사후 규칙 적용 전 LLM 출력에 mustExclude가 하나라도 있는 케이스 수 / 전체 케이스 수. `RecommendationResult`는 제외 전 목록을 함께 보관한다. V0~V2는 규칙이 없으므로 raw = violation |

### 성능·비용 (서버 계측 + 부하 테스트)

| 지표 | 측정 위치 |
| --- | --- |
| latency p50 / p95 / p99 (전체, 단계별) | Micrometer Timer, k6 |
| LLM 호출 수, 재시도 수 | `llm_call_logs` |
| input / output / cache_read 토큰 (요청당 평균) | `llm_call_logs` |
| 요청당 추정 비용 | 토큰 × 단가. 단가는 공식 가격표에서 확인해 `docs/perf/pricing.md`에 날짜와 함께 적는다 |
| 캐시 적중률 | Micrometer Counter |
| TPS, 에러율 | k6 |

## 2. 기록 형식

`docs/perf/results.csv` (헤더는 STEP 04에서 만든다. 해당 없는 칸은 비운다)

```csv
step,label,date,model,cases,repeats,recall,precision,violation_rate,raw_violation_rate,over_limit_rate,dose_error_rate,reproducibility_set,reproducibility_dose,parse_fail_rate,llm_p50_ms,llm_p95_ms,e2e_p95_ms,avg_input_tokens,avg_output_tokens,avg_cache_read_tokens,cost_per_req_usd,cache_hit_rate,tps,error_rate,note
```

`docs/perf/STEPxx-report.md`

```markdown
# STEPxx 리포트 - 제목
## 바꾼 것 (Before → After)
## 측정 조건 (모델, 케이스 수, 반복 수, 실행 명령, 날짜)
## 결과 (results.csv의 해당 줄을 표로)
## 해석 (수치가 왜 바뀌었는지, 예상과 다른 점)
## 한계 / 다음에 할 것
```

## 3. 평가셋 형식

위치: `src/test/resources/eval/cases.json`

```json
{
  "meta": { "version": "1", "reviewed": false, "reproCaseIds": ["E-001", "E-004", "…10개"] },
  "cases": [
  {
    "caseId": "E-001",
    "tags": ["SMOKING_CURRENT", "HARD"],
    "difficulty": "EASY | NORMAL | HARD",
    "input": {
      "respondentType": "MEMBER",
      "profile": { "gender": "M", "birthYear": 1998, "smoking": "HALF_PACK", "drinking": "HIGH" },
      "conditions": ["HYPERTENSION"],
      "medications": [{ "drugId": "<ACE_INHIBITOR 계열 시드 drug_id>" }],
      "answers": { "Q-SLEEP": "POOR" },
      "currentIntake": [{ "ingredient": "MAGNESIUM", "amount": 30, "unit": "mg" }]
    },
    "expected": {
      "mustInclude": ["VITAMIN_C"],
      "mayInclude": ["MAGNESIUM", "VITAMIN_B1"],
      "mustExclude": ["BETA_CAROTENE", "POTASSIUM"],
      "doses": [{ "ingredient": "VITAMIN_C", "amount": 125, "unit": "mg" }]
    },
    "rationale": "R-01, R-02, R-13 적용. 출처는 domain-rules.md"
  }
  ]
}
```

- 입력 값은 STEP 02 설문의 `value_code`를 그대로 쓴다 (예: smoking = NONE, HALF_PACK, ONE_PACK, TWO_PACK_PLUS).

- 기대값은 `domain-rules.md`의 규칙과 기준값에서만 도출한다. 규칙으로 설명할 수 없는 기대값은 넣지 않는다.
  - `mustInclude` = 그 입력에 걸리는 `ADJUST` 규칙과 R-05, R-06의 대상 성분만
  - `mustExclude` = 그 입력에 걸리는 `EXCLUDE` 규칙 대상 + 비회원이면 `R-G*` 대상만 (WARN·TIMING 대상은 넣지 않는다)
  - `mayInclude` = 위 둘에 없지만 추천돼도 괜찮은 성분 (WARN·TIMING 대상, 원하는 효과와 관련된 성분)
  - `doses` = **추가 섭취량** (권장량 − 현재 섭취량, 0 미만이면 0). 기준값 출처가 있는 성분만 채운다
- 최소 30케이스: 규칙 하나만 걸리는 케이스 10, 규칙 여러 개가 겹치는 케이스 10, 걸리는 규칙이 없는 일반 케이스 5, 비회원 5.
- 평가셋은 사용자가 검토하기 전까지 `meta.reviewed = false`로 둔다. 리포트에도 검토 여부를 적는다.
- 재현성 측정용 10케이스는 `meta.reproCaseIds`에 고정한다. 규칙이 걸리는 케이스와 안 걸리는 케이스를 섞는다.

## 4. 비용 통제

- 실제 API 평가는 `./gradlew evalRun -Pcases=N -Prepeats=R` 형태로만 돌린다. 기본값은 cases=전체, repeats=1.
- 재현성 측정은 `meta.reproCaseIds`의 10케이스 × 5회로 고정한다.
- 실행 전 예상 호출 수와 예상 비용을 출력하고, `-Pconfirm=true`가 없으면 실행하지 않는다.
