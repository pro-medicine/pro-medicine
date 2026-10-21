# STEP07 리포트 - V3 · V4 규칙 검증 (사후 → 사전)

## 바꾼 것 (Before → After)

| 버전 | 바뀌는 점 |
| --- | --- |
| V2 (Before) | 규칙 없음. LLM이 금지 성분을 내면 그대로 나간다 |
| V3 | LLM 출력 **뒤** `RuleEngine.applyPost`로 제외·경고 (rule_post). 프롬프트는 V2와 같음(v2.1) |
| V4 | V3 + LLM 호출 **전** `RuleEngine.preFilter`의 금지 성분 목록을 사용자 메시지 뒤에 넣음 (rule_pre, v4.1). 사후 검증은 유지 |

- 스키마: `recommendation_rules`(27행 = 회원 23 + 비회원 4, domain-rules.md 3번 표 그대로), `ai_recommendation_items`에 `excluded`, `applied_rule_ids`, `warnings` ([schema-changes.md](../schema-changes.md))
- 제외된 성분도 `excluded = true`로 저장 → LLM 원래 출력(raw_violation_rate)을 다시 계산할 수 있다. API 응답은 `items`(최종)와 `excludedItems`(제외와 이유)로 나눈다
- 조건 코드 매핑은 `ConditionCodeResolver` 한 곳 (STEP 07 2번 표 전체)
- 규칙 적중 Counter `recommendation.rule.hit{rule_id, phase=pre|post}`, 로그에는 규칙 ID만

## 측정 조건

| 항목 | 값 |
| --- | --- |
| 실제 API (V3·V4 전체, 재현성) | **실제 측정 대기** → [TODO-live-runs.md](TODO-live-runs.md) 07-1 ~ 07-4 |
| 이번에 실행한 것 | V3·V4 각각 Fake 4번 (fake, fake-noisy × 전체·재현성) |
| fake-noisy의 금지 성분 | 케이스의 mustExclude 중 하나를 40% 확률로 섞는다. 프롬프트에 "추천하지 마라" 목록이 있으면 5%로 낮춘다 (V4 효과를 흉내 낸 가정. 실제 모델의 준수율이 아니다) |
| 날짜 | 2026-10-21 |

## 결과

### V2 · V3 · V4 비교 (실제 API) — 실제 측정 대기

| 지표 | V2 | V3 | V4 | 비고 |
| --- | --- | --- | --- | --- |
| violation_rate (최종) | 측정 안 함 | 측정 안 함 | 측정 안 함 | V3부터 0이어야 정상 |
| raw_violation_rate | - | 측정 안 함 | 측정 안 함 | V4에서 줄어드는 게 핵심 |
| 규칙별 걸린 횟수 상위 5 | - | 측정 안 함 | 측정 안 함 | `runs/*.json`의 ruleHits |
| recall | 측정 안 함 | 측정 안 함 | 측정 안 함 | 제외 때문에 떨어졌는지 |
| avg_input_tokens | 측정 안 함 | 측정 안 함 | 측정 안 함 | 금지 목록 추가분 |
| e2e_p95_ms | 측정 안 함 | 측정 안 함 | 측정 안 함 | |

### 메커니즘 확인 (Fake-noisy, 실제 측정값 아님) — results.csv `06,V2-*`, `07,V3-*`, `07,V4-*`

| 지표 | V2-fake-noisy | V3-fake-noisy | V4-fake-noisy | V2 repro | V3 repro | V4 repro |
| --- | --- | --- | --- | --- | --- | --- |
| violation_rate (최종) | 0.0938 | **0.0** | **0.0** | 0.26 | **0.0** | **0.0** |
| raw_violation_rate | 0.0938 | 0.0938 | 0.0 | 0.26 | 0.26 | 0.0 |
| recall | 0.7692 | 0.7692 | 0.7692 | 0.6667 | 0.6667 | 0.6667 |
| precision | 0.9286 | 1.0 | 1.0 | 0.8060 | 1.0 | 1.0 |

- 같은 시드(같은 실수 패턴)에서 V3는 최종 위반을 0으로 만들고, LLM의 원래 위반(raw)은 그대로 남아 기록된다
- V4는 Fake 가정상 프롬프트 금지 목록이 있으면 금지 성분을 덜 넣으므로 raw가 0이 됐다. **이 숫자는 가정에서 나온 것**이고, 실제 모델이 금지 목록을 얼마나 따르는지는 실제 실행으로만 알 수 있다
- recall은 그대로다. 규칙이 제외하는 성분(EXCLUDE 대상)은 평가셋에서 mustInclude가 될 수 없기 때문 (mustInclude는 ADJUST·R-05·R-06 대상)
- precision이 1.0으로 오른 것은 허용 밖 성분(금지 성분)이 최종 목록에서 빠졌기 때문

### 규칙별 걸린 횟수 (V3-repro-fake-noisy, 50회, phase=post) 상위 5

| 규칙 | 횟수 | 내용 |
| --- | ---: | --- |
| R-01 | 10 | 흡연 → 비타민 C ADJUST |
| R-07 | 8 | 임신 가능성 → 엽산 ADJUST |
| R-02 | 5 | 흡연 → 베타카로틴 EXCLUDE |
| R-04 | 5 | 커피 → 철분 TIMING |
| R-03 / R-21 / R-23 | 5 | 음주 B1 WARN / 철분-칼슘 TIMING / 성인 남성 철분 ASK |

V4에서는 같은 실행에서 사전 단계(pre) 적중이 추가된다: R-02 15, R-G1~G4 각 10, R-13 5 (`runs/V4-repro-fake-noisy-*.json`).

### 단계 비용 (로컬 JVM, `runs/V3-*`, `runs/V4-*`의 stageTimings)

| 실행 | rule_pre 평균 / p95 | rule_post 평균 / p95 | rule_post 최대 |
| --- | --- | --- | --- |
| V3-fake-noisy | - | 0.323 / 0.686 ms | 3.98 ms |
| V3-repro-fake-noisy | - | 0.257 / 0.621 ms | 4.14 ms |
| V4-fake-noisy | 0.204 / 0.752 ms | 0.370 / 0.553 ms | 5.41 ms |
| V4-repro-fake-noisy | 0.147 / 0.252 ms | 0.243 / 0.440 ms | 4.28 ms |

규칙 27개를 메모리에서 매칭하므로 평균·p95 모두 1ms 미만이다. 최대값은 첫 호출(JIT 전)이다.

## 해석

- 안전 판단(금지 성분)은 규칙으로 처리하므로 LLM 품질과 무관하게 최종 위반이 0이 된다. 0이 아니면 규칙이나 조건 매핑 버그다 (`EvalRunnerTest`·`RuleEngineTest`로 보호).
- V4의 가치는 "LLM이 처음부터 덜 틀리게" 해서 제외로 빈 자리가 생기는 것을 줄이는 것이다. 비용은 금지 목록만큼 입력 토큰이 늘어나는 것 (비회원은 항상 4개 → 매 요청 수십 토큰)

## 한계 / 다음에 할 것

- 실제 API 실행 → TODO 07-1 ~ 07-4
- 규칙·메시지는 서비스 공개 전 약사 검토 필요 (domain-rules.md 주석)
