# V7 마이페이지 "최근 추천 제품 보기" — 결과 보고서

- 작성: 2026-11-02 · 지시문: `docs/prompts/V7-recent-recommendation-prompt.md`
- **요약: 완료. 기준 대비 새 실패 0 (아래 1건은 타이밍 테스트로 단독 재실행 3/3 통과), 새 테스트 12개 모두 통과, `pnpm build` 통과.**

## 1. 작업 위치·커밋

- worktree: `C:\Projects\pro-medicine-v7` · 브랜치 `feat/v7-recent-recommendation` · 시작 커밋 `c1a826c`
- 커밋 (push 안 함)

| 해시 | 제목 |
|---|---|
| d29bdcf | [FEAT] V7 마이페이지 최근 추천 1건 조회 API (GET /users/me/recommendations/recent) |
| 35e4737 | [TEST] V7 최근 추천 API 시나리오·쿼리 수 측정 테스트 |
| dddd957 | [FEAT] V7 마이페이지 최근 추천 제품 보기 섹션·모달 |
| (이 문서) | [DOCS] V7 지시문·결과 보고서·작업 로그 |

- worktree에 복사한 파일: `docs/prompts/V7-recent-recommendation-prompt.md` 1개뿐. 테스트는 `src/test/resources/application.yml`을 써서 `.env`·`application.yml`은 복사하지 않았다.
- `pnpm install`로 생긴 `frontend/pnpm-lock.yaml`(git 미추적)은 커밋하지 않았다.

## 2. API 명세

`GET /api/v1/users/me/recommendations/recent` (회원 전용, 토큰 없으면 401 `E003`)

```json
{ "success": true, "data": {
  "exists": true,
  "source": "V2_PERSONALIZED",
  "recommendedAt": "2026-10-01T10:00:00",
  "recommendationId": 345,
  "notice": "입력하신 약·질환 기준으로 주의 성분이 확인되지 않은 제품이에요. 복용 전 의사·약사와 상담하세요.",
  "ingredients": [
    { "ingredient": "OMEGA3", "reason": "...", "sortOrder": 1, "warnings": [],
      "products": [ { "catalogProductId": 9315, "productName": "Ultra Omega-3", "brand": "Nordic",
                      "imageUrl": null, "alsoContains": [], "cautions": [], "warnings": [] } ] }
  ]
}}
```

- 기록이 없으면 `{ "exists": false }` (200)
- `source`: `V1_SURVEY` / `V2_PERSONALIZED` / `V4_CHAT`. `chatSessionId`는 V4일 때만, `recommendationId`는 V4 트랙 1이면 없음
- 성분 카드는 **기존 `ChatCard`**(V1 `RecommendationResponse.Item` 필드 + V3 `ReviewProductResponse.Product` 목록)를 그대로 썼다. 새 필드 이름 없음
- 초안과 다른 점: 성분 한글 이름(`name`)은 내려주지 않는다. 프론트가 기존 `GET /ingredients`로 매핑한다 (`ResultStep`·`ChatMessage`와 같은 방식). `notice`(V3 안내 문구)를 추가했다
- 제외 성분(`excludedItems`)은 내보내지 않는다. 성분 순서는 원래 추천 순서

## 3. 최근 1건 고르는 기준

| 후보 | 조건 | 비교 시각 |
|---|---|---|
| ① `ai_recommendations` | `status = 'DONE'`, 회원 = `response.user` / `session.user` / `chatSession.user` 중 하나 | `coalesce(finished_at, created_at)` (트랙 2는 `created_at`이 PENDING 시점이라 완료 시각 사용) |
| ② `ai_chat_results` | `track = 'REVIEW'`, 회원 = `chatSession.user` | `created_at` (트랙 1 결과는 완료 때 저장) |

- 더 최근 것 1건, 같으면 ①. 트랙 2는 ①에서만 센다 (`ai_chat_results`의 RULE_BASED는 읽지 않음 → 중복 없음)
- 비회원 V1(`response.user = NULL`)·PENDING·FAILED는 빠진다. 규칙 기반 폴백도 DONE이면 포함
- 쿼리는 리포지토리 새 메서드 2개(`findRecentDoneByMember`, `findRecentReviewByUserId`), `Pageable` 1건. 기존 `findLatestDoneByUserId`·`/latest`는 그대로

## 4. 제품 붙이는 방식 (A: 서버가 한 응답에)

