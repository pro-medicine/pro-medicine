# STEP02 리포트 - 설문 API (시작 · 동의 · 제출)

## 바꾼 것 (Before → After)

- 설문 시작·제출 API를 새로 만들었다 (LLM 없음). 이 STEP의 Before/After는 **설문 문항 선택지 조회 방식**이다.
  - Before: 문항 14개를 읽은 뒤 문항마다 선택지를 따로 조회 (`findByQuestion_QuestionId...`, N+1)
  - After: 설문 단위로 선택지를 한 번에 조회해서 문항별로 나눔 (`findByQuestion_Survey_SurveyId...`)
- 설문 시작과 제출이 같은 `SurveyQueryService.loadQuestions()`를 쓰므로 둘 다 영향을 받는다.

## 측정 조건

| 항목 | 값 |
| --- | --- |
| 환경 | 로컬 JVM(Java 21), H2 인메모리(MySQL 모드), MockMvc (HTTP 소켓 없음) |
| 데이터 | 설문 v1 (문항 14개, 선택지 37개), 회원 1명(동의함, 프로필·질환·복용 약 있음) |
| 호출 | API마다 220회, 앞 20회 워밍업 제외 → 200회로 계산 (nearest-rank 백분위) |
| SQL 수 | Hibernate statistics `getPrepareStatementCount()`, 요청 1회 기준 |
| 실행 명령 | `cd backend` → `./gradlew perfTest -PperfLabel=before` / `-PperfLabel=after` |
| 원본 출력 | [`runs/STEP02-survey-before.json`](runs/STEP02-survey-before.json), [`runs/STEP02-survey-after.json`](runs/STEP02-survey-after.json) |
| 날짜 | 2026-10-21 |

실제 MySQL·네트워크가 없는 조건이라 절대값은 운영 환경과 다르다. 같은 조건에서 Before/After를 비교하는 용도로만 쓴다.

## 결과

### SQL 수 (요청 1회)

| API | Before | After |
| --- | ---: | ---: |
| 설문 시작 (회원, 동의함) | 20 | 7 |
| 설문 시작 (비회원) | 16 | 3 |
| 설문 제출 (회원, 14문항) | 41 | 28 |

After 기준 설문 시작(회원) 7개 = 활성 설문 1 + 사용자 1 + 프로필·질환·복용 약 3 + 문항 1 + 선택지 1.

### 지연 (ms, n=200)

| API | Before p50 | Before p95 | After p50 | After p95 |
| --- | ---: | ---: | ---: | ---: |
| 설문 시작 (회원) | 25.453 | 49.476 | 15.697 | 25.290 |
| 설문 시작 (비회원) | 17.123 | 30.109 | 8.481 | 15.286 |
| 설문 제출 (회원, 14문항) | 45.805 | 70.227 | 23.087 | 41.951 |
| 설문 제출 (비회원, 11문항) | 22.025 | 39.494 | 8.922 | 17.571 |

## 해석

- 설문 시작 SQL이 문항 수(14)만큼 줄었다. 문항이 늘어도 After는 SQL 수가 그대로다.
- 지연도 같이 줄었지만, 두 측정은 같은 JVM을 다시 띄워 따로 실행했고 H2 인메모리라 SQL 1개 비용이 작다. 지연 수치보다 SQL 수 감소가 더 확실한 근거다.
- 제출(회원)은 After도 SQL 28개다. 답 13행 + 질환·복용 약 삭제/추가가 행마다 INSERT되기 때문이다 (`IDENTITY` 키라 JDBC 배치가 안 됨). 지금 규모(문항 14개)에서는 문제로 보지 않아 그대로 둔다.

## 한계 / 다음에 할 것

- MockMvc라 Tomcat·네트워크·직렬화 일부가 빠져 있다. 실제 서버 기준 수치는 k6 도입(STEP 08) 이후 측정한다.
- MySQL 실측은 하지 않았다 (테스트는 H2). 필요하면 같은 테스트를 MySQL 프로필로 돌릴 수 있게 STEP 08 이후 검토.
