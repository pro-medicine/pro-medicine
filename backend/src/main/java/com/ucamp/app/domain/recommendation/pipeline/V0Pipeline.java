package com.ucamp.app.domain.recommendation.pipeline;

import com.ucamp.app.global.metrics.StageTimer;
import com.ucamp.app.infra.llm.LlmClient;
import com.ucamp.app.infra.llm.LlmProperties;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.LlmResponse;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V0: 의도적으로 가장 단순한 방식. 이후 모든 개선의 Before라 지우지 않는다.
 * 프롬프트에 "JSON으로 답해라"만 있고 구조화 출력 · 스키마 강제 · 재시도 · 기준표 · 규칙 검증이 전부 없다.
 * 응답 텍스트의 첫 '{'부터 마지막 '}'까지를 Jackson으로 파싱하고, 실패하면 재시도 없이 빈 결과(PARSE_FAIL).
 */
@Component
@RequiredArgsConstructor
public class V0Pipeline implements RecommendationPipeline {
    public static final String VERSION = "V0";
    public static final String PROMPT_VERSION = "v0.1";
    private static final int MAX_NAME_LENGTH = 150;

    private final LlmClient llmClient;
    private final LlmCallLogService llmCallLogService;
    private final LlmProperties llmProperties;
    private final PromptTemplates promptTemplates;
    private final InputFormatter inputFormatter;
    private final ObjectMapper objectMapper;
    private final StageTimer stageTimer;

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public RecommendationResult run(RecommendationInput input) {
        String user = promptTemplates.render("v0/user.txt", Map.of("input", inputFormatter.format(input)));
        LlmRequest request = PipelineRequests.recommend(input, VERSION, PROMPT_VERSION, llmProperties.getModel(),
                promptTemplates.load("v0/system.txt"), user, llmProperties.getMaxTokens(), LlmRequest.TAG_RECOMMEND).build();
        LlmResponse response = stageTimer.record(StageTimer.LLM, VERSION, () -> llmClient.call(request));

        Parsed parsed = stageTimer.record(StageTimer.PARSE, VERSION, () -> parse(response.text()));
        List<Long> logIds = response.callLogId() == null ? List.of() : List.of(response.callLogId());
        if (parsed.failure() != null) {
            llmCallLogService.markOutcome(response.callLogId(), LlmCallLog.PARSE_FAIL);
            return RecommendationResult.parseFailure(VERSION, PROMPT_VERSION, response.model(), logIds, parsed.failure());
        }
        return RecommendationResult.builder()
                .pipelineVersion(VERSION)
                .promptVersion(PROMPT_VERSION)
                .model(response.model())
                .llmCallLogIds(logIds)
                .parseSuccess(true).items(parsed.items()).summary(parsed.summary()).build();
    }

    // 실패 유형(본문 대신 리포트에 남김): NO_JSON_OBJECT, INVALID_JSON, MISSING_RECOMMENDATIONS, EMPTY_NAME
    private record Parsed(List<RecommendationItem> items, String summary, String failure) {
        static Parsed fail(String failure) {
            return new Parsed(List.of(), null, failure);
        }
    }

    private Parsed parse(String text) {
        if (text == null) {
            return Parsed.fail("NO_JSON_OBJECT");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Parsed.fail("NO_JSON_OBJECT");
        }
        try {
            JsonNode root = objectMapper.readTree(text.substring(start, end + 1));
            JsonNode recs = root.get("recommendations");
            if (recs == null || !recs.isArray()) {
                return Parsed.fail("MISSING_RECOMMENDATIONS");
            }
            List<RecommendationItem> items = new ArrayList<>();
            int order = 1;
            for (JsonNode r : recs) {
                String name = r.path("name").asString("").strip();
                if (name.isEmpty()) {
                    return Parsed.fail("EMPTY_NAME");
                }
                items.add(RecommendationItem.builder()
                        .ingredient(name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name)
                        .reason(r.path("reason").asString(null))
                        .sortOrder(order++)
                        .amount(amount(r.path("amount")))
                        .unit(r.path("unit").isString() ? r.path("unit").asString() : null)
                        .build());
            }
            return new Parsed(items, root.path("summary").asString(null), null);
        } catch (JacksonException e) {
            return Parsed.fail("INVALID_JSON");
        }
    }

    // 숫자 또는 숫자 문자열 ("10")만 받는다
    private static BigDecimal amount(JsonNode node) {
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (!node.isString()) {
            return null;
        }
        try {
            return new BigDecimal(node.asString().strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
