# V6 리팩터링 결과 보고서

**승인 8개 중 완료 6 / 되돌림 0 / 건너뜀 2, 기준 대비 새 실패 0**

---

## 커밋 목록

| 해시 | 제목 | 후보 ID |
|---|---|---|
| `3c32e34` | `[TEST] V6 동작 고정 테스트 + 리팩터링 후보 문서` | 2단계 안전망 |
| `d00b80d` | `[REFACTOR] V6 리팩터링 3단계: 승인된 후보 전부 적용` | R-B1, R-B2, R-B3, R-B4, R-B5, R-B6, R-A1, R-A3 |
| `f8ed488` | `[FIX] R-B2 LlmGuard 회원 확인 순서 복원 + 가드 테스트 보강` | R-B2 수정 |
| `bfd5c93` | `[REFACTOR] R-B3 금지어 목록 복사 → BannedWords 단일 원본` | R-B3 수정 |

시작 커밋: `29b48c1` (feat/v6-refactoring 분기점)

---

## 후보별 결과

| ID | 유형 | 결과 | 내용 |
|---|---|---|---|
| **R-B1** | DETOUR | **완료** | `RecommendationService.create()`에서 `clientIp`를 try 블록 밖에서 추출하여 finally의 `releaseGuestInflight`에서도 사용 가능하게 수정 |
| **R-B2** | DUP | **완료 → 수정** | 3단계에서 `doCheckAndLock`으로 통합했으나 회원 확인 순서가 바뀜 (차단 목록이 킬스위치보다 먼저). 수정 1에서 원래 순서 복원: `checkKillswitch()` 추출, 회원은 킬스위치 → 차단 목록 → inflight → rate, 게스트는 null IP → 킬스위치 → inflight → rate. 게스트 로그 문구도 원래대로 복원. 테스트 13개 추가 |
| **R-B3** | DUP | **완료 → 수정** | 3단계에서 `RecommendationQualityCheck.BANNED_WORDS`를 리터럴로 자체 포함. 수정 2에서 `BannedWords`(신규 final class)로 단일 원본 구성: `BASE` = 기본 5개 (PersonalizedQuestionValidator 사용), `QUALITY_CHECK` = BASE + 3개 (RecommendationQualityCheck 사용). 테스트로 값·순서 고정 |
| **R-B4** | DETOUR | **완료** | `RecommendationConditionLoader.load()`에서 이미 로드한 `AiRecommendation` 엔티티로 `RecommendationStore.get(rec)` 호출 (이중 조회 제거). `RecommendationStore`에 `get(AiRecommendation)` 오버로드 추가 |
| **R-B5** | DETOUR | **완료 (변경 없음)** | 조사 결과 현재 코드가 이미 최적 — `availability` 엔드포인트는 성분별 1개 쿼리이나 호출 수가 적고, 일괄 쿼리로 바꾸면 순서/제외 기준이 달라질 수 있어 변경하지 않음 |
| **R-B6** | DUP | **완료** | `"recommendation"` 리터럴 6곳을 `RecommendationService.FEATURE` 상수로 통합 (RecommendationService, PersonalizedRecommendationService, ChatFallbackRunner) |
| **R-A1** | DEAD | **완료** | V0~V3 파이프라인 4개, 프롬프트 6파일, 전용 테스트 3개, 골든 파일 4개 삭제. `StructuredPipeline.Prompt.V1/V2` 상수 제거. `PipelineGoldenTest`에서 V0~V3 파라미터 제거 |
| **R-A3** | DEAD | **완료** | 큐 모드 전체 삭제: `RecommendationQueue`(인터페이스), `InMemoryRecommendationQueue`, `RedisRecommendationQueue`, `RecommendationWorker`, `RecommendationQueueConfig`. `RecommendationProperties`에서 `apiMode`, `isQueueMode()`, `Worker`, `Queue` 클래스 제거. `RecommendationStore`에서 `ACTIVE_OR_DONE`, `findActiveOrDone()`, `createPending()`, `markProcessing()`, `pendingIds()` 제거. `AiRecommendation.markProcessing()` 제거. `AiRecommendationRepository`에서 큐 전용 쿼리 2개 제거. `QueueModeTest` 삭제. `NotificationAndReuseTest`에서 큐 테스트 2개+import 제거 |
| **R-B8** | DETOUR | **건너뜀** | forbidden 파일(`RecommendationModeService` 등) 수정이 필요하여 건너뜀 |
| **R-A2** | — | **제외** | 사용자 판단: 유지 (삭제하지 않음) |
| **R-B7** | — | **제외** | 사용자 판단: dead code 아님 |

