# STEP09 리포트 - V9 처리 구조 개선 + 알림 발송 트래픽 몰림

## 바꾼 것 (Before → After)

| 이름 | 방식 | 설정 |
| --- | --- | --- |
| V9-sync (Before) | 요청 스레드(플랫폼)에서 LLM 호출 | `recommendation.api-mode=sync` (기본) |
| V9-vt | Java 21 가상 스레드, 코드 변경 없음 | `spring.threads.virtual.enabled=true` |
| V9-queue | 접수 → 즉시 202 + recommendationId → 워커 처리 → 클라이언트 GET 폴링 | `recommendation.api-mode=queue`, `recommendation.worker.concurrency` |

- `ai_recommendations`에 `status`(PENDING/PROCESSING/DONE/FAILED, 기본 DONE), `started_at`, `finished_at`, `error_code` 추가 ([schema-changes.md](../schema-changes.md))
- 큐: 메모리(기본) / **Redis List**(`recommendation.queue.store=redis`, LPUSH/BRPOP). Stream이 아니라 List를 고른 이유: 작업 상태의 원본이 DB라 consumer group·ack 없이도 PENDING 행으로 재처리할 수 있고 운영할 것이 적다. 대신 꺼낸 뒤 워커가 죽으면 큐에서 사라지므로, 워커 시작 시 PENDING 행을 다시 넣는다. Redis 7(Docker)로 실연결 테스트 통과
- 멱등: 같은 responseId 접수는 진행 중·완료 작업을 돌려준다 (8개 동시 요청 → 작업 1개, `QueueModeTest`). 서버 한 대 기준 잠금이라 여러 대면 DB 유니크나 Redis SETNX가 필요하다
- 동기 API는 지우지 않고 설정으로 고른다. 응답 코드는 DONE이면 200, 아니면 202
- 워커 동시 처리 수(= LLM 동시 호출 상한) 기본값 4. **Anthropic 계정 rate limit을 콘솔에서 확인하지 못했다** (실제 API 미사용). 측정은 20으로 했다

### 알림 트래픽 (보내는 쪽 · 받는 쪽)

- 보내는 쪽: `domain/recommendation/perf/NotificationSpikeSimulator` — dedup_key `{user_id}:{type}:{yyyyMMdd}`로 같은 날 같은 알림은 한 번만, `scheduled_at`을 설정 구간에 고르게 흩음, 발송은 `NotificationSender` 인터페이스 + 로그 구현. notification 도메인(응진 담당)은 건드리지 않고 `notifications` 테이블에도 쓰지 않는다. 유니크 제약은 [STEP09_proposal_notification_dedup.sql](../sql/STEP09_proposal_notification_dedup.sql) 제안만
- 받는 쪽: `GET /api/v1/users/me/recommendations/latest` — 최근 DONE 추천 이후 프로필·질환·복용 약 변경이 없으면 저장된 결과를 그대로(LLM 없음), 아니면 새 설문 안내

## 측정 조건

| 항목 | 값 |
| --- | --- |
| k6 | 미설치 → 스크립트만 (`perf/k6/spike.js`), 실행은 [TODO-live-runs.md](TODO-live-runs.md) 09-1 |
| 대체 측정 | in-JVM 부하 테스트 `StructureLoadPerfTest`: 실제 Tomcat(RANDOM_PORT) + H2 + 프로세스 안 WireMock(LLM 고정 지연 3초) + JDK HttpClient(요청마다 가상 스레드, open 모델) |
| 같은 서버 자원 | Tomcat `threads.max=50`, Hikari 기본 10, 파이프라인 V4, 큐 워커 20, 클라이언트 타임아웃 30초, 큐는 0.5초 간격 폴링(최대 240초) |
| 시나리오 | steady: 초당 10건 × 30초(300건) / spike-burst: 10초 안에 600건 / spike-spread: 같은 600건을 60초에 고르게 (실제 분산 구간 30분을 1/30로 줄임, 도착 시각은 `NotificationSpikeSimulator`로 생성) |
| N=600을 고른 이유 | Tomcat 50스레드 × LLM 3초 = 초당 약 16.7건 처리. 10초에 600건(초당 60건)이면 처리에 36초가 걸려 30초 타임아웃을 넘는 요청이 생긴다 → sync가 확실히 무너지는 수준 |
| 반복 | 같은 구성을 3번 실행 (runs JSON 파일 이름의 타임스탬프 순서: 1회차 `179258064x~179258118x`, 2회차 `179258153x~179258204x`, 3회차 그 이후) |
| 날짜 | 2026-10-21 |

