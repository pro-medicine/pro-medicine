# V3 약사 리뷰 기반 제품 추천 (백엔드) — 구현 프롬프트

> Claude Code(레포 `pro-medicine`, Windows)에 넘기는 프롬프트. 설계 문서 전문은 맨 아래 **부록**.
> 이번 범위는 **레포 안 백엔드**(DB·API·안전 필터·테스트·문서)다. 리뷰 수집·추출·SQL 생성 파이프라인은 레포 밖에서 따로 하고, 결과 SQL 파일만 나중에 import한다.

---

## 0. 브랜치 (가장 먼저)

```bash
git status                       # 변경이 있으면 멈추고 보고 (커밋·stash 금지)
git checkout feat/ingredient-expansion
git log --oneline -2             # b0c1f84, d4e2a73 (V2.5) 가 보여야 함
git checkout -b feat/review-product-recommendation
```
- V2.5 위에서 작업한다 (V2.5·V3를 함께 PR로 올릴 예정). develop으로 바꾸지 않는다
- 커밋은 단계마다 해도 된다 (`[FEAT] …`, `[DOCS] …`). **push는 하지 않는다**

## 1. 먼저 읽을 것
- `CLAUDE.md`
- 부록(설계) 전체 — 특히 §2 결정, §4 테이블, §5 추천 로직, §7 API
- `backend/schema.sql`의 `catalog_products`, `backend/src/main/java/com/ucamp/app/domain/product/entity/CatalogProduct.java` (**수정 금지**, 주연님 담당)
- 추천 결과: `domain/recommendation/service/RecommendationService.java`(`get`), `RecommendationStore`, `dto/RecommendationResponse.java`, `entity/AiRecommendation.java`(`response` / `session`)
- 사용자 조건: `service/RecommendationInputAssembler.java`(`assemble(responseId)`, `assembleForMember(userId, pregnancy)`), `pipeline/RecommendationInput.java`(`isGuest()`), `rule/ConditionCodeResolver.java`, `rule/RuleEngine.java`(`applyPost`), `rule/RuleService.java`
- V2 회차의 임신 답: `domain/personalized/entity/AiQuestionSession.java`(`pregnancy`)
- 기존 SQL 형식: `backend/docs/sql/STEP16_ingredient_expansion.sql`, `STEP14_personalized_questions.sql`

## 2. 범위와 금지
- 새 패키지 **`com.ucamp.app.domain.review`** 에만 코드를 추가한다 (controller / dto / repository / service / safety)
- DB는 **읽기 전용**. 쓰기는 SQL import로만 한다 → 엔티티 대신 **JdbcTemplate** (`spring-boot-starter-data-jpa`에 포함, 새 라이브러리 아님)
- 기존 파일 수정은 최소로: `CatalogProduct.java`·주연님 검색/상세 API·프론트는 건드리지 않는다. 기존 추천 응답(`RecommendationResponse`)도 바꾸지 않는다
- 요청 처리 중 **OpenAI 호출 없음** (임베딩 컬럼은 저장만, V4에서 사용)
- 새 라이브러리 추가 금지

## 3. 작업 순서

### 3-1. DB — `backend/docs/sql/STEP17_review_product_data.sql` (새로)
부록 §4 그대로. 다시 실행해도 안전하게 (`CREATE TABLE IF NOT EXISTS`, 컬럼 추가는 존재 여부 확인 후).
- `catalog_products` 컬럼 추가: `name_ko`, `brand`, `data_source`(NOT NULL DEFAULT 'SAMPLE'), `external_id`, `raw_materials`, `is_active`(DEFAULT TRUE), `blocked_kr`(DEFAULT FALSE), `image_key`, `updated_at` + UNIQUE(`data_source`, `external_id`)
- `catalog_products.catalog_product_id` **AUTO_INCREMENT 보정** (`schema.sql`에 빠져 있음. 엔티티는 IDENTITY). 기존 FK가 있으면 MySQL에서 MODIFY가 막히는지 확인하고 방법을 보고
- 새 테이블 3개: `product_ingredients`, `review_mentions`, `review_product_scores` (컬럼·인덱스·UNIQUE는 부록 §4 표 그대로)
- 맨 위 주석: 실행 대상(이미 쓰는 DB / 처음 세팅), 영향 기능(catalog_products 컬럼 추가 → 주연님 검색·상세에는 영향 없음), ddl-auto가 만들지 않으니 반드시 실행
- `backend/schema.sql`에도 반영 (새 테이블 + 컬럼)
- `ingredient_code` 값은 V2.5 이후 `IngredientCode` 24개 + `OTHER`

