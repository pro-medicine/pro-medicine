package com.ucamp.app.domain.recommendation.entity;

import com.ucamp.app.domain.product.entity.CatalogProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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
 * V7.1 추천 성분 카드별 제품 순위 스냅샷 (회원 추천이 DONE이 될 때 저장).
 * 순위·같이 든 성분·안전 경고는 추천받을 때의 값이고, 제품 이름·사진·가격은 보여줄 때 catalog_products에서 읽는다.
 * catalog_products(다른 팀원 테이블)는 FK로 참조만 한다 (지연 로딩, id만 읽음. 제품이 지워지면 이 행도 CASCADE)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ai_recommendation_products",
        uniqueConstraints = @UniqueConstraint(name = "uq_ai_rec_product_rank",
                columnNames = {"recommendation_item_id", "product_rank"}))
@EntityListeners(AuditingEntityListener.class)
public class AiRecommendationProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_product_id")
    private Long recommendationProductId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_rec_products_item"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AiRecommendationItem item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalog_product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_rec_products_product"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogProduct product;

    // rank는 MySQL 예약어
    @Column(name = "product_rank", nullable = false)
    private Integer productRank;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "also_contains")
    private String alsoContains;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings")
    private String warnings;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AiRecommendationProduct(AiRecommendationItem item, CatalogProduct product, Integer productRank,
                                   String alsoContains, String warnings) {
        this.item = item;
        this.product = product;
        this.productRank = productRank;
        this.alsoContains = alsoContains;
        this.warnings = warnings;
    }

    /** 제품 id (프록시 초기화 없이) */
    public Long catalogProductId() {
        return product.getCatalogProductId();
    }
}