부하 발생기·WireMock·서버가 같은 JVM과 CPU를 쓴다. 수치는 이 장비·조건에서의 상대 비교로만 쓴다.

## 결과

### 3회차 요약 (가장 최근 실행, results.csv의 마지막 `09,*-spike-burst` 줄)

| 지표 | V9-sync | V9-vt | V9-queue |
| --- | --- | --- | --- |
| steady: TPS / p95 | 9.11 / 3,761 ms | 9.10 / 3,051 ms | 5.47 / 완료 24,110 ms |
| spike-burst: 에러율 | 0.043 | **0.147** | 0.0 |
| spike-burst: p95 | 28,905 ms | 28,795 ms | 접수 1,069 ms / 완료 93,507 ms |
| spike-burst: TPS | 14.35 | 12.59 | 5.55 |
| spike-burst: LLM 동시 호출 최대 | 50 | 314 | 20 |
| spike-spread: 에러율 / p95 | 0.0 / 3,075 ms | 0.0 / 3,481 ms | 0.0 / 완료 36,954 ms |

**3회 실행의 spike-burst 에러율**: sync 0.232 → 0.053 → 0.043, vt 0.002 → 0.002 → **0.147**, queue 0.0 → 0.0 → 0.0. vt는 3회차에 크게 나빠졌다 (아래 해석).

### 처리 구조 비교 (2회차, 괄호 안은 1회차)

| 지표 | V9-sync | V9-vt | V9-queue |
| --- | --- | --- | --- |
| steady: TPS / p95 | 9.11 / 3,073 ms (9.11 / 3,054) | 9.11 / 3,069 ms (9.05 / 4,975) | 5.95 / 완료 19,850 ms (6.02 / 19,258) |
| spike-burst: 에러율 | **0.053 (0.232)** | 0.002 (0.002) | **0.0 (0.0)** |
| spike-burst: p95 | 28,516 ms (29,048) | 21,701 ms (28,804) | 접수 1,373 ms / 완료 88,937 ms (1,831 / 101,940) |
| spike-burst: 최대 대기 | 29,993 ms (29,986) | 21,942 ms (29,107) | 완료 92,477 ms (105,973) |
| spike-burst: TPS | 14.20 (11.46) | 18.06 (16.44) | 5.83 (5.14) |
| spike-spread: 에러율 / p95 | 0.0 / 3,071 ms (0.0 / 3,080) | 0.0 / 3,092 ms (0.0 / 3,150) | 0.0 / 완료 35,656 ms (0.0 / 43,742) |
| LLM 동시 호출 최대 (burst) | 50 (50) | **256 (413)** | **20 (20)** |
| 프로세스 CPU 최대 / 평균 (burst) | 26% / 14% (38% / 18%) | 28% / 20% (69% / 18%) | 76% / 32% (71% / 31%) |
| 힙 사용 최대 (burst) | 362 MB (397) | 246 MB (281) | 263 MB (212) |
| 스레드 수 최대 | 아래 "한계" 참고 | | |

results.csv: `09,V9-sync-spike-burst`, `09,V9-vt-spike-burst`, `09,V9-queue-spike-burst` (구성별 2줄씩, 실행 순서대로)

- **sync**: 몰리면 Tomcat 50스레드가 LLM 3초를 기다리며 묶여 대기열이 쌓이고, 30초 타임아웃을 넘긴 요청이 실패한다 (5.3~23.2%). 평소(steady, spread)에는 3초대로 문제없다
- **vt**: 스레드가 묶이지 않아 실패가 거의 없고 TPS도 가장 높다. 대신 **LLM 동시 호출이 256~413까지 올라간다.** 실제 API라면 계정 rate limit(429)에 바로 걸릴 양이다. 병목이 서버에서 LLM 쪽으로 옮겨갈 뿐이다. p95가 21~29초인 것은 H2·Hikari(10)·같은 JVM 자원 경쟁 때문으로 보인다 (단계별로 더 봐야 함)
- **queue**: 접수는 1~2초 안에 끝나고 실패가 0이며, LLM 동시 호출이 워커 수(20)를 넘지 않는다. 대신 완료까지 오래 걸린다 (burst p95 89~102초). **워커 20 × 3초 = 초당 6.7건이 처리 상한**이라 steady(초당 10건)에서도 대기열이 쌓였다. 워커 수는 rate limit 안에서 도착률에 맞게 정해야 한다
- **spike-spread**: 같은 600명이라도 60초에 흩으면 sync·vt 모두 실패 0, p95 3초대다. 보내는 쪽 분산이 가장 싸고 효과가 크다

