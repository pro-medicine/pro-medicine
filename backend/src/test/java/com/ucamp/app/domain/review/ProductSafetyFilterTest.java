package com.ucamp.app.domain.review;

import com.ucamp.app.domain.recommendation.rule.Rule;
import com.ucamp.app.domain.recommendation.rule.RuleEngine;
import com.ucamp.app.domain.review.safety.ProductSafetyFilter;
import com.ucamp.app.domain.review.safety.ProductSafetyFilter.Reason;
import com.ucamp.app.domain.review.safety.ProductSafetyFilter.Result;
import com.ucamp.app.domain.review.safety.ProductSafetyFilter.UserConditions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제품 안전 필터 순수 단위 테스트 (Spring 없음). 규칙은 seed-recommendation-rules.sql의 해당 행을 그대로 옮겼다.
 * 조건 코드는 ConditionCodeResolver 결과 형식 (약 계열·질환·상태·현재 섭취 성분 코드·GUEST)
 */
class ProductSafetyFilterTest {
    static final String R16_MSG = "와파린 복용 중에는 비타민 K 섭취량을 일정하게 유지하세요";
    static final String R21_MSG = "철분과 칼슘은 다른 시간대에 드세요";
    static final String R22_MSG = "고용량 철분은 아연 흡수를 줄일 수 있어요";
    static final String RK02_MSG = "신장 기능이 떨어져 있으면 마그네슘이 몸에 쌓일 수 있어요. 의사·약사와 상담하세요";

    static final List<Rule> RULES = List.of(
            new Rule("R-16", "DRUG", "WARFARIN", "VITAMIN_K", "WARN", R16_MSG, false, "S-15"),
            new Rule("R-17", "DRUG", "LEVOTHYROXINE", "IRON", "TIMING", "갑상선약과 철분은 시간 간격을 두고 드세요", false, "S-16"),
            new Rule("R-21", "INGREDIENT", "IRON", "CALCIUM", "TIMING", R21_MSG, false, "S-16"),
            new Rule("R-22", "INGREDIENT", "IRON", "ZINC", "WARN", R22_MSG, false, "S-16"),
            new Rule("R-23", "STATUS", "ADULT_MALE_TAKING_IRON", "IRON", "ASK", "철분제를 드시는 이유(처방 여부)를 확인해 주세요", false, "S-17"),
            new Rule("R-38", "STATUS", "PREGNANT_OR_LACTATING", "LUTEIN", "EXCLUDE", "임신·수유 중에는 루테인 보충제를 피하세요", false, "S-30"),
            new Rule("R-K02", "DISEASE", "KIDNEY", "MAGNESIUM", "WARN", RK02_MSG, false, "S-20"),
            new Rule("R-G6", "STATUS", "GUEST", "COQ10", "EXCLUDE", "임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요", true, "S-30"));

    final ProductSafetyFilter filter = new ProductSafetyFilter(new RuleEngine(RULES));

    static UserConditions member(Set<String> conditions, boolean hasMedicationOrDisease, String... recommended) {
        return new UserConditions(conditions, false, hasMedicationOrDisease, List.of(recommended));
    }

    @Test
    void 와파린_D3_K2_제품은_같이_든_비타민K_때문에_제외() {
        Result r = filter.check("VITAMIN_D", List.of("VITAMIN_D", "VITAMIN_K"), member(Set.of("WARFARIN"), true, "VITAMIN_D"));

        assertThat(r.excluded()).isTrue();
        assertThat(r.reason()).isEqualTo(Reason.RULE);
    }

    @Test
    void 와파린_비타민K_제품에서_찾는_성분이_K면_경고와_감점() {
        Result r = filter.check("VITAMIN_K", List.of("VITAMIN_K"), member(Set.of("WARFARIN"), true, "VITAMIN_K"));

        assertThat(r.excluded()).isFalse();
        assertThat(r.targetWarn()).isTrue();
        assertThat(r.warnings()).containsExactly(R16_MSG);
    }

    @Test
    void 신장_질환_마그네슘_제품은_경고() {
        Result r = filter.check("MAGNESIUM", List.of("MAGNESIUM"), member(Set.of("KIDNEY"), true, "MAGNESIUM"));

        assertThat(r.excluded()).isFalse();
        assertThat(r.targetWarn()).isTrue();
        assertThat(r.warnings()).containsExactly(RK02_MSG);
    }

