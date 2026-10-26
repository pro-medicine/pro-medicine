# AI 영양제 추천 - Claude Code 작업 프롬프트

> 이 프롬프트들이 만든 측정 자료(`docs/perf/`, `perf/`)와 부하·평가 테스트 코드는 저장소에서 뺐다 (로컬에서만 사용). 프롬프트 안의 해당 경로·명령은 작업 기록용이다.

Claude Code에게 STEP 순서대로 하나씩 맡기는 프롬프트 모음이에요.
기능 구현은 기본이고, STEP마다 **Before/After를 같은 평가셋으로 측정해서 기록**하는 게 목표예요.

## 파일 구성

| 파일 | 내용 | 실제 Claude API 호출 |
| --- | --- | --- |
| `00_COMMON.md` | 모든 STEP 공통 규칙 (각 STEP이 먼저 읽음) | - |
| `01_entity.md` | Flyway·Testcontainers 설정, ERD → 엔티티 생성 | 없음 |
| `02_survey_api.md` | 설문 시작 · 민감정보 동의 · 제출, 프로필 반영 | 없음 |
| `03_llm_v0.md` | Claude API 연결, V0(가장 단순한 추천), 호출 기록 테이블 | 수동 3회 |
| `04_measurement_baseline.md` | 단계별 지연 측정, 평가셋, 평가 러너, **V0 기준선 측정** | 평가셋 1회 + 재현성 50회 |
| `05_v1_structured_output.md` | V1: tool use로 출력 형식 강제 + 재요청 | 같은 양 |
| `06_v2_dose_calculation.md` | V2: 용량은 기준표로 코드 계산 | 같은 양 |
| `07_v3_v4_rules.md` | V3: 사후 규칙 검증, V4: 사전 금지 목록 | 2배 |
| `08_cost_optimization.md` | V6 결과 캐시, V7 프롬프트 캐싱, V8 모델 라우팅 + k6 | 4배 (부하 테스트는 WireMock) |
| `09_async_and_spike.md` | V9: 동기/가상 스레드/큐 비교 + **알림 발송 트래픽 몰림** | 없음 (WireMock) |
| `10_v10_resilience.md` | V10: 타임아웃·재시도·서킷 브레이커·규칙 폴백 + 장애 주입 | 없음 (WireMock) |
| `11_final_report.md` | 전체 결과를 `docs/perf/SUMMARY.md`로 정리 | 없음 |
| `reference/erd.sql` | 확정 ERD | |
| `reference/domain-rules.md` | 기준표·규칙 초기값과 출처 | |
| `reference/measurement.md` | 지표 정의, 기록 형식, 평가셋 형식 | |

버전 번호는 처음 설계(V0~V10)를 그대로 따랐어요. V5(RAG)는 비교 결과 지금 구조에 필요 없다고 판단해서 뺐어요.

## 사용 방법

### 1. 프롬프트를 프로젝트 안으로 복사 (한 번만)

**PowerShell**에서 실행해요. `<프로젝트 경로>`를 실제 경로로 바꿔주세요.

```powershell
New-Item -ItemType Directory -Force "<프로젝트 경로>\docs\prompts"
Copy-Item -Recurse -Force "C:\Users\Administrator\Desktop\프롬포트\supplement-recommendation\*" "<프로젝트 경로>\docs\prompts\"
```

Claude Code는 프로젝트 폴더 안의 파일을 다루는 게 편해서, 모든 프롬프트가 `docs/prompts/` 경로를 기준으로 쓰여 있어요.

### 2. Claude Code 실행

**프로젝트 루트에서 연 터미널**(IntelliJ 터미널 또는 PowerShell)에서 실행해요.

```powershell
claude
```

### 3. STEP 하나씩 진행

Claude Code 입력창에 아래처럼 입력해요.

```text
docs/prompts/01_entity.md 를 읽고 그대로 진행해줘
```

