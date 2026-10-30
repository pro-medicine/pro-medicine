# V4 AI 대화창 (증상 문장 → 성분 + 제품 추천) — 구현 프롬프트

> Claude Code(레포 `pro-medicine`, Windows)에 넘기는 프롬프트.
> 설계 문서: **`docs/design/V4-chat-symptom-recommendation.md`** (이 프롬프트와 같이 레포에 들어 있음). 설계가 기준이고, 이 프롬프트는 작업 순서·범위·금지만 정한다.
> 범위: **백엔드 전체 + 프론트 대화창 화면**. 리뷰 수집·추출·SQL 생성 파이프라인(레포 밖)은 범위 밖.

---

## 0. 브랜치 (가장 먼저)

```bash
# 터미널: 개발 PC의 레포 루트 (C:\Projects\pro-medicine)
git status                       # 아래 두 파일 말고 변경이 있으면 멈추고 보고 (커밋·stash 금지)
                                 #   docs/design/V4-chat-symptom-recommendation.md (새 파일, 이번 작업용)
                                 #   docs/prompts/V4-chat-symptom-recommendation-prompt.md         (이 프롬프트)
git checkout feat/review-product-recommendation
git log --oneline -3             # cb5fd8f (develop 머지), b728ad8 (AI_SETUP 정리) 가 보여야 함
git checkout -b feat/chat-recommendation
```
- V3 PR 브랜치 위에서 작업한다 (V3 PR이 먼저 머지될 예정). develop으로 바꾸지 않는다
- 커밋은 단계마다 해도 된다 (`[FEAT] …`, `[TEST] …`, `[DOCS] …`). 첫 커밋에 위 두 문서를 같이 넣는다. **push는 하지 않는다**

## 1. 먼저 읽을 것
- `CLAUDE.md`
- 설계 문서 전체 — 특히 §2 결정, §3 흐름, §4 LLM 구조화, §5 트랙 1, §6 트랙 2, §7 테이블, §8 API, §14 확인 기록
- V3 (재사용 대상): `domain/review/` 전체 — `safety/ProductSafetyFilter.java`, `service/ReviewProductRecommendationService.java`(`ORDER`, `pick`, `Scored`), `service/RecommendationConditionLoader.java`, `repository/ReviewProductQueryRepository.java`, `config/ReviewProductProperties.java`
- 추천 파이프라인: `domain/recommendation/pipeline/StructuredPipeline.java`(호출·검증·1회 재요청 흐름, `promptVersion`), `RecommendationInput.java`, `PostProcessSteps.java`(`applyDoses`, `applyRules`), `PromptTemplates.java`, `RecommendationOutputSchema.java`, `RecommendationOutputValidator.java`
- 추천 저장·큐: `service/RecommendationService.java`, `RecommendationStore.java`, `RecommendationWorker.java`, `entity/AiRecommendation.java`
- 입력 조립·규칙: `service/RecommendationInputAssembler.java`(`assembleForMember`), `rule/ConditionCodeResolver.java`, `rule/RuleEngine.java`, `rule/RuleService.java`
- V2 (패턴 참고): `domain/personalized/` — `controller/PersonalizedSessionController.java`(`@CurrentUserId`, `member(userId)`), `service/RecommendationModeService.java`(건강정보 판단), `PersonalizedRecommendationService.java`(`personalizedAnswers` 넣는 방식), 회차 90일 삭제 스케줄러
- LLM 인프라: `infra/llm/` — `LlmClient`, `LlmRequest`, `SpringAiLlmClient`, `FakeLlmClient`, `LoggingLlmClient`, `log/`(llm_call_logs), `application.yml`·`application-local-llm.yml`의 `spring.ai.*`·`llm.*`
- DB: `backend/docs/sql/AI_SETUP.sql`(1) 구조, 4) 리뷰 데이터), `backend/schema.sql`, `backend/docs/schema-changes.md`
- 프론트: `frontend/src/App.jsx`, `home/HomePage.jsx`, `home/RecommendBanner.jsx`, `recommendation/`(`RecommendationEntry`, `PersonalizedModal`, `ResultStep`, `ProductRecommendations`, `recommendation.css`), `api/recommendationApi.js`(`userHeader`)

