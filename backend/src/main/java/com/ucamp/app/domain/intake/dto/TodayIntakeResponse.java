package com.ucamp.app.domain.intake.dto;

import lombok.Getter;
import lombok.Setter;

// 홈 화면 "오늘 먹을 영양제" 한 줄
@Getter @Setter
public class TodayIntakeResponse {
    private Long id;        // schedule_id (복용 체크는 '일정' 단위)
    private String timing;  // "아침 식후"
    private String name;
    private String desc;
    private Boolean taken; // 약 하나를 섭취 했는지 T/F
    private String mealSlot;    // BREAKFAST / LUNCH / DINNER (홈 타임라인)
    private String remindAt;    // 알림 시각 "08:00"
    private String completedAt; // 체크한 시각 "08:10" (안 먹었으면 null)
}