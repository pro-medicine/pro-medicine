package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.notification.entity.NotificationType;
import com.ucamp.app.domain.notification.event.FollowAcceptedEvent;
import com.ucamp.app.domain.notification.event.FollowRequestedEvent;
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
 * 팔로우 관련 알림.
 * - 팔로우 요청(NEW_FOLLOWER): 요청을 받은 사람에게 "OO님이 팔로우를 요청했어요"
 * - 팔로우 수락(FOLLOW_ACCEPTED): 요청한 사람에게 "OO님이 팔로우 요청을 수락했어요"
 * - 거절은 알림을 보내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowNotificationService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * 팔로우 요청이 DB에 저장(커밋)된 뒤에 실행된다.
     * 요청 저장이 실패해 롤백되면 실행되지 않고, 여기서 오류가 나도 팔로우 요청에는 영향이 없다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFollowRequested(FollowRequestedEvent event) {
        try {
            notifyFollowRequested(event.followerId(), event.followingId());
        } catch (Exception e) {
            log.error("팔로우 요청 알림 처리 실패 followerId={}, followingId={}",
                    event.followerId(), event.followingId(), e);
        }
    }

    /**
     * 팔로우 수락이 DB에 저장(커밋)된 뒤에 실행된다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFollowAccepted(FollowAcceptedEvent event) {
        try {
            notifyFollowAccepted(event.followerId(), event.followingId());
        } catch (Exception e) {
            log.error("팔로우 수락 알림 처리 실패 followerId={}, followingId={}",
                    event.followerId(), event.followingId(), e);
        }
    }

    /**
     * 요청을 받은 사람(followingId)에게 알림을 만든다. 알림을 일으킨 사람은 요청한 사람(followerId).
     * 테스트에서 직접 호출할 수 있도록 이벤트 처리와 분리했다.
     */
    public void notifyFollowRequested(Long followerId, Long followingId) {
        User follower = userRepository.findById(followerId).orElse(null);
        if (follower == null) {
            return;
        }
        notificationService.create(
                followingId,
                NotificationType.NEW_FOLLOWER,
                requestedTitle(follower.getNickname()),
                followerId,
                null,
                null,
                requestedDedupKey(followingId, followerId));
    }

    /**
     * 요청한 사람(followerId)에게 알림을 만든다. 알림을 일으킨 사람은 수락한 사람(followingId).
     */
    public void notifyFollowAccepted(Long followerId, Long followingId) {
        User following = userRepository.findById(followingId).orElse(null);
        if (following == null) {
            return;
        }
        notificationService.create(
                followerId,
                NotificationType.FOLLOW_ACCEPTED,
                acceptedTitle(following.getNickname()),
                followingId,
                null,
                null,
                acceptedDedupKey(followerId, followingId));
    }

    /** 예: "김민지님이 팔로우를 요청했어요" */
    static String requestedTitle(String nickname) {
        return "%s님이 팔로우를 요청했어요".formatted(nickname);
    }

    /** 예: "김민지님이 팔로우 요청을 수락했어요" */
    static String acceptedTitle(String nickname) {
        return "%s님이 팔로우 요청을 수락했어요".formatted(nickname);
    }

    /**
     * 예: "FOLLOW:2:1" (받는 사람 2, 요청한 사람 1).
     * 같은 사람에게서 온 요청 알림은 한 번만 만든다. (요청 → 취소 → 재요청을 반복해도 알림이 쌓이지 않음)
     */
    static String requestedDedupKey(Long receiverId, Long followerId) {
        return "FOLLOW:%d:%d".formatted(receiverId, followerId);
    }

    /**
     * 예: "FOLLOW_ACCEPTED:1:2" (받는 사람 1, 수락한 사람 2).
     */
    static String acceptedDedupKey(Long receiverId, Long followingId) {
        return "FOLLOW_ACCEPTED:%d:%d".formatted(receiverId, followingId);
    }
}
