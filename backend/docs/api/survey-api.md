# 설문 API 명세 (STEP 02, 화면 담당: 채원)

백엔드만 구현했다. 프론트엔드 연동 시 이 문서를 기준으로 맞춘다. 문구·필드명 변경이 필요하면 영준과 협의한다.

- 모든 응답은 공통 `ApiResponse`로 감싼다: `{ "success": true, "code": "S000", "message": "성공", "data": ... }`
- 실패 시: `{ "success": false, "code": "S002", "message": "설문 답변이 올바르지 않습니다." }`
- **임시 인증**: 로그인이 아직 없어 `X-User-Id: {userId}` 헤더로 회원을 구분한다. 헤더가 없으면 비회원. 로그인 연동 시 헤더 대신 토큰/세션으로 바뀐다.

## 흐름

```text
GET  /api/v1/surveys/active                  설문 시작 (문항 + 회원이면 미리 채운 값)
  ↓ 화면에서 한 문항씩 진행 (서버 호출 없음)
POST /api/v1/users/me/sensitive-consent      민감정보 동의 (회원만, 민감 문항 전에 한 번)
  ↓ 동의 후에는 GET /surveys/active 를 다시 호출하면 민감 문항이 포함된다
POST /api/v1/surveys/{surveyId}/responses    제출 → responseId
```

## 1. 설문 시작 `GET /api/v1/surveys/active`

| 상황 | 내려오는 문항 |
| --- | --- |
| 비회원 | `guest_visible = true`이고 민감하지 않은 문항 11개 |
| 회원, 동의 안 함 | 민감하지 않은 문항 11개 + 미리 채운 값 |
| 회원, 동의함 | 14개 전체 (임신·질환·복용 약 포함) + 미리 채운 값 |

응답 예시 (회원, 동의함):

```json
{
  "success": true, "code": "S000", "message": "성공",
  "data": {
    "surveyId": 1,
    "version": "v1",
    "respondentType": "MEMBER",
    "needsConfirm": false,
    "questions": [
      {
        "questionId": 3, "code": "Q-SMOKING", "text": "흡연하시나요?", "answerType": "SINGLE", "sensitive": false,
        "options": [
          { "optionId": 3, "valueCode": "NONE", "label": "안 피움" },
          { "optionId": 4, "valueCode": "HALF_PACK", "label": "하루 반 갑 이하" }
        ],
        "prefill": { "optionIds": [4] }
      },
      { "questionId": 2, "code": "Q-BIRTH", "text": "출생 연도를 알려주세요", "answerType": "NUMBER", "sensitive": false,
        "options": [], "prefill": { "valueNumber": 1990 } },
      { "questionId": 13, "code": "Q-MEDICATIONS", "text": "복용 중인 약을 알려주세요", "answerType": "TEXT", "sensitive": true,
        "options": [], "prefill": { "valueText": "[{\"drugId\":\"D-003\"},{\"name\":\"마스터에 없는 약\"}]" } }
    ]
  }
}
```

- `prefill`은 회원이고 저장된 값이 있을 때만 있다. 문항을 빼지 않으니 화면에서 "미리 채운 값 + 수정 가능"으로 보여주고, 건너뛸지는 화면에서 정한다.
- `needsConfirm = true`: 프로필 갱신일이 6개월을 넘음 → 미리 채운 값을 확인하도록 안내.
- `answerType`: `SINGLE`(하나 선택), `MULTI`(여러 개), `NUMBER`(숫자), `TEXT`(JSON 문자열, 아래 참고).

## 2. 민감정보 동의 `POST /api/v1/users/me/sensitive-consent`

- 헤더 `X-User-Id` 필수 (없으면 401 `E003`). 여러 번 호출해도 처음 동의 시각이 유지된다.

```json
{ "success": true, "code": "S000", "message": "성공",
  "data": { "sensitiveConsent": true, "sensitiveConsentAt": "2026-10-21T18:00:00" } }
```

## 3. 제출 `POST /api/v1/surveys/{surveyId}/responses`

요청:

```json
{
  "answers": [
    { "questionId": 1, "optionIds": [1] },
    { "questionId": 2, "valueNumber": 1990 },
    { "questionId": 10, "optionIds": [24, 26] },
    { "questionId": 12, "optionIds": [34] },
    { "questionId": 13, "valueText": "[{\"drugId\":\"D-003\"},{\"name\":\"마스터에 없는 약\"}]" },
    { "questionId": 14, "valueText": "[{\"ingredient\":\"MAGNESIUM\",\"amount\":30,\"unit\":\"mg\"}]" }
  ]
}
```

