package com.ucamp.app.domain.personalized;

import com.ucamp.app.domain.personalized.service.RecommendationDiff;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 설계 9-1: V1 대비 추가·제외·순서 변화 (최종 추천 기준)
class RecommendationDiffTest {

    @Test
    void 추가_제외_순서_변화() {
        RecommendationDiff diff = RecommendationDiff.of(rec("VITAMIN_C", "MAGNESIUM", "VITAMIN_D"),
                rec("MAGNESIUM", "VITAMIN_B12", "VITAMIN_D"));

        assertThat(diff.added()).containsExactly("VITAMIN_B12");
        assertThat(diff.removed()).containsExactly("VITAMIN_C");
        assertThat(diff.rankChanged()).containsExactly(new RecommendationDiff.RankChange("MAGNESIUM", 2, 1));
    }

    @Test
    void 같으면_빈_변화() {
        RecommendationDiff diff = RecommendationDiff.of(rec("VITAMIN_D"), rec("VITAMIN_D"));
        assertThat(diff.added()).isEmpty();
        assertThat(diff.removed()).isEmpty();
        assertThat(diff.rankChanged()).isEmpty();
    }

    @Test
    void V0의_한글_이름은_성분_코드로_맞춰_비교한다() {
        RecommendationDiff diff = RecommendationDiff.of(rec("비타민 D", "비타민 A"), rec("VITAMIN_A", "VITAMIN_D"));
        assertThat(diff.added()).isEmpty();
        assertThat(diff.removed()).isEmpty();
        assertThat(diff.rankChanged()).hasSize(2);
    }

    private static RecommendationResponse rec(String... ingredients) {
        List<RecommendationResponse.Item> items = Arrays.stream(ingredients)
                .map(i -> new RecommendationResponse.Item(i, "r", Arrays.asList(ingredients).indexOf(i) + 1,
                        null, null, null, null, null, null, null))
                .toList();
        return new RecommendationResponse(1L, null, "DONE", null, "m", "s", null, items, List.of(), false, null);
    }
}
