# V5 버그 수정 결과 보고서

## 1. 버그 1: 회원 V1 설문에서 성별·나이 중복 질문

### 증상
가입 시 성별과 나이를 입력했는데도, V1 추천 설문에서 성별(Q-GENDER)과 출생 연도(Q-BIRTH) 문항이 다시 나타남.

### 원인
`RecommendationModeService.mode()`와 `RecommendationInputAssembler`가 `health_profiles` 테이블만 조회하고,
가입 시 입력한 성별·나이가 저장되는 `auth_demographics` 테이블은 확인하지 않았음.

- `health_profiles`는 설문 제출이나 마이페이지 건강정보 입력 후에만 채워짐
- `auth_demographics`는 회원가입 시 성별("여성"/"남성"/"선택 안 함")과 만 나이(Integer)를 저장
- 따라서 가입 직후 health_profiles가 비어 있는 회원은 이미 입력한 성별·나이를 다시 물어봄

### 수정 내용

#### 새 파일
| 파일 | 설명 |
|------|------|
| `domain/recommendation/service/MemberDemographicsFallback.java` | auth_demographics를 조회해 프로필 코드로 변환하는 폴백 컴포넌트 (읽기 전용) |

#### 변환 규칙
- 성별: "여성" → F, "남성" → M, "선택 안 함" → null (missing에 유지)
- 출생 연도: `termsAgreedAt.getYear() - age`

#### 수정 파일
| 파일 | 변경 내용 |
|------|-----------|
| `RecommendationModeService.java` | `mode()` 내 GENDER/BIRTH_YEAR missing 판단 시 profile이 null이면 `demographicsFallback` 조회 후 결정 |
| `RecommendationInputAssembler.java` | `assemble()`, `assembleForMember()` 모두 gender/birthYear가 null이면 폴백으로 채움 |

#### 우선순위 (높은 순)
1. 설문 답 (사용자가 직접 선택한 값)
2. health_profiles (마이페이지에서 입력한 값)
3. auth_demographics (가입 시 입력한 값)

### 테스트

#### 단위 테스트: `MemberDemographicsFallbackTest` (7개)
- 여성 → F, 남성 → M, 선택 안 함 → null 변환
- auth_demographics 행 없음 → null
- 출생 연도 = 동의시각 연도 - 나이
- 나이 null → 출생 연도 null

#### 통합 테스트: `AuthDemographicFallbackModeTest` (5개)
- auth_demographics만 있고 health_profiles에 성별·출생연도 없음 → GENDER/BIRTH_YEAR가 missing에 미포함, V2 판정
- "선택 안 함" → GENDER는 missing에 유지, BIRTH_YEAR는 빠짐
- auth_demographics 행 없음 → 기존과 동일하게 missing에 포함
- health_profiles 값이 있으면 auth_demographics보다 우선
- 비회원 흐름은 auth_demographics와 무관 (401)

### V2 부수 효과
auth_demographics에 성별·나이가 있는 회원은 해당 항목이 missing에서 빠지므로, 나머지 조건(민감정보 동의, 질환, 복용 약)만 충족되면 V1 대신 V2로 진입할 수 있음. 이는 의도된 동작으로, 가입 시 제공한 정보를 활용해 불필요한 재질문을 줄이는 것이 목적.

---

## 2. 버그 2: 제품 추천 버튼(제품 추천 보기) 미노출 — 원인 정정

### 증상
성분 추천은 정상 동작하지만, 결과 화면의 성분 카드에 [제품 추천 보기] 버튼이 나타나지 않음.

### 초기 진단 (정정됨)
초기에는 "회원 + stale guestToken → `RecommendationAccess.isOwner()` 거부"를 주 원인으로 판단했으나, 작업 4 재조사에서 이 가설이 **반증**됨:

