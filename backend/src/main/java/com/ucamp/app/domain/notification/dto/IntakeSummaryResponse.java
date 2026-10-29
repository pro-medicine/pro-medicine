package com.ucamp.app.domain.notification.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * 홈 복용 알림 카드 응답.
 *
 * @param nextReminder 오늘 남은 다음 복용 알림 (없으면 null)
 * @param onTime       이번 주(월요일~오늘) 제시간 복용률
 */
public record IntakeSummaryResponse(
        NextReminder nextReminder,
        OnTimeRate onTime
) {

    /**
     * 다음 복용 알림. 같은 분에 울리는 일정은 하나로 묶는다. (복용 알림 스케줄러와 같은 기준)
     *
     * @param remindAt 알림 시각 (분 단위, 예: 12:30)
     * @param mealSlot 끼니 (BREAKFAST, LUNCH, DINNER)
     * @param count    그 시각에 먹을 영양제 수
     * @param names    그 시각에 먹을 영양제 이름
     */
    public record NextReminder(
            LocalTime remindAt,
            String mealSlot,
            int count,
            List<String> names
    ) {
    }

    /**
     * 제시간 복용률. 알림 시각부터 1시간이 지나기 전에 체크한 비율. (미리 먹은 것도 제시간으로 본다)
     *
     * @param total  이번 주 복용 체크 수
     * @param onTime 그중 제시간에 체크한 수
     * @param rate   onTime / total * 100 (반올림, 체크가 없으면 0)
     */
    public record OnTimeRate(
            int total,
            int onTime,
            int rate
    ) {
    }
}
