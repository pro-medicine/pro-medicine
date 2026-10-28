# STEP 07. V3 · V4 - 규칙 검증 (사후 → 사전)

> 먼저 `docs/prompts/00_COMMON.md`, `docs/prompts/reference/measurement.md`, `docs/prompts/reference/domain-rules.md`를 읽고 따른다. STEP 06이 끝난 상태에서 시작한다.

## 목표

안전 판단을 LLM에 맡기지 않고 규칙 테이블로 처리한다. 두 버전을 순서대로 만들고 각각 측정한다.

| 버전 | 바뀌는 점 |
| --- | --- |
| V3 | LLM 출력을 받은 **뒤** 규칙 테이블로 제외·경고 (사후 검증) |
| V4 | V3 + LLM 호출 **전에** 금지 성분 목록을 프롬프트에 넣음 (사전 필터) |

V3에서 `raw_violation_rate`(LLM이 원래 위반한 비율)를 처음 측정하고, V4에서 그게 얼마나 줄어드는지 본다.

## 1. 스키마 추가

**recommendation_rules** (새 테이블)

| 컬럼 | 타입 |
| --- | --- |
| rule_id | VARCHAR(10) PK (R-01 …) |
| condition_type | VARCHAR(15) NOT NULL (HABIT, DRUG, STATUS, INGREDIENT, DISEASE) |
| condition_code | VARCHAR(40) NOT NULL |
| target_ingredient | VARCHAR(30) NOT NULL |
| action | VARCHAR(10) NOT NULL (EXCLUDE, WARN, TIMING, ADJUST, ASK) |
| message_ko | VARCHAR(300) NOT NULL |
| guest_excluded | BOOLEAN NOT NULL DEFAULT FALSE |
| source_id | VARCHAR(20) NOT NULL |
| active | BOOLEAN NOT NULL DEFAULT TRUE |

**ai_recommendation_items** (컬럼 추가)

| 컬럼 | 타입 |
| --- | --- |
| excluded | BOOLEAN NOT NULL DEFAULT FALSE |
| applied_rule_ids | VARCHAR(200) NULL (쉼표 구분) |
| warnings | TEXT NULL (JSON 배열) |

- 제외된 성분도 `excluded = true`로 저장한다. 그래야 "LLM이 원래 무엇을 추천했는지"가 남아 `raw_violation_rate`를 다시 계산할 수 있다.
- 시드는 `domain-rules.md` 3번 표 그대로 (R-G1~R-G4 포함, 총 27행).

## 2. 조건 코드 매핑

STEP 06에서 만든 `ConditionCodeResolver`를 아래 표 전체로 확장한다. 매핑은 이 클래스 한 곳에서만 관리한다.

| 입력 | 조건 코드 |
| --- | --- |
| smoking ≠ NONE | SMOKING_CURRENT |
| drinking = HIGH | DRINKING_HIGH |
| caffeine = HIGH | CAFFEINE_HIGH |
| diet_type = VEGAN | DIET_VEGAN |
| sun_exposure = LOW | SUN_LOW |
| Q-PREGNANCY = POSSIBLE | PREGNANCY_POSSIBLE |
| Q-PREGNANCY = PREGNANT, LACTATING | PREGNANT_OR_LACTATING |
| 복용 약의 drug_class | 그 값 그대로 (ACE_INHIBITOR 등) |
| 성별 M, 19세 이상, 현재 섭취 성분에 IRON | ADULT_MALE_TAKING_IRON |
| 현재 섭취 또는 추천 성분 | INGREDIENT 규칙의 condition_code |
| 비회원 | GUEST |

## 3. V3 구현 (사후 검증)

- `V3Pipeline` = V2 흐름 + 마지막에 `RuleEngine.applyPost(result, conditions)`
- 조치별 처리: EXCLUDE → `excluded = true`, WARN/TIMING/ASK → `warnings`에 메시지 추가, ADJUST → 기준표 조건 행 선택에 반영(이미 V2에서 SMOKING_CURRENT 조건 행으로 처리됨, 여기서는 규칙 ID만 기록)
- 비회원이면 `guest_excluded = true` 규칙도 적용
- 규칙에 걸린 횟수를 Micrometer Counter `recommendation.rule.hit` (태그 rule_id, phase=post)로 남긴다
- `rule_post` 단계 Timer 추가
- **RuleEngine은 LLM과 무관한 순수 클래스**로 만들고, 규칙마다 단위 테스트를 둔다

## 4. V4 구현 (사전 필터)

- `V4Pipeline` = V3 + LLM 호출 전 `RuleEngine.preFilter(conditions)` → EXCLUDE 대상 성분 목록
- 프롬프트에 "다음 성분은 추천하지 마라: ..." 와 이유를 넣는다. `prompt_version = "v4.1"`
- 사후 검증은 그대로 유지한다 (최종 안전 책임은 사후 검증)
- `rule_pre` 단계 Timer 추가

## 5. 측정

1. `./gradlew evalRun -PpipelineVersion=V3 -Pconfirm=true`
2. `./gradlew evalRun -PpipelineVersion=V4 -Pconfirm=true`
3. 각각 재현성도 측정
4. `docs/perf/STEP07-report.md`

| 지표 | V2 | V3 | V4 | 비고 |
| --- | --- | --- | --- | --- |
| violation_rate (최종) | | | | V3부터 0이어야 정상. 0이 아니면 규칙·매핑 버그 |
| raw_violation_rate (LLM 원래 위반) | - | | | V4에서 줄어드는 게 핵심 |
| 규칙별 걸린 횟수 상위 5 | - | | | |
| recall | | | | 제외 때문에 떨어졌는지 |
| avg_input_tokens | | | | 금지 목록 추가분 |
| e2e_p95_ms | | | | |

- V2까지는 규칙이 없으므로 raw_violation_rate = violation_rate다 (measurement.md 정의).

## 6. 완료 조건

- `./gradlew test` 통과, 규칙 27개(회원 23 + 비회원 4) 각각 단위 테스트
- 스키마 변경 기록, `results.csv` V3·V4 줄, 리포트, progress 기록
