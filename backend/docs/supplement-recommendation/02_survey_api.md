# STEP 02. 설문 API (시작 · 동의 · 제출)

> 먼저 `docs/prompts/00_COMMON.md`를 읽고 따른다. STEP 01(엔티티)이 끝난 상태에서 시작한다.

## 목표

로그인 여부와 상관없이 설문을 할 수 있고, 제출하면 응답이 회차 단위로 저장되고 회원 프로필에 반영되는 상태까지 만든다.
LLM 호출은 이번 STEP에서 하지 않는다.

## 1. 설문 흐름 (확정된 설계)

```text
[설문 시작] GET /api/surveys/active
  회원: 프로필(health_profiles, user_conditions, user_medications) 1회 조회
        → 이미 아는 문항은 미리 채운 값과 함께 내려줌
        → 민감 문항(is_sensitive)은 users.sensitive_consent = true일 때만 포함
  비회원: guest_visible = true 이고 is_sensitive = false 인 문항만
        ↓
[문항 진행] 클라이언트가 한 문항씩 보여줌. 서버 호출 없음
        ↓
[민감정보 동의] POST /api/users/me/sensitive-consent  (회원만, 처음 한 번)
        ↓
[제출] POST /api/surveys/{surveyId}/responses
  한 트랜잭션에서:
  1) survey_responses 1행 (status = SUBMITTED, submitted_at = now)
  2) survey_answers N행
  3) 회원이면 profile_key가 있는 답을 프로필에 반영
  응답: responseId
```

- 분기 문항(노출 조건)은 없다. 한 문항의 선택지로 해결한다. (예: 흡연 → 안 피움 / 반 갑 이하 / 1갑 / 2갑 이상)
- `status = IN_PROGRESS`는 이번 STEP에서 쓰지 않는다. 제출 시점에만 행을 만든다.

## 2. 미리 채우기 규칙

- `profile_key`가 `health_profiles`의 컬럼명(`gender`, `birth_year`, `smoking`, `drinking`, `caffeine`, `diet_type`, `sun_exposure`, `height`, `weight`)과 같으면 그 값으로 미리 채운다.
- `profile_key = "conditions"` → `user_conditions` 목록, `profile_key = "medications"` → `user_medications` 목록으로 채운다.
- `health_profiles.updated_at`이 6개월을 넘었으면 응답에 `needsConfirm = true`를 붙인다. (필드별 갱신일이 없으므로 프로필 전체 기준)
- 문항을 빼지 않고 "미리 채운 값 + 수정 가능"으로 내려준다. 건너뛸지는 클라이언트가 정한다.

## 3. 제출 시 프로필 반영 규칙

- 선택형 답: 고른 `question_options.value_code`를 `profile_key` 컬럼에 그대로 저장한다.
- 숫자형 답: `value_number`를 저장한다 (`height`, `weight`, `birth_year`).
- `conditions`: 제출한 목록으로 `user_conditions`를 교체한다 (`condition_name`에는 `value_code` 저장). `NONE`을 고르면 기존 행을 지우고 새 행은 만들지 않는다. `NONE`과 다른 값을 같이 고르면 400.
- `medications`: `value_text` JSON으로 `user_medications`를 교체한다. `drugId`면 `drug_id`, `name`이면 `unmatched_name`. 빈 배열이면 기존 행만 지운다.
- `profile_key`가 없는 답(수면, 피로 등)은 `survey_answers`에만 남긴다.
- `health_profiles` 행이 없으면 만든다 (`@MapsId`).
- 민감 문항 답이 하나라도 있으면 `includes_sensitive = true`.

## 4. 검증

- 비회원이 민감 문항 답을 보내면 400.
- 회원인데 동의 없이 민감 문항 답을 보내면 403.
- `question_id`가 해당 `survey_id`의 문항이 아니면 400.
- 선택형 문항에 그 문항의 선택지가 아닌 `option_id`를 보내면 400.
- `SINGLE` 문항에 답이 2개 이상이면 400.
- `TEXT` 문항 중 JSON 형식이 정해진 것(`Q-MEDICATIONS`, `Q-SUPPLEMENTS`)은 형식이 틀리거나 모르는 성분 코드면 400.
- 같은 문항 답이 중복되면 400.

## 5. 인증

- 프로젝트에 이미 인증이 있으면 그대로 쓴다. 현재 사용자 ID를 얻는 방법만 확인한다.
- 없으면 이번 STEP에서는 `X-User-Id` 헤더로 회원을 식별하는 임시 `CurrentUserResolver`를 만든다. 헤더가 없으면 비회원. 클래스에 `// TEMP: 인증 구현 시 교체` 주석을 단다.

## 6. 설문 시드 데이터

`src/main/resources/data/survey-v1.sql`(또는 프로젝트 방식에 맞는 시드)에 설문 v1을 넣는다. `status = ACTIVE`.

