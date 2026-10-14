package com.enterprise.gateway.security;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

/** Endpoints that can be called without a token. Everything else under /api requires a valid JWT. */
final class PublicPaths {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<String> ANY_METHOD = List.of("/", "/.well-known/jwks.json", "/actuator/health/**",
            "/actuator/health", "/actuator/info", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            "/api-docs/**", "/error");
    /** The marketplace can be browsed without an account: products, categories, stores and product reviews. */
    private static final List<String> GET_ONLY = List.of("/api/products", "/api/products/**", "/api/categories",
            "/api/categories/**", "/api/merchants", "/api/merchants/**", "/api/reviews");
    private static final List<String> POST_ONLY = List.of("/api/auth/login", "/api/auth/register");

    private PublicPaths() {
    }

    static boolean isPublic(String method, String path) {
        if (HttpMethod.OPTIONS.matches(method) || matches(ANY_METHOD, path)) {
            return true;
        }
        if (HttpMethod.GET.matches(method) && matches(GET_ONLY, path)) {
            return true;
        }
        return HttpMethod.POST.matches(method) && matches(POST_ONLY, path);
    }

    private static boolean matches(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }
}