### 알림 도착 분포 (분당 요청 수)

`NotificationAndReuseTest`(600명, 9:00 시작):

```text
분산 없음 (spread = 0)   9:00 ███████████████████████████████████████████ 600
분산 30분               9:00~9:29 매분 20 (30개 구간 모두 20)
```

부하 테스트의 spike-spread는 같은 분포를 60초로 줄여 초당 10건으로 보냈다.

### 재사용으로 줄어든 LLM 호출 (`NotificationReusePerfTest`, runs `STEP09-notification-reuse-*.json`)

| 항목 | 값 |
| --- | --- |
| 조건 | 회원 200명 모두 추천 완료, **가정: 이후 30%(60명) 프로필 변경** |
| 재사용 (LLM 없음) | 140명 |
| LLM 호출 | 재사용 있음 60 / 없음 200 → **70% 감소** |
| latest 조회 지연 (MockMvc, H2) | p50 6.2 ms / p95 11.2 ms |

절감률은 "프로필 변경 비율" 가정을 그대로 따라간다 (변경 30% → 절감 70%). 실제 비율은 운영 데이터로 확인해야 한다.

## 해석

- 알림 몰림은 우리가 만드는 트래픽이라 **보내는 쪽 분산 + 받는 쪽 재사용**만으로도 대부분 막을 수 있다. 처리 구조는 그다음이다.
- 구조로 보면 queue가 LLM 동시 호출을 통제하고 실패가 없어 rate limit이 있는 외부 API 앞에 맞다. 다만 완료 지연이 길어 워커 수(= rate limit 안의 최대치)와 화면의 폴링 UX(진행 중 표시)가 같이 필요하다.
- vt는 코드 변경 없이 서버 쪽 대기는 없애지만, LLM 동시 호출을 제한하지 않으면 실제 환경에서는 429로 바뀐다. STEP 10의 Bulkhead와 같이 써야 한다.
- 실행 간 편차가 크다 (sync burst 에러율 23.2% / 5.3% / 4.3%, vt 0.2% / 0.2% / 14.7%). 같은 JVM에서 부하 발생기·WireMock·서버가 CPU를 나눠 쓰는 조건이라 그렇다. **queue만 세 번 모두 실패 0**이었고, vt가 sync보다 낫다는 결론은 3회차에서 뒤집혔다. vt는 요청을 모두 동시에 받아들이므로 그 뒤의 공유 자원(Hikari 커넥션 10, H2, WireMock)에서 대기가 생기면 30초 타임아웃을 넘기는 요청이 한꺼번에 나올 수 있다. vt만으로 몰림을 막는다고 보기 어렵다.

## 한계 / 다음에 할 것

- **스레드 수는 측정 실패**: 1·2회차 값은 같은 JVM의 다른 테스트 컨텍스트와 WireMock Jetty 스레드가 섞였고, 3회차는 포트로 거르려 했지만 RANDOM_PORT의 Tomcat 스레드 이름이 `http-nio-auto-1-exec-*`라 Tomcat 스레드를 세지 못했다 (큐 워커 20만 잡힘). 비교에 쓰지 않는다. 설정상 최대는 sync = Tomcat 50, vt = 플랫폼 워커 없음(요청마다 가상 스레드), queue = Tomcat 50 + 큐 워커 20. 다음에는 스레드 이름 대신 Tomcat의 `tomcat.threads.busy` 메트릭을 쓰는 것이 맞다
- 운영 설정(`spring.jpa.open-in-view` 기본 true)에서는 요청이 DB 커넥션을 LLM 대기 동안 잡고 있을 수 있다. 테스트 설정은 false라 이 효과는 측정에 없다 → 팀 협의 필요 (추천 API에서 OSIV를 끄거나 전역 설정 검토)
- k6 실행(TODO 09-1), 실제 rate limit 확인 후 워커 수 조정
