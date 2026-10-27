# 추천 API 명세 (STEP 03~10, 화면 담당: 채원)

백엔드만 구현했다. 설문 API는 [survey-api.md](survey-api.md). 응답은 공통 `ApiResponse`로 감싼다.
임시 인증은 설문과 같다: 회원은 `X-User-Id` 헤더 (로그인 연동 시 교체).

## 흐름

```text
설문 제출 POST /api/v1/surveys/{surveyId}/responses → responseId
  ↓
POST /api/v1/recommendations { responseId }
  - sync 모드(기본): LLM 응답까지 기다렸다가 200 + 결과 (수 초 걸림)
  - queue 모드: 즉시 202 + { recommendationId, status: PENDING } → GET으로 status가 DONE/FAILED가 될 때까지 조회 (권장 간격 1초)
  ↓
GET /api/v1/recommendations/{recommendationId}

알림을 열고 들어온 회원: GET /api/v1/users/me/recommendations/latest
  - reusable = true  → 그대로 보여주면 된다 (LLM 호출 없음)
  - needsNewSurvey = true → 설문 화면으로 안내
```

서버 모드는 설정(`recommendation.api-mode`)으로 정해진다. 화면은 **응답 코드 200이면 결과를 바로, 202면 조회 반복**으로 처리하면 두 모드 모두 대응된다.

## 1. 추천 생성 `POST /api/v1/recommendations`

요청: `{ "responseId": 12 }` — 같은 responseId로 다시 요청하면 (queue 모드) 진행 중이거나 끝난 기존 작업을 돌려준다.

응답 (200, DONE):

```json
{
  "success": true, "code": "S000", "message": "성공",
  "data": {
    "recommendationId": 34,
    "responseId": 12,
    "status": "DONE",
    "modelName": "claude-opus-5-5",
    "summary": "흡연과 실내 생활을 고려한 추천이에요.",
    "createdAt": "2026-10-21T21:00:00",
    "items": [
      {
        "ingredient": "VITAMIN_C", "reason": "흡연자는 비타민 C 필요량이 늘어요", "sortOrder": 1,
        "amount": 100, "unit": "mg", "currentIntake": 25, "intakeStatus": "KNOWN",
        "warnings": null, "appliedRuleIds": ["R-01"]
      },
      {
        "ingredient": "VITAMIN_D", "reason": "햇빛 노출이 적어요", "sortOrder": 2,
        "doseNote": "기준값 미등록",
        "warnings": ["햇빛 노출이 적으면 비타민 D 부족 위험이 있어요"], "appliedRuleIds": ["R-06"]
      }
    ],
    "excludedItems": [
      {
        "ingredient": "BETA_CAROTENE", "reason": "…", "sortOrder": 3,
        "warnings": ["흡연자는 고용량 베타카로틴 보충제를 피하는 게 좋아요"], "appliedRuleIds": ["R-02"]
      }
    ]
  }
}
```

| 필드 | 설명 |
| --- | --- |
| `items` | 최종 추천. `ingredient`는 성분 코드 (`GET /api/v1/ingredients`의 code → 이름·단위) |
| `amount` / `unit` | 하루 **추가** 섭취량 (기준표 − 현재 섭취량). 기준값이 없으면 없음 + `doseNote: "기준값 미등록"` |
| `intakeStatus` | KNOWN(현재 섭취량 반영) / UNKNOWN(모름 또는 단위가 달라 미반영) |
| `doseNote` | "추가 섭취 필요 없음", "상한섭취량에 맞춰 조정", "현재 섭취량 미반영" 등 |
| `warnings` | 규칙 경고·복용 시간 안내 (화면에 그대로 표시) |
| `excludedItems` | 안전 규칙으로 제외된 성분과 이유. 사용자에게 보여줄지는 화면에서 결정 |
| `status` | PENDING / PROCESSING / DONE / FAILED (queue 모드) |
| `errorCode` | FAILED일 때 (예: R003 LLM 실패) |
| `fallback`·`notice` | STEP 10: LLM 장애 시 규칙 기반 기본 안내 (`modelName = "RULE_ONLY"`). 아래 4번 참고 |

응답 (202, queue 모드): `data = { "recommendationId": 34, "responseId": 12, "status": "PENDING", "items": [], "excludedItems": [] }`

오류: 400 `R002`(responseId 없음), 404 `S005`(응답 없음), 502 `R003`(sync 모드 LLM 실패), 404 `R001`(조회 시 없음)

## 2. 추천 조회 `GET /api/v1/recommendations/{recommendationId}`

POST와 같은 형식. queue 모드에서는 status가 DONE/FAILED가 될 때까지 반복 조회한다.

## 3. 알림 진입 `GET /api/v1/users/me/recommendations/latest` (회원, `X-User-Id` 필수)

```json
{ "success": true, "code": "S000", "message": "성공",
  "data": { "reusable": true, "needsNewSurvey": false, "recommendation": { "...": "1번 data와 같은 형식" } } }
```

| reusable | needsNewSurvey | reason | 화면 |
| --- | --- | --- | --- |
| true | false | - | 저장된 추천 표시 |
| false | true | NO_RECOMMENDATION | 설문 시작 |
| false | true | PROFILE_CHANGED | "건강 정보가 바뀌었어요" + 설문 시작 (이전 추천은 `recommendation`에 참고용으로 옴) |

## 4. 장애 시 기본 안내 (STEP 10)

LLM이 장애(서킷 OPEN, 재시도 소진)면 규칙 기반 기본 안내로 응답한다: `modelName = "RULE_ONLY"`, `fallback = true`,
`notice = "AI 맞춤 추천을 잠시 사용할 수 없어 기본 안내를 보여드려요"`. 화면은 notice를 그대로 보여주면 된다.
