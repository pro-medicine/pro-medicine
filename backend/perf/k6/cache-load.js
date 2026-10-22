// STEP 08 부하 테스트: 같은 요청 세트를 V4 / V6 서버에 보내 TPS, p95, LLM 호출 수를 비교한다.
// 준비 (backend 폴더):
//   1) node perf/k6/gen-requests.js
//   2) WireMock (LLM 대체, 고정 지연 3초):
//      docker run --rm -p 8089:8080 -v "$PWD/perf/wiremock/normal:/home/wiremock" wiremock/wiremock:3.13.2 --async-response-enabled=true --container-threads=500
//   3) 서버 (V4 또는 V6). API 키는 WireMock이라 아무 값이나 된다:
//      OPENAI_API_KEY=dummy ./gradlew bootRun --args='--llm.provider=spring-ai --spring.ai.model.chat=openai --spring.ai.openai.base-url=http://localhost:8089/v1 --recommendation.pipeline-version=V4'
//      (로컬 MySQL에 docs/sql/STEP02~07 시드가 있어야 한다)
// 실행:
//   k6 run -e BASE_URL=http://localhost:8080 -e LABEL=V4 perf/k6/cache-load.js
//   k6 run -e BASE_URL=http://localhost:8080 -e LABEL=V6 perf/k6/cache-load.js
// LLM 호출 수: curl -s http://localhost:8089/__admin/requests/count -d '{"method":"POST","url":"/v1/chat/completions"}'
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const data = JSON.parse(open('./data/requests.json'));
const profiles = new SharedArray('profiles', () => data.profiles);
const sequence = new SharedArray('sequence', () => data.sequence);
const TOTAL = Number(__ENV.REQUESTS || 600);

export const options = {
  scenarios: {
    cache: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 50),
      iterations: TOTAL,
      maxDuration: '10m',
    },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  tags: { label: __ENV.LABEL || 'unknown' },
};

const json = { headers: { 'Content-Type': 'application/json' } };

export default function () {
  const i = exec.scenario.iterationInTest;
  const answers = profiles[sequence[i % sequence.length]];
  const submit = http.post(`${BASE_URL}/api/v1/surveys/1/responses`, JSON.stringify({ answers }), json);
  if (!check(submit, { 'survey 200': (r) => r.status === 200 })) {
    return;
  }
  const responseId = submit.json('data.responseId');
  const rec = http.post(`${BASE_URL}/api/v1/recommendations`, JSON.stringify({ responseId }),
    Object.assign({ tags: { name: 'recommend' } }, json));
  check(rec, { 'recommend 200': (r) => r.status === 200 });
}
