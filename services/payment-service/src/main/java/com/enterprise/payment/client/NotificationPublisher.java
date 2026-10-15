package com.enterprise.payment.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fire-and-forget notifications. Runs on a background thread so a slow or failing notification service never
 * blocks or breaks the request that raised it. Authenticated with the internal service key.
 */
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final RestClient client;

    public NotificationPublisher(ServiceClientFactory factory, @Value("${clients.notification.url}") String url) {
        this.client = factory.create("Notification service", url);
    }

    @Async
    public void toUser(String bearerToken, Long userId, String type, String title, String message) {
        send(bearerToken, new NotificationRequest(userId, "USER", type, title, message, null));
    }

    @Async
    public void toAdmins(String bearerToken, String type, String title, String message) {
        send(bearerToken, new NotificationRequest(null, "ADMINS", type, title, message, null));
    }

    private void send(String bearerToken, NotificationRequest request) {
        try {
            client.post().uri("/internal/notifications")
                    .headers(headers -> {
                        if (bearerToken != null) {
                            headers.setBearerAuth(bearerToken);
                        }
                    })
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Notification {} could not be delivered: {}", request.type(), ex.getMessage());
        }
    }

    record NotificationRequest(Long userId, String audience, String type, String title, String message,
                               String email) {
    }
}
