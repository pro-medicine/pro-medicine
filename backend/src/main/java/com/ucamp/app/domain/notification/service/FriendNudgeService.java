package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.intake.repository.IntakeScheduleRepository;
import com.ucamp.app.domain.notification.entity.NotificationType;
import com.ucamp.app.domain.notification.repository.FriendNotificationTargetRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 친구 챙겨주기(FRIEND_NUDGE): 친구에게 "영양제 챙겨 드세요" 알림을 보낸다.
 * - 내가 팔로우 중인(수락된) 친구에게만 보낼 수 있다.
 * - 같은 친구에게는 하루 1번만 보낼 수 있다. (dedup_key에 날짜를 넣음)
 * - 친구가 복용 현황을 공유하고 있고 오늘 복용을 모두 끝냈으면 보내지 않는다.
 *   (공유하지 않는 친구는 완료 여부를 알려주면 안 되므로 확인하지 않고 보낸다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendNudgeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final FriendNotificationTargetRepository friendNotificationTargetRepository;
    private final IntakeScheduleRepository intakeScheduleRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public void nudge(Long senderId, Long targetUserId) {
        nudge(senderId, targetUserId, LocalDate.now(KST));
    }

    /**
     * 테스트에서 날짜를 고정할 수 있도록 날짜를 받는 메서드를 따로 둔다.
     */
    @Transactional
    public void nudge(Long senderId, Long targetUserId, LocalDate today) {
        if (senderId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!friendNotificationTargetRepository.isAcceptedFollowing(senderId, targetUserId)) {
            throw new BusinessException(ErrorCode.NUDGE_NOT_FOLLOWING);
        }
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String dedupKey = dedupKey(targetUserId, senderId, today);
        if (notificationService.exists(dedupKey)) {
            throw new BusinessException(ErrorCode.NUDGE_ALREADY_SENT);
        }
        if (target.isShareIntakeStatus() && isAllTaken(targetUserId, today)) {
            throw new BusinessException(ErrorCode.NUDGE_ALREADY_COMPLETED);
        }

        // 버튼을 동시에 두 번 눌러도 dedup_key 유니크 제약 때문에 알림은 1개만 저장된다
        notificationService.create(
                targetUserId,
                NotificationType.FRIEND_NUDGE,
                title(sender.getNickname()),
                senderId,   // 알림을 일으킨 사람 (챙겨준 친구)
                null,
                null,
                dedupKey);
        log.info("친구 챙겨주기 알림 senderId={}, targetUserId={}", senderId, targetUserId);
    }

    // 오늘 먹을 일정이 있고 모두 복용했는지 (FriendGoalNotificationService와 같은 기준)
    private boolean isAllTaken(Long userId, LocalDate date) {
        long total = intakeScheduleRepository.countTodaySchedules(userId, date);
        return total > 0 && intakeScheduleRepository.countTodayTakenSchedules(userId, date) == total;
    }

    /** 예: "김민지님이 영양제 챙겨 드시래요" */
    static String title(String nickname) {
        return "%s님이 영양제 챙겨 드시래요".formatted(nickname);
    }

    /** 예: "NUDGE:2:1:2026-11-03" (받는 사람 2, 보낸 사람 1). 같은 친구에게 하루 1번 */
    static String dedupKey(Long receiverId, Long senderId, LocalDate date) {
        return "NUDGE:%d:%d:%s".formatted(receiverId, senderId, date);
    }
}
