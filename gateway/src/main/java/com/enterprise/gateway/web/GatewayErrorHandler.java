package com.enterprise.gateway.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns proxy failures into clean JSON: 503 when a service is down, 504 when it is too slow. */
@RestControllerAdvice
public class GatewayErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(HttpServletRequest request) {
        return body(HttpStatus.NOT_FOUND, "No API route matches " + request.getRequestURI(), request);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> unavailable(ResourceAccessException ex, HttpServletRequest request) {
        boolean timeout = ex.getCause() instanceof SocketTimeoutException || ex.getCause() instanceof HttpTimeoutException;
        log.warn("Downstream call for {} failed: {}", request.getRequestURI(), ex.getMessage());
        return timeout
                ? body(HttpStatus.GATEWAY_TIMEOUT, "The service took too long to respond. Please retry.", request)
                : body(HttpStatus.SERVICE_UNAVAILABLE, "The service is starting up or unavailable. Please retry in a few seconds.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Gateway error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return body(HttpStatus.BAD_GATEWAY, "The gateway could not complete the request", request);
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        Object correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        if (correlationId != null) {
            body.put("correlationId", correlationId);
        }
        return ResponseEntity.status(status).body(body);
    }
}