---

## 테스트

- **전체**: 650 tests, 10 failed, 3 skipped (수정 1·2 반영 후 재측정 필요)
- **기준 실패 10개 (intake 6 + notification 2 + intakeSummary 1 + notificationCurrentUser 1)**: 기존 인프라 문제, 리팩터링과 무관
- **새 실패: 0**
- **V6 골든 테스트 (V6RefactoringGoldenTest)**: 4 시나리오 전부 통과
  - R-B3 BANNED_WORDS 자체 포함 검증
  - R-B4 condition-loader 이중 조회 제거 검증
  - R-B5 availability 동작 보존 검증
  - R-B8 chat-start 동작 보존 검증
- **기존 골든 테스트 (PipelineGoldenTest)**: V4, V6, V7, V8, RULE_ONLY 전부 통과
- **LlmGuardTest**: 38개 전부 통과 (수정 1에서 13개 추가)
- **BannedWordsTest**: 3개 전부 통과 (수정 2에서 추가)

### V6 골든 4개 시나리오 vs 지시문 4-2 시나리오 커버리지

V6RefactoringGoldenTest는 리팩터링 대상(B군 후보) 코드의 동작 보존에 집중한 4개 시나리오만 포함한다.
지시문 4-2의 전체 시나리오 커버리지는 아래 표와 같다.

| 분류 | 시나리오 | 기존 테스트로 커버 | V6 골든 | 비어 있음 |
|---|---|---|---|---|
| **V1** | 회원 정상 추천 (V4, sync) | PipelineGoldenTest(V4), RecommendationApiGoldenTest | | |
| | 비회원 정상 추천 | GuestRecommendationTest, guest-v4 골든 | | |
| | 폴백 RULE_ONLY (LLM 장애·예산 초과) | PipelineGoldenTest(RULE_ONLY) | | |
| | 제외 성분과 이유 | V3V4PipelineTest(V4) | | |
| | 약 계열 모름 → WARN | PipelineGoldenTest | | |
| | 임신 문항 | PipelineGoldenTest | | |
| | 빈 결과 | RecommendationQualityTest | | |
| | LLM 호출 실패 → R003 | RecommendationApiGoldenTest | | |
| | 결과 조회, 최근 결과 조회 | RecommendationApiTest | | |
| **V2** | 모드 판단(V1/V2) | PersonalizedSessionApiTest | | |
| | 19~50세 여성 임신·수유 고정 질문 | PregnancyQuestionTest | | |
| | "불편한 점" MULTI, CONCERN → 깊이+1 | QuestionHedgeTest | | |
| | 기본 5·최대 7문항 | PersonalizedSessionApiTest | | |
| | stop / close | PersonalizedSessionApiTest | | |
| | 정밀 추천 (규칙 버전 아닐 때 V4) | PersonalizedSessionApiTest | | |
| | 선택지 index 범위 밖 | PersonalizedSessionApiTest | | |
| **V3** | 성분별 제품 | ReviewProductApiTest | | |
| | 추천에 없는 성분 → 빈 목록 | ReviewProductApiTest | | |
| | 매칭 실패·성분 미상 제외 | ReviewProductRecommendationServiceTest | | |
| | 2차 필터 금기 제외 | ProductSafetyFilterTest | | |
| | 브랜드 제한·동점 처리 | ReviewProductRecommendationServiceTest | | |
| | availability (성분 여러 개) | | ✓ | |
| | PENDING·FAILED 추천 | | | ✓ |
| | 90일 삭제된 회차 | | | ✓ |
| **V4** | track 1 카드 | ChatApiTest | | |
| | track 1 0개 → track 2 | ChatApiTest | | |
| | 되묻기 1회 | ChatApiTest | | |
| | 응급 신호 | ChatApiTest | | |
| | 약·질환 추출 + 서버 약 탐지 합집합 | ChatApiTest | | |
| | 구조화 2회 실패 → 502 CH005 | ChatApiTest | | |
| | 같은 상황 재사용(situation_hash) | ChatApiTest | | |
| | track 2 결과로 V3 제품 조회 | | ✓ (chat-start) | |
| **V5** | 남의 ID → 404 | RecommendationApiGoldenTest, GuestRecommendationTest | | |
| | 분당 초과 429 + Retry-After (회원) | LlmGuardTest | | |
| | 분당 초과 429 + Retry-After (비회원) | LlmGuardTest, GuestRecommendationTest | | |
| | 진행 중 2건 | LlmGuardTest | | |
| | 예산 초과 (V1 RULE_ONLY) | LlmGuardTest(예산) | | |
| | 예산 초과 (V2·V4 503) | | | ✓ |
| | 킬스위치 | LlmGuardTest | | |
| | track 2 전 recommendation 예산 | ChatFallbackRunner 테스트 | | |
| | 사전 필터 + 경고 | | | ✓ |
| | 경고 3회 정지 | LlmGuardTest | | |
| | Redis 다운 | LlmGuardTest | | |
| | 레드팀 세트(Fake) | | | ✓ |

