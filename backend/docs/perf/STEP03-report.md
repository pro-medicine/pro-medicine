# STEP03 리포트 - LLM 연결 + V0 (Before 기준선)

## 바꾼 것 (Before → After)

- Before: 추천 기능 없음
- After:
  - `infra/llm`: `LlmClient` → `AnthropicLlmClient`(Messages API를 Spring `RestClient`로 직접 호출) / `FakeLlmClient`, 기록 데코레이터 `LoggingLlmClient`
  - `llm_call_logs` 테이블 (모든 호출의 토큰·지연·결과, 본문은 저장 안 함)
  - `RecommendationPipeline` 구조 + `V0Pipeline`(프롬프트에 "JSON으로 답해라"만), `RecommendationInputAssembler`
  - `POST /api/v1/recommendations`, `GET /api/v1/recommendations/{id}`

## 측정 조건

| 항목 | 값 |
| --- | --- |
| 실제 API 측정 | **실제 측정 대기** (밤새 실행은 실제 API 예산 0달러) → [TODO-live-runs.md](TODO-live-runs.md) 03-1, 03-2 |
| 대신 확인한 것 | WireMock으로 HTTP 형식 검증(`AnthropicLlmClientTest`), Fake LLM으로 설문 제출 → 추천 생성 → `llm_call_logs` 기록까지 (`RecommendationApiTest`) |
| 모델 | 기본 `claude-opus-5-5` (단가 [pricing.md](pricing.md)) |
| 날짜 | 2026-10-21 |

## 결과

### 실제 API 3회 호출 (실제 측정 대기)

| 회차 | latency_ms | input_tokens | output_tokens | outcome |
| --- | --- | --- | --- | --- |
| 1 | 측정 안 함 | 측정 안 함 | 측정 안 함 | - |
| 2 | 측정 안 함 | 측정 안 함 | 측정 안 함 | - |
| 3 | 측정 안 함 | 측정 안 함 | 측정 안 함 | - |

### 연결 검증 (실제 API 아님)

| 검증 | 방법 | 결과 |
| --- | --- | --- |
| 요청 헤더(`x-api-key`, `anthropic-version: 2023-06-01`), system/messages 블록 형식 | WireMock 요청 검사 | 통과 |
| `content`의 text / tool_use 파싱, `usage` 4개 필드, `request-id` 헤더 | WireMock 응답 | 통과 |
| 429 + `retry-after`, 400(재시도 대상 아님), 읽기 타임아웃 → `TIMEOUT` | WireMock | 통과 |
| API 키가 없으면 HTTP 요청을 보내지 않음 | WireMock 호출 수 0 | 통과 |
| 성공·HTTP 오류·타임아웃 모두 `llm_call_logs`에 기록, 파싱 실패 시 `PARSE_FAIL`로 갱신 | `LoggingLlmClientTest`, `V0PipelineTest` | 통과 |

## 해석

- 실제 지연·토큰 수치는 아직 없다. V0 기준 수치는 STEP 04의 평가셋 실행(실제 API)에서 처음 나온다.
- 공식 문서 확인 결과, 기본 모델 Claude Opus 5.5는 **강제 `tool_choice`(`type: tool`)를 400으로 거부**하고 `temperature`도 받지 않는다. 그래서 클라이언트는 `temperature`가 null이면 보내지 않고, 도구 강제는 설정(`llm.anthropic.force-tool-choice`, 기본 false)으로 두었다. STEP 05 V1 설계에 영향이 있다 (`auto` + `strict: true` 도구 + 프롬프트 지시 + 검증·재요청).

## 한계 / 다음에 할 것

- 실제 API 3회 호출 → `TODO-live-runs.md` 03-2
- 재시도·서킷 브레이커 없음 (STEP 10). 타임아웃만 `llm.timeout-ms`(30초)
