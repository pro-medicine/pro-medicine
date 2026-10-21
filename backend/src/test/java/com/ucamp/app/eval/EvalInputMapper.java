package com.ucamp.app.eval;

import com.ucamp.app.domain.health.entity.Drug;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.eval.EvalCaseFile.EvalCase;
import com.ucamp.app.eval.EvalCaseFile.Input;

import java.util.List;
import java.util.Map;

// 평가 케이스 → 정규화 입력 (HTTP·DB를 거치지 않고 파이프라인에 바로 넣는다)
public final class EvalInputMapper {
    private EvalInputMapper() {
    }

    public static RecommendationInput toInput(EvalCase c, Map<String, Drug> drugs, String evalRunId) {
        Input in = c.input();
        Map<String, Object> p = in.profile();
        Map<String, Object> a = in.answers() == null ? Map.of() : in.answers();
        boolean guest = RecommendationInput.GUEST.equals(in.respondentType());
        return RecommendationInput.builder()
                .respondentType(in.respondentType())
                .gender(str(p.get("gender")))
                .birthYear(p.get("birthYear") == null ? null : ((Number) p.get("birthYear")).intValue())
                .smoking(str(p.get("smoking")))
                .drinking(str(p.get("drinking")))
                .caffeine(str(p.get("caffeine")))
                .dietType(str(p.get("dietType")))
                .sunExposure(str(p.get("sunExposure")))
                .pregnancy(guest ? null : str(a.get("Q-PREGNANCY")))
                .sleep(str(a.get("Q-SLEEP")))
                .fatigue(str(a.get("Q-FATIGUE")))
                .goals(goals(a.get("Q-GOAL")))
                .conditions(guest || in.conditions() == null ? List.of() : in.conditions())
                .medications(guest || in.medications() == null ? List.of() : in.medications().stream()
                        .map(m -> {
                            Drug d = m.drugId() == null ? null : drugs.get(m.drugId());
                            if (m.drugId() != null && d == null) {
                                throw new IllegalArgumentException(c.caseId() + ": unknown drugId " + m.drugId());
                            }
                            return d == null ? new RecommendationInput.Medication(null, null, m.name())
                                    : new RecommendationInput.Medication(d.getDrugId(), d.getDrugClass(), d.getName());
                        }).toList())
                .currentIntake(in.currentIntake() == null ? List.of() : in.currentIntake().stream()
                        .map(i -> new RecommendationInput.Intake(i.ingredient(), i.amount(), i.unit())).toList())
                .evalRunId(evalRunId)
                .build();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> goals(Object o) {
        if (o == null) {
            return List.of();
        }
        if (o instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of(o.toString());
    }
}