- STEP 하나가 끝나면 결과(테스트 통과, `docs/progress.md`, `docs/perf/STEPxx-report.md`)를 확인하고 커밋한 뒤 다음으로 넘어가요.
- 다음 STEP으로 넘어가기 전에 Claude Code 입력창에 `/clear`를 입력해서 대화를 비워요. 이전 STEP 내용은 `docs/progress.md`에 남아 있어서 이어서 작업할 수 있어요.
- STEP 04, 07, 09처럼 큰 단계는 바로 시작하지 말고 계획부터 보는 게 좋아요. 입력창에서 `Shift + Tab`을 눌러 plan mode로 바꾼 뒤 같은 문장을 입력하면, 계획을 먼저 보여주고 승인받은 뒤 진행해요.

### 4. 평가 실행 (STEP 04부터)

실제 API를 쓰는 명령은 비용이 들어서 직접 실행하는 걸 추천해요. **프로젝트 루트 터미널**에서 실행해요.

```powershell
# 예상 호출 수와 비용만 출력
./gradlew evalRun -PpipelineVersion=V0

# 실제 실행
./gradlew evalRun -PpipelineVersion=V0 -Pconfirm=true

# 재현성 측정 (10케이스 × 5회)
./gradlew evalRun -PpipelineVersion=V0 -Pmode=repro -Pconfirm=true
```

## STEP 시작 전에 직접 해야 할 것

| 언제 | 할 일 |
| --- | --- |
| STEP 03 전 | Anthropic Console에서 API 키 발급 → 환경변수 설정 (아래) |
| STEP 04 후 | Claude Code가 만든 평가셋(`src/test/resources/eval/cases.json`) 검토 후 `reviewed: true`로 변경 |
| STEP 06 전 | `reference/domain-rules.md` 2번 표의 `TODO` 칸을 2025 한국인 영양소 섭취기준 원문 수치로 채우기 (안 채우면 해당 성분은 용량 없이 추천돼요) |
| STEP 01 전 | Docker Desktop 설치·실행 (테스트용 MySQL 컨테이너. STEP 08부터 Redis, WireMock도 사용) |
| STEP 08 전 | k6 설치 |
| STEP 09 전 | Anthropic Console에서 내 계정의 rate limit 확인 |
| 서비스 공개 전 | 규칙·기준값 약사 검토, 민감정보 동의 문구 법률 검토 |

**API 키 설정 (PowerShell)**

```powershell
setx ANTHROPIC_API_KEY "발급받은-키"
```

설정 후 터미널과 IntelliJ를 다시 열어야 적용돼요. 키는 코드나 `application.yml`에 넣지 마세요.

**k6 설치 (PowerShell)**

```powershell
winget install k6 --source winget
```

## 결과물이 쌓이는 곳

```text
docs/
├── prompts/            (이 프롬프트들)
├── progress.md         (STEP별 진행 기록)
├── schema-changes.md   (STEP별 추가된 테이블·컬럼)
└── perf/
    ├── results.csv     (모든 측정 결과 누적)
    ├── STEPxx-report.md
    ├── runs/           (평가 실행 상세)
    ├── pricing.md      (토큰 단가와 확인 날짜)
    └── SUMMARY.md      (STEP 11 최종)
```

## 알아두면 좋은 것

- 원래 ERD에서 바뀌는 스키마는 STEP 03(`llm_call_logs`), 06(`ingredient_standards`, 추천 항목 용량 컬럼), 07(`recommendation_rules`, 추천 항목 제외·경고 컬럼), 09(추천 상태 컬럼, `notifications.dedup_key` 유니크)뿐이에요. 전부 `docs/schema-changes.md`에 기록돼요.
- `catalog_products`에는 제품별 성분 함량 테이블이 없어서, 지금은 "현재 섭취량"을 설문(`Q-SUPPLEMENTS`)으로 받아요. 등록한 영양제에서 자동으로 계산하려면 제품-성분 테이블이 따로 필요해요.
- 비회원 응답을 가입 후 계정에 연결하는 기능은 범위에서 뺐어요 (가로채기 방지 설계가 먼저 필요해요).
