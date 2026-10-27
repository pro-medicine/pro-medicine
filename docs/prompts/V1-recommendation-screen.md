# [작업 프롬프트] Version 1 — 설문 → 영양성분 추천 화면 (AI 담당: 영준)

## 0. 먼저 읽을 것
- 루트 `CLAUDE.md` (팀 규칙: 기존 화면 유지, 담당 폴더, 공통 파일은 협의 후 수정, 새 라이브러리 금지, `pnpm build`, docs/logs 기록)
- `backend/docs/api/survey-api.md` (설문 API)
- `backend/docs/api/recommendation-api.md` (추천 API)
- `frontend/src/App.jsx`, `frontend/src/api/client.js`, `frontend/src/home/HomeFeature.tsx`(기존 `SurveyModal`, 하드코딩 예시)

코드를 쓰기 전에 위 파일을 읽고, 아래 설계와 다른 점이 있으면 구현하지 말고 먼저 알려줘.

## 1. 목표
백엔드에 이미 완성된 설문·추천 API를 화면에 연결해서
**설문 → (민감정보 동의) → 제출 → 추천 생성 → 결과 표시** 흐름을 완성한다.
백엔드 코드는 수정하지 않는다.

## 2. 작업 범위와 위치
기존 프론트 구조를 그대로 따른다. 새 구조를 만들지 말고, `home/`, `friends/`, `api/`가 어떻게 생겼는지 먼저 보고 같은 방식으로 만든다.

```
frontend/src/
├─ api/
│  └─ recommendationApi.js     ← 새 파일 (friendApi.js와 같은 형식)
└─ recommendation/             ← 새 기능 폴더 (home/, friends/와 같은 레벨)
   ├─ index.js                 ← home/index.js처럼 대표 파일 export
   ├─ RecommendationModal.jsx  ← 전체 흐름 컨테이너 (기존 모달 패턴)
   ├─ SurveyStep.jsx
   ├─ ConsentStep.jsx
   ├─ ResultStep.jsx
   ├─ MedicationInput.jsx
   ├─ SupplementInput.jsx
   └─ recommendation.css       ← 이 기능 전용 CSS
```

- **수정 금지**: `src/home/*`(채원 담당), `src/shared/ui.tsx`, `src/index.css`, `src/api/client.js`, 다른 팀원 폴더
- **`App.jsx` 연결은 최소한으로**: 기존 `SurveyModal` 자리를 `RecommendationModal`로 바꾸는 import 1줄 + 렌더 1줄만. 변경 전에 나에게 확인받는다 (공통 파일).
- 새 라이브러리 추가 금지. Tailwind 클래스도 쓰지 않는다 (기존 화면은 일반 CSS 파일 사용).

## 2-1. 코드 스타일 (기존 코드와 똑같이)
참고 파일: `home/HomePage.jsx`, `home/RecommendBanner.jsx`, `home/index.js`, `home/home.css`, `friends/AddFriendModal.jsx`, `api/friendApi.js`

- **파일 형식**: `.jsx` (TypeScript 쓰지 않음), 함수 컴포넌트 + `export default function 이름(...)`
- **파일 첫 줄 주석**: `// 추천 설문 모달 (담당: 영준) — 설문 → 제출 → 결과 흐름` 처럼 한 줄 설명
- **props 설명**: 컴포넌트 위에 JSDoc으로 `props (App.jsx에서 전달)` 목록 (HomePage.jsx 참고)
- **상태**: `useState` 옆에 한글 한 줄 주석 (`const [sending, setSending] = useState(false); // 제출 중 여부`)
- **흐름 주석**: 큰 단계는 `// 1) 화면이 처음 열릴 때 ...`, `// 2) ...`처럼 번호 주석
- **API 파일**: `import { api } from "./client";` 후 함수마다 한글 주석 한 줄 + `return api.get/post(...)` 형태 (friendApi.js와 동일). 회원 헤더는 `{ headers: { "X-User-Id": userId } }`로 넘기고 userId가 없으면 헤더를 빼는 작은 헬퍼 하나만 둔다
- **비동기 처리**: `async/await` + `try/catch`, 실패 메시지는 `e.message`를 state에 넣어 화면에 표시 (AddFriendModal.jsx 방식)
- **모달 패턴**: `AddFriendModal.jsx`와 같은 구조 — 바깥 `div`(배경, `onMouseDown={onClose}`) + 안쪽 카드(`onMouseDown={(e) => e.stopPropagation()}`), ESC로 닫기 `useEffect`, 닫기 버튼 `aria-label="닫기"`
- **CSS**: `home.css`처럼 파일 맨 위에 설명 주석, **모든 선택자는 `rec-` 접두사**, 색상 변수는 `:root`가 아니라 `.rec-modal` 안에만 선언. 색·여백·둥근 모서리는 home.css의 톤(베이지 배경, 골드 포인트, 흰 카드)에 맞춘다
- **들여쓰기**: 2칸, 큰따옴표, 세미콜론 사용 (기존 파일과 동일)
- **조건 렌더링**: 단계별 화면은 `step` state 하나로 분기 (`"consent" | "survey" | "loading" | "result" | "error"`)

