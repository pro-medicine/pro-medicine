# Version 2 — 회원 맞춤 질문 → 정밀 성분 추천 상세 설계

> 담당: 영준 / 작성일: 2026-10-26 / 브랜치: `feat/recommendation-pipeline`에서 분기한 V2 브랜치
> 상위 문서: `docs/ai-roadmap.md`

---

## 1. 목적과 범위

- **V1 = 비회원 전용**: 고정 설문 → 성분 추천 (이미 구현)
- **V2 = 회원 전용**: 저장된 건강정보 + **LLM이 만드는 맞춤 질문(객관식)** → 정밀 성분 추천
- 홈의 같은 배너 위치에서 로그인 여부·건강정보 유무로 V1/V2가 갈린다.

### 이번에 하는 것
- 회원 V1/V2 분기 판단 API
- 맞춤 질문 회차(세션) API: 시작, 답변, 그만하기, 정밀 추천 생성
- 서버 주제 계획·깊이·종료 판단
- LLM 질문 생성 (구조화 출력) + 검증 + 재요청
- 정밀 추천: 기존 추천 파이프라인 재사용, 결과는 기존 `ai_recommendations`에 저장
- 회차 기록 저장(90일) + 모니터링 지표
- V2 화면 (영준 담당 폴더)

### 하지 않는 것 (TODO)
- 비회원 V1 결과 → "로그인 후 건강정보 등록" 연결 (화면이 어느 정도 만들어진 뒤)
- 로그인 연동 (다른 팀원 개발 중. 그 전까지는 `X-User-Id` 헤더로 회원 판단)
- LLM 실패 시 대체 질문 (지금은 "잠시 후 다시 시도" 안내만)
- 이용 횟수 제한

---

## 2. 확정된 결정

| # | 결정 |
|---|---|
| D1 | V1은 비회원, V2는 회원. 회원이어도 건강정보가 부족하면 V1 |
| D2 | 건강정보 있음 = ① 성별·출생 연도 있음 ② 민감정보 동의 ③ 질환·복용 약을 한 번이라도 답함("없음" 포함) |
| D3 | 응답은 **객관식만**. 선택지 **최대 5개**, "없어요"·"잘 모르겠어요"도 선택지 안에 포함 |
| D4 | 여러 개 선택(MULTI) 허용, **최대 2개**. 질문 문장에 "최대 2개" 안내 |
| D5 | **질문과 선택지 모두 LLM이 생성**. 선택지마다 `kind`(NONE/UNKNOWN/NORMAL/CONCERN)를 붙이고 서버는 `kind`로 깊이·전환 판단 |
| D6 | 질문 수 기본 5, 최대 7 |
| D7 | 주제별 최대 깊이: 약물·불편한 점 2, 생활습관 1 |
| D8 | "그만하기" 허용 → 지금까지 답으로 정밀 추천 |
| D9 | 맞춤 답변은 **프로필에 저장하지 않음** (회차 기록에만) |
| D10 | 이미 답한 주제도 다시 물음 (건너뛰지 않음). 질문 표현은 매번 달라도 됨 |
| D11 | V1 대비 변화는 **모니터링만** (사용자에게 안 보임) |
| D12 | 모델은 품질 우선 (추천과 같은 모델). 화면은 "질문을 만들고 있어요" 같은 진행 문구로 대기 시간을 가림 (화면에서 시간에 맞춰 문구 변경) |
| D13 | LLM 실패 → "잠시 후 다시 시도해 주세요" |
| D14 | 임신·수유 여부: **19~50세 여성**에게 V2 시작 시 고정 질문으로 묻기 (질문 수에 포함 안 함, 회차에만 저장) |
| D15 | 회차 기록(질문·선택지·답) 저장, **90일 후 삭제** |
| D16 | "불편한 점이 있으세요?" 열린 질문을 **맨 처음**에 |
| D17 | 중간에 나가면 다시 시작할 때 처음부터 |
| D18 | 정밀 추천 결과는 V1과 **같은 형식·같은 화면** |
| D19 | 정밀 추천 결과는 기존 `ai_recommendations`에 저장 (`session_id` 추가, `response_id`는 NULL 허용) |
| D20 | 맞춤 질문은 별도 테이블 (`ai_question_sessions`, `ai_question_turns`) |

