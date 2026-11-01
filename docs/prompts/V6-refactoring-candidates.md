# V6 리팩토링 — 1단계 승인 요청 목록

- 작성: 2026-11-01
- 브랜치: `feat/v6-refactoring` (← `feat/v5-llm-security`)
- 기준 테스트: 646 tests, **11 failed** (intake·notification 4개 클래스), 4 skipped — `QuestionHedgeTest` 시간 경합 포함 가능
- 프론트엔드 빌드: **통과** (148 modules, 1.92s)

---

## 1. 요약

| 구분 | 후보 수 | 유형 |
|---|---|---|
| A군 (비교 변형) | 3 (R-A1 정리, R-A2 제외 권장, R-A3 정리) | DEAD |
| B군 (운영 개선) | 8 (R-B1~B8) | DETOUR 4, DUP 3, DEAD 1 |
| **합계** | **11** (실제 진행 권장 10, R-A2 제외 권장) | |

- 예상 수정 파일: **~30개** (테스트·골든 포함)
- 위험 중간 이상: R-B2 (보안 코드), R-B4 (쿼리 변경), R-B5 (N+1 → 일괄), R-B8 (이중 조회)
- 승인 시 진행 순서: R-B1·R-B3·R-B6 → R-A1·R-A3 → R-B2 → R-B4·R-B5 → R-B7·R-B8

---

## 2. 수정 화이트리스트 (확인 결과)

설계서 + 지시서 2-1절의 경로를 실제 파일 기준으로 확인했다. **모든 경로 존재 확인 완료.**

| 경로 | 존재 | 비고 |
|---|---|---|
| `backend/src/main/java/com/ucamp/app/domain/recommendation/**` | ✅ | |
| `backend/src/main/java/com/ucamp/app/domain/personalized/**` | ✅ | |
| `backend/src/main/java/com/ucamp/app/domain/chat/**` | ✅ | |
| `backend/src/main/java/com/ucamp/app/domain/review/**` | ✅ | |
| `backend/src/main/java/com/ucamp/app/infra/llm/**` | ✅ | |
| `backend/src/main/resources/prompts/**` | ✅ | v0/, v1/, v2/, v4/, chat/, common/, pq/ |
| `backend/src/main/resources/config/recommendation-defaults.properties` | ✅ | 모든 키 활성 사용 확인 |
| `backend/src/main/resources/application-pq-upfront.yml` | ✅ | |
| 위 패키지 대응 `backend/src/test/**` | ✅ | golden/ 포함 |
| `frontend/src/recommendation/**`, `frontend/src/chat/**` | ✅ | |
| `frontend/src/api/recommendationApi.js` | ✅ | |
| `docs/prompts/V6-*`, `docs/logs/*` | ✅ | |

- 빠진 경로: 없음
- 넓은 부분: 없음
- 수정안: 없음

---

## 3. 도메인 지도 — 설계서 대비 달라진 점

설계서 1장의 도메인 지도를 실제 import로 다시 확인했다. **181개 파일, ~12,446줄.**

| 항목 | 설계서 | 실제 코드 | 차이 |
|---|---|---|---|
| 순환 의존 | recommendation ↔ personalized, recommendation ↔ chat, review ↔ chat | 동일 | 없음 |
| 파일 수 | 181 | 181 | 없음 |
| C군 경계 위반 | 4건 (review→chat Entity, review→recommendation Repository 등) | 동일 | 없음 |

---

## 4. 요청 흐름 표

### V1 추천 생성 — `POST /api/v1/recommendations`

