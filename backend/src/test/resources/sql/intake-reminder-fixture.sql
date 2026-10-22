-- IntakeReminderServiceTest 전용 테스트 데이터
-- 예시 데이터(schema.sql)와 겹치지 않도록 9000번대 id와 별도 이메일·닉네임을 사용한다.
-- 테스트 트랜잭션 안에서 실행되어 테스트가 끝나면 롤백된다.

INSERT INTO users (user_id, email, password_hash, nickname, share_intake_status, sensitive_consent, created_at, updated_at) VALUES
    (9001, 'reminder-test-9001@example.com', 'test-hash', '알림테스트9001', FALSE, FALSE, NOW(), NOW()),
    (9002, 'reminder-test-9002@example.com', 'test-hash', '알림테스트9002', FALSE, FALSE, NOW(), NOW());

INSERT INTO user_products (user_product_id, user_id, catalog_product_id, product_kind, manual_product_name, registered_via, start_date, is_active, created_at) VALUES
    (9001, 9001, NULL, 'SUPPLEMENT', '테스트 비타민D', 'MANUAL', '2026-01-01', TRUE, NOW()),
    (9002, 9002, NULL, 'SUPPLEMENT', '테스트 오메가3', 'MANUAL', '2026-01-01', TRUE, NOW()),
    (9003, 9002, NULL, 'SUPPLEMENT', '테스트 유산균',  'MANUAL', '2026-01-01', TRUE, NOW()),
    (9004, 9002, NULL, 'MEDICINE',   '테스트 혈압약',  'MANUAL', '2026-01-01', TRUE, NOW());

-- 9001: 사용자 9001 점심 12:30
-- 9002~9004: 사용자 9002 아침 08:30 (3개 → 알림 1개로 묶여야 함)
-- 9005: 사용자 9001 저녁 23:59 (자정을 넘는 조회 범위 테스트용)
INSERT INTO intake_schedules (schedule_id, user_product_id, meal_slot, meal_relation, remind_at, is_enabled, created_at) VALUES
    (9001, 9001, 'LUNCH',     'AFTER', '12:30:00', TRUE, NOW()),
    (9002, 9002, 'BREAKFAST', 'AFTER', '08:30:00', TRUE, NOW()),
    (9003, 9003, 'BREAKFAST', 'AFTER', '08:30:00', TRUE, NOW()),
    (9004, 9004, 'BREAKFAST', 'AFTER', '08:30:00', TRUE, NOW()),
    (9005, 9001, 'DINNER',    'AFTER', '23:59:00', TRUE, NOW());
