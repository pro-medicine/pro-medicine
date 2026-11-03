package com.ucamp.app.domain.notification.repository;

import com.ucamp.app.domain.notification.entity.Notification;
import com.ucamp.app.domain.notification.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 메서드 이름의 User_Id = Notification.user 필드의 id (밑줄은 "연관 엔티티 안의 필드"라는 뜻)
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /*
     * 알림 목록 / 안 읽은 개수에서 "지난 복용 알림"은 뺀다.
     * - 복용 알림(INTAKE_REMINDER): 복용 시각(scheduled_at)이 reminderCutoff보다 이전이면 숨김 (1시간 지남)
     * - 복용 놓침 알림(INTAKE_MISSED): 복용 시각이 missedCutoff보다 이전이면 숨김 (놓침 알림이 뜬 뒤 1시간 지남)
     * - scheduled_at이 없는 알림과 다른 종류의 알림은 그대로 보인다.
     * DB에서 지우지 않고 조회에서만 빼므로, 복용 기록·주간 통계에는 영향이 없다.
     */
    String VISIBLE_CONDITION = """
            (n.notificationType NOT IN (:reminderType, :missedType)
               OR n.scheduledAt IS NULL
               OR (n.notificationType = :reminderType AND n.scheduledAt >= :reminderCutoff)
               OR (n.notificationType = :missedType AND n.scheduledAt >= :missedCutoff))
            """;

    // 알림 목록: types에 해당하는 보이는 알림을 최신순으로 (pageable로 개수 제한)
    // excludedIds: 따로 숨길 알림 id (복용을 마친 시각의 복용·놓침 알림). 비어 있으면 안 되므로 없으면 [-1]을 넣는다
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND n.notificationType IN :types"
            + " AND n.notificationId NOT IN :excludedIds AND "
            + VISIBLE_CONDITION + " ORDER BY n.createdAt DESC, n.notificationId DESC")
    List<Notification> findVisible(@Param("userId") Long userId,
                                   @Param("types") Collection<NotificationType> types,
                                   @Param("reminderType") NotificationType reminderType,
                                   @Param("missedType") NotificationType missedType,
                                   @Param("reminderCutoff") LocalDateTime reminderCutoff,
                                   @Param("missedCutoff") LocalDateTime missedCutoff,
                                   @Param("excludedIds") Collection<Long> excludedIds,
                                   Pageable pageable);

    // 종 아이콘 뱃지: 보이는 알림 중 안 읽은 개수 (목록과 같은 조건, 개수 제한 없음)
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.readAt IS NULL"
            + " AND n.notificationId NOT IN :excludedIds AND " + VISIBLE_CONDITION)
    long countVisibleUnread(@Param("userId") Long userId,
                            @Param("reminderType") NotificationType reminderType,
                            @Param("missedType") NotificationType missedType,
                            @Param("reminderCutoff") LocalDateTime reminderCutoff,
                            @Param("missedCutoff") LocalDateTime missedCutoff,
                            @Param("excludedIds") Collection<Long> excludedIds);

    // 같은 알림이 이미 있는지 (친구 챙겨주기를 하루 1번으로 제한할 때 사용)
    boolean existsByDedupKey(String dedupKey);

    // 내 알림인지 확인하면서 조회 (남의 알림을 읽음 처리하지 못하게)
    Optional<Notification> findByNotificationIdAndUser_Id(Long notificationId, Long userId);

    // 모두 읽음
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.user.id = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    // 알림 저장. 같은 dedup_key가 이미 있으면 아무것도 하지 않음 (중복 방지)
    // 주의: Connector/J 기본 설정(useAffectedRows=false)에서는 중복이어도 1이 반환되므로
    //       반환값으로 저장 여부를 판단하지 말 것
    @Modifying
    @Query(value = """
            INSERT INTO notifications
                (user_id, schedule_id, actor_user_id, notification_type, title,
                 scheduled_at, sent_at, dedup_key, reference_id, created_at)
            VALUES
                (:userId, :scheduleId, :actorUserId, :type, :title,
                 :scheduledAt, NOW(), :dedupKey, :referenceId, NOW())
            ON DUPLICATE KEY UPDATE notification_id = notification_id
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("scheduleId") Long scheduleId,
                       @Param("actorUserId") Long actorUserId,
                       @Param("type") String type,
                       @Param("title") String title,
                       @Param("scheduledAt") LocalDateTime scheduledAt,
                       @Param("dedupKey") String dedupKey,
                       @Param("referenceId") Long referenceId);
}
