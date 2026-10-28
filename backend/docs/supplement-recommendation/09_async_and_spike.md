# STEP 09. V9 - 처리 구조 개선 + 알림 발송 트래픽 몰림 대응

> 먼저 `docs/prompts/00_COMMON.md`, `docs/prompts/reference/measurement.md`를 읽고 따른다. STEP 08이 끝난 상태에서 시작한다.

## 목표

LLM 응답은 수 초가 걸린다. 동기 처리로는 요청이 몰릴 때 스레드가 묶여 전체가 느려진다.
처리 구조를 바꿔 같은 서버 자원에서 처리량과 지연을 비교하고, **"알맞은 영양성분을 섭취하세요" 알림을 한 번에 보냈을 때 트래픽이 몰리는 상황**을 재현해서 대응한다.

## 1. 비교할 세 가지 구조

| 이름 | 방식 |
| --- | --- |
| V9-sync | 지금 구조 (플랫폼 스레드, 요청 스레드에서 LLM 호출) - Before |
| V9-vt | Java 21 가상 스레드 켜기 (`spring.threads.virtual.enabled=true`). 코드 변경 최소 |
| V9-queue | 비동기 작업 큐: 요청 접수 → 즉시 202 + recommendationId → 워커가 처리 → 클라이언트는 조회(폴링) |

### V9-queue 상세

- `ai_recommendations`에 컬럼 추가: `status` VARCHAR(10) NOT NULL DEFAULT 'DONE' (PENDING, PROCESSING, DONE, FAILED), `started_at`, `finished_at` DATETIME NULL, `error_code` VARCHAR(30) NULL
- 큐는 STEP 08의 Redis를 쓴다 (Redis List 또는 Stream 중 하나를 고르고 이유를 리포트에 적는다)
- 워커 동시 처리 수 = LLM 동시 호출 상한. 설정값 `recommendation.worker.concurrency`. Anthropic 계정의 rate limit을 넘지 않도록 정한다 (콘솔에서 확인한 값을 progress에 적는다)
- 같은 `responseId`로 중복 요청이 오면 기존 작업을 돌려준다 (멱등)
- `GET /api/recommendations/{id}`가 status를 포함해 응답
- 동기 API는 지우지 않고 설정으로 고를 수 있게 둔다 (비교 측정용)

## 2. 알림 트래픽 몰림 시나리오

알림을 받은 사용자가 거의 동시에 앱을 열고 추천을 다시 요청하는 상황이다. 우리가 직접 만드는 트래픽이라 **보내는 쪽에서 제어할 수 있다**는 점이 핵심이다.

### 2-1. 보내는 쪽 제어

- 알림 일괄 발송 배치를 만든다: 대상 사용자 조회 → `notifications` 행 생성 → 발송
- `dedup_key`로 같은 날 같은 알림이 두 번 생성되지 않게 한다 (`{user_id}:{type}:{yyyyMMdd}`, 유니크 제약 추가)
- 발송 분산: 전원에게 같은 시각에 보내지 않고 `scheduled_at`을 일정 구간(설정값, 예: 30분)에 고르게 흩는다
- 실제 푸시 발송은 범위 밖. 발송은 `NotificationSender` 인터페이스 + 로그로 대신한다

### 2-2. 받는 쪽 대응

- 알림을 열고 들어오는 요청은 대부분 **이미 추천받은 사용자의 조회**다. `GET /api/users/me/recommendations/latest`를 만든다: 그 사용자의 `survey_responses` → `ai_recommendations` 중 가장 최근 `status = DONE`인 것을 돌려준다.
- 재사용 조건: `health_profiles.updated_at` < 최신 `ai_recommendations.created_at`이고, 그 이후 `user_conditions`·`user_medications` 변경이 없으면 LLM을 부르지 않고 저장된 결과를 돌려준다 (STEP 08 캐시와 별개). 조건을 못 맞추면 새 설문을 하도록 안내한다
- 새로 생성해야 하는 요청만 큐로 보낸다

### 2-3. 측정 시나리오 (k6, LLM은 WireMock 고정 지연 3초)

| 시나리오 | 내용 |
| --- | --- |
| steady | 초당 일정 요청 (기준선) |
| spike-burst | 알림 동시 발송 가정: 10초 안에 사용자 N명이 요청 |
| spike-spread | 같은 N명, 발송 분산 적용 후 도착 분포 |

- N은 로컬 장비에서 V9-sync가 확실히 무너지는 수준으로 정하고, 정한 값과 이유를 리포트에 적는다
- 스크립트는 `perf/k6/`에 두고 실행 명령을 리포트에 적는다

## 3. 측정

`docs/perf/STEP09-report.md`

| 지표 | V9-sync | V9-vt | V9-queue |
| --- | --- | --- | --- |
| steady: TPS / p95 | | | |
| spike-burst: 에러율 / p95 / 최대 대기 | | | |
| spike-spread: 에러율 / p95 | | | |
| 스레드 수 최대 / CPU / 힙 | | | |
| LLM 동시 호출 최대 | | | |

- V9-queue는 "접수 응답 시간"과 "완료까지 걸린 시간"을 나눠서 적는다
- 알림 쪽: 분산 전·후 도착 분포 그래프(분당 요청 수)와, 재사용으로 LLM 호출이 몇 % 줄었는지
- `results.csv`에는 각 구조별로 spike-burst 결과 한 줄씩 넣는다 (`label`에 시나리오 이름)

## 4. 완료 조건

- `./gradlew test` 통과 (큐 처리, 멱등, dedup 테스트 포함)
- 리포트, `results.csv`, progress 기록
