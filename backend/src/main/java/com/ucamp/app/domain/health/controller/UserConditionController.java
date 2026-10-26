package com.ucamp.app.domain.health.controller;

import com.ucamp.app.domain.health.dto.UserConditionsRequest;
import com.ucamp.app.domain.health.dto.UserConditionsResponse;
import com.ucamp.app.domain.health.service.UserConditionService;
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
@RequestMapping("/api/v1/users/{userId}/conditions")
public class UserConditionController {
    private final UserConditionService userConditionService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserConditionsResponse>> getConditions(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userConditionService.getConditions(userId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserConditionsResponse>> replaceConditions(@PathVariable Long userId,
                                                                                 @RequestBody UserConditionsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userConditionService.replaceConditions(userId, request)));
    }
}
