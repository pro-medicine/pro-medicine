package com.ucamp.app.domain.chat;

import com.ucamp.app.domain.chat.entity.AiChatSession;
import com.ucamp.app.domain.chat.repository.AiChatSessionRepository;
import com.ucamp.app.domain.chat.service.ChatRecommendationService;
import com.ucamp.app.domain.chat.vector.InMemoryReviewVectorIndex;
import com.ucamp.app.domain.personalized.service.PersonalizedSessionCleanupScheduler;
import com.ucamp.app.domain.recommendation.cache.CacheKeyFactory;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.embed.FakeEmbeddingClient;
import com.ucamp.app.support.TestMembers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyAnswers.text;
import static com.ucamp.app.support.SurveyFixture.CONDITION_NONE;
import static com.ucamp.app.support.SurveyFixture.Q_CONDITIONS;
import static com.ucamp.app.support.SurveyFixture.Q_MEDICATIONS;
import static com.ucamp.app.support.SurveyFixture.SURVEY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V4 대화창 API (설계 §9). LLM은 FakeLlmClient(문장별 구조화 응답), 검색 문장 임베딩은 FakeEmbeddingClient에
 * 실제 리뷰 언급 벡터(seed-chat-mentions.sql)를 넣어 실제 코사인 값으로 트랙 1을 돈다. 트랙 2는 요청 스레드에서 바로 실행
 */
