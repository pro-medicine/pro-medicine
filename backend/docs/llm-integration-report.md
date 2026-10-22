# LLM 연동 검증 보고서 (Spring AI + OpenAI)

- 작성: 2026-10-22, 영준 (Claude Code)
- 브랜치: `feat/recommendation-pipeline`
- 범위: Claude 직접 HTTP 호출 → **Spring AI 2.0.1 + OpenAI** 전환 후, 설문부터 추천 응답까지 LLM 앞뒤의 데이터·형식·오류 처리가 제대로 동작하는지 확인
- 실제 API 사용: **4회** (상한 10회), 약 **$0.01**. 부하 테스트는 이번 범위에서 제외 (실데이터 준비 전)

## 1. 결론

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| Spring AI 자동 설정으로 OpenAI 모델 생성 | 정상 | `SpringAiWiringTest` (자동 설정 → `OpenAiChatModel`), 실제 API 4회 |
| API 키 `backend/.env` → 요청 헤더 | 정상 | WireMock에서 `Authorization: Bearer <OPENAI_API_KEY 값>` 확인, 실제 API 인증 성공 |
| LLM 앞: 입력 조립 → 프롬프트 → 요청 형식 | 정상 | §3. 사전 금지 목록(V4)과 JSON 스키마(strict)가 요청에 들어감 |
| LLM 뒤: 구조화 출력 파싱 → 검증 → 규칙 → 용량 → 저장 → 응답 | 정상 | §4. 규칙 위반 성분은 사후 검증에서 제외, 용량 규칙 적용 확인 |
| 형식 실패 시 재요청 | 정상 | 깨진 JSON → 재요청 1회 → 성공 (WireMock) |
| 오류 처리 (429·500·400·타임아웃) | 정상 | 상태 코드·retry-after·재시도 가능 여부가 기존 형식으로 변환됨, SDK 중복 재시도 없음 |
| 실제 모델(`gpt-6.1-sol`)에서 구조화 출력 | 정상 | 2회 모두 재요청 없이 스키마대로 응답 |
| 전체 테스트 | 165개 통과 (Redis 실연결 2개 건너뜀) | `./gradlew test` |

실제 모델에서 `gpt-6.1-sol`이 Chat Completions + structured outputs로 동작하는 것을 확인했다. 모델 문서상 이 모델은 Chat Completions에서 tool calling을 지원하지 않는데, 이번 구조는 tool calling을 쓰지 않으므로 영향이 없다.

## 2. 구조와 유지보수

### 호출 경로

```text
RecommendationController → RecommendationService → PipelineRegistry(V0~V8, RULE_ONLY)
  → StructuredPipeline (입력 조립 · 규칙 사전 필터 · 프롬프트)
    → LlmClient (인터페이스)
       ResilientLlmClient (선택, llm.resilience.enabled) → LoggingLlmClient (llm_call_logs)
         → SpringAiLlmClient ─ Spring AI ChatModel (자동 설정: spring.ai.*) ─ OpenAI SDK ─ HTTP
                └ ChatProviderSupport (회사별 차이: OpenAiProviderSupport)
    ← toolInput(JSON) → 출력 검증 · 재요청 → 규칙 사후 검증 → 용량 계산 → 저장
```

- 추천 파이프라인은 `LlmClient` 인터페이스만 안다. Spring AI·OpenAI 클래스는 `infra/llm` 밖으로 나가지 않는다.
- `SpringAiLlmClient`는 Spring AI **공통 API**(`ChatModel`, `ChatOptions`, `StructuredOutputChatOptions.outputSchema`)만 쓴다.
- 회사마다 다른 것만 `ChatProviderSupport`에 모았다: 출력 토큰 파라미터(OpenAI 추론 모델은 `max_completion_tokens`), strict 구조화 출력, `prompt_cache_key`, SDK 예외 → `LlmCallException` 변환, 입력 토큰에 캐시가 포함되는지.

### 설정이 있는 곳 (코드에 하드코딩 없음)