### 3-2. 테스트 데이터 — `backend/src/test/resources/sql/seed-review-products.sql` (새로)
실데이터 import 전이라 테스트용 **예시 데이터**를 만든다 (주석에 "예시 데이터" 명시). 아래 경우가 모두 들어가게:
- MAGNESIUM 후보 4개 이상: 인기 점수가 서로 다른 것, **점수 동점**(동점 처리 확인용), **같은 브랜드 3개**(브랜드 최대 2개 확인용)
- VITAMIN_D 후보: 라벨에 VITAMIN_K가 같이 든 제품(와파린 사용자 → 같이 든 성분 규칙 확인용), VITAMIN_D만 든 제품
- `OTHER` 성분이 든 제품, `product_ingredients`가 비어 있는 제품, `blocked_kr = 1` 제품, `is_active = 0` 제품
- 같은 추천 결과에 마그네슘 + 비타민 B6가 있을 때 커버리지가 붙는 복합 제품 (MAGNESIUM + VITAMIN_B6)
- `review_mentions`는 점수 테이블과 맞게 몇 행 (V3 요청 경로에서는 읽지 않지만 FK·스키마 확인용)

### 3-3. 조회 — `ReviewProductQueryRepository` (JdbcTemplate)
- `findCandidates(ingredientCode, limit 30)`: `review_product_scores` JOIN `catalog_products` WHERE `ingredient_code = ?` AND `is_active = 1` AND `blocked_kr = 0` ORDER BY `base_score DESC` LIMIT 30
- `findIngredients(productIds)`: `product_ingredients` 일괄 조회 (N+1 금지)
- 화면 이름: `COALESCE(name_ko, product_name)`

### 3-4. 사용자 조건 — 추천 결과에서 다시 만들기
- `ai_recommendations`에 `response`가 있으면 `assembler.assemble(responseId)`, `session`이 있으면 `assembler.assembleForMember(session.userId, session.pregnancy)`
- 조건 코드: `ConditionCodeResolver.resolve(input)`, 비회원: `input.isGuest()`
- 추천 결과의 성분 목록(`items`, 제외되지 않은 것)도 같이 가져온다 → 요청 성분 검증 + 커버리지

### 3-5. 안전 필터 — `ProductSafetyFilter` (부록 §5-3)
제품 1개의 `product_ingredients` 전체를 `RecommendationItem`으로 만들어 기존 `RuleEngine.applyPost(items, conditions, guest)`에 넣는다. 결과를 **찾는 성분(a)** / **같이 든 성분(나머지)** 로 나눠서:
| 경우 | 처리 |
|---|---|
| a 성분이 EXCLUDE | (성분 추천 단계에서 이미 빠졌어야 함) → 제품 제외 |
| a 성분 WARN | 경고 표시 + 감점 0.5 |
| 같이 든 성분이 **EXCLUDE 또는 WARN** | **제품 제외** |
| 같이 든 성분이 TIMING / ADJUST / ASK | 무시 |
| `condition_type = INGREDIENT` 규칙(성분 조합, 예: R-21 철분+칼슘) | 제품 안 조합에는 적용하지 않음 → 결과에서 무시 |
| `OTHER` 성분 있음 | 복용 약 또는 질환이 하나라도 있으면 **제외**, 없으면 경고 "일부 성분은 확인하지 못했어요" |
| `product_ingredients` 비어 있음 | 제외 |
- 비회원 규칙(R-G*)은 `STATUS/GUEST/EXCLUDE`라 같이 든 성분에 걸리면 위 규칙대로 제품 제외가 된다 (의도한 동작)
- "복용 약 또는 질환이 있음"은 `RecommendationInput`의 약·질환 필드로 판단. 필드 이름은 코드에서 확인
- 규칙 엔진에 넣을 때 어떤 rule이 어느 성분에 걸렸는지 구분하는 방법이 `PostResult`로 부족하면, `RuleEngine` 공개 API를 최소로 확장하고 보고 (기존 동작 변경 금지)

### 3-6. 서비스 — `ReviewProductRecommendationService` (부록 §5-2, §5-4, §5-5)
- 요청 성분이 추천 결과의 `items`에 없거나 제외된 성분이면 → 빈 목록
- 최종 점수 = `base_score` + 1.0 × 커버리지(최대 2) − 0.5 × (a 성분 WARN 있음)
  - 커버리지: 같은 추천 결과의 **다른 추천 성분**(제외 아님)이 제품 라벨에 같이 든 수 → 응답 `alsoContains`
- 동점: 최종 점수(소수 셋째 자리 반올림) → 추천 약사 수 많은 순 → 마지막 추천일 최신 → 비추천 적은 순 → 경고 없는 제품 → `catalog_product_id` 작은 순
- **최대 3개**, 같은 브랜드 최대 2개, 부족하면 있는 만큼
- `cautions` = `review_product_scores.common_cautions`
- 가중치·개수는 설정값으로 (`review.score.coverage-weight=1.0`, `review.score.warn-penalty=0.5`, `review.products.max=3`, `review.products.max-per-brand=2`, `review.products.candidates=30`)