- V1·V2·V4 트랙 2: `RecommendationAccess.requireOwned`(V3 API와 같은 본인 확인) → 성분마다 `ReviewProductRecommendationService.recommend(recId, code)` 호출.
  `RecommendationConditionLoader`(설문/V2/대화 분기, 대화에서만 말한 약 포함)와 `ProductSafetyFilter`를 그대로 거친다. 필터를 우회하는 쿼리 없음. 리뷰 서비스 코드는 수정하지 않았다.
  - **제품은 지금 데이터 기준으로 다시 계산된다.** 추천 받은 뒤 리뷰 점수·프로필(약·질환)이 바뀌면 결과도 달라질 수 있다 (V3 버튼과 같은 동작)
  - 성분 하나의 제품 조회가 예외를 던지면 그 성분만 `products: []` + `WARN` 로그(`recent recommendation products failed: recommendationId=…, ingredient=…`), 응답은 200
  - 서비스에 트랜잭션을 걸지 않았다: 한 성분 조회의 예외가 바깥 트랜잭션을 rollback-only로 만들어 응답 전체가 실패하지 않게 하려는 것. 엔티티는 FK 유무·id만 읽는다
- V4 트랙 1: `ai_chat_results.cards` 스냅샷을 그대로 돌려준다 (다시 계산하지 않음, V4 "다시 열면 같은 결과")
- 제품 0개 성분도 카드는 나온다 (`products: []` → 화면 "아직 추천할 제품이 없어요")

## 5. 요청당 DB 쿼리 수 · LLM 호출

`RecentRecommendationQueryCountTest` (Hibernate 통계의 준비 SQL 수 + `ReviewProductQueryRepository` 호출 수):

| 경우 | 쿼리 수 |
|---|---|
| V2 추천, 성분 1개 | 10 |
| V2 추천, 성분 3개 | 20 |
| V4 트랙 1 스냅샷 | 2 (후보 ①·② 조회만) |

- 성분 1개당 약 5개씩 늘어난다 (`conditionLoader.load`가 성분마다 반복: 추천 행·항목·프로필 조회 + 후보·라벨 조회). 코드상 성분 수 상한은 없지만 보통 몇 개라 이번에는 최적화하지 않았다 (후속 개선 2)
- **LLM·임베딩 호출 0회**: 모든 시나리오 테스트에서 `/recent` 호출 전에 Fake LLM·Embedding 기록을 비우고, 호출 뒤 요청이 0건인지 확인했다. `LlmGuard`를 거치지 않는다 (LLM 호출이 없어서)

## 6. 테스트 결과

- 기준 (`c1a826c`): 664개, 실패 10, 건너뜀 3 — `IntakeScheduleRepositoryTest` 6, `IntakeSummaryControllerTest` 1, `NotificationCurrentUserTest` 1, `NotificationSubscribeControllerTest` 2 (모두 다른 도메인)
- 작업 후: 676개(+12), 실패 11, 건너뜀 3 — 기준 10개 + `QuestionHedgeTest` 1개
  - `QuestionHedgeTest`(V2 질문 hedge, `Thread.sleep` 지연 기반)는 전체 실행 때만 1번 실패, **단독 재실행 3회 모두 통과**. 이번 변경과 관계없는 코드(personalized)라 타이밍 문제로 판단 → 새 실패 0

| # | 시나리오 (`RecentRecommendationApiTest`) | 결과 |
|---|---|---|
| 1 | 기록 없음 → `exists: false` | 통과 |
| 2 | V1 회원 추천 → `V1_SURVEY`, 성분 순서, 제품 = V3 API 결과 | 통과 |
| 3 | V1 다음 V2 → V2, 기존 `/latest`는 그대로 V1 | 통과 |
| 4 | V2 다음 V4 트랙 1 → 카드 스냅샷 그대로 (스냅샷의 제품 이름 유지) | 통과 |
| 5 | V4 트랙 2 → `ai_recommendations` 1건만 (더 늦은 RULE_BASED 스냅샷은 무시) | 통과 |
| 6 | 최신이 PENDING·FAILED → 그 전 DONE | 통과 |
| 7 | 다른 회원 추천(V2·트랙 1)이 더 최근 → 내 것만 | 통과 |
| 8 | 토큰 없음 / 게스트 토큰만 → 401 | 통과 |
| 9 | 대화에서만 와파린을 말한 트랙 2 → VITAMIN_D는 9312만, OMEGA3 경고, 둘 다 V3 API 결과와 같음 | 통과 |
| 10 | 90일 삭제 뒤 → V2·트랙 2 추천 행은 남지만 안 보이고, 트랙 1은 지워짐 → 남은 V1이 나옴 | 통과 |
| 추가 | 성분 하나의 제품 조회 예외 → 그 성분만 `products: []`, 다른 성분은 정상, 200 | 통과 |
| 측정 | 요청당 쿼리 수 (`RecentRecommendationQueryCountTest`) | 통과 |

