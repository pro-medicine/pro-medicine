package com.ucamp.app.domain.chat.parse;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ① 구조화 출력 검증 (enum·길이·필수값). 오류 문장은 재요청 프롬프트에 그대로 붙인다 (RecommendationOutputValidator와 같은 방식)
 */
public final class ChatParseValidator {
    public static final int QUESTION_MAX = 60;
    public static final int OPTION_MAX = 20;
    public static final int OPTIONS_MIN = 2;
    public static final int OPTIONS_MAX = 4;
    public static final int SEARCH_QUERY_MAX = 80;

    private ChatParseValidator() {
    }

    public record Result(ChatParse parse, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public static Result validate(JsonNode out, Collection<String> drugClasses, Collection<String> conditionCodes) {
        List<String> errors = new ArrayList<>();
        if (out == null || !out.isObject()) {
            return new Result(null, List.of("응답이 JSON 객체가 아니다"));
        }
        String redFlag = out.path("red_flag").isString() ? out.path("red_flag").asString() : null;
        if (redFlag == null || !ChatParseOutputSchema.RED_FLAGS.contains(redFlag)) {
            errors.add("red_flag는 NONE, EMERGENCY, CRISIS 중 하나여야 한다");
        }
        if (!out.path("off_topic").isBoolean()) {
            errors.add("off_topic은 true/false여야 한다");
        }
        if (!out.path("needs_clarification").isBoolean()) {
            errors.add("needs_clarification은 true/false여야 한다");
        }
        boolean offTopic = out.path("off_topic").asBoolean(false);
        boolean needs = out.path("needs_clarification").asBoolean(false);

        ChatParse.Clarification clarification = clarification(out.path("clarification"), needs, errors);

        String query = out.path("search_query").isString() ? out.path("search_query").asString().strip() : null;
        if (query == null) {
            errors.add("search_query는 문자열이어야 한다");
        } else if (query.length() > SEARCH_QUERY_MAX) {
            errors.add("search_query는 " + SEARCH_QUERY_MAX + "자 이내여야 한다 (현재 " + query.length() + "자)");
        } else if (query.isEmpty() && !offTopic && !needs && ChatParse.NONE.equals(redFlag)) {
            errors.add("검색할 수 있는 입력이면 search_query를 비우면 안 된다");
        }

        List<ChatParse.Medication> medications = new ArrayList<>();
        Set<String> classes = new LinkedHashSet<>(drugClasses);
        classes.add(DrugCatalog.UNKNOWN);
        JsonNode meds = out.path("medications");
        if (!meds.isArray()) {
            errors.add("medications는 배열이어야 한다");
        } else {
            for (JsonNode m : meds) {
                String name = m.path("name").isString() ? m.path("name").asString().strip() : "";
                String drugClass = m.path("drug_class").isString() ? m.path("drug_class").asString() : null;
                if (name.isEmpty()) {
                    errors.add("medications의 name이 비어 있다");
                    continue;
                }
                if (drugClass == null || !classes.contains(drugClass)) {
                    errors.add("medications의 drug_class '" + drugClass + "'는 목록에 없는 코드다 (모르면 UNKNOWN)");
                    continue;
                }
                medications.add(new ChatParse.Medication(name, drugClass));
            }
        }

        List<String> conditions = new ArrayList<>();
        JsonNode conds = out.path("conditions");
        if (!conds.isArray()) {
            errors.add("conditions는 배열이어야 한다");
        } else {
            for (JsonNode c : conds) {
                String code = c.isString() ? c.asString() : null;
                if (code == null || !conditionCodes.contains(code)) {
                    errors.add("conditions의 '" + code + "'는 목록에 없는 코드다");
                } else if (!conditions.contains(code)) {
                    conditions.add(code);
                }
            }
        }
        if (!errors.isEmpty()) {
            return new Result(null, List.copyOf(errors));
        }
        return new Result(new ChatParse(redFlag, offTopic, needs, clarification, query, List.copyOf(medications),
                List.copyOf(conditions)), List.of());
    }

    private static ChatParse.Clarification clarification(JsonNode node, boolean needs, List<String> errors) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            if (needs) {
                errors.add("needs_clarification이 true면 clarification에 질문과 선택지를 넣어야 한다");
            }
            return null;
        }
        if (!node.isObject()) {
            errors.add("clarification은 객체 또는 null이어야 한다");
            return null;
        }
        if (!needs) {
            // 되묻기가 필요 없다고 했으면 질문은 쓰지 않는다 (오류로 보지 않음)
            return null;
        }
        String question = node.path("question").isString() ? node.path("question").asString().strip() : "";
        if (question.isEmpty() || question.length() > QUESTION_MAX) {
            errors.add("clarification.question은 1~" + QUESTION_MAX + "자여야 한다");
        }
        List<String> options = new ArrayList<>();
        JsonNode opts = node.path("options");
        if (!opts.isArray()) {
            errors.add("clarification.options는 배열이어야 한다");
        } else {
            for (JsonNode o : opts) {
                String label = o.isString() ? o.asString().strip() : "";
                if (label.isEmpty() || label.length() > OPTION_MAX) {
                    errors.add("clarification.options의 각 선택지는 1~" + OPTION_MAX + "자여야 한다");
                } else if (!options.contains(label)) {
                    options.add(label);
                }
            }
            if (options.size() < OPTIONS_MIN || options.size() > OPTIONS_MAX) {
                errors.add("clarification.options는 서로 다른 선택지 " + OPTIONS_MIN + "~" + OPTIONS_MAX + "개여야 한다");
            }
        }
        return new ChatParse.Clarification(question, List.copyOf(options));
    }
}
