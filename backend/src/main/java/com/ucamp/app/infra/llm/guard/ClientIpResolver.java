package com.ucamp.app.infra.llm.guard;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class ClientIpResolver {
    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        if (request == null) return null;
        String realIp = request.getHeader("X-Real-IP");
        realIp = realIp == null ? null : realIp.strip();
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (isInternalProxy(realIp) && forwardedFor != null) {
            String[] hops = forwardedFor.split(",");
            if (hops.length >= 2 && realIp.equals(hops[hops.length - 1].strip())) {
                String clientIp = hops[hops.length - 2].strip();
                if (isIpLiteral(clientIp)) {
                    return clientIp;
                }
            }
        }

        if (isIpLiteral(realIp)) return realIp;
        return request.getRemoteAddr();
    }

    private static boolean isInternalProxy(String ip) {
        if (!isIpLiteral(ip)) return false;
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isSiteLocalAddress() || address.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean isIpLiteral(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) return false;
        if (ip.indexOf(':') >= 0) {
            if (!ip.matches("[0-9a-fA-F:.]+")) return false;
            try {
                return InetAddress.getByName(ip) instanceof Inet6Address;
            } catch (UnknownHostException e) {
                return false;
            }
        }

        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            for (int i = 0; i < part.length(); i++) {
                if (part.charAt(i) < '0' || part.charAt(i) > '9') return false;
            }
            if (Integer.parseInt(part) > 255) return false;
        }
        return true;
    }
}
