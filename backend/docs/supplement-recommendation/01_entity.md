# STEP 01. 엔티티 초기 생성

> 공통 규칙은 `docs/prompts/00_COMMON.md`를 먼저 읽고 따른다.

`docs/prompts/reference/erd.sql`을 기준으로 Spring Boot + JPA 엔티티 클래스를 만들어줘.
이번 작업은 **엔티티만** 만든다. controller, service, dto는 폴더만 만들고 클래스는 만들지 않는다.

## 1. 기술 스택

- Java 21 (STEP 09에서 가상 스레드를 쓰므로 고정), Spring Boot 3.x, Spring Data JPA, Lombok
- DB는 MySQL 기준 (`reference/erd.sql`의 타입을 따른다)
- 기존 프로젝트의 base package를 먼저 확인하고 그 아래에 만든다. 없으면 `com.example.supplement`를 쓴다.

## 1-1. 스키마 관리 (Flyway)

- Flyway를 추가한다. `src/main/resources/db/migration/V1__erd.sql` = `reference/erd.sql` 내용 그대로.
- 이후 STEP에서 추가하는 테이블·컬럼·시드 데이터는 전부 `V{n}__설명.sql`로 추가한다.
- `spring.jpa.hibernate.ddl-auto=validate`로 둔다. 엔티티와 스키마가 다르면 앱이 뜨지 않게 해서 불일치를 바로 잡는다.
- 테스트는 Testcontainers MySQL로 같은 마이그레이션을 적용해서 돌린다 (Docker 필요). 테스트용 공통 베이스 클래스를 `src/test/java/.../support/`에 만든다.
- 이번 STEP의 마지막 확인으로, 엔티티 전체가 `validate`를 통과하는 테스트를 하나 만든다.

## 2. 패키지 구조

```text
{base-package}
├── global
│   └── config
│       └── JpaAuditingConfig.java   (8번 참고)
└── domain
    ├── user
    │   ├── controller
    │   ├── service
    │   ├── dto
    │   └── entity
    ├── health
    ├── survey
    ├── recommendation
    ├── product
    ├── intake
    ├── notification
    ├── guardian
    ├── follow
    └── disposal
```

모든 도메인 폴더 아래에 `controller`, `service`, `dto`, `entity` 네 개 폴더를 만든다.
비어 있는 폴더는 `.gitkeep` 파일을 넣어 Git에 남게 한다.

## 3. 도메인별 테이블 배치

| 도메인 | 테이블 → 엔티티 클래스 |
| --- | --- |
| user | `users` → `User` |
| health | `health_profiles` → `HealthProfile`, `user_conditions` → `UserCondition`, `drugs` → `Drug`, `user_medications` → `UserMedication` |
| survey | `surveys` → `Survey`, `survey_questions` → `SurveyQuestion`, `question_options` → `QuestionOption`, `survey_responses` → `SurveyResponse`, `survey_answers` → `SurveyAnswer` |
| recommendation | `ai_recommendations` → `AiRecommendation`, `ai_recommendation_items` → `AiRecommendationItem` |
| product | `catalog_products` → `CatalogProduct`, `user_products` → `UserProduct` |
| intake | `intake_schedules` → `IntakeSchedule`, `intake_records` → `IntakeRecord` |
| notification | `notifications` → `Notification` |
| guardian | `guardian_settings` → `GuardianSetting` |
| follow | `follows` → `Follow` |
| disposal | `disposal_locations` → `DisposalLocation` |

`reference/erd.sql`에 있는 테이블은 전부 엔티티로 만들고, 위 표에 없는 테이블이 있으면 만들지 말고 알려줘.

## 4. 엔티티 작성 규칙

- `@Entity`, `@Table(name = "테이블명")`을 붙인다.
- 클래스에 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 붙인다. `@Setter`, `@Data`는 쓰지 않는다.
- 생성은 생성자에 `@Builder`를 붙여서 한다. 클래스 레벨 `@Builder`, `@AllArgsConstructor`는 쓰지 않는다.
- 빌더 생성자에는 자동 생성 PK(`IDENTITY`), `@MapsId`로 채워지는 `HealthProfile.userId`, Auditing 필드(`created_at`, `updated_at`)를 **뺀 나머지 필드 전부**를 받는다. `drugs.drug_id`는 직접 넣는 값이라 빌더에 포함한다.
- 컬럼명은 `@Column(name = "snake_case")`로 명시하고, 필드명은 camelCase로 쓴다.
- `NOT NULL` 컬럼은 `nullable = false`, `VARCHAR(n)`은 `length = n`, `DECIMAL(p,s)`는 `precision`, `scale`을 맞춘다.
- 타입 매핑: `BIGINT` → `Long`, `INT` → `Integer`, `SMALLINT` → `Integer` + `@Column(columnDefinition = "SMALLINT")` (이게 없으면 `ddl-auto=validate`에서 타입 불일치로 실패한다), `VARCHAR`/`TEXT` → `String`(`TEXT`는 `columnDefinition = "TEXT"`), `DECIMAL` → `BigDecimal`, `BOOLEAN` → `Boolean`, `DATETIME` → `LocalDateTime`, `DATE` → `LocalDate`, `TIME` → `LocalTime`
- `DEFAULT`가 있는 컬럼은 빌더에서 값이 안 들어와도 같은 기본값이 들어가도록 생성자에서 처리한다. (예: `isActive = isActive != null ? isActive : true`) 단, `DEFAULT CURRENT_TIMESTAMP`인 `created_at`/`updated_at`은 Auditing이 채우므로 생성자에서 다루지 않는다.
- 비즈니스 메서드는 이번에 만들지 않는다. 상태 변경 메서드도 만들지 않는다.

