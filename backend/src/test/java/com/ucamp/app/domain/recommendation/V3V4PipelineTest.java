package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.pipeline.PipelineRegistry;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class V3V4PipelineTest {
    // 버전 이름으로 꺼낸다 (평가 러너·API와 같은 경로)
    @Autowired
    PipelineRegistry registry;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    MeterRegistry meterRegistry;

    // 흡연 + ACE 억제제 복용 남성
    private final RecommendationInput input = RecommendationInput.builder()
            .respondentType("MEMBER").gender("M").birthYear(1980).smoking("ONE_PACK")
            .medications(List.of(new RecommendationInput.Medication("D-003", "ACE_INHIBITOR", "에날라프릴")))
            .build();

    @BeforeEach
    void answer() {
        fake.reset();
        // LLM이 금지 성분(베타카로틴, 칼륨)을 섞어서 답한 상황
        fake.answerWith(new FakeAnswer(List.of(
                new FakeAnswer.Item("VITAMIN_C", null, "흡연", null, null),
                new FakeAnswer.Item("BETA_CAROTENE", null, "눈", null, null),
                new FakeAnswer.Item("POTASSIUM", null, "혈압", null, null)), "요약"));
    }

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void V3_사후_검증으로_제외하고_원래_출력은_보존() {
        RecommendationResult r = registry.get("V3").run(input);

        assertThat(r.itemsOrEmpty()).hasSize(3);
        assertThat(r.finalItems()).extracting(RecommendationItem::ingredient).containsExactly("VITAMIN_C");
        RecommendationItem c = r.finalItems().get(0);
        assertThat(c.amount()).isEqualByComparingTo("135"); // R-01 ADJUST는 기준표 흡연 행(KDRI 100 + 35)으로 반영
        assertThat(c.appliedRuleIdsOrEmpty()).containsExactly("R-01");
        assertThat(r.itemsOrEmpty().stream().filter(RecommendationItem::excluded))
                .extracting(RecommendationItem::appliedRuleIds)
                .containsExactly(List.of("R-02"), List.of("R-13"));
        // V3는 프롬프트에 금지 목록이 없다
        assertThat(fake.requests().get(0).messages().get(0).text()).doesNotContain("추천하지 마라");
        assertThat(meterRegistry.find("recommendation.rule.hit").tag("rule_id", "R-02").tag("phase", "post").counter())
                .isNotNull();
    }

    @Test
    void V4_사전_필터로_금지_목록을_사용자_메시지_뒤에_넣고_사후_검증도_유지() {
        RecommendationResult r = registry.get("V4").run(input);

        String user = fake.requests().get(0).messages().get(0).text();
        assertThat(user).contains("추천하지 마라").contains("BETA_CAROTENE").contains("POTASSIUM");
        assertThat(user.indexOf("추천하지 마라")).isGreaterThan(user.indexOf("사용자 정보"));
        assertThat(fake.requests().get(0).system()).doesNotContain("혈압약(ACE 억제제)"); // 금지 이유는 시스템(고정 부분)에 없음
        assertThat(r.finalItems()).extracting(RecommendationItem::ingredient).containsExactly("VITAMIN_C");
        assertThat(r.promptVersion()).isEqualTo("v4.3");
    }
}