---

## 3. 전체 흐름

```
[홈 배너]
 ├ 비회원(X-User-Id 없음) → V1 (기존)
 └ 회원 → GET /api/v1/users/me/recommendation-mode
          ├ mode = V1 (건강정보 부족) → V1 회원 모드 설문 (답이 프로필에 저장됨) → 끝나면 V2 안내
          └ mode = V2 → POST /api/v1/personalized-sessions (회차 시작)
                         ├ 19~50세 여성 → 임신·수유 고정 질문 (서버가 만든 질문, LLM 호출 없음)
                         └ 첫 LLM 질문: 불편한 점 (MULTI, 최대 2개)
                         ↓
                       POST .../answers (답 저장 → 서버가 다음 주제 결정 → LLM 질문 생성)
                         ↓ (반복, 기본 5 / 최대 7)
                       done = true 또는 사용자가 "그만하기"
                         ↓
                       POST .../recommendation (정밀 추천: 기존 파이프라인)
                         ↓
                       V1과 같은 결과 화면
```

---

## 4. API 명세

공통: 응답은 `ApiResponse` (`{ success, code, message, data }`). 회원 판단은 `X-User-Id` 헤더 (로그인 연동 시 교체). 모든 V2 API는 헤더가 없으면 401.

### 4-1. 모드 판단 `GET /api/v1/users/me/recommendation-mode`
```json
{ "mode": "V2", "missing": [] }
{ "mode": "V1", "missing": ["BIRTH_YEAR", "SENSITIVE_CONSENT", "MEDICATIONS_ANSWERED"] }
```
- `missing` 값: `GENDER`, `BIRTH_YEAR`, `SENSITIVE_CONSENT`, `CONDITIONS_ANSWERED`, `MEDICATIONS_ANSWERED`
- "답함" 판단: `user_conditions`/`user_medications`에 행이 있거나, 회원의 설문 응답 중 해당 문항(Q-CONDITIONS / Q-MEDICATIONS)에 답한 기록이 있으면 답함 ("없음" 선택 포함)

### 4-2. 회차 시작 `POST /api/v1/personalized-sessions`
요청 본문 없음. 진행 중인 회차가 있으면 `ABANDONED`로 바꾸고 새로 시작 (D17).
```json
{
  "sessionId": 12,
  "status": "IN_PROGRESS",
  "question": {
    "turnId": 101, "turnNo": 0, "fixed": true, "topic": "PREGNANCY",
    "text": "현재 임신 중이거나 수유 중이신가요?",
    "answerType": "SINGLE", "maxSelect": 1,
    "options": [
      { "index": 0, "label": "해당 없어요" },
      { "index": 1, "label": "임신 가능성이 있어요" },
      { "index": 2, "label": "임신 중이에요" },
      { "index": 3, "label": "수유 중이에요" }
    ]
  },
  "progress": { "asked": 0, "base": 5, "max": 7 }
}
```
- `kind`는 **화면에 내려주지 않는다** (서버 내부 판단용).
- 임신 고정 질문이 아니면 첫 LLM 질문(불편한 점)이 바로 온다.

### 4-3. 답변 `POST /api/v1/personalized-sessions/{sessionId}/answers`
```json
{ "turnId": 101, "selected": [0] }
```
응답 (다음 질문이 있을 때): 4-2와 같은 형식 (`question`, `progress`)
응답 (끝났을 때): `{ "sessionId": 12, "status": "READY", "question": null, "progress": {...} }`
- 검증: 이 회차의 **마지막 질문**에 대한 답인지, `selected` 개수(SINGLE 1개, MULTI 1~2개), 범위, NONE/UNKNOWN 선택지는 다른 선택지와 함께 고를 수 없음

### 4-4. 그만하기 `POST /api/v1/personalized-sessions/{sessionId}/stop`
- 마지막 질문에 답하지 않았으면 그 질문은 버린다. `status = READY`로 바꾼다. 답한 질문이 0개여도 가능 (프로필만으로 정밀 추천).

