package com.ucamp.app.domain.chat.repository;

import com.ucamp.app.domain.chat.entity.AiChatResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiChatResultRepository extends JpaRepository<AiChatResult, Long> {

    Optional<AiChatResult> findByChatSession_ChatSessionId(Long chatSessionId);

    // V7 마이페이지 최근 추천: 회원의 가장 최근 트랙 1(REVIEW) 결과. 트랙 2는 ai_recommendations 쪽에서 센다
    @Query("select r from AiChatResult r join r.chatSession cs "
            + "where r.track = 'REVIEW' and cs.user.id = :userId "
            + "order by r.createdAt desc, r.chatResultId desc")
    List<AiChatResult> findRecentReviewByUserId(@Param("userId") Long userId, Pageable pageable);
}
