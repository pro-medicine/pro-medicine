package com.ucamp.app.domain.health.controller;

import com.ucamp.app.domain.health.dto.HealthProfileRequest;
import com.ucamp.app.domain.health.dto.HealthProfileResponse;
import com.ucamp.app.domain.health.service.HealthProfileService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/{userId}/health-profile")
public class HealthProfileController {
    private final HealthProfileService healthProfileService;

    @GetMapping
    public ResponseEntity<ApiResponse<HealthProfileResponse>> getHealthProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(healthProfileService.getHealthProfile(userId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<HealthProfileResponse>> saveHealthProfile(@PathVariable Long userId,
                                                                               @RequestBody HealthProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(healthProfileService.saveHealthProfile(userId, request)));
    }
}