### 3-7. API — `ReviewProductController` (부록 §7)
- `GET /api/v1/recommendations/{recommendationId}/products?ingredient=MAGNESIUM` → 최대 3개
- `GET /api/v1/recommendations/{recommendationId}/products/availability` → 추천 결과의 성분마다 `true/false` (같은 필터 통과 제품 ≥ 1). 화면은 true일 때만 [제품 추천 보기] 버튼
- 응답은 기존 `ApiResponse` 형식. 권한은 기존 `GET /api/v1/recommendations/{id}`와 같게 (지금 기준을 따르고, 다르게 해야 하면 보고)
- 없는 추천 id → 기존과 같은 404 / 잘못된 성분 코드 → 400 / 결과 0개 → `products: []` + "지금은 추천할 제품이 없어요"
- `notice`: 회원 "입력하신 약·질환 기준으로 주의 성분이 확인되지 않은 제품이에요. 복용 전 의사·약사와 상담하세요." / 비회원은 뒤에 "약·질환 정보가 없어 확인 범위가 좁아요." 추가
- 제외된 제품은 이름 없이 `excludedCount`만
- 메트릭: `review.products.request`(Timer), `review.products.result`(outcome = ok / empty / all_excluded), `review.safety.excluded`(reason 태그: rule / other_ingredient / unknown_ingredients)

### 3-8. 테스트
- `ProductSafetyFilterTest` (순수 단위):
  - 와파린 + 제품(VITAMIN_D, VITAMIN_K)에서 a = VITAMIN_D → 같이 든 K 때문에 **제외** (R-16 WARN)
  - 와파린 + 제품(VITAMIN_K)에서 a = VITAMIN_K → 경고 + 감점
  - 신장 질환 + 제품(MAGNESIUM) a = MAGNESIUM → 경고
  - 임신·수유 + 제품(OMEGA3, LUTEIN) a = OMEGA3 → 같이 든 루테인 때문에 제외 (R-38)
  - 철분 + 칼슘 복합 제품 → R-21(INGREDIENT, TIMING) 무시, 통과
  - OTHER 포함 + 복용 약 있음 → 제외 / 복용 약·질환 없음 → 경고
  - 성분 비어 있음 → 제외
  - 비회원 + 제품(OMEGA3, COQ10) a = OMEGA3 → R-G6 때문에 제외
- `ReviewProductRecommendationServiceTest` (테스트 seed 사용): 정렬·동점 순서, 브랜드 최대 2, 최대 3, 커버리지 가점과 `alsoContains`, 추천 결과에 없는 성분 → 빈 목록, `blocked_kr`·비활성 제외
- API 테스트: 정상 / 없는 id 404 / 잘못된 성분 400 / 0개 / availability
- `./gradlew test`: 기존 실패 39개(알림·복용·친구, develop과 같음) 외에 새 실패가 없어야 함. 기존 골든은 바뀌면 안 된다

### 3-9. 수동 확인 (가능하면)
- 로컬 DB에 STEP17 → 테스트 seed(또는 예시 데이터) → 서버 → 기존 추천 결과 id로 두 API 호출. 다시 실행해도 STEP17 결과가 같은지
- 실데이터 SQL(`review_import_YYYY-MM.sql`)은 레포 밖 파이프라인에서 따로 받는다. 이번엔 없어도 된다

### 3-10. 문서
- `backend/docs/schema-changes.md`: STEP 17 (catalog_products 컬럼 추가 + 새 테이블 3개 / 영향 기능: 리뷰 제품 추천, catalog_products는 컬럼 추가만이라 주연님 기능 영향 없음 / 실행 안내)
- `docs/design/V3-review-product-recommendation.md`: 부록(설계 문서)을 그대로 레포에 추가
- `docs/ai-roadmap.md`: V3 섹션을 부록 기준으로 갱신 (리뷰 제품 추천, 완료 기준: 위 테스트 통과)
- `docs/logs/YYYY-MM-DD.md` 맨 아래에 블록 추가 (기존 내용 수정·삭제 금지)

## 4. 주의
- 점수식·가중치·개수·필터 규칙은 **부록 그대로**. 바꾸고 싶으면 바꾸지 말고 보고
- 사용자에게 보이는 문구에 진단·처방 표현 금지, 약사·채널 이름 노출 금지
- 자막 원문·`evidence_quote`·`context`는 API로 내보내지 않는다

## 5. 끝나고 보고할 것
1. 변경 파일 목록
2. 테스트 결과 (새 테스트 수, 전체 실패가 기존 39개뿐인지)
3. AUTO_INCREMENT 보정 방법과 기존 DB에서 실행 결과
4. `RuleEngine`을 건드렸는지 (건드렸으면 무엇을, 왜)
5. 권한·404 등 기존과 다르게 처리한 것
6. 부록에서 막히거나 이상한 부분

---

# 부록: V3 설계 문서 전문

# Version 3 — 약사 유튜브 리뷰 기반 제품 추천 (최종 설계안)

