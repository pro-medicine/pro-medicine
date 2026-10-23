package com.ucamp.app.domain.product.controller;

import com.ucamp.app.domain.product.dto.NutritionResponse;
import com.ucamp.app.domain.product.service.NutritionService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/{userId}/nutrition")
public class NutritionController {
    private final NutritionService nutritionService;

    @GetMapping
    public ResponseEntity<ApiResponse<NutritionResponse>> getNutrition(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(nutritionService.getNutrition(userId)));
    }
}
