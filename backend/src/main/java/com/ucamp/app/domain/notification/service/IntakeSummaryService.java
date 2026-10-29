package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.intake.entity.IntakeRecord;
import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import com.ucamp.app.domain.notification.dto.IntakeSummaryResponse;
import com.ucamp.app.domain.notification.dto.IntakeSummaryResponse.NextReminder;
import com.ucamp.app.domain.notification.dto.IntakeSummaryResponse.OnTimeRate;
import com.ucamp.app.domain.notification.repository.IntakeSummaryRepository;
import com.ucamp.app.domain.product.entity.UserProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * 홈 복용 알림 카드.
 * - 다음 알림: 오늘 남은 일정 중 가장 빠른 알림 시각과, 그 시각(같은 분)에 먹을 영양제
 * - 제시간 복용률: 이번 주(월요일~오늘) 복용 체크 중 알림 시각부터 1시간이 지나기 전에 체크한 비율
 * 서버 시간대와 상관없이 한국 시간 기준으로 계산한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntakeSummaryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 알림 시각부터 이 시간 안에 체크하면 제시간으로 본다 */
    static final long ON_TIME_MINUTES = 60;

    private final IntakeSummaryRepository intakeSummaryRepository;

    public IntakeSummaryResponse getSummary(Long userId) {
        return getSummary(userId, LocalDateTime.now(KST));
    }

    /** 기준 시각을 받아서 계산 (테스트에서 시각을 고정할 수 있게 분리) */
    public IntakeSummaryResponse getSummary(Long userId, LocalDateTime now) {
        return new IntakeSummaryResponse(
                nextReminder(userId, now),
                onTimeRate(userId, now.toLocalDate())
        );
    }

    private NextReminder nextReminder(Long userId, LocalDateTime now) {
        List<IntakeSchedule> upcoming =
                intakeSummaryRepository.findUpcomingToday(userId, now.toLocalDate(), now.toLocalTime());
        if (upcoming.isEmpty()) {
            return null;   // 오늘 남은 알림 없음
        }

        // 가장 빠른 시각(분 단위)과 같은 분에 울리는 일정만 묶는다 (예: 12:30:00, 12:30:30 → 12:30 알림 1개)
        LocalTime first = minuteOf(upcoming.get(0));
        List<IntakeSchedule> sameTime = upcoming.stream()
                .filter(schedule -> minuteOf(schedule).equals(first))
                .toList();

        return new NextReminder(
                first,
                sameTime.get(0).getMealSlot(),
                sameTime.size(),
                sameTime.stream().map(schedule -> productName(schedule.getUserProduct())).toList()
        );
    }

    private OnTimeRate onTimeRate(Long userId, LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<IntakeRecord> records = intakeSummaryRepository.findTakenRecords(userId, monday, today);

        int total = records.size();
        int onTime = (int) records.stream()
                .filter(r -> isOnTime(r.getIntakeDate(), r.getSchedule().getRemindAt(), r.getCompletedAt()))
                .count();
        int rate = total == 0 ? 0 : (int) Math.round(onTime * 100.0 / total);
        return new OnTimeRate(total, onTime, rate);
    }

    /**
     * 제시간에 체크했는지: 그날 알림 시각 + 1시간이 지나기 전이면 제시간.
     * 알림 전에 미리 먹은 것도 제시간으로 본다. 다음 날 뒤늦게 체크하면 늦은 것.
     */
    static boolean isOnTime(LocalDate intakeDate, LocalTime remindAt, LocalDateTime completedAt) {
        LocalDateTime deadline = LocalDateTime.of(intakeDate, remindAt).plusMinutes(ON_TIME_MINUTES);
        return !completedAt.isAfter(deadline);
    }

    private static LocalTime minuteOf(IntakeSchedule schedule) {
        return schedule.getRemindAt().truncatedTo(ChronoUnit.MINUTES);
    }

    /** 검색해서 등록한 제품은 제품명, 직접 입력한 제품은 입력한 이름 */
    private static String productName(UserProduct product) {
        if (product.getCatalogProduct() != null) {
            return product.getCatalogProduct().getProductName();
        }
        return product.getManualProductName();
    }
}