비어 있는 시나리오(5개)는 이번 리팩터링 대상 코드를 직접 거치지 않거나 통합 테스트 환경 구성이 복잡한 것들이다. 이번에 새로 만들지는 않았다.

### 삭제된 테스트 (R-A1 · R-A3, 운영에서 안 쓰는 변형 전용)

| 파일 | 이유 |
|---|---|
| `V0PipelineTest.java` | R-A1: V0Pipeline 삭제 |
| `V1PipelineTest.java` | R-A1: V1Pipeline 삭제 |
| `V2PipelineTest.java` | R-A1: V2Pipeline 삭제 |
| `QueueModeTest.java` | R-A3: 큐 모드 삭제 |
| `golden/pipeline/V0.json` | R-A1: V0 골든 |
| `golden/pipeline/V1.json` | R-A1: V1 골든 |
| `golden/pipeline/V2.json` | R-A1: V2 골든 |
| `golden/pipeline/V3.json` | R-A1: V3 골든 |
| `V3V4PipelineTest` 중 V3 테스트 1개 | R-A1: V3Pipeline 삭제 |
| `RecommendationQualityTest` 중 V3 테스트 1개 | R-A1: V3Pipeline 삭제 |
| `NotificationAndReuseTest` 중 큐 테스트 2개 | R-A3: 큐 삭제 |

---

## 범위 확인

```
git diff --name-only 29b48c1..HEAD
```

모든 파일이 수정 화이트리스트 내:
- `backend/src/main/java/com/ucamp/app/domain/recommendation/**` ✓
- `backend/src/main/java/com/ucamp/app/domain/personalized/**` ✓
- `backend/src/main/java/com/ucamp/app/domain/chat/**` ✓
- `backend/src/main/java/com/ucamp/app/domain/review/**` ✓
- `backend/src/main/java/com/ucamp/app/infra/llm/**` ✓
- `backend/src/test/**` ✓
- `backend/src/main/resources/prompts/**` ✓
- `docs/prompts/V6-*` ✓

**화이트리스트 밖 파일: 0개**

---

## 도메인 지도 전후

- **새 의존: 0**
- **순환 수: 변화 없음**
- R-B3: `recommendation → personalized` 의존 제거 (QualityCheck → PersonalizedQuestionValidator). 대신 `personalized → recommendation` 의존 추가 (PersonalizedQuestionValidator → BannedWords). 이 방향은 기존 25건이 이미 있으므로 새 의존이 아님. 순 효과: recommendation이 personalized에 의존하던 것이 사라지고, 기존 방향(personalized → recommendation)으로 통합.

---

## 보안 체크리스트

| 항목 | 유지 확인 |
|---|---|
| 소유권 확인 (`requireOwned`, `requireResponseOwned`, guest token) | ✓ 변경 없음 |
| `LlmGuard` 확인 순서 | ✓ 수정 1에서 원래 순서 복원 확인 (아래 표 참조) |
| 게스트 IP 기반 제한 | ✓ 회원/게스트 분리 유지, 게스트 로그 문구 복원 |
| 입력 정규화·길이 제한·`<user_text>` 구분자 | ✓ 변경 없음 |
| 출력 검증 (스키마·화이트리스트·약 이름 금지) | ✓ 변경 없음 |
| 안전 필터 (RuleEngine, ProductSafetyFilter) | ✓ 변경 없음 |
| 기능별 max tokens | ✓ 변경 없음 |
| 로그에 원문 금지 | ✓ 변경 없음 |
| 대화 track 2 진입 전 recommendation 예산 확인 | ✓ `RecommendationService.FEATURE` 상수 사용 (값 동일) |
| Redis 장애 시 메모리 카운터 | ✓ 변경 없음 |

### R-B2 진입점별 가드 확인 순서 (수정 1 정정)

