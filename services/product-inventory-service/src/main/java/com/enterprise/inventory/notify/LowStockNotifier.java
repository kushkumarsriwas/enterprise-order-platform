package com.enterprise.inventory.notify;

import com.enterprise.inventory.client.ServiceClientFactory;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

/** Sends a LOW_STOCK alert to admins via the notification service - asynchronously and only after commit. */
@Component
public class LowStockNotifier {

    private static final Logger log = LoggerFactory.getLogger(LowStockNotifier.class);

    private final RestClient notificationClient;

    public LowStockNotifier(ServiceClientFactory factory, @Value("${clients.notification.url}") String url) {
        this.notificationClient = factory.create("notification-service", url);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(LowStockEvent event) {
        if (event.bearerToken() == null) {
            return;
        }
        try {
            notificationClient.post().uri("/internal/notifications")
                    .headers(headers -> headers.setBearerAuth(event.bearerToken()))
                    .body(Map.of(
                            "audience", "ADMINS",
                            "type", "LOW_STOCK",
                            "title", "Low stock: " + event.productName(),
                            "message", event.sku() + " has " + event.available() + " unit(s) left (reorder level "
                                    + event.reorderLevel() + ")."))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Low-stock notification for product {} failed: {}", event.productId(), ex.getMessage());
        }
    }
}
