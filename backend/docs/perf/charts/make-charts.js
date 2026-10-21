// results.csv → 핵심 추이 차트(SVG) 3개. 실행: node docs/perf/charts/make-charts.js  (backend 폴더에서)
// 라이브러리 없이 SVG를 직접 만든다. 같은 label이 여러 줄이면 가장 마지막 줄(최신 실행)을 쓴다.
// 색: 기본 팔레트 slot 1 파랑 / slot 2 주황 (라이트·다크 각각 색약 분리 검증 통과), 막대 끝 4px 둥글게, 막대 사이 2px 간격.
// 각 막대의 <title>은 브라우저에서 마우스를 올리면 값이 보이는 툴팁이다.
const fs = require('fs');
const path = require('path');

const CSV = path.join(__dirname, '..', 'results.csv');
const OUT = __dirname;

function parseCsv(text) {
  const rows = [];
  for (const line of text.split(/\r?\n/).filter(l => l.length)) {
    const cells = [];
    let cur = '', q = false;
    for (let i = 0; i < line.length; i++) {
      const c = line[i];
      if (q) {
        if (c === '"' && line[i + 1] === '"') { cur += '"'; i++; } else if (c === '"') q = false; else cur += c;
      } else if (c === '"') q = true; else if (c === ',') { cells.push(cur); cur = ''; } else cur += c;
    }
    cells.push(cur);
    rows.push(cells);
  }
  const header = rows.shift();
  return rows.map(r => Object.fromEntries(header.map((h, i) => [h, r[i] ?? ''])));
}

const rows = parseCsv(fs.readFileSync(CSV, 'utf8'));
function row(step, label) {
  const found = rows.filter(r => r.step === step && r.label === label);
  if (!found.length) throw new Error(`results.csv에 ${step},${label} 없음`);
  return found[found.length - 1];
}
function note(r, key) {
  const m = r.note.match(new RegExp(key + '=([0-9.]+)'));
  return m ? Number(m[1]) : NaN;
}

const STYLE = `<style>
  .bg{fill:#fcfcfb}.t1{fill:#0b0b0b}.t2{fill:#52514e}.grid{stroke:#e4e3df}.axis{stroke:#9b9a95}
  .s1{fill:#2a78d6}.s2{fill:#eb6834}
  @media (prefers-color-scheme: dark){.bg{fill:#1a1a19}.t1{fill:#f0efec}.t2{fill:#c3c2b7}.grid{stroke:#33332f}.axis{stroke:#6b6a65}.s1{fill:#3987e5}.s2{fill:#d95926}}
  text{font-family:system-ui,-apple-system,"Segoe UI","Malgun Gothic",sans-serif}
</style>`;

const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
const fmt = v => (Math.abs(v) >= 100 ? Math.round(v).toLocaleString('en-US') : String(Math.round(v * 1000) / 1000));

// 눈금 4칸이 1·2·2.5·5 × 10^n 단위로 떨어지게 최댓값을 올린다
function niceMax(v) {
  const raw = v / 4;
  const p = Math.pow(10, Math.floor(Math.log10(raw)));
  const step = [1, 2, 2.5, 5, 10].map(m => m * p).find(x => x >= raw);
  return step * 4;
}

// 세로 막대 (한 패널). groups: [{name, values:[{series, value}]}]
function panel(x0, y0, w, h, title, groups, seriesNames, maxValue, unit) {
  const top = y0 + 34, bottom = y0 + h - 34, left = x0 + 44, right = x0 + w - 8;
  const plotH = bottom - top;
  const max = niceMax(maxValue || Math.max(...groups.flatMap(g => g.values.map(v => v.value))) * 1.1 || 1);
  let s = `<text x="${x0}" y="${y0 + 16}" class="t1" font-size="14" font-weight="600">${esc(title)}</text>`;
  for (let i = 0; i <= 4; i++) {
    const v = max * i / 4, y = bottom - plotH * i / 4;
    s += `<line x1="${left}" x2="${right}" y1="${y}" y2="${y}" class="grid"/>`;
    s += `<text x="${left - 6}" y="${y + 4}" text-anchor="end" class="t2" font-size="11">${fmt(v)}${unit}</text>`;
  }
  s += `<line x1="${left}" x2="${right}" y1="${bottom}" y2="${bottom}" class="axis"/>`;
  const gw = (right - left) / groups.length;
  const n = seriesNames.length;
  const bw = Math.min(44, (gw * 0.6) / n);
  groups.forEach((g, gi) => {
    const cx = left + gw * gi + gw / 2;
    const start = cx - (bw * n + 2 * (n - 1)) / 2;
    g.values.forEach((v, si) => {
      const bh = Math.max(0, plotH * v.value / max);
      const x = start + si * (bw + 2), y = bottom - bh, r = Math.min(4, bh);
      // 위쪽만 둥근 막대
      s += `<path class="s${si + 1}" d="M${x},${bottom} V${y + r} Q${x},${y} ${x + r},${y} H${x + bw - r} Q${x + bw},${y} ${x + bw},${y + r} V${bottom} Z">`
        + `<title>${esc(g.name)} · ${esc(v.series)}: ${fmt(v.value)}${unit}</title></path>`;
      s += `<text x="${x + bw / 2}" y="${y - 4}" text-anchor="middle" class="t1" font-size="11">${fmt(v.value)}${unit}</text>`;
    });
    s += `<text x="${cx}" y="${bottom + 16}" text-anchor="middle" class="t2" font-size="12">${esc(g.name)}</text>`;
  });
  return s;
}

