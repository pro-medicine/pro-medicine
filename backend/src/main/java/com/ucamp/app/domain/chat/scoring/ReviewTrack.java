package com.ucamp.app.domain.chat.scoring;

import com.ucamp.app.domain.chat.config.ChatProperties;
import com.ucamp.app.domain.chat.dto.ChatCard;
import com.ucamp.app.domain.chat.repository.ChatCandidateRepository;
import com.ucamp.app.domain.chat.vector.ReviewVectorIndex;
import com.ucamp.app.domain.health.entity.IngredientCode;
import com.ucamp.app.domain.recommendation.config.RecommendationProperties;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.pipeline.PostProcessSteps;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.rule.ConditionCodeResolver;
import com.ucamp.app.domain.recommendation.rule.RuleService;
import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.domain.review.repository.ReviewProductQueryRepository;
import com.ucamp.app.domain.review.repository.ReviewProductQueryRepository.Candidate;
import com.ucamp.app.domain.review.safety.ProductSafetyFilter;
import com.ucamp.app.domain.review.service.ReviewProductRecommendationService;
import com.ucamp.app.domain.review.service.ReviewProductRecommendationService.Scored;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 트랙 1: 검색 결과 → 상황 점수 → 성분 카드 → 제품 (설계 5-3 ~ 5-5). 요청 중 DB는 읽기만, LLM 호출 없음.
 * 1. τ를 넘은 언급으로 (성분, 제품) 상황 점수 S
 * 2. 1차: 후보 성분 전체 기준으로 제품 최종 점수 → 성분 점수(상위 3개 합) → 상위 cards.max개 성분
 * 3. 카드 성분에 성분 단위 규칙(applyDoses → applyRules): EXCLUDE면 카드 제거(제외 목록), WARN이면 경고
 * 4. 2차: 남은 카드 성분 기준 커버리지·안전 필터 → V3 ORDER·pick(최대 N개, 브랜드 제한) → 제품 0개 카드 제거
 * 최종 점수 = S + base × base_score + coverage × 커버리지(최대 2) − warn × (카드 성분 WARN)
 */
@Component
@RequiredArgsConstructor
public class ReviewTrack {
    public static final String CARD_REASON = "비슷한 고민에 약사들이 추천한 성분이에요";
    public static final String TRACK_TAG = "review";

    private final ChatCandidateRepository candidateRepository;
    private final ReviewProductQueryRepository productQueryRepository;
    private final ReviewProductRecommendationService v3;
    private final RuleService ruleService;
    private final PostProcessSteps postProcessSteps;
    private final ConditionCodeResolver conditionCodeResolver;
    private final RecommendationProperties recommendationProperties;
    private final ChatProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock = Clock.systemDefaultZone();

    /**
     * @param cards                최종 카드 (0개면 트랙 2로)
     * @param excludedItems        성분 단위 규칙으로 제외된 카드 성분 (V1 F1-4와 같은 형식)
     * @param excludedProductCount 안전 필터로 빠진 제품 수
     * @param hitMentionIds        τ를 넘은 언급 id → sim (검수용, API로 내보내지 않음)
     */
    public record Result(List<ChatCard> cards, List<RecommendationResponse.Item> excludedItems,
                         int excludedProductCount, Map<Long, Double> hitMentionIds, Double topSimilarity) {
        public boolean empty() {
            return cards.isEmpty();
        }
    }

