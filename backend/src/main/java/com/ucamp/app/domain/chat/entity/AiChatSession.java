package com.ucamp.app.domain.chat.entity;

import com.ucamp.app.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * V4 대화창 1회 (한 고민 = 한 세션). 대화에서 뽑은 약·질환은 프로필에 저장하지 않고 이 세션에만 남긴다.
 * 대화 원문이 건강 정보라 90일 뒤 삭제한다 (turns·results는 CASCADE, ai_recommendations.chat_session_id는 SET NULL).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ai_chat_sessions", indexes = {
        @Index(name = "idx_chat_sessions_user", columnList = "user_id, started_at"),
        @Index(name = "idx_chat_sessions_started", columnList = "started_at")})
@EntityListeners(AuditingEntityListener.class)
public class AiChatSession {

    public static final String OPEN = "OPEN";
    public static final String CLARIFYING = "CLARIFYING";
    public static final String SEARCHING = "SEARCHING";
    public static final String DONE = "DONE";
    public static final String FALLBACK_PENDING = "FALLBACK_PENDING";
    public static final String RED_FLAG = "RED_FLAG";
    public static final String FAILED = "FAILED";
    /** 결과가 정해진 상태 (같은 세션에 다시 입력하면 409) */
    public static final Set<String> FINISHED = Set.of(DONE, FALLBACK_PENDING, RED_FLAG);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_session_id")
    private Long chatSessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    // NOT_APPLICABLE / POSSIBLE / PREGNANT / LACTATING (V2와 같은 값, 19~50세 여성만)
    @Column(name = "pregnancy", length = 20)
    private String pregnancy;

    // 되묻기를 이미 했는지 (최대 1번, D5)
    @Column(name = "clarified", nullable = false)
    private boolean clarified;

    // 대화에서 뽑아 매칭한 약 [{drugId, drugClass, name}]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_medications")
    private String extraMedications;

    // 대화에서 뽑은 질환 코드 ["HYPERTENSION"]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_conditions")
    private String extraConditions;

    @CreatedDate
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static AiChatSession start(User user) {
        AiChatSession session = new AiChatSession();
        session.user = user;
        session.status = OPEN;
        return session;
    }

    public boolean isFinished() {
        return FINISHED.contains(status);
    }

    public void answerPregnancy(String pregnancy) {
        this.pregnancy = pregnancy;
    }

    public void markClarifying() {
        this.status = CLARIFYING;
        this.clarified = true;
    }

    public void markSearching(String extraMedications, String extraConditions) {
        this.status = SEARCHING;
        this.extraMedications = extraMedications;
        this.extraConditions = extraConditions;
    }

    public void markDone() {
        finish(DONE);
    }

    public void markFallbackPending() {
        this.status = FALLBACK_PENDING;
    }

    public void markRedFlag() {
        finish(RED_FLAG);
    }

    public void markFailed() {
        finish(FAILED);
    }

    // OFF_TOPIC·파싱 실패 후 다시 입력할 수 있게 되돌림 (되묻기 여부는 유지)
    public void reopen() {
        this.status = clarified ? CLARIFYING : OPEN;
    }

    private void finish(String status) {
        this.status = status;
        this.completedAt = LocalDateTime.now();
    }
}