> 담당: 영준 / 확정: 2026-10-28 / 상위 문서: `docs/ai-roadmap.md`
> 순서: V2.5(`feat/ingredient-expansion`) 위에서 V3 브랜치 → V2.5·V3를 함께 PR (2026-10-28 결정)
> 성분 코드는 V2.5 이후 24개 기준
> 근거 데이터: 2026-10-28 실데이터 32개 영상 (언급 92, 확정 32, 제품 31)
> V4(AI 대화창)는 별도 설계 — 증상 자유 입력 → 벡터 검색은 V4에서

---

## 1. 목적

성분 추천 결과의 성분 카드 → **[제품 추천 보기]** → 약사 유튜버들이 **그 성분 제품으로** 많이 추천한 제품 중, 우리 규칙으로 안전 확인된 제품만 보여준다.

```
증상 → 성분 추천 (기존 V1/V2) → [마그네슘] 카드 → 제품 추천 (V3) → 제품 3개
```

### 하지 않는 것
- 증상(자유 문장)으로 제품 찾기, 벡터 검색 → **V4 대화창**
- 화면에 약사·채널 이름, 리뷰 요약, 영상 노출
- 제품 이미지 업로드·관리자 등록 (리팩토링 때, S3) → 그 전엔 기본 이미지
- 함량 기반 상한 체크 (함량은 리뷰에서 뽑지 않음)
- 성분 코드 확장 (OTHER 성분: 크레아틴, 홍경천 등) → 로드맵 Data3-5

---

## 2. 확정 결정

| # | 결정 |
|---|---|
| D1 | 후보는 **리뷰에 나온 제품만**. DB에서 제품을 확정 못 한 언급은 추천 제외 + 등록 대기 목록 (§6) |
| D2 | **a 성분 카드 = 리뷰에서 a 성분 제품으로 추천된 제품, a 점수로 순위.** 라벨에 a가 있어도 a로 추천한 리뷰가 없으면 후보 아님. b·c 리뷰 점수는 a 카드에 섞지 않음 |
| D3 | V3 순위에 **사용자 상황(증상)은 쓰지 않음**. 성분별 좋은 제품은 누구에게나 같다 → 인기 점수는 import 때 미리 계산 |
| D4 | 화면에는 **제품만**: 이름, 브랜드, 이미지, 같이 든 추천 성분, 안전 경고, 공통 주의점(2명 이상 약사가 말한 것) |
| D5 | 협찬·자사 제품 구분 없이 `is_sponsored` 하나, 0.5배 |
| D6 | 같은 약사의 여러 번 추천 = 진짜 좋아하는 것 → 약사별 `1 + log2(영상 수)` |
| D7 | 안전 판단은 기존 `RuleEngine`을 **제품의 모든 성분**에 적용 (규칙 추가 없음) |
| D8 | 데이터 반영은 월 1회 **개발자가 직접** 파이프라인 실행 → SQL import (자동화·관리자 기능 없음) |
| D9 | **청크 기준은 LLM에게 맡김**: 자막을 시간·길이 규칙으로 자르지 않고, LLM이 자막 전체를 읽고 언급마다 그 제품에 대한 설명(`context`: 누구에게·왜, 200자 이내)을 정리. 약사가 증상을 먼저 말하든, 제품 사이사이·마지막에 설명하든 LLM이 모아 줌. 서버에는 자막 원문·조각 저장 안 함 |
| D10 | `context` 임베딩은 **지금 저장만** (비용 ≈ 0), V3에서는 사용 안 함 → V4 대화창에서 사용 |
| D11 | 효율 우선, 기존 구조 변경 최소: `CatalogProduct.java`·주연님 검색 API는 건드리지 않음 |

---

## 3. 데이터 흐름 (월 1회, 개발자 PC)

```
[1] 영상 정보   YouTube Data API          제목·설명란·챕터·업로드일  (yt_collect.py meta)
[2] 자막        youtube-transcript-api    시간 + 문장, 집 PC·맥     (yt_collect.py transcript / 맥 키트)
      │ 둘 다 LLM에 넣음
      ▼
[3] LLM 추출    gpt-5-mini Batch (실패만 gpt-5)
                언급마다 { 제품명(한·영), 브랜드, 성분, 추천/비추천/중립, 협찬, 목적 태그, 주의점, context, 시점 }
[4] 제품 확정   DSLD(해외) / 식약처 I0030(국내) / 수동 등록 → catalog_products id, 실패면 NULL
[5] 임베딩      context → text-embedding-3-small (1536)
[6] SQL 생성    제품 + 제품 성분 + 언급 + ★인기 점수 계산 → review_import_YYYY-MM.sql (Git 금지)
      │
      ▼
[서버] 개발자가 import (트랜잭션 1개, 다시 실행해도 결과 같음)
```

---

## 4. 테이블 (STEP17 — STEP16은 V2.5 성분 확장)

