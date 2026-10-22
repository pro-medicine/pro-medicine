// STEP 09 처리 구조 비교: steady / spike-burst / spike-spread 시나리오 (LLM은 WireMock 고정 지연 3초)
// 준비 (backend 폴더):
//   1) WireMock: docker run --rm -p 8089:8080 -v "$PWD/perf/wiremock/normal:/home/wiremock" wiremock/wiremock:3.13.2 --async-response-enabled=true --container-threads=800
//   2) 서버 (세 구조 중 하나). 같은 자원 조건: --server.tomcat.threads.max=50
//      V9-sync : OPENAI_API_KEY=dummy ./gradlew bootRun --args='--llm.provider=spring-ai --spring.ai.model.chat=openai --spring.ai.openai.base-url=http://localhost:8089/v1 --recommendation.pipeline-version=V4 --server.tomcat.threads.max=50'
//      V9-vt   : 위 + --spring.threads.virtual.enabled=true
//      V9-queue: 위 + --recommendation.api-mode=queue --recommendation.worker.concurrency=20 (Redis 큐면 --recommendation.queue.store=redis)
//   3) 응답 ID 준비: 비회원 설문을 N건 제출해 둔다 (setup()이 한다)
// 실행:
//   k6 run -e SCENARIO=steady       -e MODE=sync  perf/k6/spike.js
//   k6 run -e SCENARIO=spike-burst  -e MODE=queue perf/k6/spike.js
//   k6 run -e SCENARIO=spike-spread -e MODE=vt    perf/k6/spike.js
// MODE=queue면 202 접수 후 GET으로 완료까지 기다린 시간(complete_ms)을 따로 기록한다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SCENARIO = __ENV.SCENARIO || 'spike-burst';
const MODE = __ENV.MODE || 'sync';
const N = Number(__ENV.N || 600);

const acceptMs = new Trend('accept_ms', true);
const completeMs = new Trend('complete_ms', true);
const failed = new Rate('failed');

const scenarios = {
  // 초당 10건 30초
  steady: { executor: 'constant-arrival-rate', rate: 10, timeUnit: '1s', duration: '30s', preAllocatedVUs: 400, maxVUs: 2000 },
  // 알림 동시 발송 가정: 10초 안에 N명
  'spike-burst': { executor: 'constant-arrival-rate', rate: N / 10, timeUnit: '1s', duration: '10s', preAllocatedVUs: 800, maxVUs: 3000 },
  // 발송 분산(30분)을 60초로 줄여 같은 N명을 고르게
  'spike-spread': { executor: 'constant-arrival-rate', rate: N / 60, timeUnit: '1s', duration: '60s', preAllocatedVUs: 400, maxVUs: 2000 },
};

export const options = {
  scenarios: { [SCENARIO]: scenarios[SCENARIO] },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  setupTimeout: '5m',
};

const json = { headers: { 'Content-Type': 'application/json' }, timeout: '30s' };

export function setup() {
  const ids = [];
  const total = SCENARIO === 'steady' ? 300 : N;
  for (let i = 0; i < total; i++) {
    const answers = [{ questionId: 1, optionIds: [i % 2 === 0 ? 1 : 2] }, { questionId: 7, optionIds: [16] }];
    const r = http.post(`${BASE_URL}/api/v1/surveys/1/responses`, JSON.stringify({ answers }), json);
    ids.push(r.json('data.responseId'));
  }
  return { ids };
}

export default function (data) {
  const responseId = data.ids[exec.scenario.iterationInTest % data.ids.length];
  const start = Date.now();
  const r = http.post(`${BASE_URL}/api/v1/recommendations`, JSON.stringify({ responseId }), json);
  acceptMs.add(Date.now() - start);
  if (MODE !== 'queue') {
    failed.add(r.status !== 200);
    completeMs.add(Date.now() - start);
    check(r, { '200': (x) => x.status === 200 });
    return;
  }
  if (r.status !== 202 && r.status !== 200) {
    failed.add(true);
    return;
  }
  const id = r.json('data.recommendationId');
  const deadline = start + 240000;
  while (Date.now() < deadline) {
    const g = http.get(`${BASE_URL}/api/v1/recommendations/${id}`, { timeout: '30s' });
    const status = g.json('data.status');
    if (status === 'DONE' || status === 'FAILED') {
      failed.add(status !== 'DONE');
      completeMs.add(Date.now() - start);
      return;
    }
    sleep(0.5);
  }
  failed.add(true);
}
