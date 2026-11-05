package com.ucamp.app.domain.product.controller;

import com.ucamp.app.domain.product.dto.CatalogProductSearchResponse;
import com.ucamp.app.domain.product.dto.IntakeTipResponse;
import com.ucamp.app.domain.product.service.CatalogProductService;
import com.ucamp.app.domain.product.service.IntakeTipService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 영양제 등록 페이지의 제품 검색: GET /api/v1/catalog-products?keyword=비타민
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/catalog-products")
public class CatalogProductController {
    private final CatalogProductService catalogProductService;
    private final IntakeTipService intakeTipService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CatalogProductSearchResponse>>> search(
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success(catalogProductService.search(keyword)));
    }

    @GetMapping("/{catalogProductId}/intake-tips")
    public ResponseEntity<ApiResponse<List<IntakeTipResponse>>> intakeTips(
            @PathVariable Long catalogProductId) {
        return ResponseEntity.ok(ApiResponse.success(intakeTipService.getTips(catalogProductId)));
    }

    @GetMapping("/supplements/{supplementId}/intake-tips")
    public ResponseEntity<ApiResponse<List<IntakeTipResponse>>> supplementIntakeTips(
            @PathVariable Long supplementId) {
        return ResponseEntity.ok(ApiResponse.success(intakeTipService.getSupplementTips(supplementId)));
    }
}