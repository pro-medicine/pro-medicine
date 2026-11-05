
package com.ucamp.app.domain.user.controller;

import com.ucamp.app.domain.user.dto.NicknameUpdateRequest;
import com.ucamp.app.domain.user.dto.ShareIntakeRequest;
import com.ucamp.app.domain.user.dto.UserResponse;
import com.ucamp.app.domain.user.dto.WithdrawalRequest;
import com.ucamp.app.domain.user.service.AccountWithdrawalService;
import com.ucamp.app.domain.user.security.RefreshCookieFactory;
import com.ucamp.app.domain.user.service.UserService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;
    private final AccountWithdrawalService accountWithdrawalService;
    private final RefreshCookieFactory refreshCookieFactory;

    // 사용자 정보 조회 - 본인만 허용
    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getUser(userId))
        );
    }

    // 닉네임 변경 - 본인만 허용
    @PatchMapping("/nickname")
    public ResponseEntity<ApiResponse<UserResponse>> changeNickname(
            @RequestBody NicknameUpdateRequest request,
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        userService.changeNickname(userId, request.nickname())
                )
        );
    }

    @PatchMapping("/share-intake")
    public ResponseEntity<ApiResponse<UserResponse>> changeShareIntake(
            @CurrentUserId Long userId,
            @Valid @RequestBody ShareIntakeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(userService.changeShareIntakeStatus(userId, request.shared())));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @CurrentUserId Long userId,
            @Valid @RequestBody WithdrawalRequest request
    ) {
        accountWithdrawalService.withdraw(userId, request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(null));
    }
}
