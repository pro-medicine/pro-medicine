package com.ucamp.app.domain.chat.repository;

import com.ucamp.app.domain.chat.entity.AiChatResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiChatResultRepository extends JpaRepository<AiChatResult, Long> {

    Optional<AiChatResult> findByChatSession_ChatSessionId(Long chatSessionId);

    // V7 마이페이지 최근 추천: 회원의 가장 최근 트랙 1(REVIEW) 결과. 트랙 2는 ai_recommendations 쪽에서 센다
    @Query("select r from AiChatResult r join r.chatSession cs "
            + "where r.track = 'REVIEW' and cs.user.id = :userId "
            + "order by r.createdAt desc, r.chatResultId desc")
    List<AiChatResult> findRecentReviewByUserId(@Param("userId") Long userId, Pageable pageable);

    // V7.1 추천 기록: user_id가 나인 트랙 1(REVIEW) 결과. 대화 세션이 90일 뒤 지워져도(chat_session_id NULL) 남는다
    @Query("select r from AiChatResult r left join fetch r.chatSession "
            + "where r.track = 'REVIEW' and r.user.id = :userId "
            + "order by r.createdAt desc, r.chatResultId desc")
    List<AiChatResult> findHistoryReviewByUserId(@Param("userId") Long userId, Pageable pageable);

    // V7.1 추천 기록 상세: 내 트랙 1 결과만 (남의 것·없는 것은 같은 결과)
    @Query("select r from AiChatResult r where r.chatResultId = :id and r.track = 'REVIEW' and r.user.id = :userId")
    Optional<AiChatResult> findOwnedReview(@Param("id") Long chatResultId, @Param("userId") Long userId);

    // V7.1: 트랙 2 추천 id 중 대화 결과 행(RULE_BASED)이 있는 것. 90일 뒤 chat_session_id가 비어도
    // 결과 행은 남으므로 V2 정밀 추천과 V4 트랙 2를 구분할 수 있다
    @Query("select r.recommendationId from AiChatResult r where r.recommendationId in :ids")
    List<Long> findRecommendationIdsIn(@Param("ids") Collection<Long> recommendationIds);
}
