package com.ucamp.app.domain.chat;

import com.ucamp.app.domain.chat.config.ChatProperties;
import com.ucamp.app.domain.chat.scoring.SituationScorer;
import com.ucamp.app.domain.chat.scoring.SituationScorer.Key;
import com.ucamp.app.domain.chat.scoring.SituationScorer.Situation;
import com.ucamp.app.domain.chat.vector.ReviewVectorIndex.Hit;
import com.ucamp.app.domain.chat.vector.ReviewVectorIndex.Mention;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// 상황 점수 (설계 5-3): w = (sim − τ)/(1 − τ), 약사별 max, 1 + log2 n_c, 협찬·최신성 보정, 비추천 감점
class SituationScorerTest {
    static final double TAU = 0.30;
    static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    final ChatProperties.Score cfg = new ChatProperties.Score();

    static Hit hit(long id, String channel, String video, long product, List<String> ingredients, String stance,
                   boolean sponsored, LocalDate published, double sim) {
        return new Hit(new Mention(id, channel, video, product, ingredients, stance, sponsored, published), sim);
    }

    static Hit rec(long id, String channel, String video, long product, double sim) {
        return hit(id, channel, video, product, List.of("MAGNESIUM"), Mention.RECOMMEND, false, TODAY, sim);
    }

    @Test
    void 같은_약사가_같은_영상에서_여러_번_말해도_표_1장_가장_높은_w() {
        // sim 0.65 → w 0.5, sim 0.44 → w 0.2. 같은 영상이라 n_c = 1 → 1 + log2 1 = 1
        Map<Key, Situation> s = SituationScorer.score(List.of(
                rec(1, "A", "v1", 10, 0.65), rec(2, "A", "v1", 10, 0.44)), TAU, TODAY, cfg);

        Situation m = s.get(new Key("MAGNESIUM", 10));
        assertThat(m.score()).isCloseTo(0.5, within(1e-9));
        assertThat(m.recommendChannels()).isEqualTo(1);
        assertThat(m.mentionIds()).containsExactly(1L, 2L);
    }

    @Test
    void 같은_약사의_영상이_2개면_1_더하기_log2_2() {
        Map<Key, Situation> s = SituationScorer.score(List.of(
                rec(1, "A", "v1", 10, 0.65), rec(2, "A", "v2", 10, 0.44)), TAU, TODAY, cfg);

        assertThat(s.get(new Key("MAGNESIUM", 10)).score()).isCloseTo(0.5 * 2, within(1e-9));
    }

    @Test
    void 약사_2명이면_더하고_비추천은_w만큼_뺀다() {
        Map<Key, Situation> s = SituationScorer.score(List.of(
                rec(1, "A", "v1", 10, 0.65),
                rec(2, "B", "v2", 10, 0.51),
                hit(3, "C", "v3", 10, List.of("MAGNESIUM"), Mention.NOT_RECOMMEND, false, TODAY, 0.44)),
                TAU, TODAY, cfg);

        Situation m = s.get(new Key("MAGNESIUM", 10));
        assertThat(m.score()).isCloseTo(0.5 + 0.3 - 0.2, within(1e-9));
        assertThat(m.recommendChannels()).isEqualTo(2);
        assertThat(m.notRecommendChannels()).isEqualTo(1);
    }

    @Test
    void 비추천만_있으면_후보가_아니다() {
        Map<Key, Situation> s = SituationScorer.score(List.of(
                hit(1, "A", "v1", 10, List.of("MAGNESIUM"), Mention.NOT_RECOMMEND, false, TODAY, 0.9)), TAU, TODAY, cfg);

        assertThat(s).isEmpty();
    }

    @Test
    void 전부_협찬이면_절반_최신성은_2년마다_절반_최소_035() {
        Map<Key, Situation> sponsored = SituationScorer.score(List.of(
                hit(1, "A", "v1", 10, List.of("MAGNESIUM"), Mention.RECOMMEND, true, TODAY, 0.65)), TAU, TODAY, cfg);
        assertThat(sponsored.get(new Key("MAGNESIUM", 10)).score()).isCloseTo(0.25, within(1e-9));

        Map<Key, Situation> old = SituationScorer.score(List.of(
                hit(1, "A", "v1", 10, List.of("MAGNESIUM"), Mention.RECOMMEND, false, TODAY.minusDays(731), 0.65)),
                TAU, TODAY, cfg);
        assertThat(old.get(new Key("MAGNESIUM", 10)).score()).isCloseTo(0.5 * 0.5, within(0.001));

        Map<Key, Situation> veryOld = SituationScorer.score(List.of(
                hit(1, "A", "v1", 10, List.of("MAGNESIUM"), Mention.RECOMMEND, false, TODAY.minusYears(10), 0.65)),
                TAU, TODAY, cfg);
        assertThat(veryOld.get(new Key("MAGNESIUM", 10)).score()).isCloseTo(0.5 * 0.35, within(1e-9));
    }

    @Test
    void 성분은_언급의_ingredients_기준이고_다른_성분_점수에_섞이지_않는다() {
        Map<Key, Situation> s = SituationScorer.score(List.of(
                hit(1, "A", "v1", 10, List.of("VITAMIN_D", "VITAMIN_K"), Mention.RECOMMEND, false, TODAY, 0.65),
                rec(2, "A", "v1", 11, 0.65)), TAU, TODAY, cfg);

        assertThat(s.keySet()).containsExactlyInAnyOrder(
                new Key("VITAMIN_D", 10), new Key("VITAMIN_K", 10), new Key("MAGNESIUM", 11));
        assertThat(s).doesNotContainKey(new Key("MAGNESIUM", 10));
    }

    @Test
    void τ_미만_언급과_제품_미확정_언급은_쓰지_않는다() {
        Map<Key, Situation> s = SituationScorer.score(List.of(
                rec(1, "A", "v1", 10, 0.29),
                new Hit(new Mention(2, "A", "v1", null, List.of("MAGNESIUM"), Mention.RECOMMEND, false, TODAY), 0.9)),
                TAU, TODAY, cfg);

        assertThat(s).isEmpty();
    }
}
