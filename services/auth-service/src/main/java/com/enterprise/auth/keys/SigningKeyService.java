package com.enterprise.auth.keys;

import com.enterprise.auth.security.KeyResolver;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.RsaPublicJwk;
import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns the RSA key pair(s): loads them from the database, creates one on first start,
 * signs with the newest key and publishes all public keys as a JWK Set.
 */
@Service
public class SigningKeyService implements KeyResolver {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyService.class);

    private final SigningKeyRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final Map<String, RSAPublicKey> publicKeys = new LinkedHashMap<>();
    private volatile String activeKeyId;
    private volatile PrivateKey activePrivateKey;

    public SigningKeyService(SigningKeyRepository repository, TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
    }

    @PostConstruct
    void init() {
        List<SigningKey> stored = repository.findAllByOrderByCreatedAtDesc();
        if (stored.isEmpty()) {
            SigningKey created = transactionTemplate.execute(status -> repository.save(generate()));
            stored = List.of(created);
            log.info("Generated new RSA signing key {}", created.getKeyId());
        }
        for (SigningKey key : stored) {
            publicKeys.put(key.getKeyId(), decodePublic(key.getPublicKey()));
        }
        SigningKey newest = stored.get(0);
        activeKeyId = newest.getKeyId();
        activePrivateKey = decodePrivate(newest.getPrivateKey());
        log.info("Using signing key {} ({} key(s) published)", activeKeyId, publicKeys.size());
    }

    public String activeKeyId() {
        return activeKeyId;
    }

    public PrivateKey activePrivateKey() {
        return activePrivateKey;
    }

    @Override
    public Key resolve(String keyId) {
        RSAPublicKey key = keyId == null ? publicKeys.get(activeKeyId) : publicKeys.get(keyId);
        if (key == null) {
            throw new io.jsonwebtoken.security.SecurityException("Unknown signing key id: " + keyId);
        }
        return key;
    }

    /** JSON Web Key Set (RFC 7517) with the public keys only. */
    public String jwksJson() {
        String keys = publicKeys.entrySet().stream()
                .map(entry -> {
                    RsaPublicJwk jwk = Jwks.builder().key(entry.getValue()).id(entry.getKey()).build();
                    return Jwks.json(jwk);
                })
                .collect(Collectors.joining(","));
        return "{\"keys\":[" + keys + "]}";
    }

    private static SigningKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            Base64.Encoder encoder = Base64.getEncoder();
            return new SigningKey(UUID.randomUUID().toString(),
                    encoder.encodeToString(pair.getPublic().getEncoded()),
                    encoder.encodeToString(pair.getPrivate().getEncoded()));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate RSA key pair", ex);
        }
    }

    private static RSAPublicKey decodePublic(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid stored public key", ex);
        }
    }

    private static PrivateKey decodePrivate(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid stored private key", ex);
        }
    }
}
