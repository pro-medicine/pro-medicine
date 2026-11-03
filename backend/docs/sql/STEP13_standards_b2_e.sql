-- STEP13: ingredient_standards에 비타민 B2·비타민 E 기준 추가 (2025 한국인 영양소 섭취기준)
-- 이유: 홈 "오늘 먹은 영양소"가 14개 성분의 사람별 권장량을 이 표에서 찾는데, B2와 E 행이 없었음
-- 값 출처: yakssok_14_nutrients_cleaned_v4_all_units.xlsx '성인기준_2025' 시트 (KDRI 2025, https://www.kns.or.kr/fileroom/fileroom_view.asp?BoardID=Kdr&idx=167)
--   비타민 B2: 권장섭취량(RNI) / 비타민 E: 충분섭취량(AI), 상한섭취량 540mg α-TE
-- 영향 범위: 행 20개 추가만 (기존 행은 그대로). 추천 기능(영준 담당)도 이 표를 쓰므로 B2·E 용량 계산에 함께 반영됨
-- 적용: 실행 후 백엔드를 재시작해야 함 (StandardsCache가 앱 시작 때 한 번 읽음). 다시 실행해도 됨.

INSERT INTO `ingredient_standards` (`standard_id`, `ingredient_code`, `gender`, `age_min`, `age_max`, `condition_code`, `recommended_amount`, `upper_limit`, `unit`, `standard_version`, `source_id`) VALUES
(201,'VITAMIN_B2','M',19,29,NULL,1.50,NULL,'mg','KDRI-2025','S-01'),
(202,'VITAMIN_B2','M',30,49,NULL,1.50,NULL,'mg','KDRI-2025','S-01'),
(203,'VITAMIN_B2','M',50,64,NULL,1.50,NULL,'mg','KDRI-2025','S-01'),
(204,'VITAMIN_B2','M',65,74,NULL,1.40,NULL,'mg','KDRI-2025','S-01'),
(205,'VITAMIN_B2','M',75,NULL,NULL,1.40,NULL,'mg','KDRI-2025','S-01'),
(206,'VITAMIN_B2','F',19,29,NULL,1.20,NULL,'mg','KDRI-2025','S-01'),
(207,'VITAMIN_B2','F',30,49,NULL,1.20,NULL,'mg','KDRI-2025','S-01'),
(208,'VITAMIN_B2','F',50,64,NULL,1.20,NULL,'mg','KDRI-2025','S-01'),
(209,'VITAMIN_B2','F',65,74,NULL,1.10,NULL,'mg','KDRI-2025','S-01'),
(210,'VITAMIN_B2','F',75,NULL,NULL,1.00,NULL,'mg','KDRI-2025','S-01'),
(211,'VITAMIN_E','M',19,29,NULL,12.00,540.00,'mg α-TE','KDRI-2025','S-01'),
(212,'VITAMIN_E','M',30,49,NULL,12.00,540.00,'mg α-TE','KDRI-2025','S-01'),
(213,'VITAMIN_E','M',50,64,NULL,12.00,540.00,'mg α-TE','KDRI-2025','S-01'),
(214,'VITAMIN_E','M',65,74,NULL,12.00,540.00,'mg α-TE','KDRI-2025','S-01'),
(215,'VITAMIN_E','M',75,NULL,NULL,12.00,540.00,'mg α-TE','KDRI-2025','S-01'),
(216,'VITAMIN_E','F',19,29,NULL,12.00,540.00,'mg α-TE','KDRI-2025','S-01'),
(217,'VITAMIN_E','F',30,49,NULL,12.00,540.00,'mg α-TE','KDRI-2025','S-01'),
(218,'VITAMIN_E','F',50,64,NULL,12.00,540.00,'mg α-TE','KDRI-2025','S-01'),
(219,'VITAMIN_E','F',65,74,NULL,12.00,540.00,'mg α-TE','KDRI-2025','S-01'),
(220,'VITAMIN_E','F',75,NULL,NULL,12.00,540.00,'mg α-TE','KDRI-2025','S-01')
ON DUPLICATE KEY UPDATE `recommended_amount`=VALUES(`recommended_amount`), `upper_limit`=VALUES(`upper_limit`), `unit`=VALUES(`unit`);

-- 확인: 20이 나오면 성공
-- SELECT COUNT(*) FROM ingredient_standards WHERE ingredient_code IN ('VITAMIN_B2','VITAMIN_E');
