package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.product.entity.CatalogProduct;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationProduct;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationItemRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationProductRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.domain.review.service.ReviewProductRecommendationService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * V7.1 추천 성분별 제품 순위 스냅샷. 회원 추천이 DONE으로 커밋된 뒤 같은 스레드에서
 * 제외되지 않은 성분마다 V3 ReviewProductRecommendationService.recommend(조건 로더·ProductSafetyFilter 그대로) 결과를
 * 반환 순서대로 1~N위로 저장한다. LLM·임베딩 호출 없음.
 * <ul>
 *   <li>성분마다 새 트랜잭션(REQUIRES_NEW): 한 성분이 실패해도 그 성분만 롤백, 다른 성분·추천(DONE)은 그대로</li>
 *   <li>실패는 경고 로그(추천 id·성분 코드만) + 메트릭. 예외를 밖으로 던지지 않는다 (추천 응답이 실패하면 안 됨)</li>
 *   <li>이미 저장된 추천·비회원 추천은 건너뛴다</li>
 * </ul>
 * AFTER_COMMIT 리스너 안의 데이터 접근은 끝난 트랜잭션에 참여하므로 반드시 REQUIRES_NEW로 연다.
 * fallbackExecution: 트랜잭션 없이 발행된 경우(지금은 없음)에도 바로 실행한다
 */
@Slf4j
@Service
public class RecommendationProductSnapshotService {
    public static final String METRIC = "recommendation.products.snapshot";

    private final AiRecommendationRepository recommendationRepository;
    private final AiRecommendationItemRepository itemRepository;
    private final AiRecommendationProductRepository productRepository;
    private final ReviewProductRecommendationService productService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate newTx;
    private final TransactionTemplate newReadTx;

    public RecommendationProductSnapshotService(AiRecommendationRepository recommendationRepository,
                                                AiRecommendationItemRepository itemRepository,
                                                AiRecommendationProductRepository productRepository,
                                                ReviewProductRecommendationService productService,
                                                EntityManager entityManager, ObjectMapper objectMapper,
                                                MeterRegistry meterRegistry,
                                                PlatformTransactionManager transactionManager) {
        this.recommendationRepository = recommendationRepository;
        this.itemRepository = itemRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.newReadTx = new TransactionTemplate(transactionManager);
        this.newReadTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.newReadTx.setReadOnly(true);
    }

    private record Target(Long itemId, String ingredient) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCompleted(RecommendationCompletedEvent event) {
        snapshot(event.recommendationId());
    }

    /** 성분별 제품 순위 저장. 저장한 성분 수를 돌려준다 (건너뛰면 0) */
    public int snapshot(Long recommendationId) {
        List<Target> targets;
        try {
            targets = newReadTx.execute(status -> targets(recommendationId));
        } catch (RuntimeException e) {
            log.warn("recommendation products snapshot skipped: recommendationId={}, error={}", recommendationId,
                    e.getClass().getSimpleName());
            count("fail");
            return 0;
        }
        if (targets == null || targets.isEmpty()) {
            return 0;
        }
        int saved = 0;
        for (Target t : targets) {
            try {
                newTx.executeWithoutResult(status -> saveItem(recommendationId, t));
                saved++;
                count("success");
            } catch (RuntimeException e) {
                log.warn("recommendation products snapshot failed: recommendationId={}, ingredient={}, error={}",
                        recommendationId, t.ingredient(), e.getClass().getSimpleName());
                count("fail");
            }
        }
        return saved;
    }

    // 회원·DONE·아직 저장 안 함일 때만 제외되지 않은 성분 목록
    private List<Target> targets(Long recommendationId) {
        AiRecommendation rec = recommendationRepository.findById(recommendationId).orElse(null);
        if (rec == null || rec.userIdOrNull() == null || !AiRecommendation.DONE.equals(rec.getStatus())
                || productRepository.existsByItem_Recommendation_RecommendationId(recommendationId)) {
            return List.of();
        }
        return itemRepository.findByRecommendation_RecommendationIdOrderBySortOrderAscRecommendationItemIdAsc(recommendationId)
                .stream().filter(i -> !i.isExcluded())
                .map(i -> new Target(i.getRecommendationItemId(), i.getIngredientName())).toList();
    }

    private void saveItem(Long recommendationId, Target t) {
        List<ReviewProductResponse.Product> products = productService.recommend(recommendationId, t.ingredient()).products();
        AiRecommendationItem item = itemRepository.getReferenceById(t.itemId());
        int rank = 1;
        for (ReviewProductResponse.Product p : products) {
            productRepository.save(AiRecommendationProduct.builder()
                    .item(item)
                    .product(entityManager.getReference(CatalogProduct.class, p.catalogProductId()))
                    .productRank(rank++)
                    .alsoContains(objectMapper.writeValueAsString(p.alsoContains() == null ? List.of() : p.alsoContains()))
                    .warnings(objectMapper.writeValueAsString(p.warnings() == null ? List.of() : p.warnings()))
                    .build());
        }
    }

    private void count(String outcome) {
        meterRegistry.counter(METRIC, "outcome", outcome).increment();
    }
}
