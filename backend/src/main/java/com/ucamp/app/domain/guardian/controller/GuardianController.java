
package com.ucamp.app.domain.guardian.controller;

import com.ucamp.app.domain.guardian.dto.GuardianCreateRequest;
import com.ucamp.app.domain.guardian.dto.GuardianDaysRequest;
import com.ucamp.app.domain.guardian.dto.GuardianResponse;
import com.ucamp.app.domain.guardian.service.GuardianService;
import com.ucamp.app.global.auth.CurrentUserId;
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

    // 안부지킴이 신청
    @PostMapping
    public ResponseEntity<ApiResponse<GuardianResponse>> request(
            @CurrentUserId Long userId,
            @Valid @RequestBody GuardianCreateRequest request) {

        GuardianResponse response = guardianService.request(
                userId,
                request.targetId(),
                request.inactivityDays()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    // 내가 지켜보는 사용자 목록
    @GetMapping("/watching")
    public ResponseEntity<ApiResponse<List<GuardianResponse>>> watching(
            @CurrentUserId Long userId) {

        List<GuardianResponse> responses =
                guardianService.getWatching(userId);

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    // 나를 지키는 사용자 목록
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<GuardianResponse>>> mine(
            @CurrentUserId Long userId) {

        List<GuardianResponse> responses =
                guardianService.getGuardian(userId);

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    // 안부지킴이 신청 수락
    @PatchMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<GuardianResponse>> accept(
            @CurrentUserId Long userId,
            @PathVariable Long id) {

        GuardianResponse response = guardianService.accept(userId, id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 안부지킴이 설정 일수 변경
    @PatchMapping("/{id}/days")
    public ResponseEntity<ApiResponse<GuardianResponse>> changeDays(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @Valid @RequestBody GuardianDaysRequest req) {

        GuardianResponse response = guardianService.changeDays(
                userId,
                id,
                req.inactivityDays()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 안부지킴이 취소
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @CurrentUserId Long userId,
            @PathVariable Long id) {

        guardianService.remove(userId, id);

        return ResponseEntity.noContent().build();
    }
}
