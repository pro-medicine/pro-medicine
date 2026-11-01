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

## 2. 버그 2: 제품 추천 버튼(제품 추천 보기) 미노출

### 증상
성분 추천은 정상 동작하지만, 결과 화면의 성분 카드에 [제품 추천 보기] 버튼이 나타나지 않음.

### 원인: 코드 버그 + 데이터 이슈

#### 코드 버그 (주 원인)
`RecommendationAccess.isOwner()` (line 65-68)에서 **회원(userId != null)이 guestToken을 함께 보내면 무조건 `return false`** 처리:

```java
// 수정 전
if (guestToken != null) {
    return false;
}
```

프론트엔드의 `guestTokenHeader()` (`recommendationApi.js`)는 sessionStorage에 guestToken이 있으면 **항상** `X-Guest-Token` 헤더를 첨부함. 따라서:
1. 비회원으로 추천 → guestToken이 sessionStorage에 저장
2. 이후 로그인 → sessionStorage의 guestToken은 그대로 남아 있음
3. 회원으로 추천 → JWT(Authorization) + stale guestToken(X-Guest-Token) 동시 전송
4. `isOwner()`가 guestToken != null로 인해 `return false` → 404 → 버튼 미노출

같은 패턴이 `requireResponseOwned()` (line 53)에도 존재.

#### 데이터 이슈 (부 원인)
`review_product_scores` 테이블은 `AI_SETUP.sql` 수동 실행으로만 채워짐.
해당 SQL을 실행하지 않은 환경에서는 테이블이 비어 있어, 코드 버그와 무관하게 모든 성분의 availability가 false가 됨.
이는 환경 설정 문제이므로 코드 수정 대상 아님.

### 수정 내용

| 파일 | 변경 내용 |
|------|-----------|
| `RecommendationAccess.java` | `isOwner()`: 회원이 guestToken을 보내도 무시하고 JWT 기반 소유권 확인으로 진행 |
| `RecommendationAccess.java` | `requireResponseOwned()`: 동일하게 guestToken 조건 제거, 회원 응답 소유권만 확인 |

수정 후에도 비회원 게스트 토큰 검증(SHA-256 constant-time 비교)은 그대로 동작하며, V5 보안 모델에 영향 없음.

### 보안 영향 분석
- 회원 인증은 JWT로 확립되므로, stale guestToken을 무시해도 IDOR 방어에 영향 없음
- 비회원 흐름: guestToken SHA-256 검증 로직은 변경 없음 (userId == null 분기)
- 기존 `GuestRecommendationTest` 전체 통과 확인

### 테스트

#### 통합 테스트: `MemberStaleGuestTokenProductTest` (2개)
- 회원 JWT + stale guestToken 동시 전송 → availability 200 OK (MAGNESIUM: true)
- 회원 JWT + stale guestToken 동시 전송 → 제품 추천 200 OK

---

## 3. 검증 결과

| 항목 | 결과 |
|------|------|
| 새 단위 테스트 (MemberDemographicsFallbackTest) | 7/7 통과 |
| 새 통합 테스트 (AuthDemographicFallbackModeTest) | 5/5 통과 |
| 새 통합 테스트 (MemberStaleGuestTokenProductTest) | 2/2 통과 |
| 기존 RecommendationModeTest | 8/8 통과 |
| 기존 ReviewProductApiTest | 6/6 통과 |
| 기존 GuestRecommendationTest | 통과 |
| 프론트엔드 빌드 (vite build) | 성공 |
| 기준 실패 테스트 (intake/notification 4개 클래스, 10개) | 변동 없음 (기존 결함) |

## 4. 수정하지 않은 파일 (제약 조건 준수)

SecurityConfig, application.yml, docker-compose, build.gradle, AI_SETUP.sql,
user/survey/health 도메인 엔티티 — 모두 변경 없음.
`AuthDemographicRepository`는 기존 인터페이스의 `findById()` 메서드만 호출 (읽기 전용).
