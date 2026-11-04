package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.chat.dto.ChatCard;
import com.ucamp.app.domain.chat.entity.AiChatResult;
import com.ucamp.app.domain.chat.entity.AiChatSession;
import com.ucamp.app.domain.chat.entity.AiChatTurn;
import com.ucamp.app.domain.chat.repository.AiChatResultRepository;
import com.ucamp.app.domain.chat.repository.AiChatSessionRepository;
import com.ucamp.app.domain.chat.repository.AiChatTurnRepository;
import com.ucamp.app.domain.chat.service.ChatSessionStore;
import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.personalized.entity.AiQuestionTurn;
import com.ucamp.app.domain.personalized.planner.OptionKind;
import com.ucamp.app.domain.personalized.repository.AiQuestionSessionRepository;
import com.ucamp.app.domain.personalized.repository.AiQuestionTurnRepository;
import com.ucamp.app.domain.personalized.service.PersonalizedSessionCleanupScheduler;
import com.ucamp.app.domain.personalized.service.TurnCodec;
import com.ucamp.app.domain.personalized.service.TurnOption;
import com.ucamp.app.domain.product.entity.CatalogProduct;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationProduct;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationItemRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationProductRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.recommendation.service.RecommendationHistoryService;
import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import com.ucamp.app.infra.llm.embed.FakeEmbeddingClient;
import com.ucamp.app.support.TestMembers;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyAnswers.number;
import static com.ucamp.app.support.SurveyFixture.GENDER_F;
import static com.ucamp.app.support.SurveyFixture.Q_BIRTH;
import static com.ucamp.app.support.SurveyFixture.Q_GENDER;
import static com.ucamp.app.support.SurveyFixture.Q_GOAL;
import static com.ucamp.app.support.SurveyFixture.SURVEY_ID;
import static com.ucamp.app.support.TestJwt.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V7.1 마이페이지 "추천 기록" GET /api/v1/users/me/recommendations/history (+ 상세 2개).
 * 데이터는 설문 API(Fake LLM, V1) 또는 리포지토리로 만든다. 기록 요청 중 LLM·임베딩 호출 0회를 매번 확인한다.
 * 테스트 트랜잭션 안이라 DONE 커밋 뒤 제품 순위 저장(AFTER_COMMIT)은 돌지 않는다 → 스냅샷이 필요한 시나리오는 행을 직접 넣는다
 * (저장 자체는 RecommendationProductSnapshotTest가 실제 커밋으로 확인)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestMembers.class)
