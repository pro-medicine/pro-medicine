package com.ucamp.app.domain.recommendation;

import tools.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.domain.recommendation.pipeline.V1Pipeline;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class V1PipelineTest {
    @Autowired
    V1Pipeline pipeline;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    LlmCallLogRepository logRepository;
    @Autowired
    ObjectMapper om;

    private static final String VALID = """
            {"recommendations":[{"ingredient":"VITAMIN_C","reason":"흡연","priority":1,"amount":125,"unit":"mg"},
                                {"ingredient":"MAGNESIUM","reason":"수면","priority":2,"amount":null,"unit":null}],
             "summary":"요약"}""";
    private static final String INVALID = """
            {"recommendations":[{"ingredient":"VITAMIN_E","reason":"x","priority":1,"amount":null,"unit":null}],"summary":"s"}""";

    @BeforeEach
    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 정상_tool_use_입력을_성분_코드로() throws Exception {
        fake.setResponder(req -> fake.toolResponse(req, "submit_recommendation", read(VALID)));

        RecommendationResult r = pipeline.run(V0PipelineTest.input());

        assertThat(r.parseSuccess()).isTrue();
        assertThat(r.items()).extracting(i -> i.ingredient()).containsExactly("VITAMIN_C", "MAGNESIUM");
        assertThat(r.items().get(0).amount()).isEqualByComparingTo("125");
        assertThat(r.items().get(0).unit()).isEqualTo("mg");
        assertThat(r.llmCallLogIds()).hasSize(1);
        // 요청에 strict 도구 정의가 들어간다
        LlmRequest req = fake.requests().get(0);
        assertThat(req.toolsOrEmpty()).hasSize(1);
        assertThat(req.toolsOrEmpty().get(0).strict()).isTrue();
        assertThat(req.toolName()).isEqualTo("submit_recommendation");
        assertThat(req.promptVersion()).isEqualTo("v1.1");
        assertThat(req.system()).contains("VITAMIN_C: 비타민 C, mg").contains("현재 복용 중인 영양제 함량을 뺀");
    }

    @Test
    void 스키마_위반_후_재요청_성공() {
        AtomicInteger n = new AtomicInteger();
        fake.setResponder(req -> fake.toolResponse(req, "submit_recommendation",
                read(n.getAndIncrement() == 0 ? INVALID : VALID)));

        RecommendationResult r = pipeline.run(V0PipelineTest.input());

        assertThat(r.parseSuccess()).isTrue();
        assertThat(r.llmCallLogIds()).hasSize(2);
        LlmCallLog first = logRepository.findById(r.llmCallLogIds().get(0)).orElseThrow();
        LlmCallLog repair = logRepository.findById(r.llmCallLogIds().get(1)).orElseThrow();
        assertThat(first.getOutcome()).isEqualTo(LlmCallLog.VALIDATION_FAIL);
        assertThat(first.getCallTag()).isEqualTo("RECOMMEND");
        assertThat(repair.getCallTag()).isEqualTo("RECOMMEND_REPAIR");
        assertThat(repair.getOutcome()).isEqualTo(LlmCallLog.SUCCESS);
        assertThat(repair.getAttemptNo()).isEqualTo(1);
        // 재요청 메시지에 실패 이유가 붙는다
        assertThat(fake.requests().get(1).messages().get(0).text()).contains("VITAMIN_E");
    }

    @Test
    void 재요청도_실패하면_PARSE_FAIL과_빈_결과() {
        fake.setResponder(req -> fake.toolResponse(req, "submit_recommendation", read(INVALID)));

        RecommendationResult r = pipeline.run(V0PipelineTest.input());

        assertThat(r.parseSuccess()).isFalse();
        assertThat(r.items()).isEmpty();
        assertThat(fake.requests()).hasSize(2);
        assertThat(logRepository.findById(r.llmCallLogIds().get(1)).orElseThrow().getOutcome())
                .isEqualTo(LlmCallLog.PARSE_FAIL);
    }

    @Test
    void 도구를_호출하지_않고_텍스트로_답하면_검증_실패로_재요청() {
        AtomicInteger n = new AtomicInteger();
        fake.setResponder(req -> n.getAndIncrement() == 0 ? fake.textResponse(req, "비타민 C를 추천합니다")
                : fake.toolResponse(req, "submit_recommendation", read(VALID)));

        RecommendationResult r = pipeline.run(V0PipelineTest.input());

        assertThat(r.parseSuccess()).isTrue();
        assertThat(fake.requests().get(1).messages().get(0).text()).contains("JSON 형식(submit_recommendation 스키마)으로 답하지 않았다");
    }

    private tools.jackson.databind.JsonNode read(String s) {
        try {
            return om.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
