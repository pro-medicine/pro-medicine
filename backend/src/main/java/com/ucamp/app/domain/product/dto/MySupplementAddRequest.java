package com.ucamp.app.domain.product.dto;

import java.util.List;

// 영양제 등록 요청 본문: {"supplementId": 4, "schedules": [{"mealSlot": "BREAKFAST", ...}]}
// supplementId(엑셀 영양제 검색) · catalogProductId(기존 제품) · manualProductName(직접 입력) 중 하나만 보낸다
// schedules는 없어도 됨 (일정 없이 등록)
public record MySupplementAddRequest(
        Long catalogProductId,
        String manualProductName,
        List<ScheduleRequest> schedules,
        Long supplementId
) {

}
