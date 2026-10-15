package com.enterprise.inventory.client;

import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.CorrelationIdFilter;
import com.enterprise.inventory.security.CurrentUser;
import com.enterprise.inventory.security.InternalApiKey;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds RestClients for calling other services: timeouts, the internal service key, token relay (the caller's
 * JWT is forwarded), correlation-id propagation and translation of downstream errors into {@link ApiException}s.
 */
@Component
public class ServiceClientFactory {

    private final ObjectMapper objectMapper;
    private final InternalApiKey internalApiKey;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public ServiceClientFactory(ObjectMapper objectMapper, InternalApiKey internalApiKey,
                                @Value("${clients.connect-timeout:5s}") Duration connectTimeout,
                                @Value("${clients.read-timeout:15s}") Duration readTimeout) {
        this.objectMapper = objectMapper;
        this.internalApiKey = internalApiKey;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public RestClient create(String serviceName, String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(InternalApiKey.HEADER, internalApiKey.value());
                    String token = CurrentUser.token();
                    if (token != null && !request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
                        request.getHeaders().setBearerAuth(token);
                    }
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    int status = response.getStatusCode().value();
                    String message = readMessage(response.getBody());
                    if (status >= 500 || status == 401) {
                        throw ApiException.serviceUnavailable(serviceName + " could not process the request"
                                + (message == null ? "" : ": " + message));
                    }
                    throw new ApiException(HttpStatus.valueOf(status),
                            message != null ? message : serviceName + " rejected the request");
                })
                .build();
    }

    private String readMessage(InputStream body) {
        try {
            Map<?, ?> json = objectMapper.readValue(body, Map.class);
            Object message = json.get("message");
            return message == null ? null : message.toString();
        } catch (Exception ex) {
            return null;
        }
    }
}
