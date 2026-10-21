package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.domain.recommendation.pipeline.V2Pipeline;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 기준값 시드(sql/seed-ingredient-standards.sql, domain-rules.md 출처)로 V2 용량 계산 확인
@SpringBootTest
class V2PipelineTest {
    @Autowired
    V2Pipeline pipeline;
    @Autowired
    FakeLlmClient fake;

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 용량은_LLM이_아니라_기준표로_계산() {
        fake.answerWith(new FakeAnswer(List.of(
                new FakeAnswer.Item("VITAMIN_C", "비타민 C", "흡연", new BigDecimal("999"), "mg"),
                new FakeAnswer.Item("MAGNESIUM", "마그네슘", "수면", null, null)), "요약"));
        RecommendationInput input = RecommendationInput.builder()
                .respondentType("MEMBER").gender("M").birthYear(1990).smoking("ONE_PACK")
                .currentIntake(List.of(new RecommendationInput.Intake("VITAMIN_C", new BigDecimal("25"), "mg")))
                .build();

        RecommendationResult r = pipeline.run(input);

        RecommendationItem c = r.items().get(0);
        assertThat(c.ingredient()).isEqualTo("VITAMIN_C");
        assertThat(c.amount()).isEqualByComparingTo("100"); // 흡연 남성 125 - 현재 25
        assertThat(c.unit()).isEqualTo("mg");
        assertThat(c.intakeStatus()).isEqualTo("KNOWN");
        RecommendationItem mg = r.items().get(1);
        assertThat(mg.amount()).isNull();
        assertThat(mg.doseNote()).isEqualTo("기준값 미등록");
        // 요청 스키마에 amount가 없다 → Fake도 용량을 넣지 않는다
        assertThat(fake.requests().get(0).toolsOrEmpty().get(0).inputSchema().toString()).doesNotContain("amount");
        assertThat(fake.requests().get(0).system()).contains("용량은 쓰지 마라");
        assertThat(r.promptVersion()).isEqualTo("v2.1");
    }
}
