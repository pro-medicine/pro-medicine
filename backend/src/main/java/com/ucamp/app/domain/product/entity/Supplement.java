package com.ucamp.app.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 영양제 (식약처 DB 정제본 엑셀, 14개 성분 데이터가 있는 제품 목록).
 * 데이터는 backend/docs/sql/STEP12_supplements.sql로 넣는다. 앱에서는 읽기만 한다.
 * 기존 catalog_products(식약처 import·추천 제품)와는 따로 두고, 영양제 등록·홈 영양소 계산에 쓴다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "supplements")
public class Supplement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplement_id")
    private Long supplementId;

    // 식약처 식품코드 (엑셀 제품을 구분하는 값)
    @Column(name = "food_code", nullable = false, unique = true, length = 30)
    private String foodCode;

    // 품목제조보고번호 (catalog_products.mfds_item_no와 같은 종류의 값)
    @Column(name = "report_no", length = 30)
    private String reportNo;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    // 대분류 (영양성분, 기능성 원료, 복합 등)
    @Column(name = "category_group", length = 30)
    private String categoryGroup;

    // 중분류 (비타민 D, 엠에스엠 등) → 카드 태그
    @Column(name = "category", length = 50)
    private String category;

    // 1회 분량 (예: 500mg)
    @Column(name = "serving_size", length = 30)
    private String servingSize;

    // 1일 섭취횟수
    @Column(name = "servings_per_day", nullable = false)
    private Integer servingsPerDay;

    // 하루 함량 요약 (예: "비타민 D 10㎍ · 아연 8.5mg"), 함량 정보가 없으면 null
    @Column(name = "nutrient_summary", length = 500)
    private String nutrientSummary;

    @Builder
    public Supplement(String foodCode, String reportNo, String productName, String manufacturer, String categoryGroup,
                      String category, String servingSize, Integer servingsPerDay, String nutrientSummary) {
        this.foodCode = foodCode;
        this.reportNo = reportNo;
        this.productName = productName;
        this.manufacturer = manufacturer;
        this.categoryGroup = categoryGroup;
        this.category = category;
        this.servingSize = servingSize;
        this.servingsPerDay = servingsPerDay != null ? servingsPerDay : 1;
        this.nutrientSummary = nutrientSummary;
    }

    /** 섭취 방법 안내 (예: "1일 2회") */
    public String intakeText() {
        return "1일 %d회".formatted(servingsPerDay == null ? 1 : servingsPerDay);
    }
}
