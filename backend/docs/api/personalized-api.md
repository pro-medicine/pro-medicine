# V2 회원 맞춤 질문 API 명세 (STEP 14, 담당: 영준)

회원 전용. 저장된 건강정보 + LLM이 만드는 객관식 맞춤 질문 → 정밀 성분 추천. 설계: [docs/design/V2-personalized-recommendation.md](../../../docs/design/V2-personalized-recommendation.md).
응답은 공통 `ApiResponse`(`{ success, code, message, data }`)로 감싼다. 회원은 `X-User-Id` 헤더로 판단한다 (로그인 연동 시 교체). **모든 V2 API는 헤더가 없으면 401 (E003)**.
비회원 V1 설문·추천은 [survey-api.md](survey-api.md), [recommendation-api.md](recommendation-api.md).

## 흐름

```text
[홈 배너] 회원 → GET /api/v1/users/me/recommendation-mode
  ├ mode = V1 → 기존 회원 설문 (답이 프로필에 저장됨) → 끝나면 V2 안내
  └ mode = V2 → POST /api/v1/personalized-sessions (회차 시작)
                 ├ 19~50세 여성 → 임신·수유 고정 질문 (서버가 만든 질문, LLM 호출 없음)
                 └ 그 밖 → 첫 LLM 질문: 불편한 점 (MULTI, 최대 2개)
               POST /api/v1/personalized-sessions/{id}/answers  (반복, 기본 5 / 최대 7문항)
               └ 응답 status = READY (question = null) 또는 사용자가 POST .../stop (✕ 닫기는 POST .../close, 추천 없음)
               POST /api/v1/personalized-sessions/{id}/recommendation → V1과 같은 결과 형식
```

- 질문 생성은 LLM 호출이라 **답변 API 응답에 수 초 걸릴 수 있다** (화면은 대기 문구로 가린다).
- 중간에 나가면 다시 시작할 때 처음부터 (새로 시작하면 끝나지 않은 이전 회차는 `ABANDONED`).
- 맞춤 답변은 **프로필에 저장하지 않는다** (회차 기록에만, 90일 뒤 삭제).

## 1. 모드 판단 `GET /api/v1/users/me/recommendation-mode`

```json
{ "success": true, "code": "S000", "message": "성공", "data": { "mode": "V2", "missing": [] } }
{ "success": true, "code": "S000", "message": "성공", "data": { "mode": "V1", "missing": ["BIRTH_YEAR", "SENSITIVE_CONSENT", "MEDICATIONS_ANSWERED"] } }
```

| missing 값 | 빠진 것 |
|---|---|
| `GENDER` | 프로필 성별 |
| `BIRTH_YEAR` | 프로필 출생 연도 |
| `SENSITIVE_CONSENT` | 민감정보 수집 동의 |
| `CONDITIONS_ANSWERED` | 질환을 답한 적 없음 (`user_conditions` 행도 없고, 회원 설문의 Q-CONDITIONS 답도 없음. "없음" 답은 답한 것) |
| `MEDICATIONS_ANSWERED` | 복용 약을 답한 적 없음 (`user_medications` 행도 없고, 회원 설문의 Q-MEDICATIONS 답도 없음. 빈 목록 답은 답한 것) |

없는 회원 ID는 404 (U001).

## 2. 회차 시작 `POST /api/v1/personalized-sessions`

요청 본문 없음. 끝나지 않은 회차(IN_PROGRESS, READY)가 있으면 `ABANDONED`로 바꾸고 새로 시작한다.

응답 (200) — 19~50세 여성 (임신 고정 질문):

