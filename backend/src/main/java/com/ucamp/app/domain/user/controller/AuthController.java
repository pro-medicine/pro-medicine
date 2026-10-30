package com.ucamp.app.domain.user.controller;

import com.ucamp.app.domain.user.dto.AuthResponse;
import com.ucamp.app.domain.user.dto.LoginRequest;
import com.ucamp.app.domain.user.dto.SignupRequest;
import com.ucamp.app.domain.user.dto.UserResponse;
import com.ucamp.app.domain.user.security.JwtTokenProvider;
import com.ucamp.app.domain.user.security.RefreshCookieFactory;
import com.ucamp.app.domain.user.security.RefreshOriginGuard;
import com.ucamp.app.domain.user.security.RefreshTokenService;
import com.ucamp.app.domain.user.service.AuthService;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
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
    private final RefreshTokenService refreshTokens;
    private final RefreshCookieFactory refreshCookies;
    private final RefreshOriginGuard originGuard;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @Valid @RequestBody SignupRequest body,
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) String previousRefreshCookie
    ) {
        UserResponse user = authService.signup(body);
        refreshTokens.revoke(previousRefreshCookie);
        RefreshTokenService.Issued refresh = refreshTokens.issue(user.userId(), false);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(refresh))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(createAuthResponse(user)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest body,
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) String previousRefreshCookie
    ) {
        UserResponse user = authService.login(body);
        refreshTokens.revoke(previousRefreshCookie);
        RefreshTokenService.Issued refresh = refreshTokens.issue(user.userId(), body.rememberMe());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(refresh))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(createAuthResponse(user)));
    }

    /** No bearer header is required: the HttpOnly cookie is the only refresh credential. */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            HttpServletRequest request,
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshCookie
    ) {
        originGuard.requireTrustedOrigin(request);
        try {
            RefreshTokenService.Rotated rotated = refreshTokens.rotate(refreshCookie);
            UserResponse user = authService.me(rotated.userId());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(rotated.token()))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(ApiResponse.success(createAuthResponse(user)));
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.UNAUTHORIZED) throw ex;
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, refreshCookies.clear())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(authService.me(userId)));
    }

    /** Cookie-scoped logout must work even after the access token expires. */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshCookie
    ) {
        originGuard.requireTrustedOrigin(request);
        refreshTokens.revoke(refreshCookie);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(null));
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