@Transactional
@Sql(scripts = "/sql/schema-review-products.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql("/sql/seed-review-products.sql")
class RecommendationHistoryApiTest {
    static final String URL = "/api/v1/users/me/recommendations/history";
    static final LocalDateTime T0 = LocalDateTime.now().minusDays(10).withNano(0);
    static final long GOAL_SLEEP = 24L;
    static final long GOAL_FATIGUE = 25L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    TestMembers members;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    FakeEmbeddingClient fakeEmbedding;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;
    @Autowired
    TurnCodec turnCodec;
    @Autowired
    AiQuestionSessionRepository questionSessionRepository;
    @Autowired
    AiQuestionTurnRepository questionTurnRepository;
    @Autowired
    AiChatSessionRepository chatSessionRepository;
    @Autowired
    AiChatTurnRepository chatTurnRepository;
    @Autowired
    AiChatResultRepository chatResultRepository;
    @Autowired
    ChatSessionStore chatSessionStore;
    @Autowired
    AiRecommendationRepository recommendationRepository;
    @Autowired
    AiRecommendationItemRepository itemRepository;
    @Autowired
    AiRecommendationProductRepository productRepository;
    @Autowired
    PersonalizedSessionCleanupScheduler cleanupScheduler;

    @AfterEach
    void reset() {
        fake.reset();
        fakeEmbedding.reset();
    }

    // ---------- 목록 ----------

    @Test
    void 기록이_없으면_빈_목록() throws Exception {
        assertThat(history(member()).path("items").size()).isZero();
    }

    @Test
    void 출처를_섞어_완료_시각_최신순으로_불편한_점과_성분을_보여준다() throws Exception {
        User user = member();
        long v1 = v1(user, List.of(GOAL_SLEEP, GOAL_FATIGUE), "MAGNESIUM", "VITAMIN_B6");
        setRecommendationTime(v1, T0);
        long v2 = v2(user, List.of(0, 1), "OMEGA3");
        setRecommendationTime(v2, T0.plusHours(1));
        AiChatSession chat2 = chat(user, "요즘 기운이 없어요");
        long track2 = track2(chat2, "VITAMIN_D");
        setRecommendationTime(track2, T0.plusHours(2));
        AiChatSession chat1 = chat(user, "잠이 안와요");
        long track1 = track1(chat1, T0.plusHours(3), "MAGNESIUM", "VITAMIN_D");

        JsonNode items = history(user).path("items");

        assertThat(items.size()).isEqualTo(4);
        JsonNode first = items.get(0);
        assertThat(first.path("source").asString()).isEqualTo("V4_CHAT");
        assertThat(first.path("chatResultId").asLong()).isEqualTo(track1);
        assertThat(first.path("recommendationId").isNull()).isTrue();
        assertThat(first.path("concern").path("type").asString()).isEqualTo("TEXT");
        assertThat(first.path("concern").path("text").asString()).isEqualTo("잠이 안와요");
        assertThat(strings(first.path("ingredients"))).containsExactly("MAGNESIUM", "VITAMIN_D");

        JsonNode second = items.get(1);
        assertThat(second.path("source").asString()).isEqualTo("V4_CHAT");
        assertThat(second.path("recommendationId").asLong()).isEqualTo(track2);
        assertThat(second.path("concern").path("text").asString()).isEqualTo("요즘 기운이 없어요");
        assertThat(strings(second.path("ingredients"))).containsExactly("VITAMIN_D");

        JsonNode third = items.get(2);
        assertThat(third.path("source").asString()).isEqualTo("V2_PERSONALIZED");
        assertThat(third.path("concern").path("type").asString()).isEqualTo("LABELS");
        assertThat(strings(third.path("concern").path("labels"))).containsExactly("쉽게 피곤해요", "잠을 잘 못 자요");

        JsonNode fourth = items.get(3);
        assertThat(fourth.path("source").asString()).isEqualTo("V1_SURVEY");
        assertThat(fourth.path("recommendationId").asLong()).isEqualTo(v1);
        assertThat(fourth.path("concern").path("type").asString()).isEqualTo("LABELS");
        assertThat(strings(fourth.path("concern").path("labels"))).containsExactly("수면", "피로 회복");
        assertThat(strings(fourth.path("ingredients"))).containsExactly("MAGNESIUM", "VITAMIN_B6");
        assertThat(fourth.path("recommendedAt").asString()).startsWith(T0.toString().substring(0, 16));
    }

    @Test
    void 기록은_최근_20건까지() throws Exception {
        User user = member();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < RecommendationHistoryService.LIMIT + 1; i++) {
            long id = v2(user, List.of(0), "OMEGA3");
            setRecommendationTime(id, T0.plusMinutes(i));
            ids.add(id);
        }

        JsonNode items = history(user).path("items");

        assertThat(items.size()).isEqualTo(20);
        assertThat(items.get(0).path("recommendationId").asLong()).isEqualTo(ids.get(20));
        assertThat(items.get(19).path("recommendationId").asLong()).isEqualTo(ids.get(1)); // 가장 오래된 1건은 빠짐
    }

    @Test
    void V2_없어요_모르겠어요_답없음은_NONE() throws Exception {
        User user = member();
        long none = v2(user, List.of(2), "OMEGA3");
        setRecommendationTime(none, T0.plusHours(2));
        long unknown = v2(user, List.of(3), "OMEGA3");
        setRecommendationTime(unknown, T0.plusHours(1));
        long noAnswer = v2(user, null, "OMEGA3");
        setRecommendationTime(noAnswer, T0);

        JsonNode items = history(user).path("items");

        assertThat(items.get(0).path("concern").path("type").asString()).isEqualTo("NONE");
        assertThat(items.get(0).path("concern").path("text").asString()).isEqualTo("특별히 불편한 점 없음");
        assertThat(items.get(1).path("concern").path("text").asString()).isEqualTo("잘 모르겠음");
        assertThat(items.get(2).path("concern").path("type").asString()).isEqualTo("NONE");
        assertThat(items.get(2).path("concern").path("text").isNull()).isTrue();
    }

    @Test
    void V4는_되묻기_답을_같이_주고_주제_밖으로_거절된_앞_입력은_빼다() throws Exception {
        User user = member();
        AiChatSession clarified = chatSessionRepository.save(AiChatSession.start(user));
        turn(clarified, 1, "피곤해요", "{\"question\":\"언제 피곤하세요?\",\"options\":[]}");
        turn(clarified, 2, "오후에요", null);
        track1(clarified, T0.plusHours(1), "MAGNESIUM");
        AiChatSession offTopic = chatSessionRepository.save(AiChatSession.start(user));
        turn(offTopic, 1, "오늘 날씨 어때?", null);
        turn(offTopic, 2, "잠이 안와요", null);
        track1(offTopic, T0, "MAGNESIUM");

        JsonNode items = history(user).path("items");

        assertThat(items.get(0).path("concern").path("text").asString()).isEqualTo("피곤해요");
        assertThat(items.get(0).path("concern").path("answer").asString()).isEqualTo("오후에요");
        assertThat(items.get(1).path("concern").path("text").asString()).isEqualTo("잠이 안와요");
        assertThat(items.get(1).path("concern").path("answer").isNull()).isTrue();
    }

    @Test
    void 끝나지_않은_추천과_다른_회원_기록은_목록에_없다() throws Exception {
        User user = member();
        User other = member();
        AiQuestionSession session = questionSessionRepository.save(AiQuestionSession.start(user));
        recommendationRepository.save(AiRecommendation.builder().session(session).user(user)
                .status(AiRecommendation.PENDING).build());
        recommendationRepository.save(AiRecommendation.builder().session(session).user(user)
                .status(AiRecommendation.FAILED).build());
        long mine = v2(user, List.of(0), "OMEGA3");
        v2(other, List.of(0), "MAGNESIUM");
        track1(chat(other, "남의 고민"), T0, "MAGNESIUM");

        JsonNode items = history(user).path("items");

        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).path("recommendationId").asLong()).isEqualTo(mine);
    }

    // ---------- 90일 ----------

    @Test
    void 지난_90일_정리_뒤에도_기록은_남고_불편한_점만_EXPIRED() throws Exception {
        User user = member();
        long v1 = v1(user, List.of(GOAL_SLEEP), "MAGNESIUM");
        setRecommendationTime(v1, T0);
        long v2 = v2(user, List.of(0), "OMEGA3");
        setRecommendationTime(v2, T0.plusHours(1));
        AiChatSession chat2 = chat(user, "요즘 기운이 없어요");
        long track2 = track2(chat2, "VITAMIN_D");
        setRecommendationTime(track2, T0.plusHours(2));
        AiChatSession chat1 = chat(user, "잠이 안와요");
        long track1 = track1(chat1, T0.plusHours(3), "MAGNESIUM");
        snapshot(v2, "OMEGA3", 9315L);
        long responseId = jdbcTemplate.queryForObject(
                "select response_id from ai_recommendations where recommendation_id = ?", Long.class, v1);
        int answersBefore = answers(responseId);
        entityManager.flush();
        jdbcTemplate.update("update ai_question_sessions set started_at = DATEADD('DAY', -91, started_at) where user_id = ?",
                user.getId());
        jdbcTemplate.update("update ai_chat_sessions set started_at = DATEADD('DAY', -91, started_at) where user_id = ?",
                user.getId());
        jdbcTemplate.update("update survey_responses set submitted_at = DATEADD('DAY', -91, submitted_at) where response_id = ?",
                responseId);

        cleanupScheduler.purge(LocalDateTime.now());
        entityManager.clear();

        // 원문은 지워진다: 대화 입력·맞춤 질문, V1 Q-GOAL 답 (응답·다른 답은 남음)
        assertThat(count("select count(*) from ai_chat_turns where chat_session_id in (?, ?)",
                chat1.getChatSessionId(), chat2.getChatSessionId())).isZero();
        assertThat(count("select count(*) from ai_question_turns t join ai_question_sessions s on s.session_id = t.session_id "
                + "where s.user_id = ?", user.getId())).isZero();
        assertThat(count("select count(*) from survey_answers a join survey_questions q on q.question_id = a.question_id "
                + "where a.response_id = ? and q.code = 'Q-GOAL'", responseId)).isZero();
        assertThat(answers(responseId)).isEqualTo(answersBefore - 1);
        assertThat(count("select count(*) from survey_responses where response_id = ?", responseId)).isEqualTo(1);
        // 트랙 1 결과 행은 남고 세션만 NULL
        assertThat(jdbcTemplate.queryForObject("select chat_session_id from ai_chat_results where chat_result_id = ?",
                Long.class, track1)).isNull();

        JsonNode items = history(user).path("items");
        assertThat(items.size()).isEqualTo(4);
        assertThat(sources(items)).containsExactly("V4_CHAT", "V4_CHAT", "V2_PERSONALIZED", "V1_SURVEY");
        items.forEach(i -> assertThat(i.path("concern").path("type").asString()).isEqualTo("EXPIRED"));
        assertThat(strings(items.get(0).path("ingredients"))).containsExactly("MAGNESIUM");

        // 상세 제품도 그대로
        JsonNode v2Detail = detail(user, v2);
        assertThat(v2Detail.path("snapshot").asBoolean()).isTrue();
        assertThat(ids(card(v2Detail, "OMEGA3"))).containsExactly(9315L);
        assertThat(ids(card(chatDetail(user, track1), "MAGNESIUM"))).containsExactly(9301L);

        // 세션이 NULL인 트랙 1 결과: V7 /recent·대화 재사용도 오류 없이 (세션으로 찾으니 잡히지 않음)
        mockMvc.perform(get("/api/v1/users/me/recommendations/recent").header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk());
        assertThat(chatSessionStore.result(chat1.getChatSessionId())).isEmpty();
        assertThat(chatSessionStore.findReusable(user.getId(), "anything")).isEmpty();
    }

    // ---------- 상세 ----------

    @Test
    void 저장된_순위가_있으면_그대로_보여주고_판매중단_차단_제품은_뺀다() throws Exception {
        User user = member();
        long recId = v2(user, List.of(0), "MAGNESIUM", "OMEGA3");
        // 지금 V3 순위(9301 → 9302 → 9305)와 다른 순서 + 비활성 9308 + 차단 9307
        snapshotWith(recId, "MAGNESIUM", List.of(9305L, 9308L, 9301L, 9307L), List.of("저장된 경고"));
        jdbcTemplate.update("update review_product_scores set base_score = 9.9 where catalog_product_id = 9302");

        JsonNode data = detail(user, recId);

        assertThat(data.path("snapshot").asBoolean()).isTrue();
        assertThat(data.path("source").asString()).isEqualTo("V2_PERSONALIZED");
        assertThat(ids(card(data, "MAGNESIUM"))).containsExactly(9305L, 9301L);
        JsonNode p = card(data, "MAGNESIUM").path("products").get(0);
        assertThat(p.path("productName").asString()).isNotBlank();
        assertThat(strings(p.path("warnings"))).containsExactly("저장된 경고");
        // 스냅샷이 있는 추천에서 저장 행이 없는 성분은 빈 목록 (다시 계산하지 않음)
        assertThat(card(data, "OMEGA3").path("products").size()).isZero();
        assertThat(data.path("notice").asString()).isEqualTo(ReviewProductResponse.MEMBER_NOTICE);
    }

    @Test
    void 저장된_순위가_없는_예전_추천은_지금_기준으로_다시_계산() throws Exception {
        User user = member();
        long recId = v2(user, List.of(0), "MAGNESIUM");

        JsonNode data = detail(user, recId);

        assertThat(data.path("snapshot").asBoolean()).isFalse();
        assertThat(card(data, "MAGNESIUM").path("products")).isEqualTo(v3Products(user, recId, "MAGNESIUM"));
    }

    @Test
    void 트랙1_상세는_저장된_카드_그대로_비활성_제품만_뺀다() throws Exception {
        User user = member();
        AiChatSession chat = chat(user, "잠이 안와요");
        List<ChatCard> cards = List.of(new ChatCard("MAGNESIUM", "비슷한 고민", 1, BigDecimal.ONE, "mg", null, "UNKNOWN",
                null, List.of(), List.of(product(9301, "스냅샷 이름"), product(9308, "비활성 제품"))));
        long id = saveResult(chat, cards, T0);

        JsonNode data = chatDetail(user, id);

        assertThat(data.path("snapshot").asBoolean()).isTrue();
        assertThat(data.path("chatResultId").asLong()).isEqualTo(id);
        assertThat(ids(card(data, "MAGNESIUM"))).containsExactly(9301L);
        assertThat(card(data, "MAGNESIUM").path("products").get(0).path("productName").asString()).isEqualTo("스냅샷 이름");
    }

    @Test
    void 남의_기록_끝나지_않은_추천은_404_비회원은_401() throws Exception {
        User user = member();
        User other = member();
        long othersRec = v2(other, List.of(0), "OMEGA3");
        long othersChat = track1(chat(other, "남의 고민"), T0, "MAGNESIUM");
        AiQuestionSession session = questionSessionRepository.save(AiQuestionSession.start(user));
        long pending = recommendationRepository.save(AiRecommendation.builder().session(session).user(user)
                .status(AiRecommendation.PENDING).build()).getRecommendationId();

        for (String path : List.of(URL + "/" + othersRec, URL + "/chat/" + othersChat, URL + "/" + pending,
                URL + "/999999", URL + "/chat/999999")) {
            mockMvc.perform(get(path).header("Authorization", bearer(user.getId())))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("R001"));
        }
        for (String path : List.of(URL, URL + "/" + othersRec, URL + "/chat/" + othersChat)) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void 회원_V1_추천과_대화_결과에는_user_id가_저장된다() throws Exception {
        User user = member();
        long v1 = v1(user, List.of(GOAL_SLEEP), "MAGNESIUM");
        AiChatSession chat = chat(user, "잠이 안와요");
        chatSessionStore.saveReviewResult(chat.getChatSessionId(), "{\"cards\":[]}", "[]", null, "{}", null, 0);
        entityManager.flush();

        assertThat(jdbcTemplate.queryForObject("select user_id from ai_recommendations where recommendation_id = ?",
                Long.class, v1)).isEqualTo(user.getId());
        assertThat(jdbcTemplate.queryForObject("select user_id from ai_chat_results where chat_session_id = ?",
                Long.class, chat.getChatSessionId())).isEqualTo(user.getId());
    }

    // ---------- 도우미 ----------

    JsonNode history(User user) throws Exception {
        return call(URL, user);
    }

    JsonNode detail(User user, long recId) throws Exception {
        return call(URL + "/" + recId, user);
    }

    JsonNode chatDetail(User user, long chatResultId) throws Exception {
        return call(URL + "/chat/" + chatResultId, user);
    }

    // 기록 API 호출 + LLM·임베딩 0회 확인
    JsonNode call(String url, User user) throws Exception {
        entityManager.flush();
        entityManager.clear();
        fake.reset();
        fakeEmbedding.reset();
        String body = mockMvc.perform(get(url).header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(fake.requests()).isEmpty();
        assertThat(fakeEmbedding.requests()).isEmpty();
        return objectMapper.readTree(body).path("data");
    }

    JsonNode v3Products(User user, long recId, String ingredient) throws Exception {
        String body = mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products").param("ingredient", ingredient)
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("products");
    }

    User member() {
        return members.create(true, Map.of("gender", "F", "birth_year", 1995), List.of(), List.of());
    }

    // V1: 설문(Q-GOAL 포함) 제출 → 추천 생성 (Fake LLM)
    long v1(User user, List<Long> goals, String... ingredients) throws Exception {
        fake.answerWith(new FakeAnswer(Arrays.stream(ingredients)
                .map(c -> new FakeAnswer.Item(c, null, "가짜 응답", BigDecimal.ONE, null)).toList(), "가짜 응답입니다"));
        String submitted = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON).header("Authorization", bearer(user.getId()))
                        .content(objectMapper.writeValueAsString(Map.of("answers", List.of(
                                choice(Q_GOAL, goals.toArray(Long[]::new)), choice(Q_GENDER, GENDER_F),
                                number(Q_BIRTH, 1995))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long responseId = objectMapper.readTree(submitted).path("data").path("responseId").asLong();
        String body = mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(user.getId())).content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("recommendationId").asLong();
    }

    // V2: 회차 + 불편한 점 질문(CONCERN_OPEN, selected null이면 답 없음) + DONE 추천
    long v2(User user, List<Integer> selected, String... ingredients) {
        AiQuestionSession session = questionSessionRepository.save(AiQuestionSession.start(user));
        AiQuestionTurn turn = AiQuestionTurn.builder().session(session).turnNo(2).fixed(false).topic("CONCERN_OPEN")
                .depth(1).questionText("요즘 불편한 점을 골라주세요").answerType("MULTI")
                .optionsJson(turnCodec.encodeOptions(List.of(TurnOption.of("쉽게 피곤해요", OptionKind.CONCERN),
                        TurnOption.of("잠을 잘 못 자요", OptionKind.CONCERN), TurnOption.of("없어요", OptionKind.NONE),
                        TurnOption.of("잘 모르겠어요", OptionKind.UNKNOWN))))
                .build();
        if (selected != null) {
            turn.answer(turnCodec.encodeSelected(selected));
        }
        questionTurnRepository.save(turn);
        AiRecommendation rec = recommendationRepository.save(AiRecommendation.builder().session(session).user(user)
                .summary("가짜 정밀 추천").modelName("fake").status(AiRecommendation.DONE).build());
        items(rec, ingredients);
        return rec.getRecommendationId();
    }

    AiChatSession chat(User user, String text) {
        AiChatSession chat = chatSessionRepository.save(AiChatSession.start(user));
        turn(chat, 1, text, null);
        return chat;
    }

    void turn(AiChatSession chat, int no, String text, String clarification) {
        chatTurnRepository.save(AiChatTurn.builder().chatSession(chat).turnNo(no).userText(text)
                .clarification(clarification).llmCallLogIds("[]").build());
    }

    // V4 트랙 2: DONE 추천 + RULE_BASED 결과 행 (ChatSessionStore.startFallback과 같은 연결)
    long track2(AiChatSession chat, String... ingredients) {
        AiRecommendation rec = recommendationRepository.save(AiRecommendation.builder().chatSession(chat)
                .user(chat.getUser()).summary("가짜 대화 추천").modelName("fake").status(AiRecommendation.DONE).build());
        items(rec, ingredients);
        chatResultRepository.save(AiChatResult.builder().chatSession(chat).user(chat.getUser())
                .track(AiChatResult.RULE_BASED).recommendationId(rec.getRecommendationId()).build());
        return rec.getRecommendationId();
    }

    // V4 트랙 1: 성분마다 제품 9301 하나인 카드 스냅샷
    long track1(AiChatSession chat, LocalDateTime at, String... ingredients) {
        List<ChatCard> cards = new ArrayList<>();
        for (int i = 0; i < ingredients.length; i++) {
            cards.add(new ChatCard(ingredients[i], "비슷한 고민", i + 1, BigDecimal.ONE, "mg", null, "UNKNOWN", null,
                    List.of(), List.of(product(9301, "스냅샷 제품"))));
        }
        return saveResult(chat, cards, at);
    }

    long saveResult(AiChatSession chat, List<ChatCard> cards, LocalDateTime at) {
        String json = objectMapper.writeValueAsString(Map.of("cards", cards, "excludedItems", List.of(),
                "excludedProductCount", 0, "medicationNotice", ""));
        AiChatResult result = chatResultRepository.save(AiChatResult.builder().chatSession(chat).user(chat.getUser())
                .track(AiChatResult.REVIEW).cards(json).build());
        entityManager.flush();
        jdbcTemplate.update("update ai_chat_results set created_at = ? where chat_result_id = ?", Timestamp.valueOf(at),
                result.getChatResultId());
        return result.getChatResultId();
    }

    static ReviewProductResponse.Product product(long id, String name) {
        return new ReviewProductResponse.Product(id, name, "NOW", null, null, List.of(), List.of(), List.of());
    }

    void items(AiRecommendation rec, String... ingredients) {
        for (int i = 0; i < ingredients.length; i++) {
            itemRepository.save(AiRecommendationItem.builder().recommendation(rec).ingredientName(ingredients[i])
                    .reason("가짜 응답").sortOrder(i + 1).build());
        }
    }

    void snapshot(long recId, String ingredient, Long... productIds) {
        snapshotWith(recId, ingredient, List.of(productIds), List.of());
    }

    // 제품 순위 스냅샷 행 직접 저장 (커밋 뒤 저장 흐름은 RecommendationProductSnapshotTest)
    void snapshotWith(long recId, String ingredient, List<Long> productIds, List<String> warnings) {
        entityManager.flush();
        AiRecommendationItem item = itemRepository
                .findByRecommendation_RecommendationIdOrderBySortOrderAscRecommendationItemIdAsc(recId).stream()
                .filter(i -> i.getIngredientName().equals(ingredient)).findFirst().orElseThrow();
        int rank = 1;
        for (Long id : productIds) {
            productRepository.save(AiRecommendationProduct.builder().item(item)
                    .product(entityManager.getReference(CatalogProduct.class, id)).productRank(rank++)
                    .alsoContains("[]").warnings(objectMapper.writeValueAsString(warnings)).build());
        }
        entityManager.flush();
    }

    void setRecommendationTime(long recId, LocalDateTime at) {
        entityManager.flush();
        jdbcTemplate.update("update ai_recommendations set created_at = ?, finished_at = ? where recommendation_id = ?",
                Timestamp.valueOf(at.minusSeconds(5)), Timestamp.valueOf(at), recId);
        entityManager.clear();
    }

    int answers(long responseId) {
        return count("select count(*) from survey_answers where response_id = ?", responseId);
    }

    int count(String sql, Object... args) {
        entityManager.flush();
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }

    static List<String> sources(JsonNode items) {
        List<String> out = new ArrayList<>();
        items.forEach(n -> out.add(n.path("source").asString()));
        return out;
    }

    static JsonNode card(JsonNode data, String ingredient) {
        for (JsonNode c : data.path("ingredients")) {
            if (ingredient.equals(c.path("ingredient").asString())) {
                return c;
            }
        }
        throw new AssertionError(ingredient + " 카드가 없음: " + data);
    }

    static List<Long> ids(JsonNode card) {
        List<Long> out = new ArrayList<>();
        card.path("products").forEach(p -> out.add(p.path("catalogProductId").asLong()));
        return out;
    }
}