| 무엇 | 어디 | 값 |
| --- | --- | --- |
| API 키 | `backend/.env` (Git 제외) | `OPENAI_API_KEY=...` → `spring.ai.openai.api-key=${OPENAI_API_KEY:}` |
| 실제 API 켜기 | `application-local-llm.yml` | `spring.ai.model.chat=openai`, `llm.provider=spring-ai` |
| 기본값 (키 없이 서버·테스트 실행) | `config/recommendation-defaults.properties` | `spring.ai.model.*=none`, `llm.provider=fake` |
| 모델 | `application-local-llm.yml` / `LlmProperties` 기본값 | `gpt-6.1-sol`, 작은 모델 `gpt-6-luna` |
| 타임아웃 | `llm.timeout-ms` 하나 | `spring.ai.openai.chat.timeout=${llm.timeout-ms}ms` |
| SDK 재시도 | `spring.ai.openai.max-retries=0` | 재시도는 `ResilientLlmClient`(STEP 10) 한 곳에서만 |
| 추론 강도 | `spring.ai.openai.chat.reasoning-effort` | 지정 안 함(모델 기본 medium) |
| 단가 | `docs/perf/pricing.md`, `EvalCost` | 2026-10-22 OpenAI 가격표 |

점검 결과: 메인 Java 코드에 남은 OpenAI 고유 문자열은 `OpenAiProviderSupport`와 `LlmConfig`(빈 등록 1줄)뿐이고, 나머지는 주석이다. 모델 ID는 `LlmProperties` 기본값과 `application-local-llm.yml`에 같은 값이 두 번 있다 (yml이 우선, 기본값은 yml이 없을 때를 위한 것).

### 다른 회사 모델로 바꿀 때

1. `build.gradle`: `spring-ai-starter-model-openai` → 해당 회사 starter
2. `application-local-llm.yml`: `spring.ai.model.chat` 값과 `spring.ai.<회사>.*` 연결 설정
3. `LlmConfig.chatProviderSupport()`: 그 회사용 `ChatProviderSupport` 구현 (토큰 파라미터·예외 변환 등, 클래스 1개)
4. `llm.model` / `llm.small-model`, `pricing.md`·`EvalCost` 단가

추천 파이프라인, 프롬프트, 검증, 규칙, 용량 계산, 저장, 테스트용 Fake는 그대로다.

## 3. LLM 앞: 보내는 데이터와 형식

WireMock 테스트가 잡은 실제 요청 본문 (`docs/llm-integration/normal-llm-request.json`). 실제 API 호출도 같은 코드 경로다.

| 필드 | 값 | 확인한 것 |
| --- | --- | --- |
| `model` | `gpt-6.1-sol` | `llm.model` (V8 라우팅이면 작은 모델) |
| `messages[0]` | system, 664자 | 성분 코드 목록, 규칙(용량 쓰지 말 것, 금지 목록 지킬 것) |
| `messages[1]` | user | 설문 → 프로필로 조립한 사용자 정보 + **V4 사전 금지 목록** |
| `max_completion_tokens` | 8000 | 추론 모델용 파라미터 (`max_tokens`는 보내지 않음) |
| `response_format` | `json_schema`, `strict: true` | 스키마: `recommendations[{ingredient(enum), reason, priority}]`, `summary` |
| `temperature`, `tools` | 보내지 않음 | 최신 추론 모델이 거부할 수 있는 값은 넣지 않는다 |

사용자 메시지 예 (흡연자, 햇빛 노출 적음, 비회원):

```text
사용자 정보:
- 응답자: 비회원 (질환·복용 약·임신 여부는 묻지 않음)
- 성별: 여성
- 나이: 31세
- 흡연: 하루 1갑
- 햇빛 노출: 거의 실내
- 현재 복용 중인 영양제 성분(하루 함량): 없음

다음 성분은 추천하지 마라 (이유):
- BETA_CAROTENE: 흡연자는 고용량 베타카로틴 보충제를 피하는 게 좋아요
- POTASSIUM / ST_JOHNS_WORT / VITAMIN_A: (비회원이라 확인 불가 → 회원 맞춤 추천에서 확인)
```

