package com.ucamp.app.domain.chat.repository;

import com.ucamp.app.domain.chat.entity.AiChatTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AiChatTurnRepository extends JpaRepository<AiChatTurn, Long> {

    List<AiChatTurn> findByChatSession_ChatSessionIdOrderByTurnNoAsc(Long chatSessionId);

    // V7.1 추천 기록의 "불편한 점": 여러 대화 세션의 입력 (세션별 순서)
    List<AiChatTurn> findByChatSession_ChatSessionIdInOrderByChatSession_ChatSessionIdAscTurnNoAsc(Collection<Long> ids);
}
