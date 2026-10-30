# 00. 공통 규칙 (모든 STEP에서 먼저 읽는다)

이 프로젝트는 설문 기반 AI 영양제 추천 서비스다. 기능 구현은 기본이고, **목표는 단계별 개선을 수치로 측정해서 남기는 것**이다.
모든 STEP은 "구현 → 측정 → 기록" 순서로 끝난다. 측정 없이 끝난 STEP은 완료가 아니다.

## 1. 참고 자료

| 파일 | 내용 |
| --- | --- |
| `backend/docs/supplement-recommendation/reference/erd.sql` | 확정 ERD (MySQL DDL) |
| `backend/docs/supplement-recommendation/reference/domain-rules.md` | 기준표·규칙 테이블의 초기 데이터와 출처 |
| `backend/docs/supplement-recommendation/reference/measurement.md` | 측정 지표 정의, 기록 형식, 평가셋 형식 |

## 2. 패키지 구조

```text
{base-package}
├── global          (config, 공통 예외, 측정용 AOP 등)
├── infra
│   └── llm         (LLM 클라이언트. 도메인이 아니라 외부 연동이라 여기에 둔다)
└── domain/{도메인}/controller, service, dto, entity
```

- Repository는 `domain/{도메인}/repository`에 둔다.
- 도메인 간 호출은 service → service로만 한다. 다른 도메인의 repository를 직접 쓰지 않는다.

## 3. 스키마 변경 규칙

- `reference/erd.sql`에 없는 테이블·컬럼이 필요하면 STEP 프롬프트에 적힌 것만 추가한다. 임의로 추가하지 않는다.
- 스키마와 시드 데이터는 항상 Flyway 마이그레이션(`db/migration/V{n}__설명.sql`)으로 추가한다 (STEP 01에서 설정). `ddl-auto=validate`를 유지한다.
- 추가한 내용은 `docs/schema-changes.md`에 "STEP / 테이블 / 컬럼 / 이유" 표로 누적 기록한다.

## 4. LLM 사용 규칙

- 모든 LLM 호출은 `infra/llm`의 `LlmClient` 인터페이스를 거친다. 서비스 코드가 HTTP를 직접 부르지 않는다.
- API 키는 환경변수 `ANTHROPIC_API_KEY`로만 받는다. 코드, `application.yml`, 테스트, 문서에 키를 쓰지 않는다.
- 단위 테스트와 CI는 실제 API를 부르지 않는다 (`FakeLlmClient` 사용). 실제 API는 평가 실행(`evalRun`)과 수동 확인에서만 쓴다.
- 모델 이름, API 버전 헤더, 요청 형식은 코드에 하드코딩하기 전에 Anthropic 공식 문서(https://docs.claude.com)에서 현재 값을 확인한다.

## 5. 건강 정보 규칙

- 권장량·상한섭취량·상호작용 같은 수치와 규칙은 **절대 지어내지 않는다.** `reference/domain-rules.md`에 출처와 함께 있는 값만 쓴다.
- 값이 비어 있으면 빈 채로 두고 `TODO(출처 필요)`로 표시한다. 테스트에는 `src/test/resources`에 `TEST ONLY` 주석을 단 가짜 값을 따로 둔다.
- 로그에 질환·복용 약 원문을 남기지 않는다. 코드값(`HYPERTENSION`)이나 규칙 ID(`R-13`)만 남긴다.

## 6. 측정 기록 규칙

- 결과는 `docs/perf/results.csv`에 한 줄씩 누적한다. 기존 줄은 수정하지 않는다.
- STEP마다 `docs/perf/STEPxx-report.md`를 만든다. 형식은 `reference/measurement.md`를 따른다.
- Before/After는 **같은 평가셋, 같은 모델, 같은 조건**에서 측정한다. 조건이 다르면 리포트에 적는다.
- 수치를 추정하거나 반올림해서 좋아 보이게 쓰지 않는다. 실행한 명령과 원본 출력 경로를 리포트에 남긴다.

## 7. 진행 기록

STEP을 끝내면 `docs/progress.md`에 아래 형식으로 추가한다.

```markdown
## STEP xx - 제목 (YYYY-MM-DD)
- 한 일:
- 측정 결과 요약: (results.csv 줄 번호 또는 리포트 링크)
- 결정한 것 / 미룬 것:
- 다음 STEP 전에 사용자가 할 일:
```

## 8. 작업 방식

- 시작 전에 해당 STEP 프롬프트를 끝까지 읽고, 작업 계획을 5줄 이내로 먼저 보여준다.
- 프롬프트와 실제 코드가 맞지 않으면(이미 있는 클래스, 다른 이름 등) 임의로 맞추지 말고 먼저 알려준다.
- 각 STEP 끝에 빌드와 테스트(`./gradlew test`)가 통과해야 한다.
- 커밋은 사용자가 요청할 때만 한다.