```json
{
  "success": true, "code": "S000", "message": "성공",
  "data": {
    "sessionId": 12,
    "status": "IN_PROGRESS",
    "question": {
      "turnId": 101, "turnNo": 0, "fixed": true, "topic": "PREGNANCY",
      "text": "현재 임신 중이거나 수유 중이신가요?",
      "answerType": "SINGLE", "maxSelect": 1,
      "options": [
        { "index": 0, "label": "해당 없어요", "exclusive": true },
        { "index": 1, "label": "임신 가능성이 있어요", "exclusive": false },
        { "index": 2, "label": "임신 중이에요", "exclusive": false },
        { "index": 3, "label": "수유 중이에요", "exclusive": false }
      ]
    },
    "progress": { "asked": 0, "base": 5, "max": 7 },
    "canStop": false
  }
}
```

그 밖의 회원은 첫 LLM 질문이 바로 온다:

```json
"question": {
  "turnId": 102, "turnNo": 1, "fixed": false, "topic": "CONCERN_OPEN",
  "text": "요즘 불편한 점을 골라주세요 (최대 2개)",
  "answerType": "MULTI", "maxSelect": 2,
  "options": [
    { "index": 0, "label": "쉽게 피곤해요", "exclusive": false }, { "index": 1, "label": "잠을 잘 못 자요", "exclusive": false },
    { "index": 2, "label": "없어요", "exclusive": true }, { "index": 3, "label": "잘 모르겠어요", "exclusive": true }
  ]
},
"progress": { "asked": 1, "base": 5, "max": 7 },
"canStop": false
```

| 필드 | 설명 |
|---|---|
| `turnNo` | 0 = 고정 질문(임신), 1부터 LLM 질문 |
| `topic` | 서버 주제 코드 (`CONCERN_OPEN`, `MED:METFORMIN`, `CONCERN:쉽게 피곤해요`, `CONDITION:HYPERTENSION`, `LIFESTYLE:SMOKING`, `PREGNANCY`). 화면에 보여줄 필요는 없다 |
| `maxSelect` | SINGLE 1, MULTI 2 |
| `options[].index` | 답할 때 보내는 값. 선택지 성격(kind: NONE/UNKNOWN/NORMAL/CONCERN)은 **내려주지 않는다** (서버 판단용) |
| `options[].exclusive` | true = "없어요"·"잘 모르겠어요"처럼 다른 선택지와 함께 고를 수 없는 선택지 (화면에서 고르면 다른 선택 해제) |
| `progress.asked` | 지금까지 낸 LLM 질문 수 (고정 질문 제외). 남은 수는 서버도 정확히 모른다 (답에 따라 달라짐) |
| `canStop` | true면 "여기까지 답하고 결과 보기" 가능 (임신 고정 질문에 답했고 LLM 질문을 1개 이상 답함, 진행 중일 때만) |

## 3. 답변 `POST /api/v1/personalized-sessions/{sessionId}/answers`

```json
{ "turnId": 101, "selected": [0] }
```

- `selected`는 고른 순서대로. 불편한 점(MULTI)에서 고른 순서대로 이어서 묻는다.
- 응답 (다음 질문이 있을 때): 2번과 같은 형식.
- 응답 (끝났을 때): `{ "sessionId": 12, "status": "READY", "question": null, "progress": { "asked": 5, "base": 5, "max": 7 } }`

검증 (실패하면 400 PQ004):
- 이 회차의 **마지막 질문**이고 아직 답하지 않은 질문인지 (`turnId`)
- 개수: SINGLE 1개, MULTI 1~2개 / 범위 안 / 중복 없음
- "없어요"·"잘 모르겠어요"는 다른 선택지와 함께 고를 수 없음

## 4. 여기까지 답하고 결과 보기 `POST /api/v1/personalized-sessions/{sessionId}/stop`

- 답하지 않은 마지막 질문은 버리고(`progress.asked`에서도 빠짐) `status = READY`.
- 조건: 임신 고정 질문 대상자는 그 질문에 답했고, 답한 LLM 질문이 1개 이상. 아니면 409 PQ003. 회차 응답의 `canStop`이 `true`일 때만 화면 버튼을 켠다.
- 이미 READY면 그대로 READY를 돌려준다. DONE·ABANDONED·FAILED면 409 PQ003.
- 응답: 3번의 끝났을 때와 같은 형식.

