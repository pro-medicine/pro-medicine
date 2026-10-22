package com.ucamp.app.home.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class HomeSupplementDto {
    private Long id;        // schedule_id (복용 체크는 '일정' 단위)
    private String timing;  // "아침 식후"
    private String name;
    private String desc;
    private Boolean taken;
}