// SUMMARY.md의 수치와 results.csv 대조. 실행: node docs/perf/verify-summary.js  (backend 폴더에서)
// SUMMARY.md에서 "숫자 `{csv:step|label[#n]|열}`" 형식을 찾아, results.csv의 그 줄·열 값과 같은지 본다.
// - 열이 note:key 이면 note 칸의 key=값을 읽는다
// - #n이 없으면 같은 label의 첫 줄, #n이면 n번째 줄
// - SUMMARY에 적힌 자릿수로 CSV 값을 반올림해서 비교한다
// 출처 표시가 없는 수치는 각 STEPxx-report.md 링크로 출처를 단다 (이 스크립트 대상 아님).
const fs = require('fs');
const path = require('path');

const dir = __dirname;
const summary = fs.readFileSync(path.join(dir, 'SUMMARY.md'), 'utf8');
const lines = fs.readFileSync(path.join(dir, 'results.csv'), 'utf8').split(/\r?\n/).filter(l => l.length);

function parseLine(line) {
  const cells = [];
  let cur = '', q = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (q) {
      if (c === '"' && line[i + 1] === '"') { cur += '"'; i++; } else if (c === '"') q = false; else cur += c;
    } else if (c === '"') q = true; else if (c === ',') { cells.push(cur); cur = ''; } else cur += c;
  }
  cells.push(cur);
  return cells;
}
const header = parseLine(lines[0]);
const rows = lines.slice(1).map(l => Object.fromEntries(parseLine(l).map((v, i) => [header[i], v])));

const re = /(-?\d[\d,]*(?:\.\d+)?)\s*%?\s*`\{csv:([^|}]+)\|([^|}]+)\|([^}]+)\}`/g;
let checked = 0;
const failures = [];
for (const m of summary.matchAll(re)) {
  const [, shown, step, labelSpec, column] = m;
  const [label, nth] = labelSpec.split('#');
  const matches = rows.filter(r => r.step === step && r.label === label);
  const row = matches[(nth ? Number(nth) : 1) - 1];
  checked++;
  if (!row) {
    failures.push(`${step}|${labelSpec}: results.csv에 줄 없음`);
    continue;
  }
  let raw;
  if (column.startsWith('note:')) {
    const key = column.slice(5);
    const nm = row.note.match(new RegExp(key + '=([0-9.]+)'));
    raw = nm ? nm[1] : undefined;
  } else {
    raw = row[column];
  }
  if (raw === undefined || raw === '') {
    failures.push(`${step}|${labelSpec}|${column}: 값 없음`);
    continue;
  }
  const shownNum = Number(shown.replace(/,/g, ''));
  const decimals = (shown.split('.')[1] || '').length;
  const csvRounded = Number(Number(raw).toFixed(decimals));
  if (csvRounded !== shownNum) {
    failures.push(`${step}|${labelSpec}|${column}: SUMMARY ${shown} ≠ results.csv ${raw}`);
  }
}
console.log(`대조한 수치: ${checked}, 불일치: ${failures.length}`);
failures.forEach(f => console.log('  - ' + f));
process.exit(failures.length ? 1 : 0);
