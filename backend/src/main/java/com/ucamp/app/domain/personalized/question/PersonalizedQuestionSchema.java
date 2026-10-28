package com.ucamp.app.domain.personalized.question;

import com.ucamp.app.infra.llm.LlmOutputSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 맞춤 질문 구조화 출력 스키마 (설계 6-3, json_schema strict).
 * 개수·길이 제한은 strict 스키마로 보장되지 않을 수 있어 PersonalizedQuestionValidator가 따로 막는다.
 */
public final class PersonalizedQuestionSchema {
    public static final String NAME = "submit_question";

    private static final LlmOutputSchema SCHEMA = build();

    private PersonalizedQuestionSchema() {
    }

    public static LlmOutputSchema schema() {
        return SCHEMA;
    }

    private static LlmOutputSchema build() {
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
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("question", "answerType", "options"));
        schema.put("properties", props);
        return new LlmOutputSchema(NAME, schema, true);
    }
}
