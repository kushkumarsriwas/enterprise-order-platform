package com.enterprise.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limit per client IP to slow down password guessing: marketplace sign-in/sign-up, and a much
 * stricter limit for the administrator console sign-in. (With several gateway instances the counters would live
 * in Redis so that all instances share them.)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String ADMIN_LOGIN = "/api/admin/auth/login";

    private final int maxPerMinute;
    private final int adminMaxPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(@Value("${app.rate-limit.auth-per-minute:20}") int maxPerMinute,
                                @Value("${app.rate-limit.admin-login-per-minute:5}") int adminMaxPerMinute) {
        this.maxPerMinute = maxPerMinute;
        this.adminMaxPerMinute = adminMaxPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !(request.getRequestURI().startsWith("/api/auth/")
                || request.getRequestURI().equals(ADMIN_LOGIN));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long minute = Instant.now().getEpochSecond() / 60;
        if (windows.size() > 10_000) {
            windows.clear();
        }
        boolean admin = request.getRequestURI().equals(ADMIN_LOGIN);
        Window window = windows.compute((admin ? "admin:" : "") + clientIp(request),
                (ip, current) -> current == null || current.minute != minute ? new Window(minute) : current);
        if (window.count.incrementAndGet() > (admin ? adminMaxPerMinute : maxPerMinute)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Too many login attempts. Please wait a minute and try again.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