```
                 ┌──────────────────────┐
                 │ ① catalog_products   │  제품 목록 (기존 + 컬럼 추가)
                 └──────────┬───────────┘
        ┌───────────────────┼─────────────────────────┐
        │ 1:N               │ 1:N                     │ 1:N
        ▼                   ▼                         ▼
┌─────────────────┐ ┌────────────────────────┐ ┌──────────────────────────┐
│ ② product_      │ │ ③ review_mentions      │ │ ④ review_product_scores  │
│   ingredients   │ │   LLM 추출 + 벡터       │ │   성분별 리뷰 인기 점수   │
│   안전 필터      │ │   (1행 = 언급 1건)      │ │   (import 때 ③으로 계산) │
└─────────────────┘ └────────────────────────┘ └──────────────────────────┘
```

### ① catalog_products — 제품 목록 (기존, 컬럼만 추가)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| `catalog_product_id` | BIGINT PK | ★ AUTO_INCREMENT 보정 (`schema.sql`에 빠져 있음) |
| `product_name`, `image_url`, … | (기존) | 주연님 검색·상세용, 그대로 |
| ★ `name_ko` | VARCHAR(255) | 화면 표시용 한글 이름. 리뷰에서 가장 많이 쓴 표기 ("나우 마그네슘 글리시네이트"), 없으면 `product_name` |
| ★ `brand` | VARCHAR(255) | "NOW" |
| ★ `data_source` | VARCHAR(20) NOT NULL DEFAULT 'SAMPLE' | DSLD / MFDS / MANUAL / SAMPLE |
| ★ `external_id` | VARCHAR(64) | DSLD 라벨 ID / 품목제조신고번호 |
| ★ `raw_materials` | TEXT | 원재료 원문 (검수용) |
| ★ `is_active` | BOOLEAN DEFAULT TRUE | 단종·비노출 |
| ★ `blocked_kr` | BOOLEAN DEFAULT FALSE | 국내 반입 차단 성분(멜라토닌 등) 포함 |
| ★ `image_key` | VARCHAR(500) | S3 키 (리팩토링 때) |
| ★ `updated_at` | DATETIME | import 시각 |
| UNIQUE | (`data_source`, `external_id`) | 월별 upsert 키 |

### ② product_ingredients — 제품 1 : 성분 N (라벨 기준, 안전 필터·같이 든 성분)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| `product_ingredient_id` | BIGINT PK | |
| `catalog_product_id` | BIGINT FK → ① | |
| `ingredient_code` | VARCHAR(30) | MAGNESIUM … / 코드 없으면 `OTHER` |
| `ingredient_name` | VARCHAR(100) | 라벨 원래 이름 ("Red Yeast Rice") |
| `amount_per_day` | DECIMAL(12,3) NULL | DSLD만, NULL = "들어 있음" |
| `unit` | VARCHAR(20) | |
| `source` | VARCHAR(20) | DSLD_LABEL / MFDS_FUNCTIONAL / MANUAL |
| UNIQUE | (`catalog_product_id`, `ingredient_code`, `ingredient_name`) | 같은 제품 안에서 성분 코드당 1행, OTHER는 이름으로 구분 |

### ③ review_mentions — 언급 1건 = 1행 (메타데이터 + 벡터)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| `mention_id` | BIGINT PK | |
| `video_id`, `mention_no` | VARCHAR(20), INT | UNIQUE, 재import 키 |
| `channel_id` | VARCHAR(30) | 약사 채널 (점수 계산용, 화면 노출 안 함) |
| `published_at` | DATE | 최신성 |
| `catalog_product_id` | BIGINT FK → ① NULL | 확정 못 하면 NULL |
| `resolve_status` | VARCHAR(20) | RESOLVED / PENDING_REVIEW |
| `product_name_ko`, `brand_en` | | 리뷰 표기 (등록 대기 목록용) |
| `ingredients` | JSON | ["MAGNESIUM"] — 리뷰에서 **어떤 성분 제품으로** 말했나 |
| `stance` | VARCHAR(15) | RECOMMEND / NOT_RECOMMEND / NEUTRAL |
| `is_sponsored` | BOOLEAN | |
| `purpose_tags` | JSON | ["SLEEP"] (V4용, V3 순위엔 안 씀) |
| `cautions` | JSON | [{category, text}] 숫자 없음 |
| `context` | VARCHAR(400) | LLM이 정리한 "누구에게·왜" (V4 검색용) |
| `embedding` | MEDIUMBLOB | context 벡터 float32×1536 (V4용) |
| `embedding_model` | VARCHAR(80) | text-embedding-3-small |
| `start_sec`, `evidence_quote` | | 검수용, 화면 노출 안 함 |
| `prompt_version` | VARCHAR(30) | review-extract-v3 |

