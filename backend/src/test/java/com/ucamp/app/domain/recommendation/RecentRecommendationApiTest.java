package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.chat.dto.ChatCard;
import com.ucamp.app.domain.chat.entity.AiChatResult;
import com.ucamp.app.domain.chat.entity.AiChatSession;
import com.ucamp.app.domain.chat.repository.AiChatResultRepository;
import com.ucamp.app.domain.chat.repository.AiChatSessionRepository;
import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.personalized.repository.AiQuestionSessionRepository;
import com.ucamp.app.domain.personalized.service.PersonalizedSessionCleanupScheduler;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationItemRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
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
import static com.ucamp.app.support.SurveyAnswers.text;
import static com.ucamp.app.support.SurveyFixture.CONDITION_NONE;
import static com.ucamp.app.support.SurveyFixture.GENDER_F;
import static com.ucamp.app.support.SurveyFixture.Q_BIRTH;
import static com.ucamp.app.support.SurveyFixture.Q_CONDITIONS;
import static com.ucamp.app.support.SurveyFixture.Q_GENDER;
import static com.ucamp.app.support.SurveyFixture.Q_MEDICATIONS;
import static com.ucamp.app.support.SurveyFixture.SURVEY_ID;
import static com.ucamp.app.support.TestJwt.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V7 마이페이지 "최근 추천 제품 보기" GET /api/v1/users/me/recommendations/recent.
 * 추천 데이터는 설문 API(Fake LLM) 또는 리포지토리로 미리 만들고, /recent 요청 중에는 LLM·임베딩 호출이 0회인지 확인한다.
 * 시각은 JDBC로 직접 지정한다 (같은 트랜잭션 안에서 만든 행이라 저장 시각이 같을 수 있음).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestMembers.class)
