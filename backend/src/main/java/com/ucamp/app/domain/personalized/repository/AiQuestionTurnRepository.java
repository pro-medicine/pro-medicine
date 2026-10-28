package com.ucamp.app.domain.personalized.repository;

import com.ucamp.app.domain.personalized.entity.AiQuestionTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiQuestionTurnRepository extends JpaRepository<AiQuestionTurn, Long> {
    List<AiQuestionTurn> findBySession_SessionIdOrderByTurnNoAsc(Long sessionId);

    // 회차의 마지막 질문 (답은 이 질문에만 받는다)
    Optional<AiQuestionTurn> findFirstBySession_SessionIdOrderByTurnNoDesc(Long sessionId);
}
