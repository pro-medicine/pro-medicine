
package com.ucamp.app.domain.product.controller;

import com.ucamp.app.domain.product.dto.MySupplementAddRequest;
import com.ucamp.app.domain.product.dto.MySupplementResponse;
import com.ucamp.app.domain.product.dto.MySupplementScheduleUpdateRequest;
import com.ucamp.app.domain.product.service.MySupplementService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/{userId}/products")
public class MySupplementController {

    private final MySupplementService mySupplementService;

    // 본인 영양제 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<MySupplementResponse>>> getMySupplements(
            @PathVariable Long userId,
            @CurrentUserId Long currentUserId
    ) {
        requireSelf(userId, currentUserId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        mySupplementService.getMySupplements(userId)
                )
        );
    }

    // 본인 영양제 등록
    @PostMapping
    public ResponseEntity<ApiResponse<MySupplementResponse>> addMySupplement(
            @PathVariable Long userId,
            @RequestBody MySupplementAddRequest request,
            @CurrentUserId Long currentUserId
    ) {
        requireSelf(userId, currentUserId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        mySupplementService.addMySupplement(userId, request)
                )
        );
    }

    // 본인 영양제 복용 일정 수정
    @PutMapping("/{userProductId}/schedules")
    public ResponseEntity<ApiResponse<MySupplementResponse>> updateSchedules(
            @PathVariable Long userId,
            @PathVariable Long userProductId,
            @RequestBody MySupplementScheduleUpdateRequest request,
            @CurrentUserId Long currentUserId
    ) {
        requireSelf(userId, currentUserId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        mySupplementService.updateSchedules(
                                userId,
                                userProductId,
                                request
                        )
                )
        );
    }

    // 본인 영양제 삭제
    @DeleteMapping("/{userProductId}")
    public ResponseEntity<Void> removeMySupplement(
            @PathVariable Long userId,
            @PathVariable Long userProductId,
            @CurrentUserId Long currentUserId
    ) {
        requireSelf(userId, currentUserId);

        mySupplementService.removeMySupplement(userId, userProductId);

        return ResponseEntity.noContent().build();
    }

    // 요청 사용자와 JWT 인증 사용자 일치 여부 확인
    private void requireSelf(Long userId, Long currentUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        if (!userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
