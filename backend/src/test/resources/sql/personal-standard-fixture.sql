-- 사람별 권장량 테스트 데이터 (9700번대 id, 테스트 트랜잭션이 끝나면 롤백)
-- 9701: 여성 1996년생(2026년 30세), v2 맞춤 질문에서 임신 중 / 9702: 설문 정보 없음 / 9703: 남성 1970년생(56세) 흡연
INSERT INTO users (user_id, email, password_hash, nickname, share_intake_status, sensitive_consent, created_at, updated_at, last_active_at) VALUES
    (9701, 'standard-test-9701@example.com', 'test-hash', '기준테스트9701', FALSE, TRUE, NOW(), NOW(), NOW()),
    (9702, 'standard-test-9702@example.com', 'test-hash', '기준테스트9702', FALSE, FALSE, NOW(), NOW(), NOW()),
    (9703, 'standard-test-9703@example.com', 'test-hash', '기준테스트9703', FALSE, FALSE, NOW(), NOW(), NOW());

INSERT INTO health_profiles (user_id, birth_year, gender, smoking, updated_at) VALUES
    (9701, 1996, 'F', 'NONE', NOW()),
    (9703, 1970, 'M', 'CURRENT', NOW());

INSERT INTO ai_question_sessions (session_id, user_id, status, pregnancy, question_count, stopped_by_user, started_at) VALUES
    (9701, 9701, 'DONE', 'PREGNANT', 0, FALSE, NOW());
