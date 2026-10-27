package com.ucamp.app.domain.personalized.question;

import com.ucamp.app.domain.personalized.planner.OptionKind;
import com.ucamp.app.domain.personalized.planner.Topic;
import com.ucamp.app.domain.personalized.question.PersonalizedQuestionValidator.Violation;
import com.ucamp.app.domain.recommendation.pipeline.InputFormatter;
import com.ucamp.app.domain.recommendation.pipeline.PromptTemplates;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.infra.llm.LlmCallException;
import com.ucamp.app.infra.llm.LlmClient;
import com.ucamp.app.infra.llm.LlmMessage;
import com.ucamp.app.infra.llm.LlmProperties;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.LlmResponse;
import com.ucamp.app.infra.llm.LlmUnavailableException;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 맞춤 질문 1개 생성 (설계 6장): 구조화 출력 → 서버 검증 → 실패 시 이유를 붙여 1회 재요청.
 * V1 추천 파이프라인(StructuredPipeline)과 같은 방식이다.
 * - 첫 호출 검증 실패: 그 로그 행 outcome = VALIDATION_FAIL, 재요청은 call_tag = PQ_QUESTION_REPAIR
 * - 재요청도 실패: 재요청 로그 행 outcome = PARSE_FAIL → QuestionGenerationException (PQ005)
 * 모델은 추천과 같은 모델(llm.model), 추론 강도 low, 출력 한도 500 토큰. DB 트랜잭션 밖에서 부른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalizedQuestionGenerator {
    public static final String PIPELINE_VERSION = "PQ";
    public static final String PROMPT_VERSION = "pq1.1";
    public static final String REASONING_EFFORT = "low";
    public static final int MAX_TOKENS = 500;
    public static final String MULTI_SUFFIX = "(최대 2개)";

    private final LlmClient llmClient;
    private final LlmCallLogService llmCallLogService;
    private final LlmProperties llmProperties;
    private final PromptTemplates promptTemplates;
    private final InputFormatter inputFormatter;
    private final PersonalizedQuestionValidator validator;
    private final MeterRegistry meterRegistry;

    /** 이 회차에서 이미 낸 질문과 고른 답 (프롬프트의 "이전 질문과 답") */
    public record PastQuestion(String question, List<String> selectedLabels) {
    }

    /**
     * @param known 프로필 기반 정보 (성별·나이·생활습관·질환·약·임신)
     */
    public record Context(Long sessionId, RecommendationInput known, Topic topic, int depth,
                          List<PastQuestion> history) {
    }

    public GeneratedQuestion generate(Context ctx) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            GeneratedQuestion q = generateOnce(ctx);
            count(q.repaired() ? "repair_success" : "success");
            return q;
        } catch (QuestionGenerationException e) {
            count("fail");
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("pq.question.latency"));
        }
    }

    private GeneratedQuestion generateOnce(Context ctx) {
        String system = promptTemplates.load("pq/system.txt");
        String user = userMessage(ctx);
        List<String> previous = ctx.history().stream().map(PastQuestion::question).toList();
        String answerType = ctx.topic().answerType();
        List<GeneratedQuestion.Attempt> attempts = new ArrayList<>();

        LlmResponse first = call(ctx, system, user, LlmRequest.TAG_PQ_QUESTION, attempts);
        List<Violation> errors = validator.validate(first.output(), answerType, previous);
        attempts.add(attempt(LlmRequest.TAG_PQ_QUESTION, first, errors));
        if (errors.isEmpty()) {
            return accepted(first.output(), answerType, first.callLogId(), attempts);
        }
        llmCallLogService.markOutcome(first.callLogId(), LlmCallLog.VALIDATION_FAIL);
        String repairUser = user + "\n\n" + promptTemplates.render("pq/repair.txt",
                Map.of("errors", PromptTemplates.bulletList(errors.stream().map(Violation::message).toList())));
        LlmResponse repair = call(ctx, system, repairUser, LlmRequest.TAG_PQ_QUESTION_REPAIR, attempts);
        List<Violation> repairErrors = validator.validate(repair.output(), answerType, previous);
        attempts.add(attempt(LlmRequest.TAG_PQ_QUESTION_REPAIR, repair, repairErrors));
        if (!repairErrors.isEmpty()) {
            llmCallLogService.markOutcome(repair.callLogId(), LlmCallLog.PARSE_FAIL);
            log.warn("personalized question failed after repair: session={}, topic={}, errors={}", ctx.sessionId(),
                    ctx.topic().code(), repairErrors.stream().map(Violation::code).toList());
            throw new QuestionGenerationException(QuestionGenerationException.VALIDATION_FAIL_AFTER_REPAIR, attempts, null);
        }
        return accepted(repair.output(), answerType, repair.callLogId(), attempts);
    }

    private LlmResponse call(Context ctx, String system, String user, String tag,
                             List<GeneratedQuestion.Attempt> attempts) {
        LlmRequest request = LlmRequest.builder()
                .model(llmProperties.getModel())
                .system(system)
                .messages(List.of(LlmMessage.user(user)))
                .maxTokens(MAX_TOKENS)
                .reasoningEffort(REASONING_EFFORT)
                .outputSchema(PersonalizedQuestionSchema.schema())
                .callTag(tag)
                .pipelineVersion(PIPELINE_VERSION)
                .promptVersion(PROMPT_VERSION)
                .sessionId(ctx.sessionId())
                .build();
        try {
            return llmClient.call(request);
        } catch (LlmUnavailableException e) {
            log.warn("personalized question LLM unavailable: reason={}, session={}", e.getReason(), ctx.sessionId());
            throw new QuestionGenerationException(QuestionGenerationException.LLM_UNAVAILABLE, attempts, e);
        } catch (LlmCallException e) {
            log.warn("personalized question LLM call failed: kind={}, status={}, session={}", e.getKind(),
                    e.getHttpStatus(), ctx.sessionId());
            throw new QuestionGenerationException(QuestionGenerationException.LLM_CALL_FAILED, attempts, e);
        }
    }

    String userMessage(Context ctx) {
        return promptTemplates.render("pq/user.txt", Map.of(
                "known", inputFormatter.formatKnown(ctx.known()),
                "topic", ctx.topic().code(),
                "topicLabel", TopicPurposes.label(ctx.topic()),
                "depth", String.valueOf(ctx.depth()),
                "maxDepth", String.valueOf(ctx.topic().maxDepth()),
                "answerType", ctx.topic().answerType(),
                "purpose", TopicPurposes.purpose(ctx.topic(), ctx.depth()),
                "history", history(ctx.history())));
    }

    static String history(List<PastQuestion> history) {
        if (history.isEmpty()) {
            return "없음 (이 회차의 첫 질문)";
        }
        return IntStream.range(0, history.size())
                .mapToObj(i -> (i + 1) + ". Q: " + history.get(i).question() + " / A: "
                        + String.join(", ", history.get(i).selectedLabels()))
                .collect(Collectors.joining("\n"));
    }

    // 검증을 통과한 출력 → 질문. MULTI인데 "(최대 2개)" 안내가 빠졌으면 붙인다 (설계 D4)
    static GeneratedQuestion accepted(JsonNode out, String answerType, Long callLogId,
                                      List<GeneratedQuestion.Attempt> attempts) {
        String question = out.get("question").asString().strip();
        if (Topic.MULTI.equals(answerType) && !question.contains("최대 2개")) {
            question = question + " " + MULTI_SUFFIX;
        }
        List<GeneratedQuestion.Option> options = new ArrayList<>();
        for (JsonNode o : out.get("options")) {
            options.add(new GeneratedQuestion.Option(o.get("label").asString().strip(),
                    OptionKind.valueOf(o.get("kind").asString())));
        }
        return new GeneratedQuestion(question, answerType, List.copyOf(options), callLogId, List.copyOf(attempts));
    }

    static GeneratedQuestion.Attempt attempt(String tag, LlmResponse r, List<Violation> violations) {
        return attempt(tag, r, r.output(), violations);
    }

    static GeneratedQuestion.Attempt attempt(String tag, LlmResponse r, JsonNode output, List<Violation> violations) {
        return new GeneratedQuestion.Attempt(tag, output, List.copyOf(violations), r.callLogId(), r.model(),
                r.latencyMs(), r.usage());
    }

    private void count(String outcome) {
        meterRegistry.counter("pq.question.generate", "outcome", outcome).increment();
    }
}
