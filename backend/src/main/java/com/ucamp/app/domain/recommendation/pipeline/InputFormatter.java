package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 정규화 입력을 LLM에 보낼 사람이 읽는 문장으로 바꾼다. 모든 버전이 같은 형식을 쓴다.
 * 같은 입력이면 항상 같은 문자열이 나오도록 순서를 고정한다 (재현성·캐시).
 */
@Component
public class InputFormatter {
    /** V2 정밀 추천의 현재 영양제 섭취 (묻지 않음). 섭취량을 모르면 서버가 기준 권장량으로 용량을 계산한다 */
    static final String UNKNOWN_INTAKE = "알 수 없음 (이 경우 서버가 기준 권장량으로 계산함)";
    private static final Map<String, String> SMOKING = Map.of("NONE", "안 피움", "HALF_PACK", "하루 반 갑 이하",
            "ONE_PACK", "하루 1갑", "TWO_PACK_PLUS", "하루 2갑 이상");
    private static final Map<String, String> LEVEL = Map.of("NONE", "안 함", "LOW", "적음", "HIGH", "많음",
            "NORMAL", "보통");
    private static final Map<String, String> DIET = Map.of("OMNI", "일반식", "VEGETARIAN", "채식(유제품·달걀 포함)",
            "VEGAN", "비건");
    private static final Map<String, String> SUN = Map.of("LOW", "거의 실내", "NORMAL", "보통");
    private static final Map<String, String> SLEEP = Map.of("GOOD", "좋음", "NORMAL", "보통", "POOR", "잘 못 잠");
    private static final Map<String, String> PREGNANCY = Map.of("NOT_APPLICABLE", "해당 없음", "POSSIBLE", "임신 가능성 있음",
            "PREGNANT", "임신 중", "LACTATING", "수유 중");
    private static final Map<String, String> CONDITION = Map.of("HYPERTENSION", "고혈압", "DIABETES", "당뇨",
            "THYROID", "갑상선 질환", "KIDNEY", "신장 질환");
    private static final Map<String, String> GOAL = Map.of("SLEEP", "수면", "FATIGUE", "피로 회복", "IMMUNITY", "면역",
            "BONE", "뼈 건강", "EYE", "눈 건강");

    public String format(RecommendationInput in) {
        List<String> lines = new ArrayList<>();
        lines.add("- 응답자: " + (in.isGuest() ? "비회원 (질환·복용 약·임신 여부는 묻지 않음)" : "회원"));
        if (in.gender() != null) {
            lines.add("- 성별: " + ("M".equals(in.gender()) ? "남성" : "F".equals(in.gender()) ? "여성" : in.gender()));
        }
        if (in.age() != null) {
            lines.add("- 나이: " + in.age() + "세");
        }
        add(lines, "흡연", in.smoking(), SMOKING);
        add(lines, "음주", in.drinking(), LEVEL);
        add(lines, "커피·차", in.caffeine(), LEVEL);
        add(lines, "식단", in.dietType(), DIET);
        add(lines, "햇빛 노출", in.sunExposure(), SUN);
        add(lines, "최근 수면", in.sleep(), SLEEP);
        add(lines, "최근 피로감", in.fatigue(), LEVEL);
        add(lines, "임신·수유", in.pregnancy(), PREGNANCY);
        if (!in.isGuest()) {
            lines.add("- 진단받은 질환: " + (in.conditionsOrEmpty().isEmpty() ? "없음"
                    : String.join(", ", in.conditionsOrEmpty().stream().map(c -> CONDITION.getOrDefault(c, c)).toList())));
            lines.add("- 복용 중인 약: " + (in.medicationsOrEmpty().isEmpty() ? "없음"
                    : String.join(", ", in.medicationsOrEmpty().stream().map(InputFormatter::medication).toList())));
        }
        if (!in.goalsOrEmpty().isEmpty()) {
            lines.add("- 원하는 효과: " + String.join(", ", in.goalsOrEmpty().stream().map(g -> GOAL.getOrDefault(g, g)).toList()));
        }
        // V2 정밀 추천·V4 대화창은 영양제 섭취를 묻지 않으므로 "알 수 없음". 보류 근거로 쓰지 않도록 서버 동작을 같이 알린다
        // (V1은 설문 답 그대로 "없음")
        lines.add("- 현재 복용 중인 영양제 성분(하루 함량): " + (in.currentIntakeOrEmpty().isEmpty()
                ? (in.isPersonalized() || in.chatSessionId() != null ? UNKNOWN_INTAKE : "없음")
                : String.join(", ", in.currentIntakeOrEmpty().stream()
                .map(i -> i.ingredient() + " " + i.amount().stripTrailingZeros().toPlainString() + " " + i.unit())
                .toList())));
        String text = String.join("\n", lines);
        // V2 정밀 추천만: 맞춤 질문 답변 블록 (설계 7-3). 답이 없으면 V1과 같은 문자열
        if (!in.personalizedAnswersOrEmpty().isEmpty()) {
            text += "\n\n## 맞춤 질문 답변\n" + String.join("\n", in.personalizedAnswersOrEmpty().stream()
                    .map(a -> "- [" + a.topicLabel() + "] " + a.question() + " → " + String.join(", ", a.selectedLabels()))
                    .toList());
        }
        return text;
    }

