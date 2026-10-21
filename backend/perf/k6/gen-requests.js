// 부하 테스트 요청 세트 생성 (STEP 08). 실행: node perf/k6/gen-requests.js  (backend 폴더에서)
// - 비회원 설문 답 조합 1,000개를 시드 고정 난수로 만든다 (문항·선택지 ID는 docs/sql/STEP02_seed_survey_v1.sql 기준)
// - 요청 2,000건: 상위 20% 조합(200개)이 요청의 60%를 차지하도록 뽑는다 (나머지 40%는 나머지 800개에서 균등)
// 결과: perf/k6/data/requests.json  { profiles: [answers...], sequence: [profileIndex...], distribution: {...} }
const fs = require('fs');
const path = require('path');

const PROFILES = 1000;
const REQUESTS = 2000;
const HOT_SHARE = 0.6;     // 상위 조합이 차지하는 요청 비율
const HOT_PROFILES = 200;  // 상위 20%

let seed = 20261021;
function rand() { // mulberry32
  seed |= 0; seed = seed + 0x6D2B79F5 | 0;
  let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
  t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
  return ((t ^ t >>> 14) >>> 0) / 4294967296;
}
const pick = (arr) => arr[Math.floor(rand() * arr.length)];

// questionId: [optionIds] (seed-survey-v1.sql)
const Q = {GENDER: [1, [1, 2]], SMOKING: [3, [3, 4, 5, 6]], DRINKING: [4, [7, 8, 9]], CAFFEINE: [5, [10, 11, 12]],
  DIET: [6, [13, 14, 15]], SUN: [7, [16, 17]], SLEEP: [8, [18, 19, 20]], FATIGUE: [9, [21, 22, 23]]};
const GOALS = [24, 25, 26, 27, 28];
const BIRTH_YEARS = [1965, 1975, 1985, 1995, 2003];
const SUPPLEMENTS = ['[]', '[{"ingredient":"VITAMIN_C","amount":100,"unit":"mg"}]', '[{"ingredient":"MAGNESIUM","amount":30,"unit":"mg"}]',
  '[{"ingredient":"IRON","amount":10,"unit":"mg"}]'];

const seen = new Set();
const profiles = [];
while (profiles.length < PROFILES) {
  const answers = [];
  for (const [qid, options] of Object.values(Q)) answers.push({questionId: qid, optionIds: [pick(options)]});
  answers.push({questionId: 2, valueNumber: pick(BIRTH_YEARS)});
  const goals = GOALS.filter(() => rand() < 0.3);
  if (goals.length) answers.push({questionId: 10, optionIds: goals});
  answers.push({questionId: 14, valueText: pick(SUPPLEMENTS)});
  const key = JSON.stringify(answers);
  if (seen.has(key)) continue;
  seen.add(key);
  profiles.push(answers);
}

const sequence = [];
for (let i = 0; i < REQUESTS; i++) {
  sequence.push(rand() < HOT_SHARE ? Math.floor(rand() * HOT_PROFILES)
    : HOT_PROFILES + Math.floor(rand() * (PROFILES - HOT_PROFILES)));
}
const hot = sequence.filter(i => i < HOT_PROFILES).length;
const distinct = new Set(sequence).size;
const out = {
  generatedBy: 'perf/k6/gen-requests.js', seed: 20261021,
  distribution: {profiles: PROFILES, requests: REQUESTS, hotProfiles: HOT_PROFILES, hotRequestShare: hot / REQUESTS,
    distinctProfilesInSequence: distinct},
  profiles, sequence};
const file = path.join(__dirname, 'data', 'requests.json');
fs.mkdirSync(path.dirname(file), {recursive: true});
fs.writeFileSync(file, JSON.stringify(out));
console.log(JSON.stringify(out.distribution));
