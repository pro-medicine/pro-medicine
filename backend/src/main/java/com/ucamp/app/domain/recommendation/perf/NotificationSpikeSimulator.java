package com.ucamp.app.domain.recommendation.perf;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "알맞은 영양성분을 섭취하세요" 알림 일괄 발송의 부하 시뮬레이션 (STEP 09 2-1).
 * notification 도메인(다른 담당자)은 건드리지 않고, 실제 notifications 테이블에도 쓰지 않는다.
 * - 중복 방지: dedup_key = {user_id}:{type}:{yyyyMMdd}. 같은 날 같은 알림은 한 번만 계획한다 (메모리 Set으로 흉내.
 *   실제 테이블의 유니크 제약은 docs/sql/STEP09_proposal_notification_dedup.sql 제안으로만 남김)
 * - 발송 분산: scheduled_at을 [start, start + spread) 구간에 고르게 흩는다. spread = 0이면 전원 같은 시각
 * - 실제 푸시는 범위 밖: NotificationSender 인터페이스 + 로그 구현으로 대신한다
 */
public class NotificationSpikeSimulator {
    public static final String TYPE = "NUTRIENT_REMINDER";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    public record PlannedNotification(long userId, String type, String dedupKey, LocalDateTime scheduledAt) {
    }

    public interface NotificationSender {
        void send(PlannedNotification notification);
    }

    private final Set<String> dedupKeys = ConcurrentHashMap.newKeySet();

    public static String dedupKey(long userId, String type, LocalDate day) {
        return userId + ":" + type + ":" + day.format(DAY);
    }

    /** 대상 사용자에게 보낼 알림 계획. 같은 날 이미 계획한 사용자는 건너뛴다 */
    public List<PlannedNotification> plan(Collection<Long> userIds, LocalDateTime start, Duration spread) {
        List<Long> targets = new ArrayList<>();
        for (Long userId : userIds) {
            if (dedupKeys.add(dedupKey(userId, TYPE, start.toLocalDate()))) {
                targets.add(userId);
            }
        }
        List<PlannedNotification> planned = new ArrayList<>();
        long spreadMillis = spread.toMillis();
        for (int i = 0; i < targets.size(); i++) {
            long offset = targets.size() <= 1 || spreadMillis == 0 ? 0 : spreadMillis * i / targets.size();
            long userId = targets.get(i);
            planned.add(new PlannedNotification(userId, TYPE, dedupKey(userId, TYPE, start.toLocalDate()),
                    start.plus(Duration.ofMillis(offset))));
        }
        return planned;
    }

    public void sendAll(List<PlannedNotification> planned, NotificationSender sender) {
        planned.forEach(sender::send);
    }

    /** 분 단위 도착 분포 (분 오프셋 → 건수). 알림을 받은 사용자가 바로 앱을 연다고 가정 */
    public static Map<Long, Integer> perMinute(List<PlannedNotification> planned, LocalDateTime start) {
        Map<Long, Integer> counts = new TreeMap<>();
        for (PlannedNotification p : planned) {
            long minute = Duration.between(start, p.scheduledAt()).toMinutes();
            counts.merge(minute, 1, Integer::sum);
        }
        return counts;
    }
}