## 3. API 요약 (상세는 명세 문서 기준)
공통: 응답은 `{ success, code, message, data }`. `src/api/client.js`의 `api`(axios)는 성공 시 `data`만 돌려주고 실패 시 `Error(message)`를 던진다.
회원 구분: 헤더 `X-User-Id: {userId}` (없으면 비회원). `App.jsx`의 `userId`를 props로 받는다. 비회원 테스트를 위해 `userId`가 없으면 헤더를 보내지 않는다.

| 순서 | API | 용도 |
|---|---|---|
| 1 | `GET /api/v1/surveys/active` | 문항 + 회원이면 `prefill`, `needsConfirm` |
| 2 | `POST /api/v1/users/me/sensitive-consent` | 회원 민감정보 동의 (동의 후 1번 다시 호출하면 민감 문항 포함) |
| 3 | `GET /api/v1/drugs?keyword=` | 복용 약 검색 (Q-MEDICATIONS) |
| 4 | `GET /api/v1/ingredients` | 성분 코드 → 이름·단위 (Q-SUPPLEMENTS 입력, 결과 표시) |
| 5 | `POST /api/v1/surveys/{surveyId}/responses` | 제출 → `responseId` |
| 6 | `POST /api/v1/recommendations` `{ responseId }` | 추천 생성 |
| 7 | `GET /api/v1/recommendations/{id}` | queue 모드 폴링 |

⚠️ `api` 인터셉터가 HTTP 상태 코드를 버리므로, **200/202 구분 대신 `data.status`로 판단**한다.
- `DONE` → 결과 화면
- `PENDING` / `PROCESSING` → 1초 간격으로 7번 폴링 (최대 90초, 넘으면 에러 화면)
- `FAILED` → 에러 화면 (`errorCode` 표시용)

## 4. 화면 흐름
```
[시작] GET surveys/active
  ├ 회원 + 동의 안 함 → ConsentStep (동의하면 surveys/active 재호출 / 건너뛰면 민감 문항 없이 진행)
  └ 그 외 → SurveyStep
[SurveyStep] 한 문항씩 진행 (이전/다음, 진행률 표시). 서버 호출 없음
  - needsConfirm = true면 첫 화면에 "저장된 정보가 6개월 이상 지났어요. 확인해 주세요" 안내
  - prefill 값은 미리 선택된 상태로 표시하고 수정 가능
  - 모든 문항은 건너뛰기 가능 (답하지 않은 문항은 제출에서 뺀다)
[제출] POST responses → POST recommendations
[LoadingStep] "AI가 추천을 만들고 있어요" (수 초~수십 초 걸릴 수 있음 안내)
[ResultStep] 결과 표시
[ErrorStep] 메시지 + 다시 시도 버튼 (제출부터 재시도 / 처음부터)
```

## 5. 문항 타입별 입력
분기는 `questionId`가 아니라 **`code`/`answerType`/`valueCode`**로 한다 (ID는 환경마다 다름).

