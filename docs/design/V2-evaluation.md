# Version 2 성능·품질 측정 설계 (맞춤 질문 → 정밀 추천)

> 담당: 영준 / 작성일: 2026-10-26 / 상위 문서: `docs/design/V2-personalized-recommendation.md`
> V1 측정 기준: `backend/docs/supplement-recommendation/reference/measurement.md`, 결과: `backend/docs/perf/`

---

## 1. V1 측정과 다른 점

| | V1 | V2 |
|---|---|---|
| LLM 호출 | 추천 1회 (+재요청) | **질문 최대 7회 + 정밀 추천 1회** (+재요청) |
| 입력 | 설문 답 (고정) | 프로필 + **LLM이 만든 질문에 대한 답** (매번 달라짐) |
| 필요한 것 | 케이스 입력 → 결과 비교 | **가상 사용자**가 질문에 자동으로 답해야 함 |
| 비교 단위 | 요청 1건 | **회차(세션) 1개** = 질문 흐름 전체 + 최종 추천 |

그래서 측정 러너는 "회차를 처음부터 끝까지 자동으로 진행하는 가상 사용자"를 가진다.

---

## 2. 가상 사용자 (자동 답변 정책)

LLM이 만든 선택지는 매번 문장이 달라서 "몇 번째 선택지"나 "이 문장"으로 답할 수 없다. 대신 **선택지의 `kind`로 답한다.** 문장이 바뀌어도 같은 성격의 답을 고르게 되므로, 같은 사용자 반복 실험에서 **질문 생성의 차이만** 볼 수 있다.

```json
"answerPolicy": {
  "kindOrder": ["CONCERN", "NORMAL", "UNKNOWN", "NONE"],
  "openPick": 2
}
```
- SINGLE: `kindOrder` 순서대로, 그 kind의 선택지가 있으면 그중 **첫 번째**를 고른다
- MULTI(불편한 점): `openPick`개(0~2)만큼 `CONCERN` 선택지를 앞에서부터 고른다. `openPick = 0`이면 `NONE` 선택지를 고른다 (없으면 `UNKNOWN`)
- 임신 고정 질문: 페르소나에 정해 둔 값으로 답한다
- 실제 사람처럼 LLM으로 답하게 하는 방식(LLM 가상 사용자)은 비용과 변동이 커서 쓰지 않는다

---

## 3. 실험 구성 (총 10회차)

### 실험 A — 서로 다른 사용자 5명 × 1회: 성능, 정확도, 안전

각 페르소나는 **주제 계획의 다른 경로**와 **다른 안전 규칙**을 하나씩 건드리도록 골랐다.

| ID | 프로필 | 답변 정책 | 확인하려는 것 | 기대값 |
|---|---|---|---|---|
| A1 | 남 46세, 흡연(반 갑), 메트포르민, 고혈압 | 걱정 많음 (`CONCERN` 우선, openPick 2) | 깊이 질문, 질문 예산 상한(7) | 임신 질문 없음 / `MED:METFORMIN` 질문함 / B12를 추천하면 R-11 경고 |
| A2 | 여 27세, 비건, 햇빛 적음, 약 없음, **임신 중** | 보통 (`NORMAL` 우선, openPick 1) | 임신 고정 질문, 임신 규칙 | 임신 질문 **함** / 비타민A **제외** (R-08) / B12 확인 안내 (R-05) |
| A3 | 여 64세, 와파린, 갑상선약(레보티록신) | 모름 많음 (`UNKNOWN` 우선, openPick 1) | 50세 초과 여성, 복용 시간 규칙 | 임신 질문 **없음** / 비타민K 경고 (R-16) / 철분·칼슘 복용 시간 안내 (R-17, R-17b) |
| A4 | 남 52세, **혈압약(이름 모름)** | 없음 많음 (`NONE` 우선, openPick 0) | 빠른 종료 경로, 계열 모름 규칙 | 질문 수 적음 / 칼륨을 추천하면 **제외가 아니라 경고** (R-U01) |
| A5 | 남 33세, 흡연(한 갑), **항우울제(SSRI)** | 섞음 (`NORMAL`, `CONCERN`, …, openPick 1) | 제외 규칙 두 개 | 베타카로틴 **제외** (R-02) / 세인트존스워트 **제외** (R-19) |

### 실험 B — 같은 사용자 5회: 일관성

- 페르소나 **A1 그대로 5회** (주제가 가장 많고 깊이 질문이 생기는 경로라 차이가 잘 드러남)
- 프로필, 답변 정책, 설정이 모두 같으므로 **달라지는 것은 LLM 출력뿐**

