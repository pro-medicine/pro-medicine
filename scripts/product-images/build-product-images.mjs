#!/usr/bin/env node
// 추천 제품 이미지·참고 가격 등록 도우미 (scripts/product-images/README.md 2~3단계)
// 채운 CSV + 이미지 폴더를 읽어서
//   1) 빠진 이미지·잘못된 가격을 알려주고
//   2) S3에 올릴 폴더(out/upload)를 "<catalog_product_id>.<확장자>" 이름으로 준비하고
//   3) DB에 넣을 UPDATE SQL(out/product-images.sql)을 만든다.
// 외부 라이브러리 없이 Node 기본 기능만 사용한다. S3 업로드는 출력되는 AWS CLI 명령으로 한다.
//
// 사용법:
//   node scripts/product-images/build-product-images.mjs <채운 CSV> <이미지 폴더> --base-url <S3 또는 CloudFront 주소> [--prefix products/]
// 예:
//   node scripts/product-images/build-product-images.mjs products.csv images --base-url https://yakssok-images.s3.ap-northeast-2.amazonaws.com

import fs from "node:fs";
import path from "node:path";

const IMAGE_TYPES = { ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp" };
const MAX_IMAGE_BYTES = 2 * 1024 * 1024; // 2MB 넘는 사진은 줄여서 올리기 (화면에는 64px로 보임)

// ---------- 인자 ----------
function parseArgs(argv) {
  const positional = [];
  const options = { prefix: "products/" };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--base-url") options.baseUrl = argv[++i];
    else if (argv[i] === "--prefix") options.prefix = argv[++i];
    else if (argv[i] === "--out") options.out = argv[++i];
    else positional.push(argv[i]);
  }
  options.csv = positional[0];
  options.imageDir = positional[1];
  return options;
}

