package com.ucamp.app.domain.chat.vector;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 약사 리뷰 언급 벡터 검색. 지금은 서버 메모리 구현(InMemoryReviewVectorIndex)이고,
 * 언급이 아주 많아지면 이 인터페이스 뒤에서 벡터 DB로 바꾼다 (설계 5-1, T5)
 */
public interface ReviewVectorIndex {

    /**
     * @param query 정규화하지 않은 쿼리 벡터도 받는다 (안에서 정규화)
     * @return sim ≥ tau 인 언급, sim 높은 순 최대 k개
     */
    List<Hit> search(float[] query, double tau, int k);

    int size();

    /** 인덱스를 만든 시점의 MAX(review_product_scores.computed_at). 데이터가 없으면 null */
    LocalDateTime dataVersion();

    /** 트랙 1 점수에 쓰는 언급 정보 (context·인용은 들고 있지 않음) */
    record Mention(long mentionId, String channelId, String videoId, Long catalogProductId, List<String> ingredients,
                   String stance, boolean sponsored, LocalDate publishedAt) {
        public static final String RECOMMEND = "RECOMMEND";
        public static final String NOT_RECOMMEND = "NOT_RECOMMEND";

        public boolean recommend() {
            return RECOMMEND.equals(stance);
        }
    }

    record Hit(Mention mention, double similarity) {
    }
}
