# 추천 제품 이미지·참고 가격 등록

추천 결과의 "제품 추천 보기"에 나오는 제품에 **사진**과 **참고 가격**을 붙이는 방법이다.
외부 쇼핑 API를 쓰지 않고, 추천에 나올 수 있는 제품만 사람이 한 번 채워서 S3와 DB에 넣는다.

- 추천에 나올 수 있는 제품 = `review_product_scores`에 있는 제품 (2026-11 기준 약 60개, 성분별 상위 3개는 약 33개)
- 화면: 사진이 있으면 사진, 없으면 브랜드 첫 글자 / 가격이 있으면 "참고 가격 12,900원"
- 백엔드는 `catalog_products.image_url`, `price`를 그대로 읽는다 (응답 `ReviewProductResponse.Product.imageUrl`, `price`)

## 0. 처음 한 번: S3 버킷과 AWS CLI

1. AWS 콘솔 → S3 → 버킷 만들기
   - 리전: 아시아 태평양(서울) `ap-northeast-2`
   - 이름 예: `yakssok-images` (전 세계에서 하나뿐이어야 함)
   - "모든 퍼블릭 액세스 차단"에서 **버킷 정책으로 허용하는 부분만** 해제
2. 버킷 → 권한 → 버킷 정책: `products/` 아래 파일만 누구나 읽을 수 있게
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Principal": "*",
       "Action": "s3:GetObject",
       "Resource": "arn:aws:s3:::yakssok-images/products/*"
     }]
   }
   ```
3. AWS CLI 설치 후 `aws configure` (액세스 키는 본인 PC에만. 채팅·코드·커밋에 넣지 않는다)
   - 업로드할 IAM 사용자에게는 이 버킷의 `s3:PutObject`, `s3:ListBucket` 권한만 준다

## 1. 제품 목록 뽑기

MySQL Workbench에서 `scripts/product-images/export-products.sql` 실행
→ 결과 창 Export → **CSV** 로 저장 (예: `products.csv`)

- `best_rank`가 1~3인 제품이 화면에 자주 나온다 → 먼저 채운다
- 추천 데이터가 새로 들어오면 `SET @only_missing = 1;`로 바꿔서 이미지가 없는 제품만 뽑는다

## 2. 사진 자동 받기 (DSLD 제품)

추천 제품 대부분은 미국 NIH 라벨 DB(DSLD) 제품이다. 라벨 썸네일을 자동으로 받는다 (DSLD 데이터는 CC0).

```powershell
node scripts/product-images/fetch-dsld-images.mjs products.csv images
```

- `images/<catalog_product_id>.jpg` 로 저장된다. 이미 있는 사진은 건너뛴다 (`--force`로 다시 받기)
- 썸네일이 없는 라벨, 식약처(MFDS) 제품은 `manual-images.txt`에 목록으로 남긴다 → 그 제품만 직접 찾는다
- 네트워크 오류로 못 받은 사진은 다시 실행하면 이어서 받는다
- DSLD 사진은 라벨(포장지) 촬영본이다
- `fetch failed (SELF_SIGNED_CERT_IN_CHAIN)` 이 나오면 네트워크 보안 장비 인증서를 Node가 모르는 것이다.
  `node --use-system-ca scripts/...` 처럼 옵션을 붙여 Windows 인증서를 쓰게 한다 (Node 22.15 이상).
  인증서 검사를 끄는 `NODE_TLS_REJECT_UNAUTHORIZED=0`은 쓰지 않는다

## 2-1. 남은 사진 직접 넣기 + CSV 채우기

엑셀로 `products.csv`를 열고 아래 칸만 채운다. 저장할 때 **"CSV UTF-8(쉼표로 분리)"**.

| 칸 | 넣을 값 | 예 |
|---|---|---|
| `image_file` | 사진 파일 이름 (비워 두면 `<catalog_product_id>.jpg/.png/.webp`를 찾음) | `cgn-d3k2.jpg` |
| `price` | 참고 가격 (원, 쉼표·"원" 있어도 됨) | `12900` |
| `image_source` | 사진 출처 (SQL 주석으로 남음) | `제조사 공식 사이트` |

`manual-images.txt`에 나온 제품 사진은 `images/<catalog_product_id>.jpg` 이름으로 저장한다. jpg·png·webp, 2MB 이하.
쇼핑몰 사진을 그대로 가져오지 말고, 제조사 공식 이미지(출처 기록)나 직접 찍은 사진을 쓴다.

## 3. 스크립트 실행

```powershell
node scripts/product-images/build-product-images.mjs products.csv images --base-url https://yakssok-images.s3.ap-northeast-2.amazonaws.com
```

- 빠진 사진, 잘못된 가격을 알려준다 (그 제품만 빼고 나머지는 진행)
- `out/upload/` 에 `<id>.jpg` 이름으로 사진을 모은다
- `out/product-images.sql` 에 `UPDATE catalog_products ...` 를 만든다
- 마지막에 S3 업로드 명령을 출력한다

## 4. S3 업로드 + DB 반영

```powershell
aws s3 sync "out/upload" s3://yakssok-images/products/ --cache-control "public, max-age=604800"
```

`out/product-images.sql`을 **로컬 MySQL**과 **서버 MySQL**에서 각각 실행한다.
다시 실행해도 같은 값으로 덮어쓰므로 안전하다.

서버(EC2)에서 실행 예:
```bash
docker compose exec -T mysql mysql -u root -p yakssok < product-images.sql
```

팀원 로컬 DB에도 들어가도록 `out/product-images.sql`을 `backend/docs/sql/`에 커밋해 공유한다.
(docker-compose의 초기화 SQL은 DB를 처음 만들 때만 실행되므로, 이미 만들어진 DB에는 직접 실행해야 한다)

## 주의

- 가격은 참고용이다. 화면에도 "가격은 참고용이에요. 판매처와 시기에 따라 달라요."가 같이 나온다.
- 버킷 이름·주소는 비밀이 아니지만, **AWS 액세스 키는 절대 커밋하지 않는다.**
