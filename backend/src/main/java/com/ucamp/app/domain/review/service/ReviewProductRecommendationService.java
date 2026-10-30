package com.ucamp.app.domain.review.service;

import com.ucamp.app.domain.health.entity.IngredientCode;
import com.ucamp.app.domain.recommendation.rule.RuleService;
import com.ucamp.app.domain.review.config.ReviewProductProperties;
import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.domain.review.repository.ReviewProductQueryRepository;
import com.ucamp.app.domain.review.repository.ReviewProductQueryRepository.Candidate;
import com.ucamp.app.domain.review.safety.ProductSafetyFilter;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 성분 카드 → 약사 리뷰 기반 제품 추천 (설계 5-2 ~ 5-6). 요청 중 LLM·외부 호출 없음, DB는 읽기만.
 * 1. 요청 성분이 추천 결과 items(제외 안 됨)에 있는지 확인 (없으면 빈 목록)
 * 2. 인기 점수 상위 후보(review.products.candidates개) → 3. 라벨 성분 일괄 조회 → 안전 필터 → 커버리지
 * 4. 최종 점수 = base_score + 가중치 x 커버리지(최대 2) - 감점 x (찾는 성분 WARN) → 동점 처리 → 브랜드 제한 → 상위 N
 */
@Service
@RequiredArgsConstructor
public class ReviewProductRecommendationService {
    public static final String REQUEST_METRIC = "review.products.request";
    public static final String RESULT_METRIC = "review.products.result";
    public static final String EXCLUDED_METRIC = "review.safety.excluded";
    static final String OUTCOME_OK = "ok";
    static final String OUTCOME_EMPTY = "empty";
    static final String OUTCOME_ALL_EXCLUDED = "all_excluded";

    private final RecommendationConditionLoader conditionLoader;
    private final ReviewProductQueryRepository queryRepository;
    private final RuleService ruleService;
    private final ReviewProductProperties properties;
    private final MeterRegistry meterRegistry;

    // 안전 필터를 통과한 후보 하나 (정렬용 값 포함). V4 대화창 추천도 같은 순위·브랜드 제한을 쓰려고 public
    public record Scored(Candidate candidate, BigDecimal finalScore, List<String> alsoContains,
                          ProductSafetyFilter.Result safety) {
    }

    private record Evaluation(List<Scored> passed, int excludedCount, int candidateCount) {
    }

