package com.ucamp.app.domain.intake.dto;

import java.time.LocalDate;

// 이번 달 주차별 달성률 (월요일 시작, 달의 범위로 잘라서 계산)
// rate가 null이면 아직 오지 않은 주이거나 먹어야 할 일정이 없던 주
public record WeekRate(int week, LocalDate start, LocalDate end, Integer rate) {
}
