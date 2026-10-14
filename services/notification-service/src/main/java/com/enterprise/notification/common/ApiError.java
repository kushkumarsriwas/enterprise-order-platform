package com.enterprise.notification.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/** Uniform error body returned by every endpoint of this service. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message, String path, String correlationId) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, null);
    }
}
