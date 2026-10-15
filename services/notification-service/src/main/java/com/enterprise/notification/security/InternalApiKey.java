package com.enterprise.notification.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Shared secret that authenticates service-to-service calls to {@code /internal/**} endpoints
 * (sent in the {@value #HEADER} header). Set {@code INTERNAL_API_KEY} to the same value on every service.
 * On Render, where every service uses the same database user, the key is derived from that shared database
 * password when {@code INTERNAL_API_KEY} is not set, so the deployment keeps working without extra settings.
 */
@Component
public class InternalApiKey {

    public static final String HEADER = "X-Internal-Key";

    private final byte[] value;

    public InternalApiKey(@Value("${app.internal.api-key:}") String configured,
                          @Value("${spring.datasource.password:}") String databasePassword) {
        String key;
        if (configured != null && !configured.isBlank()) {
            key = configured.trim();
        } else if (databasePassword != null && !databasePassword.isBlank()) {
            key = "derived-" + sha256("nexora-internal:" + databasePassword);
        } else {
            throw new IllegalStateException("Set INTERNAL_API_KEY (app.internal.api-key) for service-to-service calls");
        }
        this.value = key.getBytes(StandardCharsets.UTF_8);
    }

    public String value() {
        return new String(value, StandardCharsets.UTF_8);
    }

    /** Constant-time comparison, so the key can't be guessed byte by byte from response times. */
    public boolean matches(String candidate) {
        return candidate != null && MessageDigest.isEqual(value, candidate.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
