package com.ucamp.app.domain.user.security;

import com.ucamp.app.domain.user.controller.AuthController;
import com.ucamp.app.domain.user.dto.LoginRequest;
import com.ucamp.app.domain.user.dto.UserResponse;
import com.ucamp.app.domain.user.security.JwtTokenProvider;
import com.ucamp.app.domain.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthControllerRefreshTest {
    private AuthService authService;
    private JwtTokenProvider jwt;
    private RefreshTokenService tokens;
    private RefreshCookieFactory cookies;
    private AuthController controller;
    private HttpServletRequest request;
    private UserResponse user;

    @BeforeEach
    void setup() {
        authService = mock(AuthService.class);
        jwt = mock(JwtTokenProvider.class);
        tokens = mock(RefreshTokenService.class);
        cookies = new RefreshCookieFactory(false, "Lax");
        controller = new AuthController(authService, jwt, tokens, cookies,
                new RefreshOriginGuard("http://localhost:5173"));
        request = mock(HttpServletRequest.class);
        when(request.getHeader("Origin")).thenReturn("http://localhost:5173");
        user = new UserResponse(6L, "test@example.com", "테스트", false, LocalDateTime.now());
        when(jwt.createAccessToken(6L)).thenReturn("jwt-value");
        when(jwt.getExpiresInSeconds()).thenReturn(900L);
    }

    @Test
    void loginSetsCookieAndKeepsExistingResponseContract() {
        when(authService.login(any())).thenReturn(user);
        when(tokens.issue(6L, true)).thenReturn(new RefreshTokenService.Issued("refresh-value", 2592000));
        var result = controller.login(new LoginRequest("test@example.com", "pw", true), null);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("HttpOnly"));
        assertTrue(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("Max-Age=2592000"));
        assertEquals("jwt-value", result.getBody().getData().accessToken());
        verify(tokens).issue(6L, true);
    }

    @Test
    void refreshRotatesAndReissuesAccessToken() {
        when(tokens.rotate("old")).thenReturn(new RefreshTokenService.Rotated(
                6L, new RefreshTokenService.Issued("new", 3600)));
        when(authService.me(6L)).thenReturn(user);
        var result = controller.refresh(request, "old");
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("new"));
        verify(tokens).rotate("old");
    }

    @Test
    void logoutRevokesAndExpiresCookie() {
        var result = controller.logout(request, "previous");
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
        verify(tokens).revoke("previous");
    }


    @Test
    void refreshWithoutCookieReturns401() {
        when(tokens.rotate((String) null))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        var result = controller.refresh(request, null);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertTrue(result.getHeaders()
                .getFirst(HttpHeaders.SET_COOKIE)
                .contains("Max-Age=0"));
    }

    @Test
    void refreshWithInvalidTokenReturns401() {
        when(tokens.rotate("invalid-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        var result = controller.refresh(request, "invalid-token");

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertTrue(result.getHeaders()
                .getFirst(HttpHeaders.SET_COOKIE)
                .contains("Max-Age=0"));
    }

    @Test
    void refreshWithUntrustedOriginReturns403() {
        when(request.getHeader("Origin"))
                .thenReturn("https://attacker.example");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> controller.refresh(request, "some-token")
        );

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(tokens, never()).rotate(anyString());
    }

}
