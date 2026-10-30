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
-- 예시 약물 (schema.sql 예시 데이터와 같은 값). STEP 16부터 규칙 대상 (CALCIUM_CHANNEL_BLOCKER: R-30c3, STATIN: R-36)
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-011', '암로디핀', 'CALCIUM_CHANNEL_BLOCKER');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-012', '아토르바스타틴', 'STATIN');
-- 성분 확장 (STEP 16): 새 약물 계열 대표 약. drug_class는 R-30b·R-31·R-33b·R-39의 DRUG 조건 코드와 같다
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-013', '클로피도그렐', 'ANTIPLATELET');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-014', '인슐린', 'INSULIN');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-015', '타크로리무스', 'IMMUNOSUPPRESSANT');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-016', '발프로산', 'ANTICONVULSANT');
-- 약 이름을 모르고 계열만 아는 경우 (STEP 12). drug_class = 계열 모름 코드, 규칙은 R-U*로 WARN/TIMING 위주
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-U01', '혈압약 (이름 모름)', 'ANTIHYPERTENSIVE_UNKNOWN');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-U02', '이뇨제 (이름 모름)', 'DIURETIC_UNKNOWN');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-U03', '당뇨약 (이름 모름)', 'ANTIDIABETIC_UNKNOWN');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-U04', '위산억제제 (이름 모름)', 'ACID_REDUCER_UNKNOWN');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-U05', '항응고제 (이름 모름)', 'ANTICOAGULANT_UNKNOWN');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-U06', '갑상선약 (이름 모름)', 'THYROID_MED_UNKNOWN');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-U07', '항생제 (이름 모름)', 'ANTIBIOTIC_UNKNOWN');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-U08', '항우울제 (이름 모름)', 'ANTIDEPRESSANT_UNKNOWN');
-- 성분 확장 (STEP 16): 계열 모름. drug_class를 대표 약과 같은 계열 코드로 두어 같은 규칙이 걸린다 (항암제는 계열 모름만)
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-U09', '항혈소판제 (이름 모름)', 'ANTIPLATELET');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-U10', '항암제 (이름 모름)', 'CHEMOTHERAPY');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-U11', '면역억제제 (이름 모름)', 'IMMUNOSUPPRESSANT');
INSERT INTO drugs (drug_id, name, drug_class) VALUES ('D-U12', '뇌전증약 (이름 모름)', 'ANTICONVULSANT');
