package com.ucamp.app.home.controller;

import com.ucamp.app.home.dto.HomeResponse;
import com.ucamp.app.home.dto.HomeIntakeRequest;
import com.ucamp.app.home.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")  // 로컬 확인용. 팀 공통 CORS 설정이 생기면 삭제
public class HomeController {

    private final HomeService homeService;

    // 홈 화면 데이터: GET /home?userId=1&date=2026-10-21 (date 없으면 오늘)
    @GetMapping
    public HomeResponse getHome(
            @RequestParam Long userId,   // ⚠ 임시: 로그인 연동 후 교체
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return homeService.getHome(userId, orToday(date));
    }

    // 복용 체크 저장: PATCH /home/intake/{scheduleId}?userId=1


    private LocalDate orToday(LocalDate date) {
        return date != null ? date : LocalDate.now(ZoneId.of("Asia/Seoul"));
    }
}