```
RecommendationController.create
 → RecommendationService.create(userId, responseId, guestToken, httpRequest)
    ├─ ClientIpResolver.resolve(httpRequest)          ← R-B1: 여기서 1회
    ├─ LlmGuard.checkAndLock(userId, "recommendation") 또는 checkAndLockGuest(ip, "recommendation-guest")
    ├─ [sync 모드] runPipeline(3-arg)
    │   ├─ RecommendationInputAssembler.assembleForMember/Guest
    │   │   └─ DB: health_profiles, health_conditions, health_medications (READ ×3)
    │   ├─ ClientIpResolver.resolve(httpRequest)      ← R-B1: 여기서 또 1회 (중복)
    │   ├─ PipelineRegistry.get("V4") → V4Pipeline
    │   └─ execute(pipeline, input, feature)
    │       ├─ LlmGuard.isFeatureBudgetExceeded("recommendation")
    │       ├─ V4Pipeline.run(input)  [StructuredPipeline]
    │       │   ├─ LlmClient.call (system+user 프롬프트)     LLM ×1 (+repair 최대 2)
    │       │   ├─ DoseCalculator (Feature.DOSE_CALC)
    │       │   ├─ RuleEngine.apply (Feature.RULE_POST)
    │       │   └─ BannedWords.filter (Feature.BANNED_LIST)
    │       ├─ [LLM 실패] → RULE_ONLY 폴백               LLM ×0, DB: llm_call_logs WRITE
    │       └─ RecommendationStore.save                    DB: ai_recommendations WRITE, llm_call_logs WRITE
    └─ LlmGuard.releaseInflight / releaseGuestInflight
```
**합계: DB READ ×3, DB WRITE ×2, LLM ×1(+repair)**

### V1 추천 조회 — `GET /api/v1/recommendations/{id}`

```
RecommendationController.get
 → RecommendationAccess.requireOwned(userId, recommendationId, guestToken)
    └─ DB: ai_recommendations READ ×1
 → RecommendationStore.get(recommendationId)
    └─ DB: ai_recommendations READ ×1
```
**합계: DB READ ×2, LLM ×0**

### V2 세션 시작 — `POST /api/v1/personalized-sessions`

```
PersonalizedSessionController.start
 → PersonalizedSessionService.start(userId)
    ├─ RecommendationModeService.mode(userId)
    │   └─ HealthProfileService.get(userId)             DB: health_profiles READ ×1
    ├─ RecommendationInputAssembler.assembleForMember    DB: health_profiles, conditions, medications READ ×3
    ├─ PregnancyQuestion.applies(input)
    ├─ [V2 모드] LlmClient.call (질문 생성)             LLM ×1
    └─ SessionStore.save                                 DB: ai_personalized_sessions WRITE ×1
```
**합계: DB READ ×4, DB WRITE ×1, LLM ×1**

### V2 답변 — `POST /api/v1/personalized-sessions/{id}/answers`

```
PersonalizedSessionController.answer
 → PersonalizedSessionService.answer(userId, sessionId, turnId, selected)
    ├─ SessionStore.state                                DB: READ ×1
    ├─ LlmClient.call (다음 질문 생성)                  LLM ×1
    └─ SessionStore.saveTurn                             DB: WRITE ×1
```
**합계: DB READ ×1, DB WRITE ×1, LLM ×1**

### V2 정밀 추천 — `POST /api/v1/personalized-sessions/{id}/recommendation`

```
PersonalizedSessionController.recommend
 → PersonalizedRecommendationService.recommend(userId, sessionId)
    ├─ LlmGuard.checkAndLock(userId, "recommendation")  ← R-B6: "recommendation" 리터럴
    ├─ pipelineVersion() → 설정값이 RULE_VERSIONS 밖이면 "V4"
    ├─ RecommendationService.runInput(input, version)
    │   └─ runPipeline(3-arg) → execute (V4Pipeline)     LLM ×1 (+repair)
    └─ LlmGuard.releaseInflight
```
**합계: DB READ ×3+, DB WRITE ×2, LLM ×1(+repair)**

### V3 제품 추천 — `GET /api/v1/recommendations/{id}/products`

```
ReviewProductController.products
 → RecommendationAccess.requireOwned                     DB: ai_recommendations READ ×1
 → ReviewProductRecommendationService.recommend(recId, ingredient)
    ├─ RecommendationConditionLoader.load(recId)
    │   ├─ AiRecommendationRepository.findById           DB: READ ×1  ← R-B4: entity 조회
    │   └─ RecommendationStore.get                       DB: READ ×1  ← R-B4: 같은 행 DTO로 재조회
    ├─ ReviewProductMatcher.match(ingredient, conditions)
    │   └─ ReviewMentionRepository queries                DB: READ ×2~3
    └─ ProductSafetyFilter.filter                         DB: READ ×0 (메모리)
```
**합계: DB READ ×5~6, DB WRITE ×0, LLM ×0**

### V3 제품 가용성 — `GET /api/v1/recommendations/{id}/products/availability`

