-- STEP11: 알림 종류 추가 (복용 놓침 · 친구 챙겨주기 · 안부지킴이 신청/수락)
-- 영향 범위: notifications 테이블의 CHECK 제약(ck_notif_kind)만 바뀝니다. 기존 알림 데이터는 그대로입니다.
-- 이 SQL을 실행하지 않으면 새 종류의 알림을 저장할 때 CHECK 제약 오류가 납니다.
-- 실행: 로컬 DB와 서버 DB(yakssok)에서 각각 한 번씩 실행합니다. (docker initdb는 DB를 처음 만들 때만 돌기 때문)
--
-- 새 종류
--   INTAKE_MISSED      복용 놓침 (복용 시각 1시간 뒤에도 체크 안 함)
--   FRIEND_NUDGE       친구가 영양제 챙겨주기 (하루 1번)
--   GUARDIAN_REQUESTED 안부지킴이 신청 받음 (알림에서 수락·거절)
--   GUARDIAN_ACCEPTED  안부지킴이 신청이 수락됨

ALTER TABLE `notifications` DROP CHECK `ck_notif_kind`;

ALTER TABLE `notifications`
    ADD CONSTRAINT `ck_notif_kind` CHECK (`notification_type` IN (
        'INTAKE_REMINDER', 'INTAKE_MISSED',
        'FRIEND_GOAL_ACHIEVED', 'NEW_FOLLOWER', 'FOLLOW_ACCEPTED', 'FRIEND_NUDGE',
        'GUARDIAN', 'GUARDIAN_REQUESTED', 'GUARDIAN_ACCEPTED'
    ));

-- 확인: 아래 결과에 새 종류가 보이면 성공
-- SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_NAME = 'ck_notif_kind';
