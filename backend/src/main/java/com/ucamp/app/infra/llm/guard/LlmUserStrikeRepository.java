package com.ucamp.app.infra.llm.guard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface LlmUserStrikeRepository extends JpaRepository<LlmUserStrike, Long> {

    @Query("SELECT COUNT(s) FROM LlmUserStrike s WHERE s.userId = :userId AND s.createdAt >= :since")
    long countRecent(Long userId, LocalDateTime since);
}
