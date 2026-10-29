package com.ucamp.app.domain.personalized.question;

import com.ucamp.app.infra.llm.LlmOutputSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 맞춤 질문 묶음 출력 스키마 (질문 미리 만들기). 질문마다 id(요청한 주제 번호)와 submit_question과 같은 필드.
 * withFollowup이면 "CONCERN을 고르면 이어질 2단계 질문"(followup, 없으면 null)도 받는다.
 */
public final class QuestionBatchSchema {
    public static final String NAME = "submit_questions";

    private static final LlmOutputSchema PLAIN = build(false);
    private static final LlmOutputSchema WITH_FOLLOWUP = build(true);

    private QuestionBatchSchema() {
    }

    public static LlmOutputSchema schema(boolean withFollowup) {
        return withFollowup ? WITH_FOLLOWUP : PLAIN;
    }

    private static Map<String, Object> question() {
        Map<String, Object> optionProps = new LinkedHashMap<>();
        optionProps.put("label", Map.of("type", "string"));
        optionProps.put("kind", Map.of("type", "string", "enum", List.of("NONE", "UNKNOWN", "NORMAL", "CONCERN")));
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("type", "object");
        option.put("additionalProperties", false);
        option.put("required", List.of("label", "kind"));
        option.put("properties", optionProps);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("question", Map.of("type", "string"));
        props.put("answerType", Map.of("type", "string", "enum", List.of("SINGLE", "MULTI")));
        props.put("options", Map.of("type", "array", "items", option));
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("type", "object");
        q.put("additionalProperties", false);
        q.put("required", List.of("question", "answerType", "options"));
        q.put("properties", props);
        return q;
    }

    private static LlmOutputSchema build(boolean withFollowup) {
        Map<String, Object> q = question();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = new LinkedHashMap<>((Map<String, Object>) q.get("properties"));
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("id", Map.of("type", "integer"));
        itemProps.putAll(props);
        List<String> required = new java.util.ArrayList<>(List.of("id", "question", "answerType", "options"));
        if (withFollowup) {
            itemProps.put("followup", Map.of("anyOf", List.of(question(), Map.of("type", "null"))));
            required.add("followup");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("additionalProperties", false);
        item.put("required", required);
        item.put("properties", itemProps);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("questions"));
        schema.put("properties", Map.of("questions", Map.of("type", "array", "items", item)));
        return new LlmOutputSchema(NAME, schema, true);
    }
}