### 4-5. 정밀 추천 `POST /api/v1/personalized-sessions/{sessionId}/recommendation`
- `status`가 `READY`일 때만. 응답은 **V1 추천 응답과 같은 형식** (`RecommendationResponse`).
- 성공하면 회차 `status = DONE`, `recommendation_id` 저장.
- 같은 회차로 다시 호출하면 저장된 결과를 그대로 돌려준다.

### 4-6. 오류 코드
| HTTP | code | 상황 |
|---|---|---|
| 401 | E003 | `X-User-Id` 없음 (기존 코드 재사용) |
| 409 | P001 | 건강정보 부족 → V1로 (`missing` 포함) |
| 404 | P002 | 회차 없음 또는 다른 회원의 회차 |
| 409 | P003 | 회차 상태가 맞지 않음 (이미 끝남, READY 아님 등) |
| 400 | P004 | 잘못된 답 (turnId 불일치, 개수·범위 오류, NONE과 다른 선택지 동시 선택) |
| 502 | P005 | 질문 생성 실패 (재요청까지 실패) → 화면: "잠시 후 다시 시도해 주세요". 회차 `FAILED` |

---

## 5. 서버 주제 계획 (Topic Planner)

### 5-1. 주제 종류
| 주제 | 생성 조건 | 최대 깊이 | answerType |
|---|---|---|---|
| `CONCERN_OPEN` 불편한 점 | 항상, **맨 처음** | 1 (이 질문 자체) | MULTI (최대 2) |
| `CONCERN:<라벨>` | 불편한 점에서 고른 선택지마다 (최대 2개, 고른 순서) | 2 | SINGLE |
| `MED:<drugClass 또는 이름>` | 프로필의 복용 약마다. 규칙(`recommendation_rules`)이 있는 계열 먼저 | 2 | SINGLE |
| `CONDITION:<code>` | 프로필 질환마다 | 1 | SINGLE |
| `LIFESTYLE:SMOKING` | smoking ≠ NONE | 1 | SINGLE |
| `LIFESTYLE:DRINKING` | drinking = HIGH | 1 | SINGLE |
| `LIFESTYLE:CAFFEINE` | caffeine = HIGH | 1 | SINGLE |
| `LIFESTYLE:DIET` | diet_type = VEGAN, VEGETARIAN | 1 | SINGLE |
| `LIFESTYLE:SUN` | sun_exposure = LOW | 1 | SINGLE |

- 고정 질문 `PREGNANCY`는 주제 계획 밖이며 질문 수에 세지 않는다.
- 약 이름만 있고 계열이 없는 약(`unmatched_name`)도 `MED:<이름>`으로 묻는다 (LLM에는 이름만 전달).

### 5-2. 순서
`CONCERN_OPEN` → `MED:*` → `CONCERN:*` (고른 순서) → `CONDITION:*` → `LIFESTYLE:*`

- 안전 관련 정보(복용 약)를 질문 예산 안에서 먼저 묻기 위해 약물을 불편한 점보다 앞에 둔다 (2026-10-26 결정, 12장-1)

### 5-3. 진행 규칙 (서버가 결정, LLM은 판단하지 않음)
사용자가 답하면 고른 선택지의 `kind`로 결정한다.

| 고른 선택지 kind | 동작 |
|---|---|
| `CONCERN` | 현재 깊이 < 주제 최대 깊이 → **같은 주제 depth+1** / 아니면 다음 주제 |
| `NORMAL` | 다음 주제 |
| `NONE` | 다음 주제 (CONCERN_OPEN에서 NONE이면 `CONCERN:*` 주제 없음) |
| `UNKNOWN` | 다음 주제 |
| MULTI에서 2개 선택 | 둘 다 `CONCERN:*` 주제로 등록 (kind와 상관없이, NONE/UNKNOWN 제외) |

### 5-4. 질문 수 예산 (D6)
- `asked` = 지금까지 낸 LLM 질문 수 (고정 질문 제외)
- **새 주제 시작**은 `asked < 5`일 때만
- **같은 주제 깊이 질문(depth 2)**은 `asked < 7`이면 허용
- 남은 주제가 없거나 위 조건을 못 맞추면 `READY`

