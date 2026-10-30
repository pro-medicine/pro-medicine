package com.ucamp.app.domain.chat.scoring;

import com.ucamp.app.domain.chat.config.ChatProperties;
import com.ucamp.app.domain.chat.vector.ReviewVectorIndex.Hit;
import com.ucamp.app.domain.chat.vector.ReviewVectorIndex.Mention;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 상황 점수 (설계 5-3). 벡터 유사도를 가중치로 쓴 인기 점수. 성분 a, 제품 p에 대해 a ∈ 언급의 ingredients인 언급만 (V3 D2).
 * <pre>
 * S(a, p) = Σ_추천 약사 c [ w_c × (1 + log2 n_c) × 협찬 보정 × 최신성 보정 ] − Σ_비추천 약사 c [ w_c ]
 *   w   = (sim − τ) / (1 − τ)
 *   w_c = 약사 c의 (a, p) 언급 중 가장 높은 w (같은 말을 여러 번 해도 표 1장, V3 D6)
 *   n_c = 약사 c의 (a, p) 추천 언급 중 τ를 넘은 영상 수
 *   협찬 보정 = c의 추천 언급이 전부 협찬이면 × 0.5 / 최신성 = 0.5^(c의 마지막 추천 후 경과 연수 / 2), 최소 0.35
 *   (발행일이 없으면 최신성 보정 없음)
 * </pre>
 * 제품이 확정되지 않은 언급(catalog_product_id 없음)은 쓰지 않는다. 점수 > 0 인 것만 후보
 */
public final class SituationScorer {

    public record Key(String ingredient, long catalogProductId) {
    }

    /**
     * @param recommendChannels     이 상황에서 추천한 약사 수 (동점 처리용)
     * @param notRecommendChannels  이 상황에서 비추천한 약사 수
     * @param lastPublishedAt       이 상황의 추천 언급 중 가장 최근 발행일
     */
    public record Situation(Key key, double score, int recommendChannels, int notRecommendChannels,
                            LocalDate lastPublishedAt, List<Long> mentionIds) {
    }

    private SituationScorer() {
    }

    public static Map<Key, Situation> score(List<Hit> hits, double tau, LocalDate today, ChatProperties.Score cfg) {
        // (a, p) → 약사 → 언급들
        Map<Key, Map<String, List<Hit>>> grouped = new LinkedHashMap<>();
        for (Hit h : hits) {
            Mention m = h.mention();
            if (m.catalogProductId() == null || h.similarity() < tau) {
                continue;
            }
            for (String a : m.ingredients()) {
                grouped.computeIfAbsent(new Key(a, m.catalogProductId()), k -> new LinkedHashMap<>())
                        .computeIfAbsent(m.channelId(), k -> new ArrayList<>()).add(h);
            }
        }
        Map<Key, Situation> out = new LinkedHashMap<>();
        for (Map.Entry<Key, Map<String, List<Hit>>> e : grouped.entrySet()) {
            double score = 0;
            int rec = 0;
            int notRec = 0;
            LocalDate last = null;
            List<Long> ids = new ArrayList<>();
            for (List<Hit> byChannel : e.getValue().values()) {
                List<Hit> recHits = byChannel.stream().filter(h -> h.mention().recommend()).toList();
                List<Hit> notHits = byChannel.stream().filter(h -> !h.mention().recommend()).toList();
                byChannel.forEach(h -> ids.add(h.mention().mentionId()));
                if (!recHits.isEmpty()) {
                    rec++;
                    double w = maxWeight(recHits, tau);
                    Set<String> videos = new HashSet<>();
                    recHits.forEach(h -> videos.add(h.mention().videoId()));
                    boolean allSponsored = recHits.stream().allMatch(h -> h.mention().sponsored());
                    LocalDate channelLast = recHits.stream().map(h -> h.mention().publishedAt())
                            .filter(d -> d != null).max(LocalDate::compareTo).orElse(null);
                    double v = w * (1 + log2(videos.size()))
                            * (allSponsored ? cfg.getSponsoredFactor() : 1.0)
                            * recency(channelLast, today, cfg);
                    score += v;
                    if (channelLast != null && (last == null || channelLast.isAfter(last))) {
                        last = channelLast;
                    }
                }
                if (!notHits.isEmpty()) {
                    notRec++;
                    score -= maxWeight(notHits, tau);
                }
            }
            if (score > 0) {
                out.put(e.getKey(), new Situation(e.getKey(), score, rec, notRec, last, List.copyOf(ids)));
            }
        }
        return out;
    }

    static double weight(double sim, double tau) {
        if (tau >= 1) {
            return 0;
        }
        return Math.max(0, (sim - tau) / (1 - tau));
    }

    private static double maxWeight(List<Hit> hits, double tau) {
        return hits.stream().mapToDouble(h -> weight(h.similarity(), tau)).max().orElse(0);
    }

    static double recency(LocalDate last, LocalDate today, ChatProperties.Score cfg) {
        if (last == null || today == null) {
            return 1.0;
        }
        double years = Math.max(0, ChronoUnit.DAYS.between(last, today)) / 365.25;
        return Math.max(cfg.getRecencyMin(), Math.pow(0.5, years / cfg.getRecencyHalfLifeYears()));
    }

    private static double log2(int n) {
        return n <= 1 ? 0 : Math.log(n) / Math.log(2);
    }
}
