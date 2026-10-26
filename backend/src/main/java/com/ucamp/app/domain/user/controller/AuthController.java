package com.ucamp.app.domain.user.controller;

import com.ucamp.app.domain.user.dto.CsrfResponse;
import com.ucamp.app.domain.user.dto.LoginRequest;
import com.ucamp.app.domain.user.dto.SignupRequest;
import com.ucamp.app.domain.user.security.SessionAuth;
import com.ucamp.app.domain.user.service.AuthService;
import com.ucamp.app.domain.user.dto.UserResponse;
import com.ucamp.app.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final SessionAuth sessionAuth;

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<CsrfResponse>> csrf(HttpServletRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(new CsrfResponse(sessionAuth.csrfToken(request))));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest body,
                                                            @RequestHeader(value = "X-CSRF-Token", required = false) String csrf,
                                                            HttpServletRequest request) {
        sessionAuth.requireCsrf(request, csrf);
        UserResponse created = authService.signup(body);
        sessionAuth.login(request, created.userId(), false);
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(created));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequest body,
                                                           @RequestHeader(value = "X-CSRF-Token", required = false) String csrf,
                                                           HttpServletRequest request) {
        sessionAuth.requireCsrf(request, csrf);
        UserResponse user = authService.login(body);
        sessionAuth.login(request, user.userId(), body.rememberMe());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(user));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(HttpServletRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(authService.me(sessionAuth.requireUser(request))));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrf,
            HttpServletRequest request) {
        sessionAuth.requireCsrf(request, csrf);
        sessionAuth.logout(request);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(null));
    }
}