- 시드: 기존 `schema-review-products.sql`·`seed-review-products.sql`을 읽기만 했다. 새 시드 파일은 만들지 않았다 (리포지토리로 직접 생성)
- 예외 시나리오는 성분 코드가 아닌 값이 저장된 추천(V3 서비스가 `INVALID_INPUT`을 던짐)으로 재현했다

## 7. 프론트

- `MyPage.jsx` → `profile-columns` 다음, 바깥 `</div>` 직전에 `isMine`일 때만 `RecentRecommendationSection`
- 섹션: 작은 글씨 "최근에 추천받은 제품이에요" + 기존 `btn btn-soft btn-sm` [추천 제품 보기]. 마이페이지를 열 때는 API를 부르지 않고 **버튼을 누를 때** 부른다
- 모달: 기존 `rec-backdrop`·`rec-modal`(최대 높이 + 내부 스크롤)과 `common/useModalFocus`(ESC·포커스 가두기·연 버튼으로 포커스 복원), 배경 클릭·× 닫기
  - 상단: 제목 + "11월 1일 · 맞춤 질문 추천" (V1 "설문 추천", V4 "대화창 추천")
  - 본문: `IngredientCard` + `ProductRecommendations`(제품 목록을 넘겨 API 추가 호출 없음) 재사용, 제품은 펼친 상태로 시작(접기 가능), 0개면 "아직 추천할 제품이 없어요"
  - 기록 없음: "아직 추천받은 기록이 없어요" + 안내 문구만 (이동 기능 없음) / 오류: "추천 기록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요" / 로딩: 기존 스피너
- 재사용 컴포넌트·`index.js`·`recommendation.css`는 수정하지 않았다 (파일 경로로 직접 import)
- `pnpm build` 통과. ESLint: 새 파일 경고 0 (`MyPage.jsx:202`의 기존 `exhaustive-deps` 경고 1건은 원래 있던 것)
- **로컬 화면 확인은 하지 않았다** (백엔드 + MySQL 서버 기동이 필요). PR 전에 기록 없음 / V1 / V2 / V4 트랙 1 모달 모양과 360px 폭 스크롤을 확인해야 한다

### `MyPage.jsx` 변경 (주연님께 PR에서 알릴 내용)

```diff
 import { getFriendCalendar, getFriendDayIntakes } from "../api/friendApi";
+import RecentRecommendationSection from "../recommendation/RecentRecommendationSection";
@@
         </section>
       </div>
+
+      {/* 최근 추천 제품 보기 (V7, 담당: 영준). 내 화면에서만 */}
+      {isMine && <RecentRecommendationSection />}
     </div>
```

## 8. 변경 파일 (`git diff --name-only c1a826c..HEAD`)

| 파일 | 구분 | 2-1 범위 |
|---|---|---|
| `backend/.../chat/repository/AiChatResultRepository.java` | 쿼리 메서드 추가만 | ✅ |
| `backend/.../recommendation/controller/MyRecommendationController.java` | 메서드 추가만 | ✅ |
| `backend/.../recommendation/dto/RecentRecommendationResponse.java` | 새 파일 | ✅ |
| `backend/.../recommendation/repository/AiRecommendationRepository.java` | 쿼리 메서드 추가만 | ✅ |
| `backend/.../recommendation/service/RecentRecommendationService.java` | 새 파일 | ✅ |
| `backend/src/test/.../recommendation/RecentRecommendationApiTest.java` | 새 파일 | ✅ |
| `backend/src/test/.../recommendation/RecentRecommendationQueryCountTest.java` | 새 파일 | ✅ |
| `frontend/src/api/recommendationApi.js` | 맨 아래 함수 추가 | ✅ |
| `frontend/src/mypage/MyPage.jsx` | import 1줄 + 3줄 | ✅ |
| `frontend/src/recommendation/RecentRecommendation{Section,Modal}.jsx`, `recentRecommendation.css` | 새 파일 | ✅ |
| `docs/prompts/V7-*`, `docs/logs/2026-11-02.md`(맨 아래 추가) | 문서 | ✅ |

- 2-1 범위 밖 0개. **V1과 겹치는 파일 0개** (V1 커밋 `9592a0b`의 8개 파일과 원본 폴더의 V1 진행 파일 `LandingPage.jsx`·`GuestSurveySection.jsx`·`guestSurvey.css` 포함, `docs/logs/2026-11-02.md`만 둘 다 맨 아래 추가)
- 리뷰 서비스(`domain/review`)는 수정하지 않고 public 메서드만 호출했다

## 9. 원본 폴더 상태