```
ReviewProductController.availability
 → RecommendationAccess.requireOwned                      DB: READ ×1
 → ReviewProductRecommendationService.availability(recId)
    ├─ RecommendationConditionLoader.load(recId)          DB: READ ×2 (R-B4)
    └─ [성분별 반복] recommend(recId, ingredient)         DB: READ ×N  ← R-B5: N+1
```
**합계: DB READ ×3+N, DB WRITE ×0, LLM ×0** (N = 추천 성분 수)

### V4 대화 시작 — `POST /api/v1/chat/sessions`

```
ChatSessionController.start
 → ChatRecommendationService.start(userId)
    ├─ RecommendationModeService.mode(userId)             DB: READ ×1  ← R-B8: 프로필 1차 조회
    ├─ RecommendationInputAssembler.assembleForMember     DB: READ ×3  ← R-B8: 프로필 2차 조회 (중복)
    ├─ PregnancyQuestion.applies(input)
    └─ ChatSessionStore.start                             DB: WRITE ×1
```
**합계: DB READ ×4 (R-B8로 ×3 가능), DB WRITE ×1, LLM ×0**

### V4 메시지 — `POST /api/v1/chat/sessions/{id}/messages`

```
ChatSessionController.message
 → ChatRecommendationService.message(userId, chatSessionId, request)
    ├─ LlmGuard.checkAndLock(userId, "chat")
    ├─ handle(userId, id, text, pregnancy)
    │   ├─ ChatSessionStore.state                         DB: READ ×1
    │   ├─ RedFlagDetector.detect(text)                   (메모리)
    │   ├─ LlmGuard.isFeatureBudgetExceeded("chat")
    │   ├─ ChatPreFilter.detect(text)                     (메모리)
    │   ├─ ChatParser.parse (LLM 구조화)                  LLM ×1
    │   ├─ ChatInputAssembler.match + assemble            DB: READ ×3
    │   ├─ ServerDrugDetector.detect                      (메모리)
    │   ├─ SituationHash.compute → findReusable           DB: READ ×1
    │   ├─ [Track 1] EmbeddingClient.embed                Embed ×1
    │   ├─ ReviewVectorIndex.search                       (메모리)
    │   ├─ ReviewTrack.run                                DB: READ ×2~3
    │   ├─ [Track 1 결과 있음] saveReviewResult            DB: WRITE ×2
    │   └─ [Track 1 없음 → Track 2]
    │       ├─ LlmGuard.isFeatureBudgetExceeded("recommendation")
    │       ├─ ChatSessionStore.startFallback              DB: WRITE ×1
    │       └─ ChatFallbackRunner.submit → run
    │           ├─ LlmGuard.isFeatureBudgetExceeded("recommendation")  (이중 안전장치)
    │           ├─ RecommendationService.runInput → V4Pipeline  LLM ×1
    │           └─ RecommendationStore.complete             DB: WRITE ×2
    └─ LlmGuard.releaseInflight
```
**합계: Track 1 — DB READ ×7, DB WRITE ×2, LLM ×1, Embed ×1**
**합계: Track 2 — DB READ ×5, DB WRITE ×3, LLM ×2, Embed ×1**

### V4 조회 — `GET /api/v1/chat/sessions/{id}`

```
ChatSessionController.get
 → ChatRecommendationService.get(userId, chatSessionId)
    ├─ ChatSessionStore.state                             DB: READ ×1
    └─ ChatSessionStore.result                            DB: READ ×1
```
**합계: DB READ ×2, LLM ×0**

---

## 5. 후보 표

### A군 — 비교 변형 정리

