# STEP 11. 최종 정리 - 성능 개선 리포트

> 먼저 `docs/prompts/00_COMMON.md`를 읽고 따른다. STEP 10까지 끝난 상태에서 시작한다.

## 목표

STEP 02~10의 측정 결과를 한 문서로 모은다. 면접·포트폴리오에 바로 쓸 수 있는 형태로 만든다.
**새로 측정하지 않는다.** `docs/perf/results.csv`와 각 STEP 리포트에 있는 수치만 쓴다.

## 1. 산출물: `docs/perf/SUMMARY.md`

```markdown
# AI 영양제 추천 - 단계별 개선 결과

## 한 줄 요약
(가장 큰 개선 2~3개를 수치로)

## 아키텍처
(최종 파이프라인: 설문 → 입력 조립 → [캐시] → 사전 규칙 → LLM(tool use) → 용량 계산 → 사후 규칙 → 저장. 다이어그램은 Mermaid)

## 버전별 결과
| 버전 | 바꾼 것 | 핵심 지표 Before → After | 리포트 |
(V0 ~ V10, results.csv 기준)

## 품질
(parse_fail_rate, violation_rate, raw_violation_rate, dose_error_rate, reproducibility 추이 표)

## 비용·지연
(요청당 비용, 토큰, p95 추이 표)

## 트래픽·장애
(알림 몰림 시나리오, 장애 주입 결과)

## 측정 방법과 한계
(평가셋 크기와 검토 여부, H2/로컬 측정 여부, WireMock 사용 범위, 단가 기준일)

## 하지 않은 것과 이유
(RAG를 넣지 않은 이유 등)
```

## 2. 규칙

- 수치마다 출처 줄(`results.csv`의 step·label)을 표 옆에 적는다. `results.csv`에 없는 수치(STEP 02·03 등)는 해당 `STEPxx-report.md` 링크로 출처를 단다.
- 측정하지 않은 항목은 "측정 안 함"으로 쓴다. 추정치를 넣지 않는다.
- 결과가 나빠진 항목도 숨기지 않는다 (예: V4에서 입력 토큰 증가, V8 작은 모델의 recall 하락).
- 문장은 짧고 담백하게. 과장 표현(획기적, 극적인 등)을 쓰지 않는다.

## 3. 추가로 만들 것

- `README.md`에 실행 방법(필요한 환경변수, docker-compose, 프로필), 평가 실행 방법, 부하 테스트 실행 방법 섹션을 추가하거나 갱신한다.
- `docs/perf/charts/`에 핵심 추이 차트 2~3개 (results.csv로 생성하는 스크립트와 함께).

## 4. 완료 조건

- `SUMMARY.md`의 모든 수치가 results.csv와 일치하는지 스크립트로 대조하고, 대조 결과를 progress에 적는다.
