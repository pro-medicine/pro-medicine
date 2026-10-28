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
| OMEGA3 | 오메가3 (EPA·DHA) | mg |
| PROBIOTICS | 프로바이오틱스 (유산균) | 억 CFU |
| CURCUMIN | 커큐민 (강황) | mg |
| COQ10 | 코엔자임 Q10 | mg |
| COLLAGEN | 콜라겐 | mg |
| GLUCOSAMINE | 글루코사민 | mg |
| NIACIN | 나이아신 (비타민 B3) | mg NE |
| MILK_THISTLE | 밀크씨슬 (실리마린) | mg |
| LUTEIN | 루테인 | mg |
| VITAMIN_B6 | 비타민 B6 | mg |

OMEGA3 ~ VITAMIN_B6 10개는 V2.5 성분 확장(2026-10-28, STEP 16)에서 추가했다. 선정 기준은 약사 영상 제목·설명란 빈도 + 식약처 건강기능식품 원료 여부이고, 국내 의약품 성분(베르베린·NAC)과 반입 차단 성분(멜라토닌)은 뺐다.
유산균은 **억 CFU**로 저장한다 (100억 CFU = 100). 원래 숫자(10,000,000,000)는 `DECIMAL(10,2)`·`DECIMAL(12,3)` 컬럼에서 넘친다. DSLD 라벨의 "Billion CFU"는 × 10 해서 억 CFU로 바꾼다.

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
| NIACIN | M | 19+ | - | 16 | 35 (보충제 기준) | mg NE | NIH-ODS | S-26 |
| NIACIN | F | 19+ | - | 14 | 35 (보충제 기준) | mg NE | NIH-ODS | S-26 |
| VITAMIN_B6 | ALL | 19-50 | - | 1.3 | 100 | mg | NIH-ODS | S-28 |
| VITAMIN_B6 | M | 51+ | - | 1.7 | 100 | mg | NIH-ODS | S-28 |
| VITAMIN_B6 | F | 51+ | - | 1.5 | 100 | mg | NIH-ODS | S-28 |
| OMEGA3 | ALL | 19+ | - | (비움: 권장량 출처 없음) | 5000 (EPA+DHA, 보충제) | mg | NIH-ODS | S-21 |
| 그 외 성분 | | | | TODO(KDRI-2025 원문) | TODO | | KDRI-2025 | S-01 |

V2.5 성분 확장 기준값 (2026-10-28):
- 영양소(나이아신·비타민 B6)는 `recommended` = 권장섭취량, `upper_limit` = 상한섭취량. 기존 비타민 C처럼 NIH-ODS로 시작하고, KDRI-2025 원문을 확보하면 교체한다 (값이 다를 수 있음)
- 나이아신 상한 35mg은 NIH ODS의 "dietary supplements" 기준(음식 제외)이다. 현재 섭취량 계산이 보충제 기준이라 맞다
- 오메가3 상한 5000mg은 S-21에 인용된 FDA 권고(EPA+DHA 보충제 하루 5g 이하)다. 권장량 출처가 없어 `recommended`는 비운다
- 기능성 원료(나머지 8개)는 식약처 건강기능식품 공전 원료별 1일 섭취량을 쓴다 (`recommended` = 섭취 범위 하한, `upper_limit` = 섭취 범위 상한, `standard_version` = `MFDS-FUNC`)
- TODO(출처 확보 전, 행을 만들지 않음 → "기준값 미등록"): 프로바이오틱스, 커큐민, 코엔자임 Q10, 콜라겐, 글루코사민, 밀크씨슬, 루테인 (공전 원문 자동 접속 불가, PDF 확보 시 채움), 오메가3 권장 섭취 범위

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
| R-G5 | STATUS | GUEST | CURCUMIN | EXCLUDE | 임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요 | S-23, S-30 |
| R-G6 | STATUS | GUEST | COQ10 | EXCLUDE | 임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요 | S-30 |
| R-G7 | STATUS | GUEST | COLLAGEN | EXCLUDE | 임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요 | S-30 |
| R-G8 | STATUS | GUEST | GLUCOSAMINE | EXCLUDE | 임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요 | S-25, S-30 |
| R-G9 | STATUS | GUEST | NIACIN | EXCLUDE | 임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요 | S-30 |
| R-G10 | STATUS | GUEST | MILK_THISTLE | EXCLUDE | 임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요 | S-27, S-30 |
| R-G11 | STATUS | GUEST | LUTEIN | EXCLUDE | 임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요 | S-30 |
| R-G12 | STATUS | GUEST | PROBIOTICS | EXCLUDE | 복용 중인 약에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요 | S-22 |

