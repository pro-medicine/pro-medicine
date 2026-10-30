package com.ucamp.app.domain.chat.repository;

import com.ucamp.app.domain.chat.entity.AiChatTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatTurnRepository extends JpaRepository<AiChatTurn, Long> {

    List<AiChatTurn> findByChatSession_ChatSessionIdOrderByTurnNoAsc(Long chatSessionId);
}
