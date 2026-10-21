# 기준표·규칙 초기 데이터와 출처

이 파일에 있는 값만 시드 데이터와 평가셋 기대값에 쓴다. 없는 값은 지어내지 않는다.
설계용 초안이라 서비스 공개 전에 약사 검토가 필요하다.

## 1. 성분 코드

| code | 이름 | 단위 |
| --- | --- | --- |
| VITAMIN_C | 비타민 C | mg |
| VITAMIN_A | 비타민 A (레티놀) | mcg RAE |
| BETA_CAROTENE | 베타카로틴 | mg |
| VITAMIN_B1 | 비타민 B1 (티아민) | mg |
| VITAMIN_B12 | 비타민 B12 | mcg |
| VITAMIN_D | 비타민 D | mcg |
| VITAMIN_K | 비타민 K | mcg |
| FOLATE | 엽산 | mcg DFE |
| MAGNESIUM | 마그네슘 | mg |
| IRON | 철분 | mg |
| CALCIUM | 칼슘 | mg |
| ZINC | 아연 | mg |
| POTASSIUM | 칼륨 | mg |
| ST_JOHNS_WORT | 세인트존스워트 | - |

## 2. 기준값 (ingredient_standards 시드)

1차 기준은 **2025 한국인 영양소 섭취기준(보건복지부·한국영양학회)**이다. 원문 수치는 사용자가 원문을 내려받아 채운다.
아래는 출처를 확인한 값만 넣었고, 출처가 미국 기준(NIH)인 값은 `standard_version`을 구분한다.

| ingredient | gender | age | condition | recommended | upper_limit | unit | standard_version | source |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| VITAMIN_C | M | 19+ | - | 90 | 2000 | mg | NIH-ODS | S-03 |
| VITAMIN_C | F | 19+ | - | 75 | 2000 | mg | NIH-ODS | S-03 |
| VITAMIN_C | M | 19+ | SMOKING_CURRENT | 125 (90+35) | 2000 | mg | NIH-ODS | S-03 |
| VITAMIN_C | F | 19+ | SMOKING_CURRENT | 110 (75+35) | 2000 | mg | NIH-ODS | S-03 |
| FOLATE | ALL | 19+ | - | 400 | (상한 비움: 상한 1000mcg은 합성 엽산(folic acid) 기준이라 DFE 단위 권장량과 섞지 않는다) | mcg DFE | NIH-ODS | S-11 |
| IRON | M | 19-50 | - | 8 | TODO(출처 필요) | mg | NIH-ODS | S-16 |
| 그 외 성분 | | | | TODO(KDRI-2025 원문) | TODO | | KDRI-2025 | S-01 |

## 3. 규칙 (recommendation_rules 시드)

조치: `EXCLUDE` 추천 제외 / `WARN` 경고 표시 / `TIMING` 복용 시간 분리 안내 / `ADJUST` 필요량 조정 / `ASK` 추가 확인

| rule_id | 조건 타입 | 조건 코드 | 대상 성분 | 조치 | 메시지 | source |
| --- | --- | --- | --- | --- | --- | --- |
| R-01 | HABIT | SMOKING_CURRENT | VITAMIN_C | ADJUST | 흡연자는 비타민 C 필요량이 하루 35mg 많아요 | S-03, S-04 |
| R-02 | HABIT | SMOKING_CURRENT | BETA_CAROTENE | EXCLUDE | 흡연자는 고용량 베타카로틴 보충제를 피하는 게 좋아요 | S-05, S-06 |
| R-03 | HABIT | DRINKING_HIGH | VITAMIN_B1 | WARN | 음주가 잦으면 비타민 B1이 부족해지기 쉬워요 | S-07 |
| R-04 | HABIT | CAFFEINE_HIGH | IRON | TIMING | 커피·차와 철분은 시간 간격을 두고 드세요 | S-08 |
| R-05 | HABIT | DIET_VEGAN | VITAMIN_B12 | ASK | 비건 식단은 비타민 B12 보충이 필요한지 확인하세요 | S-09 |
| R-06 | HABIT | SUN_LOW | VITAMIN_D | ASK | 햇빛 노출이 적으면 비타민 D 부족 위험이 있어요 | S-10 |
| R-07 | STATUS | PREGNANCY_POSSIBLE | FOLATE | ADJUST | 임신 가능성이 있으면 엽산(folic acid) 하루 400mcg이 권장돼요 | S-11 |
| R-08 | STATUS | PREGNANT_OR_LACTATING | VITAMIN_A | EXCLUDE | 임신·수유 중에는 고용량 비타민 A(레티놀)를 피하세요 | S-06 |
| R-11 | DRUG | METFORMIN | VITAMIN_B12 | WARN | 메트포르민 장기 복용 시 B12 흡수가 줄 수 있어요 | S-12 |
| R-12 | DRUG | PPI | VITAMIN_B12 | WARN | 위산억제제 장기 복용 시 B12 흡수가 줄 수 있어요 | S-12 |
| R-12b | DRUG | PPI | MAGNESIUM | WARN | 위산억제제 장기 복용 시 마그네슘이 줄 수 있어요 | S-13 |
| R-13 | DRUG | ACE_INHIBITOR | POTASSIUM | EXCLUDE | 혈압약(ACE 억제제) 복용 중 칼륨 보충은 전문가와 상담하세요 | S-14 |
| R-13b | DRUG | ARB | POTASSIUM | EXCLUDE | 혈압약(ARB) 복용 중 칼륨 보충은 전문가와 상담하세요 | S-14 |
| R-14 | DRUG | K_SPARING_DIURETIC | POTASSIUM | EXCLUDE | 칼륨보존 이뇨제와 칼륨 보충은 함께 쓰면 안 돼요 | S-14 |
| R-15 | DRUG | THIAZIDE_LOOP_DIURETIC | MAGNESIUM | WARN | 이뇨제 복용 시 마그네슘이 빠져나갈 수 있어요 | S-13 |
| R-16 | DRUG | WARFARIN | VITAMIN_K | WARN | 와파린 복용 중에는 비타민 K 섭취량을 일정하게 유지하세요 | S-15 |
| R-17 | DRUG | LEVOTHYROXINE | IRON | TIMING | 갑상선약과 철분은 시간 간격을 두고 드세요 | S-16 |
| R-17b | DRUG | LEVOTHYROXINE | CALCIUM | TIMING | 갑상선약과 칼슘은 시간 간격을 두고 드세요 | S-17 |
| R-18 | DRUG | TETRA_QUINOLONE | MAGNESIUM | TIMING | 일부 항생제와 마그네슘은 시간 간격을 두고 드세요 | S-13 |
| R-19 | DRUG | SSRI | ST_JOHNS_WORT | EXCLUDE | 항우울제(SSRI)와 세인트존스워트는 함께 드시면 안 돼요 | S-18 |
| R-21 | INGREDIENT | IRON | CALCIUM | TIMING | 철분과 칼슘은 다른 시간대에 드세요 | S-16 |
| R-22 | INGREDIENT | IRON | ZINC | WARN | 고용량 철분은 아연 흡수를 줄일 수 있어요 | S-16 |
| R-23 | STATUS | ADULT_MALE_TAKING_IRON | IRON | ASK | 철분제를 드시는 이유(처방 여부)를 확인해 주세요 | S-16 |

