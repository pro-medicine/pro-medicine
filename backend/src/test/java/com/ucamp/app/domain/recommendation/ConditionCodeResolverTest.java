package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.rule.ConditionCodeResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionCodeResolverTest {
    private final ConditionCodeResolver resolver = new ConditionCodeResolver();

    private static RecommendationInput.RecommendationInputBuilder member() {
        return RecommendationInput.builder().respondentType("MEMBER").gender("F").birthYear(1990);
    }

    @Test
    void 생활습관_매핑() {
        assertThat(resolver.resolve(member().smoking("HALF_PACK").drinking("HIGH").caffeine("HIGH")
                .dietType("VEGAN").sunExposure("LOW").build()))
                .containsExactlyInAnyOrder("SMOKING_CURRENT", "DRINKING_HIGH", "CAFFEINE_HIGH", "DIET_VEGAN", "SUN_LOW");
        assertThat(resolver.resolve(member().smoking("NONE").drinking("LOW").caffeine("LOW").dietType("VEGETARIAN")
                .sunExposure("NORMAL").build())).isEmpty();
    }

    @Test
    void 임신_매핑() {
        assertThat(resolver.resolve(member().pregnancy("POSSIBLE").build())).containsExactly("PREGNANCY_POSSIBLE");
        assertThat(resolver.resolve(member().pregnancy("PREGNANT").build())).containsExactly("PREGNANT_OR_LACTATING");
        assertThat(resolver.resolve(member().pregnancy("LACTATING").build())).containsExactly("PREGNANT_OR_LACTATING");
        assertThat(resolver.resolve(member().pregnancy("NOT_APPLICABLE").build())).isEmpty();
    }

    @Test
    void V2_성별_미공개면_임신상태를_단정하지_않고_예방_필터만_요청한다() {
        var input = member().gender(null).pregnancy(null).sessionId(100L).build();
        assertThat(resolver.resolve(input)).containsExactly(ConditionCodeResolver.PREGNANCY_NOT_ASSESSED);
        assertThat(resolver.resolve(member().gender("M").sessionId(100L).build())).isEmpty();
        assertThat(resolver.resolve(member().gender("F").sessionId(100L).build())).isEmpty();
        assertThat(resolver.resolve(member().gender(null).build())).isEmpty();
        assertThat(resolver.resolve(member().gender(null).sessionId(100L).pregnancy("PREGNANT").build()))
                .containsExactly(ConditionCodeResolver.PREGNANT_OR_LACTATING);
    }

    @Test
    void 진단_질환은_질환_코드_그대로_모르는_값은_무시() {
        assertThat(resolver.resolve(member().conditions(List.of("KIDNEY", "HYPERTENSION", "DIABETES", "THYROID", "NONE"))
                .build())).containsExactly("KIDNEY", "HYPERTENSION", "DIABETES", "THYROID");
    }

    @Test
    void 복용_약은_drug_class_그대로_마스터에_없는_약은_무시() {
        assertThat(resolver.resolve(member().medications(List.of(
                new RecommendationInput.Medication("D-003", "ACE_INHIBITOR", "에날라프릴"),
                new RecommendationInput.Medication(null, null, "기타 약"))).build()))
                .containsExactly("ACE_INHIBITOR");
    }

    @Test
    void 성인_남성_철분_섭취와_INGREDIENT_코드() {
        var iron = List.of(new RecommendationInput.Intake("IRON", BigDecimal.TEN, "mg"));
        assertThat(resolver.resolve(member().gender("M").currentIntake(iron).build()))
                .containsExactlyInAnyOrder("ADULT_MALE_TAKING_IRON", "IRON");
        assertThat(resolver.resolve(member().gender("F").currentIntake(iron).build())).containsExactly("IRON");
        int minorBirthYear = Year.now().getValue() - 17;
        assertThat(resolver.resolve(member().gender("M").birthYear(minorBirthYear).currentIntake(iron).build()))
                .containsExactly("IRON");
    }

    @Test
    void 비회원() {
        assertThat(resolver.resolve(RecommendationInput.builder().respondentType("GUEST").build()))
                .containsExactly("GUEST");
    }
}