응답: `{ "success": true, "code": "S000", "message": "성공", "data": { "responseId": 12 } }`

- 답하지 않은 문항은 보내지 않는다. 회원이면 `profile_key`가 있는 답이 프로필에 반영된다 (성별·출생 연도·흡연·음주·커피·식단·햇빛·질환·복용 약).
- 질환에서 "없음(NONE)"을 고르면 저장된 질환이 지워진다. 복용 약을 `[]`로 보내면 저장된 약이 지워진다.

### TEXT 문항 JSON 형식

| 문항 | 형식 | 비고 |
| --- | --- | --- |
| `Q-MEDICATIONS` | `[{"drugId":"D-001"},{"name":"약 이름"}]` | 검색(`GET /api/v1/drugs?keyword=`)에서 고르면 `drugId`, 못 찾으면 `name`. 한 항목에 둘 중 하나만 |
| `Q-SUPPLEMENTS` | `[{"ingredient":"MAGNESIUM","amount":30,"unit":"mg"}]` | `ingredient`는 `GET /api/v1/ingredients`의 `code`, `amount`는 0 이상 숫자, 단위는 성분 목록의 `unit` 권장 |

### 오류

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | S002 | 이 설문의 문항이 아님, 선택지가 그 문항 것이 아님, SINGLE에 2개 이상, 같은 문항 중복, 질환 NONE과 다른 값 같이 선택, JSON 형식 오류·모르는 성분 코드·없는 drugId, 출생 연도 범위 밖 |
| 400 | S003 | 비회원이 민감 문항(임신·질환·복용 약) 답을 보냄 |
| 403 | S004 | 회원인데 민감정보 동의 없이 민감 문항 답을 보냄 |
| 404 | S001 | 진행 중(ACTIVE)인 설문이 아님 |
| 404 | U001 | `X-User-Id`의 회원이 없음 |

요청 본문이 JSON이 아니면 공통 예외 처리기가 500(`E001`)을 돌려준다 (공통 `GlobalExceptionHandler` 범위라 따로 협의 필요).

## 4. 기준 데이터

- `GET /api/v1/drugs?keyword=와파린` → `[{ "drugId": "D-007", "name": "와파린", "drugClass": "WARFARIN" }]` (keyword 없으면 이름순 20개)
- `GET /api/v1/ingredients` → `[{ "code": "VITAMIN_C", "name": "비타민 C", "unit": "mg" }, ...]` (14개)

## 5. 설문 v1 문항 (시드: `backend/docs/sql/STEP02_seed_survey_v1.sql`, 예시 데이터)

| questionId | code | answerType | 민감 | 비회원 | 선택지 value_code |
| --- | --- | --- | --- | --- | --- |
| 1 | Q-GENDER | SINGLE | N | Y | M, F |
| 2 | Q-BIRTH | NUMBER | N | Y | - |
| 3 | Q-SMOKING | SINGLE | N | Y | NONE, HALF_PACK, ONE_PACK, TWO_PACK_PLUS |
| 4 | Q-DRINKING | SINGLE | N | Y | NONE, LOW, HIGH |
| 5 | Q-CAFFEINE | SINGLE | N | Y | NONE, LOW, HIGH |
| 6 | Q-DIET | SINGLE | N | Y | OMNI, VEGETARIAN, VEGAN |
| 7 | Q-SUN | SINGLE | N | Y | LOW, NORMAL |
| 8 | Q-SLEEP | SINGLE | N | Y | GOOD, NORMAL, POOR |
| 9 | Q-FATIGUE | SINGLE | N | Y | LOW, NORMAL, HIGH |
| 10 | Q-GOAL | MULTI | N | Y | SLEEP, FATIGUE, IMMUNITY, BONE, EYE |
| 11 | Q-PREGNANCY | SINGLE | Y | N | NOT_APPLICABLE, POSSIBLE, PREGNANT, LACTATING |
| 12 | Q-CONDITIONS | MULTI | Y | N | NONE, HYPERTENSION, DIABETES, THYROID, KIDNEY |
| 13 | Q-MEDICATIONS | TEXT | Y | N | - |
| 14 | Q-SUPPLEMENTS | TEXT | N | Y | - |

ID는 시드 기준이라 환경마다 다를 수 있다. 화면은 `code`/`valueCode`로 분기하고 `questionId`/`optionId`는 응답에서 받은 값을 그대로 쓴다.
