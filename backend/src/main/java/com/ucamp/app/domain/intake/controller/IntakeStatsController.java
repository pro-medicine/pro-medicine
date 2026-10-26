package com.ucamp.app.domain.intake.controller;

import com.ucamp.app.domain.intake.dto.IntakeStatsResponse;
import com.ucamp.app.domain.intake.service.IntakeStatsService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/{userId}/intake-stats")
public class IntakeStatsController {
    private final IntakeStatsService intakeStatsService;

    @GetMapping
    public ResponseEntity<ApiResponse<IntakeStatsResponse>> getStats(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(intakeStatsService.getStats(userId)));
    }
}