## 2. 범위와 금지
- 새 코드는 **`com.ucamp.app.domain.chat`** (controller / dto / entity / repository / service / parse / vector / scoring)
- 기존 파일은 **아래 목록만** 수정한다. 이 밖의 파일을 고쳐야 하면 고치지 말고 보고
  | 파일 | 수정 내용 |
  |---|---|
  | `ReviewProductRecommendationService` | `ORDER`·`pick`·`Scored`를 public으로 (동작 변화 없음) |
  | `RecommendationInput` | `chatSessionId` 필드 추가 |
  | `StructuredPipeline` | `chatSessionId`가 있으면 프롬프트 기록 버전 끝에 `-c` |
  | `RecommendationConditionLoader` | `chatSession` 분기 (설계 §6) |
  | `AiRecommendation` | `chatSession` 연관 + "`response`·`session`·`chatSession` 중 하나" 검증 |
  | V2 90일 삭제 스케줄러 | 대화 테이블 삭제 + `ai_recommendations.chat_session_id` NULL 처리 추가 |
  | `infra/llm/` | 임베딩 클라이언트 추가, `FakeLlmClient`에 대화 구조화 응답 추가 |
  | `application*.yml` | `chat.*` 설정, 임베딩 모델 설정 |
  | `AI_SETUP.sql`, `schema.sql`, 테스트 H2 DDL·시드 | 설계 §7 |
  | 프론트 | 새 폴더 `chat/`, `api/chatApi.js`, `App.jsx`(버튼 1줄), `ProductRecommendations.jsx`(`products` prop), 필요하면 `ResultStep`의 카드 분리 (§3-11) |
- 금지
  - `RuleEngine`·규칙 데이터 변경, 새 규칙 추가
  - V1/V2/V3 API 응답 형식 변경 (V3 제품 API는 Loader 분기로 대화 추천도 처리할 뿐 형식은 그대로)
  - 새 라이브러리 추가 (임베딩은 이미 있는 `spring-ai-starter-model-openai`의 `EmbeddingModel` 사용)
  - 다른 팀원 코드 수정 (`CatalogProduct`, 마이페이지·복용·알림·친구 기능)
  - LLM이 만든 문장을 사용자에게 보여주기 (**되묻기 질문만 예외**, 그것도 검증 후)
  - 대화 원문을 로그(애플리케이션 로그)에 찍기 — 건강 정보. DB `ai_chat_turns`에만 저장

## 3. 작업 순서

### 3-0. V3 공개 범위 변경 (설계 §13 0단계)
- `ORDER`·`pick`·`Scored`를 public으로. V4가 `Scored`를 만들 수 있게 생성 방법도 확인 (record 생성자 접근)
- `./gradlew test`로 V3 테스트(`ProductSafetyFilterTest`·`ReviewProductRecommendationServiceTest`·`ReviewProductApiTest`)가 그대로 통과하는지 확인 → 커밋

### 3-1. DB (설계 §7)
- `AI_SETUP.sql` 1): DROP 목록에 `ai_chat_results`·`ai_chat_turns`·`ai_chat_sessions` (자식 먼저, `ai_recommendations`보다 앞), CREATE 3개, `ai_recommendations` CREATE에 `chat_session_id` + FK, 머리말 "AI 테이블 16개" → 19개와 목록
- 엔티티 3개 (`AiChatSession`, `AiChatTurn`, `AiChatResult`) — JSON 컬럼은 기존 엔티티(`ai_question_turns` 선택지 JSON 등)와 같은 방식
- `schema.sql`, 테스트 H2 DDL 반영
- **엔티티 대조 검사**: 로컬 MySQL에서 `ddl-auto=create`로 만든 테이블과 `AI_SETUP.sql`로 만든 테이블을 `information_schema`로 비교 (컬럼·타입·NULL·FK). 차이가 있으면 맞추고 보고
- `AI_SETUP.sql`을 같은 DB에 2번 실행해도 오류 없고 결과가 같은지 확인

### 3-2. 임베딩 클라이언트 (`infra/llm/`)
- `EmbeddingClient` 인터페이스: 문장 1개 → `float[]` (+ 모델 이름)
- 실제 구현: Spring AI `EmbeddingModel`, 모델 `text-embedding-3-small` (설정 `chat.embedding.model`). **리뷰 데이터와 같은 모델이어야 한다**
- 가짜 구현: 기본 프로필·테스트용. 같은 문장 → 같은 벡터(해시 기반, 1536차원, 정규화). 테스트에서 특정 문장에 특정 벡터를 줄 수 있게
- 현재 `spring.ai.model.*` 설정이 기본 프로필에서 어떻게 꺼져 있는지 먼저 확인하고, `local-llm`에서만 임베딩이 켜지게. 방법을 보고
- `llm_call_logs`에 call_tag `CHAT_EMBED`로 기록 (토큰·지연·비용). 기존 로그 테이블 구조가 임베딩에 안 맞으면 억지로 넣지 말고 보고

