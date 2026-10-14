package com.enterprise.auth.client;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Tells the product & inventory service to hide (or show again) every listing of a suspended store. */
@Component
public class CatalogClient {

    private static final String NAME = "Product and inventory service";

    private final RestClient client;

    public CatalogClient(ServiceClientFactory factory, @Value("${clients.inventory.url}") String url) {
        this.client = factory.create(NAME, url);
    }

    /** Idempotent: calling it twice with the same value changes nothing. */
    public void setStoreSuspended(Long sellerId, boolean suspended) {
        RemoteCalls.call(NAME, () -> client.put().uri("/internal/sellers/{sellerId}/suspension", sellerId)
                .body(Map.of("suspended", suspended))
                .retrieve()
                .toBodilessEntity());
    }
}