| ID | 군 | 유형 | 위치 | 내용 | 근거 | 바꾸는 방법 | 운영 경로 영향 | 경계 영향 | 보안 영향 | 위험도 | 권장 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| R-A1 | A | DEAD | `pipeline/V0Pipeline.java`, `V1Pipeline.java`, `V2Pipeline.java`, `V3Pipeline.java`, `prompts/v0/`, `v1/`, `StructuredPipeline.Prompt.V1`, `.V2` | V0~V3 파이프라인 + 전용 프롬프트 제거. 운영은 V4만 사용 (`pipeline-version=V4`). V2 정밀추천도 `RULE_VERSIONS` 밖이면 V4로 폴백. v2/ 프롬프트는 V3이 유일한 사용처 (V3은 `Prompt.V2` 사용). v0/, v1/은 V0, V1 전용. | `PipelineRegistry`에 V0~V3 빈 등록됨. `StructuredPipeline.Prompt` enum에 V1("v1"), V2("v2") 있음. V4 프롬프트(`v4/`)는 V4·V6~V8 공유 — **삭제 대상 아님**. `PersonalizedRecommendationService.RULE_VERSIONS = Set.of("V4","V6","V7","V8","RULE_ONLY")` → V0~V3 설정 시 V4로 교체됨. | 1) V0~V3 Pipeline 클래스 삭제 2) `prompts/v0/`, `v1/`, `v2/` 디렉토리 삭제 3) `StructuredPipeline.Prompt`에서 V1, V2 제거 4) `PipelineRegistry`에서 V0~V3 등록 제거 5) 대응 테스트·골든 삭제 (`V0PipelineTest`, `V1PipelineTest`, `V2PipelineTest`, `V0.json`, `V1.json`, `V2.json`, `V3.json`) | **없음** — 운영 설정 V4에서 V0~V3은 실행 불가. 삭제 후 알 수 없는 버전 요청 시 기존과 같이 `RECOMMENDATION_UNKNOWN_PIPELINE` 예외 | 없음 (의존 제거만) | **보안 이득**: 불필요한 LLM 진입점 4개 제거. 규칙 우회(V0 무방비, V1 규칙 없음) 경로 차단 | **낮음** | 정리 |
| R-A2 | A | — | `pipeline/V6Pipeline.java`, `V7Pipeline.java`, `V8Pipeline.java`, `ModelRouter.java`, cache 관련 | V6~V8 + ModelRouter + cache. 운영 미사용이나 실험 인프라로 유지 가치 있음 | V6 = V4 + RESULT_CACHE, V7 = V4 + PROMPT_CACHE, V8 = V4 + MODEL_ROUTING. `ModelRouter`는 V8 전용. 현재 비활성이나 향후 실험 재사용 가능 | (삭제 시) V6~V8 Pipeline, ModelRouter, CacheKeyFactory 등 제거 | **없음** — 운영 미사용 | 없음 | 없음 | 낮음 | **제외 권장** (유지) |
| R-A3 | A | DEAD | `recommendation/queue/` 패키지, `RecommendationService.submit()`, `.process()`, `runPipeline(2-arg)`, `RecommendationStore.createPending`, `.findActiveOrDone`, `.markProcessing`, `.pendingIds` | queue 모드 전체 제거. 운영은 `api-mode=sync`. queue 전용 메서드가 6개+, 관련 Config·Worker 클래스 존재. | `RecommendationService.submit` (L85-98): queue 전용, `create()`에서 sync가 아닐 때만 호출. `process()` (L101-116): queue worker 전용. `runPipeline(2-arg)` (L123-125): `process()`만 호출, feature를 `"recommendation"` 리터럴로 하드코딩. `RecommendationStore.createPending`, `findActiveOrDone`, `markProcessing`, `pendingIds`: queue 전용. **주의: `ChatFallbackRunner`는 `recommendationStore.complete()`·`fail()`을 사용하지만 이것은 queue 메서드가 아닌 공유 메서드** | 1) `queue/` 패키지 삭제 (Config, Worker) 2) `RecommendationService`에서 `submit()`, `process()`, `runPipeline(2-arg)` 제거 3) `RecommendationStore`에서 queue 전용 메서드 4개 제거 4) R-B6과 연동: `runPipeline(2-arg)` 제거 시 feature 리터럴 문제도 해소 | **없음** — sync 모드에서 queue 코드 미실행. `ChatFallbackRunner`가 쓰는 `complete()`·`fail()`은 잔존 | 없음 (의존 제거만) | **보안 이득**: 비인가 queue 진입점 제거. queue 모드는 LlmGuard 일부 확인을 건너뛸 수 있음 | **낮음** | 정리 |

### B군 — 운영 개선

