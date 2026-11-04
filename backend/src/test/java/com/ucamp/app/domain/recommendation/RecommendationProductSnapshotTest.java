package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.chat.config.ChatProperties;
import com.ucamp.app.domain.chat.vector.InMemoryReviewVectorIndex;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationItemRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.recommendation.service.RecommendationProductSnapshotService;
import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.domain.review.service.ReviewProductRecommendationService;
import com.ucamp.app.domain.chat.entity.AiChatSession;
import com.ucamp.app.domain.chat.repository.AiChatSessionRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import com.ucamp.app.infra.llm.LlmUnavailableException;
import com.ucamp.app.infra.llm.embed.FakeEmbeddingClient;
import com.ucamp.app.support.TestMembers;
import com.ucamp.app.domain.review.repository.ReviewProductQueryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyAnswers.number;
import static com.ucamp.app.support.SurveyFixture.GENDER_F;
import static com.ucamp.app.support.SurveyFixture.Q_BIRTH;
import static com.ucamp.app.support.SurveyFixture.Q_GENDER;
import static com.ucamp.app.support.SurveyFixture.Q_SUN;
import static com.ucamp.app.support.SurveyFixture.SUN_LOW;
import static com.ucamp.app.support.SurveyFixture.SURVEY_ID;
import static com.ucamp.app.support.TestJwt.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V7.1 제품 순위 스냅샷: DONE 경로마다 "커밋 뒤 실제로 ai_recommendation_products에 저장되는지".
 * AFTER_COMMIT 리스너는 테스트 트랜잭션(롤백)에서는 돌지 않으므로 이 클래스는 @Transactional을 걸지 않고 실제로 커밋한다.
 * 그래서 리뷰 제품 시드를 매번 넣고, 만든 행(추천·설문·대화·회차·시드)을 테스트마다 직접 지운다 (같은 H2 DB를 다른 테스트와 공유).
 * 저장된 순위는 같은 시점의 V3 서비스 결과(recommend)와 같아야 한다. LLM은 Fake, 스냅샷 중 LLM·임베딩 추가 호출 없음
 */