### ④ review_product_scores — 성분 × 제품 리뷰 인기 점수 (import 때 통째로 다시 계산, 서버는 읽기만)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| `ingredient_code` | VARCHAR(30) PK | 리뷰에서 추천된 성분 (③ `ingredients`) |
| `catalog_product_id` | BIGINT PK FK → ① | |
| `base_score` | DECIMAL(8,3) | 인기 점수 (§5-1) |
| `recommend_channels` | INT | 동점 2순위 |
| `not_recommend_channels` | INT | 동점 4순위 |
| `last_published_at` | DATE | 동점 3순위 |
| `common_cautions` | JSON | 2명 이상 약사가 말한 주의점만 |
| `mention_ids` | JSON | 근거 언급 (V4·검수용) |
| `computed_at` | DATETIME | 데이터 버전 |
| INDEX | (`ingredient_code`, `base_score` DESC) | "마그네슘 상위 N개" 한 번에 |

**제품 테이블에 넣지 않는 이유**: 제품 : 점수 = 1 : N (복합 제품은 성분마다 1행, 예: B 컴플렉스 8행), `catalog_products`는 공용 테이블, 매달 통째로 다시 만드는 데이터.

### SQL 파일
- `backend/docs/sql/STEP17_review_product_data.sql` (기존 DB용 ALTER 포함, 다시 실행해도 안전)
- `backend/schema.sql` 반영, `backend/docs/schema-changes.md`에 STEP 17 + 영향 기능 (catalog_products 컬럼 추가 → 주연님 공유)
- 엔티티 없이 읽기 전용(JdbcTemplate) → ddl-auto가 만들지 않으므로 **STEP17을 먼저 실행**

---

## 5. 추천 로직

### 5-1. 인기 점수 (import 때, ④ `base_score`)
| 항목 | 값 |
|---|---|
| 추천 약사 1명 | `1 + log2(그 약사의 추천 영상 수)` (1편 1.0, 2편 2.0, 4편 3.0) |
| 협찬 | 그 약사의 추천 영상이 **전부** 협찬이면 × 0.5 |
| 최신성 | × 0.5^(마지막 추천 후 경과 연수 / 2), 최소 0.35 |
| 비추천 약사 | 1명당 − 1.0 |
| 중립 | 0 |
| 후보 조건 | 추천 약사 ≥ 1 그리고 `base_score > 0` |

### 5-2. 최종 점수 (요청 때, 저장 안 함)
```
최종 = base_score + 1.0 × 커버리지 (최대 2) − 0.5 × (경고 있음)
```
- **커버리지**: 사용자의 같은 추천 결과에 있는 **다른 추천 성분**(제외되지 않은 것)이 제품 라벨(②)에 같이 들어 있는 수. 화면: "추천 성분 중 비타민 B6도 함께 들어 있어요"
- 같이 든 성분은 점수만 올리고, a 카드 순위의 근거(인기)는 a 리뷰만 (D2)

### 5-3. 안전 필터 (요청 때)
| 경우 | 처리 |
|---|---|
| 찾는 성분(a)에서 EXCLUDE | (성분 추천 단계에서 이미 제외 → 카드 없음) |
| 찾는 성분(a)에서 WARN | 경고 표시 + 감점 0.5 (성분 추천과 같은 기준) |
| **같이 든 다른 성분**에서 WARN 이상 | **제품 제외** (사용자가 모르고 고를 수 있음) |
| `OTHER` 성분 포함 | 복용 약·질환 있음 → **제외** / 없음 → "일부 성분은 확인하지 못했어요" 경고 |
| ② 성분이 비어 있음 (성분 미상) | 제외 |
| `blocked_kr`, `is_active = 0` | 제외 |
- 조건 코드는 추천 결과의 입력으로 `ConditionCodeResolver` (기존), 규칙은 `RuleEngine.applyPost`에 제품 성분 목록을 넣어 재사용

### 5-4. 동점 처리
최종 점수(소수 셋째 자리) → 추천 약사 수 많은 순 → 마지막 추천일 최신 → 비추천 적은 순 → 경고 없는 제품 → `catalog_product_id` 작은 순

### 5-5. 개수 — 1개 이상, 3개 이하
- 최대 **3개**, 같은 브랜드 최대 2개, 부족하면 있는 만큼 (채워 넣지 않음)
- **최소 1개 보장**: 안전 필터까지 통과한 제품이 0개인 성분은 카드에 [제품 추천 보기] 버튼을 띄우지 않음
  - 별도 API `.../products/availability`로 성분별 통과 제품 ≥ 1 여부를 내려줌 (기존 추천 응답은 바꾸지 않음) → 화면은 true일 때만 버튼 노출
  - 그래도 0개가 되는 경우(그 사이 import로 데이터 변경)는 빈 목록 + "지금은 추천할 제품이 없어요"

### 5-6. 처리 순서
1. 추천 결과 조회 → 요청 성분이 `items`에 있고 제외 아님 확인
2. ④ `WHERE ingredient_code = ? ORDER BY base_score DESC LIMIT 30` + ① `is_active = 1 AND blocked_kr = 0`
3. ② 제품 성분 일괄 조회 → 안전 필터 → 커버리지
4. 최종 점수 정렬 → 브랜드 제한 → 상위 N

