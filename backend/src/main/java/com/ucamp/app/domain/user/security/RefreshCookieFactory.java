package com.ucamp.app.domain.user.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookieFactory {
    public static final String NAME = "yakssok_refresh";
    private static final String PATH = "/api/v1/auth";

    private final boolean secure;
    private final String sameSite;

    public RefreshCookieFactory(
            @Value("${app.auth.cookie-secure:false}") boolean secure,
            @Value("${app.auth.cookie-same-site:Lax}") String sameSite
    ) {
        if (!"Lax".equals(sameSite) && !"Strict".equals(sameSite) && !"None".equals(sameSite)) {
            throw new IllegalArgumentException("Invalid refresh cookie SameSite mode");
        }
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None requires Secure=true");
        }
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public String issue(RefreshTokenService.Issued token) {
        return cookie(token.value(), token.maxAgeSeconds()).toString();
    }

    public String clear() {
        return cookie("", 0).toString();
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(PATH)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }
}