| answerType | 입력 | 제출 형식 |
|---|---|---|
| SINGLE | 선택지 하나 | `{ questionId, optionIds: [id] }` |
| MULTI | 여러 개. Q-CONDITIONS에서 `NONE`(없음)을 고르면 다른 선택 해제, 다른 걸 고르면 `NONE` 해제 | `{ questionId, optionIds: [...] }` |
| NUMBER | 숫자 (Q-BIRTH는 1900~올해) | `{ questionId, valueNumber }` |
| TEXT · Q-MEDICATIONS | 검색창(입력 300ms 디바운스 → `/drugs?keyword=`) + 선택한 약 칩 목록. 검색 결과에 없으면 "직접 입력"으로 이름 추가. **"혈압약 (이름 모름)" 같은 항목도 검색 결과로 나오니**, 입력창 아래에 "약 이름을 모르면 '혈압약'처럼 종류로 검색해 보세요" 안내 | `valueText: JSON.stringify([{drugId}, {name}])` — 한 항목에 둘 중 하나만 |
| TEXT · Q-SUPPLEMENTS | 성분 선택(`/ingredients`) + 하루 섭취량 숫자 + 단위(성분 목록의 unit 고정 표시). 여러 개 추가·삭제 | `valueText: JSON.stringify([{ingredient, amount, unit}])` |

## 6. 결과 화면 (ResultStep)
- 상단: `summary`
- `fallback === true`면 상단에 `notice`를 눈에 띄게 표시 ("AI 맞춤 추천을 잠시 사용할 수 없어 기본 안내를 보여드려요")
- **추천 성분 카드** (`items`, `sortOrder` 순)
  - 성분 이름 (`/ingredients`로 코드 → 이름 변환, 못 찾으면 코드 그대로)
  - 하루 추가 섭취량 `amount` + `unit`. 없으면 `doseNote` 표시 (예: "기준값 미등록")
  - `intakeStatus === "KNOWN"`이면 "현재 섭취량 {currentIntake}{unit} 반영", `UNKNOWN`이면 "현재 섭취량 미반영"
  - `reason`
  - `warnings`가 있으면 카드 안에 경고 박스로 **그 성분에만** 표시 (예: 혈압약 + 칼륨 → 의사·약사 상담 안내)
  - 비활성 버튼 "제품 추천 보기 (준비 중)" — V3에서 활성화할 자리
- **제외된 성분** (`excludedItems`가 있을 때만): 접을 수 있는 영역 "안전을 위해 제외한 성분", 성분 이름 + `warnings`(제외 이유)
- 하단 고정 문구:
  - "설문과 건강 프로필에 입력한 약을 기준으로 확인했어요."
  - "이 결과는 의학적 진단이 아니에요. 복용 전 의사·약사와 상담하세요."
- 버튼: "다시 설문하기", "닫기"

## 7. 예외·엣지 케이스 (전부 확인)
- 비회원: 민감 문항이 안 내려오고, 동의 단계 없음
- 회원 동의 거부: 민감 문항 없이 제출 가능
- 서버 꺼짐/네트워크 오류: 에러 화면 + 재시도
- 오류 코드별 메시지는 서버 `message`를 그대로 보여준다 (`S002` 답변 오류, `S003` 비회원 민감 문항, `S004` 동의 없음, `R003` AI 실패)
- `items`가 빈 배열: "지금 추천할 성분이 없어요" + 제외 성분이 있으면 그 목록
- 폴링 90초 초과: 에러 화면
- 로딩 중 닫기: 폴링 타이머 정리 (언마운트 시 `clearTimeout`)
- 중복 제출 방지: 제출 중 버튼 비활성화

## 8. 이번 작업에서 하지 않는 것
- 백엔드 수정, 알림 진입(`/users/me/recommendations/latest`) 화면 연결, 제품 추천, 로그인 연동, 스타일 전면 개편

## 9. 완료 기준
- `cd frontend && pnpm build` 성공 (터미널: 로컬 PC의 frontend 폴더)
- 로컬 백엔드(Fake LLM 또는 local-llm 프로필) + `pnpm dev`로 아래를 직접 확인
  1. 비회원 설문 → 결과
  2. 회원 동의 → 복용 약 "혈압약 (이름 모름)" + 영양제 입력 → 결과에서 칼륨 카드에 상담 경고
  3. 제외 성분이 있는 케이스 (예: 흡연 → 베타카로틴 제외)
  4. 백엔드 끈 상태에서 에러 화면
- 기존 홈·친구·마이페이지·폐의약품 화면 이동이 깨지지 않음

## 10. 작업 후 보고
- 변경 파일 목록, 구현 내용, 테스트 결과, `App.jsx` 변경 내용, 협의가 필요한 점(채원: 기존 `SurveyModal` 교체 여부)
- `docs/logs/YYYY-MM-DD.md` 맨 아래에 기록 추가 (기존 내용 수정 금지)