1. `RecommendationEntry.jsx` (line 17-18)가 회원 진입 시 `sessionStorage.removeItem("guestToken")`을 실행하므로, 정상 진입 경로에서는 stale guestToken이 남지 않음
2. 같은 `guestToken != null → return false` 로직이 `requireResponseOwned()`에도 있었는데, 만약 이것이 트리거됐다면 POST /recommendations(성분 추천 생성)부터 실패했어야 함. 실제 증상은 "성분 추천은 나오고 제품 버튼만 안 나옴"
3. `guestTokenHeader()`는 모든 추천 API(createRecommendation, getRecommendation, getProductAvailability)에 동일하게 적용되므로, availability만 실패하는 시나리오를 설명하지 못함

### 실제 원인: 데이터/환경 이슈

**주 원인: `review_product_scores` 테이블에 해당 성분 코드의 데이터 없음**

- 테스트 시드(`seed-review-products.sql`)에는 MAGNESIUM, VITAMIN_D, VITAMIN_B6, OMEGA3 4개 성분만 데이터 있음
- 실제 추천에서 이 4개 외의 성분(VITAMIN_C, ZINC, PROBIOTICS 등)이 추천되면 `findCandidates()` 결과가 빈 목록 → availability = false → 버튼 미노출
- 프로덕션/개발 환경에서 `AI_SETUP.sql`을 실행하지 않았거나, 실행했더라도 추천된 성분 코드에 맞는 제품 점수가 없으면 동일 현상

**부 원인: 90일 삭제 등으로 FK가 모두 null인 추천**

- `RecommendationConditionLoader.load()`가 response/session/chatSession이 모두 null이면 `Loaded.empty()` 반환
- `recommendedIngredients`가 빈 목록 → availability가 빈 맵 `{}` → 모든 성분 false

### RecommendationAccess 수정은 유지

stale guestToken 가설은 정상 진입 경로에서는 재현되지 않지만, 아래 엣지 케이스에 대한 방어로 수정은 유지:
- 비회원 추천 도중 로그인 없이 동일 브라우저에서 다른 탭으로 로그인한 뒤 돌아오는 경우
- sessionStorage 정리가 실행되지 않는 직접 URL 접근 등

수정 후에도 비회원 게스트 토큰 검증(SHA-256 constant-time 비교)은 그대로 동작하며, V5 보안 모델에 영향 없음.

### 테스트

#### 통합 테스트: `MemberStaleGuestTokenProductTest` (2개)
- 회원 JWT + stale guestToken 동시 전송 → availability 200 OK
- 회원 JWT + stale guestToken 동시 전송 → 제품 추천 200 OK

---

## 3. 작업 3: 제품 추천 회원 전용 제한

### 결정
비회원(V1 게스트)에게는 제품 추천을 제공하지 않는다. V1 회원, V2, V4(대화창)는 기존대로 제품 추천 가능.

### 백엔드

#### 응답 방식
기존 `ErrorCode.UNAUTHORIZED` (E003, HTTP 401, "인증이 필요합니다.") 사용.

#### 수정 파일
| 파일 | 변경 내용 |
|------|-----------|
| `ReviewProductController.java` | `requireMember(userId)` 검사를 `requireOwned()` 앞에 추가. 비회원이면 401 |

#### 보안 순서
`requireMember` → `requireOwned` 순서로 검사. 비회원은 `requireOwned` 도달 전에 일률적으로 401을 받으므로 추천 존재 여부가 새지 않음.

### 프론트엔드

#### 수정 파일
| 파일 | 변경 내용 |
|------|-----------|
| `recommendationApi.js` | `guestTokenHeader()`: 로그인 상태(`getAccessToken()` 있음)면 guestToken을 보내지 않음 |
| `ResultStep.jsx` | `userId` prop 추가. 비회원이면 availability 호출 생략, 로그인 안내 문구 표시 |
| `RecommendationModal.jsx` | ResultStep에 `userId` prop 전달 |
| `PersonalizedModal.jsx` | ResultStep에 `userId` prop 전달 (V2는 회원 전용이라 항상 있음) |
| `recommendation.css` | `.rec-login-guide` 스타일 추가 |

