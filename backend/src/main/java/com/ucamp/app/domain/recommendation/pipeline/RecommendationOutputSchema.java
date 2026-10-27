package com.ucamp.app.domain.recommendation.pipeline;

import com.ucamp.app.domain.health.entity.IngredientCode;
import com.ucamp.app.infra.llm.LlmOutputSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 추천 결과 구조화 출력 스키마 (JSON Schema, strict). V1은 amount·unit 포함, V2부터는 뺀다.
 * strict 스키마는 additionalProperties=false와 모든 속성의 required가 필요하다 (OpenAI structured outputs 문서).
 * 이름 submit_recommendation은 프롬프트·검증 메시지에서 이 형식을 가리키는 말로 쓴다.
 */
public final class RecommendationOutputSchema {
    public static final String NAME = "submit_recommendation";

    private RecommendationOutputSchema() {
    }

    public static LlmOutputSchema schema(boolean withDose) {
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("ingredient", Map.of("type", "string",
                "enum", Arrays.stream(IngredientCode.values()).map(Enum::name).toList(),
                "description", "성분 코드"));
        itemProps.put("reason", Map.of("type", "string", "description", "이 사용자에게 필요한 이유 (한국어 한두 문장)"));
        itemProps.put("priority", Map.of("type", "integer", "description", "추천 순서. 1부터 빠짐없이 연속"));
        List<String> required = new ArrayList<>(List.of("ingredient", "reason", "priority"));
        if (withDose) {
            itemProps.put("amount", Map.of("type", List.of("number", "null"),
                    "description", "현재 복용 중인 영양제 함량을 뺀 하루 추가 섭취량. 모르면 null"));
            itemProps.put("unit", Map.of("type", List.of("string", "null"), "description", "amount의 단위. amount가 null이면 null"));
            required.add("amount");
            required.add("unit");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("additionalProperties", false);
        item.put("required", required);
        item.put("properties", itemProps);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("recommendations", Map.of("type", "array", "items", item));
        props.put("summary", Map.of("type", "string", "description", "추천 전체 요약 (한국어)"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("recommendations", "summary"));
        schema.put("properties", props);
        return new LlmOutputSchema(NAME, schema, true);
    }
}
