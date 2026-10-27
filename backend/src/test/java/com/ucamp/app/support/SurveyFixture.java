package com.ucamp.app.support;

// src/test/resources/sql/seed-survey-v1.sql 의 문항·선택지 ID (예시 데이터)
public final class SurveyFixture {
    public static final long SURVEY_ID = 1L;

    public static final long Q_GENDER = 1L;
    public static final long Q_BIRTH = 2L;
    public static final long Q_SMOKING = 3L;
    public static final long Q_DRINKING = 4L;
    public static final long Q_CAFFEINE = 5L;
    public static final long Q_DIET = 6L;
    public static final long Q_SUN = 7L;
    public static final long Q_SLEEP = 8L;
    public static final long Q_FATIGUE = 9L;
    public static final long Q_GOAL = 10L;
    public static final long Q_PREGNANCY = 11L;
    public static final long Q_CONDITIONS = 12L;
    public static final long Q_MEDICATIONS = 13L;
    public static final long Q_SUPPLEMENTS = 14L;

    public static final long GENDER_M = 1L;
    public static final long GENDER_F = 2L;
    public static final long SMOKING_NONE = 3L;
    public static final long SMOKING_HALF_PACK = 4L;
    public static final long SMOKING_ONE_PACK = 5L;
    public static final long DRINKING_NONE = 7L;
    public static final long DRINKING_HIGH = 9L;
    public static final long CAFFEINE_NONE = 10L;
    public static final long CAFFEINE_HIGH = 12L;
    public static final long DIET_OMNI = 13L;
    public static final long DIET_VEGAN = 15L;
    public static final long SUN_LOW = 16L;
    public static final long SUN_NORMAL = 17L;
    public static final long SLEEP_POOR = 20L;
    public static final long FATIGUE_HIGH = 23L;
    public static final long GOAL_SLEEP = 24L;
    public static final long GOAL_IMMUNITY = 26L;
    public static final long PREGNANCY_NA = 29L;
    public static final long PREGNANCY_POSSIBLE = 30L;
    public static final long CONDITION_NONE = 33L;
    public static final long CONDITION_HYPERTENSION = 34L;
    public static final long CONDITION_DIABETES = 35L;

    private SurveyFixture() {
    }
}
