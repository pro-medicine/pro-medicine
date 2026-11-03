package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.notification.entity.NotificationType;
import com.ucamp.app.domain.notification.event.GuardianAcceptedEvent;
import com.ucamp.app.domain.notification.event.GuardianRequestedEvent;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 안부지킴이 신청·수락 알림. (팔로우 알림 FollowNotificationService와 같은 방식)
 * - 신청(GUARDIAN_REQUESTED): 신청을 받은 사람에게 "OO님이 안부지킴이를 신청했어요" → 알림에서 바로 수락·거절
 * - 수락(GUARDIAN_ACCEPTED): 신청한 사람에게 "OO님이 안부지킴이 신청을 수락했어요. N일 이상 접속하지 않으면 알려드릴게요"
 * - 거절은 알림을 보내지 않는다. (팔로우 거절과 같음)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardianNotificationService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * 신청이 DB에 저장(커밋)된 뒤에 실행된다. 여기서 오류가 나도 신청에는 영향이 없다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onGuardianRequested(GuardianRequestedEvent event) {
        try {
            notifyRequested(event.settingId(), event.guardianUserId(), event.targetUserId());
        } catch (Exception e) {
            log.error("안부지킴이 신청 알림 처리 실패 settingId={}", event.settingId(), e);
        }
    }

    /**
     * 수락이 DB에 저장(커밋)된 뒤에 실행된다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onGuardianAccepted(GuardianAcceptedEvent event) {
        try {
            notifyAccepted(event.settingId(), event.guardianUserId(), event.targetUserId(), event.inactivityDays());
        } catch (Exception e) {
            log.error("안부지킴이 수락 알림 처리 실패 settingId={}", event.settingId(), e);
        }
    }

    /**
     * 신청을 받은 사람(targetUserId)에게 알림. 알림을 일으킨 사람 = 신청한 사람(guardianUserId).
     * 프론트는 actorUserId로 "나를 지키려는 사람 목록"에서 대기 중인 신청을 찾아 수락·거절한다.
     */
    public void notifyRequested(Long settingId, Long guardianUserId, Long targetUserId) {
        User guardian = userRepository.findById(guardianUserId).orElse(null);
        if (guardian == null) {
            return;
        }
        notificationService.create(
                targetUserId,
                NotificationType.GUARDIAN_REQUESTED,
                requestedTitle(guardian.getNickname()),
                guardianUserId,
                null,
                null,
                requestedDedupKey(targetUserId, settingId));
    }

    /**
     * 신청한 사람(guardianUserId)에게 알림. 알림을 일으킨 사람 = 수락한 사람(targetUserId).
     */
    public void notifyAccepted(Long settingId, Long guardianUserId, Long targetUserId, int inactivityDays) {
        User target = userRepository.findById(targetUserId).orElse(null);
        if (target == null) {
            return;
        }
        notificationService.create(
                guardianUserId,
                NotificationType.GUARDIAN_ACCEPTED,
                acceptedTitle(target.getNickname(), inactivityDays),
                targetUserId,
                null,
                null,
                acceptedDedupKey(guardianUserId, settingId));
    }

    /** 예: "김민지님이 안부지킴이를 신청했어요" */
    static String requestedTitle(String nickname) {
        return "%s님이 안부지킴이를 신청했어요".formatted(nickname);
    }

    /** 예: "김민지님이 안부지킴이 신청을 수락했어요. 3일 이상 접속하지 않으면 알려드릴게요" */
    static String acceptedTitle(String nickname, int inactivityDays) {
        return "%s님이 안부지킴이 신청을 수락했어요. %d일 이상 접속하지 않으면 알려드릴게요"
                .formatted(nickname, inactivityDays);
    }

    /**
     * 예: "GUARDIAN_REQUEST:2:15" (받는 사람 2, 설정 15).
     * 거절하면 설정이 지워지고 다시 신청하면 새 설정 id가 생기므로, 재신청은 새 알림으로 온다.
     */
    static String requestedDedupKey(Long receiverId, Long settingId) {
        return "GUARDIAN_REQUEST:%d:%d".formatted(receiverId, settingId);
    }

    /** 예: "GUARDIAN_ACCEPTED:1:15" (받는 사람 1, 설정 15) */
    static String acceptedDedupKey(Long receiverId, Long settingId) {
        return "GUARDIAN_ACCEPTED:%d:%d".formatted(receiverId, settingId);
    }
}
