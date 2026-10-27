package com.ucamp.app.domain.intake.controller;

import com.ucamp.app.domain.intake.dto.IntakeRequest;
import com.ucamp.app.domain.intake.dto.IntakeStatsResponse;
import com.ucamp.app.domain.intake.service.IntakeService;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.home.dto.HomeIntakeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/intakes")
public class IntakeController {
    private final IntakeService intakeService;

    @PostMapping
    public ResponseEntity<Void> updateIntake(
            @RequestParam Long userId,
            @RequestBody IntakeRequest req
    ) {
        intakeService.updateIntake(userId, req.getDate(), req.isTaken());
        return ResponseEntity.noContent().build();   // 204
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<IntakeStatsResponse>> getStats(@RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success(intakeService.getStats(userId)));
    }
}
