package com.enterprise.gateway.security;

import com.enterprise.gateway.web.CorrelationIdFilter;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Key;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First line of defence: rejects requests to protected APIs without a valid RS256 token before they
 * reach any service. The services validate the same token again (defense in depth).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtValidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtValidationFilter.class);

    private final KeyResolver keyResolver;
    private final String issuer;

    public JwtValidationFilter(KeyResolver keyResolver, @Value("${security.jwt.issuer}") String issuer) {
        this.keyResolver = keyResolver;
        this.issuer = issuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (PublicPaths.isPublic(request.getMethod(), path)) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            reject(request, response, 401, "Unauthorized", "Missing access token");
            return;
        }
        try {
            String username = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(JwsHeader jwsHeader) {
                            return keyResolver.resolve(jwsHeader.getKeyId());
                        }
                    })
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(header.substring(7).trim())
                    .getPayload()
                    .get("username", String.class);
            log.debug("{} {} by {}", request.getMethod(), path, username);
        } catch (KeySourceUnavailableException ex) {
            reject(request, response, 503, "Service Unavailable",
                    "Authentication service is starting up. Please retry in a few seconds.");
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            reject(request, response, 401, "Unauthorized", "Invalid or expired access token");
            return;
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletRequest request, HttpServletResponse response, int status, String error,
                               String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        Object correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status
                + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\",\"path\":\""
                + request.getRequestURI().replace("\"", "") + "\""
                + (correlationId == null ? "" : ",\"correlationId\":\"" + correlationId + "\"") + "}");
    }
}
