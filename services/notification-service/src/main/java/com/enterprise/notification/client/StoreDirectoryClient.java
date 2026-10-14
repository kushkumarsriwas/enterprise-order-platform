package com.enterprise.notification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Looks up a seller in the account service's public store directory. Used to make sure a customer can only
 * start a conversation with a real, active store, and to get the store name from a trusted source.
 */
@Component
public class StoreDirectoryClient {

    private static final String NAME = "Account service";

    private final RestClient client;

    public StoreDirectoryClient(ServiceClientFactory factory, @Value("${clients.auth.url}") String url) {
        this.client = factory.create(NAME, url);
    }

    /** The store's name; 404 when the store does not exist or is suspended. */
    public String storeName(Long merchantId) {
        Store store = RemoteCalls.call(NAME, () -> client.get().uri("/api/merchants/{merchantId}", merchantId)
                .retrieve().body(Store.class));
        return store.storeName();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Store(Long merchantId, String storeName) {
    }
}
