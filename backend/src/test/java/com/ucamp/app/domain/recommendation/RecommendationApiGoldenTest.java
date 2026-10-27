package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationItemRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import com.ucamp.app.support.GoldenFiles;
import com.ucamp.app.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ucamp.app.support.SurveyAnswers.choice;
import static com.ucamp.app.support.SurveyAnswers.number;
import static com.ucamp.app.support.SurveyAnswers.text;
import static com.ucamp.app.support.SurveyFixture.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 결과 고정 테스트 (리팩터링 안전망): V4 설문 제출 → 추천 생성 → 조회 → 알림 진입의 HTTP 응답 전체와 저장된 행.
 * 운영처럼 요청마다 트랜잭션이 따로 돌도록 @Transactional을 붙이지 않는다 (POST와 GET의 숫자 표기 차이까지 그대로 고정).
 * ID·시각처럼 실행마다 바뀌는 값만 지운다.
 */
@SpringBootTest(properties = "recommendation.pipeline-version=V4")
@AutoConfigureMockMvc
class RecommendationApiGoldenTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AiRecommendationRepository recommendationRepository;
    @Autowired
    AiRecommendationItemRepository itemRepository;
    @Autowired
    FakeLlmClient fake;

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 회원_추천_생성_조회_알림진입() throws Exception {
        fake.answerWith(new FakeAnswer(List.of(
                new FakeAnswer.Item("VITAMIN_C", null, "흡연자는 필요량이 늘어요", null, null),
                new FakeAnswer.Item("VITAMIN_D", null, "햇빛 노출이 적어요", null, null),
                new FakeAnswer.Item("POTASSIUM", null, "혈압", null, null),
                new FakeAnswer.Item("IRON", null, "피로", null, null)), "회원 요약"));
        User user = TestUsers.create(userRepository, true);
        long responseId = submit(user.getId(), List.of(
                choice(Q_GENDER, GENDER_M), number(Q_BIRTH, Year.now().getValue() - 40),
                choice(Q_SMOKING, SMOKING_ONE_PACK), choice(Q_CAFFEINE, CAFFEINE_HIGH), choice(Q_SUN, SUN_LOW),
                choice(Q_PREGNANCY, PREGNANCY_NA), choice(Q_CONDITIONS, CONDITION_HYPERTENSION),
                text(Q_MEDICATIONS, "[{\"drugId\":\"D-003\"},{\"name\":\"기타 약\"}]"),
                text(Q_SUPPLEMENTS, "[{\"ingredient\":\"VITAMIN_C\",\"amount\":25,\"unit\":\"mg\"}]")));

        Map<String, Object> golden = new LinkedHashMap<>();
        MvcResult created = mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"responseId\":" + responseId + "}")).andReturn();
        golden.put("post", response(created));
        long recommendationId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("recommendationId").asLong();

        golden.put("get", response(mockMvc.perform(get("/api/v1/recommendations/" + recommendationId)).andReturn()));
        golden.put("latest", response(mockMvc.perform(get("/api/v1/users/me/recommendations/latest")
                .header("X-User-Id", user.getId())).andReturn()));
        golden.put("saved", saved(recommendationId));
        GoldenFiles.assertMatches("api/member-v4", golden);
    }

    @Test
    void 비회원_추천과_오류_응답() throws Exception {
        long responseId = submit(null, List.of(choice(Q_GENDER, GENDER_F), number(Q_BIRTH, Year.now().getValue() - 30),
                choice(Q_DIET, DIET_VEGAN), choice(Q_SUN, SUN_LOW)));

        Map<String, Object> golden = new LinkedHashMap<>();
        golden.put("post", response(mockMvc.perform(post("/api/v1/recommendations")
                .contentType(MediaType.APPLICATION_JSON).content("{\"responseId\":" + responseId + "}")).andReturn()));
        golden.put("notFound", response(mockMvc.perform(get("/api/v1/recommendations/999999")).andReturn()));
        golden.put("noResponseId", response(mockMvc.perform(post("/api/v1/recommendations")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn()));
        golden.put("unknownResponse", response(mockMvc.perform(post("/api/v1/recommendations")
                .contentType(MediaType.APPLICATION_JSON).content("{\"responseId\":999999}")).andReturn()));
        golden.put("latestGuest", response(mockMvc.perform(get("/api/v1/users/me/recommendations/latest")).andReturn()));
        GoldenFiles.assertMatches("api/guest-v4", golden);
    }

    // ---------- 스냅샷 ----------

    private Map<String, Object> response(MvcResult r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getResponse().getStatus());
        // 숫자 표기(1E+2 / 100.00)까지 보려고 원문 문자열도 남긴다
        String body = r.getResponse().getContentAsString();
        m.put("body", scrub(objectMapper.readTree(body)));
        m.put("raw", scrubRaw(body));
        return m;
    }

    private JsonNode scrub(JsonNode node) {
        if (node.isObject()) {
            ObjectNode o = (ObjectNode) node;
            for (String k : List.of("recommendationId", "responseId", "createdAt")) {
                if (o.has(k)) {
                    o.put(k, "<" + k + ">");
                }
            }
            o.forEach(this::scrub);
        } else if (node.isArray()) {
            node.forEach(this::scrub);
        }
        return node;
    }

    private static String scrubRaw(String body) {
        return body.replaceAll("\"(recommendationId|responseId)\":\\d+", "\"$1\":0")
                .replaceAll("\"createdAt\":\"[^\"]*\"", "\"createdAt\":\"-\"");
    }

    private Map<String, Object> saved(long recommendationId) {
        AiRecommendation rec = recommendationRepository.findById(recommendationId).orElseThrow();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", rec.getSummary());
        m.put("modelName", rec.getModelName());
        m.put("status", rec.getStatus());
        m.put("errorCode", rec.getErrorCode());
        m.put("startedAt", rec.getStartedAt() == null ? null : "set");
        m.put("finishedAt", rec.getFinishedAt() == null ? null : "set");
        m.put("items", itemRepository.findByRecommendation_RecommendationIdOrderBySortOrderAscRecommendationItemIdAsc(
                recommendationId).stream().map(RecommendationApiGoldenTest::item).toList());
        return m;
    }

    private static Map<String, Object> item(AiRecommendationItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ingredientName", i.getIngredientName());
        m.put("reason", i.getReason());
        m.put("sortOrder", i.getSortOrder());
        m.put("recommendedAmount", plain(i.getRecommendedAmount()));
        m.put("currentIntake", plain(i.getCurrentIntake()));
        m.put("intakeStatus", i.getIntakeStatus());
        m.put("unit", i.getUnit());
        m.put("doseNote", i.getDoseNote());
        m.put("excluded", i.isExcluded());
        m.put("appliedRuleIds", i.getAppliedRuleIds());
        m.put("warnings", i.getWarnings());
        return m;
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toString();
    }

    private long submit(Long userId, List<Map<String, Object>> answers) throws Exception {
        var req = post("/api/v1/surveys/" + SURVEY_ID + "/responses").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("answers", answers)));
        if (userId != null) {
            req.header("X-User-Id", userId);
        }
        String body = mockMvc.perform(req).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("responseId").asLong();
    }
}
