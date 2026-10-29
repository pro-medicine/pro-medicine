package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.notification.dto.IntakeSummaryResponse;
import com.ucamp.app.domain.notification.dto.IntakeSummaryResponse.NextReminder;
import com.ucamp.app.domain.notification.dto.IntakeSummaryResponse.OnTimeRate;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테스트 데이터는 sql/intake-summary-fixture.sql에서 넣는다. (schema.sql 예시 데이터에 의존하지 않음)
 * 기준일 2026-10-29(목), 이번 주 = 10/26(월) ~ 10/29(목)
 * 테이블 구조는 로컬 DB에 schema.sql로 만들어져 있어야 한다.
 * 각 테스트는 @Transactional로 끝나면 롤백되므로 DB 데이터는 바뀌지 않는다.
 */
@SpringBootTest
@Transactional
@Sql("/sql/intake-summary-fixture.sql")
class IntakeSummaryServiceTest {

    private static final LocalDate OCT_29 = LocalDate.of(2026, 10, 29);

    @Autowired
    private IntakeSummaryService intakeSummaryService;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("다음 알림: 가장 빠른 시각의 일정을 같은 분끼리 묶고, 중단한 제품은 제외한다")
    void nextReminderGroupsSameMinute() {
        IntakeSummaryResponse result = intakeSummaryService.getSummary(9501L, at(10, 30));

        NextReminder next = result.nextReminder();
        assertThat(next).isNotNull();
        assertThat(next.remindAt()).isEqualTo(LocalTime.of(12, 30));   // 11:00(끊은 영양제)은 제외
        assertThat(next.mealSlot()).isEqualTo("LUNCH");
        assertThat(next.count()).isEqualTo(2);                          // 12:30:00 + 12:30:30
        assertThat(next.names()).containsExactly("테스트 오메가3", "테스트 유산균");
    }

    @Test
    @DisplayName("다음 알림: 오늘 이미 먹은 일정은 빠진다")
    void nextReminderSkipsTakenToday() {
        em.createNativeQuery("""
                INSERT INTO intake_records
                    (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared)
                VALUES (9590, 9502, '2026-10-29', 'TAKEN', '2026-10-29 10:00:00', false)
                """).executeUpdate();

        NextReminder next = intakeSummaryService.getSummary(9501L, at(10, 30)).nextReminder();

        assertThat(next.count()).isEqualTo(1);
        assertThat(next.names()).containsExactly("테스트 유산균");
    }

    @Test
    @DisplayName("다음 알림: 알림 시각이 지난 일정은 다음 알림이 아니다")
    void nextReminderIsAfterNow() {
        NextReminder next = intakeSummaryService.getSummary(9501L, at(13, 0)).nextReminder();

        assertThat(next.remindAt()).isEqualTo(LocalTime.of(19, 0));
        assertThat(next.mealSlot()).isEqualTo("DINNER");
        assertThat(next.names()).containsExactly("테스트 종합비타민");
    }

    @Test
    @DisplayName("다음 알림: 오늘 남은 알림이 없으면 null")
    void noNextReminder() {
        assertThat(intakeSummaryService.getSummary(9501L, at(19, 0)).nextReminder()).isNull();
    }

    @Test
    @DisplayName("제시간 복용률: 이번 주 TAKEN 기록 중 알림 시각 1시간 안에 체크한 비율 (지난주·건너뜀 제외)")
    void onTimeRateThisWeek() {
        OnTimeRate onTime = intakeSummaryService.getSummary(9501L, at(10, 30)).onTime();

        assertThat(onTime.total()).isEqualTo(4);
        assertThat(onTime.onTime()).isEqualTo(2);
        assertThat(onTime.rate()).isEqualTo(50);
    }

    @Test
    @DisplayName("일정과 복용 기록이 없는 사용자는 다음 알림 null, 복용률 0")
    void emptyUser() {
        IntakeSummaryResponse result = intakeSummaryService.getSummary(9502L, at(10, 30));

        assertThat(result.nextReminder()).isNull();
        assertThat(result.onTime().total()).isZero();
        assertThat(result.onTime().onTime()).isZero();
        assertThat(result.onTime().rate()).isZero();
    }

    @Test
    @DisplayName("제시간 기준: 알림 1시간 뒤까지는 제시간, 그 뒤나 다음 날은 늦음, 미리 먹은 것은 제시간")
    void isOnTimeBoundary() {
        LocalTime remindAt = LocalTime.of(12, 30);

        assertThat(IntakeSummaryService.isOnTime(OCT_29, remindAt, OCT_29.atTime(13, 30))).isTrue();      // 정확히 1시간
        assertThat(IntakeSummaryService.isOnTime(OCT_29, remindAt, OCT_29.atTime(13, 30, 1))).isFalse();  // 1초 늦음
        assertThat(IntakeSummaryService.isOnTime(OCT_29, remindAt, OCT_29.atTime(11, 0))).isTrue();       // 미리 먹음
        assertThat(IntakeSummaryService.isOnTime(OCT_29, remindAt, OCT_29.plusDays(1).atTime(9, 0))).isFalse(); // 다음 날
    }

    private static LocalDateTime at(int hour, int minute) {
        return OCT_29.atTime(hour, minute);
    }
}
