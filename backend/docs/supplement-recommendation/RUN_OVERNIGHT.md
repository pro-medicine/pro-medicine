# 밤샘 일괄 실행 지시 (pro-medicine 저장소 전용)

STEP 02부터 STEP 11까지를 순서대로 끝까지 진행한다. 사용자는 자리를 비우므로 중간에 멈추거나 승인을 기다리지 않는다.
이 파일의 규칙은 `00_COMMON.md`와 각 STEP 파일보다 우선한다. 충돌하면 이 파일을 따른다.

## 0. 이 저장소에 맞춘 경로와 전제

- 저장소 루트: Claude Code를 실행한 폴더 (`pro-medicine`). 루트의 `CLAUDE.md`(팀 규칙)를 반드시 따른다.
- 백엔드는 `backend/` 하위다. 빌드·테스트는 `backend` 폴더에서 `./gradlew` (안 되면 `gradlew.bat`)로 실행한다.
- 프롬프트 파일의 경로는 아래처럼 바꿔 읽는다.

| 프롬프트에 적힌 경로 | 실제 경로 |
| --- | --- |
| `docs/prompts/` | `backend/docs/supplement-recommendation/` |
| `docs/progress.md`, `docs/perf/`, `docs/schema-changes.md` | `backend/docs/progress.md`, `backend/docs/perf/`, `backend/docs/schema-changes.md` |
| `src/main/...`, `src/test/...` | `backend/src/main/...`, `backend/src/test/...` |
| `{base-package}` | `com.ucamp.app` |
| `perf/k6/`, `perf/wiremock/` | `backend/perf/k6/`, `backend/perf/wiremock/` |

## 1. 이미 끝난 것 (STEP 01은 하지 않는다)

