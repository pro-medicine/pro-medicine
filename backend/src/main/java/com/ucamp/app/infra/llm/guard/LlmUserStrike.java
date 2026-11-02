package com.ucamp.app.infra.llm.guard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * LLM 경고 기록 (V5 보안). 사유: INJECTION, OUTPUT_INVALID.
 * 24시간 내 경고 횟수로 정지·차단 결정.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "llm_user_strikes", indexes = @Index(name = "idx_strikes_user_created", columnList = "user_id, created_at"))
@EntityListeners(AuditingEntityListener.class)
public class LlmUserStrike {

    public static final String INJECTION = "INJECTION";
    public static final String OUTPUT_INVALID = "OUTPUT_INVALID";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "strike_id")
    private Long strikeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reason", nullable = false, length = 30)
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public LlmUserStrike(Long userId, String reason) {
        this.userId = userId;
        this.reason = reason;
    }
}