### 비회원 보수 규칙

비회원은 질환·복용 약·임신 여부를 묻지 않으므로, 회원이라면 규칙으로 걸러졌을 성분을 비회원 추천에서는 **용량과 상관없이 전부 제외**한다. 같은 표에 행으로 넣고 `guest_excluded = true`로 둔다.

| rule_id | 조건 타입 | 조건 코드 | 대상 성분 | 조치 | 메시지 | source |
| --- | --- | --- | --- | --- | --- | --- |
| R-G1 | STATUS | GUEST | POTASSIUM | EXCLUDE | 복용 중인 약에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요 | S-14 |
| R-G2 | STATUS | GUEST | ST_JOHNS_WORT | EXCLUDE | 복용 중인 약에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요 | S-18 |
| R-G3 | STATUS | GUEST | VITAMIN_A | EXCLUDE | 임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요 | S-06 |
| R-G4 | STATUS | GUEST | BETA_CAROTENE | EXCLUDE | 흡연 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요 | S-05, S-06 |

규칙은 회원용 23개 + 비회원용 4개, 총 27개다.

## 4. 출처

| id | 출처 |
| --- | --- |
| S-01 | 2025 한국인 영양소 섭취기준, 보건복지부·한국영양학회 (보도: https://www.foodtoday.or.kr/news/article.html?no=201397) |
| S-03 | NIH ODS Vitamin C Fact Sheet: https://ods.od.nih.gov/factsheets/vitaminc-consumer/ |
| S-04 | DRI Vitamin C, National Academies: https://nap.edu/read/11537/chapter/21 |
| S-05 | ATBC 연구 (흡연자 베타카로틴과 폐암): https://pmc.ncbi.nlm.nih.gov/articles/PMC6636175 |
| S-06 | NIH ODS Vitamin A Fact Sheet: https://ods.od.nih.gov/factsheets/vitaminA-Consumer/ |
| S-07 | NIH ODS Thiamin Fact Sheet: https://ods.od.nih.gov/pdf/factsheets/Thiamin-Consumer.pdf |
| S-08 | Effect of tea on iron absorption: https://pubmed.ncbi.nlm.nih.gov/11029010/ |
| S-09 | NIH ODS Vitamin B12 Fact Sheet: https://ods.od.nih.gov/factsheets/VitaminB12-Consumer/ |
| S-10 | NICE PH56 (BMJ 요약): https://ep.bmj.com/content/101/1/43 |
| S-11 | NIH ODS Folate Fact Sheet: https://ods.od.nih.gov/factsheets/Folate-Consumer/ |
| S-12 | PPIs, H2RAs, Metformin and B12: https://pmc.ncbi.nlm.nih.gov/articles/PMC6054240 |
| S-13 | Magnesium and Drugs: https://pmc.ncbi.nlm.nih.gov/articles/PMC6539869 |
| S-14 | FDA Potassium Chloride 라벨: https://www.accessdata.fda.gov/drugsatfda_docs/nda/2015/208019Orig1s000LBL.pdf |
| S-15 | NIH ODS Vitamin K Fact Sheet: https://ods.od.nih.gov/factsheets/vitaminK-Consumer/ |
| S-16 | NIH ODS Iron Fact Sheet: https://ods.od.nih.gov/factsheets/Iron-Consumer/ |
| S-17 | Calcium 상호작용: https://www.rxreasoner.com/substances/calcium/precautions |
| S-18 | NCCIH St. John's Wort: https://www.nccih.nih.gov/health/tips/things-to-know-about-st-johns-wort-and-depression |
