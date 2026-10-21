# STEP05 리포트 - V1 출력 형식 강제

## 바꾼 것 (Before → After)

| | V0 | V1 |
| --- | --- | --- |
| 출력 형식 | 프롬프트에 "JSON으로 답해라" | `submit_recommendation` 도구 (strict JSON Schema, 성분 코드 enum) |
| 파싱 실패 시 | 빈 결과 (재시도 없음) | `RecommendationOutputValidator`의 실패 이유를 붙여 1회 재요청 (`call_tag = RECOMMEND_REPAIR`) |
| 성분 이름 | 자유 텍스트 → 평가 시 별칭 표로 매핑 | 성분 코드 |
| 프롬프트 | `prompts/v0`, v0.1 | `prompts/v1`, v1.1 |

### 프롬프트와 다르게 한 것: `tool_choice`

STEP 05는 `tool_choice`로 도구 사용을 강제하라고 했지만, Anthropic 공식 문서(2026-10 기준)상 기본 모델 **Claude Opus 5.5와 Sonnet 5.5는 `tool_choice: {type: "tool"}` / `"any"`를 400으로 거부**한다. 그래서 V1은
1. `tool_choice: auto` + `strict: true` 도구 (도구를 쓰면 입력이 스키마대로 강제됨)
2. 시스템 프롬프트에 "반드시 submit_recommendation 도구를 한 번 호출해서 제출" 지시
3. 도구를 호출하지 않으면 검증 실패("도구를 호출하지 않았다")로 보고 1회 재요청

으로 구현했다. 강제를 받는 모델을 쓸 때는 `llm.anthropic.force-tool-choice=true`로 `{type: "tool"}`을 보낼 수 있다.

## 측정 조건

| 항목 | 값 |
| --- | --- |
| 실제 API (V1 전체, 재현성) | **실제 측정 대기** → [TODO-live-runs.md](TODO-live-runs.md) 05-1, 05-2 |
| 이번에 실행한 것 | Fake 실행 4번 (`-Pllm=fake`, `-Pllm=fake-noisy`, 각각 전체·재현성) |
| 평가셋 | 32케이스, reviewed = false |
| 날짜 | 2026-10-21 |

## 결과

### V0 대비 표 (실제 API) — 실제 측정 대기

| 지표 | V0 | V1 | 비고 |
| --- | --- | --- | --- |
| parse_fail_rate | 측정 안 함 | 측정 안 함 | |
| 재요청 발생률 | - | 측정 안 함 | RECOMMEND_REPAIR 호출 수 / 케이스 수 (results.csv note의 repair_calls) |
| avg_input_tokens | 측정 안 함 | 측정 안 함 | tool 정의 + 성분 목록으로 늘어나는 양 |
| llm_p95_ms | 측정 안 함 | 측정 안 함 | 재요청 포함 |
| recall / violation_rate | 측정 안 함 | 측정 안 함 | 형식만 바꿨으므로 큰 변화가 없어야 정상 |

### 메커니즘 확인 (Fake, 실제 측정값 아님) — results.csv `05,V1-*` 4줄

| label | parse_fail | 재요청 수 / 실행 수 | recall | violation |
| --- | --- | --- | --- | --- |
| V0-fake-noisy (비교) | 0.0313 | - | 0.7692 | 0.0938 |
| V1-fake-noisy | 0.0 | 1 / 32 | 0.7692 | 0.0938 |
| V0-repro-fake-noisy (비교) | 0.2 | - | 0.65 | 0.24 |
| V1-repro-fake-noisy | 0.0 | 10 / 50 | 0.6667 | 0.26 |

- 같은 비율(15%)로 형식을 깨뜨리는 Fake에서 V0는 그만큼 파싱 실패가 났고, V1은 재요청으로 모두 회복했다 (재요청 Fake는 정상 응답을 주도록 만들었으므로 회복률 100%는 설계상 당연하다. 실제 모델의 회복률은 실제 실행으로 확인해야 한다)
- recall·violation은 형식 변경과 무관하게 비슷하다. 재현성 실행에서 숫자가 조금 다른 이유는 Fake 난수 시드가 버전에 따라 달라서다 (내용 차이가 아님)
- Fake 토큰 수는 글자 수 추정치라 V0와 V1 입력 토큰 비교(tool 정의 증가분)에 쓰지 않는다

## 해석

- 실제 지표는 아직 없다. V1의 기대 효과는 parse_fail_rate 감소와 성분 이름 매핑 오류 제거(별칭 표 불필요)다.
- 비용 측면에서 tool 정의(성분 enum 14개 포함)와 tool use 시스템 안내(가격표 기준 auto 선택 시 286토큰)만큼 입력 토큰이 늘어난다.

## 한계 / 다음에 할 것

- 실제 API 실행 → TODO 05-1, 05-2
- V1도 용량은 LLM이 정한다. STEP 06에서 기준표 계산으로 바꾼다.