function legend(x, y, names) {
  return names.map((n, i) => `<rect x="${x + i * 230}" y="${y - 9}" width="10" height="10" rx="2" class="s${i + 1}"/>`
    + `<text x="${x + i * 230 + 16}" y="${y}" class="t2" font-size="12">${esc(n)}</text>`).join('');
}

function svg(w, h, body, title, desc) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-labelledby="t d">`
    + `<title id="t">${esc(title)}</title><desc id="d">${esc(desc)}</desc>${STYLE}`
    + `<rect width="${w}" height="${h}" class="bg"/>${body}</svg>\n`;
}

// 1) STEP 08: 결과 캐시 (in-JVM 부하, WireMock 3초, 2,000건)
{
  const v4 = row('08', 'V4-load-wiremock-2000'), v6 = row('08', 'V6-load-wiremock-2000');
  const calls = [{name: 'V4', values: [{series: 'LLM 호출', value: note(v4, 'llm_calls')}]},
    {name: 'V6 (결과 캐시)', values: [{series: 'LLM 호출', value: note(v6, 'llm_calls')}]}];
  const tps = [{name: 'V4', values: [{series: 'TPS', value: Number(v4.tps)}]},
    {name: 'V6 (결과 캐시)', values: [{series: 'TPS', value: Number(v6.tps)}]}];
  const body = panel(16, 40, 330, 260, 'LLM 호출 수 (요청 2,000건)', calls, ['LLM 호출'], 0, '')
    + panel(366, 40, 330, 260, '처리량 TPS', tps, ['TPS'], 0, '')
    + `<text x="16" y="24" class="t1" font-size="16" font-weight="700">STEP 08 결과 캐시 효과 (in-JVM 부하, LLM = WireMock 3초)</text>`
    + `<text x="16" y="330" class="t2" font-size="11">출처: results.csv 08,V4-load-wiremock-2000 / 08,V6-load-wiremock-2000 (k6 미설치로 대체 측정)</text>`;
  fs.writeFileSync(path.join(OUT, 'step08-cache-load.svg'), svg(712, 342, body, 'STEP 08 결과 캐시 효과',
    `LLM 호출 V4 ${note(v4, 'llm_calls')}, V6 ${note(v6, 'llm_calls')}; TPS V4 ${v4.tps}, V6 ${v6.tps}`));
}

// 2) STEP 09: spike-burst (10초 안에 600건)
{
  const names = ['V9-sync', 'V9-vt', 'V9-queue'];
  const rs = names.map(n => row('09', `${n}-spike-burst`));
  const err = names.map((n, i) => ({name: n.replace('V9-', ''), values: [{series: '에러율', value: Number(rs[i].error_rate) * 100}]}));
  const conc = names.map((n, i) => ({name: n.replace('V9-', ''), values: [{series: 'LLM 동시 호출', value: note(rs[i], 'llm_concurrent_max')}]}));
  const body = panel(16, 40, 330, 260, '에러율 (클라이언트 타임아웃 30초)', err, ['에러율'], 0, '%')
    + panel(366, 40, 330, 260, 'LLM 동시 호출 최대', conc, ['LLM 동시 호출'], 0, '')
    + `<text x="16" y="24" class="t1" font-size="16" font-weight="700">STEP 09 알림 몰림 (10초 안에 600건, Tomcat 50스레드, 큐 워커 20)</text>`
    + `<text x="16" y="330" class="t2" font-size="11">출처: results.csv 09,V9-{sync,vt,queue}-spike-burst 최신 실행 (in-JVM, k6 미설치로 대체 측정)</text>`;
  fs.writeFileSync(path.join(OUT, 'step09-spike-burst.svg'), svg(712, 342, body, 'STEP 09 처리 구조별 알림 몰림 결과',
    names.map((n, i) => `${n} 에러율 ${rs[i].error_rate}, LLM 동시 호출 ${note(rs[i], 'llm_concurrent_max')}`).join('; ')));
}

// 3) STEP 10: 장애 시나리오별 최종 성공률 (폴백 포함)
{
  const scenarios = ['normal', 'slow', 'flaky', 'outage'];
  const groups = scenarios.map(sc => ({name: sc, values: [
    {series: 'V9-queue', value: note(row('10', `V9-queue-${sc}`), 'success_with_fallback') * 100},
    {series: 'V10', value: note(row('10', `V10-${sc}`), 'success_with_fallback') * 100}]}));
  const body = panel(16, 56, 680, 280, '최종 성공률 % (V10은 규칙 기반 폴백 포함)', groups, ['V9-queue', 'V10'], 100, '%')
    + legend(16, 44, ['V9-queue (장애 대응 없음)', 'V10 (재시도·서킷·폴백)'])
    + `<text x="16" y="24" class="t1" font-size="16" font-weight="700">STEP 10 장애 주입 (LLM = WireMock, queue 모드 워커 20)</text>`
    + `<text x="16" y="356" class="t2" font-size="11">출처: results.csv 10,V9-queue-* / 10,V10-* note의 success_with_fallback (폴백 제외 성공률은 STEP10-report.md)</text>`;
  fs.writeFileSync(path.join(OUT, 'step10-fault-success.svg'), svg(712, 368, body, 'STEP 10 장애 시나리오별 최종 성공률',
    groups.map(g => `${g.name}: V9-queue ${fmt(g.values[0].value)}%, V10 ${fmt(g.values[1].value)}%`).join('; ')));
}
console.log('charts written to', OUT);
