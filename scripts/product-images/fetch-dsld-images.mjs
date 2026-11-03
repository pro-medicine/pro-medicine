#!/usr/bin/env node
// DSLD(미국 NIH 영양제 라벨 DB) 라벨 썸네일 자동 다운로드 (scripts/product-images/README.md 2단계)
// export-products.sql로 뽑은 CSV에서 data_source = DSLD 인 제품의 external_id(DSLD 라벨 번호)로
//   https://api.ods.od.nih.gov/dsld/s3/pdf/thumbnails/<라벨 번호>.jpg
// 를 받아 <이미지 폴더>/<catalog_product_id>.jpg 로 저장한다. (DSLD 데이터는 CC0 퍼블릭 도메인)
// 썸네일이 없는 라벨(404), 식약처 제품 등은 "직접 찾을 제품"으로 알려준다.
//
// 사용법:
//   node scripts/product-images/fetch-dsld-images.mjs <CSV> <이미지 폴더> [--force]
//   --force: 이미 있는 사진도 다시 받기 (기본은 건너뜀 → 직접 넣은 사진을 덮어쓰지 않음)
// 필요: Node 18 이상 (기본 fetch 사용, 설치할 라이브러리 없음)

import fs from "node:fs";
import path from "node:path";
import { parseCsv } from "./build-product-images.mjs";

// 테스트할 때만 DSLD_THUMBNAIL_BASE 환경변수로 주소를 바꿀 수 있다
const THUMBNAIL_BASE = process.env.DSLD_THUMBNAIL_BASE ?? "https://api.ods.od.nih.gov/dsld/s3/pdf/thumbnails";
const THUMBNAIL_URL = (labelId) => `${THUMBNAIL_BASE}/${labelId}.jpg`;
const TIMEOUT_MS = 15_000;
const DELAY_MS = 300; // 공공 서버에 부담을 주지 않도록 한 장씩 천천히
const EXISTING = [".jpg", ".jpeg", ".png", ".webp"];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function download(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(url, { signal: controller.signal });
    if (res.status === 404 || res.status === 403) return { missing: true };
    if (!res.ok) return { error: `HTTP ${res.status}` };
    const type = res.headers.get("content-type") ?? "";
    if (!type.startsWith("image/")) return { missing: true }; // 이미지가 아닌 응답(오류 페이지 등)
    const bytes = Buffer.from(await res.arrayBuffer());
    if (bytes.length < 1024) return { missing: true }; // 1KB 미만은 빈 이미지로 봄
    return { bytes };
  } catch (e) {
    if (e.name === "AbortError") return { error: "시간 초과" };
    // "fetch failed"만으로는 원인을 알 수 없어서 실제 원인 코드도 같이 보여준다 (인증서, DNS, 연결 거부 등)
    const cause = e.cause?.code ?? e.cause?.message;
    return { error: cause ? `${e.message} (${cause})` : e.message };
  } finally {
    clearTimeout(timer);
  }
}

async function main() {
  const args = process.argv.slice(2);
  const force = args.includes("--force");
  const [csvPath, imageDir] = args.filter((a) => !a.startsWith("--"));
  if (!csvPath || !imageDir) {
    console.error("사용법: node scripts/product-images/fetch-dsld-images.mjs <CSV> <이미지 폴더> [--force]");
    process.exit(1);
  }
  if (typeof fetch !== "function") {
    console.error("Node 18 이상이 필요해요. (node -v 로 확인)");
    process.exit(1);
  }

  const rows = parseCsv(fs.readFileSync(csvPath, "utf8"));
  if (rows.length === 0 || !("data_source" in rows[0]) || !("external_id" in rows[0])) {
    console.error("CSV에 data_source, external_id 칸이 없어요. 최신 export-products.sql로 다시 뽑아 주세요.");
    process.exit(1);
  }
  fs.mkdirSync(imageDir, { recursive: true });

  let saved = 0;
  let skipped = 0;
  const manual = []; // 직접 찾아야 할 제품

  for (const row of rows) {
    const id = row.catalog_product_id;
    const label = `${id} ${row.name ?? ""} (${row.brand || "브랜드 없음"})`;
    const already = EXISTING.some((ext) => fs.existsSync(path.join(imageDir, `${id}${ext}`)));
    if (already && !force) {
      skipped++;
      continue;
    }
    if (row.data_source !== "DSLD" || !/^\d+$/.test(row.external_id ?? "")) {
      manual.push(`${label} - ${row.data_source || "출처 없음"} 제품이라 자동으로 받을 수 없어요`);
      continue;
    }

    const result = await download(THUMBNAIL_URL(row.external_id));
    if (result.bytes) {
      fs.writeFileSync(path.join(imageDir, `${id}.jpg`), result.bytes);
      saved++;
      console.log(`  ✔ ${label}`);
    } else if (result.missing) {
      manual.push(`${label} - DSLD에 썸네일이 없어요 (라벨 ${row.external_id})`);
    } else {
      manual.push(`${label} - 받지 못했어요: ${result.error} (다시 실행하면 이어서 받아요)`);
    }
    await sleep(DELAY_MS);
  }

  console.log(`\n✔ 새로 받은 사진 ${saved}개, 이미 있어서 건너뛴 사진 ${skipped}개`);
  if (manual.length > 0) {
    const listPath = path.join(path.dirname(csvPath), "manual-images.txt");
    fs.writeFileSync(listPath, manual.join("\n") + "\n", "utf8");
    console.log(`\n⚠ 직접 찾아야 할 제품 ${manual.length}개 (목록: ${listPath})`);
    manual.forEach((m) => console.log(`  - ${m}`));
    console.log(`\n  → 공식 사이트 등에서 "<catalog_product_id>.jpg" 이름으로 ${imageDir} 폴더에 저장하고, CSV image_source에 출처를 적어 주세요.`);
  }
  console.log("\n다음 단계: build-product-images.mjs 실행 (README 3단계)\n");
}

main();
