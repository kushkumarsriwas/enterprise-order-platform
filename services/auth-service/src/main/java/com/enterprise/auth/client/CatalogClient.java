package com.enterprise.auth.client;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Tells the product and inventory service to hide or show every product of a suspended or reactivated store. */
@Component
public class CatalogClient {

    private static final String NAME = "Product and inventory service";

    private final RestClient client;

    public CatalogClient(ServiceClientFactory factory, @Value("${clients.inventory.url}") String url) {
        this.client = factory.create(NAME, url);
    }

    public void setMerchantActive(Long merchantId, boolean active) {
        RemoteCalls.call(NAME, () -> client.put().uri("/internal/merchants/{merchantId}/status", merchantId)
                .body(Map.of("active", active))
                .retrieve()
                .toBodilessEntity());
    }
}
