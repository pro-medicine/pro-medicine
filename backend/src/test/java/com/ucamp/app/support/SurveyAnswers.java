package com.ucamp.app.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 설문 제출 요청의 답 한 개를 만드는 헬퍼
public final class SurveyAnswers {
    private SurveyAnswers() {
    }

    public static Map<String, Object> choice(long questionId, Long... optionIds) {
        Map<String, Object> m = new HashMap<>();
        m.put("questionId", questionId);
        m.put("optionIds", List.of(optionIds));
        return m;
    }

    public static Map<String, Object> number(long questionId, Number value) {
        return Map.of("questionId", questionId, "valueNumber", value);
    }

    public static Map<String, Object> text(long questionId, String value) {
        return Map.of("questionId", questionId, "valueText", value);
    }
}
