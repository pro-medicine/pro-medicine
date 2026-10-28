package com.ucamp.app.domain.personalized.planner;

/**
 * 맞춤 질문 주제 (설계 5-1).
 *
 * @param code     기록·로그용 코드. CONCERN_OPEN, CONCERN:쉽게 피곤해요, MED:METFORMIN, CONDITION:HYPERTENSION, LIFESTYLE:SMOKING
 * @param subject  LLM에 넣을 대상. CONCERN은 사용자가 고른 라벨, MED는 약 이름(계열), CONDITION·LIFESTYLE은 코드
 * @param maxDepth 같은 주제로 이어서 물을 수 있는 최대 깊이
 */
public record Topic(Type type, String code, String subject, int maxDepth, String answerType) {
    public static final String SINGLE = "SINGLE";
    public static final String MULTI = "MULTI";
    public static final int CODE_LENGTH = 60;

    public enum Type {
        CONCERN_OPEN, CONCERN, MED, CONDITION, LIFESTYLE
    }

    public static final Topic CONCERN_OPEN = new Topic(Type.CONCERN_OPEN, "CONCERN_OPEN", null, 1, MULTI);

    public static Topic concern(String label) {
        return new Topic(Type.CONCERN, code("CONCERN:", label), label, 2, SINGLE);
    }

    /** 계열이 있으면 MED:{계열}, 약 마스터에 없는 약이면 MED:{이름} (LLM에는 이름만) */
    public static Topic medication(String drugClass, String name) {
        String key = drugClass != null ? drugClass : name;
        String subject = drugClass == null ? name : (name == null ? drugClass : name + "(" + drugClass + ")");
        return new Topic(Type.MED, code("MED:", key), subject, 2, SINGLE);
    }

    public static Topic condition(String conditionCode) {
        return new Topic(Type.CONDITION, code("CONDITION:", conditionCode), conditionCode, 1, SINGLE);
    }

    public static Topic lifestyle(String habit) {
        return new Topic(Type.LIFESTYLE, code("LIFESTYLE:", habit), habit, 1, SINGLE);
    }

    private static String code(String prefix, String key) {
        String code = prefix + (key == null ? "" : key.strip());
        return code.length() <= CODE_LENGTH ? code : code.substring(0, CODE_LENGTH);
    }
}
