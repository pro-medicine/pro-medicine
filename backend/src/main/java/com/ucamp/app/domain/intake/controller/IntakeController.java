package com.ucamp.app.domain.intake.controller;

import com.ucamp.app.domain.intake.dto.IntakeRequest;
import com.ucamp.app.domain.intake.dto.IntakeStatsResponse;
import com.ucamp.app.domain.intake.dto.TodayIntakeResponse;
import com.ucamp.app.domain.intake.service.IntakeService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/intakes")
@CrossOrigin(
        origins = "${app.cors.allowed-origins:http://localhost:5173}",
        methods = {RequestMethod.GET, RequestMethod.POST},
        allowedHeaders = {"Content-Type"}
)
public class IntakeController {
    private final IntakeService intakeService;

    @PostMapping
    public ResponseEntity<Void> updateIntake(
            @RequestParam Long userId,
            @RequestBody IntakeRequest req
    ) {
        LocalDate date = req.getDate() != null ? req.getDate() : LocalDate.now(ZoneId.of("Asia/Seoul"));
        intakeService.updateIntake(userId, req.getScheduleId(), date, req.isTaken());
        return ResponseEntity.noContent().build();   // 204
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<IntakeStatsResponse>> getStats(@RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success(intakeService.getStats(userId)));
    }

    // 홈 화면 오늘 먹을 영양제: GET /api/v1/intakes/today?userId=1&date=2026-10-21 (date 없으면 오늘)
    @GetMapping("/today")
    public ResponseEntity<List<TodayIntakeResponse>> getTodayIntakes(
            @RequestParam Long userId,   // TODO: 로그인 연동 후 교체
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate target = date != null ? date : LocalDate.now(ZoneId.of("Asia/Seoul"));
        return ResponseEntity.ok(intakeService.getTodayIntakes(userId, target));
    }
}
