# STEP06 리포트 - V2 용량 계산 분리 (기준표)

## 바꾼 것 (Before → After)

| | V1 | V2 |
| --- | --- | --- |
| 용량 | LLM이 출력 (tool 스키마에 amount·unit) | 기준표 권장량 − 현재 섭취량 (`DoseCalculator`, 코드 계산) |
| 현재 섭취량 반영 | LLM 재량 | 설문 `Q-SUPPLEMENTS` 값으로 계산 (단위가 다르면 변환하지 않고 UNKNOWN) |
| 상한 체크 | 없음 | 현재 + 추천 > 상한이면 상한까지로 줄이고 `dose_note`에 기록 |
| 프롬프트 | v1.1 | v2.1 ("용량은 쓰지 마라", 스키마에서 amount·unit 제거) |
| 스키마 | - | `ingredient_standards` 새 테이블, `ai_recommendation_items`에 용량 컬럼 5개 ([schema-changes.md](../schema-changes.md)) |

기준표 시드는 `domain-rules.md` 2번 표에서 출처가 있는 6행만 넣었다 (비타민 C 4행, 엽산 1행, 남성 철분 1행). 나머지 성분은 `TODO(KDRI-2025 원문)`이라 **용량 없이 추천되고 `dose_note = "기준값 미등록"`**이다.

## 측정 조건

| 항목 | 값 |
| --- | --- |
| 실제 API (V2 전체, 재현성) | **실제 측정 대기** → [TODO-live-runs.md](TODO-live-runs.md) 06-1, 06-2 |
| 이번에 실행한 것 | Fake 실행 4번 (fake, fake-noisy × 전체·재현성) |
| over_limit_rate | 이번 STEP부터 계산 (상한을 아는 항목만: 현재 기준표에서는 비타민 C만 상한 2000mg이 있다) |
| 날짜 | 2026-10-21 |

## 결과

### V1 대비 표 (실제 API) — 실제 측정 대기

| 지표 | V1 | V2 | 비고 |
| --- | --- | --- | --- |
| dose_error_rate | 측정 안 함 | 측정 안 함 | 기대 용량이 있는 항목 기준 |
| over_limit_rate | 측정 안 함 | 측정 안 함 | |
| reproducibility_set / dose | 측정 안 함 | 측정 안 함 | 코드 계산으로 dose 쪽이 오르는지 |
| avg_output_tokens | 측정 안 함 | 측정 안 함 | 용량을 안 쓰니 줄어드는지 |
| e2e_p95_ms | 측정 안 함 | 측정 안 함 | calc 단계 추가분 |

### 메커니즘 확인 (Fake, 실제 측정값 아님) — results.csv `05,V1-*`, `06,V2-*`

| 지표 | V1-fake-noisy | V2-fake-noisy | V1-repro-fake-noisy | V2-repro-fake-noisy |
| --- | --- | --- | --- | --- |
| dose_error_rate (n) | 0.125 (8) | 0.0 (8) | 0.3571 (28) | 0.0 (28) |
| over_limit_rate (n) | - | 0.0 (6) | - | 0.0 (15) |
| reproducibility_set / dose | - | - | 0.6 / 0.54 | 0.6 / 0.6 |

- noisy Fake는 LLM 용량을 ±30% 흔든다. V2는 LLM 용량을 쓰지 않으므로 dose_error가 0이 되고, 성분 집합이 같으면 용량도 항상 같다 (repro set = dose)
- 기대 용량 자체가 기준표에서 나왔으므로 V2의 dose_error 0은 "계산이 기준표와 일치한다"는 뜻이다. 실제 LLM 품질 수치가 아니다

### calc 단계 비용 (로컬 JVM 실측, `runs/V2-*.json`의 `stageTimings`)

| 실행 | calc 평균 | calc p95 | calc 최대 | 같은 실행의 llm(Fake) p50 |
| --- | ---: | ---: | ---: | ---: |
| V2-fake (32회) | 0.62 ms | 0.85 ms | 12.57 ms | 3.93 ms |
| V2-repro-fake (50회) | 0.45 ms | 0.85 ms | 9.02 ms | 4.59 ms |
| V2-fake-noisy (32회) | 0.42 ms | 0.98 ms | 7.27 ms | 3.15 ms |
| V2-repro-fake-noisy (50회) | 0.30 ms | 0.56 ms | 5.62 ms | 3.21 ms |

calc는 1ms 안팎이다. 실제 LLM 호출은 수 초 단위(STEP 08·09에서 WireMock 3초 가정)라 계산 비용은 무시할 수준이다. 최대값은 첫 호출(JIT 전)이다.

## 해석

- 용량을 코드로 옮기면 용량 재현성과 정확도는 기준표 품질로 결정된다. 지금은 기준값이 6행뿐이라 대부분 성분이 용량 없이 나간다. **`domain-rules.md`의 TODO 수치를 채우는 것이 V2 효과를 키우는 가장 큰 일**이다.
- 기준표 조회는 앱 시작 시 메모리에 올리는 캐시(`StandardsCache`)라 요청마다 DB를 읽지 않는다. 변경은 재시작 또는 `reload()`로 반영한다.

## 한계 / 다음에 할 것

- 실제 API 실행 → TODO 06-1, 06-2
- 엽산 상한은 합성 엽산 기준이라 DFE 권장량과 섞지 않으려고 비워 두었다 (domain-rules.md 주석 그대로)