R-G5~R-G12는 V2.5 성분 확장(STEP 16)에서 같은 기준으로 추가했다 (회원 규칙에 `EXCLUDE`가 있는 성분: R-32·R-33d·R-34b·R-35c·R-36e·R-37·R-38·R-31). 오메가3·비타민 B6은 회원 규칙이 `WARN`뿐이라 비회원 규칙이 없다.

### 약 계열만 아는 경우 (STEP 12)

"혈압약을 먹는데 이름은 모른다"처럼 계열만 아는 사용자를 위해 `drugs`에 계열 모름 항목(`D-U01`~`D-U08`, 이름 "혈압약 (이름 모름)" 등)을 두고, 조건 코드는 `drug_class` 그대로 쓴다. 대상 성분은 하위 계열 규칙의 대상 성분을 합친 것이다. 정확한 약을 모르므로 `EXCLUDE` 대신 **추천은 하되 해당 성분에 상담 안내(`WARN`)**를 붙인다. 원래 `TIMING`인 규칙은 `TIMING` 그대로 둔다. 항우울제 + 세인트존스워트는 대부분의 항우울제와 위험한 상호작용이 있어 `EXCLUDE`를 유지한다.

| rule_id | 조건 타입 | 조건 코드 | 대상 성분 | 조치 | 메시지 | source |
| --- | --- | --- | --- | --- | --- | --- |
| R-U01 | DRUG | ANTIHYPERTENSIVE_UNKNOWN | POTASSIUM | WARN | 혈압약 종류에 따라 칼륨 보충이 위험할 수 있어요. 드시기 전에 의사·약사와 상담하세요 | S-14 |
| R-U01b | DRUG | ANTIHYPERTENSIVE_UNKNOWN | MAGNESIUM | WARN | 일부 혈압약(이뇨제)은 마그네슘을 줄일 수 있어요. 의사·약사와 상담하세요 | S-13 |
| R-U02 | DRUG | DIURETIC_UNKNOWN | POTASSIUM | WARN | 이뇨제 종류에 따라 칼륨 보충이 위험할 수 있어요. 드시기 전에 의사·약사와 상담하세요 | S-14 |
| R-U02b | DRUG | DIURETIC_UNKNOWN | MAGNESIUM | WARN | 일부 이뇨제는 마그네슘을 줄일 수 있어요. 의사·약사와 상담하세요 | S-13 |
| R-U03 | DRUG | ANTIDIABETIC_UNKNOWN | VITAMIN_B12 | WARN | 일부 당뇨약(메트포르민)은 오래 드시면 B12 흡수가 줄 수 있어요. 의사·약사와 상담하세요 | S-12 |
| R-U04 | DRUG | ACID_REDUCER_UNKNOWN | VITAMIN_B12 | WARN | 위산억제제를 오래 드시면 B12 흡수가 줄 수 있어요. 의사·약사와 상담하세요 | S-12 |
| R-U04b | DRUG | ACID_REDUCER_UNKNOWN | MAGNESIUM | WARN | 위산억제제를 오래 드시면 마그네슘이 줄 수 있어요. 의사·약사와 상담하세요 | S-13 |
| R-U05 | DRUG | ANTICOAGULANT_UNKNOWN | VITAMIN_K | WARN | 항응고제 종류에 따라 비타민 K가 약효에 영향을 줄 수 있어요. 의사·약사와 상담하세요 | S-15 |
| R-U06 | DRUG | THYROID_MED_UNKNOWN | IRON | TIMING | 갑상선약과 철분은 시간 간격을 두고 드세요. 간격은 의사·약사와 상담하세요 | S-16 |
| R-U06b | DRUG | THYROID_MED_UNKNOWN | CALCIUM | TIMING | 갑상선약과 칼슘은 시간 간격을 두고 드세요. 간격은 의사·약사와 상담하세요 | S-17 |
| R-U07 | DRUG | ANTIBIOTIC_UNKNOWN | MAGNESIUM | TIMING | 일부 항생제는 마그네슘과 시간 간격을 두고 드셔야 해요. 의사·약사와 상담하세요 | S-13 |
| R-U08 | DRUG | ANTIDEPRESSANT_UNKNOWN | ST_JOHNS_WORT | EXCLUDE | 항우울제와 세인트존스워트는 함께 드시면 안 돼요. 의사·약사와 상담하세요 | S-18 |
| R-K01 | DISEASE | KIDNEY | POTASSIUM | WARN | 신장 질환이 있으면 칼륨 보충제는 먼저 의사·약사와 상담하세요 | S-19 |
| R-K02 | DISEASE | KIDNEY | MAGNESIUM | WARN | 신장 기능이 떨어져 있으면 마그네슘이 몸에 쌓일 수 있어요. 의사·약사와 상담하세요 | S-20 |