---

## 4. 측정 지표

### 4-1. 질문 품질 (질문 1개 단위)
| 지표 | 정의 | 목표 |
|---|---|---|
| 첫 시도 통과율 | 재요청 없이 검증 통과한 질문 / 전체 질문 | 높을수록 좋음 (V1 parse_fail처럼 기록) |
| 재요청 비율 | 재요청이 필요했던 질문 / 전체 | 기록 |
| 생성 실패율 | 재요청까지 실패(P005) / 전체 | **0** |
| 검증 실패 이유 분포 | 개수, 중복 라벨, UNKNOWN 없음, 금지어, 길이, 질문 반복, answerType 불일치 | 어떤 규칙이 얼마나 막았는지 |
| 원본 금지어 비율 | 검증 **전** LLM 원본에 금지어가 있던 비율 | 기록 (검증이 실제로 막은 양) |
| 질문 반복률 | 같은 회차 안에서 이전 질문과 유사도 0.8 이상 | 0에 가깝게 |

### 4-2. 흐름 정확도 (회차 단위)
| 지표 | 정의 | 목표 |
|---|---|---|
| 임신 질문 정확도 | 19~50세 여성에게만 물었는가 | **100%** |
| 필수 주제 포함률 | 페르소나의 `mustAskTopics` 중 실제로 물은 비율 | 100% (질문 예산 안에서) |
| 주제 계획 준수율 | 질문 수 ≤ 7, 새 주제는 5문항 전에만, 깊이 ≤ 최대 | **100%** (서버 규칙이라 결정적) |
| 회차 질문 수 | 회차당 LLM 질문 수 | 기록 |
| kind 분포 | 선택지 kind 비율, 고른 답 kind 비율 | 기록 |

### 4-3. 정밀 추천 안전·정확도 (V1 지표 재사용)
| 지표 | 정의 | 목표 |
|---|---|---|
| 최종 위반율 (`violation_rate`) | `mustExclude` 성분이 최종 추천에 남은 비율 | **0** |
| 원본 위반율 (`raw_violation_rate`) | 규칙 적용 **전** LLM 원본에 `mustExclude`가 있던 비율 | 기록 (규칙이 막은 양) |
| 경고 표시율 | `expectWarn` 성분을 추천했을 때 해당 경고가 붙은 비율 | **100%** |
| 계열 모름 처리 | A4에서 칼륨이 제외가 아니라 경고로 처리됐는가 | **예** |
| recall / precision | `mustInclude`·`mayInclude` 기준 (V1과 같은 정의) | 상대 비교용 |

### 4-4. 성능·비용
| 지표 | 정의 |
|---|---|
| 질문 생성 지연 p50 / p95 | 질문 LLM 호출 1회 |
| 정밀 추천 지연 p50 / p95 | 추천 LLM 호출 1회 |
| 회차 전체 LLM 시간 | 회차의 모든 LLM 호출 시간 합 (사용자가 기다리는 시간의 하한) |
| 회차당 호출 수 | 질문 + 재요청 + 추천 |
| 회차당 토큰·비용 | 입력·출력·캐시 토큰, `docs/perf/pricing.md` 단가로 계산 |

### 4-5. 일관성 (실험 B 전용)
| 지표 | 정의 | 해석 |
|---|---|---|
| 주제 경로 일치율 | 5회차의 (topic, depth) 순서가 기준 회차와 같은 쌍의 비율 | 낮으면 LLM이 kind를 다르게 붙여서 경로가 갈린 것 |
| 경로 길이 편차 | 회차별 질문 수의 최소·최대·표준편차 | |
| 질문 문장 유사도 | 같은 (topic, depth) 위치끼리 문자 2-gram Jaccard 평균 (추가 API 호출 없음) | 1에 가까우면 거의 같은 질문, 낮으면 표현이 매번 다름 (D10상 달라도 됨) |
| 선택지 개수 편차 | 같은 위치의 선택지 수 차이 | |
| kind 일관성 | 같은 위치에서 비슷한 라벨(유사도 0.6 이상)에 붙은 kind가 같은 비율 | **판단 기준의 일관성** — 가장 중요 |
| 최종 성분 일치도 | 회차 쌍별 추천 성분 집합 Jaccard 평균, Top-3 겹침 | |
| 제외 일관성 | 5회 모두 같은 성분이 제외됐는가 | **100%** (규칙이라 결정적이어야 함) |
| 막음 일관성 | 회차별 재요청 비율·검증 실패 이유의 편차 | |

---

## 5. 페르소나 파일 형식

