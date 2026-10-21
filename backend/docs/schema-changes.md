# 스키마 변경 기록 (ERD 대비)

기준 ERD: `backend/docs/supplement-recommendation/reference/erd.sql`.
Flyway를 쓰지 않는다. 엔티티에 반영하면 로컬 DB는 `ddl-auto: update`로 바뀌고, 팀 공유용 MySQL DDL은 `backend/docs/sql/STEPxx_*.sql`에 둔다.

| STEP | 테이블 | 컬럼 | 이유 | 영향받는 기능 | SQL |
| --- | --- | --- | --- | --- | --- |
| 02 | (변경 없음) | - | 설문 v1·약물 시드만 추가 | 설문 | `docs/sql/STEP02_seed_survey_v1.sql`, `docs/sql/STEP02_seed_drugs.sql` |
| 03 | llm_call_logs (새 테이블) | llm_call_log_id, response_id, eval_run_id, pipeline_version, call_tag, model, prompt_version, input/output/cache_read/cache_creation_tokens, latency_ms, attempt_no, outcome, http_status, created_at | 모든 LLM 호출의 비용·지연 원본 기록 | AI 추천 (다른 기능 영향 없음) | `docs/sql/STEP03_llm_call_logs.sql` |
