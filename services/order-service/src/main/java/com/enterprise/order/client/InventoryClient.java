package com.enterprise.order.client;

import com.enterprise.order.common.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);
    private static final String NAME = "Inventory service";

    private final RestClient client;

    public InventoryClient(ServiceClientFactory factory, @Value("${clients.inventory.url}") String url) {
        this.client = factory.create(NAME, url);
    }

    public List<ProductInfo> lookup(List<Long> productIds) {
        return RemoteCalls.call(NAME, () -> client.post().uri("/internal/products/lookup")
                .body(java.util.Map.of("productIds", productIds))
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<ProductInfo>>() {
                }));
    }

    public Reservation reserve(String orderRef, List<Item> items) {
        return RemoteCalls.call(NAME, () -> client.post().uri("/internal/inventory/reservations")
                .body(new ReserveRequest(orderRef, items))
                .retrieve()
                .body(Reservation.class));
    }

    /** Commit is retried a few times because the payment has already been taken at this point. */
    public void commit(String orderRef) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                RemoteCalls.call(NAME, () -> client.post().uri("/internal/inventory/reservations/{ref}/commit", orderRef)
                        .retrieve().toBodilessEntity());
                return;
            } catch (ApiException ex) {
                if (ex.getStatus() != HttpStatus.SERVICE_UNAVAILABLE) {
                    throw ex;
                }
                last = ex;
                log.warn("Stock commit for {} failed (attempt {}): {}", orderRef, attempt, ex.getMessage());
                sleep(attempt * 500L);
            }
        }
        throw last;
    }

    public void release(String orderRef) {
        RemoteCalls.call(NAME, () -> client.post().uri("/internal/inventory/reservations/{ref}/release", orderRef)
                .retrieve().toBodilessEntity());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public record Item(Long productId, Integer quantity) {
    }

    record ReserveRequest(String orderRef, List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reservation(String orderRef, String currency, Instant expiresAt, List<Line> lines) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(Long productId, Long merchantId, String merchantName, String sku, String name,
                       BigDecimal unitPrice, BigDecimal unitCost, int quantity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductInfo(Long productId, Long merchantId, String merchantName, String name, BigDecimal price,
                              boolean purchasable, int availableQuantity) {
    }
}