## 5. PK 규칙

- 기본은 `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` + `Long`
- `drugs.drug_id`는 `String` PK이고 직접 넣는 값이라 `@GeneratedValue`를 쓰지 않는다.
- `health_profiles.user_id`는 `users`와 1:1로 PK를 공유한다. `@Id Long userId` + `@MapsId @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") User user`로 매핑한다.
- `reference/erd.sql`의 PK 컬럼에는 `AUTO_INCREMENT`가 걸려 있다(`-- PK 자동 증가` 주석 아래 `MODIFY` 블록). `IDENTITY` 전략은 이걸 전제로 한다.

## 6. 연관관계 규칙

- FK 컬럼은 숫자 필드 대신 연관관계로 매핑한다: `@ManyToOne(fetch = FetchType.LAZY)` + `@JoinColumn(name = "...")`
- 단방향만 만든다. `@OneToMany`, `mappedBy`, `cascade`는 이번에 넣지 않는다.
- `reference/erd.sql`에 FK 제약이 없는 컬럼이라도 아래 표에 있으면 연관관계로 매핑한다. 표에 없는 `_id` 컬럼은 `Long` 필드로 둔다.

| 엔티티.필드 | 참조 대상 | NULL 허용 |
| --- | --- | --- |
| HealthProfile.user | User (`@MapsId` 1:1) | X |
| UserCondition.user | User | X |
| UserMedication.user | User | X |
| UserMedication.drug | Drug | O (마스터에 없는 약은 `unmatched_name`) |
| SurveyQuestion.survey | Survey | X |
| QuestionOption.question | SurveyQuestion | X |
| SurveyResponse.survey | Survey | X |
| SurveyResponse.user | User | O (비회원이면 null) |
| SurveyAnswer.response | SurveyResponse | X |
| SurveyAnswer.question | SurveyQuestion | X |
| SurveyAnswer.option | QuestionOption | O (숫자·주관식 답이면 null) |
| AiRecommendation.response | SurveyResponse | X |
| AiRecommendationItem.recommendation | AiRecommendation | X |
| UserProduct.user | User | X |
| UserProduct.catalogProduct | CatalogProduct | O (직접 입력 제품) |
| IntakeSchedule.userProduct | UserProduct | X |
| IntakeRecord.schedule | IntakeSchedule | X |
| Notification.user | User | X |
| Notification.schedule | IntakeSchedule | O |
| Notification.actorUser | User | O |
| GuardianSetting.user | User | X |
| GuardianSetting.guardianUser | User | X |
| Follow.follower | User | X |
| Follow.following | User | X |

- NULL 허용이 X면 `@ManyToOne(fetch = FetchType.LAZY, optional = false)`, `@JoinColumn(nullable = false)`를 함께 쓴다. (`HealthProfile.user`는 5번의 `@OneToOne` 규칙을 따른다)

## 7. Enum 규칙

값이 정해진 컬럼은 enum으로 만들고 `@Enumerated(EnumType.STRING)`으로 매핑한다. enum은 해당 도메인의 `entity` 패키지에 둔다.

| 컬럼 | Enum 이름 | 값 |
| --- | --- | --- |
| surveys.status | SurveyStatus | DRAFT, ACTIVE, RETIRED |
| survey_questions.answer_type | AnswerType | SINGLE, MULTI, NUMBER, TEXT |
| survey_responses.respondent_type | RespondentType | MEMBER, GUEST |
| survey_responses.status | ResponseStatus | IN_PROGRESS, SUBMITTED |

위 표에 없는 상태값 컬럼(`follows.status`, `intake_records.status`, `notifications.notification_type`, `user_products.product_kind`, `user_products.registered_via`, `intake_schedules.meal_slot`, `intake_schedules.meal_relation`, `disposal_locations.location_type`)은 값이 아직 정해지지 않았으니 `String`으로 두고 `// TODO: enum 전환` 주석을 단다.

## 8. 시간 필드

- `created_at`, `updated_at`은 JPA Auditing으로 채운다.
- 메인 애플리케이션 클래스에 `@EnableJpaAuditing`이 없으면 별도 `JpaAuditingConfig` 클래스를 `global/config`에 만든다.
- 테이블마다 시간 컬럼 구성이 달라서 공통 상속 클래스로 묶지 않는다. 각 엔티티에 `@EntityListeners(AuditingEntityListener.class)`를 붙이고 `created_at`에는 `@CreatedDate @Column(updatable = false)`, `updated_at`에는 `@LastModifiedDate`를 쓴다.
- `submitted_at`, `completed_at`, `sent_at`, `read_at` 같은 이벤트 시각은 Auditing 대상이 아니라 일반 컬럼으로 둔다.

## 9. 작업 순서

1. 프로젝트 구조와 base package를 확인한다.
2. 도메인 폴더와 하위 4개 폴더(`.gitkeep` 포함)를 만든다.
3. 참조되는 쪽부터 만든다: User → Drug, Survey, CatalogProduct → 나머지
4. 모든 엔티티를 만든 뒤 `./gradlew compileJava`(또는 Maven이면 `./mvnw compile`)로 컴파일이 되는지 확인한다.
5. 끝나면 만든 파일 목록과, 규칙대로 하지 못했거나 판단이 필요했던 부분을 정리해서 알려줘.
6. `docs/progress.md`에 STEP 01 완료 기록을 남긴다 (00_COMMON.md의 형식).

## 10. 하지 말 것

- `reference/erd.sql`에 없는 컬럼이나 테이블을 추가하지 않는다.
- controller, service, dto, repository 클래스는 만들지 않는다.
- 1-1의 Flyway·`ddl-auto=validate`·Testcontainers 설정 외에 `application.yml`을 바꾸지 않는다.
