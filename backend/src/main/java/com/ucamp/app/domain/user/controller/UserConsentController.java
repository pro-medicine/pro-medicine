package com.ucamp.app.domain.user.controller;

import com.ucamp.app.domain.user.dto.SensitiveConsentResponse;
import com.ucamp.app.domain.user.service.UserConsentService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
public class UserConsentController {
    private final UserConsentService userConsentService;

    // 민감정보(질환·복용 약·임신 여부) 수집 동의. 회원만, 여러 번 호출해도 결과는 같다
    @PostMapping("/sensitive-consent")
    public ResponseEntity<ApiResponse<SensitiveConsentResponse>> agree(@CurrentUserId Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.success(
                SensitiveConsentResponse.from(userConsentService.agreeSensitiveConsent(userId))));
    }
}
