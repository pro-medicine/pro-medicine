# Claude Code 작업 지시 — V7 마이페이지 "최근 추천 제품 보기"

- 시작 위치: 원본 레포 `C:\Projects\pro-medicine` (지금 브랜치 `feat/v1-onboarding-survey`, V1 리팩토링이 커밋 전 상태로 진행 중)
- **작업 위치: 이 프롬프트를 받은 Claude Code가 직접 만드는 worktree `C:\Projects\pro-medicine-v7`**, 브랜치 `feat/v7-recent-recommendation`
- 기준 커밋: **`c1a826c`** (`feat/v1-onboarding-survey`의 끝 = develop과 같음, fix: 페이지 제목 약쏙해줘로 변경 … #116)
- 원본 폴더의 브랜치·파일은 **절대 건드리지 않는다.** (V1 작업이 진행 중)
- 이 파일 위치: 원본 `docs/prompts/V7-recent-recommendation-prompt.md` (커밋 안 된 파일) → worktree로 복사해서 커밋한다 (1장 3번)
- 작성: 2026-11-02 · 수정: 2026-11-02 (V1 리팩토링과 worktree로 동시 진행, Claude Code가 worktree를 직접 만들어 바로 시작)

## 목표

회원이 마이페이지 맨 아래에서 **가장 최근에 받은 추천 1건**(성분 여러 개 + 성분별 제품 여러 개)을 볼 수 있게 한다.

- 대상 추천: V1 회원 설문 추천, V2 맞춤 질문 정밀 추천, V4 대화창 추천(track 1·track 2)
- 이 중 **가장 최근 1건만** 보여준다. 목록·이전 기록 보기는 하지 않는다.
- 화면: 마이페이지 맨 아래에 작은 안내 문구 **"최근에 추천받은 제품이에요"** + **[추천 제품 보기]** 버튼 → 누르면 **모달(창)** 로 성분 카드와 제품을 보여준다.

## 절대 조건

1. **LLM·임베딩 호출 0회** — 저장된 결과를 읽기만 한다. 새 추천을 만들지 않는다.
2. **본인 것만** — 로그인한 회원 본인의 추천만. 비회원은 401.
3. **기존 동작 유지** — 기존 API(`/users/me/recommendations/latest` 포함)의 URL·응답 형식·동작을 바꾸지 않는다.
4. **V1 리팩토링과 겹치지 않기** — V1이 고치는 파일(2-1 "V1과 겹치는 파일")은 수정하지 않는다. 나중에 develop 위로 rebase할 때 충돌이 없게 한다.
5. **수정 범위 최소** — 새 파일 위주. 다른 담당자 폴더(`src/mypage/`)는 끼워 넣는 몇 줄만.
6. **안전 판단 유지** — 제품을 보여줄 때 V3·V4에서 쓰는 안전 필터를 그대로 거친다. 필터를 건너뛰는 지름길을 만들지 않는다.

---

## 0. 진행 방식 — 멈춤은 한 번

| 단계 | 내용 | 코드 수정 | 끝나면 |
|---|---|---|---|
| 준비 | worktree 생성·기준 테스트 (1장) | ❌ | 멈추지 않고 1단계로 |
| 1 | 코드 확인 → **확인 결과 + 구현 계획** 출력 | ❌ | **멈추고 승인 대기 (유일한 멈춤)** |
| 2 | 백엔드 구현 + 테스트 | ✅ | 멈추지 않고 3단계로 |
| 3 | 프론트 구현 + 빌드 | ✅ | 멈추지 않고 4단계로 |
| 4 | 결과 보고서 작성·커밋 | — | 끝 |

- 승인 뒤에는 질문하지 않는다. 막히면 아래처럼 처리하고 보고서에 적는다.
  - 2-1 목록 밖 파일을 고쳐야 함 → 고치지 않고, 대안(새 파일로 우회)이 있으면 그렇게, 없으면 그 부분만 빼고 보고서에 적는다.
  - 기존 테스트가 새로 깨짐 → 원인이 내 변경이면 고치고, 고칠 수 없으면 그 변경을 되돌린다.

---

## 1. 준비 — worktree를 직접 만들고 바로 시작

> Claude Code는 원본 폴더 `C:\Projects\pro-medicine`에서 시작한다. 1~2번은 원본 폴더에서, 3번부터는 **전부 worktree 폴더**에서 한다.
> worktree는 시작 폴더 밖에 있으므로 **모든 명령·파일 경로를 절대 경로로 쓴다** (예: `cd C:/Projects/pro-medicine-v7/backend && ./gradlew test`, Read/Edit/Write도 `C:/Projects/pro-medicine-v7/...`). 원본 폴더 경로(`C:/Projects/pro-medicine/...`)의 파일을 수정하지 않았는지 단계마다 확인한다.

1. 원본 폴더에서 상태만 확인한다: `git status`, `git branch --show-current`(= `feat/v1-onboarding-survey`), `git cat-file -t c1a826c`(= commit).
   - V1 작업 중인 수정 파일이 보이는 게 정상이다.
   - **checkout·switch·stash·pull·reset·add·commit 등 원본 상태를 바꾸는 명령은 쓰지 않는다.**
   - `C:\Projects\pro-medicine-v7` 폴더나 `feat/v7-recent-recommendation` 브랜치가 이미 있으면 새로 만들지 말고 멈추고 보고한다.
2. worktree 생성 (원본 폴더에서 실행):
   ```bash
   git worktree add ../pro-medicine-v7 -b feat/v7-recent-recommendation c1a826c
   ```
   - worktree는 커밋만 가져가므로 V1의 커밋 전 변경은 들어오지 않는다. 그게 맞다.
   - `git worktree list`로 생성 결과를 확인한다.
3. 이 프롬프트 파일을 worktree로 **복사**한다 (원본 파일은 그대로 둔다):
   `C:/Projects/pro-medicine/docs/prompts/V7-recent-recommendation-prompt.md` → `C:/Projects/pro-medicine-v7/docs/prompts/V7-recent-recommendation-prompt.md`
   - 원본 폴더에만 있고 git에 안 올라간 로컬 설정 파일(`.env`, `application-local*.yml` 등)이 테스트·빌드에 필요하면 이것도 **복사만** 한다. 키 값은 출력하지 않는다. 무엇을 복사했는지 보고서에 적는다.
4. 이후 모든 작업은 `C:/Projects/pro-medicine-v7`에서 한다.
5. 읽을 문서 (worktree 기준): `CLAUDE.md`(끝까지), `docs/prompts/V2-personalized-recommendation.md`, `docs/prompts/V4-chat-symptom-recommendation-prompt.md`, `docs/prompts/V5-llm-security.md`, `docs/prompts/V6-refactoring-result.md`. V1 지시문은 원본 폴더의 `docs/prompts/V1-onboarding-refactor-prompt.md`에 있다 (worktree에는 없음, **읽기만**).
6. 기준 테스트: worktree의 `backend` 폴더에서 `./gradlew test` → 통과/실패/건너뜀 수와 실패 클래스 기록. 이후 비교는 "기준 실패 외 새 실패 0".
7. 프론트: worktree의 `frontend` 폴더에서 **`pnpm install` → `pnpm build`** (CLAUDE.md 기준 pnpm, worktree는 `node_modules`가 따로라 설치가 필요하다). 결과 기록. `pnpm-lock.yaml`이 바뀌면 커밋하지 않는다.
8. 서버를 띄워야 하면 원본 폴더 쪽 서버와 포트가 겹치지 않게 다른 포트를 쓴다. 테스트만 할 거면 띄우지 않는다.
9. 준비가 끝나면 멈추지 않고 바로 3장(1단계)으로 간다.

---

## 2. 규칙

### 2-1. 수정 범위
- **새로 만드는 파일** (위치는 1단계에서 실제 구조를 보고 확정)
  - 백엔드: `domain/recommendation/` 아래 새 컨트롤러(또는 기존 `MyRecommendationController`에 메서드 추가), 새 서비스(`RecentRecommendationService` 등), 새 DTO
  - 프론트 (`frontend/src/recommendation/` 아래 **새 파일만**)
    - `RecentRecommendationSection.jsx` — 마이페이지에 들어가는 작은 섹션(문구 + 버튼)
    - `RecentRecommendationModal.jsx` — 모달
    - `recentRecommendation.css` — **V7 스타일은 전부 이 새 파일에.** 컴포넌트에서 직접 import
  - 테스트: 위에 대응하는 `backend/src/test/**`
  - `docs/prompts/V7-*`, `docs/logs/*`(맨 아래 추가만)
- **기존 파일 중 고쳐도 되는 것**
  - `AiRecommendationRepository`, `AiChatResultRepository` — **쿼리 메서드 추가만** (기존 메서드 수정 금지)
  - `MyRecommendationController` — 메서드 추가만 (새 컨트롤러로 해도 됨)
  - `domain/review`의 제품 추천 서비스 — 이미 public인 메서드 호출만. 고쳐야 하면 1단계 계획에 올린다
  - `frontend/src/api/recommendationApi.js` — 맨 아래에 함수 추가만
  - `frontend/src/mypage/MyPage.jsx` — **import 1줄 + 컴포넌트 끼워 넣기 몇 줄만** (주연 담당 파일. 다른 줄은 손대지 않는다). import는 `../recommendation/RecentRecommendationSection` 경로로 직접 한다
- **V1과 겹치는 파일 — 수정 금지** (V1이 지금 고치고 있거나 고칠 예정)
  - `frontend/src/recommendation/index.js`, `frontend/src/recommendation/recommendation.css`
  - `frontend/src/landing/**`, `GuestSurveySection.jsx`
  - `backend/docs/sql/AI_SETUP*.sql`, `backend/src/test/resources/sql/seed-survey-v1.sql`
  - `backend/docs/api/survey-api.md`, `backend/docs/supplement-recommendation/02_survey_api.md`
  - 설문 순서를 검증하는 테스트
- **절대 수정하지 않는 것**: 원본 폴더 `C:\Projects\pro-medicine`의 모든 파일, 다른 도메인 코드, `global/**`, `SecurityConfig`, `application*.yml`, `schema.sql`(DB 구조 변경 없음), Nginx·docker-compose, `.github/**`, `build.gradle`, `package.json`·`pnpm-lock.yaml`(새 라이브러리 없음)

### 2-2. 그 외
- **push 금지.** 커밋까지만 (worktree에서). 커밋 메시지는 레포 방식(`[FEAT]`, `[TEST]`, `[DOCS]`).
- 테스트는 Fake LLM/Embedding으로. 실호출 금지. 팀 DB(`yakssok`)는 건드리지 않는다.
- `CLAUDE.md` 규칙대로 worktree의 `docs/logs/YYYY-MM-DD.md` 맨 아래에 작업 블록 추가. V1도 같은 날짜 파일에 추가할 수 있으니, rebase 때 충돌이 나면 **두 블록 모두 남기는** 방식으로 푼다 (보고서에 안내).

---

## 3. 1단계 — 코드 확인 + 구현 계획 (코드 수정 없음)

### 3-1. 미리 확인된 사실 (2026-11-02, e798dc7 기준 — c1a826c에서 다시 확인할 것)
- 기존 `GET /api/v1/users/me/recommendations/latest`(`MyRecommendationController` → `RecommendationReuseService` → `RecommendationStore.latestDoneForUser`)는 `AiRecommendationRepository.findLatestDoneByUserId`를 쓰는데, 이 쿼리는 **`join r.response resp where resp.user.id = :userId`** 라서 **V1 설문 추천만** 잡는다. V2(`session_id`)·V4 track 2(`chat_session_id`) 추천은 `response`가 NULL이라 빠진다. 또 프로필이 바뀌면 `reusable=false`가 되는 등 "알림 재사용" 용도의 판단이 섞여 있다.
  → **이 API는 그대로 두고 새 API를 만든다.**
- V4 track 1(`REVIEW`) 결과는 `ai_recommendations`가 아니라 **`ai_chat_results.cards`(JSON 스냅샷)** 에 성분·제품이 같이 저장된다. track 2(`RULE_BASED`)는 `ai_chat_results.recommendation_id`로 `ai_recommendations` 행을 가리킨다.
- 마이페이지는 `frontend/src/mypage/MyPage.jsx`의 `ProfileScreen`. 맨 아래는 `profile-columns` div(영양관리 + 내가 먹는 영양제) 다음, 바깥 `</div>` 직전이다. `isMine`일 때만 보여야 한다. (c1a826c 전에 #111 영양제 등록 모달 변경이 들어왔으니 위치를 다시 확인)

### 3-2. 확인할 것 (파일:줄 근거와 함께)
1. 세 경로에서 "회원 id"를 어떻게 찾는지: `response.user`, `session.user`(`ai_question_sessions.user_id`), `chatSession.user`(`ai_chat_sessions.user_id`). 비회원 V1(guest)은 회원 id가 없으니 제외되는지.
2. "최근"의 기준 시각: `ai_recommendations`는 `created_at`이 PENDING 시점인지, 완료 시각 컬럼이 있는지 / `ai_chat_results.created_at`. 서로 비교할 시각을 하나로 정한다 (완료 시각이 있으면 완료 시각 권장).
3. 상태: `ai_recommendations`는 `DONE`만. PENDING·FAILED·RULE_ONLY 폴백 결과를 어떻게 다룰지 (RULE_ONLY도 DONE으로 저장된 정상 결과면 포함).
4. 중복: V4 track 2는 `ai_chat_results`(RULE_BASED)와 `ai_recommendations` 둘 다에 흔적이 있다 → **`ai_recommendations` 쪽 한 번만** 세고, `ai_chat_results`에서는 `track = 'REVIEW'`만 읽는다.
5. 제품을 붙이는 방법
   - `ai_recommendations` 기반(V1·V2·V4 track 2): 지금 화면이 쓰는 V3 제품 API(`/recommendations/{id}/products?ingredient=`, `availability`)가 내부에서 부르는 서비스 메서드와, 그 안의 소유권 확인·회원 전용 제한·`RecommendationConditionLoader`(설문/V2/대화 분기)·`ProductSafetyFilter` 흐름
   - V4 track 1: `cards` JSON에 이미 제품이 있는지, 형식이 V3 `ReviewProductResponse.Product`와 같은지
6. 90일 삭제(V2 회차·V4 대화 세션) 뒤 남는 데이터: V4 track 1은 `ai_chat_results`까지 지워져 기록이 사라지는지, V2는 추천은 남고 `session_id`만 NULL이 되는지 → **`session_id`가 NULL이 된 V2 추천은 회원을 찾을 수 없어 안 보이게 되는지** 확인하고 계획에 적는다 (회원 id를 찾을 다른 경로가 있는지 포함).
7. 프론트: 결과 모달에서 재사용할 수 있는 컴포넌트 — `IngredientCard.jsx`, `ProductRecommendations.jsx`, `ResultStep.jsx`의 카드·제품 부분, V4 `ChatMessage.jsx`의 track 1 카드 표시. props 형태와 그대로 쓸 수 있는지. **재사용 컴포넌트는 고치지 않고 import만** 한다 (`index.js`를 거치지 않고 파일 경로로).
8. 모달 패턴: `RecommendationModal.jsx`·`PersonalizedModal.jsx`·MyPage의 `SettingsModal`이 쓰는 방식(포커스, ESC 닫기, 배경 클릭)을 확인해 같은 방식으로.
9. 2-1 "V1과 겹치는 파일" 목록이 맞는지: 원본 폴더의 V1 지시문과 원본 폴더 `git status`(읽기만)를 보고 빠진 파일이 있으면 계획에 추가한다.

### 3-3. 구현 계획 출력 (터미널에 출력, 파일로 만들지 않아도 됨)
- worktree 생성 결과 (경로, 브랜치, 시작 커밋), 복사한 파일, 기준 테스트·빌드 결과
- 3-2 확인 결과 요약 (예상과 다른 점 강조)
- API 설계안 (아래 4-1 초안 기준, 바꾼 점과 이유)
- 제품 붙이는 방식 결정: **(A) 서버가 한 응답에 제품까지 담기** vs **(B) 서버는 성분 + 추천 id만, 프론트가 기존 V3 제품 API 재사용**. 장단점과 추천안 (기본 권장은 아래 4-2)
- 수정·추가 파일 목록 (2-1 범위 안인지, V1과 겹치지 않는지 표시)
- 테스트 시나리오 목록

**출력 후 멈추고 "승인" 또는 수정 사항을 기다린다.**

---

## 4. 2단계 — 백엔드

### 4-1. API (초안)
`GET /api/v1/users/me/recommendations/recent`

- 인증: `@CurrentUserId`, 없으면 401 (기존 `UNAUTHORIZED` 코드 재사용)
- 응답 (`ApiResponse` 감싸기):
```json
{
  "exists": true,
  "source": "V2_PERSONALIZED",
  "recommendedAt": "2026-11-01T21:03:11",
  "recommendationId": 345,
  "chatSessionId": null,
  "ingredients": [
    {
      "ingredient": "MAGNESIUM",
      "name": "마그네슘",
      "warnings": [],
      "products": [
        { "catalogProductId": 12, "productName": "...", "brand": "NOW",
          "imageUrl": null, "alsoContains": [], "cautions": [], "warnings": [] }
      ]
    }
  ]
}
```
- `source`: `V1_SURVEY` / `V2_PERSONALIZED` / `V4_CHAT`
- 기록이 없으면 `{ "exists": false }` (200, 오류 아님)
- 성분 카드 항목·제품 항목 필드는 **기존 DTO(V1 카드, V3 `ReviewProductResponse.Product`)를 그대로 재사용**한다. 새 필드 이름을 만들지 않는다. 위 JSON은 모양만 보여주는 예시.
- 제외 성분(`excludedItems`)은 이 화면에서 보여주지 않는다. 용량·이유 문구는 기존 카드 DTO에 있으면 그대로 내려도 된다 (프론트가 고를 수 있게).
- 성분은 원래 추천 순서 그대로.

### 4-2. 제품 붙이는 방식 (기본 권장: A, 1단계에서 확인 후 확정)
- **V1·V2·V4 track 2**: 추천의 성분마다 V3 제품 추천 서비스 메서드를 서버에서 호출해 붙인다. 이때 **V3 API가 하는 소유권 확인·회원 전용 제한·`RecommendationConditionLoader`·`ProductSafetyFilter`를 그대로 거치는 메서드**를 쓴다. 필터를 우회하는 새 쿼리를 만들지 않는다.
  - 제품은 **지금 데이터 기준으로 다시 계산**된다 (추천 받던 때와 리뷰 데이터가 달라졌으면 결과도 다를 수 있음). 이 점을 보고서에 적는다.
  - 제품이 0개인 성분도 카드는 보여준다 (`products: []`) — 프론트에서 "아직 추천할 제품이 없어요".
- **V4 track 1**: `ai_chat_results.cards` 스냅샷을 그대로 읽어 같은 형식으로 바꾼다 (다시 계산하지 않음 — V4 "다시 열었을 때 같은 결과" 원칙).
- 성분 수만큼 V3 서비스를 부르면 DB 조회가 늘어난다 → 요청당 쿼리 수를 측정해 보고서에 적는다. 성분 수가 적어(최대 몇 개인지 확인) 일괄 조회 최적화는 하지 않아도 된다. 필요하면 "후속 개선"에만 적는다.

### 4-3. 최근 1건 고르기
- 후보 ① `ai_recommendations`: `status = DONE` 이고 회원 id가 `response.user` / `session.user` / `chatSession.user` 중 하나로 일치하는 것 중 최신 1건
- 후보 ② `ai_chat_results`: `track = 'REVIEW'` 이고 `chatSession.user`가 일치하는 것 중 최신 1건
- ①②를 3-2에서 정한 시각으로 비교해 더 최근 것. 같으면 `ai_recommendations` 쪽 (id 큰 쪽).
- 쿼리는 리포지토리에 **새 메서드로 추가** (기존 `findLatestDoneByUserId` 수정 금지). `Pageable` 1건 방식은 기존과 같게.
- LLM·임베딩·`LlmGuard`를 거치지 않는다 (LLM 호출이 없으므로 V5 가드 대상 아님). 다만 기존 컨트롤러들처럼 본인 확인은 반드시.

### 4-4. 테스트
- `RecentRecommendationServiceTest` / API 테스트 (Fake LLM·Embedding 주입, **호출 0회 검증**)
  1. 기록 없음 → `exists: false`
  2. V1 회원 추천만 → `V1_SURVEY`, 성분·제품
  3. V1 다음 V2 → V2가 나옴
  4. V2 다음 V4 track 1 → V4, `cards` 스냅샷 그대로
  5. V4 track 2 → `ai_recommendations` 기반 1건만 (중복 없음, `source = V4_CHAT`)
  6. 최신 추천이 PENDING·FAILED → 그 전 DONE 추천이 나옴
  7. 다른 회원의 추천이 더 최근 → 내 것만 나옴
  8. 비회원(헤더·토큰 없음) → 401
  9. 대화에서만 와파린을 말한 V4 track 2 → 오메가3 제품이 안전 필터로 빠지는지 (V3 API와 같은 결과)
  10. 90일 삭제 뒤 상태 (3-2 6번 확인 결과대로 기대값 작성)
- 테스트 시드가 필요하면 **새 시드 파일**을 만든다 (`seed-survey-v1.sql` 등 V1이 고치는 시드는 수정 금지).
- 기존 테스트 전체: 기준 실패 외 새 실패 0

---

## 5. 3단계 — 프론트

### 5-1. 마이페이지 끼워 넣기 (`MyPage.jsx`, 최소 수정)
- `profile-columns` div 다음, 바깥 `</div>` 직전에 **`isMine`일 때만** `<RecentRecommendationSection />`을 넣는다. 친구 프로필 화면에는 안 보인다.
- `MyPage.jsx`에서 바꾸는 건 import 1줄 + 끼워 넣는 몇 줄뿐. 상태·로직은 전부 새 컴포넌트 안에 둔다.

### 5-2. 섹션 (`RecentRecommendationSection.jsx`, 작게)
- 문구: 작은 글씨로 **"최근에 추천받은 제품이에요"**, 버튼 **[추천 제품 보기]**
- 화면을 열 때 API를 미리 부르지 않는다. **버튼을 누를 때** 부른다 (마이페이지 로딩에 영향 없게).
- 기존 마이페이지 디자인(`btn btn-soft btn-sm` 등 기존 클래스)을 가져다 쓴다. 새 색·폰트를 만들지 않는다. 추가 스타일은 `recentRecommendation.css`에만, 클래스 이름은 `recent-rec-` 접두어로 다른 화면과 겹치지 않게.

### 5-3. 모달 (`RecentRecommendationModal.jsx`)
- 기존 모달과 같은 방식 (ESC·배경 클릭·닫기 버튼, 포커스 처리)
- 상단: "최근에 추천받은 제품이에요" + 작은 글씨로 추천 받은 날짜와 경로 (예: "11월 1일 · 맞춤 질문 추천", "대화창 추천")
- 본문: 성분 카드 → 카드 안에 제품 목록. 기존 `IngredientCard`·`ProductRecommendations`(목록 부분)를 재사용하고, 재사용이 안 되면 같은 CSS 클래스로 맞춘다.
- 제품 0개 성분: "아직 추천할 제품이 없어요"
- 기록 없음: "아직 추천받은 기록이 없어요" + (가능하면) 추천 받으러 가기 안내 문구만 (다른 화면으로 이동 기능은 만들지 않는다)
- 로딩: 기존 대기 표시 재사용. 오류: "추천 기록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요"
- 모바일 폭(360px)에서 모달 안 스크롤 확인.

### 5-4. API 함수
- `recommendationApi.js` 맨 아래에 `getRecentRecommendation()` 추가 (기존 함수 형식 그대로)

### 5-5. 확인
- `pnpm build` 통과
- 가능하면 로컬에서 화면 확인 (Fake 데이터 또는 테스트 회원): 기록 없음 / V1 / V2 / V4 track 1 각각 모달 모양. 확인한 경우만 보고서에 적는다.

---

## 6. 4단계 — 결과 보고서 (worktree `docs/prompts/V7-recent-recommendation-result.md`, 커밋)

- 한 줄 요약: 완료 여부, 기준 대비 새 실패 0 여부, 프론트 빌드 결과
- worktree 경로, 브랜치, 시작 커밋(`c1a826c`), 커밋 목록 (해시·제목)
- 확정한 API 명세 (실제 응답 예시 1~2개)
- 최근 1건 고르는 기준 (시각 컬럼, 상태, 중복 처리)
- 제품 붙이는 방식과 "지금 데이터로 다시 계산됨" 같은 동작 특징
- 요청당 DB 쿼리 수, LLM·임베딩 호출 0회 확인
- 테스트 결과 (4-4 시나리오별 통과 여부)
- 수정 파일 목록 (`git diff --name-only c1a826c..HEAD`) — 2-1 범위 밖 0개, **V1과 겹치는 파일 0개**인지
- 원본 폴더 상태 확인: 작업 전후 원본 폴더 `git status`가 같은지 (V1 작업 파일을 건드리지 않았는지)
- **`MyPage.jsx` 변경 부분 diff** (주연에게 PR에서 알릴 내용)
- worktree에 복사한 파일 목록
- 후속 개선 / 확인 필요 (예: 90일 삭제 뒤 V2 추천이 안 보이는 문제, 성분별 제품 일괄 조회)
- 머지 순서 안내 (push는 하지 않았음)
  1. V1(`feat/v1-onboarding-survey`) PR이 develop에 머지된 뒤
  2. worktree 터미널(`C:\Projects\pro-medicine-v7`)에서 `git fetch && git rebase origin/develop` → 충돌이 나면 `docs/logs`는 두 블록 모두 남김
  3. 테스트·빌드 다시 확인 후 V7 PR
  4. 끝나면 원본 폴더 터미널(`C:\Projects\pro-medicine`)에서 `git worktree remove ../pro-medicine-v7`
