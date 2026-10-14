package com.enterprise.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentClient {

    private static final String NAME = "Payment service";

    private final RestClient client;

    public PaymentClient(ServiceClientFactory factory, @Value("${clients.payment.url}") String url) {
        this.client = factory.create(NAME, url);
    }

    public PaymentResult charge(ChargeRequest request) {
        return RemoteCalls.call(NAME, () -> client.post().uri("/internal/payments")
                .body(request).retrieve().body(PaymentResult.class));
    }

    public PaymentResult refund(Long orderId, String reason) {
        return RemoteCalls.call(NAME, () -> client.post().uri("/internal/payments/order/{orderId}/refund", orderId)
                .body(Map.of("reason", reason == null ? "Order cancelled" : reason))
                .retrieve().body(PaymentResult.class));
    }

    public PaymentResult capture(Long orderId) {
        return RemoteCalls.call(NAME, () -> client.post().uri("/internal/payments/order/{orderId}/capture", orderId)
                .retrieve().body(PaymentResult.class));
    }

    public record ChargeRequest(Long orderId, String orderNumber, Long userId, Long merchantId, BigDecimal amount,
                                String currency,
                                String method, String cardNumber, String cardHolder, String expiry, String cvv,
                                String upiId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentResult(Long id, String transactionRef, Long orderId, BigDecimal amount, String method,
                                String status, String maskedInstrument, String failureReason) {

        public boolean isSuccessful() {
            return "SUCCESS".equals(status);
        }

        public boolean isPayOnDelivery() {
            return "PENDING".equals(status) && "COD".equals(method);
        }
    }
}
