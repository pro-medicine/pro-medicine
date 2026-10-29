package com.ucamp.app.domain.notification.controller;

import com.ucamp.app.domain.notification.dto.IntakeSummaryResponse;
import com.ucamp.app.domain.notification.dto.IntakeSummaryResponse.NextReminder;
import com.ucamp.app.domain.notification.dto.IntakeSummaryResponse.OnTimeRate;
import com.ucamp.app.domain.notification.service.IntakeSummaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 홈 복용 알림 카드 API가 @CurrentUserId(X-User-Id 헤더)로 사용자를 식별하고 응답 모양이 맞는지 테스트.
 */
@WebMvcTest(IntakeSummaryController.class)
class IntakeSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IntakeSummaryService intakeSummaryService;

    @Test
    @DisplayName("X-User-Id 헤더의 사용자로 다음 알림과 제시간 복용률을 조회한다")
    void summaryForCurrentUser() throws Exception {
        when(intakeSummaryService.getSummary(7L)).thenReturn(new IntakeSummaryResponse(
                new NextReminder(LocalTime.of(12, 30), "LUNCH", 1, List.of("테스트 오메가3")),
                new OnTimeRate(10, 8, 80)));

        mockMvc.perform(get("/api/v1/notifications/intake-summary").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.nextReminder.remindAt").value(startsWith("12:30")))
                .andExpect(jsonPath("$.data.nextReminder.count").value(1))
                .andExpect(jsonPath("$.data.nextReminder.names[0]").value("테스트 오메가3"))
                .andExpect(jsonPath("$.data.onTime.rate").value(80));

        verify(intakeSummaryService).getSummary(7L);
    }

    @Test
    @DisplayName("사용자를 알 수 없으면(헤더 없음) 401을 응답하고 조회하지 않는다")
    void unauthorizedWithoutUser() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/intake-summary"))
                .andExpect(status().isUnauthorized());

        verify(intakeSummaryService, never()).getSummary(any());
    }
}
