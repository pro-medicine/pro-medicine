package com.ucamp.app.domain.intake.dto;

import java.time.LocalDate;

// 어떤 일정을 어느 날 복용(TAKEN)했는지
public record TakenIntake(
        Long scheduleId,
        LocalDate intakeDate
) {
}
