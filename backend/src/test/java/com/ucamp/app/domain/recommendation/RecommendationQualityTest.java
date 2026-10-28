package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.pipeline.PipelineRegistry;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationQualityCheck;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.LlmRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 7-3: 요약 길이·문장 수·금지 표현·금지 성분 언급·성분 0개 → 1회 재요청, 남으면 고정 문구 / 그대로 저장
@SpringBootTest
class RecommendationQualityTest {
    static final String LONG_SUMMARY = "신장 질환의 단계와 혈압약 이름이 확인되지 않아 추천을 보류합니다. 칼륨이 쌓일 수 있습니다. "
            + "담당 의료진에게 음주량을 알리고 진료받으세요. 수면을 위해 늦은 시간 카페인과 음주를 피하고 한 달 이상 지속된 소화 불편은 꼭 확인하세요.";

    @Autowired
    PipelineRegistry registry;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    ObjectMapper objectMapper;

    // 흡연자: 사전 금지 성분 BETA_CAROTENE (R-02)
    private final RecommendationInput smoker = RecommendationInput.builder()
            .respondentType("MEMBER").gender("M").birthYear(1980).smoking("ONE_PACK").build();

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 요약_검사_단위() {
        assertThat(check("비타민 D를 골랐어요. 햇빛이 적어요.", List.of("VITAMIN_D")).ok()).isTrue();
        assertThat(check("가".repeat(101), List.of("VITAMIN_D")).summaryErrors()).hasSize(1);
        assertThat(check("하나. 둘. 셋.", List.of("VITAMIN_D")).summaryErrors()).hasSize(1);
        assertThat(check("검사 결과에 따라 판단하세요", List.of("VITAMIN_D")).summaryErrors()).hasSize(1);
        assertThat(check("요약", List.of()).itemErrors()).hasSize(1);
        // 금지 성분 이름 언급 (공백 무시)
        assertThat(RecommendationQualityCheck.check(output("베타 카로틴은 제외했어요", List.of("VITAMIN_D")),
                List.of("BETA_CAROTENE")).summaryErrors()).hasSize(1);
        assertThat(RecommendationQualityCheck.check(output("비타민A는 빼요", List.of("VITAMIN_D")),
                List.of("VITAMIN_A")).summaryErrors()).hasSize(1);
    }

    @Test
    void 약한_오류는_이유를_붙여_1회_재요청하고_고친_응답을_쓴다() {
        script(output(LONG_SUMMARY + " 베타카로틴은 제외했습니다.", List.of()),
                output("음주와 흡연을 고려해 골랐어요.", List.of("VITAMIN_C")));

        RecommendationResult r = registry.get("V4").run(smoker);

        assertThat(fake.requests()).extracting(LlmRequest::callTag)
                .containsExactly(LlmRequest.TAG_RECOMMEND, LlmRequest.TAG_RECOMMEND_REPAIR);
        String repair = fake.requests().get(1).messages().get(0).text();
        assertThat(repair).contains("100자 이내").contains("2문장 이내").contains("진료·처방·검사 지시")
                .contains("제외·금지 성분(베타카로틴)").contains("1개 이상");
        assertThat(r.parseSuccess()).isTrue();
        assertThat(r.summary()).isEqualTo("음주와 흡연을 고려해 골랐어요.");
        assertThat(r.finalItems()).extracting(RecommendationItem::ingredient).containsExactly("VITAMIN_C");
    }

    @Test
    void 재요청_뒤에도_약한_오류면_요약은_고정_문구_성분_0개는_그대로_저장() {
        script(output(LONG_SUMMARY, List.of()), output(LONG_SUMMARY, List.of()));

        RecommendationResult r = registry.get("V4").run(smoker);

        assertThat(r.parseSuccess()).isTrue();
        assertThat(r.summary()).isEqualTo(RecommendationQualityCheck.FALLBACK_SUMMARY);
        assertThat(r.finalItems()).isEmpty();
    }

    @Test
    void 재요청이_형식_오류면_약한_오류만_있던_첫_응답을_고쳐_쓴다() {
        script(output(LONG_SUMMARY, List.of("VITAMIN_C")), objectMapper.createObjectNode());

        RecommendationResult r = registry.get("V4").run(smoker);

        assertThat(r.parseSuccess()).isTrue();
        assertThat(r.summary()).isEqualTo(RecommendationQualityCheck.FALLBACK_SUMMARY);
        assertThat(r.finalItems()).extracting(RecommendationItem::ingredient).containsExactly("VITAMIN_C");
    }

    @Test
    void v4_프롬프트가_아닌_버전은_품질_검사를_하지_않는다() {
        script(output(LONG_SUMMARY, List.of()));

        RecommendationResult r = registry.get("V3").run(smoker);

        assertThat(fake.requests()).hasSize(1);
        assertThat(r.summary()).isEqualTo(LONG_SUMMARY);
    }

    private RecommendationQualityCheck.Result check(String summary, List<String> items) {
        return RecommendationQualityCheck.check(output(summary, items), List.of());
    }

    private JsonNode output(String summary, List<String> ingredients) {
        List<Map<String, Object>> recs = new java.util.ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            recs.add(Map.of("ingredient", ingredients.get(i), "reason", "이유", "priority", i + 1));
        }
        return objectMapper.valueToTree(Map.of("recommendations", recs, "summary", summary));
    }

    // 호출 순서대로 응답
    private void script(JsonNode... outputs) {
        Deque<JsonNode> queue = new ArrayDeque<>(List.of(outputs));
        fake.setResponder(req -> fake.structuredResponse(req, queue.isEmpty() ? outputs[outputs.length - 1] : queue.poll()));
    }
}