    public Result run(List<ReviewVectorIndex.Hit> hits, RecommendationInput input) {
        Map<Long, Double> hitIds = new LinkedHashMap<>();
        hits.forEach(h -> hitIds.put(h.mention().mentionId(), round4(h.similarity())));
        Double top = hits.isEmpty() ? null : round4(hits.get(0).similarity());
        double tau = properties.getSearch().getTau();
        Map<SituationScorer.Key, SituationScorer.Situation> situations =
                SituationScorer.score(hits, tau, LocalDate.now(clock), properties.getScore());
        // IngredientCode에 없는 코드(OTHER, MULTIVITAMIN 등)는 카드로 만들지 않는다 (설계 5-4, P12)
        situations.keySet().removeIf(k -> IngredientCode.find(k.ingredient()).isEmpty());
        if (situations.isEmpty()) {
            return new Result(List.of(), List.of(), 0, hitIds, top);
        }

        Set<Long> productIds = new LinkedHashSet<>();
        situations.keySet().forEach(k -> productIds.add(k.catalogProductId()));
        Map<Long, ChatCandidateRepository.Product> products = candidateRepository.findActiveProducts(productIds);
        Map<String, ChatCandidateRepository.BaseScore> baseScores = candidateRepository.findBaseScores(productIds);
        Map<Long, List<String>> labels = productQueryRepository.findIngredients(products.keySet());
        situations.keySet().removeIf(k -> !products.containsKey(k.catalogProductId()));

        Set<String> conditions = conditionCodeResolver.resolve(input);
        boolean hasMedOrDisease = !input.medicationsOrEmpty().isEmpty() || !input.conditionsOrEmpty().isEmpty();
        ProductSafetyFilter filter = new ProductSafetyFilter(ruleService.engine());
        Evaluator eval = new Evaluator(situations, products, baseScores, labels, filter, conditions, hasMedOrDisease);

        // 1차: 후보 성분 전체 기준. 성분 단위 규칙에서 EXCLUDE인 성분은 제품 필터 없이 순위만 매긴다
        // (상위 카드에 들면 제외 성분 목록으로 알려준다. 제품 필터를 거치면 제품이 다 빠져 순위에 오르지 못함)
        List<String> allIngredients = situations.keySet().stream().map(SituationScorer.Key::ingredient).distinct().toList();
        Set<String> ruleExcluded = ruleExcluded(allIngredients, conditions);
        Map<String, Double> ingredientScores = new LinkedHashMap<>();
        for (String a : allIngredients) {
            List<Scored> passed = eval.evaluate(a, allIngredients, false, ruleExcluded.contains(a));
            double sum = passed.stream().map(Scored::finalScore).sorted(Comparator.reverseOrder())
                    .limit(properties.getCards().getProductsForIngredientScore())
                    .mapToDouble(BigDecimal::doubleValue).sum();
            if (!passed.isEmpty() && sum > 0) {
                ingredientScores.put(a, sum);
            }
        }
        List<String> chosen = ingredientScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(properties.getCards().getMax())
                .map(Map.Entry::getKey).toList();
        if (chosen.isEmpty()) {
            return new Result(List.of(), List.of(), eval.excludedCount, hitIds, top);
        }

        // 성분 단위 규칙: V1/V2 카드와 같은 용량·경고·제외
        List<RecommendationItem> items = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            items.add(RecommendationItem.builder().ingredient(chosen.get(i)).reason(CARD_REASON).sortOrder(i + 1).build());
        }
        String version = recommendationProperties.getPipelineVersion();
        items = postProcessSteps.applyRules(version, input, postProcessSteps.applyDoses(version, input, items));
        List<RecommendationResponse.Item> excludedItems = items.stream().filter(RecommendationItem::excluded)
                .map(ReviewTrack::toItem).toList();
        List<RecommendationItem> kept = items.stream().filter(i -> !i.excluded()).toList();
        List<String> cardIngredients = kept.stream().map(RecommendationItem::ingredient).toList();

