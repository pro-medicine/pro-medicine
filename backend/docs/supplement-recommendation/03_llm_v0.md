# STEP 03. LLM 연결 + V0 (Before 기준선)

> 먼저 `docs/prompts/00_COMMON.md`를 읽고 따른다. STEP 02가 끝난 상태에서 시작한다.

## 목표

Claude API를 연결하고, **가장 단순한 방식(V0)**으로 추천을 만든다. V0는 이후 모든 개선의 Before가 되므로 일부러 단순하게 둔다.
나중 버전이 생겨도 V0는 지우지 않고 계속 실행할 수 있어야 한다.

## 1. LLM 클라이언트 (`infra/llm`)

```java
public interface LlmClient {
    LlmResponse call(LlmRequest request);
}
```

- `LlmRequest`: model, system 프롬프트, messages, maxTokens, temperature, (나중을 위해) tools, cacheControl 여부, 그리고 기록용 메타데이터 `callTag`, `pipelineVersion`, `promptVersion`, `responseId`(nullable), `evalRunId`(nullable)
- `LlmResponse`: 텍스트 또는 tool 입력 JSON, `stopReason`, 토큰 사용량(input, output, cache_read, cache_creation), 지연 시간(ms), 모델명, 요청 ID
- 구현체
  - `AnthropicLlmClient`: Claude Messages API를 HTTP로 호출 (Spring `RestClient`). 엔드포인트, `anthropic-version` 헤더 값, 응답 필드 이름은 https://docs.claude.com 에서 확인하고 그대로 쓴다.
  - `FakeLlmClient`: 테스트용. 미리 정한 응답을 돌려주고 사용량은 고정값.
- 설정: `llm.provider=anthropic|fake`, `llm.model`, `llm.timeout-ms`(기본 30000), `llm.max-tokens`. 기본 프로필은 `fake`, `local-llm` 프로필에서만 `anthropic`.
- 이번 STEP에서는 재시도·서킷 브레이커를 넣지 않는다 (STEP 09에서 측정하며 넣는다). 타임아웃만 둔다.

## 2. 호출 기록 (`llm_call_logs` 테이블 추가)

모든 LLM 호출을 기록하는 데코레이터 `LoggingLlmClient`를 만든다. 이후 모든 비용·지연 측정의 원본 데이터다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| llm_call_log_id | BIGINT PK AUTO_INCREMENT | |
| response_id | BIGINT NULL | survey_responses 참조 (평가 실행 중이면 null) |
| eval_run_id | VARCHAR(50) NULL | 평가 실행 ID |
| pipeline_version | VARCHAR(10) NOT NULL | V0, V1 … |
| call_tag | VARCHAR(30) NOT NULL | RECOMMEND, RECOMMEND_REPAIR(STEP 05의 검증 실패 후 재요청) |
| model | VARCHAR(60) NOT NULL | |
| prompt_version | VARCHAR(20) NOT NULL | 프롬프트 템플릿 버전 |
| input_tokens | INT NULL | |
| output_tokens | INT NULL | |
| cache_read_tokens | INT NULL | |
| cache_creation_tokens | INT NULL | |
| latency_ms | INT NOT NULL | |
| attempt_no | INT NOT NULL DEFAULT 1 | 네트워크 재시도(STEP 10)만 2, 3 … 으로 증가. 출력 검증 실패 후 재요청은 attempt_no가 아니라 call_tag로 구분 |
| outcome | VARCHAR(20) NOT NULL | SUCCESS, PARSE_FAIL, VALIDATION_FAIL, TIMEOUT, HTTP_ERROR |
| http_status | INT NULL | |
| created_at | DATETIME NOT NULL | |

- `LoggingLlmClient`는 HTTP 결과로 행을 만들고(`SUCCESS`, `TIMEOUT`, `HTTP_ERROR`) 행 ID를 `LlmResponse`에 담아 돌려준다. 파싱·검증은 파이프라인이 하므로, 파싱에 실패하면 **파이프라인이 그 행의 outcome을 `PARSE_FAIL`(V1부터 스키마 검증 실패는 `VALIDATION_FAIL`)로 갱신**한다.
- 프롬프트 본문과 응답 본문은 저장하지 않는다 (민감정보). 디버깅용 저장이 필요하면 `llm.log-body=true`일 때만 로컬 파일로 남긴다.
- 스키마 추가는 `00_COMMON.md` 3번 규칙대로 기록한다.