| ID | 군 | 유형 | 위치 | 내용 | 근거 | 바꾸는 방법 | 운영 경로 영향 | 경계 영향 | 보안 영향 | 위험도 | 권장 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| R-B1 | B | DETOUR | `RecommendationService.java:58,70` | `ClientIpResolver.resolve(httpRequest)` 이중 호출. `create()`에서 guard용(L58)과 input 조립용(L70) 두 번 호출. 같은 요청에서 IP는 불변 | `create()` L58: guest 분기에서 IP 추출 → guard 호출. L70: `assembleForGuest(guestToken, httpRequest)` 내부에서 다시 IP 추출 | `create()` 시작에서 `String ip = ClientIpResolver.resolve(httpRequest)` 한 번 계산, 이후 재사용 | **없음** — 같은 IP 값, 같은 결과 | 없음 | 없음 | **낮음** | 정리 |
| R-B2 | B | DUP | `infra/llm/guard/LlmGuard.java` | `checkAndLock` / `checkAndLockGuest` 구조 동일 (차단목록→진행중→분/일 제한→기능예산→킬스위치). `releaseInflight` / `releaseGuestInflight` 도 동일한 한 줄짜리. 분기 차이: guest는 blocklist 건너뛰고 IP 기반 키·한도 사용 | 두 메서드 모두: blocklist(회원만) → inflight lock → rate limit(분/일) → feature budget → killswitch 순서. guest는 blocklist skip + IP key + 다른 limit 값 | private 공통 메서드로 추출. 공개 API 시그니처 유지, 내부 분기로 회원/guest 구분. **확인 순서 보존 표**: | **없음** — 실행 순서·결과 동일 | 없음 | **확인 필요 — 아래 표로 증명** | **중간** | 정리 |

**R-B2 보안 확인 순서 표** (리팩토링 전후 동일해야 함):

| 진입점 | 사용자 유형 | 확인 순서 (현재) | 확인 순서 (리팩토링 후) |
|---|---|---|---|
| V1 POST /recommendations | 회원 | blocklist → inflight → rate(분/일) → budget → killswitch | 동일 (isGuest=false) |
| V1 POST /recommendations | 비회원 | ~~blocklist~~ → inflight(IP) → rate(IP,분/일) → budget → killswitch | 동일 (isGuest=true) |
| V2 POST /recommendation | 회원 | blocklist → inflight → rate(분/일) → budget → killswitch | 동일 (isGuest=false) |
| V4 POST /messages | 회원 | blocklist → inflight → rate(분/일) → budget("chat") → killswitch | 동일 (isGuest=false, feature="chat") |
| V4 Track 2 | 회원 | budget("recommendation") 만 (ChatFallbackRunner L69) | 동일 |

