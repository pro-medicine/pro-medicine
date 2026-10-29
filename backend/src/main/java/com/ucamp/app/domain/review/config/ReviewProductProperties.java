package com.ucamp.app.domain.review.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

// review.* 설정. 값은 설계(docs/design/V3-review-product-recommendation.md 5-2, 5-5) 그대로
@Getter
@Setter
@ConfigurationProperties(prefix = "review")
public class ReviewProductProperties {
    private Score score = new Score();
    private Products products = new Products();

    @Getter
    @Setter
    public static class Score {
        /** 같은 추천 결과의 다른 추천 성분이 제품에 같이 든 수(최대 2)당 가점 */
        private BigDecimal coverageWeight = new BigDecimal("1.0");
        /** 찾는 성분에 WARN 규칙이 걸리면 감점 */
        private BigDecimal warnPenalty = new BigDecimal("0.5");
        /** 커버리지 가점 상한 (성분 수) */
        private int coverageMax = 2;
    }

    @Getter
    @Setter
    public static class Products {
        /** 응답 최대 제품 수 */
        private int max = 3;
        /** 같은 브랜드 최대 제품 수 */
        private int maxPerBrand = 2;
        /** 안전 필터 전에 인기 점수 순으로 읽는 후보 수 */
        private int candidates = 30;
    }
}
