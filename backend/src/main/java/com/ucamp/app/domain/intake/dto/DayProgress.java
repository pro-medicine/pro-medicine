package com.ucamp.app.domain.intake.dto;

import java.time.LocalDate;

// 하루 복용 진행도: 그날 먹어야 했던 일정 수(expected)와 그중 먹은 수(taken)
// 마이페이지 달력의 날짜별 진행 링, 이번 주 요일 막대에 사용
public record DayProgress(LocalDate date, int expected, int taken) {

    // 먹어야 할 일정이 있고 전부 먹은 날
    public boolean completed() {
        return expected > 0 && taken >= expected;
    }
}
