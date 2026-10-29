package com.ucamp.app.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 대화 결과 스냅샷 (세션당 1행). 트랙 1은 카드·제품을 그대로 저장해 다시 열었을 때 같은 결과를 보여준다.
 * hit_mention_ids·top_similarity는 검수·τ 조정용 (API로 내보내지 않음)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ai_chat_results",
        uniqueConstraints = @UniqueConstraint(name = "uq_chat_results_session", columnNames = "chat_session_id"))
@EntityListeners(AuditingEntityListener.class)
public class AiChatResult {

    public static final String REVIEW = "REVIEW";
    public static final String RULE_BASED = "RULE_BASED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_result_id")
    private Long chatResultId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AiChatSession chatSession;

    @Column(name = "track", nullable = false, length = 20)
    private String track;

    // 트랙 2일 때 ai_recommendations id (FK 없음: 추천 결과는 세션과 따로 남는다)
    @Column(name = "recommendation_id")
    private Long recommendationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cards")
    private String cards;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hit_mention_ids")
    private String hitMentionIds;

    @Column(name = "top_similarity", precision = 5, scale = 4)
    private BigDecimal topSimilarity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "excluded")
    private String excluded;

    @Column(name = "data_version")
    private LocalDateTime dataVersion;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AiChatResult(AiChatSession chatSession, String track, Long recommendationId, String cards,
                        String hitMentionIds, BigDecimal topSimilarity, String excluded, LocalDateTime dataVersion,
                        Integer latencyMs) {
        this.chatSession = chatSession;
        this.track = track;
        this.recommendationId = recommendationId;
        this.cards = cards;
        this.hitMentionIds = hitMentionIds;
        this.topSimilarity = topSimilarity;
        this.excluded = excluded;
        this.dataVersion = dataVersion;
        this.latencyMs = latencyMs;
    }
}
