package com.enterprise.order.security;

import com.enterprise.order.common.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.MDC;

/** Writes security errors (401/403/503) as the same JSON shape used by the exception handler. */
final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    static void write(HttpServletRequest request, HttpServletResponse response, int status, String error,
                      String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String json = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status
                + ",\"error\":\"" + escape(error) + "\",\"message\":\"" + escape(message)
                + "\",\"path\":\"" + escape(request.getRequestURI()) + "\""
                + (correlationId == null ? "" : ",\"correlationId\":\"" + escape(correlationId) + "\"")
                + "}";
        response.getWriter().write(json);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