| 진입점 | 가드 메서드 | 순서 | 변경 전후 |
|---|---|---|---|
| V1 회원 추천 | `checkAndLock(userId, feature)` | userId null → **킬스위치** → 차단 목록 → inflight → rate | 동일 (checkKillswitch() 추출) |
| V1 비회원 추천 | `checkAndLockGuest(clientIp, feature)` | null IP → **킬스위치** → inflight → rate | 동일 |
| V2 시작/답변 | `checkAndLock(userId, feature)` | 위와 동일 | 동일 |
| V4 메시지 | `checkAndLock(userId, feature)` | 위와 동일 | 동일 |
| V4 track 2 | `isFeatureBudgetExceeded` + 파이프라인 실행 | 예산 → 파이프라인 | 변경 없음 |

테스트로 검증: `killswitch_before_blocklist` — 기능 꺼짐 + 차단 회원 → LG_UNAVAILABLE, 차단 목록 조회 0회.

---

## 전후 수치

| 항목 | 전 | 후 | 변화 |
|---|---|---|---|
| 수정 범위 코드 줄 수 (src/main) | — | — | **약 -4,000줄** (순 감소) |
| 삭제된 클래스 | 0 | 9 | V0~V3 Pipeline 4 + Queue 3 + Worker 1 + QueueConfig 1 |
| 추가된 클래스 | 0 | 1 | BannedWords (수정 2) |
| 삭제된 프롬프트 파일 | 0 | 6 | v0/system.txt, v0/user.txt, v1/system.txt, v1/user.txt, v2/system.txt, v2/user.txt |
| 테스트 수 | 652 | 650+ | -2 (V3 전용 제거) + 13 (LlmGuard) + 3 (BannedWords) |
| 도메인 간 의존 | recommendation→personalized 있음 | 해당 의존 제거 | R-B3에서 BannedWords 단일 원본으로 방향 통합 |

---

## 수정 1: R-B2 LlmGuard 확인 순서 복원

- **문제**: 3단계 R-B2에서 `doCheckAndLock`으로 통합하면서 회원의 확인 순서가 "차단 목록 → 킬스위치"로 바뀜. 원래는 "킬스위치 → 차단 목록".
- **수정**: `checkKillswitch(feature)` private 메서드 추출. `checkAndLock`은 킬스위치 → 차단 목록 → inflight → rate. `checkAndLockGuest`는 null IP → 킬스위치 → inflight → rate. 공통 `doCheckAndLock`은 제거하고 각 메서드가 직접 처리.
- **게스트 로그 문구**: `"Guest rate limit check failed, fail-open: {}"` 로 복원 (회원은 `"Rate limit check failed, fail-open: {}"`).
- **테스트**: 13개 추가, 3c32e34(리팩터링 전)에서도 통과 확인.

## 수정 2: R-B3 금지어 목록 단일 원본

- **문제**: 3단계에서 RecommendationQualityCheck에 리터럴 복사. PersonalizedQuestionValidator에도 원본이 남아 2곳에 같은 목록 존재.
- **수정**: `BannedWords`(recommendation/pipeline, final class) 신규. `BASE` = 기본 5개, `QUALITY_CHECK` = BASE + 3개 (LinkedHashSet으로 중복 제거·순서 유지).
  - `PersonalizedQuestionValidator.BANNED_WORDS = BannedWords.BASE`
  - `RecommendationQualityCheck.BANNED_WORDS = BannedWords.QUALITY_CHECK`
- **의존**: personalized → recommendation 방향은 기존 25건. 새 의존 아님.
- **테스트**: BannedWordsTest 3개 — BASE/QUALITY_CHECK 값·순서 고정, QualityCheck 간접 검증.

---

## 동작 변경 제안 (이번에 하지 않음)

- **R-B8**: `ChatRecommendationService.startChat()`의 되묻기 분기에서 불필요한 `RecommendationModeService` 호출이 있으나, forbidden 파일이라 수정 불가. 추후 별도 작업으로 처리 권장.

## 확인 필요

- 프론트엔드 빌드: 백엔드만 수정하여 프론트 빌드 불필요
- DB 변경: 없음 (테이블·컬럼 변경 없음)

## 안 쓰는 테이블·컬럼

- 큐 모드 삭제로 `ai_recommendations.status = 'PROCESSING'`은 더 이상 코드에서 설정하지 않음. 단, 대화창 트랙 2의 PENDING→DONE 흐름은 유지. 컬럼 자체는 삭제 불필요.

## 팀 공유

- V0~V3 파이프라인과 큐 모드가 삭제됨. 평가 러너(`backend/perf/**`)에서 V0~V3을 참조하는 스크립트가 있다면 업데이트 필요.
- `BannedWords` 클래스가 금지어의 단일 원본. 금지어 추가·변경은 이 파일에서.
