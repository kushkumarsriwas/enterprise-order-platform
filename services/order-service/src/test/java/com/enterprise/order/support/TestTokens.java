package com.enterprise.order.support;

import com.enterprise.order.security.KeyResolver;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Signs test JWTs with a throw-away RSA key and makes the service trust that key. */
@TestConfiguration
public class TestTokens {

    private static final KeyPair KEYS = generate();

    @Bean
    @Primary
    public KeyResolver testKeyResolver() {
        return keyId -> KEYS.getPublic();
    }

    public static String admin() {
        return token(1L, "admin", "ADMIN");
    }

    public static String customer(long id) {
        return token(id, "customer" + id, "CUSTOMER");
    }

    public static String merchant(long id) {
        return token(id, "merchant" + id, "MERCHANT", "Store " + id);
    }

    public static String token(long userId, String username, String role) {
        return token(userId, username, role, null);
    }

    public static String token(long userId, String username, String role, String storeName) {
        return Jwts.builder()
                .header().keyId("test-key").and()
                .issuer("enterprise-auth")
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("email", username + "@example.com")
                .claim("role", role)
                .claim("storeName", storeName)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
