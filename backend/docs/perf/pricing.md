# 토큰 단가 (USD / 100만 토큰)

- 출처: OpenAI 공식 가격표 https://developers.openai.com/api/docs/pricing , 프롬프트 캐싱 https://developers.openai.com/api/docs/guides/prompt-caching
- 확인 날짜: **2026-10-22**
- 기준: Standard 티어, 짧은 컨텍스트(입력 272K 토큰 이하). 지역 처리(data residency)는 10% 추가.
- 2026-10-22에 LLM을 Claude(직접 HTTP)에서 OpenAI(Spring AI)로 바꿨다. 이전 Claude 단가는 이 파일의 git 기록(2026-10-21)에 있다.

| 모델 | 모델 ID | 입력 | 캐시 쓰기 (30분) | 캐시 읽기 | 출력 | 이 프로젝트에서 |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| GPT-6.1 Sol | `gpt-6.1-sol` | 2.00 | 2.50 | 0.10 | 10.00 | 기본 모델 (`llm.model`) |
| GPT-6 Luna | `gpt-6-luna` | 0.10 | 0.125 | 0.01 | 0.50 | 작은 모델 (`llm.small-model`, STEP 08 라우팅) |
| GPT-6 Astra | `gpt-6-astra` | 10.00 | 12.50 | 1.00 | 50.00 | 비교 후보 (OpenAI 문서 기본 권장) |

- 캐시 쓰기: GPT-5.6 이후 모델은 30분 보관 쓰기가 입력 단가의 1.25배 (가격표에 별도 칸이 없어 캐싱 문서 기준으로 계산).
- 캐시 읽기: 가격표의 cached input 단가 (GPT-6.1 Sol은 입력의 0.05배).

## 비용 계산식 (평가 러너 `EvalCost`)

```text
요청당 비용 = (input × 입력 + cache_write × 캐시쓰기 + cache_read × 캐시읽기 + output × 출력) / 1,000,000
```

- OpenAI `usage.prompt_tokens`는 캐시 읽기·쓰기 토큰을 포함한다. `SpringAiLlmClient`가 캐시분을 빼서 `input_tokens`(llm_call_logs)에 넣으므로 세 값을 따로 곱해서 더한다.
- 추론(reasoning) 토큰은 `completion_tokens`에 포함되어 출력 단가로 과금된다.
- 최소 캐시 길이: 1,024토큰 (GPT-5.6 이후). 캐싱은 자동이고, `prompt_cache_key`는 같은 고정 부분끼리 묶는 용도다.

## 모델 선택 메모

- 2026-10-22 사용자 결정: OpenAI 사용, 기본 `gpt-6.1-sol` + 작은 모델 `gpt-6-luna`.
- `gpt-6.1-sol`은 Chat Completions에서 tool calling을 지원하지 않는다 (모델 문서). 그래서 출력 형식은 structured outputs(`response_format` json_schema strict)로 강제한다.
- 바꾸려면 `application-local-llm.yml`의 `llm.model` 또는 실행 인자 `--llm.model=...`. 평가 러너는 `-Pmodel=...`.