| ID | 군 | 유형 | 위치 | 내용 | 근거 | 바꾸는 방법 | 운영 경로 영향 | 경계 영향 | 보안 영향 | 위험도 | 권장 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| R-B3 | B | DUP | `chat/parse/BannedWords.java` (chat 패키지) | `BannedWords`는 chat이 아닌 recommendation 파이프라인(V4 `Feature.BANNED_LIST`)에서 사용됨. chat 패키지에 있어 결합 방향이 역전: recommendation → chat 의존 발생 | `StructuredPipeline.postProcess()`에서 `BannedWords.filter()` 호출. `BannedWords`가 읽는 `v4/banned.txt`도 recommendation 프롬프트. chat 쪽에서는 사용하지 않음 | `BannedWords.java`를 `recommendation/pipeline/` 패키지로 이동. import 경로만 변경 | **없음** — 클래스 위치만 변경, 로직 동일 | **개선**: recommendation → chat 의존 1개 제거 (단, 순환 자체는 다른 의존으로 잔존) | 없음 | **낮음** | 정리 |
| R-B4 | B | DETOUR | `review/service/RecommendationConditionLoader.java:48-51` | 같은 추천 행을 Entity(L48 `findById`)와 DTO(L51 `store.get`)로 이중 조회. Entity에서 conditions를 꺼내고, DTO에서 items를 꺼냄 | L48-49: `AiRecommendationRepository.findById(recId)` → Entity에서 `getConditions()` 추출. L51: `RecommendationStore.get(recId)` → DTO에서 `items()` 추출. **Repository 직접 사용은 review → recommendation 경계 위반이지만 기존 코드** | `RecommendationStore`에 conditions도 반환하는 메서드 1개 추가 → `RecommendationConditionLoader`는 Store만 호출 (1회 조회). **review 쪽 코드만 변경 + recommendation Store에 메서드 추가** | **없음** — 같은 데이터, 조회 횟수만 감소 | **개선**: review → recommendation Repository 직접 참조 제거 | 없음 | **중간** | 정리 |
| R-B5 | B | DETOUR | `review/service/ReviewProductRecommendationService.java` (availability 메서드) | availability가 성분별로 `recommend()`를 반복 호출 → 성분 N개에 대해 N+1 쿼리 패턴 | availability(recId)가 추천의 각 성분에 대해 `recommend(recId, ingredient)`를 반복 호출. 매 호출마다 `RecommendationConditionLoader.load(recId)` 포함 → 같은 추천 행을 N번 재조회 | 1) conditions를 한 번 로드 후 재사용 2) 성분별 제품 존재 여부를 일괄 쿼리 **전후 결과 동일성**: 성분별 boolean 맵의 키·값이 정확히 같아야 함. Hibernate statistics로 쿼리 수 전후 비교 | **없음** — 결과 맵 동일, 순서 보존 (LinkedHashMap) | 없음 | 없음 | **중간** | 정리 |
| R-B6 | B | DUP | `RecommendationService.java:59,61,64,132` + `ChatFallbackRunner.java:69` + `PersonalizedRecommendationService.java:57` | `"recommendation"` feature 문자열이 6곳에 리터럴로 산재. `FEATURE_GUEST = "recommendation-guest"`(L48)만 상수화됨 | `create()` L59,61,64: 회원/guest guard 호출 시 `"recommendation"`. `runInput()` L132: execute 호출 시. `ChatFallbackRunner` L69: budget 확인. `PersonalizedRecommendationService` L57: guard 호출 | 1) `RecommendationService`에 `static final String FEATURE = "recommendation"` 상수 추가 2) 6곳에서 상수 참조로 교체 3) **R-A3 연동**: `runPipeline(2-arg)` 제거 시 L132의 리터럴도 함께 제거됨 | **없음** — 같은 문자열 값 | 없음 | 없음 | **낮음** | 정리 |
| R-B7 | B | DEAD | `RecommendationStore.java:117` | `latestDoneForUser(Long userId)` 메서드 — 호출자 0개 | Grep 결과: 정의 1건, 테스트 0건, 호출 0건. 메서드 시그니처와 내부 쿼리만 존재하고 어디서도 사용하지 않음 | 메서드 삭제 (+ 대응 Repository 쿼리 메서드가 이것만 호출하면 함께 삭제) | **없음** — 미사용 코드 | 없음 | 없음 | **낮음** | 정리 |
| R-B8 | B | DETOUR | `ChatRecommendationService.java:83,86` | `start()`에서 프로필 이중 조회: L83 `modeService.mode(userId)` (내부에서 HealthProfile 조회) → L86 `recommendationInputAssembler.assembleForMember(userId, null)` (다시 HealthProfile 조회) | `modeService.mode()` → `HealthProfileService.get(userId)` → DB READ. `assembleForMember()` → 다시 `HealthProfileService` 또는 직접 Repository → DB READ. 같은 userId의 같은 프로필을 2회 조회 | `assembleForMember`의 결과에서 mode 판단에 필요한 정보를 추출하거나, mode 판단을 assembler 안에서 수행하여 1회로 통합. **주의: `modeService`는 personalized 도메인 → 화이트리스트 안에서 수정 가능** | **없음** — 같은 프로필 데이터, 판단 결과 동일 | 없음 | 없음 | **중간** | 정리 |

---

## 6. 2단계 안전망 시나리오 목록

지시서 4-2절 기준 + 후보별 추가 시나리오.

### V1 (V4 파이프라인, sync)
1. 회원 정상 추천 — V4 파이프라인 전체 경로, 프롬프트 글자 단위 일치, 중간 계산값
2. 비회원(guest token) 정상 추천 — guest guard 경로, IP 기반 제한
3. 폴백 RULE_ONLY — LLM 장애 시 규칙만 적용
4. 폴백 RULE_ONLY — 예산 초과 시
5. 제외 성분과 이유 — RuleEngine EXCLUDE/WARN 판정
6. 약 계열 모름 → WARN
7. 임신 문항 반영
8. 빈 결과 (추천할 성분 없음)
9. LLM 호출 실패 → R003
10. 결과 조회 (`GET /{id}`)
11. 최근 결과 조회

### V2
12. 모드 판단 (V1/V2 분기)
13. 19~50세 여성 임신·수유 고정 질문
14. "불편한 점" MULTI 유형
15. CONCERN → 깊이+1
16. 기본 5·최대 7문항
17. stop → READY
18. close → ABANDONED
19. 정밀 추천 (설정이 RULE_VERSIONS 밖 → V4 실행)
20. 선택지 index 범위 밖

