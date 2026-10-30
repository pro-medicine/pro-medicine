package com.ucamp.app.domain.chat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * chat.* 설정. 값은 설계(docs/design/V4-chat-symptom-recommendation.md 5-2 ~ 5-4) 그대로이고 평가셋(§9)으로 조정한다.
 * 카드당 제품 수·브랜드 제한은 V3 review.products.*를 그대로 쓴다
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "chat")
public class ChatProperties {
    private Search search = new Search();
    private Score score = new Score();
    private Cards cards = new Cards();
    private Index index = new Index();
    private Input input = new Input();
    private Embedding embedding = new Embedding();
    private Fallback fallback = new Fallback();

    @Getter
    @Setter
    public static class Search {
        /** 유사도 임계값 τ (이 값 이상인 언급만) */
        private double tau = 0.30;
        /** 검색 상위 K */
        private int topK = 50;
    }

    @Getter
    @Setter
    public static class Score {
        /** V3 base_score(상황 무관 인기) 가중치 */
        private double baseWeight = 0.3;
        /** 다른 카드 성분이 제품에 같이 든 수당 가점 */
        private double coverageWeight = 0.3;
        private int coverageMax = 2;
        /** 카드 성분에 WARN 규칙이 걸리면 감점 */
        private double warnPenalty = 0.5;
        /** 약사의 (성분, 제품) 추천 언급이 전부 협찬이면 곱함 (V3 D5) */
        private double sponsoredFactor = 0.5;
        /** 최신성 보정 0.5^(경과 연수 / half-life-years), 최소 recency-min (V3 5-1) */
        private double recencyHalfLifeYears = 2.0;
        private double recencyMin = 0.35;
    }

    @Getter
    @Setter
    public static class Cards {
        /** 성분 카드 최대 수 */
        private int max = 3;
        /** 성분 점수 = 그 성분의 제품 최종 점수 상위 N개 합 */
        private int productsForIngredientScore = 3;
    }

    @Getter
    @Setter
    public static class Index {
        /** review_product_scores.computed_at 변경 확인 주기 */
        private Duration refreshInterval = Duration.ofMinutes(10);
    }

    @Getter
    @Setter
    public static class Input {
        private int maxLength = 300;
    }

    @Getter
    @Setter
    public static class Fallback {
        /** 트랙 2를 비동기로 실행 (false면 요청 스레드에서 바로 실행: 테스트용) */
        private boolean async = true;
    }

    @Getter
    @Setter
    public static class Embedding {
        /** 쿼리 임베딩 모델. review_mentions.embedding_model과 같아야 한다 */
        private String model = "text-embedding-3-small";
    }
}
