-- FriendNotificationTargetRepositoryTest 전용 테스트 데이터
-- 예시 데이터(schema.sql)·다른 테스트 데이터와 겹치지 않도록 9200번대 id를 사용한다.
-- 테스트 트랜잭션 안에서 실행되어 테스트가 끝나면 롤백된다.

INSERT INTO users (user_id, email, password_hash, nickname, share_intake_status, sensitive_consent, created_at, updated_at) VALUES
                                                                                                                                (9201, 'friend-test-9201@example.com', 'test-hash', '친구테스트9201', TRUE, FALSE, NOW(), NOW()),
                                                                                                                                (9202, 'friend-test-9202@example.com', 'test-hash', '친구테스트9202', TRUE, FALSE, NOW(), NOW()),
                                                                                                                                (9203, 'friend-test-9203@example.com', 'test-hash', '친구테스트9203', TRUE, FALSE, NOW(), NOW()),
                                                                                                                                (9204, 'friend-test-9204@example.com', 'test-hash', '친구테스트9204', TRUE, FALSE, NOW(), NOW()),
                                                                                                                                (9205, 'friend-test-9205@example.com', 'test-hash', '친구테스트9205', TRUE, FALSE, NOW(), NOW()),
                                                                                                                                (9206, 'friend-test-9206@example.com', 'test-hash', '친구테스트9206', TRUE, FALSE, NOW(), NOW()),
                                                                                                                                (9207, 'friend-test-9207@example.com', 'test-hash', '친구테스트9207', TRUE, FALSE, NOW(), NOW());

-- 9201을 기준으로
-- 9202 → 9201 수락됨       : 알림 받음
-- 9207 → 9201 수락됨       : 알림 받음
-- 9203 → 9201 대기 중      : 제외
-- 9204 → 9201 거절됨       : 제외
-- 9205 → 9201 수락 후 취소 : 제외 (deleted_at 있음)
-- 9201 → 9206 수락됨       : 방향이 반대라 제외 (9201이 9206을 팔로우하는 것)
INSERT INTO follows (follow_id, follower_id, following_id, status, created_at, deleted_at) VALUES
                                                                                               (9201, 9202, 9201, 'ACCEPTED', NOW(), NULL),
                                                                                               (9202, 9207, 9201, 'ACCEPTED', NOW(), NULL),
                                                                                               (9203, 9203, 9201, 'PENDING',  NOW(), NULL),
                                                                                               (9204, 9204, 9201, 'REJECTED', NOW(), NULL),
                                                                                               (9205, 9205, 9201, 'ACCEPTED', NOW(), NOW()),
                                                                                               (9206, 9201, 9206, 'ACCEPTED', NOW(), NULL);
