package com.ucamp.app.domain.user.security;

import com.ucamp.app.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No DB/Redis container required: verifies cookie controls and the cookie-CSRF guard. */
class RefreshSecurityUnitTest {
    @Test
    void cookieIsHttpOnlyAndBoundToAuthPath() {
        RefreshCookieFactory cookies = new RefreshCookieFactory(false, "Lax");
        String setCookie = cookies.issue(new RefreshTokenService.Issued("secret", 60));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
        assertTrue(setCookie.contains("Max-Age=60"));
        assertFalse(setCookie.contains("Secure"));
        assertTrue(cookies.clear().contains("Max-Age=0"));
    }

    @Test
    void crossSiteCookieRequiresSecure() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshCookieFactory(false, "None"));
        assertTrue(new RefreshCookieFactory(true, "None")
                .issue(new RefreshTokenService.Issued("secret", 60)).contains("Secure"));
    }

    @Test
    void rejectMissingOrUntrustedOrigin() {
        RefreshOriginGuard guard = new RefreshOriginGuard("http://localhost:5173");
        HttpServletRequest request = mock(HttpServletRequest.class);
        assertThrows(BusinessException.class, () -> guard.requireTrustedOrigin(request));
        when(request.getHeader("Origin")).thenReturn("https://attacker.example");
        assertThrows(BusinessException.class, () -> guard.requireTrustedOrigin(request));
        when(request.getHeader("Origin")).thenReturn("http://localhost:5173");
        assertDoesNotThrow(() -> guard.requireTrustedOrigin(request));
    }
}
