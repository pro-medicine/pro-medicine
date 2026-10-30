-- FollowNotificationServiceTest 전용 테스트 데이터
-- 예시 데이터(schema.sql)·다른 테스트 데이터와 겹치지 않도록 9400번대 id를 사용한다.
-- 테스트 트랜잭션 안에서 실행되어 테스트가 끝나면 롤백된다.
-- 상황: 9401이 9402에게 팔로우를 요청하고, 9402가 수락한다.

INSERT INTO users (user_id, email, password_hash, nickname, share_intake_status, sensitive_consent, created_at, updated_at, last_active_at) VALUES
    (9401, 'follow-test-9401@example.com', 'test-hash', '팔로우테스트9401', TRUE, FALSE, NOW(), NOW(), NOW()),
    (9402, 'follow-test-9402@example.com', 'test-hash', '팔로우테스트9402', TRUE, FALSE, NOW(), NOW(), NOW());