    public ReviewProductResponse recommend(Long recommendationId, String ingredient) {
        String code = validCode(ingredient);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            RecommendationConditionLoader.Loaded loaded = conditionLoader.load(recommendationId);
            boolean guest = loaded.user() != null && loaded.user().guest();
            String notice = ReviewProductResponse.notice(guest);
            if (!loaded.recommendedIngredients().contains(code)) {
                countResult(OUTCOME_EMPTY);
                return new ReviewProductResponse(code, List.of(), 0, notice, ReviewProductResponse.EMPTY_MESSAGE);
            }

            List<Candidate> candidates = queryRepository.findCandidates(code, properties.getProducts().getCandidates());
            Evaluation eval = evaluate(code, candidates, loaded, true);
            List<ReviewProductResponse.Product> products = pick(eval.passed()).stream().map(this::toProduct).toList();

            countResult(!products.isEmpty() ? OUTCOME_OK : eval.candidateCount() == 0 ? OUTCOME_EMPTY : OUTCOME_ALL_EXCLUDED);
            return new ReviewProductResponse(code, products, eval.excludedCount(), notice,
                    products.isEmpty() ? ReviewProductResponse.EMPTY_MESSAGE : null);
        } finally {
            sample.stop(meterRegistry.timer(REQUEST_METRIC, "api", "products"));
        }
    }

    /** 추천 결과의 성분마다 안전 필터를 통과한 제품이 1개 이상 있는지 (화면의 [제품 추천 보기] 버튼 노출용) */
    public Map<String, Boolean> availability(Long recommendationId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            RecommendationConditionLoader.Loaded loaded = conditionLoader.load(recommendationId);
            Map<String, Boolean> out = new LinkedHashMap<>();
            for (String code : loaded.recommendedIngredients()) {
                List<Candidate> candidates = queryRepository.findCandidates(code, properties.getProducts().getCandidates());
                out.put(code, !evaluate(code, candidates, loaded, false).passed().isEmpty());
            }
            return out;
        } finally {
            sample.stop(meterRegistry.timer(REQUEST_METRIC, "api", "availability"));
        }
    }

    private static String validCode(String ingredient) {
        return IngredientCode.find(ingredient).map(Enum::name)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
    }

    // 안전 필터 + 커버리지 + 최종 점수. countExcluded = 제외 메트릭 기록 (availability는 기록하지 않는다)
    private Evaluation evaluate(String code, List<Candidate> candidates, RecommendationConditionLoader.Loaded loaded,
                                boolean countExcluded) {
        if (candidates.isEmpty()) {
            return new Evaluation(List.of(), 0, 0);
        }
        ProductSafetyFilter filter = new ProductSafetyFilter(ruleService.engine());
        Map<Long, List<String>> ingredients = queryRepository.findIngredients(
                candidates.stream().map(Candidate::catalogProductId).toList());
        List<String> others = loaded.recommendedIngredients().stream().filter(c -> !c.equals(code)).toList();
        ReviewProductProperties.Score score = properties.getScore();

        List<Scored> passed = new ArrayList<>();
        int excluded = 0;
        for (Candidate c : candidates) {
            List<String> label = ingredients.getOrDefault(c.catalogProductId(), List.of());
            ProductSafetyFilter.Result safety = filter.check(code, label, loaded.user());
            if (safety.excluded()) {
                excluded++;
                if (countExcluded) {
                    meterRegistry.counter(EXCLUDED_METRIC, "reason", safety.reason().tag()).increment();
                }
                continue;
            }
            Set<String> labelSet = Set.copyOf(label);
            List<String> alsoContains = others.stream().filter(labelSet::contains).toList();
            int coverage = Math.min(alsoContains.size(), score.getCoverageMax());
            BigDecimal finalScore = c.baseScore()
                    .add(score.getCoverageWeight().multiply(BigDecimal.valueOf(coverage)))
                    .subtract(safety.targetWarn() ? score.getWarnPenalty() : BigDecimal.ZERO)
                    .setScale(3, RoundingMode.HALF_UP);
            passed.add(new Scored(c, finalScore, alsoContains, safety));
        }
        return new Evaluation(passed, excluded, candidates.size());
    }

    // 동점: 최종 점수(소수 셋째 자리) → 추천 약사 수 많은 순 → 마지막 추천일 최신 → 비추천 적은 순 → 경고 없는 제품 → id 작은 순
    public static final Comparator<Scored> ORDER = Comparator
            .comparing(Scored::finalScore, Comparator.reverseOrder())
            .thenComparing(s -> s.candidate().recommendChannels(), Comparator.reverseOrder())
            .thenComparing(s -> s.candidate().lastPublishedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparingInt(s -> s.candidate().notRecommendChannels())
            .thenComparing(s -> s.safety().hasWarnings())
            .thenComparingLong(s -> s.candidate().catalogProductId());

    // 최대 N개, 같은 브랜드 최대 M개 (브랜드 없는 제품은 제한하지 않음). 부족하면 있는 만큼
    public List<Scored> pick(List<Scored> passed) {
        ReviewProductProperties.Products limits = properties.getProducts();
        List<Scored> sorted = new ArrayList<>(passed);
        sorted.sort(ORDER);
        Map<String, Integer> perBrand = new HashMap<>();
        List<Scored> out = new ArrayList<>();
        for (Scored s : sorted) {
            if (out.size() >= limits.getMax()) {
                break;
            }
            String brand = s.candidate().brand() == null ? null : s.candidate().brand().trim().toLowerCase(Locale.ROOT);
            if (brand != null && !brand.isEmpty()) {
                int n = perBrand.getOrDefault(brand, 0);
                if (n >= limits.getMaxPerBrand()) {
                    continue;
                }
                perBrand.put(brand, n + 1);
            }
            out.add(s);
        }
        return out;
    }

    private ReviewProductResponse.Product toProduct(Scored s) {
        Candidate c = s.candidate();
        return new ReviewProductResponse.Product(c.catalogProductId(), c.productName(), c.brand(), c.imageUrl(),
                s.alsoContains(), c.commonCautions(), s.safety().warnings());
    }

    private void countResult(String outcome) {
        meterRegistry.counter(RESULT_METRIC, "outcome", outcome).increment();
    }
}
