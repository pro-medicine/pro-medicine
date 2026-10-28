package com.ucamp.app.domain.notification.controller;

import com.ucamp.app.domain.notification.dto.UnreadCountResponse;
import com.ucamp.app.domain.notification.service.NotificationService;
import com.ucamp.app.domain.notification.service.NotificationSseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 알림 API가 팀 공통 @CurrentUserId로 사용자를 식별하는지 테스트.
 * (로그인 연동 전: X-User-Id 헤더 → CurrentUserResolver → userId)
 */
@WebMvcTest(NotificationController.class)
class NotificationCurrentUserTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationSseService notificationSseService;

    @Test
    @DisplayName("X-User-Id 헤더의 사용자로 안 읽은 알림 개수를 조회한다")
    void currentUserFromHeader() throws Exception {
        when(notificationService.getUnreadCount(7L)).thenReturn(new UnreadCountResponse(3));

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(3));

        verify(notificationService).getUnreadCount(7L);
    }

    @Test
    @DisplayName("사용자를 알 수 없으면(헤더 없음) 401을 응답하고 조회하지 않는다")
    void unauthorizedWithoutUser() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).getUnreadCount(any());
    }
}
