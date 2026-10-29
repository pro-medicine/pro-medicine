package com.ucamp.app.domain.personalized.question;

import com.ucamp.app.domain.personalized.planner.OptionKind;
import com.ucamp.app.domain.personalized.question.PersonalizedQuestionValidator.Violation;
import com.ucamp.app.infra.llm.LlmUsage;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 검증을 통과한 맞춤 질문.
 *
 * @param callLogId 통과한 호출의 llm_call_logs 행 (ai_question_turns.llm_call_log_id)
 * @param attempts  첫 호출과 재요청의 원본·검증 결과 (측정용 기록. 운영 흐름은 쓰지 않는다)
 */
public record GeneratedQuestion(String question, String answerType, List<Option> options, Long callLogId,
                                List<Attempt> attempts) {

    public record Option(String label, OptionKind kind) {
    }

    /** LLM 호출 한 번. output은 검증 전 원본 (파싱 실패면 null) */
    public record Attempt(String callTag, JsonNode output, List<Violation> violations, Long callLogId, String model,
                          long latencyMs, LlmUsage usage) {
    }

    public boolean repaired() {
        return attempts.size() > 1;
    }
}
