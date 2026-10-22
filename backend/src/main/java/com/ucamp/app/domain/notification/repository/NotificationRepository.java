package com.ucamp.app.domain.notification.repository;

import com.ucamp.app.domain.notification.entity.Notification;
import com.ucamp.app.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 전체 탭: 최근 50개
    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    // 내 복용 / 친구 소식 탭: 해당 타입만 최근 50개
    List<Notification> findTop50ByUserIdAndTypeInOrderByCreatedAtDesc(Long userId, Collection<NotificationType> types);

    // 종 아이콘 뱃지: 안 읽은 개수
    long countByUserIdAndReadAtIsNull(Long userId);

    // 내 알림인지 확인하면서 조회 (남의 알림을 읽음 처리하지 못하게)
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    // 모두 읽음
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    // 알림 저장. 같은 dedup_key가 이미 있으면 아무것도 하지 않음 (중복 방지)
    // 반환값: 1 = 새로 저장됨, 0 = 이미 있던 알림이라 건너뜀
    @Modifying
    @Query(value = """
            INSERT INTO notifications
                (user_id, schedule_id, actor_user_id, notification_type, title,
                 scheduled_at, sent_at, dedup_key, created_at)
            VALUES
                (:userId, :scheduleId, :actorUserId, :type, :title,
                 :scheduledAt, NOW(), :dedupKey, NOW())
            ON DUPLICATE KEY UPDATE notification_id = notification_id
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("scheduleId") Long scheduleId,
                       @Param("actorUserId") Long actorUserId,
                       @Param("type") String type,
                       @Param("title") String title,
                       @Param("scheduledAt") LocalDateTime scheduledAt,
                       @Param("dedupKey") String dedupKey);
}
