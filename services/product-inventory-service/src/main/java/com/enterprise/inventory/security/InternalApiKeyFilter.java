package com.enterprise.inventory.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects the service-to-service API: every {@code /internal/**} request must carry the shared
 * {@link InternalApiKey}. When the calling service also forwards the user's JWT, that user is authenticated by
 * {@link JwtAuthenticationFilter}; background jobs call without a user and are authenticated as
 * {@link AuthenticatedUser#SERVICE}.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final InternalApiKey internalApiKey;

    public InternalApiKeyFilter(InternalApiKey internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!internalApiKey.matches(request.getHeader(InternalApiKey.HEADER))) {
            JsonErrorWriter.write(request, response, 403, "Forbidden", "Internal endpoint");
            return;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    AuthenticatedUser.SERVICE, null, List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
        }
        chain.doFilter(request, response);
    }
}
