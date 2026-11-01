package com.ucamp.app.infra.llm.guard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface LlmBlocklistRepository extends JpaRepository<LlmBlocklist, Long> {

    /** 사용자의 가장 최근 활성 차단/정지 레코드 (영구 또는 아직 만료 안 됨) */
    @Query("SELECT b FROM LlmBlocklist b WHERE b.userId = :userId " +
            "AND (b.blockedUntil IS NULL OR b.blockedUntil > CURRENT_TIMESTAMP) " +
            "ORDER BY b.createdAt DESC LIMIT 1")
    Optional<LlmBlocklist> findActiveBlock(Long userId);
}