### 3-3. `ReviewVectorIndex` (설계 §5-1)
- 조건: `resolve_status = 'RESOLVED' AND stance IN ('RECOMMEND','NOT_RECOMMEND') AND embedding IS NOT NULL`
- BLOB → `float[1536]`: `ByteBuffer.order(LITTLE_ENDIAN)`, 6144바이트가 아니면 건너뛰고 경고 로그, `embedding_model`이 설정 모델과 다르면 건너뛰고 경고 로그, L2 정규화
- 시작 시 로드 + 10분마다 `MAX(review_product_scores.computed_at)` 비교 → 바뀌면 통째로 다시 읽어 `AtomicReference` 교체
- `search(float[] query, double tau, int k)` → (언급, sim) 목록
- 인터페이스 뒤에 둔다 (나중에 벡터 DB로 교체 가능하게)
- 로컬 실데이터 기준 91행이 올라와야 함 (설계 §14)

### 3-4. ① LLM 구조화 (설계 §4)
- 프롬프트: `src/main/resources/prompts/chat/system.txt`, `user.txt`, 버전 `chat-v1` (`PromptTemplates` 사용)
  - 출력 필드·제한은 설계 §4-1 표 그대로
  - `search_query` 지시: 증상·대상 중심, 80자 이내, 가격·품질 표현 금지, 없는 성분·제품 추측 금지. 설계 §4-1의 **실제 context 예시 3개**를 넣는다
  - `medications[].drug_class` 후보: `drugs` 테이블의 `drug_class` 값 + 한글 이름을 **DB에서 읽어** 프롬프트에 넣는다 (하드코딩 금지) + `UNKNOWN`
  - `conditions` 후보: 기존 설문 질환 코드 (코드에서 출처 확인)
- 출력 스키마: `RecommendationOutputSchema` 방식으로 JSON 스키마 강제
- 검증: enum·길이·필수값. 실패하면 이유를 붙여 1회 재요청 (call_tag `CHAT_PARSE` → `CHAT_PARSE_REPAIR`), 그래도 실패면 오류 응답 ("잠시 후 다시 시도해 주세요")
- 되묻기 질문 검증 (설계 §11 P7): 길이 초과, 성분·제품명·복용량 표현이 있으면 고정 질문("어떤 점이 가장 불편하신지 조금 더 알려주세요")으로 바꿈
- 응급·위기: LLM `red_flag` + 서버 키워드 목록(코드 상수) 중 하나라도 걸리면 추천하지 않음. 안내 문구는 설계 §4-2. `CRISIS` 문구는 아직 미정(§11 P6) → 임시 문구를 상수로 두고 보고
- `FakeLlmClient`: 대화 구조화 요청에 정해진 JSON을 돌려주게 (테스트에서 문장별 응답을 바꿀 수 있게). 기존 Fake 방식을 따른다

### 3-5. `ChatInputAssembler` (설계 §4-3)
- `assembleForMember(userId, pregnancy)` + 대화 속 약·질환을 `toBuilder()`로 합침
- 약 매칭 순서: ① 이름 일치(공백 제거) → ② LLM `drug_class`의 행 (이름 모름 행 우선, 없으면 그 계열 행) → ③ `UNKNOWN`이면 `Medication(null, null, name)` + 안내 문구
- 매칭 결과를 `ai_chat_sessions.extra_medications`·`extra_conditions`에 저장. 같은 함수로 "세션 → 입력"도 다시 만들 수 있게 (트랙 2 Loader 분기에서 사용)
- 프로필에는 저장하지 않는다

