package com.ucamp.app.domain.recommendation.cache;

import com.ucamp.app.domain.recommendation.dose.StandardsCache;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.rule.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 결과 캐시 키 = SHA-256(정렬한 "필드=값" 목록). 추천(LLM 출력)에 영향을 주는 값만 넣는다.
 * 사용자 ID, 응답 ID, 평가 실행 ID, 시각, 키·몸무게(프롬프트에 안 씀)는 넣지 않는다.
 * 현재 섭취는 성분 코드만 넣는다 (함량이 달라도 LLM이 고르는 성분은 같다고 보고, 용량은 매번 다시 계산한다).
 * 파이프라인·프롬프트 버전, 모델, 기준표·규칙 데이터 버전을 넣어 바뀌면 자동으로 새 키가 된다.
 */
@Component
@RequiredArgsConstructor
public class CacheKeyFactory {
    /** 키에 들어가는 필드 (순서 고정) */
    public static final List<String> KEY_FIELDS = List.of(
            "pipelineVersion", "promptVersion", "model", "standardsVersion", "rulesVersion",
            "respondentType", "gender", "age", "smoking", "drinking", "caffeine", "dietType", "sunExposure",
            "pregnancy", "conditions", "drugClasses", "unmatchedDrugs", "sleep", "fatigue", "goals",
            "intakeIngredients");

    private final StandardsCache standardsCache;
    private final RuleService ruleService;

    public String key(RecommendationInput in, String pipelineVersion, String promptVersion, String model) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("pipelineVersion", pipelineVersion);
        v.put("promptVersion", promptVersion);
        v.put("model", model);
        v.put("standardsVersion", standardsCache.dataVersion());
        v.put("rulesVersion", ruleService.dataVersion());
        v.put("respondentType", in.respondentType());
        v.put("gender", in.gender());
        v.put("age", in.age() == null ? null : String.valueOf(in.age()));
        v.put("smoking", in.smoking());
        v.put("drinking", in.drinking());
        v.put("caffeine", in.caffeine());
        v.put("dietType", in.dietType());
        v.put("sunExposure", in.sunExposure());
        v.put("pregnancy", in.pregnancy());
        v.put("conditions", sorted(in.conditionsOrEmpty()));
        v.put("drugClasses", sorted(in.medicationsOrEmpty().stream()
                .map(RecommendationInput.Medication::drugClass).filter(Objects::nonNull).toList()));
        v.put("unmatchedDrugs", sorted(in.medicationsOrEmpty().stream().filter(m -> m.drugClass() == null)
                .map(m -> m.name() == null ? "" : m.name().strip().toLowerCase()).toList()));
        v.put("sleep", in.sleep());
        v.put("fatigue", in.fatigue());
        v.put("goals", sorted(in.goalsOrEmpty()));
        v.put("intakeIngredients", sorted(in.currentIntakeOrEmpty().stream()
                .map(RecommendationInput.Intake::ingredient).distinct().toList()));
        String canonical = KEY_FIELDS.stream().map(f -> f + "=" + (v.get(f) == null ? "" : v.get(f)))
                .collect(Collectors.joining("|"));
        return Sha256.hex(canonical);
    }

    private static String sorted(List<String> values) {
        return values.stream().sorted().collect(Collectors.joining(","));
    }
}