- 질환·복용 약·임신 여부를 묻지 않는 비회원은 그 정보가 필요한 성분을 미리 뺀다 (비회원 규칙 R-G*).
- 개인 식별 정보(이름, 사용자 ID, 연락처)는 프롬프트에 들어가지 않는다. 출생 연도는 나이로만 바꿔서 보낸다.

## 4. LLM 뒤: 받은 응답의 처리

| 단계 | 하는 일 | 확인 결과 |
| --- | --- | --- |
| 1. 구조화 출력 파싱 | 응답 본문(JSON 문자열) → `toolInput` | 정상. 깨진 JSON은 `toolInput=null`로 넘김 |
| 2. 출력 검증 | 성분 코드 enum, priority 연속, 중복 | 실패 이유를 붙여 **재요청 1회** → 성공 (`repair-*.json`) |
| 3. 규칙 사후 검증 | LLM이 금지 성분을 넣어도 제외 + 이유 | WireMock이 일부러 넣은 `BETA_CAROTENE` → `excludedItems`(R-02, R-G4) |
| 4. 용량 계산 | 기준표 + 규칙 보정 | 실제 API: 흡연자 비타민 C 75 → **110mg** (R-01 +35mg) |
| 5. 저장·응답 | `ai_recommendations`·`items`·`llm_call_logs` | 같은 형식으로 생성(POST)·조회(GET) |
| 6. 사용량 기록 | 입력(캐시 제외)·캐시 읽기·출력·지연·모델 | OpenAI `prompt_tokens`에서 캐시분을 빼서 기록 (1500 − 1024 = 476) |

실제 API 결과 (`docs/llm-integration/live-recommendation.json`):

| 호출 | 모델 | 입력 토큰 | 출력 토큰 | 캐시 읽기 | 지연 | 결과 |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| 연결 확인 1차 | gpt-6.1-sol | 64 | 16 | 0 | 4,710ms | `{"number":2,"status":"ok"}` |
| 추천 1차 | gpt-6.1-sol | 898 | 328 | 0 | 12,352ms | 비타민 D(R-06 안내), 비타민 C 110mg, 재요청 없음 |
| 연결 확인 2차 (프롬프트 수정 후) | gpt-6.1-sol | 64 | 16 | 0 | 2,938ms | 같은 결과 |
| 추천 2차 (프롬프트 수정 후) | gpt-6.1-sol | 906 | 274 | 0 | 12,885ms | 같은 성분·용량, 재요청 없음, 베타카로틴 없음 |

비용 (gpt-6.1-sol 입력 $2 / 출력 $10, 100만 토큰당): 추천 1회 약 $0.0045, 4회 합계 약 $0.01.

## 5. 오류와 장애

| 상황 | 클라이언트 결과 | API 응답 |
| --- | --- | --- |
| 429 + `retry-after: 3` | `HTTP_ERROR 429`, retryAfter=3, 재시도 가능 | 장애 대응을 켜면(V10) 재시도 → 실패 시 규칙 기반 안내 |
| 500 | `HTTP_ERROR 500`, 재시도 가능 | 장애 대응 꺼짐(기본): `R003` "AI 추천을 만들지 못했습니다" (`error-500-api-response.json`) |
| 400 | `HTTP_ERROR 400`, 재시도 안 함 | 같음 |
| 타임아웃 (`llm.timeout-ms` 초과) | `TIMEOUT`, 재시도 가능 | 같음 |

- SDK 자체 재시도는 꺼져 있어 500에서 HTTP 요청이 **1번만** 나간다 (켜져 있으면 `ResilientLlmClient` 재시도와 겹쳐 시도 수·로그가 어긋난다).
- 모든 시도가 `llm_call_logs`에 상태 코드와 함께 남는다.

## 6. 함께 고친 것

