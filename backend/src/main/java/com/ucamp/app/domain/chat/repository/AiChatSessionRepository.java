package com.ucamp.app.domain.chat.repository;

import com.ucamp.app.domain.chat.entity.AiChatSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {

    Optional<AiChatSession> findByChatSessionIdAndUser_Id(Long chatSessionId, Long userId);

    // 같은 세션에 메시지가 동시에 와도 한 번에 하나만 처리하도록 행을 잠근다 (V2 회차와 같은 방식)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AiChatSession s where s.chatSessionId = :id")
    Optional<AiChatSession> findForUpdate(@Param("id") Long id);

    // 서버 재시작 시 끝나지 않은 트랙 2 세션 정리용
    List<AiChatSession> findByStatusIn(Collection<String> statuses);

    // 90일 지난 대화 삭제. turns·results는 DB CASCADE, ai_recommendations.chat_session_id는 DB SET NULL
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AiChatSession s where s.startedAt < :cutoff")
    int deleteStartedBefore(@Param("cutoff") LocalDateTime cutoff);
}
