package com.ucamp.app.domain.personalized.question;

import com.ucamp.app.domain.personalized.planner.Topic;

import java.util.Map;

/**
 * 주제별 한글 설명과 "목적 문장" (설계 6-2, 서버 상수). LLM에 이번 질문으로 무엇을 알아내야 하는지 알려준다.
 * 약물 계열별 목적은 설계에 예시가 있는 것(METFORMIN)만 따로 두고, 나머지는 공통 문장을 쓴다
 * (영양 관련 판단은 규칙 테이블이 하므로 여기서 상호작용을 새로 주장하지 않는다).
 */
public final class TopicPurposes {
    private static final Map<String, String> MED = Map.of(
            "METFORMIN", "복용 기간과 비타민 B12 관련 신호 확인");
    private static final String MED_DEFAULT = "복용 기간, 복용 시간, 영양제를 함께 먹는지 확인";
    private static final Map<String, String> LIFESTYLE = Map.of(
            "SMOKING", "흡연량과 기간 확인",
            "DRINKING", "음주 빈도와 양 확인",
            "CAFFEINE", "커피·차를 마시는 양과 시간 확인",
            "DIET", "식단에서 빠지기 쉬운 식품군 확인",
            "SUN", "햇빛을 받는 시간 확인");
    private static final Map<String, String> LIFESTYLE_LABEL = Map.of(
            "SMOKING", "흡연", "DRINKING", "음주", "CAFFEINE", "커피·차", "DIET", "식단", "SUN", "햇빛 노출");
    private static final Map<String, String> CONDITION_LABEL = Map.of("HYPERTENSION", "고혈압", "DIABETES", "당뇨",
            "THYROID", "갑상선 질환", "KIDNEY", "신장 질환");

    private TopicPurposes() {
    }

    /** topic 코드 옆 괄호에 들어갈 한글 설명 */
    public static String label(Topic topic) {
        return switch (topic.type()) {
            case CONCERN_OPEN -> "불편한 점";
            case CONCERN -> "사용자가 고른 불편한 점: " + topic.subject();
            case MED -> "복용 중인 약: " + topic.subject();
            case CONDITION -> "진단받은 질환: " + CONDITION_LABEL.getOrDefault(topic.subject(), topic.subject());
            case LIFESTYLE -> "생활습관: " + LIFESTYLE_LABEL.getOrDefault(topic.subject(), topic.subject());
        };
    }

    public static String purpose(Topic topic, int depth) {
        return switch (topic.type()) {
            case CONCERN_OPEN -> "사용자가 불편하게 느끼는 증상 범위 파악";
            case CONCERN -> depth <= 1 ? "고른 불편한 점이 언제, 얼마나 자주, 얼마나 오래 있는지 확인"
                    : "고른 불편한 점과 함께 나타나는 영양 관련 신호 확인";
            case MED -> topic.code().startsWith("MED:") ? MED.getOrDefault(topic.code().substring(4), MED_DEFAULT) : MED_DEFAULT;
            case CONDITION -> "질환 관리 상태와 영양 관련 신호 확인";
            case LIFESTYLE -> LIFESTYLE.getOrDefault(topic.subject(), "생활습관 확인");
        };
    }
}