@SpringBootTest(properties = {"chat.fallback.async=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@Import(TestMembers.class)
class RecommendationProductSnapshotTest {
    static final String PQ = "/api/v1/personalized-sessions";

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
    InMemoryReviewVectorIndex index;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    NamedParameterJdbcTemplate namedJdbc;
    @Autowired
    DataSource dataSource;
    @Autowired
    ReviewProductRecommendationService productService;
    @Autowired
    RecommendationProductSnapshotService snapshotService;
    @Autowired
    AiRecommendationRepository recommendationRepository;
    @Autowired
    AiRecommendationItemRepository itemRepository;
    @Autowired
    AiChatSessionRepository chatSessionRepository;
    @Autowired
    ChatProperties chatProperties;
    @Autowired
    MeterRegistry meterRegistry;
    @Autowired
    EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean
    ReviewProductQueryRepository reviewQueryRepository;

    final List<Long> userIds = new ArrayList<>();
    final List<Long> recIds = new ArrayList<>();
    final List<Long> guestResponseIds = new ArrayList<>();

    @BeforeEach
    void seed() {
        deleteSeed();
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("sql/schema-review-products.sql"),
                new ClassPathResource("sql/seed-review-products.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
        index.reload();
        fake.reset();
        fakeEmbedding.reset();
    }

    @AfterEach
    void cleanUp() {
        fake.reset();
        fakeEmbedding.reset();
        chatProperties.getFallback().setAsync(false);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("users", userIds.isEmpty() ? List.of(-1L) : userIds)
                .addValue("recs", recIds.isEmpty() ? List.of(-1L) : recIds)
                .addValue("guests", guestResponseIds.isEmpty() ? List.of(-1L) : guestResponseIds);
        String recs = "select recommendation_id from ai_recommendations where user_id in (:users) "
                + "or recommendation_id in (:recs) or response_id in (:guests)";
        namedJdbc.update("delete from ai_recommendation_products where recommendation_item_id in "
                + "(select recommendation_item_id from ai_recommendation_items where recommendation_id in (" + recs + "))", p);
        namedJdbc.update("delete from ai_recommendation_items where recommendation_id in (" + recs + ")", p);
        namedJdbc.update("delete from ai_chat_results where user_id in (:users)", p);
        namedJdbc.update("delete from ai_recommendations where recommendation_id in (" + recs + ")", p);
        namedJdbc.update("delete from ai_chat_sessions where user_id in (:users)", p);
        namedJdbc.update("delete from ai_question_sessions where user_id in (:users)", p);
        namedJdbc.update("delete from survey_answers where response_id in "
                + "(select response_id from survey_responses where user_id in (:users) or response_id in (:guests))", p);
        namedJdbc.update("delete from survey_responses where user_id in (:users) or response_id in (:guests)", p);
        deleteSeed();
        index.reload();
    }

    void deleteSeed() {
        jdbcTemplate.update("delete from ai_recommendation_products where catalog_product_id between 9300 and 9399");
        for (String sql : List.of("delete from review_product_scores where catalog_product_id between 9300 and 9399",
                "delete from product_ingredients where catalog_product_id between 9300 and 9399",
                "delete from review_mentions where video_id like 'TESTVID%' or catalog_product_id between 9300 and 9399")) {
            try {
                jdbcTemplate.update(sql);
            } catch (RuntimeException e) {
                // 테이블이 아직 없음 (schema-review-products.sql 실행 전)
            }
        }
        jdbcTemplate.update("delete from catalog_products where catalog_product_id between 9300 and 9399");
    }

    // ---------- 경로별 DONE → 제품 순위 저장 ----------

    @Test
    void V1_회원_설문_추천은_DONE_뒤_성분별_제품_순위가_V3_결과_순서대로_저장된다() throws Exception {
        User user = member();
        fake.answerWith(answer("MAGNESIUM", "VITAMIN_D"));

        long recId = v1Member(user);

        assertThat(userIdOf(recId)).isEqualTo(user.getId());
        assertSnapshotEqualsV3(recId, "MAGNESIUM", "VITAMIN_D");
        assertThat(saved(recId, "MAGNESIUM")).isNotEmpty();
    }

    @Test
    void V1_RULE_ONLY_폴백도_DONE이면_저장된다() throws Exception {
        User user = member();
        fake.setResponder(req -> {
            throw new LlmUnavailableException(LlmUnavailableException.Reason.CIRCUIT_OPEN, null);
        });

        long recId = v1Member(user);

        assertThat(jdbcTemplate.queryForObject("select model_name from ai_recommendations where recommendation_id = ?",
                String.class, recId)).isEqualTo("RULE_ONLY");
        assertThat(userIdOf(recId)).isEqualTo(user.getId());
        assertSnapshotEqualsV3(recId, "VITAMIN_D");
        assertThat(saved(recId, "VITAMIN_D")).isNotEmpty();
    }

    @Test
    void 비회원_V1_추천은_user_id가_없고_제품을_저장하지_않는다() throws Exception {
        fake.answerWith(answer("MAGNESIUM", "VITAMIN_D"));
        String body = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answers", surveyAnswers()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        long responseId = data.path("responseId").asLong();
        guestResponseIds.add(responseId);
        String rec = mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Guest-Token", data.path("guestToken").asString())
                        .content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long recId = objectMapper.readTree(rec).path("data").path("recommendationId").asLong();
        recIds.add(recId);

        assertThat(userIdOf(recId)).isNull();
        assertThat(countProducts(recId)).isZero();
    }

    @Test
    void V2_정밀_추천은_바깥_트랜잭션_커밋_뒤에_저장된다() throws Exception {
        User user = members.fullMale(1980);
        userIds.add(user.getId());
        long sessionId = data(mockMvc.perform(post(PQ).header("Authorization", bearer(user.getId()))))
                .path("sessionId").asLong();
        stop(user, sessionId);
        fake.answerWith(answer("VITAMIN_D", "MAGNESIUM"));

        long recId = data(mockMvc.perform(post(PQ + "/" + sessionId + "/recommendation")
                .header("Authorization", bearer(user.getId()))).andExpect(status().isOk()))
                .path("recommendationId").asLong();
        recIds.add(recId);

        assertThat(userIdOf(recId)).isEqualTo(user.getId());
        assertSnapshotEqualsV3(recId, "VITAMIN_D", "MAGNESIUM");
        assertThat(countProducts(recId)).isPositive();
    }

    @Test
    void V4_트랙2_요청_스레드_실행도_DONE_뒤_저장된다() throws Exception {
        User user = members.fullMale(1980);
        userIds.add(user.getId());
        long recId = chatTrack2(user);

        assertThat(jdbcTemplate.queryForObject("select status from ai_recommendations where recommendation_id = ?",
                String.class, recId)).isEqualTo("DONE");
        assertThat(userIdOf(recId)).isEqualTo(user.getId());
        assertThat(jdbcTemplate.queryForObject("select user_id from ai_chat_results where recommendation_id = ?",
                Long.class, recId)).isEqualTo(user.getId());
        assertSnapshotEqualsV3(recId, "VITAMIN_D");
        assertThat(saved(recId, "VITAMIN_D")).isNotEmpty();
    }

    @Test
    void V4_트랙2_백그라운드_실행기에서도_DONE_뒤_저장된다() throws Exception {
        User user = members.fullMale(1980);
        userIds.add(user.getId());
        chatProperties.getFallback().setAsync(true);

        long recId = chatTrack2(user);
        long deadline = System.currentTimeMillis() + 10_000;
        while (countProducts(recId) == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertThat(jdbcTemplate.queryForObject("select status from ai_recommendations where recommendation_id = ?",
                String.class, recId)).isEqualTo("DONE");
        assertSnapshotEqualsV3(recId, "VITAMIN_D");
        assertThat(saved(recId, "VITAMIN_D")).isNotEmpty();
    }

    // ---------- 실패 격리·중복 방지 ----------

    @Test
    void 성분_하나의_제품_계산이_실패해도_추천은_DONE이고_다른_성분은_저장된다() {
        User user = member();
        AiRecommendation rec = recommendationRepository.save(AiRecommendation.builder()
                .chatSession(chatSessionRepository.save(AiChatSession.start(user))).user(user)
                .summary("가짜").modelName("fake").status(AiRecommendation.DONE).build());
        recIds.add(rec.getRecommendationId());
        // 성분 코드가 아닌 값: V3 서비스가 INVALID_INPUT을 던진다
        items(rec, "NOT_AN_INGREDIENT", "VITAMIN_D");
        double failBefore = counter("fail");

        int saved = snapshotService.snapshot(rec.getRecommendationId());

        assertThat(saved).isEqualTo(1);
        assertThat(counter("fail")).isEqualTo(failBefore + 1);
        assertThat(saved(rec.getRecommendationId(), "NOT_AN_INGREDIENT")).isEmpty();
        assertSnapshotEqualsV3(rec.getRecommendationId(), "VITAMIN_D");
        assertThat(jdbcTemplate.queryForObject("select status from ai_recommendations where recommendation_id = ?",
                String.class, rec.getRecommendationId())).isEqualTo("DONE");

        // 이미 저장된 추천은 다시 저장하지 않는다
        long rows = countProducts(rec.getRecommendationId());
        assertThat(snapshotService.snapshot(rec.getRecommendationId())).isZero();
        assertThat(countProducts(rec.getRecommendationId())).isEqualTo(rows);
    }

    @Test
    void 저장된_경고와_같이_든_성분은_V3_결과와_같다() throws Exception {
        User user = member();
        fake.answerWith(answer("MAGNESIUM", "VITAMIN_B6"));

        long recId = v1Member(user);

        List<ReviewProductResponse.Product> v3 = productService.recommend(recId, "MAGNESIUM").products();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select cast(p.also_contains as varchar) as also_contains, cast(p.warnings as varchar) as warnings "
                + "from ai_recommendation_products p join ai_recommendation_items i "
                + "on i.recommendation_item_id = p.recommendation_item_id "
                + "where i.recommendation_id = ? and i.ingredient_name = 'MAGNESIUM' order by p.product_rank", recId);
        assertThat(rows).hasSize(v3.size());
        for (int i = 0; i < v3.size(); i++) {
            assertThat(strings(rows.get(i).get("ALSO_CONTAINS"))).isEqualTo(v3.get(i).alsoContains());
            assertThat(strings(rows.get(i).get("WARNINGS"))).isEqualTo(v3.get(i).warnings());
        }
        // 9306(MAGNESIUM + VITAMIN_B6 복합)은 같이 든 성분이 있다
        assertThat(rows.toString()).contains("VITAMIN_B6");
    }

    @Test
    void DONE_시_추가되는_쿼리_수와_시간() {
        User user = member();
        long one = memberRecommendation(user, "MAGNESIUM");
        long three = memberRecommendation(user, "MAGNESIUM", "OMEGA3", "VITAMIN_D");
        measure(one); // 첫 실행 준비(클래스 로딩 등)를 측정에서 빼려고 한 번 돌리고 지운다
        jdbcTemplate.update("delete from ai_recommendation_products");

        long[] single = measure(one);
        long[] triple = measure(three);

        System.out.printf("[V7.1] snapshot on DONE: 1 ingredient=%d queries %dms, 3 ingredients=%d queries %dms%n",
                single[0], single[1], triple[0], triple[1]);
        assertThat(triple[0]).isGreaterThan(single[0]);
        assertThat(fake.requests()).isEmpty();
        assertThat(fakeEmbedding.requests()).isEmpty();
    }

    long memberRecommendation(User user, String... ingredients) {
        AiRecommendation rec = recommendationRepository.save(AiRecommendation.builder()
                .chatSession(chatSessionRepository.save(AiChatSession.start(user))).user(user)
                .summary("가짜").modelName("fake").status(AiRecommendation.DONE).build());
        recIds.add(rec.getRecommendationId());
        items(rec, ingredients);
        return rec.getRecommendationId();
    }

    // [쿼리 수(JPA 준비 SQL + 리뷰 JdbcTemplate 조회), 걸린 시간 ms]
    long[] measure(long recId) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        clearInvocations(reviewQueryRepository);
        fake.reset();
        fakeEmbedding.reset();
        long start = System.nanoTime();
        snapshotService.snapshot(recId);
        long ms = (System.nanoTime() - start) / 1_000_000;
        long jdbc = mockingDetails(reviewQueryRepository).getInvocations().stream()
                .filter(i -> i.getMethod().getName().startsWith("find")).count();
        return new long[]{stats.getPrepareStatementCount() + jdbc, ms};
    }

    // ---------- 도우미 ----------

    User member() {
        User user = members.create(true, Map.of("gender", "F", "birth_year", 1995), List.of(), List.of());
        userIds.add(user.getId());
        return user;
    }

    static FakeAnswer answer(String... codes) {
        return new FakeAnswer(Arrays.stream(codes)
                .map(c -> new FakeAnswer.Item(c, null, "가짜 응답", BigDecimal.ONE, null)).toList(), "가짜 응답입니다");
    }

    static List<Map<String, Object>> surveyAnswers() {
        return List.of(choice(Q_GENDER, GENDER_F), number(Q_BIRTH, 1995), choice(Q_SUN, SUN_LOW));
    }

    long v1Member(User user) throws Exception {
        String submitted = mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses")
                        .contentType(MediaType.APPLICATION_JSON).header("Authorization", bearer(user.getId()))
                        .content(objectMapper.writeValueAsString(Map.of("answers", surveyAnswers()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long responseId = objectMapper.readTree(submitted).path("data").path("responseId").asLong();
        String body = mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(user.getId())).content("{\"responseId\":" + responseId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long recId = objectMapper.readTree(body).path("data").path("recommendationId").asLong();
        recIds.add(recId);
        return recId;
    }

    // 대화창: 해시 벡터라 비슷한 리뷰가 없음 → 트랙 2 (ChatRecommendationApiTest와 같은 준비)
    long chatTrack2(User user) throws Exception {
        fake.setResponder(req -> {
            if (req.outputSchema() != null && FakeLlmClient.CHAT_PARSE_SCHEMA.equals(req.outputSchema().name())) {
                ObjectNode node = (ObjectNode) fake.chatParseOutput(FakeLlmClient.DEFAULT_CHAT_QUERY);
                return fake.structuredResponse(req, node);
            }
            return fake.render(req, answer("VITAMIN_D"));
        });
        long chatId = data(mockMvc.perform(post("/api/v1/chat/sessions").header("Authorization", bearer(user.getId()))))
                .path("chatSessionId").asLong();
        JsonNode data = data(mockMvc.perform(post("/api/v1/chat/sessions/" + chatId + "/messages")
                .header("Authorization", bearer(user.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", "요즘 기운이 없어요")))).andExpect(status().isOk()));
        assertThat(data.path("track").asString()).isEqualTo("RULE_BASED");
        long recId = data.path("recommendationId").asLong();
        recIds.add(recId);
        return recId;
    }

    // 결과 보기 조건을 채우도록 남은 질문에 첫 선택지로 답한 뒤 그만한다 (PersonalizedRecommendationApiTest와 같은 방식)
    void stop(User user, long sessionId) throws Exception {
        while (jdbcTemplate.queryForObject("select count(*) from ai_question_turns where session_id = ?"
                + " and fixed = false and selected_json is not null", Integer.class, sessionId) == 0) {
            long turnId = jdbcTemplate.queryForObject("select turn_id from ai_question_turns where session_id = ?"
                    + " and selected_json is null", Long.class, sessionId);
            mockMvc.perform(post(PQ + "/" + sessionId + "/answers").header("Authorization", bearer(user.getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("turnId", turnId, "selected", List.of(0)))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post(PQ + "/" + sessionId + "/stop").header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk());
    }

    void items(AiRecommendation rec, String... ingredients) {
        for (int i = 0; i < ingredients.length; i++) {
            itemRepository.save(AiRecommendationItem.builder().recommendation(rec).ingredientName(ingredients[i])
                    .reason("가짜 응답").sortOrder(i + 1).build());
        }
    }

    JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }

    Long userIdOf(long recId) {
        return jdbcTemplate.queryForObject("select user_id from ai_recommendations where recommendation_id = ?",
                Long.class, recId);
    }

    long countProducts(long recId) {
        return jdbcTemplate.queryForObject("select count(*) from ai_recommendation_products p "
                + "join ai_recommendation_items i on i.recommendation_item_id = p.recommendation_item_id "
                + "where i.recommendation_id = ?", Long.class, recId);
    }

    List<Long> saved(long recId, String ingredient) {
        return jdbcTemplate.queryForList("select p.catalog_product_id from ai_recommendation_products p "
                + "join ai_recommendation_items i on i.recommendation_item_id = p.recommendation_item_id "
                + "where i.recommendation_id = ? and i.ingredient_name = ? order by p.product_rank", Long.class,
                recId, ingredient);
    }

    void assertSnapshotEqualsV3(long recId, String... ingredients) {
        for (String code : ingredients) {
            List<Long> expected = productService.recommend(recId, code).products().stream()
                    .map(ReviewProductResponse.Product::catalogProductId).toList();
            assertThat(saved(recId, code)).as(code).containsExactlyElementsOf(expected);
        }
    }

    List<String> strings(Object json) {
        return objectMapper.readValue(json.toString(), new tools.jackson.core.type.TypeReference<List<String>>() {
        });
    }

    double counter(String outcome) {
        var c = meterRegistry.find(RecommendationProductSnapshotService.METRIC).tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }
}
