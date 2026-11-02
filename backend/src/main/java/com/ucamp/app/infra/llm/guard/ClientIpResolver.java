package com.ucamp.app.infra.llm.guard;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 클라이언트 IP 추출 (V5 보안 게스트 rate limit).
 * X-Real-IP만 사용한다 (X-Forwarded-For는 클라이언트가 스푸핑할 수 있으므로 무시).
 * fallback: request.getRemoteAddr().
 */
public final class ClientIpResolver {
    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        if (request == null) return null;
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.strip();
        }
        return request.getRemoteAddr();
    }
}
