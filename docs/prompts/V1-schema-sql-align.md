# [작업 프롬프트] V1 — schema.sql의 설문·AI·약물 부분을 엔티티·시드와 맞추기 (AI 담당: 영준)

## 0. 먼저 읽을 것
- 루트 `CLAUDE.md` (공통 파일은 협의 후 수정, DB 변경 시 변경 SQL과 영향 기능 공유)
- `backend/schema.sql` (팀 공용 DDL + 예시 데이터. 다른 팀원도 계속 수정하는 파일)
- 엔티티: `backend/src/main/java/com/ucamp/app/domain/{survey,recommendation,health}/entity/*.java`
- 시드: `backend/docs/sql/STEP02_seed_survey_v1.sql`, `STEP02_seed_drugs.sql`, `STEP06_*`, `STEP07_*`, `STEP12_seed_unknown_drug_classes.sql`
- 기존 변경 SQL: `backend/docs/sql/STEP03_llm_call_logs.sql`, `STEP06_ingredient_standards.sql`, `STEP07_recommendation_rules.sql`, `STEP09_ai_recommendations_status.sql`
- `backend/docs/schema-changes.md`, `backend/README.md`(시드 절차), `backend/docs/api/survey-api.md`(설문 코드 값)

코드를 고치기 전에 위 파일을 읽고, 아래 "문제"가 실제로 맞는지 먼저 확인해서 알려줘. 다르면 멈추고 보고.

## 1. 배경
- `feat/recommendation-pipeline` 브랜치를 develop에 머지하기 전 정리 작업이다.
- 팀원들은 `schema.sql`로 로컬 DB를 만들고, 서버는 `ddl-auto: update`로 돌린다.
- `ddl-auto: update`는 없는 테이블·컬럼은 추가하지만, **기존 PK에 AUTO_INCREMENT를 붙이거나 컬럼을 바꾸지는 않는다.**

## 2. 고칠 문제 (설문·AI·약물·건강 프로필 범위만)

### 문제 1. AUTO_INCREMENT 누락 → JPA INSERT 실패
`schema.sql`에서 AUTO_INCREMENT는 `notifications`, `follows`에만 있다. 아래 엔티티는 모두 `GenerationType.IDENTITY`라서, 이 파일로 만든 DB에서는 설문 제출·추천 저장 시 `Field '...id' doesn't have a default value`로 실패할 수 있다.
- 대상 테이블: `surveys`, `survey_questions`, `question_options`, `survey_responses`, `survey_answers`, `user_conditions`, `user_medications`, `ai_recommendations`, `ai_recommendation_items`
- 할 일: 각 테이블 PK 컬럼에 `AUTO_INCREMENT` 추가 (파일의 기존 작성 방식 — `CREATE TABLE` 후 `ALTER TABLE ... PRIMARY KEY` — 은 유지하고, 컬럼 정의에 `AUTO_INCREMENT`를 붙이거나 PK 추가 뒤 `MODIFY`로 붙이는 방식 중 MySQL에서 실제로 동작하는 쪽으로)
- `drugs`는 PK가 문자열이라 대상 아님. `health_profiles`는 PK가 `user_id`라 대상 아님.
- **다른 팀원 테이블(users, user_products, intake_*, catalog_products 등)은 건드리지 않는다.** 같은 문제가 보이면 보고만 한다.

### 문제 2. AI 테이블·컬럼 누락
`schema.sql`에 없는 것을 STEP SQL 그대로 옮겨서 파일만으로 완전한 구조가 되게 한다.
- 테이블 추가: `llm_call_logs`(STEP03), `ingredient_standards`(STEP06), `recommendation_rules`(STEP07)
- 컬럼 추가: `ai_recommendation_items`(STEP06·07 컬럼), `ai_recommendations`(STEP09 상태 컬럼)
- 엔티티의 `@Column` 정의(길이, NULL 여부, 기본값)와 다르면 **엔티티 기준**으로 맞춘다.

