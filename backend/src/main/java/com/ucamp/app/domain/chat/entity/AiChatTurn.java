package com.ucamp.app.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 대화 입력 1개 (첫 입력 또는 되묻기 답). turn_no 1부터.
 * parse_output = ① LLM 구조화 결과 그대로, clarification = 화면에 보여준 되묻기 {question, options}
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ai_chat_turns",
        uniqueConstraints = @UniqueConstraint(name = "uq_chat_turn", columnNames = {"chat_session_id", "turn_no"}))
@EntityListeners(AuditingEntityListener.class)
public class AiChatTurn {

    public static final int USER_TEXT_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_turn_id")
    private Long chatTurnId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AiChatSession chatSession;

    @Column(name = "turn_no", nullable = false)
    private int turnNo;

    @Column(name = "user_text", nullable = false, length = USER_TEXT_LENGTH)
    private String userText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parse_output")
    private String parseOutput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clarification")
    private String clarification;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_call_log_ids")
    private String llmCallLogIds;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AiChatTurn(AiChatSession chatSession, int turnNo, String userText, String parseOutput,
                      String clarification, String llmCallLogIds) {
        this.chatSession = chatSession;
        this.turnNo = turnNo;
        this.userText = userText;
        this.parseOutput = parseOutput;
        this.clarification = clarification;
        this.llmCallLogIds = llmCallLogIds;
    }
}