// ---------- CSV 읽기 (엑셀 "CSV UTF-8" 형식: BOM, 따옴표, 줄바꿈 처리) ----------
export function parseCsv(text) {
  const rows = [];
  let row = [];
  let cell = "";
  let quoted = false;
  const src = text.replace(/^﻿/, "");
  for (let i = 0; i < src.length; i++) {
    const ch = src[i];
    if (quoted) {
      if (ch === '"' && src[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (ch === '"') {
        quoted = false;
      } else {
        cell += ch;
      }
    } else if (ch === '"') {
      quoted = true;
    } else if (ch === ",") {
      row.push(cell);
      cell = "";
    } else if (ch === "\n" || ch === "\r") {
      if (ch === "\r" && src[i + 1] === "\n") i++;
      row.push(cell);
      rows.push(row);
      row = [];
      cell = "";
    } else {
      cell += ch;
    }
  }
  if (cell !== "" || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  const [header, ...body] = rows.filter((r) => r.some((c) => c.trim() !== ""));
  if (!header) return [];
  const keys = header.map((h) => h.trim());
  return body.map((r) => Object.fromEntries(keys.map((k, i) => [k, (r[i] ?? "").trim()])));
}

// 가격: "12,900", "12900원", "₩12,900" → 12900 / 빈칸 → null / 이상한 값 → NaN
export function parsePrice(value) {
  if (!value) return null;
  const digits = value.replace(/[,\s원₩]/g, "");
  return /^\d+$/.test(digits) ? Number(digits) : Number.NaN;
}

// SQL 문자열 안전하게 (작은따옴표·역슬래시)
const sqlText = (v) => (v == null ? "NULL" : `'${String(v).replace(/\\/g, "\\\\").replace(/'/g, "''")}'`);

// 이미지 파일 찾기: CSV의 image_file이 있으면 그 파일, 없으면 "<id>.jpg|png|webp"
function findImage(imageDir, row) {
  const candidates = row.image_file
    ? [row.image_file]
    : Object.keys(IMAGE_TYPES).map((ext) => `${row.catalog_product_id}${ext}`);
  for (const name of candidates) {
    const full = path.join(imageDir, name);
    if (fs.existsSync(full)) return full;
  }
  return null;
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  if (!options.csv || !options.imageDir || !options.baseUrl) {
    console.error(
      "사용법: node scripts/product-images/build-product-images.mjs <채운 CSV> <이미지 폴더> --base-url <S3 주소> [--prefix products/]",
    );
    process.exit(1);
  }
  const baseUrl = options.baseUrl.replace(/\/+$/, "");
  const prefix = options.prefix.replace(/^\/+/, "").replace(/\/?$/, "/");
  const outDir = options.out ?? path.join(path.dirname(options.csv), "out");
  const uploadDir = path.join(outDir, "upload");
  fs.rmSync(uploadDir, { recursive: true, force: true });
  fs.mkdirSync(uploadDir, { recursive: true });

  const rows = parseCsv(fs.readFileSync(options.csv, "utf8"));
  if (rows.length === 0 || !("catalog_product_id" in rows[0])) {
    console.error("CSV 첫 줄에 catalog_product_id 칸이 있어야 해요. export-products.sql 결과를 그대로 저장했는지 확인해 주세요.");
    process.exit(1);
  }

  const updates = [];
  const problems = [];
  let imageCount = 0;
  let priceCount = 0;

  for (const row of rows) {
    const id = row.catalog_product_id;
    const label = `${id} ${row.name ?? ""}`.trim();
    if (!/^\d+$/.test(id)) {
      problems.push(`${label}: catalog_product_id가 숫자가 아니에요`);
      continue;
    }

    const sets = [];
    const image = findImage(options.imageDir, row);
    if (image) {
      const ext = path.extname(image).toLowerCase();
      const size = fs.statSync(image).size;
      if (!IMAGE_TYPES[ext]) {
        problems.push(`${label}: ${path.basename(image)} → jpg, png, webp만 올릴 수 있어요`);
      } else if (size > MAX_IMAGE_BYTES) {
        problems.push(`${label}: ${path.basename(image)} 크기가 ${(size / 1024 / 1024).toFixed(1)}MB예요 (2MB 이하로 줄여 주세요)`);
      } else {
        const key = `${prefix}${id}${ext === ".jpeg" ? ".jpg" : ext}`;
        fs.copyFileSync(image, path.join(uploadDir, path.basename(key)));
        sets.push(`image_key = ${sqlText(key)}`, `image_url = ${sqlText(`${baseUrl}/${key}`)}`);
        imageCount++;
      }
    } else if (row.image_file) {
      problems.push(`${label}: 이미지 파일 "${row.image_file}"을 찾을 수 없어요`);
    }

    const price = parsePrice(row.price);
    if (Number.isNaN(price)) {
      problems.push(`${label}: 가격 "${row.price}"을 숫자로 읽을 수 없어요 (예: 12900)`);
    } else if (price != null) {
      sets.push(`price = ${price}`);
      priceCount++;
    }

    if (sets.length > 0) {
      const source = row.image_source ? ` -- 이미지 출처: ${row.image_source.replace(/[\r\n]/g, " ")}` : "";
      updates.push(`UPDATE catalog_products SET ${sets.join(", ")} WHERE catalog_product_id = ${id};${source}`);
    }
  }

  const today = new Date().toISOString().slice(0, 10);
  const sql = [
    "-- 추천 제품 이미지·참고 가격 (scripts/product-images로 생성, 예시가 아닌 수동 등록 데이터)",
    `-- 생성일: ${today} / 이미지 ${imageCount}개, 가격 ${priceCount}개`,
    "-- 실행: 로컬 MySQL과 서버 MySQL에 각각 한 번씩. 다시 실행해도 같은 값으로 덮어써서 안전하다.",
    "",
    ...updates,
    "",
  ].join("\n");
  fs.writeFileSync(path.join(outDir, "product-images.sql"), sql, "utf8");

  console.log(`\n✔ 제품 ${rows.length}개 확인: 이미지 ${imageCount}개, 가격 ${priceCount}개`);
  console.log(`✔ SQL: ${path.join(outDir, "product-images.sql")}`);
  console.log(`✔ 업로드 폴더: ${uploadDir}`);
  if (problems.length > 0) {
    console.log(`\n⚠ 확인이 필요한 제품 ${problems.length}개 (SQL에서 빠졌어요):`);
    problems.forEach((p) => console.log(`  - ${p}`));
  }
  const missing = rows.filter((r) => /^\d+$/.test(r.catalog_product_id) && !findImage(options.imageDir, r)).length;
  if (missing > 0) console.log(`\nℹ 이미지가 아직 없는 제품 ${missing}개 (화면에는 브랜드 첫 글자로 보여요)`);

  const bucketHint = baseUrl.match(/^https:\/\/([^.]+)\.s3[.-]/)?.[1] ?? "<버킷 이름>";
  console.log("\n다음 단계: S3에 업로드 (AWS CLI)");
  console.log(`  aws s3 sync "${uploadDir}" s3://${bucketHint}/${prefix} --cache-control "public, max-age=604800"`);
  console.log("그다음 product-images.sql을 MySQL에서 실행하세요.\n");
}

if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith("build-product-images.mjs")) {
  main();
}