### 문제 3. 예시 데이터와 STEP02 시드 충돌
- `schema.sql` 예시: 설문 1번(문항 4개 `CONCERN`/`DIET`/`EXERCISE`/`SMOKING`), 선택지 1~15, 약물 `AMLODIPINE`/`METFORMIN`/`ATORVASTATIN`/`WARFARIN`
- STEP02 시드: 설문 1번(문항 14개 `Q-GENDER`~`Q-SUPPLEMENTS`), 선택지 1~37, 약물 `D-001`~`D-010` + `D-U01`~`D-U08`
- 두 개를 같이 실행하면 PK 중복, `schema.sql` 예시만 쓰면 추천 코드가 기대하는 `Q-*` 문항 코드가 없다.
- 할 일:
  - `schema.sql`의 설문(`surveys`, `survey_questions`, `question_options`)과 `drugs` 예시 INSERT를 **STEP02 시드 내용으로 교체**한다 (같은 ID·코드·문구).
  - 약물 예시 중 시드에 없는 것(암로디핀, 아토르바스타틴)은 시드 형식(`D-011`, `D-012` …)으로 추가하고, 계열 코드는 규칙과 맞지 않아도 되는 값(`CALCIUM_CHANNEL_BLOCKER`, `STATIN`)을 그대로 쓴다. 같은 내용을 `STEP02_seed_drugs.sql`과 `src/test/resources/sql/seed-drugs.sql`에도 추가한다.
  - `user_medications` 예시의 `drug_id`를 새 ID로 바꾼다 (메트포르민 → `D-001`, 와파린을 쓰는 예시가 있으면 `D-007`).
  - `survey_responses`·`survey_answers` 예시를 새 문항·선택지 ID에 맞게 다시 작성한다 (회원 1명 + 비회원 1명, 문항 몇 개만).
  - `ai_recommendations`·`ai_recommendation_items` 예시: `ingredient_name`을 **성분 코드**로 (`'비타민 B군'` → `VITAMIN_B12`, `'마그네슘'` → `MAGNESIUM`), STEP06·07·09 컬럼은 기본값으로.

### 문제 4. 약물 계열·건강 프로필 코드 불일치 (규칙이 조용히 빠짐)
- 약물 계열: 메트포르민 `BIGUANIDE` → `METFORMIN`, 와파린 `ANTICOAGULANT` → `WARFARIN` (규칙 `recommendation_rules.condition_code`와 같아야 함)
- `health_profiles` 예시 값을 설문 `value_code`와 맞춘다 (`survey-api.md` 5번 표 기준)
  - gender `FEMALE`/`MALE` → `F`/`M`
  - smoking `NONE`/`FORMER` → `NONE`/`HALF_PACK`/`ONE_PACK`/`TWO_PACK_PLUS` 중 (FORMER는 `NONE`)
  - drinking `OCCASIONAL`/`FREQUENT` → `LOW`/`HIGH`
  - caffeine `MEDIUM` → `LOW` 또는 `HIGH`
  - diet_type `EATING_OUT`/`BALANCED`/`LOW_VEGGIE` → `OMNI`/`VEGETARIAN`/`VEGAN` 중 (대부분 `OMNI`)
  - sun_exposure `MEDIUM`/`HIGH` → `NORMAL`
- `user_conditions.condition_name`: 서비스가 실제로 어떤 값을 저장하는지(한글 '고혈압' vs 코드 `HYPERTENSION`) 코드를 확인하고 예시를 그 형식으로 맞춘다.
- 이 값들을 다른 팀원 화면(홈, 마이페이지 등)이 쓰는지 grep으로 확인하고, 쓰고 있으면 **바꾸지 말고 보고**한다.

## 3. 하지 말 것
- 다른 팀원 테이블 구조·예시 데이터 수정 (users, follows, notifications, intake_*, user_products, catalog_products, disposal_locations, guardian_settings)
- 엔티티·서비스 코드 수정 (이번 작업은 SQL·문서만)
- 파일 맨 위 `DROP TABLE` 블록 구조 변경