### 3-6. 트랙 1 (설계 §5)
- `SituationScorer`: 설계 §5-3 식 그대로 (w, 약사별 max, `1 + log2 n_c`, 협찬·최신성 보정은 V3 §5-1 값, 비추천 감점)
- 최종 점수: `S + 0.3 × base_score + 0.3 × 커버리지(최대 2) − 0.5 × (a 경고)`. `base_score`는 `review_product_scores`에서 일괄 조회 (없으면 0)
- 성분 카드: 설계 §5-4 (성분 점수 = 제품 상위 3개 합, 최대 3장, `IngredientCode`에 없는 코드(`OTHER`·`MULTIVITAMIN`)는 카드 없음, `applyDoses` → `applyRules`로 V1 카드 형식, EXCLUDE 카드 제거·제외 목록, WARN 경고)
- 제품: `ProductSafetyFilter.check` + `findIngredients` + V3 `ORDER`·`pick`. `is_active`·`blocked_kr` 조건은 V3 후보 쿼리와 같게. `UserConditions`는 설계 §5-5대로 직접 만든다
- 카드가 0장이면 트랙 2

### 3-7. 트랙 2 (설계 §6)
- `personalizedAnswers`: 대화 원문 (+ 되묻기 질문·답), `topic = CHAT`, `topicLabel = "대화: {원문}"`
- `RecommendationInput.chatSessionId` 설정 → 기존 큐로 추천 생성, `ai_recommendations.chat_session_id` 저장 (`session_id`는 NULL)
- `RecommendationConditionLoader.input()`: `chatSession`이면 `ChatInputAssembler`로 (프로필 + 세션에 저장된 약·질환)
- 결과 응답은 `PENDING` + `recommendationId` (화면은 기존 조회 API로 폴링)

### 3-8. 세션·API (설계 §8)
- `POST /api/v1/chat/sessions`, `POST /api/v1/chat/sessions/{id}/messages`, `GET /api/v1/chat/sessions/{id}`
- 인증: V2와 같게 `@CurrentUserId` + 회원 확인. 비회원 401
- 건강정보 없는 회원: `RecommendationModeService`의 V2 판단과 같은 기준으로 막고 안내 (설계 §11 P1). 판단 기준이 V4에 안 맞으면 보고
- 임신·수유 고정 질문: V2 회차 시작과 같은 조건(19~50세 여성)과 같은 선택지
- 세션 상태 흐름, 되묻기 최대 1번(D5), 300자 초과 400, 결과가 이미 있는 세션 409, 남의 세션 접근은 V2와 같은 처리
- 결과 스냅샷 `ai_chat_results` 저장 (트랙, 카드 JSON, `hit_mention_ids`·`top_similarity`, `data_version`, `latency_ms`)
- 메트릭: 기존 `review.safety.excluded`에 `track` 태그, 새로 `chat.request`(Timer, stage 태그: parse / embed / search / total), `chat.result`(outcome = review / rule_based / clarify / red_flag / off_topic / error)

### 3-9. 설정 `chat.*` (`@ConfigurationProperties`, V3 `review.*`처럼)
| 키 | 값 |
|---|---|
| `chat.search.tau` | 0.30 |
| `chat.search.top-k` | 50 |
| `chat.score.base-weight` | 0.3 |
| `chat.score.coverage-weight` | 0.3 |
| `chat.score.coverage-max` | 2 |
| `chat.score.warn-penalty` | 0.5 |
| `chat.cards.max` | 3 |
| `chat.index.refresh-interval` | 10m |
| `chat.input.max-length` | 300 |
| `chat.embedding.model` | text-embedding-3-small |
- 카드당 제품 수·브랜드 제한은 V3 `review.products.*`를 그대로 쓴다

### 3-10. 테스트 (설계 §9)
- `ReviewVectorIndexTest`, `SituationScorerTest`, `ChatInputAssemblerTest`(이름 → 계열 → 모름), `ChatRecommendationServiceTest`(설계 §9 목록 전부), 트랙 2 + V3 제품 API 통합 테스트(대화에서만 와파린 → 오메가3 제품 제외), API 테스트(401·400·409·되묻기 2번째)
- 테스트 데이터: `AI_SETUP.sql` 4)의 실제 언급 행 몇 개를 테스트 시드로 복사해 실제 벡터로 검증 (어떤 행을 골랐는지 주석)
- 골든: `golden/openai/chat-parse-request.json` (프롬프트·스키마 요청 형태)
- `./gradlew test`: develop과 같은 기존 실패 외에 새 실패 없음. 기존 골든(V1~V8, api)은 바뀌면 안 된다 — `-c` 추가로 바뀌면 멈추고 보고

