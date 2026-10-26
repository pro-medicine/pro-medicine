# [작업 프롬프트] Version 2 — 회원 맞춤 질문 → 정밀 성분 추천 (AI 담당: 영준)

## 0. 브랜치
현재 브랜치 `feat/recommendation-pipeline`에서 새 브랜치를 만들어 작업한다 (V1이 아직 develop에 반영되지 않았기 때문).
```
git switch feat/recommendation-pipeline
git switch -c feat/personalized-recommendation
```
커밋되지 않은 변경(.gitignore, frontend/package.json, frontend/pnpm, frontend/pnpm-lock.yaml)은 이 작업과 무관하니 건드리지 말고 커밋에도 넣지 않는다.

## 1. 먼저 읽을 것
- **`docs/design/V2-personalized-recommendation.md` (이번 작업의 기준 설계. 전부 읽기)**
- 루트 `CLAUDE.md`, `docs/ai-roadmap.md`
- 기존 V1 백엔드: `backend/src/main/java/com/ucamp/app/domain/recommendation/**`, `domain/survey/**`, `domain/health/**`, `infra/llm/**`
- 기존 V1 화면: `frontend/src/recommendation/**`, `frontend/src/api/recommendationApi.js`
- `backend/docs/api/recommendation-api.md`, `survey-api.md`, `backend/docs/llm-safeguards.md`

읽은 뒤 **구현 계획을 먼저 보여주고 내 승인을 받은 다음** 코드를 쓴다. 계획에는 새로 만들/고칠 파일 목록, 설계 문서와 다르게 해야 할 점(있다면 이유), 12장 "구현 전 확인할 점"에 대한 질문을 포함한다.

## 2. 구현 순서 (단계마다 테스트 통과 후 다음 단계)
1. **DB**: 설계 8장 — 엔티티(`AiQuestionSession`, `AiQuestionTurn`), `AiRecommendation`에 `session` 추가·`response` NULL 허용, `STEP14_personalized_questions.sql`(다시 실행해도 안전), `schema.sql`, `schema-changes.md`, README 순서 안내(STEP13 → STEP14)
2. **모드 판단 API**: 설계 4-1, D2 기준
3. **주제 계획**: 설계 5장 `TopicPlanner` (순수 클래스, LLM 호출 없음) + 단위 테스트
4. **질문 생성**: 설계 6장 — 프롬프트 파일(`resources/prompts/pq/`), 출력 스키마, `PersonalizedQuestionValidator`, 1회 재요청, `call_tag` PQ_QUESTION / PQ_QUESTION_REPAIR, Fake LLM 확장
5. **회차 API**: 설계 4-2 ~ 4-4, 임신 고정 질문(19~50세 여성), 답 검증, 그만하기, 진행 중 회차는 새로 시작 시 ABANDONED
6. **정밀 추천**: 설계 7장 — 프로필 기반 조립, `personalizedAnswers`, `InputFormatter` 블록, 기존 파이프라인 재사용, `session_id`로 저장, 같은 회차 재호출 시 저장된 결과 반환
7. **모니터링**: 설계 9장 — V1 대비 diff 저장, 메트릭
8. **90일 삭제 스케줄러**: 설계 8-4
9. **API 문서**: `backend/docs/api/personalized-api.md` 새로 작성 (설계 4장 기준, 실제 구현과 일치)
10. **화면**: 설계 10장 — 기존 프론트 구조·코드 형식(`docs/prompts/V1-recommendation-screen.md`의 2-1 "코드 스타일")을 그대로 따른다. `App.jsx`는 홈 배너 분기에 필요한 최소 변경만

## 3. 지켜야 할 것
- **안전 규칙은 프로필 기준 그대로**: 맞춤 답변이 규칙(사전 금지·사후 차단·용량 계산)을 바꾸거나 우회하지 않게 한다
- 맞춤 답변은 프로필(`health_profiles`, `user_conditions`, `user_medications`)에 저장하지 않는다
- 기존 V1 동작·응답 형식·골든 테스트를 바꾸지 않는다. `response`가 NULL인 추천 행이 생겨도 V1 조회·알림 진입 재사용이 깨지지 않게 한다
- 실제 OpenAI 호출은 하지 않는다 (Fake LLM으로 테스트). 실제 호출이 필요하면 먼저 물어본다
- 새 라이브러리 추가 금지. 다른 팀원 폴더·테이블 수정 금지
- 프론트 빌드는 `npm run build` (팀은 npm 사용)

## 4. 완료 기준
- `cd backend && .\gradlew.bat test` — 기존에 실패하던 `IntakeScheduleRepositoryTest` 6개 외에는 모두 통과
- 설계 11장의 테스트 모두 작성·통과
- 로컬 MySQL에서: STEP14 실행 → 서버 실행(Fake LLM) → 모드 판단 → 회차 시작 → 답변 → 그만하기 또는 끝까지 → 정밀 추천 저장 확인 (`ai_recommendations.session_id` 있음, `response_id` NULL)
- `cd frontend && npm run build` 성공, 회원/비회원 배너 분기와 V2 질문 화면 수동 확인

## 5. 보고
- 단계별 변경 파일, 구현 내용, 테스트 결과, 설계와 다르게 한 점과 이유, 협의가 필요한 점
- `docs/logs/YYYY-MM-DD.md` 맨 아래에 기록 추가 (기존 내용 수정 금지)
- 커밋은 단계별로 나눠서 하되 push는 하지 않는다