---

## 6. DB에 없는 제품 (등록 대기)

- 언급의 제품 확정 실패 → `catalog_product_id = NULL`, `PENDING_REVIEW` → 추천 제외 (성분 모름 = 안전 확인 불가)
- import 때 **등록 대기 리포트** 생성: 제품명(표기 통일) | 추천 약사 수 | 언급 수 | DSLD·식약처 후보
- 개발자가 추천 약사 수 많은 순으로 확인 → `manual_products.csv`에 한 줄 (출처 MANUAL, 성분 직접 입력) → 다음 import에서 확정
- 2026-10-28 기준 대기 상위: 락토핏, 덴마크 유산균 이야기, 여에스더 유산균 (국내 유산균 — 식약처에 비슷한 이름이 여러 개)

---

## 7. API

`GET /api/v1/recommendations/{recommendationId}/products?ingredient=MAGNESIUM` (최대 3개)

`GET /api/v1/recommendations/{recommendationId}/products/availability` → `{ "MAGNESIUM": true, "VITAMIN_D": false }` (버튼 노출용, 같은 안전 필터 사용)
```json
{
  "ingredient": "MAGNESIUM",
  "products": [
    {
      "catalogProductId": 12,
      "productName": "Magnesium Glycinate",
      "brand": "NOW",
      "imageUrl": null,
      "alsoContains": ["VITAMIN_B6"],
      "cautions": ["알약이 클 수 있어요"],
      "warnings": []
    }
  ],
  "excludedCount": 1,
  "notice": "입력하신 약·질환 기준으로 주의 성분이 확인되지 않은 제품이에요. 복용 전 의사·약사와 상담하세요."
}
```
- 제외 제품은 이름 없이 개수만. 비회원은 notice에 "약·질환 정보가 없어 확인 범위가 좁아요" 추가
- 패키지 `domain/review/` (controller, dto, repository(JdbcTemplate), service, safety). 새 라이브러리 없음, **요청 때 OpenAI 호출 없음**

---

## 8. 파이프라인 변경

| 파일 | 변경 |
|---|---|
| `extract_spec.py` → v3 | `context` 필드 (영상에서 말한 내용만, 효능 창작 금지, 숫자 금지), 해외 브랜드 `product_name_en` 필수, 비추천 이유는 주의점에 넣지 않기 |
| `resolve_products.py` | 2026-10-28 반영: 한→영 사전 확장, `B-12`=`B12`, 브랜드 정리, DSLD 캐시 (16% → 36%). 추가: 라벨 성분 중 코드 없는 것 `OTHER`로 저장, `manual_products.csv` 읽기, 등록 대기 리포트 |
| `build_chunks.py` | **삭제** → `embed_context.py` (언급 context 임베딩) |
| `make_sql.py` | ③에 context·embedding, ④ 인기 점수 계산·INSERT, review_channels/videos/chunks 관련 제거 |
| 성분 코드 맞추기 (V2.5) | `extract_spec.py` 목록을 24개 + OTHER로 (현재 CURCUMIN·GLUCOSAMINE 없음), `resolve_products.py`의 DSLD `GROUP_CODE`·식약처 `I0030_PREFIX`에 새 10개, 유산균 Billion CFU → 억 CFU(×10) |

---

## 9. 테스트·측정

- `ProductSafetyFilterTest`: 와파린 + D3/K2 제품 → 제외 / 신장 질환 + 마그네슘 제품 → 경고 / OTHER 포함 + 복용 약 있음 → 제외, 없음 → 경고 / 성분 미상 → 제외
- `ReviewProductServiceTest`: a 카드에 a 리뷰 없는 제품 안 나옴, 커버리지 가점, 동점 순서, 브랜드 최대 2개, 제외된 성분 요청 → 빈 결과
- 파이프라인: 같은 SQL 2번 import → 행 수 동일, ④ 값 = 파이썬 시뮬레이션 값
- 측정: API p95, 성분별 후보 수, 제품 확정률·추천 언급 확정률, 안전 필터 제외율, 등록 대기 건수

---

## 10. 한계 (알고 가는 것)

- **인기 점수는 상황을 구분하지 않음**: 스트레스로 많이 추천된 마그네슘이 수면으로 1번 추천된 제품보다 높을 수 있음 → V3는 "성분별 좋은 제품"이 목적이라 수용, 증상별은 V4(벡터)
- 데이터가 적을 때(약사 1~2명) 순위가 흔들림 → 자막 수집량 확대가 우선
- 국내 제품은 확정률이 낮음(식약처 이름 중복) → 수동 등록에 의존
- OTHER 성분은 규칙 확인 불가 → 보수적으로 제외·경고

---

## 11. 남은 문제 (2026-10-28 검토)

