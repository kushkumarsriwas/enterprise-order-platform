package com.enterprise.order.client;

import com.enterprise.order.client.dto.ProductSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(
            RestClient.Builder builder,
            @Value("${product.service.url:http://enterprise-product-inventory:8082}")
            String productServiceUrl) {

        this.restClient = builder
                .baseUrl(productServiceUrl)
                .build();
    }

    public ProductSnapshot getProduct(Long productId) {
        return restClient.get()
                .uri("/api/products/{id}", productId)
                .retrieve()
                .body(ProductSnapshot.class);
    }
}