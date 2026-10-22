package com.ucamp.app.domain.health.controller;

import com.ucamp.app.domain.health.dto.DrugResponse;
import com.ucamp.app.domain.health.dto.IngredientResponse;
import com.ucamp.app.domain.health.service.DrugService;
import com.ucamp.app.domain.health.service.IngredientService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 설문 입력에 쓰는 기준 데이터: 약 검색, 성분 코드 목록
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class HealthReferenceController {
    private final DrugService drugService;
    private final IngredientService ingredientService;

    @GetMapping("/drugs")
    public ResponseEntity<ApiResponse<List<DrugResponse>>> searchDrugs(
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success(drugService.search(keyword)));
    }

    @GetMapping("/ingredients")
    public ResponseEntity<ApiResponse<List<IngredientResponse>>> ingredients() {
        return ResponseEntity.ok(ApiResponse.success(ingredientService.list()));
    }
}
