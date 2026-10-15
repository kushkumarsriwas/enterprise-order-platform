package com.enterprise.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads a store's public business details (legal name, TRN, address) from the account service for invoices. */
@Component
public class StoreDirectoryClient {

    private static final Logger log = LoggerFactory.getLogger(StoreDirectoryClient.class);

    private final RestClient client;

    public StoreDirectoryClient(ServiceClientFactory factory, @Value("${clients.auth.url}") String url) {
        this.client = factory.create("Account service", url);
    }

    /** Returns null when the account service can't be reached: the invoice is still produced, without the TRN. */
    public StoreDetails find(Long sellerId) {
        try {
            return client.get().uri("/api/sellers/{id}", sellerId).retrieve().body(StoreDetails.class);
        } catch (RuntimeException ex) {
            log.warn("Store details of seller {} unavailable: {}", sellerId, ex.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StoreDetails(Long sellerId, String storeName, String phone, String supportEmail, String city,
                               String country, String tradeLicenseNumber, String vatNumber) {
    }
}