### 5-5. 예시 (흡연 반 갑 + 메트포르민 + 고혈압, 46세 남성)
| asked | 주제 | depth | 질문 (LLM 생성 예) | 고른 답 → kind | 다음 |
|---|---|---|---|---|---|
| 1 | CONCERN_OPEN | 1 | 요즘 불편한 점을 최대 2개 골라주세요 | 쉽게 피곤해요(CONCERN), 잠을 잘 못 자요(CONCERN) | CONCERN:피로, CONCERN:수면 등록 |
| 2 | MED:METFORMIN | 1 | 메트포르민을 드신 지 얼마나 되셨어요? | 4년 이상 → CONCERN | 같은 주제 depth 2 |
| 3 | MED:METFORMIN | 2 | 손발이 저리거나 입안이 자주 헐어요? | 없어요 → NONE | 다음 주제 |
| 4 | CONCERN:쉽게 피곤해요 | 1 | 피곤함은 주로 언제 느끼세요? | 하루 종일 피곤해요 → CONCERN | depth 2 (asked 4 < 7) |
| 5 | CONCERN:쉽게 피곤해요 | 2 | 숨이 차거나 어지러운 적이 있나요? | 없어요 → NONE | 다음 주제 |
| 6 | CONCERN:잠을 잘 못 자요 | 1 | — | — | **asked = 5라 새 주제 시작 불가 → READY** |

> 약물 우선 순서라 복용 약은 예산 안에서 먼저 확인된다. 대신 불편한 점 2개를 고르고 깊이 질문이 이어지면 두 번째 불편한 점·질환·생활습관 주제는 묻지 못할 수 있다 (프로필 값은 정밀 추천 입력에 그대로 들어간다).

---

## 6. LLM 질문 생성

### 6-1. 요청 (system)
```text
너는 영양제 성분 추천을 돕는 문진 보조야. 진단이나 처방을 하지 않는다.
주어진 topic 안에서 객관식 질문 1개를 만든다.

규칙
- 질문은 50자 이내, 존댓말, 쉬운 말
- 이미 알고 있는 정보와 이전 질문을 다시 묻지 않는다
- 선택지는 2~5개, 서로 겹치지 않게, 각 20자 이내
- 선택지에 "잘 모르겠어요"를 반드시 넣고, 해당이 없을 수 있으면 "없어요"도 넣는다
- 숫자 범위를 묻는 선택지는 빈틈없이 나눈다 (예: 1년 미만 / 1~4년 / 4년 이상)
- answerType이 MULTI면 질문 끝에 "(최대 2개)"를 붙인다
- 약을 끊거나 바꾸라고 하지 않는다. 병명을 단정하지 않는다
- 각 선택지에 kind를 붙인다
  NONE: 해당 없음 / UNKNOWN: 모름 / NORMAL: 특별한 주의가 필요 없는 답 /
  CONCERN: 영양 상태나 복용 약과 관련해 더 확인할 필요가 있는 답
```

### 6-2. 요청 (user) 템플릿
```text
## 알고 있는 정보
- 성별: {남성/여성}, 나이: {n}세
- 흡연: {라벨} / 음주: {라벨} / 커피·차: {라벨} / 식단: {라벨} / 햇빛: {라벨}
- 진단받은 질환: {목록 또는 없음}
- 복용 중인 약: {이름(계열)} 목록 또는 없음
- 임신·수유: {라벨 또는 해당 없음}
## 이번 질문
- topic: {topic 코드} ({한글 설명})
- depth: {d} / 최대 {max}
- answerType: {SINGLE|MULTI}
- 목적: {주제별 목적 문장 — 서버 상수}
## 이전 질문과 답 (이 회차)
1. Q: {질문} / A: {고른 라벨들}
...
```
- 주제별 "목적 문장"은 서버 상수로 관리한다. 예: `MED:METFORMIN` → "복용 기간과 비타민 B12 관련 신호 확인", `CONCERN_OPEN` → "사용자가 불편하게 느끼는 증상 범위 파악", `LIFESTYLE:SMOKING` → "흡연량과 기간 확인"
- `CONCERN:<라벨>`은 라벨을 그대로 넣는다 ("사용자가 고른 불편한 점: 쉽게 피곤해요").

