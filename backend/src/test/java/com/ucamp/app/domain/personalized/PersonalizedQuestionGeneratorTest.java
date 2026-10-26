package com.ucamp.app.domain.personalized;

import com.ucamp.app.domain.personalized.planner.Topic;
import com.ucamp.app.domain.personalized.question.GeneratedQuestion;
import com.ucamp.app.domain.personalized.question.PersonalizedQuestionGenerator;
import com.ucamp.app.domain.personalized.question.PersonalizedQuestionGenerator.Context;
import com.ucamp.app.domain.personalized.question.PersonalizedQuestionGenerator.PastQuestion;
import com.ucamp.app.domain.personalized.question.QuestionGenerationException;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.LlmCallException;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 설계 6장: Fake LLM으로 질문 생성·재요청·실패, call_tag·session_id 기록
@SpringBootTest
class PersonalizedQuestionGeneratorTest {
    @Autowired
    PersonalizedQuestionGenerator generator;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    LlmCallLogRepository logRepository;
    @Autowired
    ObjectMapper objectMapper;

    private static final RecommendationInput KNOWN = RecommendationInput.builder()
            .respondentType(RecommendationInput.MEMBER).gender("M").birthYear(1980).smoking("HALF_PACK")
            .conditions(List.of("HYPERTENSION"))
            .medications(List.of(new RecommendationInput.Medication("D-001", "METFORMIN", "메트포르민"))).build();

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 첫_시도에_통과하면_PQ_QUESTION_한_번_low_500_session_id_기록() {
        GeneratedQuestion q = generator.generate(new Context(9001L, KNOWN, Topic.CONCERN_OPEN, 1, List.of()));

        assertThat(q.answerType()).isEqualTo("MULTI");
        assertThat(q.question()).contains("최대 2개");
        assertThat(q.options()).hasSizeBetween(2, 5);
        assertThat(q.repaired()).isFalse();
        LlmRequest request = fake.requests().get(0);
        assertThat(request.callTag()).isEqualTo(LlmRequest.TAG_PQ_QUESTION);
        assertThat(request.reasoningEffort()).isEqualTo("low");
        assertThat(request.maxTokens()).isEqualTo(500);
        assertThat(request.messages().get(0).text()).contains("- topic: CONCERN_OPEN (불편한 점)")
                .contains("- answerType: MULTI").contains("메트포르민(METFORMIN)").contains("없음 (이 회차의 첫 질문)");
        LlmCallLog log = logRepository.findById(q.callLogId()).orElseThrow();
        assertThat(log.getSessionId()).isEqualTo(9001L);
        assertThat(log.getCallTag()).isEqualTo("PQ_QUESTION");
        assertThat(log.getResponseId()).isNull();
    }

    @Test
    void 검증_실패면_이유를_붙여_1회_재요청하고_통과() {
        AtomicInteger n = new AtomicInteger();
        fake.setResponder(req -> n.getAndIncrement() == 0
                ? fake.structuredResponse(req, objectMapper.createObjectNode().put("question", "q")
                .put("answerType", "SINGLE").set("options", objectMapper.createArrayNode()))
                : fake.render(req, null));

        GeneratedQuestion q = generator.generate(new Context(9002L, KNOWN, Topic.medication("METFORMIN", "메트포르민"), 1,
                List.of(new PastQuestion("요즘 불편한 점을 골라주세요", List.of("쉽게 피곤해요")))));

        assertThat(q.repaired()).isTrue();
        assertThat(q.attempts()).extracting(GeneratedQuestion.Attempt::callTag)
                .containsExactly("PQ_QUESTION", "PQ_QUESTION_REPAIR");
        assertThat(q.attempts().get(0).violations()).isNotEmpty();
        assertThat(fake.requests().get(1).messages().get(0).text()).contains("검증에 실패했다")
                .contains("1. Q: 요즘 불편한 점을 골라주세요 / A: 쉽게 피곤해요");
        assertThat(logRepository.findById(q.attempts().get(0).callLogId()).orElseThrow().getOutcome())
                .isEqualTo(LlmCallLog.VALIDATION_FAIL);
    }

    @Test
    void 재요청도_실패하면_예외_재요청_로그는_PARSE_FAIL() {
        fake.setResponder(req -> fake.structuredResponse(req, objectMapper.createObjectNode().put("x", 1)));

        assertThatThrownBy(() -> generator.generate(new Context(9003L, KNOWN, Topic.lifestyle("SMOKING"), 1, List.of())))
                .isInstanceOfSatisfying(QuestionGenerationException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(QuestionGenerationException.VALIDATION_FAIL_AFTER_REPAIR);
                    assertThat(e.getAttempts()).hasSize(2);
                    assertThat(logRepository.findById(e.getAttempts().get(1).callLogId()).orElseThrow().getOutcome())
                            .isEqualTo(LlmCallLog.PARSE_FAIL);
                });
    }

    @Test
    void LLM_호출_실패도_질문_생성_실패() {
        fake.setResponder(req -> {
            throw new LlmCallException(LlmCallException.Kind.HTTP_ERROR, 500, null, 0, "boom", null);
        });
        assertThatThrownBy(() -> generator.generate(new Context(9004L, KNOWN, Topic.CONCERN_OPEN, 1, List.of())))
                .isInstanceOfSatisfying(QuestionGenerationException.class,
                        e -> assertThat(e.getReason()).isEqualTo(QuestionGenerationException.LLM_CALL_FAILED));
    }
}
