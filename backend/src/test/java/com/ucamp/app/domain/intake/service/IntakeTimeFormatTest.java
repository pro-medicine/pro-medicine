package com.ucamp.app.domain.intake.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 홈 타임라인용 시간 문자열 변환 (native query 값 → "HH:mm") 테스트.
 */
class IntakeTimeFormatTest {

    @Test
    @DisplayName("DB 드라이버가 주는 여러 시간 타입을 HH:mm 으로 바꾼다")
    void toHourMinute() {
        assertThat(IntakeService.toHourMinute(null)).isNull();
        assertThat(IntakeService.toHourMinute(LocalTime.of(8, 0, 30))).isEqualTo("08:00");
        assertThat(IntakeService.toHourMinute(Time.valueOf("12:30:00"))).isEqualTo("12:30");
        assertThat(IntakeService.toHourMinute(LocalDateTime.of(2026, 10, 29, 8, 10))).isEqualTo("08:10");
        assertThat(IntakeService.toHourMinute(Timestamp.valueOf("2026-10-29 19:05:00"))).isEqualTo("19:05");
        assertThat(IntakeService.toHourMinute("2026-10-29 07:45:00")).isEqualTo("07:45");
    }
}
