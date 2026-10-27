
package com.ucamp.app.domain.user.controller;

import com.ucamp.app.domain.user.dto.AuthResponse;
import com.ucamp.app.domain.user.dto.LoginRequest;
import com.ucamp.app.domain.user.dto.SignupRequest;
import com.ucamp.app.domain.user.dto.UserResponse;
import com.ucamp.app.domain.user.security.JwtTokenProvider;
import com.ucamp.app.domain.user.service.AuthService;
import com.ucamp.app.global.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @Valid @RequestBody SignupRequest body
    ) {
        UserResponse user = authService.signup(body);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(createAuthResponse(user)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest body
    ) {
        UserResponse user = authService.login(body);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(createAuthResponse(user)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(authService.me(userId)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // 현재 1차 버전은 클라이언트 토큰 폐기 방식.
        // 서버 측 JWT 무효화 기능은 별도 구현 필요.
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private AuthResponse createAuthResponse(UserResponse user) {
        return new AuthResponse(
                jwtTokenProvider.createAccessToken(user.userId()),
                "Bearer",
                jwtTokenProvider.getExpiresInSeconds(),
                user
        );
    }
}