## 4. 같이 고칠 문서
- `backend/README.md` 시드 절차: `schema.sql`에 설문·약물이 들어가므로 "새 DB = `schema.sql` 실행 → STEP06·STEP07 시드" 로 정리. STEP02 시드는 "schema.sql을 쓰지 않는 경우에만"으로 표시.
- `backend/docs/schema-changes.md`에 이번 변경 한 줄 추가 (STEP 13, 영향 기능: 설문 제출·AI 추천 저장·약물 검색)
- 기존 팀원 DB용 **한 번에 실행하는 셋업 SQL** `backend/docs/sql/STEP13_ai_survey_setup.sql` 작성. 팀원은 이 파일 하나만 실행하면 설문·성분 추천을 쓸 수 있어야 한다. 내용 순서:
  1. **구조**: AI 테이블이 없으면 만들기 (`CREATE TABLE IF NOT EXISTS` — `llm_call_logs`, `ingredient_standards`, `recommendation_rules`), 빠진 컬럼 추가, 문제 1의 대상 테이블 PK에 `AUTO_INCREMENT` (`ALTER TABLE ... MODIFY ...`). 서버를 먼저 켜지 않아도 동작해야 한다
  2. **옛 예시 삭제**: `schema.sql`이 넣은 설문·설문 응답·예시 추천·약물·복용 약 예시 (FK 순서: `ai_recommendation_items` → `ai_recommendations` → `survey_answers` → `survey_responses` → `question_options` → `survey_questions` → `surveys`, `user_medications` 예시 → `drugs`). **다른 팀원 테이블 데이터는 지우지 않는다**
  3. **데이터**: STEP02 설문·약물(이름 모름 포함), STEP06 기준표, STEP07 규칙 시드 내용 그대로 + 약물 계열 코드 UPDATE
  4. **다시 실행해도 안전하게**: `INSERT IGNORE`·`IF NOT EXISTS`·조건부 `DELETE` 사용. 두 번 실행해도 오류·중복이 없어야 한다
  - 파일 맨 위 주석에 실행 위치(각자 PC의 MySQL, Workbench 가능)와 "STEP02·06·07·12 시드를 따로 실행할 필요 없음"을 적는다
  - MySQL Workbench에서도 돌아가야 하므로 `SOURCE` 명령은 쓰지 않는다 (내용을 직접 포함)
  - 시드 파일과 내용이 겹치므로, 파일 맨 위에 "STEP02·06·07·12 시드를 바꾸면 이 파일도 같이 고칠 것"이라고 적는다

## 5. 검증 (영준 PC 터미널, 로컬 MySQL)
1. 새 DB에서 처음부터:
   ```
   mysql -u root -p -e "DROP DATABASE IF EXISTS yakssok_check; CREATE DATABASE yakssok_check CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
   ```
   `schema.sql`의 `USE yakssok;`를 임시로 바꾸지 말고, `mysql -u root -p yakssok_check < backend/schema.sql`이 동작하는지 확인 (USE 문 때문에 안 되면 방법을 보고)
2. STEP06·STEP07 시드 실행 → 오류 없어야 함
3. 백엔드를 이 DB로 실행 (`MYSQL_URL`만 바꿔서) → 부팅 시 ddl 오류 없어야 함
4. API로 확인: `GET /api/v1/surveys/active` → 14문항, `POST /surveys/1/responses`(회원·비회원 각 1회) → responseId, `POST /api/v1/recommendations` (Fake LLM 프로필) → 저장 성공
5. `GET /api/v1/drugs?keyword=혈압` → "혈압약 (이름 모름)" 포함
6. `cd backend && .\gradlew.bat test` 통과 (H2 테스트는 영향 없어야 함)
7. **STEP13 셋업 SQL 검증**: 현재 develop의 `schema.sql`(수정 전 버전, `git show origin/develop:backend/schema.sql`)로 만든 DB에 STEP13을 실행 → 4번 API 확인이 그대로 통과해야 함. STEP13을 두 번 연속 실행해도 오류가 없어야 함. 다른 팀원 테이블(users, follows, notifications 등)의 행 수가 실행 전후로 같아야 함

## 6. 보고
- 변경 파일, 각 문제별로 바꾼 내용, 검증 결과(명령과 출력 요약), 다른 팀원과 협의할 점(예시 데이터를 쓰는 화면이 있었는지)
- `docs/logs/YYYY-MM-DD.md` 맨 아래에 기록 추가 (기존 내용 수정 금지)
- 커밋은 하지 말고 변경만 남겨 둘 것
