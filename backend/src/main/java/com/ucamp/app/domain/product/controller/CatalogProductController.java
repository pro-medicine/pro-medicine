package com.ucamp.app.domain.product.controller;

import com.ucamp.app.domain.product.dto.CatalogProductSearchResponse;
import com.ucamp.app.domain.product.service.CatalogProductService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 영양제 등록 페이지의 제품 검색: GET /api/v1/catalog-products?keyword=비타민
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/catalog-products")
public class CatalogProductController {
    private final CatalogProductService catalogProductService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CatalogProductSearchResponse>>> search(
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success(catalogProductService.search(keyword)));
    }
}