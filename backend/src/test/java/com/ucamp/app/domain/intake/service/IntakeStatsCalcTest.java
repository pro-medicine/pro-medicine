package com.ucamp.app.domain.intake.service;

import com.ucamp.app.domain.intake.dto.DayProgress;
import com.ucamp.app.domain.intake.dto.WeekRate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 마이페이지 통계 계산 (최고 연속 기록, 달성률, 주차별 달성률) 단위 테스트
class IntakeStatsCalcTest {

    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1); // 목요일

    // OCT_1부터 하루씩 (expected, taken) 쌍으로 진행도 목록 만들기
    private static List<DayProgress> days(int... pairs) {
        List<DayProgress> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            list.add(new DayProgress(OCT_1.plusDays(i / 2), pairs[i], pairs[i + 1]));
        }
        return list;
    }

    @Test
    @DisplayName("최고 연속 기록: 모두 복용한 날이 가장 길게 이어진 일수")
    void bestStreak() {
        // 완료 2일 → 일부 복용 → 완료 3일 → 일정 없음 → 완료 1일
        List<DayProgress> list = days(2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 0, 0, 2, 2);
        assertThat(IntakeService.bestStreak(list)).isEqualTo(3);
    }

    @Test
    @DisplayName("최고 연속 기록: 기록이 없으면 0")
    void bestStreakEmpty() {
        assertThat(IntakeService.bestStreak(List.of())).isZero();
    }

    @Test
    @DisplayName("달성률: 먹은 수 합 / 먹어야 했던 수 합, 반올림")
    void rate() {
        // (2 + 1 + 0) / (2 + 2 + 2) = 50%
        assertThat(IntakeService.rate(days(2, 2, 2, 1, 2, 0))).isEqualTo(50);
        // 2 / 3 = 66.7 → 67
        assertThat(IntakeService.rate(days(3, 2))).isEqualTo(67);
        assertThat(IntakeService.rate(days(0, 0))).isZero();
    }

    @Test
    @DisplayName("주차별 달성률: 월요일 기준으로 나누고 아직 오지 않은 주는 null")
    void monthWeeks() {
        LocalDate today = LocalDate.of(2026, 10, 8); // 2주차 목요일
        // 10/1(목)~10/4(일): 4일 모두 완료, 10/5(월)~10/8(목): 2/2, 1/2, 0/2, 1/2
        List<DayProgress> list = days(
                2, 2, 2, 2, 2, 2, 2, 2,
                2, 2, 2, 1, 2, 0, 2, 1);

        List<WeekRate> weeks = IntakeService.monthWeeks(list, today);

        assertThat(weeks).hasSize(5); // 10/1~4, 5~11, 12~18, 19~25, 26~31
        assertThat(weeks.get(0).start()).isEqualTo(OCT_1);
        assertThat(weeks.get(0).end()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(weeks.get(0).rate()).isEqualTo(100);
        assertThat(weeks.get(1).rate()).isEqualTo(50);   // 4 / 8
        assertThat(weeks.get(2).rate()).isNull();
        assertThat(weeks.get(4).end()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("주차별 달성률: 먹을 일정이 없던 주는 null")
    void monthWeeksWithoutSchedule() {
        LocalDate today = LocalDate.of(2026, 10, 3);
        List<WeekRate> weeks = IntakeService.monthWeeks(days(0, 0, 0, 0, 0, 0), today);
        assertThat(weeks.get(0).rate()).isNull();
    }
}