#### 로그인 버튼
`RecommendationEntry`는 `{ userId, onClose }` 두 prop만 받으며, 로그인 화면 열기는 `App.jsx`의 `setAuthMode("login")`으로만 가능. 추천 컴포넌트 범위 안에서 로그인 화면을 여는 콜백이 없으므로, **안내 문구만** 표시하고 로그인 버튼은 두지 않음.
→ **App.jsx에 `onLogin` 콜백 1개를 RecommendationEntry로 전달하면 버튼 추가 가능** (이번 범위 밖).

#### V4 대화창
대화창(`ChatLauncher`/`ChatWindow`)은 `userId`가 있어야 열리므로 비회원이 사용할 수 없음. 변경 불필요 확인.

### V6 정리 후보 (비회원 전용 분기 — 이번에 삭제하지 않음)
- `ReviewProductResponse.notice(boolean guest)` — guest=true일 때 `GUEST_NOTICE_SUFFIX` 추가
- `ReviewProductResponse.GUEST_NOTICE_SUFFIX` 상수
- `ProductSafetyFilter`의 `UserConditions.guest()` 경로 및 관련 `guestExcluded` 분기
- `RecommendationConditionLoader`의 게스트 입력 조립 경로

### 테스트

#### 통합 테스트: `GuestProductRestrictionTest` (4개)
- 비회원 availability → 401
- 비회원 products → 401
- 회원 availability → 200 OK
- 회원 products → 200 OK

#### 기존 테스트 수정: `ReviewProductApiTest`
- 4개 게스트 기반 테스트를 회원 기반으로 변환 (제품 로직 검증은 동일)
- 비회원 401 검증 테스트 1개 추가 (`비회원은_제품_API에_401`)
- 총 8개 → 9개

---

## 4. 작업 4: 버그 2 원인 재확인 결과

### 재조사 결과
"회원 + stale guestToken" 가설은 정상 진입 경로에서 재현되지 않음 (상세 근거는 2절 "초기 진단 정정" 참조).

### 확인된 실제 원인

| 원인 | 유형 | 설명 |
|------|------|------|
| `review_product_scores` 데이터 없음 | 데이터/환경 | 추천된 성분 코드에 대한 제품 점수 행이 없으면 availability = false |
| 90일 삭제로 FK null | 데이터 | response/session/chatSession 모두 null → recommendedIngredients 빈 목록 → availability 빈 맵 |
| 안전 필터 전부 제외 | 정상 동작 | 회원의 약·질환 조합에 의해 모든 후보가 제외되면 availability = false (by-design) |

### 조치
- 코드 수정 대상 아님 (데이터/환경 이슈)
- `AI_SETUP.sql` 실행 후 `review_product_scores` 테이블에 데이터가 있는지 확인 필요
- 확인 쿼리: `SELECT ingredient_code, COUNT(*) FROM review_product_scores GROUP BY ingredient_code;`
- 추천에서 자주 나오는 성분(VITAMIN_C, PROBIOTICS 등)에 대한 제품 점수 데이터 추가 필요

---

## 5. 최종 검증 결과

| 항목 | 결과 |
|------|------|
| 전체 백엔드 테스트 | 665개 중 10개 실패 (기존 baseline 동일: intake/notification) |
| 새 테스트 | 19개 전체 통과 (버그1: 12개, 버그2: 2개, 작업3: 5개) |
| 기존 테스트 영향 | ReviewProductApiTest 4개 게스트→회원 전환, 1개 추가. 나머지 변동 없음 |
| 프론트엔드 빌드 (vite build) | 성공 |
| 기준 실패 테스트 | intake 4개 클래스 + notification 2개 클래스, 총 10개 (기존 결함, 변동 없음) |

## 6. 수정하지 않은 파일 (제약 조건 준수)

SecurityConfig, application.yml, docker-compose, build.gradle, AI_SETUP.sql,
user/survey/health 도메인 엔티티, App.jsx — 모두 변경 없음.
`AuthDemographicRepository`는 기존 인터페이스의 `findById()` 메서드만 호출 (읽기 전용).
