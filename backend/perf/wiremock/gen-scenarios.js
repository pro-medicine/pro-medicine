// STEP 10 장애 주입 WireMock 매핑 생성. 실행: node perf/wiremock/gen-scenarios.js  (backend 폴더에서)
// 응답 형식은 OpenAI Chat Completions (normal/mappings/chat-completions.json). 앱은 Spring AI(OpenAiChatModel)로 호출한다.
// WireMock은 확률 응답이 없어서 "상태 순환 시나리오"로 비율을 정확히 맞춘다 (요청 순서대로 상태가 하나씩 넘어간다).
//  - normal : 항상 정상, 지연 3초 (이미 있는 perf/wiremock/normal)
//  - slow   : 5건 중 1건(20%) 20초 지연, 나머지 3초
//  - flaky  : 20건 중 타임아웃 2건(10%, 35초 지연 → V9 30초·V10 15초 타임아웃 모두 초과), 5xx 1건(5%), 429 1건(5%, retry-after 1)
//  - outage : 우선순위 높은 500 매핑(outage-500.json) + 정상 매핑. 60초 뒤 500 매핑을 지우면 복구된다
//             (in-JVM 테스트는 코드로 지운다. standalone은 curl -X DELETE http://localhost:8089/__admin/mappings/{id})
const fs = require('fs');
const path = require('path');

const ROOT = __dirname;
const okBody = JSON.parse(fs.readFileSync(path.join(ROOT, 'normal', 'mappings', 'chat-completions.json'), 'utf8')).response.jsonBody;

function ok(delay) {
  return {status: 200, fixedDelayMilliseconds: delay, headers: {'Content-Type': 'application/json'}, jsonBody: okBody};
}
const error500 = {status: 500, fixedDelayMilliseconds: 200, headers: {'Content-Type': 'application/json'},
  jsonBody: {error: {message: 'injected 500', type: 'server_error', param: null, code: null}}};
const tooMany = {status: 429, fixedDelayMilliseconds: 100, headers: {'Content-Type': 'application/json', 'retry-after': '1'},
  jsonBody: {error: {message: 'injected 429', type: 'requests', param: null, code: 'rate_limit_exceeded'}}};

function cycle(name, responses) {
  const dir = path.join(ROOT, name, 'mappings');
  fs.rmSync(path.join(ROOT, name), {recursive: true, force: true});
  fs.mkdirSync(dir, {recursive: true});
  responses.forEach((response, i) => {
    const state = i === 0 ? 'Started' : `S${i}`;
    const next = i === responses.length - 1 ? 'Started' : `S${i + 1}`;
    fs.writeFileSync(path.join(dir, `${String(i).padStart(2, '0')}.json`), JSON.stringify({
      scenarioName: name, requiredScenarioState: state, newScenarioState: next,
      request: {method: 'POST', urlPath: '/v1/chat/completions'}, response}, null, 2));
  });
}

// slow: 20%
cycle('slow', [ok(20000), ok(3000), ok(3000), ok(3000), ok(3000)]);

// flaky: 20개 상태. 타임아웃 2, 5xx 1, 429 1 (위치를 흩어 둔다)
const flaky = Array.from({length: 20}, () => ok(3000));
flaky[2] = ok(35000);
flaky[12] = ok(35000);
flaky[7] = error500;
flaky[17] = tooMany;
cycle('flaky', flaky);

// outage
const outageDir = path.join(ROOT, 'outage', 'mappings');
fs.rmSync(path.join(ROOT, 'outage'), {recursive: true, force: true});
fs.mkdirSync(outageDir, {recursive: true});
fs.writeFileSync(path.join(outageDir, 'normal.json'), JSON.stringify({
  priority: 5, request: {method: 'POST', urlPath: '/v1/chat/completions'}, response: ok(3000)}, null, 2));
fs.writeFileSync(path.join(outageDir, 'outage-500.json'), JSON.stringify({
  id: '00000000-0000-0000-0000-000000000500', priority: 1,
  request: {method: 'POST', urlPath: '/v1/chat/completions'}, response: error500}, null, 2));
console.log('generated slow, flaky, outage');
