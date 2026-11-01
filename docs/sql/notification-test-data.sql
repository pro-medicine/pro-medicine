-- ============================================================
-- 알림 화면 테스트용 예시 데이터 (로컬 DB 전용, 운영 DB에 실행 금지)
-- 대상: user_id = 1 (로그인 연동 전 임시 사용자)
-- 다시 실행해도 됨: 예시 알림은 지우고 새로 넣고, 예시 사용자·친구 관계는 이미 있으면 건너뜀
--
-- 넣는 것
--  1) 예시 사용자 4명 (9801~9804, 닉네임 앞에 "테스트")
--  2) 친구 관계: 1 ↔ 테스트서준 / 1 ↔ 테스트엄마 / 1 ↔ 테스트유진 (서로 친구), 테스트하늘 → 1 친구 신청 대기
--  3) 안부지킴이: 내가(1) 테스트엄마를 지키는 중 (3일 미접속 시 알림)
--  4) 알림 5종 (복용 / 친구 완료 / 친구 신청 / 친구 수락 / 안부지킴이)
--     → 알림 패널의 상단 고정 카드(안부·오늘 복용)와 "다른 소식" 목록을 한 번에 확인
-- ============================================================

-- 1) 예시 사용자 (테스트엄마는 4일 전 마지막 접속)
INSERT IGNORE INTO users (user_id, email, password_hash, nickname, share_intake_status, sensitive_consent, created_at, updated_at, last_active_at) VALUES
    (9801, 'noti-test-9801@example.com', 'test-hash', '테스트서준', TRUE, FALSE, NOW(), NOW(), NOW()),
    (9802, 'noti-test-9802@example.com', 'test-hash', '테스트하늘', TRUE, FALSE, NOW(), NOW(), NOW()),
    (9803, 'noti-test-9803@example.com', 'test-hash', '테스트엄마', TRUE, FALSE, NOW(), NOW(), NOW() - INTERVAL 4 DAY),
    (9804, 'noti-test-9804@example.com', 'test-hash', '테스트유진', TRUE, FALSE, NOW(), NOW(), NOW());

-- 2) 친구 관계 (ACCEPTED 양방향 = 서로 친구, PENDING = 받은 친구 신청)
INSERT IGNORE INTO follows (follow_id, follower_id, following_id, status, created_at, deleted_at) VALUES
    (9801, 1, 9801, 'ACCEPTED', NOW(), NULL),
    (9802, 9801, 1, 'ACCEPTED', NOW(), NULL),
    (9803, 1, 9803, 'ACCEPTED', NOW(), NULL),
    (9804, 9803, 1, 'ACCEPTED', NOW(), NULL),
    (9805, 1, 9804, 'ACCEPTED', NOW(), NULL),
    (9806, 9804, 1, 'ACCEPTED', NOW(), NULL),
    (9807, 9802, 1, 'PENDING',  NOW(), NULL);

-- 3) 안부지킴이: 지켜보는 사람(guardian_user_id) = 1, 대상(user_id) = 테스트엄마
INSERT IGNORE INTO guardian_settings (guardian_setting_id, user_id, guardian_user_id, inactivity_days, consented_at, is_active, last_notified_at) VALUES
    (9801, 9803, 1, 3, NOW(), TRUE, NULL);

-- 4) 알림 (예시 알림만 지우고 다시 넣음)
DELETE FROM notifications WHERE dedup_key LIKE 'TEST:%';
INSERT INTO notifications (user_id, schedule_id, actor_user_id, notification_type, title, scheduled_at, sent_at, read_at, created_at, dedup_key) VALUES
    -- 안 읽음: 안부지킴이 (빨간 상단 카드, "친구 보기" → 테스트엄마 상세)
    (1, NULL, 9803, 'GUARDIAN', '테스트엄마님이 4일째 접속하지 않았어요. 안부를 확인해보세요', NULL, NOW(), NULL, NOW() - INTERVAL 3 HOUR, 'TEST:GUARDIAN'),
    -- 안 읽음: 오늘 복용 알림 (민트 상단 카드, 알림 시각이 없어서 "체크하기" → 홈)
    --   "복용 완료" 버튼으로 실제 체크까지 확인하려면 화면 왼쪽 아래 "알림 테스트 → 복용 알림"을 사용
    --   (테스트 API가 오늘 안 먹은 실제 일정의 알림 시각으로 알림을 만듦)
    (1, NULL, NULL, 'INTAKE_REMINDER', '점심 영양제 1개를 드실 시간이에요', NULL, NOW(), NULL, NOW() - INTERVAL 2 MINUTE, 'TEST:INTAKE'),
    -- 안 읽음: 친구 완료 / 친구 신청 (다른 소식, 친구 신청은 알림에서 바로 수락·거절 → 테스트하늘 신청 대기와 연결)
    (1, NULL, 9801, 'FRIEND_GOAL_ACHIEVED', '테스트서준님이 오늘 복용을 모두 완료했어요', NULL, NOW(), NULL, NOW() - INTERVAL 10 MINUTE, 'TEST:GOAL'),
    (1, NULL, 9802, 'NEW_FOLLOWER', '테스트하늘님이 팔로우를 요청했어요', NULL, NOW(), NULL, NOW() - INTERVAL 1 HOUR, 'TEST:FOLLOW'),
    -- 읽음: 친구 수락 (흐리게 표시)
    (1, NULL, 9804, 'FOLLOW_ACCEPTED', '테스트유진님이 팔로우 요청을 수락했어요', NULL, NOW(), NOW(), NOW() - INTERVAL 2 DAY, 'TEST:ACCEPT');

-- ------------------------------------------------------------
-- 예시 데이터 전부 지우기 (테스트 끝나면 아래 주석을 풀고 실행)
-- DELETE FROM notifications WHERE dedup_key LIKE 'TEST:%' OR dedup_key LIKE 'DEV:%';
-- DELETE FROM guardian_settings WHERE guardian_setting_id = 9801;
-- DELETE FROM follows WHERE follow_id BETWEEN 9801 AND 9807;
-- DELETE FROM users WHERE user_id BETWEEN 9801 AND 9804;
-- ------------------------------------------------------------
