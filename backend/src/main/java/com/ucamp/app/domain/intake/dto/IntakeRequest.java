package com.ucamp.app.domain.intake.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

// 복용 체크할 때 화면이 보내는 { taken, date }
@Getter @Setter
public class IntakeRequest {
    private Long scheduleId;
    private boolean taken;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;   // 없으면 오늘
}