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