## 4b. 닫기 `POST /api/v1/personalized-sessions/{sessionId}/close`

- ✕ 닫기(ESC·바깥 클릭 포함). 추천 없이 `status = ABANDONED`, `stopped_by_user = true`. 답하지 않은 마지막 질문은 버린다.
- IN_PROGRESS·READY만. 이미 ABANDONED면 그대로 돌려준다. DONE·FAILED면 409 PQ003.
- 응답: `{ "sessionId": 12, "status": "ABANDONED", "question": null, "progress": {...}, "canStop": false }`

## 5. 정밀 추천 `POST /api/v1/personalized-sessions/{sessionId}/recommendation`

- `status`가 READY일 때만 (아니면 409 PQ003). 응답은 **V1 추천 응답과 같은 형식** ([recommendation-api.md](recommendation-api.md) 1번). 단 `responseId`는 `null`.
- 성공하면 회차 `status = DONE`. 같은 회차로 다시 호출하면 저장된 결과를 그대로 돌려준다 (LLM 다시 안 부름).
- 항상 동기 처리 (`recommendation.api-mode=queue`여도 200 + DONE).
- 안전 규칙(조건 코드·금지 목록·사후 규칙·서버 용량 계산)은 **프로필과 임신 답 기준 그대로** 적용된다. 맞춤 답변은 LLM의 성분 선택과 이유에만 쓰인다.
- 파이프라인 버전: `recommendation.pipeline-version`이 규칙 적용 버전(V4, V6, V7, V8, RULE_ONLY)이면 그 버전, 아니면(기본 V0, V1~V3) V4. 기록용 프롬프트 버전 뒤에 `-p` (예: `v4.2-p`).
- 품질 검사 (v4.2부터, V1 추천과 공통): 요약 100자·2문장 이내, 진료·처방·검사 지시와 제외·금지 성분 언급 금지, 성분 1개 이상. 어기면 1회 재요청하고, 그래도 남으면 요약은 고정 문구("입력하신 정보를 바탕으로 고른 성분이에요.")로 바꾸고 성분 0개는 그대로 저장한다 (화면은 0개면 고정 안내).
- LLM 장애(서킷 OPEN 등)면 V1과 같이 규칙 기반 기본 안내(`fallback = true`). 그 밖의 LLM 호출 실패는 502 R003 (회차는 READY 그대로라 다시 호출할 수 있다).

## 6. 오류 코드

| HTTP | code | 상황 | 화면 |
|---|---|---|---|
| 401 | E003 | `X-User-Id` 없음 | — |
| 409 | PQ001 | 건강정보 부족. `data.missing`에 빠진 항목 (1번 표) | V1 회원 설문으로 이동 |
| 404 | PQ002 | 회차 없음 또는 다른 회원의 회차 | 처음부터 |
| 409 | PQ003 | 회차 상태가 맞지 않음 (이미 끝남, READY 아님 등) | 처음부터 |
| 400 | PQ004 | 잘못된 답 (turnId 불일치, 개수·범위 오류, "없어요"와 다른 선택지 동시 선택) | — |
| 502 | PQ005 | 질문 생성 실패 (재요청까지 실패, LLM 호출 실패). 회차 `FAILED` | "잠시 후 다시 시도해 주세요" + 닫기 |
| 502 | R003 | 정밀 추천 LLM 호출 실패 | 다시 시도 |

PQ001 응답 예:

```json
{ "success": false, "code": "PQ001", "message": "건강정보가 부족해 기본 설문으로 진행합니다.", "data": { "missing": ["SENSITIVE_CONSENT"] } }
```

> 설계 문서의 P001~P005는 `P001`이 이미 영양제(PRODUCT_NOT_FOUND)에 쓰이고 있어 **PQ001~PQ005**로 정했다.

## 7. 서버 동작 요약 (화면 참고용)

