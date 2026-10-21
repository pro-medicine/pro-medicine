# 토큰 단가 (USD / 100만 토큰)

- 출처: Anthropic 공식 가격표 https://platform.claude.com/docs/en/about-claude/pricing (docs.claude.com에서 리다이렉트)
- 확인 날짜: **2026-10-21**
- 기준: 1st-party Claude API, 글로벌 라우팅(기본값). `inference_geo`로 US 전용을 쓰면 전 항목 1.1배.

| 모델 | 모델 ID | 입력 | 캐시 쓰기 (5분) | 캐시 쓰기 (1시간) | 캐시 읽기 | 출력 | 이 프로젝트에서 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| Claude Opus 5.5 | `claude-opus-5-5` | 4.00 | 5.00 | 8.00 | 0.20 | 20.00 | 기본 모델 (`llm.model`) |
| Claude Sonnet 5.5 | `claude-sonnet-5-5` | 2.00 | 2.50 | 4.00 | 0.10 | 10.00 | 비교 후보 |
| Claude Haiku 5.5 (프롬프트 10만 토큰 이하) | `claude-haiku-5-5` | 0.10 | 0.125 | 0.20 | 0.01 | 0.50 | 작은 모델 (`llm.small-model`, STEP 08 라우팅) |
| Claude Haiku 5.5 (프롬프트 10만 토큰 초과) | `claude-haiku-5-5` | 0.50 | 0.625 | 1.00 | 0.05 | 2.50 | 해당 없음 (추천 프롬프트는 수천 토큰) |

## 비용 계산식 (평가 러너 `EvalCost`)

```text
요청당 비용 = (input × 입력 + cache_creation × 캐시쓰기(5분) + cache_read × 캐시읽기 + output × 출력) / 1,000,000
```

- Messages API `usage.input_tokens`는 캐시 읽기·쓰기 토큰을 뺀 나머지 입력이다. 세 값을 따로 곱해서 더한다.
- tool use를 쓰면 API가 시스템 프롬프트에 도구 사용 안내를 붙인다 (가격표 기준 Opus 5.5 / Sonnet 5.5 / Haiku 5.5: `auto` 선택 시 286토큰). 이 토큰도 `input_tokens`에 포함되어 돌아온다.
- 최신 모델(Opus 5.5 등)은 adaptive thinking이 항상 켜져 있어 thinking 토큰도 출력 토큰으로 과금된다.

## 모델 선택 메모

- 모델 결정은 사용자 몫이다 (STEP 03 "사용자가 먼저 해야 할 것"). 지금은 Anthropic 문서의 기본 권장 모델인 `claude-opus-5-5`를 기본값으로 두었다.
- 바꾸려면 `application-local-llm.yml`의 `llm.model` 또는 실행 인자 `--llm.model=...`. 평가 러너는 `-Pmodel=...`.
