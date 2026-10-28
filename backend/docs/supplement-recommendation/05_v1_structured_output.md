# STEP 05. V1 - 출력 형식 강제

> 먼저 `docs/prompts/00_COMMON.md`, `docs/prompts/reference/measurement.md`를 읽고 따른다. STEP 04가 끝난 상태에서 시작한다.

## 목표

V0의 파싱 실패를 없앤다. LLM 출력을 정해진 JSON 스키마로 강제하고, 실패하면 재요청한다.

| | Before (V0) | After (V1) |
| --- | --- | --- |
| 출력 형식 | 프롬프트에 "JSON으로 답해라" | tool use로 스키마 강제 |
| 파싱 실패 시 | 빈 결과 | 검증 실패 이유를 붙여 최대 1회 재요청 |
| 성분 이름 | 자유 텍스트 | 성분 코드 enum (`domain-rules.md` 1번 표) |

## 1. 구현

- `V1Pipeline` 추가 (V0는 그대로 둔다).
- Claude의 tool use로 `submit_recommendation` 도구를 정의하고 `tool_choice`로 이 도구를 반드시 쓰게 한다. 요청 형식은 공식 문서에서 확인한다.
- 스키마
  ```json
  {
    "recommendations": [
      { "ingredient": "VITAMIN_C", "reason": "string", "priority": 1, "amount": 125, "unit": "mg" }
    ],
    "summary": "string"
  }
  ```
  - `ingredient`는 enum, `priority`는 1부터 연속, `amount`·`unit`은 V1에서는 LLM이 채운다 (STEP 06에서 제거)
  - `amount`는 **현재 섭취량을 뺀 추가 섭취량**이라고 프롬프트에 명시한다. 평가셋 `expected.doses`와 같은 의미여야 V2와 비교할 수 있다. (V0 프롬프트에도 같은 문장을 넣는다)
- 응답 검증기 `RecommendationOutputValidator`: 스키마 위반, 알 수 없는 성분 코드, 중복 성분, priority 불연속을 잡는다.
- 검증 실패 시 실패 이유를 메시지에 붙여 1회 재요청. 재요청도 실패하면 `PARSE_FAIL`.
- 재요청은 `call_tag = RECOMMEND_REPAIR`로 기록한다 (`attempt_no`는 STEP 10의 네트워크 재시도 전용).
- 첫 호출의 검증 실패는 해당 로그 행의 outcome을 `VALIDATION_FAIL`로 갱신한다.
- 프롬프트 템플릿 `prompts/v1/`, `prompt_version = "v1.1"`.

## 2. 테스트

- Fake로: 정상, 스키마 위반 후 재요청 성공, 재요청도 실패
- 검증기 단위 테스트

## 3. 측정

1. `./gradlew evalRun -PpipelineVersion=V1 -Pconfirm=true`
2. `./gradlew evalRun -PpipelineVersion=V1 -Pmode=repro -Pconfirm=true`
3. `docs/perf/STEP05-report.md`에 V0 대비 표

| 지표 | V0 | V1 | 비고 |
| --- | --- | --- | --- |
| parse_fail_rate | | | |
| 재요청 발생률 | - | | call_tag=RECOMMEND_REPAIR 호출 수 / 케이스 수 |
| avg_input_tokens | | | tool 정의로 늘어난 양 |
| llm_p95_ms | | | 재요청 포함 |
| recall / violation_rate | | | 형식만 바꿨으므로 큰 변화가 없어야 정상 |

- recall이나 violation_rate가 크게 바뀌었다면 원인을 리포트에 적는다 (예: 성분 코드 enum으로 별칭 매핑 오류가 사라짐).

## 4. 완료 조건

- `./gradlew test` 통과
- `results.csv`에 V1 줄 2개
- 리포트, progress 기록
