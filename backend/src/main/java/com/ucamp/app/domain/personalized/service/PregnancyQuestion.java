package com.ucamp.app.domain.personalized.service;

import com.ucamp.app.domain.personalized.planner.OptionKind;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;

import java.util.List;

/**
 * 임신·수유 고정 질문 (설계 D14). 19~50세 여성에게 회차 시작 시 서버가 낸다 (LLM 호출 없음, 질문 수에 세지 않음).
 * 답은 회차(ai_question_sessions.pregnancy)에만 저장하고, 정밀 추천의 임신 규칙(PREGNANCY_POSSIBLE 등)에 쓰인다.
 * 나이는 추천 입력과 같은 방식(올해 - 출생 연도)으로 계산한다.
 */
public final class PregnancyQuestion {
    public static final String TOPIC = "PREGNANCY";
    public static final String TEXT = "현재 임신 중이거나 수유 중이신가요?";
    public static final int MIN_AGE = 19;
    public static final int MAX_AGE = 50;
    public static final List<TurnOption> OPTIONS = List.of(
            new TurnOption("해당 없어요", OptionKind.NONE, "NOT_APPLICABLE"),
            new TurnOption("임신 가능성이 있어요", OptionKind.CONCERN, "POSSIBLE"),
            new TurnOption("임신 중이에요", OptionKind.CONCERN, "PREGNANT"),
            new TurnOption("수유 중이에요", OptionKind.CONCERN, "LACTATING"));

    private PregnancyQuestion() {
    }

    public static boolean applies(RecommendationInput profile) {
        Integer age = profile.age();
        return "F".equals(profile.gender()) && age != null && age >= MIN_AGE && age <= MAX_AGE;
    }
}