### 성분 확장 (V2.5, STEP 16)

2026-10-28 출처 확인, 약사 검토 전 초안. 작성 원칙:
- 기본 `WARN`(추천하되 상담 안내). 출처에 "피해야 한다 / 함께 쓰지 말라"가 있으면 `EXCLUDE`
- **애매하면 제외**: 출처 해석이 필요하거나 출처 대상과 조건 코드 범위가 다르면(예: 수유부만 "피할 것"인데 코드는 임신·수유 공통) `EXCLUDE`
- 이름을 아는 약과 이름을 모르는 계열에 둘 다 규칙을 붙인다 (계열 모름 규칙은 `R-U*`)
- 한 조건 줄을 여러 조건 코드로 펼친 경우 rule_id 뒤에 숫자를 붙인다 (R-30c1~R-30c4). 계열 모름 코드 행은 `R-U` 접두어를 쓴다 (R-U30c, R-U36b, R-U36d)

| rule_id | 조건 타입 | 조건 코드 | 대상 성분 | 조치 | 메시지 | source |
| --- | --- | --- | --- | --- | --- | --- |
| R-30 | DRUG | WARFARIN | OMEGA3 | WARN | 와파린과 고용량 오메가3를 함께 드시면 출혈 위험이 높아질 수 있어요. 의사·약사와 상담하세요 | S-21, S-30 |
| R-U30 | DRUG | ANTICOAGULANT_UNKNOWN | OMEGA3 | WARN | 항응고제와 오메가3를 함께 드시면 출혈 위험이 높아질 수 있어요. 의사·약사와 상담하세요 | S-21, S-30 |
| R-30b | DRUG | ANTIPLATELET | OMEGA3 | WARN | 항혈소판제를 드시면 오메가3는 먼저 의사·약사와 상담하세요 | S-30 |
| R-U30c | DRUG | ANTIHYPERTENSIVE_UNKNOWN | OMEGA3 | WARN | 혈압약을 드시면 오메가3는 먼저 의사·약사와 상담하세요 | S-30 |
| R-30c1 | DRUG | ACE_INHIBITOR | OMEGA3 | WARN | 혈압약을 드시면 오메가3는 먼저 의사·약사와 상담하세요 | S-30 |
| R-30c2 | DRUG | ARB | OMEGA3 | WARN | 혈압약을 드시면 오메가3는 먼저 의사·약사와 상담하세요 | S-30 |
| R-30c3 | DRUG | CALCIUM_CHANNEL_BLOCKER | OMEGA3 | WARN | 혈압약을 드시면 오메가3는 먼저 의사·약사와 상담하세요 | S-30 |
| R-30c4 | DRUG | THIAZIDE_LOOP_DIURETIC | OMEGA3 | WARN | 혈압약을 드시면 오메가3는 먼저 의사·약사와 상담하세요 | S-30 |
| R-31 | DRUG | IMMUNOSUPPRESSANT | PROBIOTICS | EXCLUDE | 면역을 억제하는 약을 드시면 유산균은 피하고 의사·약사와 상담하세요 | S-22 |
| R-32 | STATUS | PREGNANT_OR_LACTATING | CURCUMIN | EXCLUDE | 임신·수유 중에는 강황·커큐민 보충제를 피하세요 | S-23, S-30 |
| R-33 | DRUG | WARFARIN | COQ10 | WARN | 와파린을 드시면 코엔자임 Q10이 약효에 영향을 줄 수 있어요. 의사·약사와 상담하세요 | S-24, S-30 |
| R-U33 | DRUG | ANTICOAGULANT_UNKNOWN | COQ10 | WARN | 항응고제를 드시면 코엔자임 Q10은 먼저 의사·약사와 상담하세요 | S-24, S-30 |
| R-33b | DRUG | INSULIN | COQ10 | WARN | 인슐린을 쓰시면 코엔자임 Q10은 먼저 의사·약사와 상담하세요 | S-24 |
| R-U33b | DRUG | ANTIDIABETIC_UNKNOWN | COQ10 | WARN | 당뇨약 종류에 따라 코엔자임 Q10과 상호작용할 수 있어요. 의사·약사와 상담하세요 | S-24 |
| R-33c | DRUG | CHEMOTHERAPY | COQ10 | WARN | 항암 치료 중에는 코엔자임 Q10을 먼저 의사와 상담하세요 | S-24 |
| R-33d | STATUS | PREGNANT_OR_LACTATING | COQ10 | EXCLUDE | 임신·수유 중에는 코엔자임 Q10을 피하세요 | S-30 |
| R-34 | DISEASE | KIDNEY | COLLAGEN | WARN | 신장 질환이 있으면 콜라겐은 먼저 의사·약사와 상담하세요 | S-30 |
| R-34b | STATUS | PREGNANT_OR_LACTATING | COLLAGEN | EXCLUDE | 임신·수유 중에는 콜라겐 보충제를 피하세요 | S-30 |
| R-35 | DRUG | WARFARIN | GLUCOSAMINE | WARN | 와파린과 글루코사민을 함께 드시면 출혈 위험이 높아질 수 있어요. 의사·약사와 상담하세요 | S-25 |
| R-U35 | DRUG | ANTICOAGULANT_UNKNOWN | GLUCOSAMINE | WARN | 항응고제와 글루코사민을 함께 드시면 출혈 위험이 높아질 수 있어요. 의사·약사와 상담하세요 | S-25 |
| R-35b | DISEASE | DIABETES | GLUCOSAMINE | WARN | 글루코사민은 일부 사람에게서 혈당을 올릴 수 있어요. 의사·약사와 상담하세요 | S-25 |
| R-35c | STATUS | PREGNANT_OR_LACTATING | GLUCOSAMINE | EXCLUDE | 임신·수유 중에는 글루코사민을 피하세요 | S-25, S-30 |
| R-36 | DRUG | STATIN | NIACIN | WARN | 스타틴을 드시면 고용량 나이아신은 권장되지 않아요. 의사·약사와 상담하세요 | S-26 |
| R-36b | DRUG | METFORMIN | NIACIN | WARN | 고용량 나이아신은 혈당을 올려 당뇨약 효과를 방해할 수 있어요. 의사·약사와 상담하세요 | S-26, S-30 |
| R-U36b | DRUG | ANTIDIABETIC_UNKNOWN | NIACIN | WARN | 고용량 나이아신은 혈당을 올려 당뇨약 효과를 방해할 수 있어요. 의사·약사와 상담하세요 | S-26, S-30 |
| R-36c | DISEASE | DIABETES | NIACIN | WARN | 고용량 나이아신은 혈당을 올릴 수 있어요. 의사·약사와 상담하세요 | S-26 |
| R-36d | DRUG | WARFARIN | NIACIN | WARN | 항응고제를 드시면 나이아신은 먼저 의사·약사와 상담하세요 | S-30 |
| R-U36d | DRUG | ANTICOAGULANT_UNKNOWN | NIACIN | WARN | 항응고제를 드시면 나이아신은 먼저 의사·약사와 상담하세요 | S-30 |
| R-36e | STATUS | PREGNANT_OR_LACTATING | NIACIN | EXCLUDE | 임신·수유 중에는 나이아신 보충제를 피하세요 | S-30 |
| R-37 | STATUS | PREGNANT_OR_LACTATING | MILK_THISTLE | EXCLUDE | 임신·수유 중에는 밀크씨슬을 피하세요 | S-27, S-30 |
| R-38 | STATUS | PREGNANT_OR_LACTATING | LUTEIN | EXCLUDE | 임신·수유 중에는 루테인 보충제를 피하세요 | S-30 |
| R-39 | DRUG | ANTICONVULSANT | VITAMIN_B6 | WARN | 뇌전증 약을 드시면 비타민 B6는 먼저 의사·약사와 상담하세요 | S-28 |

