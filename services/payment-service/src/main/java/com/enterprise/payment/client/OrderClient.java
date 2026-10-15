package com.enterprise.payment.client;

import com.enterprise.payment.common.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the order service: the payment intent (amount + lock) before charging, the outcome after charging,
 * and sales figures for the profit &amp; loss statement. The caller's token is relayed.
 */
@Component
public class OrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrderClient.class);
    private static final String NAME = "Order service";

    private final RestClient client;

    public OrderClient(ServiceClientFactory factory, @Value("${clients.order.url}") String url) {
        this.client = factory.create(NAME, url);
    }

    public PaymentIntent paymentIntent(Long orderId) {
        return RemoteCalls.call(NAME, () -> client.post().uri("/internal/orders/{id}/payment-intent", orderId)
                .retrieve().body(PaymentIntent.class));
    }

    /** Reports the result of a payment; retried a few times when the order service is temporarily down. */
    public PaymentOutcome reportOutcome(Long orderId, OutcomeRequest outcome) {
        ApiException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return RemoteCalls.call(NAME, () -> client.post().uri("/internal/orders/{id}/payment-outcome", orderId)
                        .body(outcome).retrieve().body(PaymentOutcome.class));
            } catch (ApiException ex) {
                if (ex.getStatus() != HttpStatus.SERVICE_UNAVAILABLE) {
                    throw ex;
                }
                last = ex;
                log.warn("Reporting payment {} to the order service failed (attempt {}): {}",
                        outcome.transactionRef(), attempt, ex.getMessage());
                sleep(attempt * 400L);
            }
        }
        throw last;
    }

    public SalesSummary salesSummary(Long sellerId, LocalDate from, LocalDate to) {
        return RemoteCalls.call(NAME, () -> client.get()
                .uri(uri -> uri.path("/internal/sellers/{sellerId}/sales-summary")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build(sellerId))
                .retrieve()
                .body(SalesSummary.class));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentIntent(Long orderId, String orderNumber, Long customerId, String customerUsername,
                                String customerEmail, Long sellerId, String sellerName, BigDecimal amount,
                                String currency) {
    }

    public record OutcomeRequest(Long paymentId, String transactionRef, String method, String status,
                                 String maskedInstrument, String failureReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentOutcome(boolean confirmed, boolean refundRequired, String message, OrderSummary order) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderSummary(Long id, String orderNumber, String status, String paymentState) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SalesSummary(BigDecimal revenue, BigDecimal costOfGoodsSold, BigDecimal grossProfit, long orders,
                               List<MonthAmounts> months) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MonthAmounts(String month, BigDecimal revenue, BigDecimal costOfGoodsSold, long orders) {
    }
}
