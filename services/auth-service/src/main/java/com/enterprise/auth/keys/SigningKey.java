package com.enterprise.auth.keys;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * RSA key pair used to sign JWTs. Persisted so tokens stay valid when the service restarts.
 * (In a real bank this private key would live in a vault / HSM, not in a database table.)
 */
@Entity
@Table(name = "jwt_signing_keys")
public class SigningKey {

    @Id
    @Column(name = "key_id", length = 64)
    private String keyId;

    @Column(name = "public_key", nullable = false, length = 1000)
    private String publicKey;

    @Column(name = "private_key", nullable = false, length = 4000)
    private String privateKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SigningKey() {
    }

    public SigningKey(String keyId, String publicKey, String privateKey) {
        this.keyId = keyId;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.createdAt = Instant.now();
    }

    public String getKeyId() {
        return keyId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
