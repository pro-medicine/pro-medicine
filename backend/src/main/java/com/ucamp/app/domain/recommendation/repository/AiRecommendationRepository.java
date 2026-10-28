package com.ucamp.app.domain.recommendation.repository;

import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, Long> {
    // 같은 응답의 진행 중·완료 작업 (비동기 요청 멱등 처리)
    Optional<AiRecommendation> findFirstByResponse_ResponseIdAndStatusInOrderByRecommendationIdDesc(
            Long responseId, Collection<String> statuses);

    List<AiRecommendation> findByStatusOrderByRecommendationIdAsc(String status);

    // 회원의 가장 최근 완료 추천 (알림 진입 시 재사용)
    @Query("select r from AiRecommendation r join r.response resp "
            + "where resp.user.id = :userId and r.status = 'DONE' order by r.createdAt desc, r.recommendationId desc")
    List<AiRecommendation> findLatestDoneByUserId(@Param("userId") Long userId, Pageable pageable);

    // V2 모니터링: 기준 시각 이후 회원의 가장 최근 V1(설문 기반) 완료 추천
    @Query("select r from AiRecommendation r join r.response resp "
            + "where resp.user.id = :userId and r.status = 'DONE' and r.createdAt >= :since "
            + "order by r.createdAt desc, r.recommendationId desc")
    List<AiRecommendation> findLatestDoneByUserIdSince(@Param("userId") Long userId,
                                                       @Param("since") LocalDateTime since, Pageable pageable);
}
