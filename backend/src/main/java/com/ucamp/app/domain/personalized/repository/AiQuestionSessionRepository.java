package com.ucamp.app.domain.personalized.repository;

import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiQuestionSessionRepository extends JpaRepository<AiQuestionSession, Long> {
    // 다른 회원의 회차는 없는 것과 같게 본다 (404)
    Optional<AiQuestionSession> findBySessionIdAndUser_Id(Long sessionId, Long userId);

    // 새 회차를 시작할 때 진행 중인 회차를 ABANDONED로 바꾸기 위함
    List<AiQuestionSession> findByUser_IdAndStatus(Long userId, String status);

    // 90일 지난 회차 삭제. turns는 DB CASCADE, ai_recommendations.session_id는 DB SET NULL
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AiQuestionSession s where s.startedAt < :cutoff")
    int deleteStartedBefore(@Param("cutoff") LocalDateTime cutoff);
}
