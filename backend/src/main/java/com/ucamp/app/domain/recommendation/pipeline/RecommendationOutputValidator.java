package com.ucamp.app.domain.recommendation.pipeline;

import tools.jackson.databind.JsonNode;
import com.ucamp.app.domain.health.entity.IngredientCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * submit_recommendation 도구 입력 검증 (V1부터). 실패 이유는 재요청 메시지에 그대로 붙인다.
 * 스키마 위반, 알 수 없는 성분 코드, 중복 성분, priority 불연속(1부터 연속이 아님)을 잡는다.
 */
@Component
public class RecommendationOutputValidator {

    public List<String> validate(JsonNode input, boolean withDose) {
        List<String> errors = new ArrayList<>();
        if (input == null || input.isNull() || input.isMissingNode()) {
            errors.add("정해진 JSON 형식(submit_recommendation 스키마)으로 답하지 않았다");
            return errors;
        }
        if (!input.isObject()) {
            errors.add("도구 입력이 JSON 객체가 아니다");
            return errors;
        }
        JsonNode summary = input.get("summary");
        if (summary == null || !summary.isString()) {
            errors.add("summary(문자열)가 없다");
        }
        JsonNode recs = input.get("recommendations");
        if (recs == null || !recs.isArray()) {
            errors.add("recommendations(배열)가 없다");
            return errors;
        }
        Set<String> seen = new HashSet<>();
        Set<Integer> priorities = new TreeSet<>();
        int index = 0;
        for (JsonNode r : recs) {
            index++;
            String at = "recommendations[" + index + "]";
            if (!r.isObject()) {
                errors.add(at + "가 객체가 아니다");
                continue;
            }
            JsonNode ingredient = r.get("ingredient");
            if (ingredient == null || !ingredient.isString()) {
                errors.add(at + ".ingredient가 없다");
            } else if (IngredientCode.find(ingredient.asString()).isEmpty()) {
                errors.add(at + ".ingredient '" + ingredient.asString() + "'는 허용된 성분 코드가 아니다");
            } else if (!seen.add(ingredient.asString())) {
                errors.add(at + ".ingredient '" + ingredient.asString() + "'가 중복됐다");
            }
            JsonNode reason = r.get("reason");
            if (reason == null || !reason.isString() || reason.asString().isBlank()) {
                errors.add(at + ".reason이 없다");
            }
            JsonNode priority = r.get("priority");
            if (priority == null || !priority.canConvertToInt() || !priority.isIntegralNumber()) {
                errors.add(at + ".priority(정수)가 없다");
            } else if (!priorities.add(priority.asInt())) {
                errors.add(at + ".priority " + priority.asInt() + "가 중복됐다");
            }
            if (withDose) {
                JsonNode amount = r.get("amount");
                JsonNode unit = r.get("unit");
                if (amount != null && !amount.isNull() && (!amount.isNumber() || amount.decimalValue().signum() < 0)) {
                    errors.add(at + ".amount는 0 이상 숫자 또는 null이어야 한다");
                }
                if (unit != null && !unit.isNull() && !unit.isString()) {
                    errors.add(at + ".unit은 문자열 또는 null이어야 한다");
                }
                boolean hasAmount = amount != null && !amount.isNull();
                boolean hasUnit = unit != null && !unit.isNull();
                if (hasAmount != hasUnit) {
                    errors.add(at + ".amount와 unit은 함께 있거나 함께 null이어야 한다");
                }
            }
        }
        int expected = 1;
        for (int p : priorities) {
            if (p != expected) {
                errors.add("priority는 1부터 빠짐없이 연속이어야 한다 (받은 값: " + priorities + ")");
                break;
            }
            expected++;
        }
        return errors;
    }
}
