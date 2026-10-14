package com.enterprise.payment.security;

import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;
import java.net.http.HttpClient;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Downloads the auth service's public keys (JWKS) and caches them by key id.
 * No shared secret is needed: tokens are signed with RS256 by the auth service only.
 */
@Component
public class JwksKeyProvider implements KeyResolver {

    private static final Logger log = LoggerFactory.getLogger(JwksKeyProvider.class);
    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(20);

    private final RestClient restClient;
    private final String jwksUri;
    private final Map<String, Key> keys = new ConcurrentHashMap<>();
    private volatile Instant lastAttempt = Instant.EPOCH;

    public JwksKeyProvider(@Value("${security.jwt.jwks-uri}") String jwksUri,
                           @Value("${security.jwt.jwks-timeout:30s}") Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.jwksUri = jwksUri;
    }

    /** Finds the public key for a key id, refreshing the JWKS once if the id is unknown. */
    @Override
    public Key resolve(String keyId) {
        Key key = lookup(keyId);
        if (key == null) {
            refresh();
            key = lookup(keyId);
        }
        if (key == null) {
            if (keys.isEmpty()) {
                throw new KeySourceUnavailableException("Signing keys could not be loaded from " + jwksUri);
            }
            throw new io.jsonwebtoken.security.SecurityException("Unknown signing key id: " + keyId);
        }
        return key;
    }

    private Key lookup(String keyId) {
        if (keyId == null) {
            return keys.size() == 1 ? keys.values().iterator().next() : null;
        }
        return keys.get(keyId);
    }

    private synchronized void refresh() {
        if (Duration.between(lastAttempt, Instant.now()).compareTo(MIN_REFRESH_INTERVAL) < 0) {
            return;
        }
        lastAttempt = Instant.now();
        try {
            String json = restClient.get().uri(jwksUri).retrieve().body(String.class);
            JwkSet jwkSet = Jwks.setParser().build().parse(json);
            for (Jwk<?> jwk : jwkSet.getKeys()) {
                if (jwk instanceof PublicJwk<?> publicJwk && jwk.getId() != null) {
                    keys.put(jwk.getId(), publicJwk.toKey());
                }
            }
            log.info("Loaded {} signing key(s) from {}", keys.size(), jwksUri);
        } catch (Exception ex) {
            log.warn("Could not load signing keys from {}: {}", jwksUri, ex.getMessage());
        }
    }

}