### V3
21. 성분별 제품 추천 (정상)
22. 추천에 없는 성분 → 빈 목록
23. 매칭 실패·성분 미상 제외
24. 2차 필터 금기 제외
25. 브랜드 제한·동점 처리
26. availability (성분 여러 개)
27. PENDING·FAILED 추천에 대한 제품 요청
28. 90일 삭제된 회차

### V4
29. Track 1 카드 (리뷰 기반)
30. Track 1 0개 → Track 2 (파이프라인 폴백)
31. 되묻기 1회
32. 응급 신호 (RedFlag)
33. 약·질환 추출 + 서버 약 탐지 합집합
34. 구조화 2회 실패 → 502 CH005
35. 같은 상황 재사용 (`situation_hash`)
36. Track 2 결과로 V3 제품 조회

### V5 보안
37. 남의 ID → 404
38. 분당 초과 429 + `Retry-After` (회원)
39. 분당 초과 429 + `Retry-After` (비회원)
40. 진행 중 2건 초과
41. 예산 초과 — V1 → RULE_ONLY
42. 예산 초과 — V2·V4 → 503
43. 킬스위치
44. Track 2 전 recommendation 예산 확인
45. 사전 필터 (ChatPreFilter) + 경고 누적
46. 경고 3회 정지
47. Redis 다운 → 메모리 카운터 폴백
48. 레드팀 세트 (Fake)

### 후보별 추가 시나리오
49. **R-B1 검증**: 비회원 요청에서 IP 값이 guard와 input에 동일하게 전달되는지
50. **R-B2 검증**: 회원/비회원 각각 guard 확인 순서·결과 동일성
51. **R-B4 검증**: 제품 추천 시 conditions + items 조합 결과 동일성
52. **R-B5 검증**: availability 결과 맵 (키 순서·값) + 쿼리 수 전후 비교
53. **R-B8 검증**: 대화 시작 시 프로필 조회 결과 동일 + 임신 질문 판단 동일

---

## 7. 승인 대상 아님 (참고)

### 동작 변경 제안
- 없음 (모든 후보가 동작 보존)

### 확인 필요
| 항목 | 내용 | 비고 |
|---|---|---|
| `frontend/src/api/chatApi.js:getChatSession()` | L74에서 export되지만 프론트엔드 어디서도 import/호출하지 않음 | 백엔드 `GET /api/v1/chat/sessions/{id}` 엔드포인트는 존재. 프론트 미사용 확인 후 정리 가능 (프론트 담당자 확인 필요) |
| `GET /api/v1/users/me/recommendations/latest` | 프론트엔드에서 호출 여부 미확인 | 지시서 규칙: 프론트가 안 부르는 API는 지우지 않고 여기 기록 |

### 설계서 C군 확인 결과 (이번에 고치지 않음)
| 항목 | 내용 | 이유 |
|---|---|---|
| review → chat Entity `@ManyToOne` | `ReviewMention.aiChatSession` 등 | 엔티티 관계, 도메인 밖 작업 필요 |
| review → recommendation Repository 직접 참조 | `RecommendationConditionLoader`에서 `AiRecommendationRepository.findById` | R-B4로 부분 해소 (Store 메서드 추가), 완전 제거는 review 도메인 구조 변경 필요 |
| 정리 스케줄러 | 여러 도메인 Repository 참조 | 범위 밖 |
| recommendation ↔ personalized 순환 | 양방향 서비스 의존 | 인터페이스 분리 등 큰 작업 필요 |
| recommendation ↔ chat 순환 | ChatFallbackRunner → RecommendationService | 구조적 의존, 이번 범위 밖 |

### 안 쓰는 테이블·컬럼
- 조사 범위에서 확인된 것 없음 (DB 구조 파일은 수정 금지 대상)

### 팀 공유 사항
- R-A1 진행 시 V0~V3 관련 테스트·골든 파일 삭제됨 — 과거 버전 비교 필요 시 git history 참조
- R-A3 진행 시 queue 모드 코드 삭제됨 — 향후 queue 모드 필요 시 재구현 필요
- R-B3 (`BannedWords` 이동)은 결합도 감소이나 순환 자체는 해소하지 않음

---

**작성 완료. 승인을 기다립니다.**

> **"전부 승인"** / **"전부 거절"** / **"R-xx, R-yy 빼고 승인"** 중 하나로 답해 주세요.
