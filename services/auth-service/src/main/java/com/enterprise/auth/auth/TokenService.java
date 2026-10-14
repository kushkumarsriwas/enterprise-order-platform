package com.enterprise.auth.auth;

import com.enterprise.auth.keys.SigningKeyService;
import com.enterprise.auth.user.User;
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

    public TokenService(SigningKeyService signingKeyService,
                        @Value("${security.jwt.issuer:enterprise-auth}") String issuer,
                        @Value("${security.jwt.expiration-ms:86400000}") long expirationMs) {
        this.signingKeyService = signingKeyService;
        this.issuer = issuer;
        this.expiration = Duration.ofMillis(expirationMs);
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);
        String token = Jwts.builder()
                .header().keyId(signingKeyService.activeKeyId()).and()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKeyService.activePrivateKey(), Jwts.SIG.RS256)
                .compact();
        return new IssuedToken(token, expiration.toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
