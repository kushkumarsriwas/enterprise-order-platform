package com.enterprise.auth.audit;

import jakarta.servlet.http.HttpServletRequest;

/** Who is calling: client IP (first X-Forwarded-For hop added by the gateway / load balancer) and browser. */
public record ClientInfo(String ipAddress, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        String agent = request.getHeader("User-Agent");
        return new ClientInfo(truncate(ip, 64), truncate(agent, 255));
    }

    public static ClientInfo unknown() {
        return new ClientInfo(null, null);
    }

    private static String truncate(String value, int max) {
        return value == null ? null : value.length() <= max ? value : value.substring(0, max);
    }
}
