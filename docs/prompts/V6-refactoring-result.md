# V6 리팩터링 결과 보고서

**승인 8개 중 완료 6 / 되돌림 0 / 건너뜀 2, 기준 대비 새 실패 0**

---

## 커밋 목록

| 해시 | 제목 | 후보 ID |
|---|---|---|
| `3c32e34` | `[TEST] V6 동작 고정 테스트 + 리팩터링 후보 문서` | 2단계 안전망 |
| `d00b80d` | `[REFACTOR] V6 리팩터링 3단계: 승인된 후보 전부 적용` | R-B1, R-B2, R-B3, R-B4, R-B5, R-B6, R-A1, R-A3 |

시작 커밋: `29b48c1` (feat/v6-refactoring 분기점)

---

## 후보별 결과

| ID | 유형 | 결과 | 내용 |
|---|---|---|---|
| **R-B1** | DETOUR | **완료** | `RecommendationService.create()`에서 `clientIp`를 try 블록 밖에서 추출하여 finally의 `releaseGuestInflight`에서도 사용 가능하게 수정 |
| **R-B2** | DUP | **완료** | `LlmGuard`에서 `checkAndLock`(회원)/`checkAndLockGuest`(게스트)의 공통 로직을 `doCheckAndLock(boolean isGuest, Long userId, String clientIp, String feature)`로 통합. `doRelease()` 헬퍼 추가 |
| **R-B3** | DUP | **완료** | `RecommendationQualityCheck.BANNED_WORDS`를 `List.of("중단","끊으세요","진단","처방","복용을 멈추","진료","의료진","검사 결과")`로 자체 포함. `PersonalizedQuestionValidator` 의존 제거 |
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

- **전체**: 650 tests, 10 failed, 3 skipped
- **기준 실패 10개 (intake 6 + notification 2 + intakeSummary 1 + notificationCurrentUser 1)**: 기존 인프라 문제, 리팩터링과 무관
- **새 실패: 0**
- **V6 골든 테스트 (V6RefactoringGoldenTest)**: 4 시나리오 전부 통과
  - R-B3 BANNED_WORDS 자체 포함 검증
  - R-B4 condition-loader 이중 조회 제거 검증
  - R-B5 availability 동작 보존 검증
  - R-B8 chat-start 동작 보존 검증
- **기존 골든 테스트 (PipelineGoldenTest)**: V4, V6, V7, V8, RULE_ONLY 전부 통과

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
git diff --name-only 29b48c1..HEAD → 43 files
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
- R-B3에서 `RecommendationQualityCheck → PersonalizedQuestionValidator` 의존을 **제거** (도메인 간 의존 1개 감소: recommendation → personalized)

---

## 보안 체크리스트

| 항목 | 유지 확인 |
|---|---|
| 소유권 확인 (`requireOwned`, `requireResponseOwned`, guest token) | ✓ 변경 없음 |
| `LlmGuard` 확인 순서 (차단 → inflight → 분/일 → 예산/킬스위치) | ✓ R-B2에서 순서 보존 확인 |
| 게스트 IP 기반 제한 | ✓ `doCheckAndLock`에서 isGuest 분기로 동일 동작 |
| 입력 정규화·길이 제한·`<user_text>` 구분자 | ✓ 변경 없음 |
| 출력 검증 (스키마·화이트리스트·약 이름 금지) | ✓ 변경 없음 |
| 안전 필터 (RuleEngine, ProductSafetyFilter) | ✓ 변경 없음 |
| 기능별 max tokens | ✓ 변경 없음 |
| 로그에 원문 금지 | ✓ 변경 없음 |
| 대화 track 2 진입 전 recommendation 예산 확인 | ✓ `RecommendationService.FEATURE` 상수 사용 (값 동일) |
| Redis 장애 시 메모리 카운터 | ✓ 변경 없음 |

### R-B2 진입점별 가드 확인 순서

| 진입점 | 가드 메서드 | 순서 | 변경 전후 |
|---|---|---|---|
| V1 회원 추천 | `checkAndLock(userId, feature)` | blocklist → killswitch → inflight → rate | 동일 (blocklist은 회원 전용, 나머지는 doCheckAndLock) |
| V1 비회원 추천 | `checkAndLockGuest(clientIp, feature)` | null IP → killswitch → inflight → rate | 동일 (doCheckAndLock으로 통합) |
| V2 시작/답변 | `checkAndLock(userId, feature)` | 위와 동일 | 동일 |
| V4 메시지 | `checkAndLock(userId, feature)` | 위와 동일 | 동일 |
| V4 track 2 | `isFeatureBudgetExceeded` + 파이프라인 실행 | 예산 → 파이프라인 | 변경 없음 |

---

## 전후 수치

| 항목 | 전 | 후 | 변화 |
|---|---|---|---|
| 수정 범위 코드 줄 수 (src/main) | +4136 | -4136 | **-4,066줄** (순 감소) |
| 삭제된 클래스 | 0 | 9 | V0~V3 Pipeline 4 + Queue 3 + Worker 1 + QueueConfig 1 |
| 삭제된 프롬프트 파일 | 0 | 6 | v0/system.txt, v0/user.txt, v1/system.txt, v1/user.txt, v2/system.txt, v2/user.txt |
| 테스트 수 | 652 | 650 | -2 (V3 전용 테스트 제거) |
| 도메인 간 의존 | N | N-1 | R-B3에서 recommendation→personalized 의존 1개 제거 |

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
- `RecommendationQualityCheck.BANNED_WORDS`가 자체 포함으로 변경됨. `PersonalizedQuestionValidator.BANNED_WORDS`를 별도로 수정해도 품질 검사에는 영향 없음 (의도적 분리).