### 3-11. 프론트 — 우측 하단 고정 버튼 + 채팅형 대화창 (2026-10-29 결정)
새 폴더 **`frontend/src/chat/`** (`ChatLauncher.jsx`, `ChatPanel.jsx`, `ChatMessage.jsx`, `chat.css`, `index.js`), API는 `api/chatApi.js` (새로, `userHeader` 사용)

**① 고정 버튼 (`ChatLauncher`)**
- 화면 **우측 하단에 고정된 동그라미 버튼** (`position: fixed`). 스크롤·탭 이동과 상관없이 항상 같은 자리에 떠 있음 (홈·친구·마이·폐기 지도 모든 탭)
- 하단 탭바(`BottomNav`)를 가리지 않게 탭바 높이 + 여백만큼 위에 둔다 (`env(safe-area-inset-bottom)` 고려). 지도 화면(`DisposalMapPage`)의 지도 조작 버튼과 겹치는지 확인
- 아이콘은 말풍선 모양, 접근성 라벨 "AI 영양 상담 열기". 누르면 대화창이 열리고, 열려 있을 때는 닫기(✕) 버튼으로 바뀜
- 연결은 **`App.jsx` 한 곳**에만: `<ChatLauncher userId={userId} />`를 `BottomNav` 옆에 추가. 다른 팀원 화면 파일(`HomePage` 등)은 건드리지 않는다

**② 대화창 (`ChatPanel`)**
- 버튼 위로 열리는 패널 (모바일 폭에서는 화면 아래쪽 대부분을 덮는 시트, 넓은 화면에서는 우측 하단 카드형 약 360×560px). 열고 닫을 때 짧은 애니메이션, `useModalFocus`처럼 포커스·Esc 닫기 처리
- 구성: 상단 제목("약쏙 AI 영양 상담") + 닫기 / 가운데 말풍선 목록(새 메시지가 오면 맨 아래로 스크롤) / 하단 입력창(300자, 글자 수 표시) + 보내기 버튼
- 말풍선: **챗봇은 왼쪽**, **사용자는 오른쪽**. 챗봇 말은 전부 **화면 고정 문구 또는 서버 응답 필드**이고, LLM이 쓴 문장은 되묻기 질문뿐 (설계 D4)
- 흐름 (말풍선 순서)
  1. 열면 챗봇: "안녕하세요! 요즘 불편한 점이 있으신가요? 편하게 말씀해 주세요." + 예시 칩 2~3개("피곤하고 잠을 잘 못 자요", "뼈 건강이 걱정돼요") → 칩을 누르면 그 문장이 입력됨
  2. (세션 시작 응답에 `pregnancyQuestion`이 있으면) 챗봇이 임신·수유 질문을 먼저 묻고 선택지 칩으로 답받기 → 그다음 1번 인사
  3. 사용자가 보내면 → 사용자 말풍선 + 챗봇 "입력 중…" 표시 → 응답 `type`별:
     - `CLARIFY`: 챗봇 말풍선에 질문 + 선택지 칩 (직접 입력도 가능) → 답하면 같은 세션으로 다시 전송
     - `RESULT`: 챗봇 안내("비슷한 고민에 약사들이 추천한 성분과 제품이에요") + **성분 카드 목록을 말풍선 안에** (성분 이름·이유·용량·경고, 카드마다 [제품 추천 보기] → 응답에 들어 있는 제품을 바로 펼침) + notice
     - `PENDING`: 챗봇 "말씀하신 내용으로 성분을 찾고 있어요" + 대기 표시 → `getRecommendation` 폴링(V2와 같은 간격·타임아웃) → 완료되면 같은 형식의 성분 카드(트랙 2 안내 문구) + [제품 추천 보기]는 V3 availability·제품 API 그대로
     - `RED_FLAG`: 챗봇 안내 문구만 (추천 없음), 강조 스타일
     - `OFF_TOPIC`·오류: 챗봇 안내 + 다시 입력 가능
  4. 결과 뒤 챗봇: "다른 고민도 있으신가요?" → 사용자가 다시 입력하면 **새 세션**으로 시작 (서버는 한 세션 = 한 고민, 설계 하지 않는 것 "멀티턴") — 이전 말풍선은 화면에 그대로 남김
- 비회원(`userId` 없음): 버튼은 보이고, 열면 챗봇 "로그인 후 이용할 수 있어요" + 로그인 안내만 (입력창 비활성)
- 건강정보가 없는 회원(서버 응답): 챗봇 "건강정보를 먼저 등록해 주세요" 안내
- 대화 내용은 **화면 상태로만** 들고 있고 `localStorage` 등에 저장하지 않는다 (건강 정보). 패널을 닫았다 열면 같은 탭 세션 동안은 유지, 새로고침하면 초기화

