package com.enterprise.auth.security;

/** Thrown when the signing keys cannot be obtained (for example, the auth service is still starting). */
public class KeySourceUnavailableException extends RuntimeException {

    public KeySourceUnavailableException(String message) {
        super(message);
    }
}
