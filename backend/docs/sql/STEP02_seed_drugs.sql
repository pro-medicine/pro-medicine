-- 약물 마스터 시드 (STEP 02). 예시 데이터: domain-rules.md의 약물 계열별 대표 약 1개씩.
-- drug_class 값은 recommendation_rules(STEP 07)의 DRUG 조건 코드와 같다.
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-001', '메트포르민', 'METFORMIN');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-002', '오메프라졸', 'PPI');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-003', '에날라프릴', 'ACE_INHIBITOR');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-004', '로사르탄', 'ARB');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-005', '스피로놀락톤', 'K_SPARING_DIURETIC');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-006', '히드로클로로티아지드', 'THIAZIDE_LOOP_DIURETIC');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-007', '와파린', 'WARFARIN');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-008', '레보티록신', 'LEVOTHYROXINE');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-009', '시프로플록사신', 'TETRA_QUINOLONE');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-010', '에스시탈로프람', 'SSRI');