"애매하면 제외" 원칙으로 `EXCLUDE`로 정한 규칙: R-31(출처는 "면역이 약한 사람"인데 면역억제제로 해석), R-33d(출처는 수유부만 "피할 것"), R-36e. 콜라겐·루테인은 약물 상호작용 출처가 없어 약물 규칙이 없다 (루테인: S-29).

새 약물 계열 코드 5개 (`drugs` 시드에 대표 약 + "이름 모름" 행, 이름 모름 행도 `drug_class`를 같은 코드로 두어 같은 규칙이 걸린다):

| drug_class | 대표 약 | 이름 모름 | 쓰는 규칙 |
| --- | --- | --- | --- |
| ANTIPLATELET | 클로피도그렐 | 항혈소판제 (이름 모름) | R-30b |
| INSULIN | 인슐린 | - | R-33b |
| CHEMOTHERAPY | - | 항암제 (이름 모름) | R-33c |
| IMMUNOSUPPRESSANT | 타크로리무스 | 면역억제제 (이름 모름) | R-31 |
| ANTICONVULSANT | 발프로산 | 뇌전증약 (이름 모름) | R-39 |

출처는 있지만 조건 코드가 없어 이번에 넣지 않은 것:

| 성분 | 내용 | source | 이유 |
| --- | --- | --- | --- |
| GLUCOSAMINE | 게·새우 알레르기 주의 | S-30 | 알레르기 조건 코드 없음 → V3 제품 주의점·리뷰 주의점으로 표시 |
| CURCUMIN | 고흡수 제형 간 손상, 수술 전후 주의 | S-23, S-30 | 간 질환·수술 조건 코드 없음 |
| NIACIN | 장기 고용량 간 문제, 결핵약(이소니아지드) | S-26 | 간 질환·결핵약 코드 없음 |
| VITAMIN_B6 | 결핵약(사이클로세린), 테오필린 | S-28 | 해당 약 코드 없음, 사용 빈도 낮음 |
| MILK_THISTLE | 국화과 알레르기 | S-27 | 알레르기 코드 없음 |
| OMEGA3·PROBIOTICS 등 | "질환이 있거나 의약품 복용 시 상담" 일반 문구 | S-30 | 특정 약·질환이 아니라 규칙으로 만들 수 없음 |

