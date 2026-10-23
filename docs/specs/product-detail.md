# [마이페이지] 영양제 상세 필드·표기 요구사항

- 담당: 주연 (마이페이지·영양정보)
- 관련 이슈: #33 영양제 검색/상세 API
- 데이터: `catalog_products` (현재 예시 데이터, 식약처(MFDS) 연동은 별도 이슈)

## 1. 사용 화면

| 화면 | 위치 | 쓰는 API |
|---|---|---|
| 내가 먹는 영양제 카드 | 마이페이지 "SUPPLEMENTS" 영역, 친구 상세 프로필 | 내 영양제 목록 API(예정) → 제품 정보 포함 |
| 영양제 검색 결과 | 마이페이지 "영양제 등록" | `GET /api/v1/products?keyword=` |
| 영양정보 상세 | 영양제 카드를 눌렀을 때 | `GET /api/v1/products/{productId}` |

## 2. 필드 정의

| 화면 표기 | API 필드 | DB 컬럼 | 타입 | 필수 | 식약처 제공 | 비고 |
|---|---|---|---|---|---|---|
| 제품명 | `productName` | `product_name` | VARCHAR(255) | O | O | |
| 브랜드(제조사) | `manufacturer` | `manufacturer` | VARCHAR(255) | | O | |
| 카테고리 칩 | `category` | `category` | VARCHAR(50) | | X | **신규 컬럼**, 예: 종합비타민, 오메가3, 미네랄 |
| 영양성분 | `ingredientSummary` | `ingredient_summary` | VARCHAR(255) | | △ | **신규 컬럼**, 대표 성분 요약 한 줄 |
| 용량 | `packageSize` | `package_size` | VARCHAR(100) | | X | **신규 컬럼**, 예: 60정 · 2개월분 |
| 가격 | `price` | `price` | INT (원) | | X | **신규 컬럼**, 판매처마다 달라 "참고 가격" |
| 주요 기능 | `mainFunction` | `main_function` | TEXT | | O | 식약처 인정 기능성 문구 |
| 섭취 방법 | `intakeMethod` | `intake_method` | TEXT | | O | |
| 섭취 시 주의사항 | `intakeCaution` | `intake_caution` | TEXT | | O | |
| 제품 이미지 | `imageUrl` | `image_url` | VARCHAR(1000) | | X | |
| 예시 데이터 표시 | `sample` | (`mfds_item_no`가 `SAMPLE-`로 시작) | boolean | | - | 화면에 "예시" 배지 |
| 복용 시간 | (내 영양제 API) | `intake_schedules.meal_slot`, `meal_relation` | | | - | 사용자별 값, 제품 정보 아님 |

- △: 식약처 원재료 정보에서 만들 수 있지만 그대로 쓰기 어려워 요약이 필요
- `catalog_products`에 없는 처방약(MEDICINE)은 `user_products.manual_product_name`만 표시

## 3. 표기 규칙

| 항목 | 값이 있을 때 | 값이 없을 때(null) |
|---|---|---|
| 제품명 | 그대로, 길면 2줄까지 표시 후 말줄임 | (필수라 없음) |
| 브랜드 | 그대로 | 표시 안 함 |
| 카테고리 칩 | 칩으로 표시 | 칩 숨김 |
| 영양성분 | `영양성분` 라벨 + 값 | `-` |
| 용량 | `용량` 라벨 + 값 | `-` |
| 가격 | 천 단위 콤마 + "원" (예: `28,900원`) | 표시 안 함 |
| 주요 기능 / 섭취 방법 | 상세 화면에서만, 줄바꿈 유지 | 해당 블록 숨김 |
| 주의사항 | 상세 화면에서만, 주의 색상으로 강조 | 블록 숨김 |
| 이미지 | 이미지 표시 | 기존 색상 병 그림(`ProductVisual`)으로 대체 |
| 예시 데이터 | 카드/상세에 "예시 데이터" 배지 | - |
| 복용 시간 | `아침 식후`처럼 끼니 + 식전/식후 | 표시 안 함 |

- 끼니 표기: `BREAKFAST` 아침, `LUNCH` 점심, `DINNER` 저녁 / `BEFORE` 식전, `AFTER` 식후, `ANY` 상관없음
- 카드 시안의 "1정", "2캡슐" 같은 1회 섭취 개수는 저장할 곳이 없어 이번 범위에서 제외 (아래 협의 필요 참고)

## 4. 화면별 노출 항목

| 항목 | 영양제 카드 | 검색 결과 | 상세 |
|---|---|---|---|
| 이미지 | O | O | O |
| 카테고리 칩 | O | O | O |
| 브랜드 | O | O | O |
| 제품명 | O | O | O |
| 영양성분 | O | O | O |
| 용량 | O | | O |
| 가격 | O | | O |
| 복용 시간 | O (내 영양제만) | | |
| 주요 기능 | | | O |
| 섭취 방법 | | | O |
| 주의사항 | | | O |
| 예시 배지 | O | O | O |

## 5. DB 변경 SQL

팀 `schema.sql`에 반영했고, 이미 만든 로컬 DB에는 아래를 실행하면 된다.

```sql
ALTER TABLE catalog_products
    ADD COLUMN category VARCHAR(50) NULL,
    ADD COLUMN ingredient_summary VARCHAR(255) NULL,
    ADD COLUMN package_size VARCHAR(100) NULL,
    ADD COLUMN price INT NULL;
```

- 앱의 `ddl-auto: update`로도 컬럼은 자동 추가되지만, 예시 값은 `schema.sql`의 `UPDATE catalog_products ...` 부분을 실행해야 채워진다.
- 영향 기능: 영양제 검색/상세 API(#33), 이후 내 영양제 목록·홈의 제품 표시

## 6. 협의 필요

- 가격: 판매처마다 달라 "참고 가격"으로만 쓸지, 화면에서 뺄지 (채원·팀)
- 1회 섭취 개수("1정"): `intake_schedules`에 `dose`(예: 1정) 컬럼을 추가할지 (홈·알림 담당과 협의)
- 식약처 연동 시 `category`, `ingredient_summary`를 어떤 원본 항목에서 만들지 (MFDS 연동 이슈에서 결정)
