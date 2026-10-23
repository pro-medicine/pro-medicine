package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.intake.repository.IntakeScheduleRepository;
import com.ucamp.app.domain.notification.entity.NotificationType;
import com.ucamp.app.domain.notification.event.IntakeCheckedEvent;
import com.ucamp.app.domain.notification.repository.FriendNotificationTargetRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 친구 복용 완료 알림(FRIEND_GOAL_ACHIEVED).
 * 사용자가 오늘 먹어야 할 일정을 모두 복용하면, 그 사용자를 팔로우하는 사람(수락, 취소 안 함)에게 알림을 만든다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendGoalNotificationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final IntakeScheduleRepository intakeScheduleRepository;
    private final FriendNotificationTargetRepository friendNotificationTargetRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * 복용 체크가 DB에 저장(커밋)된 뒤에 실행된다.
     * - 복용 체크가 실패해 롤백되면 실행되지 않는다.
     * - 여기서 오류가 나도 이미 저장된 복용 체크에는 영향이 없다.
     * - 원래 트랜잭션은 이미 끝났으므로, 알림 저장을 위해 새 트랜잭션(REQUIRES_NEW)을 연다.
     * - 오늘 날짜의 체크만 처리한다. (지난 날짜를 체크해도 "오늘 완료" 알림은 보내지 않음)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onIntakeChecked(IntakeCheckedEvent event) {
        if (!event.date().equals(LocalDate.now(KST))) {
            return;
        }
        try {
            notifyIfGoalAchieved(event.userId(), event.date());
        } catch (Exception e) {
            log.error("친구 복용 완료 알림 처리 실패 userId={}, date={}", event.userId(), event.date(), e);
        }
    }

    /**
     * userId가 date에 먹어야 할 일정을 모두 복용했으면 팔로워들에게 알림을 만든다.
     * 테스트에서 날짜를 고정해 호출할 수 있도록 이벤트 처리와 분리했다.
     */
    public void notifyIfGoalAchieved(Long userId, LocalDate date) {
        if (!isGoalAchieved(userId, date)) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isShareIntakeStatus()) {
            return;   // 복용 현황을 공유하지 않는 사용자는 알림을 보내지 않음
        }

        List<Long> followerIds = friendNotificationTargetRepository.findAcceptedFollowerIds(userId);
        String title = title(user.getNickname());

        for (Long followerId : followerIds) {
            notificationService.create(
                    followerId,
                    NotificationType.FRIEND_GOAL_ACHIEVED,
                    title,
                    userId,   // 알림을 일으킨 사람 (복용 완료한 친구)
                    null,
                    null,
                    dedupKey(followerId, userId, date));
        }

        if (!followerIds.isEmpty()) {
            log.info("친구 복용 완료 알림 처리 {}건 (userId={}, date={})", followerIds.size(), userId, date);
        }
    }

    /**
     * 오늘 먹어야 할 일정이 1개 이상이고, 그 일정을 모두 복용(TAKEN)했는지.
     */
    boolean isGoalAchieved(Long userId, LocalDate date) {
        long total = intakeScheduleRepository.countTodaySchedules(userId, date);
        if (total == 0) {
            return false;
        }
        return intakeScheduleRepository.countTodayTakenSchedules(userId, date) == total;
    }

    /**
     * 예: "김민지님이 오늘 영양제 섭취를 완료했어요"
     */
    static String title(String nickname) {
        return "%s님이 오늘 영양제 섭취를 완료했어요".formatted(nickname);
    }

    /**
     * 예: "FRIEND_GOAL:2:1:2026-10-23" (받는 사람 2, 완료한 사람 1). 체크를 해제했다 다시 해도 하루 1번만 생성된다.
     */
    static String dedupKey(Long receiverId, Long achieverId, LocalDate date) {
        return "FRIEND_GOAL:%d:%d:%s".formatted(receiverId, achieverId, date);
    }
}
