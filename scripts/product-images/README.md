# 추천 제품 사진·참고 가격 등록

추천 결과의 "제품 추천 보기"에 나오는 제품에 **사진**과 **참고 가격**을 붙이는 방법이다.
외부 쇼핑 API를 쓰지 않고, 추천에 나올 수 있는 제품만 한 번 정리해서 넣는다.

- 추천에 나올 수 있는 제품 = `review_product_scores`에 있는 활성·국내 반입 가능 제품 (2026-11 기준 54개: DSLD 47, 식약처 7)
- 사진은 **프론트 `frontend/public/static/products/`** 에 넣어 배포와 함께 올린다 (S3·AWS 계정 필요 없음)
- DB `catalog_products.image_url`에 `/static/products/DSLD-284189.jpg` 같은 사이트 안 주소를 저장한다
- 화면: 사진이 있으면 사진, 없으면 브랜드 첫 글자 / 가격이 있으면 "참고 가격 12,900원 (60캡슐 기준)" + "출처 아이허브 · 2026.11.02 기준"(출처는 링크)
- 백엔드 응답 `ReviewProductResponse.Product.imageUrl`, `price { amount, packageSize, source, sourceUrl, checkedAt }`
- 가격의 용량·출처는 `catalog_products.price_package / price_source / price_url / price_checked_at` 컬럼 (`backend/docs/sql/STEP10_product_price_info.sql`로 추가)

작업 파일(CSV, 받은 사진, out 폴더)은 **프로젝트 폴더 밖** (예: `C:\work\product-images\`)에 둔다.

## 1. 제품 목록 뽑기

MySQL Workbench에서 `scripts/product-images/export-products.sql` 실행 → 결과를 CSV로 저장 (`products.csv`)

- 첫 줄에 칸 이름(`catalog_product_id,name,brand,data_source,external_id,...`)이 있어야 한다
- `best_rank`가 1~3인 제품이 화면에 자주 나온다
- 추천 데이터가 새로 들어오면 `SET @only_missing = 1;`로 바꿔서 사진이 없는 제품만 뽑는다
- 로컬 DB에 추천 데이터가 없으면(결과가 몇 줄뿐) `backend/docs/sql/AI_SETUP.sql` → `AI_SETUP_2.sql` 순서로 먼저 실행한다

## 2. 사진 자동 받기 (DSLD 제품)

추천 제품 대부분은 미국 NIH 라벨 DB(DSLD) 제품이다. 라벨 썸네일을 자동으로 받는다 (DSLD 데이터는 CC0).

```powershell
node --use-system-ca scripts/product-images/fetch-dsld-images.mjs C:\work\product-images\products.csv C:\work\product-images\images
```

- `images\<catalog_product_id>.jpg` 로 저장된다. 이미 있는 사진은 건너뛴다 (`--force`로 다시 받기)
- 식약처(MFDS) 제품, 썸네일이 없는 라벨은 `manual-images.txt`에 목록으로 남긴다 → 그 제품만 직접 찾는다
- 네트워크 오류로 못 받은 사진은 다시 실행하면 이어서 받는다
- `--use-system-ca`: 네트워크 보안 장비 때문에 `SELF_SIGNED_CERT_IN_CHAIN`이 나는 환경에서 Windows 인증서를 쓰게 한다 (Node 22.15 이상). 인증서 검사를 끄는 `NODE_TLS_REJECT_UNAUTHORIZED=0`은 쓰지 않는다

## 3. 남은 사진 직접 넣기 + (선택) CSV 채우기

`manual-images.txt`에 나온 제품은 공식 사이트의 제품 사진을 **이미지 저장**으로 받아
`images\<catalog_product_id>.jpg` (png·webp도 됨, 2MB 이하) 이름으로 저장한다.
쇼핑몰 사진을 그대로 가져오지 말고 제조사 공식 이미지를 쓰고 출처를 남긴다.

가격·출처를 넣을 때만 엑셀로 `products.csv`를 고친다 (저장 형식 **"CSV UTF-8(쉼표로 분리)"**).

| 칸 | 넣을 값 | 예 |
|---|---|---|
| `image_file` | 사진 파일 이름 (비워 두면 `<catalog_product_id>.jpg/.png/.webp`를 찾음) | `cgn-d3k2.jpg` |
| `price` | 참고 가격 (원, 쉼표·"원" 있어도 됨, 비워도 됨) | `12900` |
| `image_source` | 사진 출처 (SQL 주석으로 남음) | `고려은단 공식몰` |
| `price_package` | 그 가격의 용량 (100자 이하) | `60캡슐` |
| `price_source` | 가격 출처 이름 (50자 이하, 화면에 보임) | `아이허브` |
| `price_url` | 가격 출처 주소 (**https://** 만, 화면에서 링크) | `https://kr.iherb.com/pr/...` |
| `price_checked_at` | 가격 확인 날짜 | `2026-11-02` |

`price`가 비어 있으면 용량·출처 칸은 쓰지 않는다. 표에 없는 칸(`price_memo` 같은 메모)은 무시된다.

## 4. 프론트에 사진 넣기 + SQL 만들기

프로젝트 폴더에서 실행한다.

```powershell
node scripts/product-images/build-product-images.mjs C:\work\product-images\products.csv C:\work\product-images\images --base-url /static --copy-to frontend/public/static/products
```

- 빠진 사진, 잘못된 가격을 알려준다 (그 제품만 빼고 나머지는 진행)
- 사진을 `frontend/public/static/products/DSLD-284189.jpg` 같은 이름으로 복사한다
  (DB마다 `catalog_product_id`가 다를 수 있어서 파일 이름·SQL 조건은 출처 + 외부 번호를 쓴다)
- `C:\work\product-images\out\product-images.sql` 에 `UPDATE catalog_products ...` 를 만든다

## 5. DB 반영 + 확인

0. (처음 한 번) `backend/docs/sql/STEP10_product_price_info.sql`을 실행해 가격 용량·출처 컬럼을 만든다
1. `out\product-images.sql`을 **로컬 MySQL**(Workbench)에서 실행
2. `pnpm dev` → 맞춤 추천 → 제품 추천 보기에서 사진 확인
3. SQL을 저장소에 공유: `backend/docs/sql/product-images.sql` 로 복사해 커밋
4. 머지·배포 후 **서버 MySQL에서도 STEP10 → product-images.sql 순서로 한 번씩 실행** (docker-compose 초기화 SQL은 DB를 처음 만들 때만 실행됨)
   ```bash
   docker compose exec -T mysql mysql -u root -p yakssok < backend/docs/sql/product-images.sql
   ```

다시 실행해도 같은 값으로 덮어쓰므로 안전하다.

## 나중에 S3로 옮길 때

4단계를 `--base-url https://<버킷>.s3.ap-northeast-2.amazonaws.com` 로 (`--copy-to` 없이) 실행하면
출력되는 `aws s3 sync ...` 명령으로 업로드하고, 새로 만들어진 SQL을 실행하면 된다. 코드 변경은 없다.
(버킷 정책은 `products/*` 만 공개 읽기, AWS 키는 커밋하지 않는다)
