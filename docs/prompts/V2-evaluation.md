# [작업 프롬프트] V2 맞춤 질문 성능·품질 측정 (AI 담당: 영준)

## 0. 전제
- 브랜치: `feat/personalized-recommendation` (V2 구현이 끝난 상태에서 진행)
- V2 백엔드(회차 API, 질문 생성, 정밀 추천)가 동작하고 테스트가 통과해야 한다. 아니면 멈추고 보고

## 0-1. 측정 코드는 저장소에 올리지 않는다 (중요)
커밋 e22b843에서 측정 자료와 평가 러너를 저장소에서 뺐다. 파일은 로컬에만 있고 `.git/info/exclude`에 등록돼 있다.
- 로컬 전용 경로: `backend/src/test/java/com/ucamp/app/eval/`, `backend/src/test/resources/eval/`, `backend/docs/perf/`, `backend/perf/`
- V2 측정 코드·페르소나·결과·리포트도 **전부 위 로컬 전용 경로에 둔다** (`eval` 패키지, `resources/eval/`, `docs/perf/`)
- `v2EvalRun` Gradle 태스크는 추적되는 `build.gradle`에 직접 쓰지 않는다. 로컬 전용 파일 `backend/eval.gradle`에 정의하고 `.git/info/exclude`에 `/backend/eval.gradle`를 추가한다. `build.gradle`에는 아래 한 줄만 넣는다 (파일이 없는 팀원 환경에서는 아무 일도 없음)
  ```groovy
  if (file('eval.gradle').exists()) { apply from: 'eval.gradle' }
  ```
- OpenAI 키 전달은 `build.gradle`의 기존 `passOpenAiApiKey` 방식을 `eval.gradle`에서 재사용한다
- 운영 코드에 측정용 훅을 넣어야 하면, 훅은 저장소에 올라가도 되는 형태(기본 꺼짐, 측정 코드에 의존하지 않음)로 만든다
- 커밋 전 `git status`로 측정 파일이 추적 대상에 들어가지 않았는지 확인한다

## 1. 먼저 읽을 것
- **`docs/design/V2-evaluation.md` (이번 작업의 기준 설계. 전부 읽기)**
- `docs/design/V2-personalized-recommendation.md`
- V1 측정: `backend/docs/supplement-recommendation/reference/measurement.md`, `backend/src/test/java/com/ucamp/app/eval/**`, `backend/src/test/resources/eval/cases.json`, `backend/docs/perf/results.csv`, `backend/docs/perf/pricing.md`, `backend/docs/perf/live-performance-report.md`
- `backend/build.gradle` (커밋 e22b843에서 `evalRun` 태스크가 제거됨 — 아래 0-1 참고)

**나는 자리에 없다 (무인 실행).** 승인을 기다리지 말고 끝까지 진행한다.
- 계획은 `docs/logs/YYYY-MM-DD.md`에 먼저 적고 바로 구현한다
- 애매한 결정은 설계 문서에 가장 가까운 쪽으로 정하고, 정한 내용과 이유를 로그와 리포트의 "가정" 절에 남긴다
- 되돌리기 어려운 일(push, DB 삭제, 다른 팀원 파일 수정, 비용 상한 초과)은 하지 않는다

## 2. 구현
1. `backend/src/test/resources/eval/v2-personas.json`: 설계 3장 A1~A5, 5장 형식. 기대값은 `domain-rules.md` 규칙에서만 도출, `reviewed: false`. 규칙에 없는 기대값은 넣지 말고 보고
2. 가상 사용자: 설계 2장 kind 기반 답변 정책 (순수 클래스 + 단위 테스트)
3. V2 평가 러너: V1 `EvalRunner` 구조를 재사용해 회차를 처음부터 끝까지 자동 진행. 서비스 계층을 직접 호출 (HTTP 불필요)
   - 질문마다 검증 **전** LLM 원본과 검증 실패 이유도 기록 (원본 금지어 비율, 실패 이유 분포용). 필요하면 V2 질문 생성기에 측정용 훅을 추가하되 운영 동작은 바꾸지 않는다
   - 정밀 추천의 규칙 적용 전 원본 성분도 기록 (원본 위반율용) — V1 러너가 하는 방식 그대로
4. 지표 계산: 설계 4장 전부. 일관성 지표의 문장 유사도는 문자 2-gram Jaccard (외부 API·라이브러리 없이)
5. Gradle 태스크 `v2EvalRun` (`-Pexperiment=A|B`, `-Pfake=true`, `-Pconfirm=true`): confirm 없으면 예상 호출 수·비용만 출력하고 종료, 누적 비용 $2 초과 시 중단
6. 결과 파일: 설계 7장 (`runs/v2-*.json`, `results-v2.csv`)
7. 리포트 템플릿: `backend/docs/perf/V2-eval-report.md` (measurement.md 2장 형식). Fake 결과로 채워서 형식 확인

## 3. 실행 순서 (멈추지 않고 끝까지)
1. Fake로 A, B 실행 → 러너·지표·파일 형식 확인. 문제가 있으면 고친다
2. 페르소나 기대값은 `reviewed: false` 그대로 두고 **실제 API로 A → B 순서로 실행** (`-Pconfirm=true`). 리포트에 "기대값은 사람 검토 전" 이라고 명시
3. 실제 실행 결과로 `V2-eval-report.md` 작성. 설계 8장의 "반드시 0/100%" 항목이 어긋나면 리포트 맨 위 "발견한 문제"에 따로 적는다 (고치지 말고 기록만. 원인 추정은 적어도 됨)

## 4. 지켜야 할 것
- 실제 API 비용 상한: **A·B 합계 $2**. 넘으면 즉시 중단하고 그때까지의 결과로 리포트 작성
- OpenAI 키가 실행 환경에 없거나 실제 호출이 실패하면, Fake 결과까지만 남기고 리포트에 이유를 적은 뒤 종료 (키를 찾으려고 다른 파일을 뒤지거나 출력하지 않는다)
- 운영 코드의 동작·응답 형식을 바꾸지 않는다 (측정용 훅은 기본 꺼짐)
- 새 라이브러리 추가 금지, 다른 팀원 폴더·테이블 수정 금지
- 숫자는 실행 결과 파일에서 나온 값만 쓰고 출처를 단다
- push 금지

## 5. 보고
- 끝나면 한 번에 정리: 변경 파일, 구현 내용, Fake·실제 실행 결과 요약, 실제 사용 비용, "반드시 0/100%" 위반 여부, 사람이 확인해야 할 기대값과 가정 목록
- `docs/logs/YYYY-MM-DD.md` 맨 아래에 기록 추가 (기존 내용 수정 금지)
- 커밋은 나눠서 하되 push는 하지 않는다