    /**
     * V2 맞춤 질문 생성용 "알고 있는 정보" (설계 6-2). 추천 입력 형식(format)과 따로 두어 V1 프롬프트에 영향이 없다.
     * 모르는 값은 "모름"으로 적어 LLM이 다시 물을 수 있게 한다.
     */
    public String formatKnown(RecommendationInput in) {
        List<String> lines = new ArrayList<>();
        String gender = "M".equals(in.gender()) ? "남성" : "F".equals(in.gender()) ? "여성" : "모름";
        lines.add("- 성별: " + gender + ", 나이: " + (in.age() == null ? "모름" : in.age() + "세"));
        lines.add("- 흡연: " + label(in.smoking(), SMOKING) + " / 음주: " + label(in.drinking(), LEVEL)
                + " / 커피·차: " + label(in.caffeine(), LEVEL) + " / 식단: " + label(in.dietType(), DIET)
                + " / 햇빛: " + label(in.sunExposure(), SUN));
        lines.add("- 진단받은 질환: " + (in.conditionsOrEmpty().isEmpty() ? "없음"
                : String.join(", ", in.conditionsOrEmpty().stream().map(c -> CONDITION.getOrDefault(c, c)).toList())));
        lines.add("- 복용 중인 약: " + (in.medicationsOrEmpty().isEmpty() ? "없음"
                : String.join(", ", in.medicationsOrEmpty().stream().map(InputFormatter::medicationName).toList())));
        lines.add("- 임신·수유: " + (in.pregnancy() == null ? "해당 없음" : label(in.pregnancy(), PREGNANCY)));
        return String.join("\n", lines);
    }

    private static String label(String code, Map<String, String> labels) {
        return code == null ? "모름" : labels.getOrDefault(code, code);
    }

    // 이름(계열). 약 마스터에 없는 약은 이름만
    private static String medicationName(RecommendationInput.Medication m) {
        if (m.drugClass() == null) {
            return m.name();
        }
        return (m.name() != null ? m.name() : "") + "(" + m.drugClass() + ")";
    }

    private static void add(List<String> lines, String label, String code, Map<String, String> labels) {
        if (code != null) {
            lines.add("- " + label + ": " + labels.getOrDefault(code, code));
        }
    }

    private static String medication(RecommendationInput.Medication m) {
        if (m.drugClass() != null) {
            return m.drugClass() + " 계열" + (m.name() != null ? "(" + m.name() + ")" : "");
        }
        return "기타 약(" + m.name() + ")";
    }
}