| # | 문제 | 영향 | 대응안 |
|---|---|---|---|
| P1 | **성분 추천이 낼 수 있는 성분은 14개뿐** (`IngredientCode`: 비타민 C·A·B1·B12·D·K, 베타카로틴, 엽산, 마그네슘, 철, 칼슘, 아연, 칼륨, 세인트존스워트) | 리뷰에 많이 나오는 **오메가3·유산균·B6·루테인·코큐텐** 제품은 카드 자체가 없어 영영 안 나옴 | ✅ 결정: **V2.5 = 성분 코드 확장**을 V3 전에 진행 (enum, LLM 스키마, 프롬프트, 기준표·규칙 시드, 골든 테스트). 별도 설계 |
| P2 | 제품명이 DSLD 영문 ("Magnesium Glycinate") | 한국 사용자 화면에 영어 이름 | ✅ 결정: `catalog_products.name_ko` 추가 |
| P3 | 자막이 32 / 1,000개, 추천 언급 확정 13개 | 대부분 카드에 버튼이 안 뜸 | 차차 늘려감: 맥 키트 자막 수집 + 프롬프트 v3 + 등록 대기 수동 등록 |
| P4 | 같이 든 성분 규칙의 **조치 종류** 미정 | TIMING(복용 시간)·ADJUST·ASK가 같이 든 성분에서 나오면? / 성분 조합 규칙(철분+칼슘 "다른 시간대에")은 한 제품 안에서는 말이 안 됨 | 같이 든 성분은 EXCLUDE·WARN만 판단(→ 제외), TIMING·ADJUST·ASK는 무시. INGREDIENT 유형 규칙은 제품 안 조합에는 적용하지 않음 |
| P5 | OTHER 성분 제외 범위가 넓을 수 있음 | 종합비타민은 코드 없는 성분(콜린, 이노시톨 등)이 많아 **약·질환 있는 회원에게는 종합비타민이 거의 다 빠짐** | 수용 (안전 우선) + 제외율 측정. 콜린·이노시톨처럼 **위험해서가 아니라 확인을 못 해서** 빠지는 성분은 V2.5에서 코드를 주면 "규칙 없음 = 확인됨"으로 바뀌어 해결 (규칙은 공신력 있는 출처로 확인한 것만) |
| P6 | V2 추천의 안전 조건은 **현재 프로필** 기준 | 추천 후 프로필(약·질환)을 바꾸면 제품 필터 기준이 추천 당시와 달라짐 | ✅ 수용: 최신 프로필 기준이 설계. 추천받은 제품을 실제로 먹기 시작하면 복용 정보 입력은 사용자가 직접 (자동 반영 안 함) |
| P7 | 국내 반입 차단 성분 목록이 멜라토닌 하나 | 해외 직구 제품에 다른 차단 성분이 있을 수 있음 | 식약처 해외직구 위해식품 성분 목록으로 `BLOCKED_KR` 확정 (데이터 작업) |
| P8 | 이미지 없음 | 제품 카드가 빈약 | 기본 이미지 (TODO T1) |

---

## 12. TODO (V3 이후)

| # | 할 일 | 시점 |
|---|---|---|
| T1 | **제품 이미지 업로드 (S3)**: `image_key` 컬럼 사용, 업로드 API, 화면은 `image_key` → CDN URL, 없으면 기본 이미지 | 리팩토링 |
| T2 | **관리자 제품 등록 페이지·API (숨김)**: 등록 대기 목록 보기 → 제품 검색(DSLD·식약처) → 선택·성분 입력 → 저장. 지금의 `manual_products.csv` 수동 작업을 대체 | 리팩토링 |
| T3 | 월 1회 수집·import 자동화 (스케줄 실행 + 결과 알림) | 이후 |
| T4 | 성분 코드 확장 (P1, OTHER 감소) | P1 결정에 따라 |
| T5 | 증상별 인기 점수(성분 × 태그 × 제품)·벡터 검색 | V4 대화창 |
| T6 | "추천 성분 전체 한 번에 보기" (카드 간 중복 제품 합치기, 커버리지 높은 순) | 이후 |

---

## 13. 작업 순서 (V2 push 후)

1. 새 브랜치
2. STEP17 SQL + `schema.sql` + `schema-changes.md`
3. 파이프라인: 프롬프트 v3, `embed_context.py`, `make_sql.py` 점수 계산 → 32개 재추출(약 $0.10) → SQL → 로컬 import
4. 백엔드: Repository → 안전 필터 → 서비스 → 컨트롤러 → 테스트
5. `./gradlew test`, API 확인 (마그네슘·오메가3·비타민D·B 컴플렉스)
6. `docs/logs` 기록, 채원님과 "제품 추천 보기" 화면 인터페이스 협의
7. 병행: 맥 키트로 자막 수집 → 모이는 대로 [3]~[6] 재실행, 등록 대기 상위 수동 등록
