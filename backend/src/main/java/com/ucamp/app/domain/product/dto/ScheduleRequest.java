package com.ucamp.app.domain.product.dto;

import java.time.LocalTime;

// 복용 일정 1개 요청: {"mealSlot": "BREAKFAST", "mealRelation": "AFTER", "remindAt": "08:00"}
// mealSlot: BREAKFAST/LUNCH/DINNER, mealRelation: BEFORE/AFTER/ANY
public record ScheduleRequest(
        String mealSlot,
        String mealRelation,
        LocalTime remindAt
) {
}
