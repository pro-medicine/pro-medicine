package com.ucamp.app.domain.product.dto;

import com.ucamp.app.domain.product.entity.CatalogProduct;
import com.ucamp.app.domain.product.entity.UserProduct;
import com.ucamp.app.domain.intake.entity.IntakeSchedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 마이페이지 "내가 먹는 영양제" 카드 1개 응답
 * - user_products(내가 등록한 것) + catalog_products(제품 정보) + intake_schedules(복용 일정)를 합친 모양
 * - 직접 입력 제품(catalogProduct 없음)은 제품 정보 항목이 전부 null
 */
public record MySupplementResponse(
        Long userProductId,
        Long catalogProductId,
        String productName,         // 제품명 or 직접 입력 이름
        String manufacturer,        // 카드: 네이처스웨이
        String category,            // 카드 태그: 종합비타민
        String ingredientSummary,   // 카드: 비타민 21종 · 미네랄 7종
        String packageSize,         // 카드: 60정 · 2개월분
        Integer price,              // 카드: 28,900원 (포맷은 프론트에서)
        String imageUrl,
        String mainFunction,        // 식약처 주된기능성
        String intakeMethod,        // 식약처 섭취방법
        String productKind,
        String registeredVia,
        LocalDate startDate,
        List<ScheduleResponse> schedules
) {

    // 내가 등록한 제품(up) + 그 제품의 일정 목록 → 카드 응답 1개
    // cp는 제품 정보 (검색해서 등록한 제품만 있음)
    public static MySupplementResponse of(UserProduct up, List<ScheduleResponse> schedules) {
        CatalogProduct cp = up.getCatalogProduct();

        // 직접 입력한 제품(처방약 등)은 이름만 있고 제품 정보는 없음
        if (cp == null) {
            return new MySupplementResponse(
                    up.getUserProductId(), null, up.getManualProductName(),
                    // 제조사·카테고리·성분 요약·용량·가격·이미지·주요 기능·섭취 방법 (8개 모두 없음)
                    null, null, null, null, null, null, null, null,
                    up.getProductKind(), up.getRegisteredVia(), up.getStartDate(), schedules
            );
        }

        return new MySupplementResponse(
                up.getUserProductId(),
                cp.getCatalogProductId(),
                cp.getProductName(),
                cp.getManufacturer(),
                cp.getCategory(),
                cp.getIngredientSummary(),
                cp.getPackageSize(),
                cp.getPrice(),
                cp.getImageUrl(),
                cp.getMainFunction(),
                cp.getIntakeMethod(),
                up.getProductKind(),
                up.getRegisteredVia(),
                up.getStartDate(),
                schedules
        );
    }

    /**
     * 복용 일정 (카드: 아침 식후)
     * - mealSlot: BREAKFAST/LUNCH/DINNER, mealRelation: BEFORE/AFTER/ANY
     * - 한글 표기(아침 식후)는 화면에서 바꾼다
     */
    public record ScheduleResponse(
            Long scheduleId,
            String mealSlot,
            String mealRelation,
            LocalTime remindAt
    ) {
        public static ScheduleResponse from(IntakeSchedule s) {
            return new ScheduleResponse(
                    s.getScheduleId(),
                    s.getMealSlot(),
                    s.getMealRelation(),
                    s.getRemindAt()
            );
        }
    }
}