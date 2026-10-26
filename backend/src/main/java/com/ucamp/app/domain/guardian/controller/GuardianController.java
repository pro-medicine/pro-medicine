package com.ucamp.app.domain.guardian.controller;

import com.ucamp.app.domain.guardian.dto.GuardianCreateRequest;
import com.ucamp.app.domain.guardian.dto.GuardianDaysRequest;
import com.ucamp.app.domain.guardian.dto.GuardianResponse;
import com.ucamp.app.domain.guardian.service.GuardianService;
import com.ucamp.app.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/guardians")
public class GuardianController {

    private final GuardianService guardianService;

    @PostMapping
    public ResponseEntity<ApiResponse<GuardianResponse>> request(
            @RequestParam Long userId,
            @Valid @RequestBody GuardianCreateRequest request) {
        GuardianResponse response = guardianService.request(userId, request.targetId(), request.inactivityDays());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/watching")
    public ResponseEntity<ApiResponse<List<GuardianResponse>>> watching(@RequestParam Long userId) {
        List<GuardianResponse> responses = guardianService.getWatching(userId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<GuardianResponse>>> mine(@RequestParam Long userId) {
        List<GuardianResponse> responses = guardianService.getGuardian(userId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PatchMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<GuardianResponse>> accept(
            @RequestParam Long userId,
            @PathVariable Long id) {
        GuardianResponse response = guardianService.accept(userId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/days")
    public ResponseEntity<ApiResponse<GuardianResponse>> changeDays(
            @RequestParam Long userId, @PathVariable Long id,
            @Valid @RequestBody GuardianDaysRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                guardianService.changeDays(userId, id, req.inactivityDays())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @RequestParam Long userId, @PathVariable Long id) {
        guardianService.remove(userId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
