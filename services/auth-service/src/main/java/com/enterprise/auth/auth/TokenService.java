package com.enterprise.auth.auth;

import com.enterprise.auth.keys.SigningKeyService;
import com.enterprise.auth.user.User;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Issues RS256-signed access tokens. Other services verify them with the public JWKS. */
@Service
public class TokenService {

    private final SigningKeyService signingKeyService;
    private final String issuer;
    private final Duration expiration;
    private final Duration adminExpiration;

    public TokenService(SigningKeyService signingKeyService,
                        @Value("${security.jwt.issuer:enterprise-auth}") String issuer,
                        @Value("${security.jwt.expiration-ms:86400000}") long expirationMs,
                        @Value("${security.jwt.admin-expiration-ms:7200000}") long adminExpirationMs) {
        this.signingKeyService = signingKeyService;
        this.issuer = issuer;
        this.expiration = Duration.ofMillis(expirationMs);
        this.adminExpiration = Duration.ofMillis(adminExpirationMs);
    }

    /**
     * storeName is added for sellers so other services can show the store without calling back.
     * Admin tokens live shorter (2 hours by default): the most powerful token gets the smallest window.
     */
    public IssuedToken issue(User user, String storeName) {
        Duration lifetime = user.isAdmin() ? adminExpiration : expiration;
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .header().keyId(signingKeyService.activeKeyId()).and()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)));
        if (storeName != null) {
            builder.claim("storeName", storeName);
        }
        String token = builder.signWith(signingKeyService.activePrivateKey(), Jwts.SIG.RS256).compact();
        return new IssuedToken(token, lifetime.toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
