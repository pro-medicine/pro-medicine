package com.ucamp.app.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "catalog_products")
@EntityListeners(AuditingEntityListener.class)
public class CatalogProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "catalog_product_id")
    private Long catalogProductId;

    @Column(name = "mfds_item_no", length = 100)
    private String mfdsItemNo;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "manufacturer", length = 255)
    private String manufacturer;

    @Column(name = "main_function", columnDefinition = "TEXT")
    private String mainFunction;

    @Column(name = "intake_method", columnDefinition = "TEXT")
    private String intakeMethod;

    @Column(name = "intake_caution", columnDefinition = "TEXT")
    private String intakeCaution;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    // 영양제 카드 표기용 (식약처 데이터에 없는 항목이라 비어 있을 수 있음)
    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "ingredient_summary", length = 255)
    private String ingredientSummary;

    @Column(name = "package_size", length = 100)
    private String packageSize;

    // 참고 가격(원)
    @Column(name = "price")
    private Integer price;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public CatalogProduct(String mfdsItemNo, String productName, String manufacturer, String mainFunction,
                          String intakeMethod, String intakeCaution, String imageUrl,
                          String category, String ingredientSummary, String packageSize, Integer price) {
        this.mfdsItemNo = mfdsItemNo;
        this.productName = productName;
        this.manufacturer = manufacturer;
        this.mainFunction = mainFunction;
        this.intakeMethod = intakeMethod;
        this.intakeCaution = intakeCaution;
        this.imageUrl = imageUrl;
        this.category = category;
        this.ingredientSummary = ingredientSummary;
        this.packageSize = packageSize;
        this.price = price;
    }

    public void updateFromMfds(String productName, String manufacturer, String mainFunction, String intakeMethod, String intakeCaution, String category, String ingredientSummary) {
        this.productName = productName;
        this.manufacturer = manufacturer;
        this.mainFunction = mainFunction;
        this.intakeMethod = intakeMethod;
        this.intakeCaution = intakeCaution;
        this.category = category;
        this.ingredientSummary = ingredientSummary;
    }
}
