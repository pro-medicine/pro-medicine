package com.ucamp.app.domain.product.dto;

import java.util.List;

// 영양제 등록 요청 본문: {"catalogProductId": 4, "schedules": [{"mealSlot": "BREAKFAST", ...}]}
// schedules는 없어도 됨 (일정 없이 등록)
public record MySupplementAddRequest(
        Long catalogProductId,
        List<ScheduleRequest> schedules
) {
}
