package com.ucamp.app.domain.survey.service;

// 설문 v1 문항 code. 이후 STEP(추천 입력 조립, 조건 코드 매핑)이 이 값을 기준으로 동작한다
public final class SurveyCodes {
    public static final String GENDER = "Q-GENDER";
    public static final String BIRTH = "Q-BIRTH";
    public static final String SMOKING = "Q-SMOKING";
    public static final String DRINKING = "Q-DRINKING";
    public static final String CAFFEINE = "Q-CAFFEINE";
    public static final String DIET = "Q-DIET";
    public static final String SUN = "Q-SUN";
    public static final String SLEEP = "Q-SLEEP";
    public static final String FATIGUE = "Q-FATIGUE";
    public static final String GOAL = "Q-GOAL";
    public static final String PREGNANCY = "Q-PREGNANCY";
    public static final String CONDITIONS = "Q-CONDITIONS";
    public static final String MEDICATIONS = "Q-MEDICATIONS";
    public static final String SUPPLEMENTS = "Q-SUPPLEMENTS";

    public static final String PROFILE_KEY_CONDITIONS = "conditions";
    public static final String PROFILE_KEY_MEDICATIONS = "medications";
    public static final String NONE = "NONE";

    private SurveyCodes() {
    }
}
