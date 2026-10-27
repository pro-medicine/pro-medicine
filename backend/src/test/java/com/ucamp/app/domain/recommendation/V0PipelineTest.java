package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.domain.recommendation.pipeline.V0Pipeline;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class V0PipelineTest {
    @Autowired
    V0Pipeline pipeline;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    LlmCallLogRepository logRepository;

    @BeforeEach
    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 성공_LLM이_준_이름과_용량을_그대로_쓴다() {
        fake.setResponder(req -> fake.textResponse(req, """
                추천 결과입니다.
                ```json
                {"recommendations":[{"name":"비타민C","reason":"흡연","amount":125,"unit":"mg"},
                                    {"name":"마그네슘","reason":"수면"}],"summary":"요약"}
                ```
                """));

        RecommendationResult result = pipeline.run(input());

        assertThat(result.parseSuccess()).isTrue();
        assertThat(result.pipelineVersion()).isEqualTo("V0");
        assertThat(result.items()).extracting(i -> i.ingredient()).containsExactly("비타민C", "마그네슘");
        assertThat(result.items().get(0).amount()).isEqualByComparingTo("125");
        assertThat(result.items().get(1).amount()).isNull();
        assertThat(result.summary()).isEqualTo("요약");
        LlmCallLog log = logRepository.findById(result.llmCallLogIds().get(0)).orElseThrow();
        assertThat(log.getOutcome()).isEqualTo(LlmCallLog.SUCCESS);
        // 프롬프트에 정규화 입력이 문장으로 들어간다
        assertThat(fake.requests().get(0).messages().get(0).text()).contains("흡연: 하루 반 갑 이하");
        assertThat(fake.requests().get(0).outputSchema()).isNull();
    }

    @Test
    void 파싱_실패면_재시도_없이_빈_결과와_PARSE_FAIL() {
        fake.setResponder(req -> fake.textResponse(req, "죄송하지만 JSON으로 드리기 어렵습니다."));

        RecommendationResult result = pipeline.run(input());

        assertThat(result.parseSuccess()).isFalse();
        assertThat(result.items()).isEmpty();
        assertThat(fake.requests()).hasSize(1);
        assertThat(logRepository.findById(result.llmCallLogIds().get(0)).orElseThrow().getOutcome())
                .isEqualTo(LlmCallLog.PARSE_FAIL);
    }

    @Test
    void JSON이지만_형식이_다르면_PARSE_FAIL() {
        fake.setResponder(req -> fake.textResponse(req, "{\"items\":[]}"));

        assertThat(pipeline.run(input()).parseSuccess()).isFalse();
    }

    static RecommendationInput input() {
        return RecommendationInput.builder()
                .respondentType(RecommendationInput.MEMBER)
                .gender("M").birthYear(1990).smoking("HALF_PACK")
                .conditions(List.of()).medications(List.of())
                .currentIntake(List.of(new RecommendationInput.Intake("MAGNESIUM", BigDecimal.valueOf(30), "mg")))
                .build();
    }
}