### 6-3. 출력 스키마 (json_schema strict)
```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["question", "answerType", "options"],
  "properties": {
    "question": { "type": "string" },
    "answerType": { "type": "string", "enum": ["SINGLE", "MULTI"] },
    "options": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["label", "kind"],
        "properties": {
          "label": { "type": "string" },
          "kind": { "type": "string", "enum": ["NONE", "UNKNOWN", "NORMAL", "CONCERN"] }
        }
      }
    }
  }
}
```
- 개수·길이 제한은 strict 스키마로 보장되지 않을 수 있으므로 **서버 검증**으로 막는다.

### 6-4. 서버 검증 (`PersonalizedQuestionValidator`)
| 검사 | 실패 시 |
|---|---|
| `answerType`이 서버가 요청한 값과 같음 | 재요청 |
| 선택지 2~5개, 라벨 중복 없음, 라벨 1~20자 | 재요청 |
| `UNKNOWN` 선택지가 정확히 1개 | 재요청 |
| `NONE` 선택지는 0~1개 | 재요청 |
| 질문 1~60자, 이전 질문과 같은 문장이 아님 | 재요청 |
| 금지어(중단, 끊으세요, 진단, 처방, 복용을 멈추) 없음 | 재요청 |

- 재요청은 기존 V1과 같은 방식: 틀린 이유를 붙여 **1회**. 또 실패하면 P005 + 회차 `FAILED`.
- 서버가 화면으로 보낼 때 `kind`는 빼고 `index`를 붙인다.

### 6-5. 호출 설정
- 모델: 추천과 같은 모델 (`llm` 설정 재사용), 추론 강도 low, `max_tokens` 500
- `llm_call_logs.call_tag`: `PQ_QUESTION`, `PQ_QUESTION_REPAIR` (기존 테이블 그대로, 메타데이터 추가가 필요하면 `response_id` 대신 세션 id를 기록할 컬럼 검토)
- Fake LLM: 테스트·키 없는 환경용으로 질문 스키마에 맞는 고정 응답을 돌려주게 확장

---

## 7. 정밀 추천

1. `RecommendationInputAssembler`에 **프로필 기반 조립** 메서드 추가: `assembleForMember(userId, pregnancy)` — 설문 없이 `HealthProfileService` 스냅샷(성별, 출생 연도, 흡연·음주·커피·식단·햇빛, 질환, 복용 약) + 회차의 임신 답
   - 프로필에 없는 값(수면, 피로, 목표, 현재 섭취 영양제)은 비워 둔다. 맞춤 답변이 이 역할을 한다
2. `RecommendationInput`에 `personalizedAnswers` 추가: `List<{topic, question, selectedLabels}>` (NONE/UNKNOWN 답도 포함)
3. `InputFormatter`에 블록 추가:
   ```text
   ## 맞춤 질문 답변
   - [불편한 점] 요즘 불편한 점을 최대 2개 골라주세요 → 쉽게 피곤해요, 잠을 잘 못 자요
   - [불편한 점: 쉽게 피곤해요] 피곤함은 주로 언제 느끼세요? → 하루 종일 피곤해요
   ```
4. 파이프라인은 지금 운영 버전(V4 이상)을 그대로 사용 → 조건 코드·사전 금지 목록·사후 규칙·서버 용량 계산이 **프로필 기준으로 그대로** 적용된다. 맞춤 답변은 LLM의 성분 선택과 이유에만 영향을 준다.
5. 프롬프트 버전은 `vX.Y-p`처럼 구분해서 `llm_call_logs.prompt_version`에 남긴다.
6. 결과 캐시(V6): 켜져 있다면 캐시 키에 맞춤 답변 해시를 포함한다. 지금 기본은 꺼짐.
7. 저장: `RecommendationStore`가 `response_id = null`, `session_id = 회차`로 저장.

---

## 8. 데이터베이스

