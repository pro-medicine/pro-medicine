package com.ucamp.app.domain.recommendation.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * LLM 응답·맞춤 질문에서 금지하는 표현의 단일 원본.
 * PersonalizedQuestionValidator(맞춤 질문)와 RecommendationQualityCheck(추천 요약)가 공유한다.
 */
public final class BannedWords {
    private BannedWords() {}

    /** 기본 금지어 (맞춤 질문 검증용) */
    public static final List<String> BASE = List.of("중단", "끊으세요", "진단", "처방", "복용을 멈추");

    /** 추천 요약 검증용 (BASE + 추가 표현, 중복 제거·순서 유지) */
    public static final List<String> QUALITY_CHECK;

    static {
        LinkedHashSet<String> set = new LinkedHashSet<>(BASE);
        set.add("진료");
        set.add("의료진");
        set.add("검사 결과");
        QUALITY_CHECK = List.copyOf(set);
    }
}
