package com.ucamp.app.domain.user.security;

import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Browser-initiated cookie operations require an explicitly trusted Origin. */
@Component
public class RefreshOriginGuard {
    private final Set<String> allowedOrigins;

    public RefreshOriginGuard(@Value("${app.auth.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
                              String csv) {
        allowedOrigins = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public void requireTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins.contains(origin)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