### 8-1. 새 테이블
```sql
CREATE TABLE ai_question_sessions (
  session_id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id             BIGINT       NOT NULL,
  status              VARCHAR(15)  NOT NULL,              -- IN_PROGRESS / READY / DONE / ABANDONED / FAILED
  pregnancy           VARCHAR(20)  NULL,                  -- NOT_APPLICABLE / POSSIBLE / PREGNANT / LACTATING (19~50세 여성만)
  question_count      INT          NOT NULL DEFAULT 0,    -- LLM 질문 수 (고정 질문 제외)
  stopped_by_user     BOOLEAN      NOT NULL DEFAULT FALSE,
  recommendation_id   BIGINT       NULL,
  baseline_recommendation_id BIGINT NULL,                 -- 모니터링용: 비교한 V1 추천
  diff_json           JSON         NULL,                  -- 모니터링용: V1 대비 추가·제외·순서 변화
  error_code          VARCHAR(20)  NULL,
  started_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at         DATETIME     NULL,
  KEY idx_pq_sessions_user (user_id, started_at),
  KEY idx_pq_sessions_started (started_at),
  CONSTRAINT fk_pq_sessions_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE ai_question_turns (
  turn_id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  session_id      BIGINT        NOT NULL,
  turn_no         INT           NOT NULL,                 -- 0 = 고정 질문(임신), 1부터 LLM 질문
  fixed           BOOLEAN       NOT NULL DEFAULT FALSE,
  topic           VARCHAR(60)   NOT NULL,
  depth           INT           NOT NULL,
  question_text   VARCHAR(200)  NOT NULL,
  answer_type     VARCHAR(10)   NOT NULL,                 -- SINGLE / MULTI
  options_json    JSON          NOT NULL,                 -- [{"label":"4년 이상","kind":"CONCERN"}, ...] 최대 5개
  selected_json   JSON          NULL,                     -- [2] 또는 [0,3], 답하기 전 NULL
  llm_call_log_id BIGINT        NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  answered_at     DATETIME      NULL,
  UNIQUE KEY uq_pq_turn (session_id, turn_no),
  CONSTRAINT fk_pq_turns_session FOREIGN KEY (session_id) REFERENCES ai_question_sessions (session_id) ON DELETE CASCADE
);
```

### 8-2. 기존 테이블 변경 (`ai_recommendations`)
```sql
ALTER TABLE ai_recommendations MODIFY response_id BIGINT NULL;
ALTER TABLE ai_recommendations ADD COLUMN session_id BIGINT NULL;
ALTER TABLE ai_recommendations ADD CONSTRAINT fk_ai_recommendations_session
  FOREIGN KEY (session_id) REFERENCES ai_question_sessions (session_id) ON DELETE SET NULL;
-- response_id 또는 session_id 중 하나는 있어야 한다 → 엔티티/서비스에서 검증 (90일 삭제 후 session_id가 NULL이 되므로 DB CHECK는 걸지 않는다)
```
```sql
-- llm_call_logs: V2 호출(질문 생성·정밀 추천)은 response_id가 없으므로 회차 id를 기록한다 (기록용, FK 없음)
ALTER TABLE llm_call_logs ADD COLUMN session_id BIGINT NULL;
```
- 엔티티 `AiRecommendation.response`를 `optional = true`로, `session` 연관 추가
- 기존 V1 코드(`response` 기준 조회, 알림 진입 재사용)가 `response == null`인 행에서 깨지지 않는지 확인

### 8-3. SQL 파일
- `backend/docs/sql/STEP14_personalized_questions.sql`: 위 DDL (기존 DB용, 다시 실행해도 안전하게)
- `backend/schema.sql`: 새 테이블·변경 반영
- `backend/docs/sql/STEP13_ai_survey_setup.sql`: 맨 끝에 STEP14 내용을 추가할지, 별도로 둘지 → 별도로 두고 README에 순서 안내 (STEP13 → STEP14)
- `backend/docs/schema-changes.md`에 STEP 14 행

### 8-4. 90일 삭제
- `@Scheduled(cron = "0 0 4 * * *")` 하루 한 번: `started_at < now - 90일`인 회차 삭제 (turns는 CASCADE, 추천 결과는 남고 `session_id`만 NULL)
- 삭제 건수를 로그·메트릭으로 남긴다

---

## 9. 모니터링 (D11)

