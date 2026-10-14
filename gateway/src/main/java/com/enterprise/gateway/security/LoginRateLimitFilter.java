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
 * Simple fixed-window rate limit on login/register per client IP to slow down password guessing.
 * (In production this would live in Redis so that all gateway instances share the counters.)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final int maxPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(@Value("${app.rate-limit.auth-per-minute:20}") int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long minute = Instant.now().getEpochSecond() / 60;
        if (windows.size() > 10_000) {
            windows.clear();
        }
        Window window = windows.compute(clientIp(request),
                (ip, current) -> current == null || current.minute != minute ? new Window(minute) : current);
        if (window.count.incrementAndGet() > maxPerMinute) {
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
