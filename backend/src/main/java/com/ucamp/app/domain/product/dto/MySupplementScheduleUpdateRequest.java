package com.ucamp.app.domain.product.dto;

import java.util.List;

// 복용 일정 수정 요청 본문: {"schedules": [...]} (화면의 일정 전체로 통째로 교체, 빈 배열이면 일정 없음)
public record MySupplementScheduleUpdateRequest(
        List<ScheduleRequest> schedules
) {
}
