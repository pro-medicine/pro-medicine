package com.ucamp.app.domain.product.controller;

import com.ucamp.app.domain.product.dto.MySupplementAddRequest;
import com.ucamp.app.domain.product.dto.MySupplementResponse;
import com.ucamp.app.domain.product.dto.MySupplementScheduleUpdateRequest;
import com.ucamp.app.domain.product.service.MySupplementService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 내 영양제 목록·등록·삭제: /api/v1/users/{userId}/products
// 컨트롤러는 주소만 받고, 로직은 MySupplementService에서 처리
// 로그인 연동 전이라 경로의 userId를 그대로 사용
// (연동 후 로그인 사용자 기준으로 변경 예정, PR 참고)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/{userId}/products")
public class MySupplementController {
    private final MySupplementService mySupplementService;

    // 영양제 목록: GET /api/v1/users/{userId}/products
    @GetMapping
    public ResponseEntity<ApiResponse<List<MySupplementResponse>>> getMySupplements(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(mySupplementService.getMySupplements(userId)));
    }

    // 영양제 등록: POST /api/v1/users/{userId}/products
    // 본문 {"catalogProductId": 4, "schedules": [{"mealSlot": "BREAKFAST", "mealRelation": "AFTER", "remindAt": "08:00"}]}
    @PostMapping
    public ResponseEntity<ApiResponse<MySupplementResponse>> addMySupplement(@PathVariable Long userId,
                                                                             @RequestBody MySupplementAddRequest request) {
        return ResponseEntity.ok(ApiResponse.success(mySupplementService.addMySupplement(userId, request)));
    }

    // 복용 일정 수정: PUT /api/v1/users/{userId}/products/{userProductId}/schedules, 본문 {"schedules": [...]}
    @PutMapping("/{userProductId}/schedules")
    public ResponseEntity<ApiResponse<MySupplementResponse>> updateSchedules(@PathVariable Long userId,
                                                                             @PathVariable Long userProductId,
                                                                             @RequestBody MySupplementScheduleUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(mySupplementService.updateSchedules(userId, userProductId, request)));
    }

    // 영양제 삭제: DELETE /api/v1/users/{userId}/products/{userProductId}
    @DeleteMapping("/{userProductId}")
    public ResponseEntity<ApiResponse<Void>> removeMySupplement(@PathVariable Long userId,
                                                                @PathVariable Long userProductId) {
        mySupplementService.removeMySupplement(userId, userProductId);
        return ResponseEntity.noContent().build();
    }
}