        // 2차: 남은 카드 성분 기준 커버리지·안전 필터 → V3 순위·개수
        eval.excludedCount = 0;
        List<ChatCard> cards = new ArrayList<>();
        for (RecommendationItem item : kept) {
            List<Scored> passed = eval.evaluate(item.ingredient(), cardIngredients, true, false);
            List<ReviewProductResponse.Product> picked = v3.pick(passed).stream().map(ReviewTrack::toProduct).toList();
            if (picked.isEmpty()) {
                continue;
            }
            cards.add(new ChatCard(item.ingredient(), item.reason(), cards.size() + 1, plain(item.amount()), item.unit(),
                    plain(item.currentIntake()), item.intakeStatus(), item.doseNote(),
                    item.warningsOrEmpty().isEmpty() ? null : item.warningsOrEmpty(), picked));
        }
        return new Result(List.copyOf(cards), excludedItems, eval.excludedCount, hitIds, top);
    }

    // 성분 a의 후보 제품 → 안전 필터 → 최종 점수 (V3 evaluate와 같은 구조)
    private final class Evaluator {
        private final Map<SituationScorer.Key, SituationScorer.Situation> situations;
        private final Map<Long, ChatCandidateRepository.Product> products;
        private final Map<String, ChatCandidateRepository.BaseScore> baseScores;
        private final Map<Long, List<String>> labels;
        private final ProductSafetyFilter filter;
        private final Set<String> conditions;
        private final boolean hasMedOrDisease;
        int excludedCount;

        Evaluator(Map<SituationScorer.Key, SituationScorer.Situation> situations,
                  Map<Long, ChatCandidateRepository.Product> products,
                  Map<String, ChatCandidateRepository.BaseScore> baseScores, Map<Long, List<String>> labels,
                  ProductSafetyFilter filter, Set<String> conditions, boolean hasMedOrDisease) {
            this.situations = situations;
            this.products = products;
            this.baseScores = baseScores;
            this.labels = labels;
            this.filter = filter;
            this.conditions = conditions;
            this.hasMedOrDisease = hasMedOrDisease;
        }

        /** skipSafety = 순위만 매길 때 (성분 단위로 이미 제외된 성분) */
        List<Scored> evaluate(String a, List<String> cardIngredients, boolean countExcluded, boolean skipSafety) {
            ChatProperties.Score cfg = properties.getScore();
            ProductSafetyFilter.UserConditions user = new ProductSafetyFilter.UserConditions(conditions, false,
                    hasMedOrDisease, cardIngredients);
            List<String> others = cardIngredients.stream().filter(c -> !c.equals(a)).toList();
            List<Scored> passed = new ArrayList<>();
            for (SituationScorer.Situation s : situations.values()) {
                if (!s.key().ingredient().equals(a)) {
                    continue;
                }
                long pid = s.key().catalogProductId();
                List<String> label = labels.getOrDefault(pid, List.of());
                ProductSafetyFilter.Result safety = skipSafety
                        ? new ProductSafetyFilter.Result(false, null, false, List.of()) : filter.check(a, label, user);
                if (safety.excluded()) {
                    if (countExcluded) {
                        excludedCount++;
                        meterRegistry.counter(ReviewProductRecommendationService.EXCLUDED_METRIC,
                                "reason", safety.reason().tag(), "track", TRACK_TAG).increment();
                    }
                    continue;
                }
                Set<String> labelSet = Set.copyOf(label);
                List<String> alsoContains = others.stream().filter(labelSet::contains).toList();
                int coverage = Math.min(alsoContains.size(), cfg.getCoverageMax());
                ChatCandidateRepository.BaseScore base = baseScores.get(ChatCandidateRepository.key(a, pid));
                double baseScore = base == null || base.baseScore() == null ? 0 : base.baseScore().doubleValue();
                double score = s.score() + cfg.getBaseWeight() * baseScore + cfg.getCoverageWeight() * coverage
                        - (safety.targetWarn() ? cfg.getWarnPenalty() : 0);
                BigDecimal finalScore = BigDecimal.valueOf(score).setScale(3, RoundingMode.HALF_UP);
                ChatCandidateRepository.Product p = products.get(pid);
                // ORDER 동점 처리용 값은 이 상황 기준 (추천 약사 수·최근 추천일·비추천 수)
                Candidate c = new Candidate(pid, p.productName(), p.brand(), p.imageUrl(), finalScore,
                        s.recommendChannels(), s.notRecommendChannels(), s.lastPublishedAt(),
                        base == null ? List.of() : base.commonCautions());
                passed.add(new Scored(c, finalScore, alsoContains, safety));
            }
            return passed;
        }
    }

    // 성분 단위 EXCLUDE 확인 (사후 규칙과 같은 엔진, 메트릭은 카드 성분에 실제로 적용할 때만 센다)
    private Set<String> ruleExcluded(List<String> ingredients, Set<String> conditions) {
        List<RecommendationItem> items = ingredients.stream()
                .map(a -> RecommendationItem.builder().ingredient(a).build()).toList();
        Set<String> out = new LinkedHashSet<>();
        ruleService.engine().applyPost(items, conditions, false).items().stream()
                .filter(RecommendationItem::excluded).forEach(i -> out.add(i.ingredient()));
        return out;
    }

    private static ReviewProductResponse.Product toProduct(Scored s) {
        Candidate c = s.candidate();
        return new ReviewProductResponse.Product(c.catalogProductId(), c.productName(), c.brand(), c.imageUrl(),
                s.alsoContains(), c.commonCautions(), s.safety().warnings());
    }

    private static RecommendationResponse.Item toItem(RecommendationItem i) {
        return new RecommendationResponse.Item(i.ingredient(), i.reason(), i.sortOrder(), plain(i.amount()), i.unit(),
                plain(i.currentIntake()), i.intakeStatus(), i.doseNote(),
                i.warningsOrEmpty().isEmpty() ? null : i.warningsOrEmpty(),
                i.appliedRuleIdsOrEmpty().isEmpty() ? null : i.appliedRuleIdsOrEmpty());
    }

    // RecommendationMapper와 같은 숫자 표기 (1E+2 → 100)
    private static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }
}
