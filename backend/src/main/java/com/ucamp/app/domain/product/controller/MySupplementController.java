package com.ucamp.app.domain.product.controller;

import com.ucamp.app.domain.product.dto.MySupplementResponse;
import com.ucamp.app.domain.product.service.MySupplementService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 내 영양제 목록: GET /api/v1/users/{userId}/products
// 컨트롤러는 주소만 받고, 로직은 MySupplementService에서 처리
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/{userId}/products")
public class MySupplementController {
    private final MySupplementService mySupplementService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MySupplementResponse>>> getMySupplements(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(mySupplementService.getMySupplements(userId)));
    }
}