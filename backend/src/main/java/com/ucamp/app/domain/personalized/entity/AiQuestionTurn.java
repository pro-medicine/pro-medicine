package com.ucamp.app.domain.personalized.entity;

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
 * 맞춤 질문 회차의 질문 하나와 답 (STEP 14).
 * turn_no 0 = 고정 질문(임신·수유), 1부터 LLM 질문. 회차가 삭제되면 함께 삭제된다 (ON DELETE CASCADE).
 * options_json = [{"label":"4년 이상","kind":"CONCERN"}, ...] (kind는 서버 판단용, 화면에 보내지 않는다)
 * selected_json = 고른 선택지 index 목록 [2] 또는 [0,3]. 답하기 전에는 null
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ai_question_turns",
        uniqueConstraints = @UniqueConstraint(name = "uq_pq_turn", columnNames = {"session_id", "turn_no"}))
@EntityListeners(AuditingEntityListener.class)
public class AiQuestionTurn {

    public static final String SINGLE = "SINGLE";
    public static final String MULTI = "MULTI";
    public static final int TOPIC_LENGTH = 60;
    public static final int QUESTION_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "turn_id")
    private Long turnId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AiQuestionSession session;

    @Column(name = "turn_no", nullable = false)
    private int turnNo;

    @Column(name = "fixed", nullable = false)
    private boolean fixed;

    @Column(name = "topic", nullable = false, length = TOPIC_LENGTH)
    private String topic;

    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(name = "question_text", nullable = false, length = QUESTION_LENGTH)
    private String questionText;

    @Column(name = "answer_type", nullable = false, length = 10)
    private String answerType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_json", nullable = false)
    private String optionsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_json")
    private String selectedJson;

    @Column(name = "llm_call_log_id")
    private Long llmCallLogId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Builder
    public AiQuestionTurn(AiQuestionSession session, int turnNo, boolean fixed, String topic, int depth,
                          String questionText, String answerType, String optionsJson, Long llmCallLogId) {
        this.session = session;
        this.turnNo = turnNo;
        this.fixed = fixed;
        this.topic = topic;
        this.depth = depth;
        this.questionText = questionText;
        this.answerType = answerType;
        this.optionsJson = optionsJson;
        this.llmCallLogId = llmCallLogId;
    }

    public boolean isAnswered() {
        return selectedJson != null;
    }

    public void answer(String selectedJson) {
        this.selectedJson = selectedJson;
        this.answeredAt = LocalDateTime.now();
    }
}
