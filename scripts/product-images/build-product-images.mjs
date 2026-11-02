#!/usr/bin/env node
// 추천 제품 이미지·참고 가격 등록 도우미 (scripts/product-images/README.md 2~3단계)
// 채운 CSV + 이미지 폴더를 읽어서
//   1) 빠진 이미지·잘못된 가격을 알려주고
//   2) S3에 올릴 폴더(out/upload)를 "<catalog_product_id>.<확장자>" 이름으로 준비하고
//   3) DB에 넣을 UPDATE SQL(out/product-images.sql)을 만든다. (사진, 참고 가격 + 용량·출처·확인 날짜)
//   4) --copy-to 를 주면 사진을 그 폴더(예: frontend/public/static/products)에 바로 복사한다.
// 외부 라이브러리 없이 Node 기본 기능만 사용한다.
// 기본 방식: 사진을 프론트 public 폴더에 넣어 배포와 함께 올린다 (--base-url /static --copy-to frontend/public/static/products)
// S3를 쓸 때: --base-url <S3 주소> 로 실행하고 출력되는 AWS CLI 명령으로 업로드한다.
//
// 사용법:
//   node scripts/product-images/build-product-images.mjs <CSV> <이미지 폴더> --base-url <주소> [--prefix products/] [--copy-to <폴더>]
// 예 (프론트에 포함, 기본):
//   node scripts/product-images/build-product-images.mjs products.csv images --base-url /static --copy-to frontend/public/static/products
// 예 (S3):
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
    else if (argv[i] === "--copy-to") options.copyTo = argv[++i];
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

// 가격의 용량·출처·확인 날짜 (STEP10 컬럼). 빈칸이면 NULL로 지운다 (예전 값이 남지 않게)
// price_url은 화면에서 링크가 되므로 https 주소만 받는다
export function priceInfo(row) {
  const pkg = (row.price_package ?? "").trim();
  const source = (row.price_source ?? "").trim();
  const url = (row.price_url ?? "").trim();
  const date = (row.price_checked_at ?? "").trim();
  if (pkg.length > 100) return { problem: `price_package는 100자 이하로 적어 주세요` };
  if (source.length > 50) return { problem: `price_source는 50자 이하로 적어 주세요` };
  if (url && !/^https:\/\/[^\s"'<>]+$/.test(url)) return { problem: `price_url "${url}"은 https:// 로 시작하는 주소여야 해요` };
  if (date && !/^\d{4}-\d{2}-\d{2}$/.test(date)) return { problem: `price_checked_at "${date}"은 2026-11-02 형식이어야 해요` };
  return {
    sets: [
      `price_package = ${sqlText(pkg || null)}`,
      `price_source = ${sqlText(source || null)}`,
      `price_url = ${sqlText(url || null)}`,
      `price_checked_at = ${sqlText(date || null)}`,
    ],
  };
}

// SQL 문자열 안전하게 (작은따옴표·역슬래시)
const sqlText = (v) => (v == null ? "NULL" : `'${String(v).replace(/\\/g, "\\\\").replace(/'/g, "''")}'`);

// 같은 제품을 가리키는 조건. catalog_product_id는 로컬 DB와 서버 DB에서 다를 수 있으므로
// (AUTO_INCREMENT) 출처 + 외부 번호(DSLD 라벨 번호, 식약처 신고번호)가 있으면 그걸로 찾는다
const hasExternal = (row) => !!row.data_source && !!row.external_id;
const fileStem = (row) =>
  hasExternal(row) ? `${row.data_source}-${row.external_id}`.replace(/[^A-Za-z0-9_-]/g, "_") : row.catalog_product_id;
const whereOf = (row) =>
  hasExternal(row)
    ? `data_source = ${sqlText(row.data_source)} AND external_id = ${sqlText(row.external_id)}`
    : `catalog_product_id = ${row.catalog_product_id}`;

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
      "사용법: node scripts/product-images/build-product-images.mjs <CSV> <이미지 폴더> --base-url /static --copy-to frontend/public/static/products",
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
        // S3 파일 이름: DB마다 catalog_product_id가 다를 수 있어서, 있으면 출처+외부 번호로 (예: DSLD-315732.jpg)
        const key = `${prefix}${fileStem(row)}${ext === ".jpeg" ? ".jpg" : ext}`;
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
      const info = priceInfo(row);
      if (info.problem) {
        problems.push(`${label}: ${info.problem}`);
      } else {
        sets.push(`price = ${price}`, ...info.sets);
        priceCount++;
      }
    }

    if (sets.length > 0) {
      const source = row.image_source ? ` -- 이미지 출처: ${row.image_source.replace(/[\r\n]/g, " ")}` : "";
      updates.push(`UPDATE catalog_products SET ${sets.join(", ")} WHERE ${whereOf(row)};${source}`);
    }
  }

  const today = new Date().toISOString().slice(0, 10);
  const sql = [
    "-- 추천 제품 이미지·참고 가격 (scripts/product-images로 생성, 예시가 아닌 수동 등록 데이터)",
    `-- 생성일: ${today} / 이미지 ${imageCount}개, 가격 ${priceCount}개`,
    "-- 실행: 로컬 MySQL과 서버 MySQL에 각각 한 번씩. 다시 실행해도 같은 값으로 덮어써서 안전하다.",
    "-- 제품은 출처 + 외부 번호로 찾으므로 DB마다 catalog_product_id가 달라도 같은 제품에 들어간다.",
    "-- 가격의 용량·출처 컬럼(price_package 등)이 없다는 오류가 나면 backend/docs/sql/STEP10_product_price_info.sql을 먼저 실행한다.",
    "USE yakssok;",
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

  if (options.copyTo) {
    // 프론트 public 폴더로 복사 (같은 이름은 덮어씀, 다른 파일은 그대로 둠)
    fs.mkdirSync(options.copyTo, { recursive: true });
    const files = fs.readdirSync(uploadDir);
    files.forEach((name) => fs.copyFileSync(path.join(uploadDir, name), path.join(options.copyTo, name)));
    console.log(`✔ 사진 ${files.length}개를 ${options.copyTo} 에 복사했어요`);
    console.log("\n다음 단계: product-images.sql을 MySQL에서 실행하고, pnpm dev로 추천 화면을 확인하세요.\n");
    return;
  }
  const bucketHint = baseUrl.match(/^https:\/\/([^.]+)\.s3[.-]/)?.[1] ?? "<버킷 이름>";
  console.log("\n다음 단계: S3에 업로드 (AWS CLI)");
  console.log(`  aws s3 sync "${uploadDir}" s3://${bucketHint}/${prefix} --cache-control "public, max-age=604800"`);
  console.log("그다음 product-images.sql을 MySQL에서 실행하세요.\n");
}

if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith("build-product-images.mjs")) {
  main();
}