- 엔티티 20개는 팀 PR(#11)로 이미 들어가 있다 (`com.ucamp.app.domain.*.entity`). 새로 만들지 않는다.
- 시작할 때 엔티티 컬럼이 `backend/docs/supplement-recommendation/reference/erd.sql`과 맞는지 한 번 대조하고, 다른 점은 고치지 말고 progress의 "확인한 것"에 적는다.
- follow는 `domain/friend` 패키지를 쓴다. 감사 설정은 `global/config/JpaConfig`에 이미 있다.

## 2. 팀 공통 설정은 건드리지 않는다 (00_COMMON.md, 01_entity.md보다 우선)

- **Flyway를 추가하지 않는다.** `application.yml`의 `ddl-auto: update`, datasource 설정을 바꾸지 않는다. 01_entity.md의 Flyway·`ddl-auto=validate`·Testcontainers 지시는 무시한다.
- 스키마 변경(STEP 03, 06, 07, 09)은 이렇게 한다.
  1. 엔티티에 컬럼·테이블을 추가한다 (로컬 DB는 `ddl-auto: update`로 반영된다)
  2. 같은 내용의 MySQL DDL을 `backend/docs/sql/STEPxx_설명.sql`로 남긴다 (팀 공유용, CLAUDE.md 4번)
  3. `backend/docs/schema-changes.md`에 STEP / 테이블 / 컬럼 / 이유 / 영향받는 기능을 누적한다
  4. 시드 데이터는 `backend/docs/sql/STEPxx_seed_*.sql`과, 테스트에서 쓰는 `backend/src/test/resources/` 픽스처로 둔다. 운영 시드를 앱 시작 시 자동으로 넣지 않는다.
- `backend/schema.sql`, `application.yml`, `application.yml.example`, `.gitignore`, `.idea/`, `frontend/`는 수정하지 않는다.
- 다른 담당자 도메인 코드(`friend`, `disposal`, `guardian`, `intake`, `notification`, `product`, `user`의 기존 클래스)는 수정하지 않는다. 읽기만 한다. 예외: `User` 엔티티에 이미 있는 `sensitiveConsent` 필드를 바꾸는 메서드가 필요하면 메서드 추가만 허용한다.
- `global/exception/ErrorCode`에는 새 코드를 **추가만** 한다 (기존 값 수정 금지). 설문은 `S0xx`, 추천은 `R0xx` 접두사를 쓴다.

## 3. 테스트 DB

- 테스트는 실제 MySQL에 의존하지 않는다. H2(테스트 전용, MySQL 모드)로 돌린다.
- `backend/src/test/resources/application.yml`을 만들어 H2 + `ddl-auto: create-drop`으로 설정한다. 메인 설정은 건드리지 않는다.
- 기존 `ApplicationTests`가 MySQL 없이 실패하면, 테스트 설정으로 통과하게 만든다 (테스트 클래스 자체를 지우지 않는다).

## 4. 허용된 새 라이브러리 (사용자 승인 완료, 이 목록 밖은 추가 금지)

| 라이브러리 | 범위 | 쓰는 STEP |
| --- | --- | --- |
| H2 | testRuntimeOnly | 전체 |
| spring-boot-starter-actuator, micrometer-registry-prometheus | implementation | 04~ |
| WireMock | testImplementation (부하 테스트용 standalone 실행은 문서로) | 03~ |
| spring-boot-starter-data-redis | implementation | 08~ |
| Resilience4j (spring-boot3) | implementation | 10 |

- 추가할 때마다 progress의 "결정한 것"에 이름·버전·이유를 적는다. PR에서 팀에 제안할 목록으로 쓴다.
- Redis 서버나 Docker가 없어 테스트를 못 돌리면, 캐시를 인터페이스 뒤에 두고 테스트는 메모리 구현으로 돌린 뒤 "막힌 것"에 기록한다.
- k6가 설치돼 있지 않으면 스크립트만 만들고 실행은 `TODO-live-runs.md`에 넣는다.

## 5. 이 저장소의 코드 규칙

- API 경로는 기존처럼 `/api/v1/...`로 시작한다. STEP 프롬프트의 `/api/...`는 `/api/v1/...`로 바꾼다.
- 응답은 기존 `global/common/ApiResponse`로 감싼다. 예외는 `BusinessException` + `ErrorCode`를 쓴다.
- 현재 사용자 식별: 로그인이 아직 없다. STEP 02 지시대로 `X-User-Id` 헤더 기반 임시 `CurrentUserResolver`를 `global/` 아래에 만들고 `// TEMP: 로그인 연동 시 교체` 주석을 단다.
- 설문 API(STEP 02)는 백엔드만 만든다. 프론트엔드는 건드리지 않는다. 화면 담당(채원)과 맞출 API 명세를 `backend/docs/api/survey-api.md`에 요청·응답 예시와 함께 남긴다. 추천 API도 같은 방식으로 `backend/docs/api/recommendation-api.md`.
- 기존 엔티티는 `@Setter` 없이 생성자 `@Builder` 방식이다. 상태 변경이 필요하면 의미 있는 이름의 메서드를 추가한다.

## 6. STEP 09 알림 시나리오 조정

- `notification` 도메인은 다른 담당자(응진) 영역이다. 알림 테이블·클래스를 수정하지 않는다.
- 알림 일괄 발송은 `domain/recommendation` 아래 부하 시뮬레이션용 코드(예: `recommendation/perf/NotificationSpikeSimulator`)로 만들고, 실제 `notifications` 테이블에는 쓰지 않는다.
- `notifications.dedup_key` 유니크 제약은 적용하지 말고 `backend/docs/sql/STEP09_proposal_notification_dedup.sql`에 **제안**으로만 남긴다.

## 7. 실제 Claude API

- 실제 API 예산: 0달러. 실제 API를 호출하지 않는다.
- LLM은 전부 `FakeLlmClient` / WireMock으로 구현·테스트한다. `evalRun -Pconfirm=true`, `liveTest`는 실행하지 않는다.
- 각 STEP 리포트에 "실제 측정 대기"를 표시하고, 사용자가 직접 실행할 명령을 STEP 순서대로 `backend/docs/perf/TODO-live-runs.md`에 정리한다. 명령마다 실행 위치(`backend` 폴더), 예상 호출 수를 적는다.
- `results.csv`에는 실제 측정값만 넣는다. Fake로 돌린 러너 검증은 label에 `fake`를 붙인다.
- API 키는 `ANTHROPIC_API_KEY` 환경변수로만 읽는다. `.env`나 설정 파일에 키 자리를 만들 때는 예시 파일에만 빈 값으로 둔다.

## 8. 건강 정보

- 권장량·상한·규칙은 `reference/domain-rules.md`에 있는 값만 쓴다. TODO인 값은 비워둔다. 절대 지어내지 않는다.

## 9. Git

- 시작 전 `git status`를 확인한다. 이미 수정돼 있던 `.gitignore`, `.idea/*`는 사용자 작업이므로 **스테이징하지 않는다.** `git add -A`, `git add .`를 쓰지 않고 내가 만든 파일만 경로를 지정해 추가한다.
- 현재 브랜치에서 `feat/recommendation-pipeline` 브랜치를 새로 만들어 작업한다. `main`, `develop`에는 커밋하지 않는다. push하지 않는다.
- 첫 커밋으로 `backend/docs/supplement-recommendation/` 프롬프트 폴더를 `[DOCS] AI 추천 작업 프롬프트 추가` 메시지로 커밋한다.
- STEP 하나가 끝날 때마다(테스트 통과 후) `[FEAT] STEP xx: 제목` 메시지로 커밋한다.

## 10. 진행 방식

- 각 STEP마다 `00_COMMON.md`와 해당 STEP 파일을 읽고 구현한다. "계획을 먼저 보여준다", "커밋은 사용자 요청 시만"은 이 파일 규칙으로 대체한다.
- 다음 STEP을 시작하기 전에 `backend/docs/progress.md`를 다시 읽고 이어서 한다. 대화가 길어져 이전 내용이 잘려도 progress.md와 이 파일을 기준으로 진행한다.
- 프롬프트와 실제 코드가 다르거나 애매하면, 팀 코드를 덜 건드리는 쪽으로 결정하고 진행한다. 결정과 이유를 progress의 "결정한 것"에 적는다.
- 해결 못 하는 문제는 30분 이상 붙잡지 말고, 할 수 있는 데까지 하고 progress의 "막힌 것"에 남긴 뒤 다음 STEP으로 넘어간다.
- 테스트가 실패한 상태로 다음 STEP에 가지 않는다. 고칠 수 없으면 해당 테스트를 `@Disabled`로 두고 이유를 적는다.
- Gradle 자체를 실행할 수 없으면(`./gradlew`, `gradlew.bat` 모두 실패) 테스트로 검증할 방법이 없으므로, 그 시점에서 멈추고 progress에 원인과 출력 메시지를 남긴다.

## 11. AI 활용 기록 (CLAUDE.md 6번)

- STEP이 끝날 때마다 저장소 루트의 `docs/logs/YYYY-MM-DD.md`(실행 날짜) **맨 아래에** 블록을 추가한다. 기존 내용은 절대 수정·삭제하지 않는다.
- 형식은 기존 블록을 따른다: `## 제목 (영준, Claude Code)` + 요청 / 생성·변경 파일 / 검증 / 협의 필요.

## 12. 끝나면

`backend/docs/progress.md` 맨 위에 "밤새 실행 요약"을 추가한다.
1. 완료한 STEP과 커밋 목록
2. 막힌 STEP과 이유
3. 팀과 협의할 것 (설문 API 명세 → 채원, 추가한 라이브러리 목록, 스키마 변경 SQL, 알림 dedup 제안 → 응진)
4. 사용자가 아침에 할 일: 평가셋 검토(`reviewed`), `domain-rules.md`의 TODO 수치 입력, `TODO-live-runs.md`의 실제 API 실행