| code | 문항 | answer_type | profile_key | is_sensitive | guest_visible | 선택지 value_code |
| --- | --- | --- | --- | --- | --- | --- |
| Q-GENDER | 성별 | SINGLE | gender | N | Y | M, F |
| Q-BIRTH | 출생 연도 | NUMBER | birth_year | N | Y | - |
| Q-SMOKING | 흡연하시나요? | SINGLE | smoking | N | Y | NONE, HALF_PACK, ONE_PACK, TWO_PACK_PLUS |
| Q-DRINKING | 음주 빈도 | SINGLE | drinking | N | Y | NONE, LOW, HIGH |
| Q-CAFFEINE | 커피·차 섭취 | SINGLE | caffeine | N | Y | NONE, LOW, HIGH |
| Q-DIET | 식단 | SINGLE | diet_type | N | Y | OMNI, VEGETARIAN, VEGAN |
| Q-SUN | 햇빛 노출 | SINGLE | sun_exposure | N | Y | LOW, NORMAL |
| Q-SLEEP | 최근 수면 상태 | SINGLE | (없음) | N | Y | GOOD, NORMAL, POOR |
| Q-FATIGUE | 최근 피로감 | SINGLE | (없음) | N | Y | LOW, NORMAL, HIGH |
| Q-GOAL | 원하는 효과 (복수) | MULTI | (없음) | N | Y | SLEEP, FATIGUE, IMMUNITY, BONE, EYE |
| Q-PREGNANCY | 임신·수유 | SINGLE | (없음) | Y | N | NOT_APPLICABLE, POSSIBLE, PREGNANT, LACTATING |
| Q-CONDITIONS | 진단받은 질환 (복수) | MULTI | conditions | Y | N | NONE, HYPERTENSION, DIABETES, THYROID, KIDNEY |
| Q-MEDICATIONS | 복용 중인 약 | TEXT | medications | Y | N | 선택지 없음. 약 검색 → 아래 참고 |
| Q-SUPPLEMENTS | 복용 중인 영양제 성분과 함량 | TEXT | (없음) | N | Y | JSON 문자열 `[{"ingredient":"MAGNESIUM","amount":30,"unit":"mg"}]`. `ingredient`는 성분 코드 |

- `smoking`의 `NONE`이 아닌 값은 `SMOKING_CURRENT`로 본다. 이 매핑은 STEP 06의 `ConditionCodeResolver`에서 처음 쓴다.
- 복용 약: `drugs` 시드에 `domain-rules.md`의 약물 계열(`METFORMIN`, `PPI`, `ACE_INHIBITOR`, `ARB`, `K_SPARING_DIURETIC`, `THIAZIDE_LOOP_DIURETIC`, `WARFARIN`, `LEVOTHYROXINE`, `TETRA_QUINOLONE`, `SSRI`)별 대표 약 1개씩 넣는다. 답은 `value_text`에 `[{"drugId":"D-001"},{"name":"마스터에 없는 약 이름"}]` 형식의 JSON으로 저장한다 (검색에서 고르면 drugId, 못 찾으면 name). `GET /api/drugs?keyword=`로 검색 API를 만든다.
- 성분 코드 목록: `domain-rules.md` 1번 표를 `IngredientCode` enum(코드, 한글 이름, 기준 단위)으로 만들고 `GET /api/ingredients`로 내려준다. `Q-SUPPLEMENTS` 입력은 이 코드와 단위로 받는다.
- 문항 문구는 바꿔도 되지만 `code`와 `value_code`는 이 표를 그대로 쓴다. 이후 STEP이 이 코드를 기준으로 동작한다.

## 7. 테스트 (필수)

- 비회원 설문 시작: 민감 문항이 없어야 한다.
- 동의한 회원 설문 시작: 민감 문항이 있고, 저장된 흡연 값이 미리 채워져 있어야 한다.
- 6개월 지난 프로필: `needsConfirm = true`.
- 회원 제출: response 1행, answer N행, health_profiles 반영, user_conditions 교체, `includes_sensitive = true`.
- 비회원 제출: `user_id = null`, `respondent_type = GUEST`, 프로필 테이블 변화 없음.
- 4번 검증 항목 각각 실패 케이스.

## 8. 측정 (이번 STEP)

LLM이 없어서 품질 지표는 없다. 대신 이후 비교의 기준점으로 아래만 기록한다.

- 설문 시작 API, 제출 API의 p50/p95 (문항 14개, 로컬, 각 200회 호출, 워밍업 20회 제외). JUnit이나 간단한 스크립트로 측정한다.
- 설문 시작 시 실행되는 SQL 수 (Hibernate statistics 또는 p6spy). N+1이 있으면 고치고 Before/After를 남긴다.
- `docs/perf/STEP02-report.md`에 기록한다. `results.csv`는 STEP 04에서 만들므로 이번에는 리포트만 쓴다.

## 9. 완료 조건

- `./gradlew test` 통과
- 위 테스트 전부 존재
- `docs/perf/STEP02-report.md`, `docs/progress.md` 기록