- **주제 순서**: 불편한 점 → 복용 약(규칙 있는 계열 먼저) → 고른 불편한 점(최대 2개) → 질환 → 생활습관(흡연, 음주 많음, 카페인 많음, 채식, 햇빛 적음)
- **깊이**: 고른 답이 "더 확인 필요"(CONCERN)면 같은 주제를 한 번 더 묻는다 (약물·불편한 점 최대 2, 질환·생활습관 1)
- **질문 수**: 새 주제는 5문항 전까지만, 같은 주제 추가 질문은 7문항 전까지만
- **LLM 질문 검증**: 선택지 2~5개, "잘 모르겠어요" 1개, 라벨 20자 이내, 질문 60자 이내, 이전 질문 반복·금지어(중단, 끊으세요, 진단, 처방, 복용을 멈추) 없음 → 실패하면 이유를 붙여 1회 재요청

## 7b. 질문 미리 만들기 설정 (`pq.*`, 응답 형식은 같음)

| 설정 | 기본 | 내용 |
|---|---|---|
| `pq.prefetch-strategy` | `NONE` | `NONE` 질문마다 그때 생성 / `BACKGROUND` 첫 질문은 바로, 프로필 주제 1단계는 백그라운드 묶음 1회 / `UPFRONT` 시작 때 불편한 점 + 프로필 주제를 한 번에 / `UPFRONT_FOLLOWUP` UPFRONT + 불편한 점 파생 주제 묶음 + CONCERN일 때 이어질 2단계까지 |
| `pq.prefetch-topics` | 4 | 묶음에 넣을 프로필 주제 수 (계획 순서: 약 → 질환 → 생활습관) |
| `pq.prefetch-wait-ms` | 6000 | 미리 만드는 중인 질문을 기다리는 최대 시간. 넘으면 단건 생성 |

- 미리 만든 질문은 단건과 같은 검증을 통과한 것만 쓰고, 쓰기 직전에 이 회차의 이전 질문과 반복 검사를 다시 한다. 걸리거나 없으면 단건 생성
- 보관은 서버 메모리 (재시작하면 잃고 단건 생성으로 대신). 회차가 끝나면 비우고 쓰지 않은 질문은 `pq.prefetch{result=wasted}`로 센다

## 8. 기록·운영

| 테이블 | 내용 |
|---|---|
| `ai_question_sessions` | 회차 상태, 임신 답, 질문 수, 그만하기 여부, 추천 ID, V1 대비 변화(`diff_json`, 모니터링 전용) |
| `ai_question_turns` | 질문·선택지(kind 포함)·고른 답 |
| `ai_recommendations` | 정밀 추천 결과 (`response_id = NULL`, `session_id`) |
| `llm_call_logs` | `call_tag` = PQ_QUESTION, PQ_QUESTION_REPAIR, PQ_QUESTION_BATCH·PQ_QUESTION_FOLLOWUP_BATCH(미리 만들기), PQ_QUESTION_HEDGE(이중 요청), RECOMMEND(정밀 추천), `session_id`. outcome `DISCARDED` = 이중 요청에서 쓰지 않은 응답 |

- 90일 지난 회차는 매일 04:00 삭제 (질문은 함께 삭제, 추천 결과는 남고 `session_id`만 NULL)
- 메트릭: `pq.question.generate{outcome}`, `pq.question.latency`, `pq.prefetch{result}`, `pq.hedge{result}`, `pq.session{status,stopped}`, `pq.session.questions`, `pq.answer.kind{kind}`, `pq.recommendation.diff{type}`, `pq.session.purged`
- 회차당 비용: `SELECT session_id, SUM(input_tokens), SUM(output_tokens) FROM llm_call_logs WHERE session_id IS NOT NULL GROUP BY session_id`
- 알림 진입 재사용(`/users/me/recommendations/latest`)은 지금 V1(설문 기반) 결과만 본다. V2 결과 재사용은 TODO
- DB: 기존 DB는 `docs/sql/STEP13_ai_survey_setup.sql` → `docs/sql/STEP14_personalized_questions.sql` 순서로 실행
