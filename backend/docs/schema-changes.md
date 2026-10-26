# 스키마 변경 기록 (ERD 대비)

기준 ERD: `backend/docs/supplement-recommendation/reference/erd.sql`.
Flyway를 쓰지 않는다. 엔티티에 반영하면 로컬 DB는 `ddl-auto: update`로 바뀌고, 팀 공유용 MySQL DDL은 `backend/docs/sql/STEPxx_*.sql`에 둔다.

| STEP | 테이블 | 컬럼 | 이유 | 영향받는 기능 | SQL |
| --- | --- | --- | --- | --- | --- |
| 02 | (변경 없음) | - | 설문 v1·약물 시드만 추가 | 설문 | `docs/sql/STEP02_seed_survey_v1.sql`, `docs/sql/STEP02_seed_drugs.sql` |
| 03 | llm_call_logs (새 테이블) | llm_call_log_id, response_id, eval_run_id, pipeline_version, call_tag, model, prompt_version, input/output/cache_read/cache_creation_tokens, latency_ms, attempt_no, outcome, http_status, created_at | 모든 LLM 호출의 비용·지연 원본 기록 | AI 추천 (다른 기능 영향 없음) | `docs/sql/STEP03_llm_call_logs.sql` |
| 06 | ingredient_standards (새 테이블) | standard_id, ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id | 용량을 LLM 대신 기준표로 계산 | AI 추천 | `docs/sql/STEP06_ingredient_standards.sql`, 시드 `STEP06_seed_ingredient_standards.sql` |
| 06 | ai_recommendation_items (컬럼 추가) | recommended_amount, current_intake, intake_status, unit, dose_note | 계산한 용량과 근거 저장 | AI 추천 결과 조회 | `docs/sql/STEP06_ingredient_standards.sql` |
| 07 | recommendation_rules (새 테이블) | rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active | 안전 판단을 규칙 테이블로 처리 | AI 추천 | `docs/sql/STEP07_recommendation_rules.sql`, 시드 `STEP07_seed_recommendation_rules.sql` |
| 07 | ai_recommendation_items (컬럼 추가) | excluded, applied_rule_ids, warnings | 제외된 성분도 저장해 LLM 원래 출력 보존, 경고 표시 | AI 추천 결과 조회 | `docs/sql/STEP07_recommendation_rules.sql` |
| 09 | ai_recommendations (컬럼 추가) | status(DEFAULT DONE), started_at, finished_at, error_code | 비동기(큐) 처리 상태 | AI 추천 생성·조회 | `docs/sql/STEP09_ai_recommendations_status.sql` |
| 09 | notifications (제안만, 미적용) | dedup_key UNIQUE | 같은 날 같은 알림 중복 생성 방지 | 알림 (응진 담당) | `docs/sql/STEP09_proposal_notification_dedup.sql` |
| 12 | (변경 없음) | - | 약 이름을 모르는 사용자용 계열 모름 약물 8개(D-U01~D-U08)·규칙 12개(R-U*) 시드 추가 | 설문 복용 약 검색, AI 추천 규칙 | 새 DB: `STEP02_seed_drugs.sql`·`STEP07_seed_recommendation_rules.sql`에 포함 / 기존 DB: `docs/sql/STEP12_seed_unknown_drug_classes.sql` |
| 13 | surveys, survey_questions, question_options, survey_responses, survey_answers, user_conditions, user_medications, ai_recommendations, ai_recommendation_items (PK 변경) / llm_call_logs, ingredient_standards, recommendation_rules (없으면 생성) / drugs·설문 (데이터) | 각 PK에 AUTO_INCREMENT, STEP06·07·09 컬럼, drug_class BIGUANIDE→METFORMIN·ANTICOAGULANT→WARFARIN | schema.sql을 엔티티·시드와 맞춤 (IDENTITY INSERT 실패, AI 테이블·컬럼 누락, 예시 데이터와 STEP02 시드 PK 충돌, 규칙 코드 불일치). schema.sql 예시 설문·약물을 STEP02 시드로 교체. 기존 DB용 셋업 SQL은 옛 예시 설문·응답·추천 삭제, 옛 약물 ID를 쓰는 복용 약은 새 ID(D-001 등)로 옮김 | 설문 제출, AI 추천 저장, 약물 검색 | 새 DB: `schema.sql` / 기존 DB: `docs/sql/STEP13_ai_survey_setup.sql` (이 파일 하나, 재실행 안전) |