@Transactional
@Sql(scripts = "/sql/schema-review-products.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql("/sql/seed-review-products.sql")
class RecentRecommendationApiTest {
    static final String URL = "/api/v1/users/me/recommendations/recent";
    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

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
    AiQuestionSessionRepository questionSessionRepository;
    @Autowired
    AiChatSessionRepository chatSessionRepository;
    @Autowired
    AiChatResultRepository chatResultRepository;
    @Autowired
    AiRecommendationRepository recommendationRepository;
    @Autowired
    AiRecommendationItemRepository itemRepository;
    @Autowired
    PersonalizedSessionCleanupScheduler cleanupScheduler;

    @AfterEach
    void reset() {
        fake.reset();
        fakeEmbedding.reset();
    }

    // ---------- 시나리오 ----------

    @Test
    void 기록이_없으면_exists_false() throws Exception {
        User user = member();

        JsonNode data = recent(user);

        assertThat(data.path("exists").asBoolean()).isFalse();
        assertThat(data.has("source")).isFalse();
        assertThat(data.has("ingredients")).isFalse();
    }

    @Test
    void V1_회원_설문_추천이면_성분_순서대로_V3와_같은_제품() throws Exception {
        User user = member();
        long recId = v1(user, "MAGNESIUM", "VITAMIN_B6");

        JsonNode data = recent(user);

        assertThat(data.path("exists").asBoolean()).isTrue();
        assertThat(data.path("source").asString()).isEqualTo("V1_SURVEY");
        assertThat(data.path("recommendationId").asLong()).isEqualTo(recId);
        assertThat(data.has("chatSessionId")).isFalse();
        assertThat(data.path("notice").asString()).isEqualTo(ReviewProductResponse.MEMBER_NOTICE);
        assertThat(codes(data)).containsExactly("MAGNESIUM", "VITAMIN_B6");
        JsonNode magnesium = card(data, "MAGNESIUM");
        assertThat(magnesium.path("reason").asString()).isEqualTo("가짜 응답");
        assertThat(magnesium.path("products").size()).isEqualTo(3);
        // V3 제품 API와 같은 결과 (같은 서비스·안전 필터)
        assertThat(magnesium.path("products")).isEqualTo(v3Products(user, recId, "MAGNESIUM"));
        assertThat(card(data, "VITAMIN_B6").path("products")).isEqualTo(v3Products(user, recId, "VITAMIN_B6"));
    }

    @Test
    void V1_다음_V2면_V2가_나오고_기존_latest는_그대로_V1() throws Exception {
        User user = member();
        long v1 = v1(user, "MAGNESIUM");
        setRecommendationTime(v1, T0);
        long v2 = v2(user, "OMEGA3");
        setRecommendationTime(v2, T0.plusHours(1));

        JsonNode data = recent(user);

        assertThat(data.path("source").asString()).isEqualTo("V2_PERSONALIZED");
        assertThat(data.path("recommendationId").asLong()).isEqualTo(v2);
        assertThat(data.path("recommendedAt").asString()).isEqualTo("2026-10-01T10:00:00");
        assertThat(codes(data)).containsExactly("OMEGA3");
        assertThat(card(data, "OMEGA3").path("products")).isEqualTo(v3Products(user, v2, "OMEGA3"));
        // 기존 /latest는 V1 설문 추천만 보는 동작 그대로
        mockMvc.perform(get("/api/v1/users/me/recommendations/latest").header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendation.recommendationId").value(v1));
    }

    @Test
    void V2_다음_V4_트랙1이면_저장된_카드_스냅샷_그대로() throws Exception {
        User user = member();
        long v2 = v2(user, "OMEGA3");
        setRecommendationTime(v2, T0);
        List<ChatCard> cards = List.of(
                new ChatCard("MAGNESIUM", "비슷한 고민에 약사들이 추천한 성분이에요", 1, BigDecimal.valueOf(100), "mg",
                        null, "UNKNOWN", null, List.of(), List.of(new ReviewProductResponse.Product(9301, "스냅샷 제품",
                        "NOW", null, null, List.of(), List.of(), List.of()))),
                new ChatCard("VITAMIN_D", "비슷한 고민에 약사들이 추천한 성분이에요", 2, BigDecimal.TEN, "mcg",
                        null, "UNKNOWN", null, List.of(), List.of()));
        AiChatSession chat = chatSession(user, null);
        reviewResult(chat, cards, T0.plusHours(1));

        JsonNode data = recent(user);

        assertThat(data.path("source").asString()).isEqualTo("V4_CHAT");
        assertThat(data.has("recommendationId")).isFalse();
        assertThat(data.path("chatSessionId").asLong()).isEqualTo(chat.getChatSessionId());
        // 다시 계산하지 않는다: 스냅샷의 제품 이름이 그대로 나온다 (실제 9301 이름과 다름)
        assertThat(data.path("ingredients").toString()).isEqualTo(objectMapper.writeValueAsString(cards));
        assertThat(card(data, "MAGNESIUM").path("products").get(0).path("productName").asString()).isEqualTo("스냅샷 제품");
    }

    @Test
    void V4_트랙2는_ai_recommendations_기준_1건만() throws Exception {
        User user = member();
        AiChatSession chat = chatSession(user, null);
        long recId = chatRecommendation(chat, "VITAMIN_D");
        setRecommendationTime(recId, T0);
        // 같은 세션의 트랙 2 스냅샷(RULE_BASED)이 더 늦게 저장돼도 따로 세지 않는다
        AiChatResult snapshot = chatResultRepository.save(AiChatResult.builder().chatSession(chat)
                .track(AiChatResult.RULE_BASED).recommendationId(recId).build());
        setChatResultTime(snapshot.getChatResultId(), T0.plusHours(1));

        JsonNode data = recent(user);

        assertThat(data.path("source").asString()).isEqualTo("V4_CHAT");
        assertThat(data.path("recommendationId").asLong()).isEqualTo(recId);
        assertThat(data.path("chatSessionId").asLong()).isEqualTo(chat.getChatSessionId());
        assertThat(codes(data)).containsExactly("VITAMIN_D");
    }

    @Test
    void 최신_추천이_PENDING이나_FAILED면_그_전_DONE_추천() throws Exception {
        User user = member();
        long done = v2(user, "OMEGA3");
        setRecommendationTime(done, T0);
        AiQuestionSession session = questionSessionRepository.save(AiQuestionSession.start(user));
        AiRecommendation pending = recommendationRepository.save(AiRecommendation.builder().session(session)
                .status(AiRecommendation.PENDING).build());
        AiRecommendation failed = recommendationRepository.save(AiRecommendation.builder().session(session)
                .status(AiRecommendation.PENDING).build());
        failed.markFailed("LLM_TIMEOUT");
        setRecommendationTime(pending.getRecommendationId(), T0.plusHours(1));
        setRecommendationTime(failed.getRecommendationId(), T0.plusHours(2));

        JsonNode data = recent(user);

        assertThat(data.path("recommendationId").asLong()).isEqualTo(done);
    }

    @Test
    void 다른_회원의_추천이_더_최근이어도_내_것만() throws Exception {
        User me = member();
        User other = member();
        long mine = v2(me, "OMEGA3");
        setRecommendationTime(mine, T0);
        long others = v2(other, "MAGNESIUM");
        setRecommendationTime(others, T0.plusHours(1));
        reviewResult(chatSession(other, null), List.of(), T0.plusHours(2));

        JsonNode data = recent(me);

        assertThat(data.path("recommendationId").asLong()).isEqualTo(mine);
        assertThat(codes(data)).containsExactly("OMEGA3");
    }

    @Test
    void 비회원은_401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header("X-Guest-Token", "some-guest-token")).andExpect(status().isUnauthorized());
    }

    @Test
    void 대화에서만_와파린을_말한_트랙2는_V3와_같이_안전_필터를_거친다() throws Exception {
        User user = noMedicationMember();
        AiChatSession chat = chatSession(user, "[{\"drugId\":\"D-007\",\"drugClass\":\"WARFARIN\",\"name\":\"와파린\"}]");
        long recId = chatRecommendation(chat, "VITAMIN_D", "OMEGA3");

        JsonNode data = recent(user);

        // 프로필에는 약이 없다. 대화에서 말한 와파린으로 D3+K2(9311)가 빠지고 D3만(9312) 남는다
        assertThat(ids(card(data, "VITAMIN_D"))).containsExactly(9312L);
        assertThat(card(data, "OMEGA3").path("products").toString()).contains("와파린");
        assertThat(card(data, "VITAMIN_D").path("products")).isEqualTo(v3Products(user, recId, "VITAMIN_D"));
        assertThat(card(data, "OMEGA3").path("products")).isEqualTo(v3Products(user, recId, "OMEGA3"));
    }

    @Test
    void 회차_대화_90일_삭제_뒤에는_V2_트랙2_트랙1이_안_보이고_남은_V1이_나온다() throws Exception {
        User user = member();
        long v1 = v1(user, "MAGNESIUM");
        setRecommendationTime(v1, T0);
        long v2 = v2(user, "OMEGA3");
        setRecommendationTime(v2, T0.plusHours(1));
        AiChatSession chat2 = chatSession(user, null);
        long track2 = chatRecommendation(chat2, "VITAMIN_D");
        setRecommendationTime(track2, T0.plusHours(2));
        AiChatSession chat1 = chatSession(user, null);
        reviewResult(chat1, List.of(), T0.plusHours(3));
        entityManager.flush();
        jdbcTemplate.update("update ai_question_sessions set started_at = DATEADD('DAY', -91, started_at) where user_id = ?",
                user.getId());
        jdbcTemplate.update("update ai_chat_sessions set started_at = DATEADD('DAY', -91, started_at) where user_id = ?",
                user.getId());

        cleanupScheduler.purge(LocalDateTime.now());
        entityManager.clear();

        // V2·트랙 2 추천 행은 남지만 회원을 찾을 FK가 비어 빠지고, 트랙 1 결과 행(V7.1부터 남음, chat_session_id NULL)도
        // /recent는 세션으로 회원을 찾아 빠진다 (D13: /recent 동작은 그대로. 90일 뒤 기록은 V7.1 /history에서 보인다)
        assertThat(recommendationRepository.findById(v2)).isPresent();
        assertThat(recommendationRepository.findById(track2)).isPresent();
        JsonNode data = recent(user);
        assertThat(data.path("source").asString()).isEqualTo("V1_SURVEY");
        assertThat(data.path("recommendationId").asLong()).isEqualTo(v1);
    }

    @Test
    void 성분_하나의_제품_조회가_실패해도_그_성분만_빈_목록이고_응답은_성공() throws Exception {
        User user = member();
        // 성분 코드가 아닌 값이 저장된 경우: V3 서비스가 INVALID_INPUT 예외를 던진다
        long recId = v2(user, "NOT_AN_INGREDIENT", "OMEGA3");

        JsonNode data = recent(user);

        assertThat(data.path("exists").asBoolean()).isTrue();
        assertThat(codes(data)).containsExactly("NOT_AN_INGREDIENT", "OMEGA3");
        assertThat(card(data, "NOT_AN_INGREDIENT").path("products").isArray()).isTrue();
        assertThat(card(data, "NOT_AN_INGREDIENT").path("products").size()).isZero();
        assertThat(card(data, "OMEGA3").path("products")).isEqualTo(v3Products(user, recId, "OMEGA3"));
        assertThat(card(data, "OMEGA3").path("products").size()).isPositive();
    }

    // ---------- 도우미 ----------

    // /recent 호출 + LLM·임베딩 0회 확인
    JsonNode recent(User user) throws Exception {
        entityManager.flush();
        entityManager.clear();
        fake.reset();
        fakeEmbedding.reset();
        String body = mockMvc.perform(get(URL).header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
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

    // 프로필에 질환·약이 없다고 설문에서 답한 회원 (ChatRecommendationApiTest와 같은 준비)
    User noMedicationMember() throws Exception {
        User user = members.create(true, Map.of("gender", "M", "birth_year", 1980), List.of(), List.of());
        mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(user.getId()))
                        .content(objectMapper.writeValueAsString(Map.of("answers", List.of(
                                choice(Q_CONDITIONS, CONDITION_NONE), text(Q_MEDICATIONS, "[]"))))))
                .andExpect(status().isOk());
        return user;
    }

    // V1: 설문 제출 → 추천 생성 (Fake LLM)
    long v1(User user, String... ingredients) throws Exception {
        fake.answerWith(new FakeAnswer(Arrays.stream(ingredients)
                .map(c -> new FakeAnswer.Item(c, null, "가짜 응답", BigDecimal.ONE, null)).toList(), "가짜 응답입니다"));
        String submitted = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON).header("Authorization", bearer(user.getId()))
                        .content(objectMapper.writeValueAsString(Map.of("answers",
                                List.of(choice(Q_GENDER, GENDER_F), number(Q_BIRTH, 1995))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long responseId = objectMapper.readTree(submitted).path("data").path("responseId").asLong();
        String body = mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(user.getId())).content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("recommendationId").asLong();
    }

    // V2: 맞춤 질문 회차 + DONE 추천
    long v2(User user, String... ingredients) {
        AiQuestionSession session = questionSessionRepository.save(AiQuestionSession.start(user));
        AiRecommendation rec = recommendationRepository.save(AiRecommendation.builder().session(session)
                .summary("가짜 정밀 추천").modelName("fake").status(AiRecommendation.DONE).build());
        items(rec, ingredients);
        return rec.getRecommendationId();
    }

    AiChatSession chatSession(User user, String extraMedications) {
        AiChatSession chat = AiChatSession.start(user);
        if (extraMedications != null) {
            chat.markSearching(extraMedications, "[]");
        }
        return chatSessionRepository.save(chat);
    }

    // V4 트랙 2: 대화 세션으로 DONE 추천
    long chatRecommendation(AiChatSession chat, String... ingredients) {
        AiRecommendation rec = recommendationRepository.save(AiRecommendation.builder().chatSession(chat)
                .summary("가짜 대화 추천").modelName("fake").status(AiRecommendation.DONE).build());
        items(rec, ingredients);
        return rec.getRecommendationId();
    }

    // V4 트랙 1: 카드 스냅샷 (ChatRecommendationService와 같은 JSON 형식)
    void reviewResult(AiChatSession chat, List<ChatCard> cards, LocalDateTime at) {
        String json = objectMapper.writeValueAsString(Map.of("cards", cards, "excludedItems", List.of(),
                "excludedProductCount", 0, "medicationNotice", ""));
        AiChatResult result = chatResultRepository.save(AiChatResult.builder().chatSession(chat)
                .track(AiChatResult.REVIEW).cards(json).build());
        setChatResultTime(result.getChatResultId(), at);
    }

    void items(AiRecommendation rec, String... ingredients) {
        for (int i = 0; i < ingredients.length; i++) {
            itemRepository.save(AiRecommendationItem.builder().recommendation(rec).ingredientName(ingredients[i])
                    .reason("가짜 응답").sortOrder(i + 1).build());
        }
    }

    void setRecommendationTime(long recId, LocalDateTime at) {
        entityManager.flush();
        jdbcTemplate.update("update ai_recommendations set created_at = ?, finished_at = ? where recommendation_id = ?",
                Timestamp.valueOf(at.minusSeconds(5)), Timestamp.valueOf(at), recId);
        entityManager.clear();
    }

    void setChatResultTime(long chatResultId, LocalDateTime at) {
        entityManager.flush();
        jdbcTemplate.update("update ai_chat_results set created_at = ? where chat_result_id = ?",
                Timestamp.valueOf(at), chatResultId);
        entityManager.clear();
    }

    static List<String> codes(JsonNode data) {
        List<String> out = new ArrayList<>();
        data.path("ingredients").forEach(c -> out.add(c.path("ingredient").asString()));
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
