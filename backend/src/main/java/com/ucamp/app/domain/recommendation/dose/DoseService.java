package com.ucamp.app.domain.recommendation.dose;

import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.rule.ConditionCodeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

// 추천 항목에 기준표 용량을 채운다 (V2부터). 계산은 DoseCalculator, 기준값은 StandardsCache
@Component
@RequiredArgsConstructor
public class DoseService {
    private final StandardsCache standardsCache;
    private final ConditionCodeResolver conditionCodeResolver;

    public List<RecommendationItem> applyDoses(RecommendationInput input, List<RecommendationItem> items) {
        Set<String> conditions = conditionCodeResolver.resolve(input);
        List<Standard> standards = standardsCache.standards();
        return items.stream().map(item -> {
            DoseCalculator.DoseResult dose = DoseCalculator.calculate(item.ingredient(), input.gender(), input.age(),
                    conditions, input.currentIntakeOrEmpty(), standards);
            return item.toBuilder()
                    .amount(dose.amount())
                    .unit(dose.unit())
                    .currentIntake(dose.currentIntake())
                    .intakeStatus(dose.intakeStatus())
                    .doseNote(dose.doseNote())
                    .build();
        }).toList();
    }

    public Boolean exceedsUpperLimit(RecommendationInput input, RecommendationItem item) {
        return DoseCalculator.exceedsUpperLimit(item.ingredient(), input.gender(), input.age(),
                conditionCodeResolver.resolve(input), input.currentIntakeOrEmpty(), standardsCache.standards(),
                item.amount(), item.unit());
    }
}
