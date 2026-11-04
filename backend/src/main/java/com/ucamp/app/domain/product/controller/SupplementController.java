package com.ucamp.app.domain.product.controller;

import com.ucamp.app.domain.product.dto.SupplementSearchResponse;
import com.ucamp.app.domain.product.service.SupplementService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 영양제 등록 폼 검색 (엑셀 영양제): GET /api/v1/supplements?keyword=비타민D
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/supplements")
public class SupplementController {

    private final SupplementService supplementService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SupplementSearchResponse>>> search(
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success(supplementService.search(keyword)));
    }
}
