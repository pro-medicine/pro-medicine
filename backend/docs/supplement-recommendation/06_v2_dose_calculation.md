# STEP 06. V2 - 용량 계산 분리 (기준표)

> 먼저 `docs/prompts/00_COMMON.md`, `docs/prompts/reference/measurement.md`, `docs/prompts/reference/domain-rules.md`를 읽고 따른다. STEP 05가 끝난 상태에서 시작한다.

## 목표

용량을 LLM이 정하지 않게 한다. LLM은 "어떤 성분이 왜 필요한지"만 판단하고, 용량은 기준표와 코드로 계산한다.

| | Before (V1) | After (V2) |
| --- | --- | --- |
| 용량 | LLM이 출력 | 기준표 권장량 − 현재 섭취량 (코드 계산) |
| 현재 섭취량 반영 | LLM 재량 | 설문 `Q-SUPPLEMENTS` 값으로 계산 |
| 상한 체크 | 없음 | 현재 + 추천 > 상한이면 추천량을 상한까지로 줄이고 경고 |

## 1. 스키마 추가

**ingredient_standards** (새 테이블)

| 컬럼 | 타입 |
| --- | --- |
| standard_id | BIGINT PK AUTO_INCREMENT |
| ingredient_code | VARCHAR(30) NOT NULL |
| gender | VARCHAR(5) NOT NULL (M, F, ALL) |
| age_min | INT NOT NULL |
| age_max | INT NULL (상한 없으면 null) |
| condition_code | VARCHAR(30) NULL (SMOKING_CURRENT 등, STEP 07 규칙과 같은 코드) |
| recommended_amount | DECIMAL(10,2) NULL |
| upper_limit | DECIMAL(10,2) NULL |
| unit | VARCHAR(10) NOT NULL |
| standard_version | VARCHAR(20) NOT NULL (KDRI-2025, NIH-ODS) |
| source_id | VARCHAR(10) NOT NULL (domain-rules.md의 S-xx) |

**ai_recommendation_items** (컬럼 추가)

| 컬럼 | 타입 |
| --- | --- |
| recommended_amount | DECIMAL(10,2) NULL |
| current_intake | DECIMAL(10,2) NULL |
| intake_status | VARCHAR(10) NULL (KNOWN, UNKNOWN) |
| unit | VARCHAR(10) NULL |
| dose_note | VARCHAR(200) NULL (상한 조정, 기준 없음 등) |

- 시드는 `domain-rules.md` 2번 표의 값만 넣는다. `TODO`인 값은 행을 만들지 않는다.
- 기준이 없는 성분은 용량 없이 추천하고 `dose_note = "기준값 미등록"`.

## 2. 구현

- `V2Pipeline` 추가.
- tool 스키마에서 `amount`, `unit`을 뺀다. 프롬프트에도 "용량은 쓰지 마라"를 명시한다. `prompt_version = "v2.1"`.
- `DoseCalculator` (순수 계산 클래스, LLM·DB 의존 없음)
  - 기준 조회: 성분 + 성별 + 나이 + 조건(흡연이면 SMOKING_CURRENT 행 우선) → 가장 구체적인 행 1개
  - 조건 코드는 `ConditionCodeResolver`(이번 STEP에서 생성, 입력 → 조건 코드 변환)에서 받는다. 이번에는 `smoking ≠ NONE → SMOKING_CURRENT`만 구현하고 STEP 07에서 확장한다
  - 현재 섭취량의 단위가 기준표 단위와 다르면 단위 변환하지 않고 `intake_status = UNKNOWN`으로 처리한다
  - 현재 섭취량 있음(KNOWN): `max(권장량 − 현재, 0)`. 0이면 "추가 필요 없음"으로 표시하고 목록에는 남긴다
  - 현재 섭취량 없음(UNKNOWN): 권장량 전체, `dose_note = "현재 섭취량 미반영"`
  - 상한(값이 있을 때만): `현재 + 추천 > 상한`이면 추천 = `max(상한 − 현재, 0)`, `dose_note`에 조정 사실
- 기준표 조회는 앱 시작 시 메모리에 올려두는 캐시로 처리한다 (데이터가 작고 거의 안 바뀜). 변경 반영 방법을 주석으로 남긴다.

## 3. 테스트

- `DoseCalculator` 단위 테스트: KNOWN, UNKNOWN, 0 이하, 상한 초과, 흡연자 조건 행 선택, 기준 없음, 단위 불일치
- 테스트용 기준값은 `src/test/resources`에 `TEST ONLY` 표시로 따로 둔다

## 4. 측정

1. `./gradlew evalRun -PpipelineVersion=V2 -Pconfirm=true`
2. `./gradlew evalRun -PpipelineVersion=V2 -Pmode=repro -Pconfirm=true`
3. `docs/perf/STEP06-report.md`

| 지표 | V1 | V2 | 비고 |
| --- | --- | --- | --- |
| dose_error_rate | | | 기대 용량이 있는 항목 기준 |
| over_limit_rate | | | |
| reproducibility_set / reproducibility_dose | | | 코드 계산으로 dose 쪽이 크게 오르는지 |
| avg_output_tokens | | | 용량을 안 쓰니 줄어드는지 |
| e2e_p95_ms | | | calc 단계 추가분 |

- `calc` 단계 Timer를 추가하고 단계별 지연에서 계산 비용이 무시할 수준인지 확인한다.

## 5. 완료 조건

- `./gradlew test` 통과
- 스키마 변경을 Flyway 마이그레이션으로 추가하고 `docs/schema-changes.md`에 기록
- `results.csv` V2 줄 2개, 리포트, progress 기록