**③ 기존 컴포넌트 재사용**
- 성분 카드: `ResultStep`의 카드 표시 부분을 대화창 안에서도 쓸 수 있으면 재사용. 모달 레이아웃에 묶여 있으면 **카드 부분만 작은 컴포넌트로 분리**하되 `ResultStep` 화면·동작은 그대로 (분리했으면 보고)
- `ProductRecommendations.jsx`: `products` prop(선택)을 받으면 API를 부르지 않고 그 목록을 그린다. prop이 없으면 지금 동작 그대로
- 스타일은 `chat/chat.css`. 색·글꼴·버튼은 `recommendation.css`·`shared/ui.jsx`의 기존 톤을 따른다. 사용자 문구에 진단·처방 표현, 약사·채널 이름 금지
- 디자인 시안이 따로 오면 그에 맞춰 조정할 수 있게, 크기·색은 CSS 변수로 모아 둔다
- `npm run build`, lint 경고 없음, 모바일 폭(375px)·데스크톱 폭에서 버튼·패널 위치 확인

### 3-12. 수동 확인 (가능하면)
```bash
# 터미널: 개발 PC (MySQL) — 로컬 DB에 AI_SETUP 다시 실행
mysql -u root -p --default-character-set=utf8mb4 yakssok < backend/docs/sql/AI_SETUP.sql
# 터미널: 개발 PC, backend/ — 실제 OpenAI 프로필로 서버 실행 (OPENAI_API_KEY는 backend/.env)
./gradlew bootRun --args='--spring.profiles.active=local-llm'
```
- 서버 로그에서 인덱스 91행 로드 확인
- 문장 5개로 API 호출 → 각 문장의 `track`, `top_similarity`, 카드 성분, 지연을 표로 기록
  - "요즘 야근이 많아서 피곤하고 잠을 잘 못 자요" / "뼈 건강이 걱정돼요" / "와파린 먹고 있는데 피곤해요" / "그냥 몸이 안 좋아요"(되묻기) / "가슴이 너무 아프고 숨이 차요"(응급)
- 화면: 우측 하단 버튼(각 탭에서) → 대화창 → 인사 → 입력 → 결과 카드 → 제품 펼치기, 되묻기 칩, 트랙 2 폴링, 응급 문구, "다른 고민" 새 세션, 콘솔 오류 없음

### 3-13. 문서
- `backend/docs/schema-changes.md`: V4 변경 (새 테이블 3개 + `ai_recommendations.chat_session_id`, 영향 기능, AI_SETUP 다시 실행 안내)
- `backend/docs/llm-safeguards.md`: 대화 구조화 LLM 호출, 응급·위기 차단, 되묻기 질문 검증, 대화 원문 로그 금지
- `docs/ai-roadmap.md`: V4 섹션을 설계 기준으로 갱신
- 설계 문서 §13 진행 표시 + 3-12 측정 결과를 §9 아래에 기록
- `docs/logs/YYYY-MM-DD.md` 맨 아래에 블록 추가 (기존 내용 수정·삭제 금지)

## 4. 주의
- 점수식·τ·계수·개수·필터 규칙은 **설계 그대로**. 바꾸고 싶으면 바꾸지 말고 보고
- 안전 판단은 LLM에 맡기지 않는다 (LLM은 약·질환을 **뽑기만**, 판단은 `RuleEngine`·`ProductSafetyFilter`)
- `context`·`evidence_quote`·약사·채널 정보는 API로 내보내지 않는다

## 5. 끝나고 보고할 것
1. 변경 파일 목록 (§2 목록 밖 파일을 건드렸으면 이유)
2. 테스트 결과 (새 테스트 수, 기존 실패 외 새 실패 없는지, 골든 변화 여부)
3. 엔티티 대조 검사 결과, AI_SETUP 2번 실행 결과
4. 임베딩 설정 방법 (기본 프로필 / local-llm)
5. 3-12 수동 확인 표 (문장별 track·top_similarity·카드·지연)
6. 임시로 둔 것 (CRISIS 문구 등), 고정 버튼이 다른 화면(지도 등)과 겹친 곳
7. 설계에서 막히거나 이상한 부분