@SpringBootTest(properties = "chat.fallback.async=false")
@AutoConfigureMockMvc
@Import(TestMembers.class)
@Transactional
@Sql(scripts = "/sql/schema-review-products.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql({"/sql/seed-chat-mentions.sql", "/sql/seed-review-products.sql"})
class ChatRecommendationApiTest {
    static final String QUERY = "피로가 쌓이고 잠들기 어려운 사람에게 추천";

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
    AiChatSessionRepository chatSessionRepository;
    @Autowired
    AiRecommendationRepository recommendationRepository;
    @Autowired
    CacheKeyFactory cacheKeyFactory;
    @Autowired
    PersonalizedSessionCleanupScheduler cleanupScheduler;

    @BeforeEach
    void loadIndex() {
        index.reload(); // @Sql 데이터는 테스트 트랜잭션 안에 있다
    }

    @AfterEach
    void reset() {
        fake.reset();
        fakeEmbedding.reset();
    }

    // ---------- 트랙 1 ----------

    @Test
    void 인덱스는_RESOLVED_추천_비추천_언급만_올린다() {
        assertThat(index.size()).isEqualTo(10);
    }

    @Test
    void 비슷한_리뷰가_있으면_트랙1_성분_카드와_제품() throws Exception {
        User user = members.fullMale(1980);
        parseAs(Map.of());
        fakeEmbedding.setVector(QUERY, vector(9401));

        JsonNode data = send(user, start(user), "요즘 야근이 많아서 피곤하고 잠을 잘 못 자요");

        assertThat(data.path("type").asString()).isEqualTo("RESULT");
        assertThat(data.path("track").asString()).isEqualTo("REVIEW");
        assertThat(data.path("message").asString()).isEqualTo("비슷한 고민에 약사들이 추천한 성분과 제품이에요");
        JsonNode card = data.path("cards").get(0);
        assertThat(card.path("ingredient").asString()).isEqualTo("MAGNESIUM");
        assertThat(card.path("reason").asString()).isEqualTo("비슷한 고민에 약사들이 추천한 성분이에요");
        assertThat(card.path("amount").isNumber()).isTrue(); // V1 카드와 같은 용량 계산
        JsonNode products = card.path("products");
        assertThat(products.size()).isBetween(1, 3);
        assertThat(products.get(0).path("catalogProductId").asLong()).isEqualTo(9401); // sim 1.0
        // 비추천만 받은 9404(Bluebonnet)는 후보가 아니다
        assertThat(products.toString()).doesNotContain("9404");
        // 약사·채널·리뷰 문장은 내보내지 않는다
        assertThat(data.toString()).doesNotContain("UC3iSLVH0MxHfwO69oHKpvog").doesNotContain("흡수가 좋고");
        assertThat(cardCount(data)).isLessThanOrEqualTo(3);
        assertThat(fakeEmbedding.requests()).containsExactly(QUERY);
    }

    @Test
    void 대화에서_와파린을_말하면_오메가3_카드_경고_OTHER_성분_제품_제외() throws Exception {
        User user = noMedicationMember();
        parseAs(Map.of("medications", List.of(Map.of("name", "와파린", "drug_class", "WARFARIN"))));
        fakeEmbedding.setVector(QUERY, vector(9406));

        JsonNode data = send(user, start(user), "와파린 먹고 있는데 혈관 건강이 걱정돼요");

        JsonNode omega = card(data, "OMEGA3");
        assertThat(omega.path("warnings").toString()).contains("와파린");
        // 9406·9407은 라벨에 OTHER 성분 → 복용 약이 있으면 제외. 라벨이 OMEGA3뿐인 9409만 남는다
        assertThat(ids(omega)).containsExactly(9409L);
        assertThat(data.path("excludedProductCount").asInt()).isGreaterThanOrEqualTo(2);
        // 대화 속 약은 세션에만 저장, 프로필은 그대로
        AiChatSession s = chatSessionRepository.findById(data.path("chatSessionId").asLong()).orElseThrow();
        assertThat(s.getExtraMedications()).contains("D-007");
        assertThat(jdbcTemplate.queryForObject("select count(*) from user_medications where user_id = ?",
                Integer.class, user.getId())).isZero();
    }

    @Test
    void 피_묽게_하는_약은_계열로_D_U05에_매칭되어_같은_경고() throws Exception {
        User user = noMedicationMember();
        parseAs(Map.of("medications", List.of(Map.of("name", "피 묽게 하는 약", "drug_class", "ANTICOAGULANT_UNKNOWN"))));
        fakeEmbedding.setVector(QUERY, vector(9406));

        JsonNode data = send(user, start(user), "피 묽게 하는 약 먹어요. 혈관 건강이 걱정돼요");

        assertThat(card(data, "OMEGA3").path("warnings").toString()).contains("항응고제");
        assertThat(data.has("medicationNotice")).isFalse();
        AiChatSession s = chatSessionRepository.findById(data.path("chatSessionId").asLong()).orElseThrow();
        assertThat(s.getExtraMedications()).contains("D-U05");
    }

    @Test
    void 매칭되지_않는_약은_복용_약_있음으로_보고_OTHER_성분_제품_제외와_안내() throws Exception {
        User user = noMedicationMember();
        fakeEmbedding.setVector(QUERY, vector(9406));
        parseAs(Map.of());
        JsonNode before = send(user, start(user), "혈관 건강이 걱정돼요");
        assertThat(ids(card(before, "OMEGA3"))).contains(9406L); // 약·질환이 없으면 OTHER는 경고만

        parseAs(Map.of("medications", List.of(Map.of("name", "이름모를약", "drug_class", "UNKNOWN"))));
        JsonNode after = send(user, start(user), "이름모를약을 먹는데 혈관 건강이 걱정돼요");

        assertThat(ids(card(after, "OMEGA3"))).doesNotContain(9406L, 9407L);
        assertThat(after.path("medicationNotice").asString()).contains("확인하지 못한 약");
    }

    @Test
    void 대화에서_와파린이면_D3_K2_제품은_비타민D_카드에서_빠진다() throws Exception {
        User user = members.fullMale(1980);
        parseAs(Map.of("medications", List.of(Map.of("name", "와파린", "drug_class", "WARFARIN"))));
        fakeEmbedding.setVector(QUERY, vector(9408));

        JsonNode data = send(user, start(user), "와파린 먹는데 뼈 건강이 걱정돼요");

        List<String> codes = new ArrayList<>();
        data.path("cards").forEach(c -> codes.add(c.path("ingredient").asString()));
        assertThat(codes).doesNotContain("VITAMIN_D"); // 유일한 제품이 같이 든 비타민 K(WARN)로 빠져 카드 제거
        assertThat(card(data, "VITAMIN_K").path("warnings").toString()).contains("와파린");
    }

    @Test
    void 임신_답이면_STATUS_규칙으로_코큐텐_제외() throws Exception {
        User user = members.create(true, Map.of("gender", "F", "birth_year", java.time.Year.now().getValue() - 30),
                List.of("HYPERTENSION"), List.of("D-001"));
        Long id = startWithPregnancy(user);
        parseAs(Map.of());
        fakeEmbedding.setVector(QUERY, vector(9401));

        JsonNode data = mockMvc.perform(post("/api/v1/chat/sessions/" + id + "/messages").header("X-User-Id", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", "피곤하고 잠을 못 자요", "pregnancy", "PREGNANT"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                .transform(this::data);

        assertThat(data.path("excludedItems").toString()).contains("COQ10");
        assertThat(data.path("cards").toString()).doesNotContain("\"COQ10\"");
        assertThat(chatSessionRepository.findById(id).orElseThrow().getPregnancy()).isEqualTo("PREGNANT");
    }

    // ---------- 검색 문장 속 약 이름 (chat-v2) ----------

    @Test
    void 검색_문장에_약_이름이_재요청_뒤에도_남으면_그_단어만_지우고_약은_세션에() throws Exception {
        User user = members.fullMale(1980);
        String withDrug = "와파린 복용 중 피로가 쌓인 사람에게 추천";
        parseSequence(List.of(withDrug, withDrug), List.of(Map.of("name", "와파린", "drug_class", "WARFARIN")));

        JsonNode data = send(user, start(user), "와파린 먹는데 피곤해요");

        assertThat(parseRequests()).extracting(LlmRequest::callTag).containsExactly("CHAT_PARSE", "CHAT_PARSE_REPAIR");
        assertThat(parseRequests().get(1).messages().get(0).text()).contains("search_query에 약 이름 '와파린'");
        assertThat(fakeEmbedding.requests()).containsExactly("복용 중 피로가 쌓인 사람에게 추천");
        assertThat(fakeEmbedding.requests().get(0)).doesNotContain("와파린");
        AiChatSession s = chatSessionRepository.findById(data.path("chatSessionId").asLong()).orElseThrow();
        assertThat(s.getExtraMedications()).contains("D-007");
    }

    @Test
    void 재요청_응답에_약_이름이_없으면_그_검색_문장을_그대로_쓴다() throws Exception {
        User user = members.fullMale(1980);
        parseSequence(List.of("와파린 복용 중 피로가 쌓인 사람에게 추천", "피로가 쌓여 기운이 없는 사람에게 추천"),
                List.of(Map.of("name", "와파린", "drug_class", "WARFARIN")));

        send(user, start(user), "와파린 먹는데 피곤해요");

        assertThat(parseRequests()).hasSize(2);
        assertThat(fakeEmbedding.requests()).containsExactly("피로가 쌓여 기운이 없는 사람에게 추천");
    }

    @Test
    void 눈꺼풀_경련은_추천을_진행하고_경련이_멈추지_않으면_응급() throws Exception {
        User user = members.fullMale(1980);
        parseAs(Map.of());
        fakeEmbedding.setVector(QUERY, vector(9401));

        assertThat(send(user, start(user), "눈꺼풀 경련이 있어요").path("type").asString()).isIn("RESULT", "PENDING");
        JsonNode red = send(user, start(user), "경련이 멈추지 않아요");
        assertThat(red.path("type").asString()).isEqualTo("RED_FLAG");
        assertThat(send(user, start(user), "친구가 기절했어요").path("type").asString()).isEqualTo("RED_FLAG");
    }

    // ---------- 응급·주제 밖·되묻기 ----------

    @Test
    void 서버_키워드에_걸리면_LLM이_NONE이어도_추천하지_않는다() throws Exception {
        User user = members.fullMale(1980);
        parseAs(Map.of());
        Long id = start(user);

        JsonNode data = send(user, id, "가슴이 너무 아프고 숨이 차요");

        assertThat(data.path("type").asString()).isEqualTo("RED_FLAG");
        assertThat(data.path("level").asString()).isEqualTo("EMERGENCY");
        assertThat(data.path("notice").asString()).contains("119");
        assertThat(data.has("cards")).isFalse();
        assertThat(fakeEmbedding.requests()).isEmpty();
        message(user, id, "다른 고민").andExpect(status().isConflict());
    }

    @Test
    void LLM이_CRISIS면_위기_안내() throws Exception {
        User user = members.fullMale(1980);
        parseAs(Map.of("red_flag", "CRISIS", "search_query", ""));

        JsonNode data = send(user, start(user), "요즘 너무 힘들어요");

        assertThat(data.path("type").asString()).isEqualTo("RED_FLAG");
        assertThat(data.path("level").asString()).isEqualTo("CRISIS");
    }

    @Test
    void 주제_밖이면_안내하고_같은_세션에서_다시_입력할_수_있다() throws Exception {
        User user = members.fullMale(1980);
        Long id = start(user);
        parseAs(Map.of("off_topic", true, "search_query", ""));
        assertThat(send(user, id, "오늘 날씨 어때요").path("type").asString()).isEqualTo("OFF_TOPIC");

        parseAs(Map.of());
        fakeEmbedding.setVector(QUERY, vector(9401));
        assertThat(send(user, id, "피곤하고 잠을 못 자요").path("type").asString()).isEqualTo("RESULT");
    }

    @Test
    void 되묻기는_한_번만_두_번째는_LLM이_원해도_진행() throws Exception {
        User user = members.fullMale(1980);
        Long id = start(user);
        Map<String, Object> vague = Map.of("needs_clarification", true, "search_query", "",
                "clarification", Map.of("question", "주로 언제 가장 힘드세요?", "options", List.of("아침", "오후", "잠들기 전")));
        parseAs(vague);

        JsonNode first = send(user, id, "그냥 몸이 안 좋아요");
        assertThat(first.path("type").asString()).isEqualTo("CLARIFY");
        assertThat(first.path("question").asString()).isEqualTo("주로 언제 가장 힘드세요?");
        assertThat(first.path("options").size()).isEqualTo(3);

        // 두 번째도 되묻기를 원하는 응답 → 서버가 무시. 검색 문장이 비어 트랙 2로
        JsonNode second = send(user, id, "잠들기 전");
        assertThat(second.path("type").asString()).isNotEqualTo("CLARIFY");
        assertThat(second.path("type").asString()).isEqualTo("PENDING");
        LlmRequest secondParse = parseRequests().get(1);
        String userMessage = secondParse.messages().get(0).text();
        assertThat(userMessage).contains("그냥 몸이 안 좋아요").contains("주로 언제 가장 힘드세요?").contains("잠들기 전");
    }

    @Test
    void 되묻기_질문에_성분이_들어가면_고정_질문() throws Exception {
        User user = members.fullMale(1980);
        parseAs(Map.of("needs_clarification", true, "search_query", "",
                "clarification", Map.of("question", "마그네슘을 드셔 보셨나요?", "options", List.of("네", "아니요"))));

        JsonNode data = send(user, start(user), "그냥 몸이 안 좋아요");

        assertThat(data.path("question").asString()).isEqualTo("어떤 점이 가장 불편하신지 조금 더 알려주세요");
    }

    @Test
    void 구조화가_두_번_다_실패하면_502() throws Exception {
        User user = members.fullMale(1980);
        Long id = start(user);
        parseAs(Map.of("red_flag", "WRONG"));

        message(user, id, "피곤해요").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CH005"));
        assertThat(parseRequests()).extracting(LlmRequest::callTag).containsExactly("CHAT_PARSE", "CHAT_PARSE_REPAIR");
    }

    // ---------- 트랙 2 + V3 제품 API ----------

    @Test
    void 비슷한_리뷰가_없으면_트랙2_대화에서만_말한_와파린이_V3_제품_필터에_반영된다() throws Exception {
        User user = noMedicationMember();
        parseAs(Map.of("medications", List.of(Map.of("name", "와파린", "drug_class", "WARFARIN"))),
                new FakeAnswer(List.of(
                        new FakeAnswer.Item("VITAMIN_D", "비타민 D", "가짜 응답", BigDecimal.TEN, "mcg"),
                        new FakeAnswer.Item("OMEGA3", "오메가3", "가짜 응답", BigDecimal.valueOf(1000), "mg")), "가짜 응답"));
        // 검색 문장 벡터를 정하지 않으면 해시 벡터 → 실제 언급과 유사도가 거의 0 → τ 미만

        JsonNode data = send(user, start(user), "와파린 먹고 있는데 요즘 기운이 없어요");

        assertThat(data.path("type").asString()).isEqualTo("PENDING");
        assertThat(data.path("track").asString()).isEqualTo("RULE_BASED");
        assertThat(data.path("message").asString()).isEqualTo("비슷한 리뷰가 아직 없어 입력하신 내용으로 성분을 추천했어요");
        long recId = data.path("recommendationId").asLong();
        AiRecommendation rec = recommendationRepository.findById(recId).orElseThrow();
        assertThat(rec.getChatSession().getChatSessionId()).isEqualTo(data.path("chatSessionId").asLong());
        assertThat(rec.getSession()).isNull();
        assertThat(rec.getResponse()).isNull();

        mockMvc.perform(get("/api/v1/recommendations/" + recId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));
        // 프로필에는 약이 없다. 대화에서 말한 와파린으로 D3+K2(9311)가 빠지고 D3만(9312) 남는다
        mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products").param("ingredient", "VITAMIN_D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products.length()").value(1))
                .andExpect(jsonPath("$.data.products[0].catalogProductId").value(9312));
        String omega = mockMvc.perform(get("/api/v1/recommendations/" + recId + "/products").param("ingredient", "OMEGA3"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(omega).contains("와파린");

        // 트랙 2 LLM 요청: 프롬프트 버전 -c, 대화 원문, 현재 섭취 "알 수 없음"
        LlmRequest recommend = fake.requests().stream().filter(r -> LlmRequest.TAG_RECOMMEND.equals(r.callTag()))
                .findFirst().orElseThrow();
        assertThat(recommend.promptVersion()).endsWith("-c");
        assertThat(recommend.messages().get(0).text()).contains("대화: 와파린 먹고 있는데 요즘 기운이 없어요")
                .contains("알 수 없음");
    }

    @Test
    void 결과_캐시_키는_대화_원문이_다르면_다르고_V2와도_다르다() {
        RecommendationInput base = RecommendationInput.builder().respondentType(RecommendationInput.MEMBER)
                .gender("M").birthYear(1980).conditions(List.of()).medications(List.of()).build();
        RecommendationInput chatA = base.toBuilder().chatSessionId(1L)
                .personalizedAnswers(ChatRecommendationService.chatAnswers("피곤해요", null, null)).build();
        RecommendationInput chatA2 = chatA.toBuilder().chatSessionId(2L).build();
        RecommendationInput chatB = base.toBuilder().chatSessionId(1L)
                .personalizedAnswers(ChatRecommendationService.chatAnswers("뼈 건강이 걱정돼요", null, null)).build();

        String keyA = cacheKeyFactory.key(chatA, "V4", "v4.3-c", "m");
        assertThat(cacheKeyFactory.key(chatA2, "V4", "v4.3-c", "m")).isEqualTo(keyA); // 세션 id는 키에 없음
        assertThat(cacheKeyFactory.key(chatB, "V4", "v4.3-c", "m")).isNotEqualTo(keyA);
        assertThat(cacheKeyFactory.key(chatA, "V4", "v4.3-p", "m")).isNotEqualTo(keyA);
    }

    @Test
    void 모델이_다르거나_길이가_틀린_벡터는_건너뛰고_데이터가_바뀌면_다시_읽는다() {
        jdbcTemplate.update("update review_mentions set embedding_model = 'other-model' where mention_id = 9402");
        jdbcTemplate.update("update review_mentions set embedding = X'00000000' where mention_id = 9403");
        index.refreshIfChanged(); // computed_at이 그대로면 다시 읽지 않는다
        assertThat(index.size()).isEqualTo(10);

        jdbcTemplate.update("update review_product_scores set computed_at = DATEADD('DAY', 1, computed_at)");
        index.refreshIfChanged();
        assertThat(index.size()).isEqualTo(8);
    }

    @Test
    void 대화는_90일_뒤_삭제되고_트랙2_추천은_남아_chat_session_id만_비운다() throws Exception {
        User user = members.fullMale(1980);
        parseAs(Map.of());
        JsonNode data = send(user, start(user), "요즘 기운이 없어요"); // 해시 벡터 → 트랙 2
        long chatId = data.path("chatSessionId").asLong();
        long recId = data.path("recommendationId").asLong();
        jdbcTemplate.update("update ai_chat_sessions set started_at = DATEADD('DAY', -91, started_at) where chat_session_id = ?",
                chatId);

        cleanupScheduler.purge(java.time.LocalDateTime.now());

        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_chat_sessions where chat_session_id = ?",
                Integer.class, chatId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_chat_turns where chat_session_id = ?",
                Integer.class, chatId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_chat_results where chat_session_id = ?",
                Integer.class, chatId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select chat_session_id from ai_recommendations where recommendation_id = ?",
                Long.class, recId)).isNull();
    }

    // ---------- API ----------

    @Test
    void 비회원은_401() throws Exception {
        mockMvc.perform(post("/api/v1/chat/sessions")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E003"));
        mockMvc.perform(post("/api/v1/chat/sessions/1/messages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"피곤해요\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void 건강정보가_없으면_등록_안내() throws Exception {
        User user = members.create(true, Map.of(), List.of(), List.of());
        mockMvc.perform(post("/api/v1/chat/sessions").header("X-User-Id", user.getId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CH001"));
    }

    @Test
    void 입력_300자_초과는_400_결과가_있으면_409_남의_세션은_404() throws Exception {
        User user = members.fullMale(1980);
        User other = members.fullMale(1985);
        Long id = start(user);

        message(user, id, "가".repeat(301)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CH004"));
        message(user, id, "  ").andExpect(status().isBadRequest());
        message(other, id, "피곤해요").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CH002"));

        parseAs(Map.of());
        fakeEmbedding.setVector(QUERY, vector(9401));
        send(user, id, "피곤하고 잠을 못 자요");
        message(user, id, "또 다른 고민").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CH003"));
    }

    @Test
    void 결과_다시_보기는_스냅샷_그대로() throws Exception {
        User user = members.fullMale(1980);
        parseAs(Map.of());
        fakeEmbedding.setVector(QUERY, vector(9401));
        Long id = start(user);
        JsonNode result = send(user, id, "피곤하고 잠을 못 자요");

        JsonNode again = mockMvc.perform(get("/api/v1/chat/sessions/" + id).header("X-User-Id", user.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString().transform(this::data);

        assertThat(again.path("status").asString()).isEqualTo("DONE");
        assertThat(again.path("result").path("cards")).isEqualTo(result.path("cards"));
        // 검수용 값은 DB에만 (API로 내보내지 않음)
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select top_similarity, hit_mention_ids, track from ai_chat_results where chat_session_id = ?", id);
        assertThat(((BigDecimal) row.get("TOP_SIMILARITY")).doubleValue()).isGreaterThan(0.99);
        assertThat(row.get("TRACK")).isEqualTo("REVIEW");
        assertThat(again.toString()).doesNotContain("hitMentionIds").doesNotContain("topSimilarity");
    }

    @Test
    void 대화_시작_19_50세_여성만_임신_질문() throws Exception {
        User male = members.fullMale(1980);
        mockMvc.perform(post("/api/v1/chat/sessions").header("X-User-Id", male.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pregnancyQuestion").doesNotExist());
        User female = members.create(true, Map.of("gender", "F", "birth_year", java.time.Year.now().getValue() - 30),
                List.of("HYPERTENSION"), List.of("D-001"));
        mockMvc.perform(post("/api/v1/chat/sessions").header("X-User-Id", female.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pregnancyQuestion.options.length()").value(4))
                .andExpect(jsonPath("$.data.pregnancyQuestion.options[2].code").value("PREGNANT"));
    }

    // ---------- 도우미 ----------

    // 프로필에 질환·약이 없다고 설문에서 답한 회원 (V2 조건 충족, 약 없음)
    User noMedicationMember() throws Exception {
        User user = members.create(true, Map.of("gender", "M", "birth_year", 1980), List.of(), List.of());
        mockMvc.perform(post("/api/v1/surveys/" + SURVEY_ID + "/responses").contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", user.getId())
                .content(objectMapper.writeValueAsString(Map.of("answers", List.of(
                        choice(Q_CONDITIONS, CONDITION_NONE), text(Q_MEDICATIONS, "[]"))))))
                .andExpect(status().isOk());
        return user;
    }

    void parseAs(Map<String, Object> overrides) {
        parseAs(overrides, null);
    }

    // 대화 구조화 요청에는 정해진 JSON(기본값 + overrides), 그 밖의 요청은 기존 Fake 답
    void parseAs(Map<String, Object> overrides, FakeAnswer answer) {
        Function<LlmRequest, tools.jackson.databind.JsonNode> output = req -> {
            ObjectNode node = (ObjectNode) fake.chatParseOutput(QUERY);
            overrides.forEach((k, v) -> node.set(k, objectMapper.valueToTree(v)));
            return node;
        };
        FakeAnswer fallback = answer;
        fake.setResponder(req -> {
            if (req.outputSchema() != null && FakeLlmClient.CHAT_PARSE_SCHEMA.equals(req.outputSchema().name())) {
                return fake.structuredResponse(req, output.apply(req));
            }
            return fallback == null ? fake.render(req, defaultAnswer()) : fake.render(req, fallback);
        });
    }

    // 대화 구조화 요청마다 검색 문장을 차례로 돌려준다 (재요청 흐름 확인용)
    void parseSequence(List<String> queries, List<Map<String, String>> medications) {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        fake.setResponder(req -> {
            if (req.outputSchema() != null && FakeLlmClient.CHAT_PARSE_SCHEMA.equals(req.outputSchema().name())) {
                String q = queries.get(Math.min(n.getAndIncrement(), queries.size() - 1));
                ObjectNode node = (ObjectNode) fake.chatParseOutput(q);
                node.set("medications", objectMapper.valueToTree(medications));
                return fake.structuredResponse(req, node);
            }
            return fake.render(req, defaultAnswer());
        });
    }

    static FakeAnswer defaultAnswer() {
        return new FakeAnswer(List.of(new FakeAnswer.Item("VITAMIN_D", "비타민 D", "가짜 응답", BigDecimal.TEN, "mcg")),
                "가짜 응답입니다");
    }

    List<LlmRequest> parseRequests() {
        return fake.requests().stream().filter(r -> r.outputSchema() != null
                && FakeLlmClient.CHAT_PARSE_SCHEMA.equals(r.outputSchema().name())).toList();
    }

    float[] vector(long mentionId) {
        byte[] bytes = jdbcTemplate.queryForObject("select embedding from review_mentions where mention_id = ?",
                byte[].class, mentionId);
        float[] v = new float[1536];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(v);
        return v;
    }

    Long start(User user) throws Exception {
        String body = mockMvc.perform(post("/api/v1/chat/sessions").header("X-User-Id", user.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return data(body).path("chatSessionId").asLong();
    }

    Long startWithPregnancy(User user) throws Exception {
        String body = mockMvc.perform(post("/api/v1/chat/sessions").header("X-User-Id", user.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pregnancyQuestion.question").exists())
                .andReturn().getResponse().getContentAsString();
        return data(body).path("chatSessionId").asLong();
    }

    ResultActions message(User user, Long id, String text) throws Exception {
        return mockMvc.perform(post("/api/v1/chat/sessions/" + id + "/messages").header("X-User-Id", user.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("text", text))));
    }

    JsonNode send(User user, Long id, String text) throws Exception {
        return data(message(user, id, text).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    JsonNode data(String body) {
        return objectMapper.readTree(body).path("data");
    }

    static JsonNode card(JsonNode data, String ingredient) {
        for (JsonNode c : data.path("cards")) {
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

    static int cardCount(JsonNode data) {
        return data.path("cards").size();
    }
}
