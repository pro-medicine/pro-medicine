-- 성분 기준값 시드 (STEP 06). 값은 reference/domain-rules.md 2번 표에서 출처를 확인한 것만 넣는다.
-- TODO(출처 필요)인 값은 행을 만들지 않거나 NULL로 둔다 (지어내지 않음). 나머지 성분은 KDRI-2025 원문 확인 후 추가.
-- 비타민 C (S-03, NIH-ODS). 흡연자는 +35mg (R-01)
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) VALUES ('VITAMIN_C', 'M', 19, NULL, NULL, 90, 2000, 'mg', 'NIH-ODS', 'S-03');
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) VALUES ('VITAMIN_C', 'F', 19, NULL, NULL, 75, 2000, 'mg', 'NIH-ODS', 'S-03');
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) VALUES ('VITAMIN_C', 'M', 19, NULL, 'SMOKING_CURRENT', 125, 2000, 'mg', 'NIH-ODS', 'S-03');
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) VALUES ('VITAMIN_C', 'F', 19, NULL, 'SMOKING_CURRENT', 110, 2000, 'mg', 'NIH-ODS', 'S-03');
-- 엽산 (S-11). 상한 1000mcg은 합성 엽산(folic acid) 기준이라 DFE 단위 권장량과 섞지 않아 NULL
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) VALUES ('FOLATE', 'ALL', 19, NULL, NULL, 400, NULL, 'mcg DFE', 'NIH-ODS', 'S-11');
-- 철분 남성 19-50 (S-16). 상한은 TODO(출처 필요)라 NULL
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) VALUES ('IRON', 'M', 19, 50, NULL, 8, NULL, 'mg', 'NIH-ODS', 'S-16');
