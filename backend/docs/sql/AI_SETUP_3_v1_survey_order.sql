-- AI_SETUP_3: 설문 v1 문항 순서 변경 + 첫 문항 문구 변경 (2026-11-02)
-- 실행 위치: 각자 PC의 MySQL과 배포 DB (yakssok DB 선택 후 실행). MySQL Workbench에서 파일을 열어 전체 실행해도 된다
--   예) mysql -u root -p --default-character-set=utf8mb4 yakssok < backend/docs/sql/AI_SETUP_3_v1_survey_order.sql
-- 실행 순서
--   1) 서버를 한 번 실행해서 테이블을 만든다 (이미 실행해 둔 DB면 건너뛴다)
--   2) AI_SETUP.sql (이미 실행했으면 건너뛴다)
--   3) AI_SETUP_2.sql (이미 실행했으면 건너뛴다)
--   4) 이 파일
-- 하는 일
--   - survey_questions: 설문 v1의 Q-GOAL을 1번째로 옮기고, Q-GENDER ~ Q-FATIGUE를 한 칸씩 뒤로 민다 (2~10)
--     Q-PREGNANCY(11), Q-CONDITIONS(12), Q-MEDICATIONS(13), Q-SUPPLEMENTS(14)는 그대로
--   - survey_questions: Q-GOAL 문구를 "뭐가 불편하세요? (영양제를 먹으면서 효과를 보고 싶은 점)"으로 바꾼다
--   - 문항은 code로 찾는다 (question_id에 의존하지 않음). 선택지·문항 코드·다른 테이블은 건드리지 않는다
--   - 테이블을 지우지 않는다. 기존 설문 응답·AI 추천 기록·회원 데이터는 그대로 남는다
-- 여러 번 실행해도 결과가 같다 (값을 더하지 않고 정해진 값으로 넣음). MySQL Workbench safe update 모드도 파일 안에서 잠깐 끄고 원래 값으로 되돌린다
-- 새로 셋업하는 DB는 AI_SETUP.sql에 이미 반영되어 있어 이 파일을 실행해도 바뀌는 것이 없다
SET NAMES utf8mb4;
SET @old_safe_updates = @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;
START TRANSACTION;

-- 1) 문항 순서
UPDATE survey_questions sq
JOIN surveys s ON s.survey_id = sq.survey_id
SET sq.display_order = CASE sq.code
        WHEN 'Q-GOAL'     THEN 1
        WHEN 'Q-GENDER'   THEN 2
        WHEN 'Q-BIRTH'    THEN 3
        WHEN 'Q-SMOKING'  THEN 4
        WHEN 'Q-DRINKING' THEN 5
        WHEN 'Q-CAFFEINE' THEN 6
        WHEN 'Q-DIET'     THEN 7
        WHEN 'Q-SUN'      THEN 8
        WHEN 'Q-SLEEP'    THEN 9
        WHEN 'Q-FATIGUE'  THEN 10
        ELSE sq.display_order
    END
WHERE s.version = 'v1'
  AND sq.code IN ('Q-GOAL', 'Q-GENDER', 'Q-BIRTH', 'Q-SMOKING', 'Q-DRINKING',
                  'Q-CAFFEINE', 'Q-DIET', 'Q-SUN', 'Q-SLEEP', 'Q-FATIGUE');

-- 2) 첫 문항 문구
UPDATE survey_questions sq
JOIN surveys s ON s.survey_id = sq.survey_id
SET sq.question_text = '뭐가 불편하세요? (영양제를 먹으면서 효과를 보고 싶은 점)'
WHERE s.version = 'v1'
  AND sq.code = 'Q-GOAL';

COMMIT;
SET SQL_SAFE_UPDATES = @old_safe_updates;

-- 확인: Q-GOAL 1 → Q-GENDER 2 → … → Q-FATIGUE 10 → Q-PREGNANCY 11 → Q-CONDITIONS 12 → Q-MEDICATIONS 13 → Q-SUPPLEMENTS 14
SELECT sq.display_order, sq.code, sq.question_text
FROM survey_questions sq
JOIN surveys s ON s.survey_id = sq.survey_id
WHERE s.version = 'v1'
ORDER BY sq.display_order;
