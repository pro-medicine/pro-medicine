package com.ucamp.app.domain.chat.parse;

import com.ucamp.app.infra.llm.LlmOutputSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ① 대화 문장 구조화 출력 스키마 (JSON Schema, strict, 설계 4-1). RecommendationOutputSchema와 같은 방식:
 * strict라 모든 속성이 required이고 additionalProperties=false. 길이 제한은 스키마 대신 ChatParseValidator가 검사한다.
 * drug_class 후보는 drugs 테이블에서 읽어 넣으므로 요청마다 만든다
 */
public final class ChatParseOutputSchema {
    public static final String NAME = "submit_chat_parse";
    public static final List<String> RED_FLAGS = List.of("NONE", "EMERGENCY", "CRISIS");

    private ChatParseOutputSchema() {
    }

    public static LlmOutputSchema schema(Collection<String> drugClasses, Collection<String> conditionCodes) {
        Map<String, Object> clarificationProps = new LinkedHashMap<>();
        clarificationProps.put("question", Map.of("type", "string", "description", "객관식 질문 1개 (60자 이내)"));
        clarificationProps.put("options", Map.of("type", "array", "items", Map.of("type", "string"),
                "description", "선택지 2~4개 (각 20자 이내)"));
        Map<String, Object> clarification = new LinkedHashMap<>();
        clarification.put("type", "object");
        clarification.put("additionalProperties", false);
        clarification.put("required", List.of("question", "options"));
        clarification.put("properties", clarificationProps);

        List<String> classes = new ArrayList<>(drugClasses);
        classes.add(DrugCatalog.UNKNOWN);
        Map<String, Object> medProps = new LinkedHashMap<>();
        medProps.put("name", Map.of("type", "string", "description", "문장에 나온 약 이름 그대로"));
        medProps.put("drug_class", Map.of("type", "string", "enum", classes,
                "description", "약물 계열 코드. 확실하지 않으면 UNKNOWN"));
        Map<String, Object> medication = new LinkedHashMap<>();
        medication.put("type", "object");
        medication.put("additionalProperties", false);
        medication.put("required", List.of("name", "drug_class"));
        medication.put("properties", medProps);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("red_flag", Map.of("type", "string", "enum", RED_FLAGS,
                "description", "EMERGENCY = 흉통·호흡곤란·의식 저하 등 응급, CRISIS = 자해·자살 등 위기, 없으면 NONE"));
        props.put("off_topic", Map.of("type", "boolean", "description", "건강·영양제와 무관한 입력이면 true"));
        props.put("needs_clarification", Map.of("type", "boolean",
                "description", "증상이 너무 막연해서 검색 문장을 만들 수 없으면 true"));
        props.put("clarification", Map.of("anyOf", List.of(clarification, Map.of("type", "null")),
                "description", "needs_clarification이 true일 때만 질문, 아니면 null"));
        props.put("search_query", Map.of("type", "string",
                "description", "증상·대상 중심 한 문장 (80자 이내). 검색할 수 없으면 빈 문자열"));
        props.put("medications", Map.of("type", "array", "items", medication));
        props.put("conditions", Map.of("type", "array",
                "items", Map.of("type", "string", "enum", List.copyOf(conditionCodes))));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("red_flag", "off_topic", "needs_clarification", "clarification",
                "search_query", "medications", "conditions"));
        schema.put("properties", props);
        return new LlmOutputSchema(NAME, schema, true);
    }
}
