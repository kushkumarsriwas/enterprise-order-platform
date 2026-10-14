package com.enterprise.gateway.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Gives every request an X-Correlation-Id and forwards it to the services (end-to-end tracing in logs). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String correlationId = incoming == null || incoming.isBlank() || incoming.length() > 64
                ? UUID.randomUUID().toString() : incoming;
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        MDC.put(ATTRIBUTE, correlationId);
        try {
            chain.doFilter(incoming != null && incoming.equals(correlationId) ? request
                    : new HeaderAddingRequest(request, HEADER, correlationId), response);
        } finally {
            MDC.remove(ATTRIBUTE);
        }
    }

    /** Adds one header to the request that is proxied downstream. */
    static final class HeaderAddingRequest extends HttpServletRequestWrapper {

        private final String name;
        private final String value;

        HeaderAddingRequest(HttpServletRequest request, String name, String value) {
            super(request);
            this.name = name;
            this.value = value;
        }

        @Override
        public String getHeader(String header) {
            return name.equalsIgnoreCase(header) ? value : super.getHeader(header);
        }

        @Override
        public Enumeration<String> getHeaders(String header) {
            return name.equalsIgnoreCase(header) ? Collections.enumeration(Set.of(value)) : super.getHeaders(header);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.add(name);
            return Collections.enumeration(names);
        }
    }
}
