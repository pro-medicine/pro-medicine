package com.ucamp.app.domain.recommendation.repository;

import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, Long> {
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

    // V7 마이페이지 최근 추천: V1(설문 응답)·V2(맞춤 질문 회차)·V4 트랙 2(대화 세션) 중 하나로 회원이 일치하는 최근 완료 추천.
    // 비교 시각은 완료 시각(finished_at). 90일 삭제로 FK가 모두 null이 된 추천은 회원을 찾을 수 없어 빠진다
    @Query("select r from AiRecommendation r "
            + "left join r.response resp left join resp.user ru "
            + "left join r.session s left join s.user su "
            + "left join r.chatSession cs left join cs.user cu "
            + "where r.status = 'DONE' and (ru.id = :userId or su.id = :userId or cu.id = :userId) "
            + "order by coalesce(r.finishedAt, r.createdAt) desc, r.recommendationId desc")
    List<AiRecommendation> findRecentDoneByMember(@Param("userId") Long userId, Pageable pageable);
}
