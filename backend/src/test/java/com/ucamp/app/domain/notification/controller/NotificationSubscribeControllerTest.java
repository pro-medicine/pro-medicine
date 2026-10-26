package com.ucamp.app.domain.notification.controller;

import com.ucamp.app.domain.notification.service.NotificationService;
import com.ucamp.app.domain.notification.service.NotificationSseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실시간 알림 구독 API 테스트.
 * 컨트롤러 계층만 띄우고(@WebMvcTest, DB 불필요), SSE 서비스는 실제 객체를 사용한다.
 */
@WebMvcTest(NotificationController.class)
@Import(NotificationSseService.class)
class NotificationSubscribeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationSseService notificationSseService;

    @MockitoBean
    private NotificationService notificationService;   // 컨트롤러 생성에 필요 (이 테스트에서는 사용 안 함)

    @Test
    @DisplayName("구독하면 text/event-stream 연결이 열리고 connect 이벤트를 먼저 받는다")
    void subscribe() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/notifications/subscribe").param("userId", "1"))
                .andExpect(request().asyncStarted())   // 응답을 끝내지 않고 열어 둠
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getContentAsString())
                .contains("event:connect")
                .contains("data:connected");
    }

    @Test
    @DisplayName("구독 후 새 알림 신호를 보내면 같은 연결로 notification 이벤트가 전달된다")
    void receiveNotificationEvent() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/notifications/subscribe").param("userId", "7"))
                .andExpect(request().asyncStarted())
                .andReturn();

        notificationSseService.sendNotification(7L);

        assertThat(result.getResponse().getContentAsString())
                .contains("event:notification")
                .contains("data:new");
    }
}