- Spring Boot 3.5.6 → 4.1.1 (Spring AI 2.x 요구). Jackson 3, `starter-webmvc`, Resilience4j `spring-boot4`, 테스트 모듈.
- Claude 직접 호출(`AnthropicLlmClient`)과 테스트 삭제. 부하 테스트 WireMock 매핑을 OpenAI 형식으로 다시 생성 (재측정은 하지 않음).
- 프롬프트(v1·v2·v4 system, repair)와 검증 실패 문구에 남아 있던 "도구를 호출해서 제출" 안내 → "정해진 JSON 형식(스키마)으로 답한다". 재요청 때 이 문구가 모델에게 그대로 전달되기 때문이다. 실제 측정 기록이 아직 없어 프롬프트 버전은 올리지 않았다.

## 7. 발견한 문제와 권장 사항

| # | 내용 | 영향 | 권장 |
| --- | --- | --- | --- |
| 1 | 추천 응답 지연이 약 12~13초 (`gpt-6.1-sol`, 추론 강도 medium) | V10 권장 타임아웃 15초와 여유가 작다 | 실제 지연 분포를 더 모은 뒤 `llm.timeout-ms` 결정. 줄이려면 `reasoning-effort: low` 또는 작은 모델 비교 |
| 2 | 프롬프트가 약 900토큰 → OpenAI 캐싱 최소 1,024토큰 미만 | V7(프롬프트 캐싱)은 지금 효과 없음 (캐시 읽기 0) | V7 채택 여부는 프롬프트가 길어질 때 다시 판단 |
| 3 | API `items[].ingredient`가 성분 **코드**(`VITAMIN_D`) | 화면에 그대로 쓰면 코드가 보인다 (V1 이후 기존 동작) | 프론트(채원)에서 `GET /api/v1/ingredients`로 이름 매핑, 또는 응답에 이름 필드 추가 협의 |
| 4 | 비타민 D 용량이 "기준값 미등록" | 용량 없이 추천됨 | `domain-rules.md` 2번 표 TODO 값 입력 (사용자) |
| 5 | 비회원 흡연자의 베타카로틴 제외 이유가 2개(R-02, R-G4) 중복 | 이유 문구가 겹쳐 보인다 | 회원 여부와 무관하게 이미 확인된 조건이면 R-G4를 생략하는 규칙 정리 (낮은 우선순위) |
| 6 | 내부 이름 `LlmTool`·`toolInput`은 이제 "출력 스키마"·"구조화 출력" 의미 | 읽을 때 헷갈릴 수 있다 | 동작 문제는 없음. 이름 변경은 영향 범위가 넓어 이번엔 주석으로만 설명 |
| 7 | Spring AI 2.0은 OpenAI Responses API 미지원 (2.1 마일스톤부터, Boot 4.2 필요) | 지금은 Chat Completions로 충분 | Spring AI 2.1 정식판이 나오면 검토 |
| 8 | Boot 4 전환은 팀 전체 백엔드에 영향 | develop 병합 시 다른 담당자 코드도 Boot 4로 빌드 | PR 전에 팀 공유 (Jackson 패키지 `tools.jackson.*`) |

## 8. 테스트

| 테스트 | 무엇을 | 실제 API |
| --- | --- | --- |
| `SpringAiLlmClientTest` (6) | 요청 형식(헤더·모델·토큰·스키마·strict·캐시 키·temperature 없음), 응답 파싱·사용량, 깨진 JSON, 429/500/400/타임아웃 변환, SDK 재시도 없음 | 아니오 (WireMock) |
| `SpringAiWiringTest` (4) | 자동 설정 → ChatModel → LlmClient, 설문→추천 전체 흐름 앞뒤 데이터, 재요청 복구, 500 오류 응답 | 아니오 (WireMock) |
| `SpringAiLlmClientLiveTest` (2) | 실제 연결·구조화 출력, 설문→추천 전체 흐름 | 예 (`./gradlew liveTest`, 최대 3회) |
| 기존 테스트 (설문·파이프라인 V0~V8·규칙·용량·캐시·큐·장애 대응·평가 러너) | 동작 유지 | 아니오 |

```powershell
.\gradlew.bat test                  # 165개, 실제 API 없음
.\gradlew.bat liveTest              # 실제 API 2~3회 (backend/.env의 OPENAI_API_KEY)
```

증거 파일: `docs/llm-integration/` (WireMock 요청·응답 6개, 실제 API 결과 2개)
