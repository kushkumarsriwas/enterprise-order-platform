package com.enterprise.inventory.catalog;

import com.enterprise.inventory.seller.StoreSettingsService;
import com.enterprise.inventory.stock.StockDtos.RatingUpdate;
import com.enterprise.inventory.stock.StockDtos.SuspensionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service endpoints (not routed by the API gateway). */
@RestController
@Tag(name = "Internal - catalog")
public class InternalCatalogController {

    private static final Logger log = LoggerFactory.getLogger(InternalCatalogController.class);

    private final ProductRepository productRepository;
    private final ProductCache productCache;
    private final StoreSettingsService storeSettings;

    public InternalCatalogController(ProductRepository productRepository, ProductCache productCache,
                                     StoreSettingsService storeSettings) {
        this.productRepository = productRepository;
        this.productCache = productCache;
        this.storeSettings = storeSettings;
    }

    /** The order service keeps the rating of a product up to date after each review (denormalised for search). */
    @PutMapping("/internal/products/{productId}/rating")
    @Transactional
    @Operation(summary = "Store the average rating and review count of a product")
    public ResponseEntity<Void> rating(@PathVariable Long productId, @Valid @RequestBody RatingUpdate request) {
        productRepository.updateRating(productId, request.average(), request.count());
        productCache.evictProduct(productId);
        productCache.evictListings();
        return ResponseEntity.noContent().build();
    }

    /** Called by the account service when the administrator suspends or reactivates a store. Idempotent. */
    @PutMapping("/internal/sellers/{sellerId}/suspension")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Hide (or show again) every listing of a suspended store")
    public Map<String, Object> suspension(@PathVariable Long sellerId, @Valid @RequestBody SuspensionRequest request) {
        int updated = storeSettings.suspend(sellerId, request.suspended());
        log.info("Store {} {}: {} product(s) {}", sellerId, request.suspended() ? "suspended" : "reactivated", updated,
                request.suspended() ? "hidden from the marketplace" : "visible again");
        return Map.of("sellerId", sellerId, "suspended", request.suspended(), "products", updated);
    }
}