- 이 작업은 원본 폴더에서 `git status`·`branch`·`cat-file`·`worktree add/list`와 V1 지시문 읽기만 했다. 원본 파일을 수정하지 않았다
- 작업 중 원본 폴더 상태가 바뀌었는데, **같은 시간에 진행된 V1 세션의 변경**이다 (이 작업과 무관):
  - 시작 때: `feat/v1-onboarding-survey` @ `c1a826c` + V1 커밋 전 수정 6개 + 새 파일
  - 끝날 때: V1이 `9592a0b` 커밋, 이어서 `docs/logs/2026-11-02.md`·`frontend/src/landing/LandingPage.jsx` 수정, `GuestSurveySection.jsx`·`guestSurvey.css` 추가 진행 중

## 10. 후속 개선 / 확인 필요

1. **90일 삭제 뒤 V2·V4 트랙 2 추천이 안 보이는 문제**: 회차·대화 세션이 지워지면 `session_id`·`chat_session_id`가 NULL이 되어 회원을 찾을 경로가 없다 (V3 소유권 확인도 거부). 트랙 1은 `ai_chat_results`까지 지워져 기록 자체가 없다. 그 전 추천(V1 등)이 나오거나 `exists: false`가 된다. 해결하려면 `ai_recommendations.user_id` 같은 컬럼이 필요하다 (DB 구조 변경 → 팀 협의)
2. 성분별 제품 일괄 조회: 지금은 성분마다 조건 로더를 다시 돌린다 (성분 1개당 쿼리 약 5개). 필요하면 리뷰 서비스에 `Loaded`를 받는 메서드를 추가하는 방식 (리뷰 서비스 수정 필요)
3. `QuestionHedgeTest` 타이밍 의존 테스트가 전체 실행 때 가끔 실패함 (V2 담당 확인)
4. 로컬 화면 확인 (7장)

## 11. 머지 순서 (push는 하지 않았음)

1. V1(`feat/v1-onboarding-survey`) PR이 develop에 머지된 뒤
2. worktree 터미널(`C:\Projects\pro-medicine-v7`)에서 `git fetch && git rebase origin/develop` → `docs/logs/2026-11-02.md` 충돌이 나면 **두 블록 모두 남긴다**
3. `./gradlew test`·`pnpm build` 다시 확인 후 V7 PR (PR 본문에 7장의 `MyPage.jsx` diff로 주연님께 알림)
4. 끝나면 원본 폴더 터미널(`C:\Projects\pro-medicine`)에서 `git worktree remove ../pro-medicine-v7`

## 12. rebase·머지 준비 결과 (2026-11-02)

- **새 기준 커밋**: `origin/develop` = `f1d6d9c` ([FEAT] #115 폐의약품 지도 고도화 및 실시간 도보 안내 기능 추가 (#117)). `c1a826c` 이후 develop에 들어온 커밋은 이 1개뿐이고 V1은 아직 머지 전
- **충돌 처리**: 충돌 없음. #117은 `domain/disposal`·`src/disposal` 파일만 바꿔서 V7 파일과 겹치지 않음. `docs/logs/2026-11-02.md`도 develop 쪽 변경이 없어 V7 블록이 그대로 맨 아래에 있음
- **z-index 수정** (`[FIX]`, 화면 확인에서 발견: 모달 배경 위로 AI 상담 패널이 떠 보임)
  - chat 쪽 z-index: 고정 버튼 40, 패널 45(모바일 60). 공통 모달 배경 `.rec-backdrop`은 100, 알림 패널 200, 토스트 210
  - `recentRecommendation.css`에만 `.rec-backdrop:has(> .recent-rec-modal)`(V7 모달을 감싼 배경만)과 `.recent-rec-modal`을 z-index 110으로 지정. 다른 CSS 파일·JSX는 수정하지 않음
  - 참고: 코드상으로는 원래 값(100)도 chat(최대 60)보다 높았고, 모달을 아래로 가두는 쌓임 맥락(z-index·transform이 있는 조상)도 찾지 못했음. 브라우저에서 다시 확인 필요
- **테스트**: `./gradlew test` 676개, 실패 10, 건너뜀 3. 실패는 기준과 같은 intake·notification 10개뿐이라 **새 실패 0**. `QuestionHedgeTest`도 이번엔 통과. V7 테스트 12개 통과
- **빌드**: `pnpm build` 통과
- **변경 파일**: `git diff --name-only origin/develop..HEAD`가 V7 파일 15개뿐 (z-index 수정은 기존 V7 파일 `recentRecommendation.css`라 개수 그대로)
- **화면 확인** (worktree 서버 8090·5180, 로컬 DB를 읽기 전용으로, 사용자 확인): 대화창 track 2 모달 정상
  - 로그인에 필요한 Redis는 Docker 컨테이너 `v7-redis`로 띄움 (원본 8080 로그인에도 쓰여서 남겨 둠). 확인이 끝난 뒤 worktree 서버는 끄고, 복사했던 `backend/.env`·`application.yml`은 지움