규칙은 회원용 69개(기본 23 + 계열 모름 12 + 진단 질환 2 + 성분 확장 32) + 비회원용 12개(기본 4 + 성분 확장 8), 총 81개다.

진단 질환 규칙(DISEASE, 2026-10-27): 조건 코드는 회원 프로필의 진단 질환 코드(KIDNEY 등) 그대로다. 출처에 "상담" 수준으로만 나와 있어 WARN으로 두었다 ("피해야 한다"가 명확한 경우만 EXCLUDE).
아직 규칙이 없는 질환·성분 (출처 미확인 또는 질환 대상 문구 없음, 약사 검토 후 추가):
- KIDNEY: 비타민 D·칼슘·비타민 C는 MedlinePlus에 "과다 섭취 시 신장 결석·신장 손상" 언급만 있고 신장 질환자 대상 문구는 확인하지 못함. 비타민 A는 신장 관련 문구 없음. NIH ODS 팩트시트는 자동 접근이 막혀(403) 원문을 확인하지 못함
- HYPERTENSION, DIABETES, THYROID: 질환 자체 조건 규칙 없음 (약 계열 규칙 R-13·R-13b·R-11·R-17·R-U01~R-U06으로만 다룸)

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
| S-19 | MedlinePlus Potassium in diet ("If you have kidney disease ... talk to your provider before taking potassium supplements"): https://medlineplus.gov/ency/article/002413.htm, 칼륨 제제 라벨 S-14 |
| S-20 | MedlinePlus Magnesium in diet ("Side effects from high magnesium intake are not common, except in people with significantly reduced kidney function"): https://medlineplus.gov/ency/article/002423.htm |
| S-21 | NIH ODS Omega-3 Fatty Acids ("high doses of omega-3s may cause bleeding problems when taken with warfarin (Coumadin) or other anticoagulant medicines" / FDA: EPA+DHA 보충제 하루 5g 이하): https://ods.od.nih.gov/factsheets/Omega3FattyAcids-Consumer/ |
| S-22 | NIH ODS Probiotics ("Probiotics might also cause problems such as bacterial infections in people who are already seriously ill" / "who have weak immune systems"): https://ods.od.nih.gov/factsheets/Probiotics-Consumer/ |
| S-23 | NCCIH Turmeric ("The use of turmeric supplements during pregnancy may be unsafe." / 고흡수 커큐민 간 손상 보고): https://www.nccih.nih.gov/health/turmeric |
| S-24 | NCCIH Coenzyme Q10 ("CoQ10 may interact with the anticoagulant (blood thinner) warfarin and the diabetes drug insulin" / "it may not be compatible with some types of cancer treatment"): https://www.nccih.nih.gov/health/coenzyme-q10 |
| S-25 | NCCIH Glucosamine and Chondroitin ("increased risk of bleeding in people who are taking the anticoagulant warfarin" / "glucosamine may cause increases in blood glucose (sugar) levels in some people"): https://www.nccih.nih.gov/health/glucosamine-and-chondroitin-for-osteoarthritis-what-you-need-to-know |
| S-26 | NIH ODS Niacin ("experts do not recommend high doses of nicotinic acid for people taking a statin medication" / 1,500mg 이상 니코틴산 혈당 상승·당뇨약 방해 / RDA 남 16·여 14 mg NE, 보충제 상한 35mg): https://ods.od.nih.gov/factsheets/Niacin-Consumer/ |
| S-27 | NCCIH Milk Thistle ("Little is known about whether it's safe to use milk thistle during pregnancy or while breastfeeding." / 국화과 알레르기): https://www.nccih.nih.gov/health/milk-thistle |
| S-28 | NIH ODS Vitamin B6 ("Taking certain epilepsy drugs could decrease vitamin B6 levels and reduce the drugs' ability to control seizures." / RDA 19~50세 1.3mg, 51세+ 남 1.7·여 1.5mg, 상한 100mg): https://ods.od.nih.gov/Pdf/Factsheets/Vitaminb6-Consumer.Pdf |
| S-29 | NEI AREDS/AREDS2 FAQ (루테인: "have not been associated with increased cancer risk", 약물 상호작용 언급 없음 → 루테인 약물 규칙 없음의 근거): https://www.nei.nih.gov/eye-health-information/clinical-trials/age-related-eye-disease-studies-aredsareds2/aredsareds2-frequently-asked-questions |
| S-30 | 식약처 건강기능식품 품목제조신고 I0030 "섭취 시 주의사항" (식품안전나라 오픈API, 2026-10-27 수집, 46,070건). 원료별 신고 문구 집계: 오메가3 "의약품(항응고제, 항혈소판제, 혈압강하제 등) 복용 시 전문가와 상담"(약 800건), 코엔자임Q10 "항응고제 복용 시 전문가와 상담"·"수유부는 섭취를 피할 것", 루테인·밀크씨슬·커큐민 "임산부 및 수유부는 섭취를 피할 것", 글루코사민 "게 또는 새우 알레르기", 콜라겐 "신장질환이 있는 경우 섭취 전 전문가와 상담"·"임산부 및 수유부는 섭취를 피할 것", 나이아신 "당뇨치료제 및 혈액항응고제 복용 시 섭취에 주의". ※ 고시 원문이 아니라 제품 신고 문구 집계다. 식약처 고시 원문(mfds.go.kr)은 자동 접속이 막혀 확인하지 못했다 → 약사 검토 때 고시 원문과 대조 필요 |