    @Test
    void 임신_수유_오메가3_루테인_제품은_같이_든_루테인_때문에_제외() {
        Result r = filter.check("OMEGA3", List.of("OMEGA3", "LUTEIN"),
                member(Set.of("PREGNANT_OR_LACTATING"), false, "OMEGA3"));

        assertThat(r.excluded()).isTrue();
        assertThat(r.reason()).isEqualTo(Reason.RULE);
    }

    @Test
    void 철분_칼슘_복합_제품은_제품_안_조합이라_R21_무시하고_경고_없이_통과() {
        Result r = filter.check("IRON", List.of("IRON", "CALCIUM"), member(Set.of(), false, "IRON"));

        assertThat(r.excluded()).isFalse();
        assertThat(r.targetWarn()).isFalse();
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void 철분_추천_아연_제품은_통과하고_R22_경고만_감점_없음() {
        Result r = filter.check("ZINC", List.of("ZINC"), member(Set.of(), false, "IRON", "ZINC"));

        assertThat(r.excluded()).isFalse();
        assertThat(r.targetWarn()).isFalse();
        assertThat(r.warnings()).containsExactly(R22_MSG);
    }

    @Test
    void 철분_섭취_중_아연이_같이_든_제품도_제외하지_않고_경고만() {
        // 현재 섭취 성분은 조건 코드(IRON)로 들어온다. 같이 든 성분이지만 INGREDIENT 규칙이라 제외하지 않는다
        Result r = filter.check("MAGNESIUM", List.of("MAGNESIUM", "ZINC"), member(Set.of("IRON"), false, "MAGNESIUM"));

        assertThat(r.excluded()).isFalse();
        assertThat(r.targetWarn()).isFalse();
        assertThat(r.warnings()).containsExactly(R22_MSG);
    }

    @Test
    void 철분_추천_칼슘_제품은_INGREDIENT_TIMING도_경고만() {
        Result r = filter.check("CALCIUM", List.of("CALCIUM"), member(Set.of(), false, "IRON", "CALCIUM"));

        assertThat(r.excluded()).isFalse();
        assertThat(r.warnings()).containsExactly(R21_MSG);
    }

    @Test
    void 찾는_성분과_같이_든_성분의_TIMING_ASK는_무시() {
        // 갑상선약(R-17 TIMING) + 성인 남성 철분 섭취(R-23 ASK): 철분 제품에 경고·제외 없음
        Result r = filter.check("IRON", List.of("IRON"),
                member(Set.of("LEVOTHYROXINE", "ADULT_MALE_TAKING_IRON", "IRON"), true, "IRON"));

        assertThat(r.excluded()).isFalse();
        assertThat(r.targetWarn()).isFalse();
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void OTHER_성분과_복용_약이_있으면_제외() {
        Result r = filter.check("MAGNESIUM", List.of("MAGNESIUM", "OTHER"), member(Set.of("STATIN"), true, "MAGNESIUM"));

        assertThat(r.excluded()).isTrue();
        assertThat(r.reason()).isEqualTo(Reason.OTHER_INGREDIENT);
    }

    @Test
    void OTHER_성분이_있어도_약_질환이_없으면_확인_못_함_경고() {
        Result r = filter.check("MAGNESIUM", List.of("MAGNESIUM", "OTHER"), member(Set.of(), false, "MAGNESIUM"));

        assertThat(r.excluded()).isFalse();
        assertThat(r.targetWarn()).isFalse();
        assertThat(r.warnings()).containsExactly(ProductSafetyFilter.UNVERIFIED_WARNING);
    }

    @Test
    void 라벨_성분이_없으면_제외() {
        Result r = filter.check("MAGNESIUM", List.of(), member(Set.of(), false, "MAGNESIUM"));

        assertThat(r.excluded()).isTrue();
        assertThat(r.reason()).isEqualTo(Reason.UNKNOWN_INGREDIENTS);
    }

    @Test
    void 비회원_오메가3_코큐텐_제품은_R_G6_때문에_제외() {
        Result r = filter.check("OMEGA3", List.of("OMEGA3", "COQ10"),
                new UserConditions(Set.of("GUEST"), true, false, List.of("OMEGA3")));

        assertThat(r.excluded()).isTrue();
        assertThat(r.reason()).isEqualTo(Reason.RULE);
    }

    @Test
    void 비회원_규칙은_회원에게_적용하지_않는다() {
        Result r = filter.check("OMEGA3", List.of("OMEGA3", "COQ10"), member(Set.of(), false, "OMEGA3"));

        assertThat(r.excluded()).isFalse();
    }
}