`backend/src/test/resources/eval/v2-personas.json` (V1 `cases.json`과 같은 원칙: 기대값은 `domain-rules.md` 규칙에서만 도출, `reviewed: false`로 시작해 사람이 확인)

```json
{
  "meta": { "version": "1", "reviewed": false, "generatedAt": "2026-10-26" },
  "personas": [
    {
      "personaId": "A1",
      "experiment": ["A", "B"],
      "profile": {
        "gender": "M", "birthYear": 1980,
        "smoking": "HALF_PACK", "drinking": "LOW", "caffeine": "LOW", "dietType": "OMNI", "sunExposure": "NORMAL",
        "conditions": ["HYPERTENSION"],
        "medications": [{ "drugId": "D-001" }],
        "sensitiveConsent": true
      },
      "pregnancyAnswer": null,
      "answerPolicy": { "kindOrder": ["CONCERN", "NORMAL", "UNKNOWN", "NONE"], "openPick": 2 },
      "expected": {
        "pregnancyAsked": false,
        "mustAskTopics": ["CONCERN_OPEN", "MED:METFORMIN"],
        "mustExclude": [],
        "expectWarn": [{ "ingredient": "VITAMIN_B12", "ruleId": "R-11" }],
        "mustInclude": [],
        "mayInclude": ["VITAMIN_B12", "VITAMIN_C"]
      }
    }
  ]
}
```

---

## 6. 실행 방법

V1 평가 러너(`backend/src/test/java/com/ucamp/app/eval/`) 구조를 따른다: 테스트 JVM + H2 + 실제 서비스 빈.

| 명령 | 내용 | 비용 |
|---|---|---|
| `.\gradlew.bat v2EvalRun -Pexperiment=A -Pfake=true` | Fake LLM으로 러너 검증 | 0 |
| `.\gradlew.bat v2EvalRun -Pexperiment=B -Pfake=true` | 〃 | 0 |
| `.\gradlew.bat v2EvalRun -Pexperiment=A -Pconfirm=true` | 실제 API, 5회차 | 아래 |
| `.\gradlew.bat v2EvalRun -Pexperiment=B -Pconfirm=true` | 실제 API, 5회차 | 아래 |

- `-Pconfirm=true`가 없으면 **예상 호출 수와 예상 비용만 출력하고 종료** (V1 measurement.md 4장과 같은 규칙)
- 예상 비용 (gpt-6.1-sol 기준 추정): 회차당 질문 ~6회 + 추천 1회 + 재요청 ~0.5회 ≈ 7.5회, 질문 1회 약 $0.003~0.005, 추천 1회 약 $0.005 → **10회차 약 $0.3~0.6**
- 실행 중 누적 비용이 **$2를 넘으면 중단** (안전장치)
- 회차는 순차 실행 (동시 호출 없음), 실행 시각과 모델을 결과에 기록

---

## 7. 결과 기록

| 파일 | 내용 |
|---|---|
| `backend/docs/perf/runs/v2-{A|B}-{evalRunId}.json` | 회차별 전체 기록: 질문·선택지(kind 포함)·고른 답·검증 실패 이유·재요청 원본·호출별 토큰과 지연·최종 추천·규칙 적용 결과 |
| `backend/docs/perf/results-v2.csv` | 실험 1회당 1줄: 4장 지표 요약 (V1 `results.csv`와 같은 방식, 열 이름은 4장 지표 이름) |
| `backend/docs/perf/V2-eval-report.md` | measurement.md 2장 형식: 측정 조건, 결과 표, 해석, 한계 |

- Fake 실행 결과에는 `FAKE LLM - 러너 검증용, 실제 측정값 아님`을 note에 남긴다 (V1과 동일)
- 리포트의 모든 숫자는 `results-v2.csv`·`runs/*.json`에서 나온 값만 쓰고 출처를 단다

---

## 8. 해석 기준 (리포트 작성 시)

- **반드시 0/100%여야 하는 것**: 최종 위반율 0, 경고 표시율 100%, 임신 질문 정확도 100%, 주제 계획 준수율 100%, 제외 일관성 100%. 하나라도 어긋나면 버그로 보고 원인을 적는다
- **기록하고 해석하는 것**: 재요청 비율, 질문 문장 유사도, 주제 경로 일치율, recall/precision, 지연·비용
- 주제 경로 일치율이 낮으면 → kind 판단이 흔들린다는 뜻 → 프롬프트의 kind 정의 보강을 다음 할 일로
- 질문 생성 p95가 크면 → 대기 화면 문구 시간과 비교, 작은 모델 라우팅 검토를 다음 할 일로
