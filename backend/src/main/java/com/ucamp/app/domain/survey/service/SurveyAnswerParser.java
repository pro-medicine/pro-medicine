package com.ucamp.app.domain.survey.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.health.dto.MedicationEntry;
import com.ucamp.app.domain.health.entity.IngredientCode;
import com.ucamp.app.domain.survey.dto.SupplementIntake;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// JSON 형식이 정해진 TEXT 문항(Q-MEDICATIONS, Q-SUPPLEMENTS) 해석. 형식이 틀리면 400
@Component
@RequiredArgsConstructor
public class SurveyAnswerParser {
    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_UNIT_LENGTH = 20;

    private final ObjectMapper objectMapper;

    // [{"drugId":"D-001"},{"name":"마스터에 없는 약"}]. drugId 존재 여부는 호출한 쪽에서 확인
    public List<MedicationEntry> parseMedications(String json) {
        JsonNode root = readArray(json);
        List<MedicationEntry> result = new ArrayList<>();
        for (JsonNode node : root) {
            if (!node.isObject()) {
                throw invalid();
            }
            String drugId = text(node, "drugId");
            String name = text(node, "name");
            if ((drugId == null) == (name == null)) {
                throw invalid();
            }
            if (name != null && name.length() > MAX_NAME_LENGTH) {
                throw invalid();
            }
            result.add(new MedicationEntry(drugId, name));
        }
        return result;
    }

    // [{"ingredient":"MAGNESIUM","amount":30,"unit":"mg"}]. ingredient는 성분 코드
    public List<SupplementIntake> parseSupplements(String json) {
        JsonNode root = readArray(json);
        List<SupplementIntake> result = new ArrayList<>();
        for (JsonNode node : root) {
            if (!node.isObject()) {
                throw invalid();
            }
            String ingredient = text(node, "ingredient");
            JsonNode amountNode = node.get("amount");
            String unit = text(node, "unit");
            if (ingredient == null || IngredientCode.find(ingredient).isEmpty()) {
                throw invalid();
            }
            if (amountNode == null || !amountNode.isNumber() || amountNode.decimalValue().signum() < 0) {
                throw invalid();
            }
            if (unit == null || unit.length() > MAX_UNIT_LENGTH) {
                throw invalid();
            }
            BigDecimal amount = amountNode.decimalValue();
            result.add(new SupplementIntake(ingredient, amount, unit));
        }
        return result;
    }

    private JsonNode readArray(String json) {
        if (json == null || json.isBlank()) {
            throw invalid();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                throw invalid();
            }
            return root;
        } catch (tools.jackson.core.JacksonException e) {
            throw invalid();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isString() || v.asString().isBlank()) {
            throw invalid();
        }
        return v.asString().strip();
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.SURVEY_INVALID_ANSWER);
    }
}
