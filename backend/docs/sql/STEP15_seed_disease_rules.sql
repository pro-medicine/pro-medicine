-- STEP 15: 진단 질환(신장 질환) 규칙 2행 추가. 이미 STEP07(또는 STEP13) 시드를 넣은 로컬 DB에 한 번 실행 (MySQL, 다시 실행해도 안전)
-- 새로 시드하는 경우에는 STEP07_seed_recommendation_rules.sql·STEP13_ai_survey_setup.sql에 이미 들어 있으므로 실행하지 않아도 된다
-- 조건 코드: 회원 프로필의 진단 질환(user_conditions.condition_name) 그대로. ConditionCodeResolver가 KIDNEY 등을 조건 코드로 낸다
-- 영향: 신장 질환 회원의 V1·V2 추천에서 칼륨·마그네슘에 경고 표시 (추천에서 빼지는 않음). 출처는 domain-rules.md S-19, S-20
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-K01', 'DISEASE', 'KIDNEY', 'POTASSIUM', 'WARN', '신장 질환이 있으면 칼륨 보충제는 먼저 의사·약사와 상담하세요', FALSE, 'S-19', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-K02', 'DISEASE', 'KIDNEY', 'MAGNESIUM', 'WARN', '신장 기능이 떨어져 있으면 마그네슘이 몸에 쌓일 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-20', TRUE);
