package com.ucamp.app.domain.chat.repository;

import com.ucamp.app.domain.chat.entity.AiChatResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiChatResultRepository extends JpaRepository<AiChatResult, Long> {

    Optional<AiChatResult> findByChatSession_ChatSessionId(Long chatSessionId);
}
