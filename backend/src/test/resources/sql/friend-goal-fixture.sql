-- FriendGoalNotificationServiceTest 전용 테스트 데이터
-- 예시 데이터(schema.sql)·다른 테스트 데이터와 겹치지 않도록 9300번대 id를 사용한다.
-- 테스트 트랜잭션 안에서 실행되어 테스트가 끝나면 롤백된다.

-- 9301: 복용 현황 공유 O / 9305: 복용 현황 공유 X / 나머지: 팔로워
INSERT INTO users (user_id, email, password_hash, nickname, share_intake_status, sensitive_consent, created_at, updated_at, last_active_at) VALUES
    (9301, 'goal-test-9301@example.com', 'test-hash', '목표테스트9301', TRUE,  FALSE, NOW(), NOW(), NOW()),
    (9302, 'goal-test-9302@example.com', 'test-hash', '목표테스트9302', TRUE,  FALSE, NOW(), NOW(), NOW()),
    (9303, 'goal-test-9303@example.com', 'test-hash', '목표테스트9303', TRUE,  FALSE, NOW(), NOW(), NOW()),
    (9304, 'goal-test-9304@example.com', 'test-hash', '목표테스트9304', TRUE,  FALSE, NOW(), NOW(), NOW()),
    (9305, 'goal-test-9305@example.com', 'test-hash', '목표테스트9305', FALSE, FALSE, NOW(), NOW(), NOW()),
    (9306, 'goal-test-9306@example.com', 'test-hash', '목표테스트9306', TRUE,  FALSE, NOW(), NOW(), NOW());

INSERT INTO user_products (user_product_id, user_id, catalog_product_id, product_kind, manual_product_name, registered_via, start_date, is_active, created_at) VALUES
    (9301, 9301, NULL, 'SUPPLEMENT', '목표테스트 비타민', 'MANUAL', '2026-01-01', TRUE, NOW()),
    (9302, 9301, NULL, 'SUPPLEMENT', '목표테스트 오메가3', 'MANUAL', '2026-01-01', TRUE, NOW()),
    (9305, 9305, NULL, 'SUPPLEMENT', '목표테스트 비공개', 'MANUAL', '2026-01-01', TRUE, NOW());

-- 9301: 아침(9301), 저녁(9302) 2개 / 9305: 아침(9305) 1개
INSERT INTO intake_schedules (schedule_id, user_product_id, meal_slot, meal_relation, remind_at, is_enabled, created_at) VALUES
    (9301, 9301, 'BREAKFAST', 'AFTER', '08:00:00', TRUE, NOW()),
    (9302, 9302, 'DINNER',    'AFTER', '19:00:00', TRUE, NOW()),
    (9305, 9305, 'BREAKFAST', 'AFTER', '08:00:00', TRUE, NOW());

-- 2026-10-23: 9301은 아침만 복용(1/2), 9305는 모두 복용(1/1)
INSERT INTO intake_records (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared) VALUES
    (9301, 9301, '2026-10-23', 'TAKEN', '2026-10-23 08:05:00', FALSE),
    (9305, 9305, '2026-10-23', 'TAKEN', '2026-10-23 08:05:00', FALSE);

-- 9301을 팔로우: 9302, 9303 수락 / 9304 대기
-- 9305를 팔로우: 9306 수락
INSERT INTO follows (follow_id, follower_id, following_id, status, created_at, deleted_at) VALUES
    (9301, 9302, 9301, 'ACCEPTED', NOW(), NULL),
    (9302, 9303, 9301, 'ACCEPTED', NOW(), NULL),
    (9303, 9304, 9301, 'PENDING',  NOW(), NULL),
    (9304, 9306, 9305, 'ACCEPTED', NOW(), NULL);
