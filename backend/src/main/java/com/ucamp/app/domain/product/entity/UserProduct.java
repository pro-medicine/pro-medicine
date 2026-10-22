package com.ucamp.app.domain.product.entity;

import com.ucamp.app.domain.user.entity.User;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_products")
@EntityListeners(AuditingEntityListener.class)
public class UserProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_product_id")
    private Long userProductId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_product_id")
    private CatalogProduct catalogProduct;

    // TODO: enum 전환
    @Column(name = "product_kind", nullable = false, length = 15)
    private String productKind;

    @Column(name = "manual_product_name", length = 255)
    private String manualProductName;

    // TODO: enum 전환
    @Column(name = "registered_via", nullable = false, length = 15)
    private String registeredVia;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserProduct(User user, CatalogProduct catalogProduct, String productKind, String manualProductName,
                       String registeredVia, LocalDate startDate, Boolean isActive) {
        this.user = user;
        this.catalogProduct = catalogProduct;
        this.productKind = productKind;
        this.manualProductName = manualProductName;
        this.registeredVia = registeredVia != null ? registeredVia : "SEARCH";
        this.startDate = startDate;
        this.isActive = isActive != null ? isActive : true;
    }
}
