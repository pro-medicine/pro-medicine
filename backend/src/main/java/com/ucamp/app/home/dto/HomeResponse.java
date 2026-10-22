package com.ucamp.app.home.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

// 프론트 sampleData.js의 SAMPLE_HOME과 같은 모양
@Getter @Setter
public class HomeResponse {
    private String userName;
    private String today;                   // 조회 기준 날짜 (기본: 오늘, 형식 "yyyy-MM-dd")
    private Boolean hasUnreadNotifications;
    private List<HomeSupplementResponse> supplements;
}