### 9-1. V1 대비 변화 (사용자에게 안 보임)
- 정밀 추천 완료 시, 같은 회원의 **가장 최근 V1 추천**(설문 기반, 30일 이내)이 있으면 비교해서 `diff_json`에 저장. 없으면 비교하지 않는다 (추가 LLM 호출 없음)
  ```json
  { "added": ["VITAMIN_B12"], "removed": ["VITAMIN_C"], "rankChanged": [{"ingredient":"MAGNESIUM","from":3,"to":1}] }
  ```

### 9-2. 메트릭 (Micrometer)
| 이름 | 태그 | 의미 |
|---|---|---|
| `pq.question.generate` | outcome = success / repair_success / fail | 질문 생성 결과 |
| `pq.question.latency` (Timer) | — | 질문 생성 지연 |
| `pq.session` | status = DONE / ABANDONED / FAILED, stopped = true/false | 회차 결과 |
| `pq.session.questions` (Distribution) | — | 회차당 질문 수 |
| `pq.answer.kind` | kind | 고른 답의 kind 분포 |
| `pq.recommendation.diff` | type = added / removed / rank | V1 대비 변화 |

- 비용은 `llm_call_logs`의 `PQ_*` 태그로 회차당 합계를 SQL로 계산

---

## 10. 화면 (영준 담당, 기존 프론트 구조·코드 형식 따름)

- 위치: `frontend/src/recommendation/` 안에 V2 컴포넌트 추가 (`PersonalizedModal.jsx`, `QuestionStep.jsx` 등), API는 `frontend/src/api/recommendationApi.js`에 함수 추가
- 홈 배너: `userId`가 있으면 회원 → 모드 API → V1/V2 분기. 로그인 연동 전까지 `App.jsx`의 `userId` 값으로 판단 (TODO 주석)
- 질문 화면
  - 한 번에 질문 1개, 선택지 버튼 (MULTI는 최대 2개, 2개 고르면 나머지 비활성)
  - "없어요"·"잘 모르겠어요"를 고르면 다른 선택 해제 (화면에서도 막고 서버도 검증)
  - 진행 표시: "질문 2 / 최대 7" 정도, 정확한 남은 수는 서버도 모르므로 막대는 쓰지 않음
  - "그만하고 결과 보기" 버튼 상시 노출
- 대기 화면 (질문 생성 중): 1.5~2초마다 문구가 바뀌는 애니메이션
  - "답변을 살펴보고 있어요" → "다음 질문을 고르고 있어요" → "거의 다 만들었어요"
- 정밀 추천: V1 `ResultStep` 재사용
- 오류: P005면 "잠시 후 다시 시도해 주세요" + 닫기, P001이면 V1 회원 모드로 이동

---

## 11. 테스트

- `TopicPlannerTest`: 프로필별 주제 목록·순서, MULTI 2개 → 주제 2개, 예산(새 주제 5, 깊이 7), kind별 전환
- `PersonalizedQuestionValidatorTest`: 6-4 표의 각 실패 케이스
- `PersonalizedSessionApiTest` (Fake LLM): 시작(여성 19~50세 → 임신 고정 질문 / 남성 → 바로 LLM 질문), 답변 검증 오류, 그만하기, 정밀 추천 저장(`response_id = null`, `session_id` 있음), 다른 회원 회차 접근 404, 비회원 401, 건강정보 부족 409
- `RecommendationModeTest`: D2 기준 각각 빠졌을 때
- 기존 V1 테스트·골든이 그대로 통과해야 함 (`response` NULL 허용 변경 영향 확인)
- 90일 삭제 스케줄러 단위 테스트

---

## 12. 구현 전 확인할 점

1. **주제 순서** → 결정: 약물 우선 `CONCERN_OPEN → MED → CONCERN:* → CONDITION → LIFESTYLE` (5-2, 5-5 반영)
2. **질병 주제 깊이** → 결정: 질환(`CONDITION`)은 1
3. **로그인 연동 방식**이 정해지면 `X-User-Id` 판단 부분(`@CurrentUserId`)만 교체
4. **알림 진입 재사용** → 결정: 지금은 V1(설문 기반) 추천만 재사용. V2 정밀 추천 재사용은 TODO
5. **`llm_call_logs.session_id`** → 결정: 추가 (NULL 허용, FK 없음). V2 호출은 `response_id`가 없어 회차당 비용 계산에 필요 (8-2)