## 3. 추천 파이프라인 구조

버전별 Before/After를 같은 조건에서 비교하려면 모든 버전이 공존해야 한다.

```java
public interface RecommendationPipeline {
    String version();                         // "V0"
    RecommendationResult run(RecommendationInput input);
}
```

- `RecommendationInput`: 설문 응답 + (회원이면) 프로필을 합친 **정규화 입력**. `survey_responses`에서 만드는 `RecommendationInputAssembler`를 만든다. 평가 러너도 같은 객체를 직접 만들어 넣을 수 있어야 한다.
- `RecommendationResult`: 성분 목록(코드, 이유, 순서, 용량·단위는 nullable), 요약, 사용한 파이프라인 버전, 파싱 성공 여부
- 설정 `recommendation.pipeline-version`으로 API가 쓸 버전을 고른다. 평가 러너는 버전을 인자로 받는다.
- 결과 저장: `ai_recommendations`(response_id, summary, model_name), `ai_recommendation_items`(ingredient_name, reason, sort_order). `ingredient_name`에는 V0는 LLM이 준 원문(최대 150자)을, V1부터는 성분 코드를 저장한다. 이번 STEP에서는 용량 컬럼이 없으므로 용량은 저장하지 않는다 (STEP 06에서 추가).

## 4. V0 파이프라인 (의도적으로 단순하게)

- 시스템 프롬프트: "너는 영양제 추천 도우미다. 사용자 정보를 보고 필요한 영양 성분과, 현재 섭취량을 뺀 하루 추가 섭취량을 추천해라. 아래 JSON 형식으로만 답해라." + JSON 예시
- 사용자 메시지: 정규화 입력을 사람이 읽는 문장으로 나열
- tool use, JSON 스키마 강제, 재시도, 기준표, 규칙 검증 **전부 없음**
- 응답 텍스트에서 JSON을 Jackson으로 파싱. 실패하면 재시도 없이 `PARSE_FAIL`로 기록하고 빈 결과 반환
- 성분 이름은 LLM이 준 그대로 저장한다. 성분 코드 매핑은 평가 러너에서 별칭 표(`src/test/resources/eval/ingredient-aliases.json`)로 처리한다
- 프롬프트 템플릿은 `src/main/resources/prompts/v0/` 아래 파일로 두고 `prompt_version = "v0.1"`

## 5. API

- `POST /api/recommendations` body `{ "responseId": 1 }` → 동기 호출로 추천 생성 후 결과 반환
- `GET /api/recommendations/{recommendationId}`
- 비회원도 자기 `responseId`로 호출할 수 있다. (가로채기 방지는 이번 범위 밖, `docs/progress.md`의 미룬 것에 적는다)

## 6. 테스트

- `FakeLlmClient`로 V0 성공 / 파싱 실패 케이스
- `LoggingLlmClient`가 성공·실패 모두 기록하는지
- `RecommendationInputAssembler`: 회원(프로필 병합), 비회원(설문만)
- 실제 API 테스트는 `@Tag("live")`로 분리하고 기본 테스트에서 제외. `./gradlew liveTest`로만 실행

## 7. 측정 (이번 STEP)

평가셋은 STEP 04에서 만든다. 이번에는 연결이 제대로 되는지와 대략적인 크기만 본다.

- 실제 API로 V0를 수동 3회 호출하고 `llm_call_logs`의 지연·토큰을 `docs/perf/STEP03-report.md`에 적는다.
- 사용한 모델 이름과, 공식 가격표에서 확인한 토큰 단가를 날짜와 함께 `docs/perf/pricing.md`에 적는다.

## 8. 사용자가 먼저 해야 할 것

- Anthropic Console에서 API 키 발급 후 환경변수 `ANTHROPIC_API_KEY` 설정
- 사용할 모델 결정 (기본 모델 1개. STEP 08에서 작은 모델을 추가로 비교한다)

## 9. 완료 조건

- `./gradlew test` 통과 (실제 API 호출 없이)
- `local-llm` 프로필로 서버 실행 후 설문 제출 → 추천 생성까지 동작
- `llm_call_logs`에 기록이 남음
- `docs/perf/STEP03-report.md`, `docs/perf/pricing.md`, `docs/progress.md` 기록
