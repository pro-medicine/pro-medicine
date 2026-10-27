-- STEP 12: 약 이름을 모르는 경우(계열 모름) 약물·규칙 추가. 이미 STEP02·STEP07 시드를 넣은 로컬 DB에 한 번 실행 (MySQL)
-- 새로 시드하는 경우에는 STEP02_seed_drugs.sql·STEP07_seed_recommendation_rules.sql에 이미 들어 있으므로 실행하지 않아도 된다
-- 약 이름을 모르고 계열만 아는 경우 (STEP 12). drug_class = 계열 모름 코드, 규칙은 R-U*로 WARN/TIMING 위주
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U01', '혈압약 (이름 모름)', 'ANTIHYPERTENSIVE_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U02', '이뇨제 (이름 모름)', 'DIURETIC_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U03', '당뇨약 (이름 모름)', 'ANTIDIABETIC_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U04', '위산억제제 (이름 모름)', 'ACID_REDUCER_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U05', '항응고제 (이름 모름)', 'ANTICOAGULANT_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U06', '갑상선약 (이름 모름)', 'THYROID_MED_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U07', '항생제 (이름 모름)', 'ANTIBIOTIC_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U08', '항우울제 (이름 모름)', 'ANTIDEPRESSANT_UNKNOWN');
-- 약 계열만 아는 경우 (STEP 12). 하위 계열 규칙의 대상 성분을 합쳐 추천은 하되 경고. 항우울제만 위험이 커서 EXCLUDE 유지
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U01', 'DRUG', 'ANTIHYPERTENSIVE_UNKNOWN', 'POTASSIUM', 'WARN', '혈압약 종류에 따라 칼륨 보충이 위험할 수 있어요. 드시기 전에 의사·약사와 상담하세요', FALSE, 'S-14', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U01b', 'DRUG', 'ANTIHYPERTENSIVE_UNKNOWN', 'MAGNESIUM', 'WARN', '일부 혈압약(이뇨제)은 마그네슘을 줄일 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-13', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U02', 'DRUG', 'DIURETIC_UNKNOWN', 'POTASSIUM', 'WARN', '이뇨제 종류에 따라 칼륨 보충이 위험할 수 있어요. 드시기 전에 의사·약사와 상담하세요', FALSE, 'S-14', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U02b', 'DRUG', 'DIURETIC_UNKNOWN', 'MAGNESIUM', 'WARN', '일부 이뇨제는 마그네슘을 줄일 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-13', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U03', 'DRUG', 'ANTIDIABETIC_UNKNOWN', 'VITAMIN_B12', 'WARN', '일부 당뇨약(메트포르민)은 오래 드시면 B12 흡수가 줄 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-12', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U04', 'DRUG', 'ACID_REDUCER_UNKNOWN', 'VITAMIN_B12', 'WARN', '위산억제제를 오래 드시면 B12 흡수가 줄 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-12', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U04b', 'DRUG', 'ACID_REDUCER_UNKNOWN', 'MAGNESIUM', 'WARN', '위산억제제를 오래 드시면 마그네슘이 줄 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-13', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U05', 'DRUG', 'ANTICOAGULANT_UNKNOWN', 'VITAMIN_K', 'WARN', '항응고제 종류에 따라 비타민 K가 약효에 영향을 줄 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-15', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U06', 'DRUG', 'THYROID_MED_UNKNOWN', 'IRON', 'TIMING', '갑상선약과 철분은 시간 간격을 두고 드세요. 간격은 의사·약사와 상담하세요', FALSE, 'S-16', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U06b', 'DRUG', 'THYROID_MED_UNKNOWN', 'CALCIUM', 'TIMING', '갑상선약과 칼슘은 시간 간격을 두고 드세요. 간격은 의사·약사와 상담하세요', FALSE, 'S-17', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U07', 'DRUG', 'ANTIBIOTIC_UNKNOWN', 'MAGNESIUM', 'TIMING', '일부 항생제는 마그네슘과 시간 간격을 두고 드셔야 해요. 의사·약사와 상담하세요', FALSE, 'S-13', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U08', 'DRUG', 'ANTIDEPRESSANT_UNKNOWN', 'ST_JOHNS_WORT', 'EXCLUDE', '항우울제와 세인트존스워트는 함께 드시면 안 돼요. 의사·약사와 상담하세요', FALSE, 'S-18', TRUE);
