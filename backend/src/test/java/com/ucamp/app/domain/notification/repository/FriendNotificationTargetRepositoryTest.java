package com.ucamp.app.domain.notification.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 친구 알림 받을 사람 조회 테스트.
 * 테스트 데이터는 sql/friend-notification-target-fixture.sql (9201을 팔로우하는 사람 기준)
 */
@SpringBootTest
@Transactional
@Sql("/sql/friend-notification-target-fixture.sql")
class FriendNotificationTargetRepositoryTest {

    @Autowired
    private FriendNotificationTargetRepository friendNotificationTargetRepository;

    @Test
    @DisplayName("나를 팔로우하고 수락된, 취소하지 않은 사람만 조회된다")
    void findsAcceptedFollowers() {
        assertThat(friendNotificationTargetRepository.findAcceptedFollowerIds(9201L))
                .containsExactly(9202L, 9207L);
    }

    @Test
    @DisplayName("대기 중·거절·취소된 팔로우와 반대 방향 팔로우는 제외된다")
    void excludesOtherFollows() {
        assertThat(friendNotificationTargetRepository.findAcceptedFollowerIds(9201L))
                .doesNotContain(9203L, 9204L, 9205L, 9206L);
    }

    @Test
    @DisplayName("나를 팔로우하는 사람이 없으면 빈 목록이다")
    void emptyWhenNoFollowers() {
        assertThat(friendNotificationTargetRepository.findAcceptedFollowerIds(9202L)).isEmpty();
    }
}
