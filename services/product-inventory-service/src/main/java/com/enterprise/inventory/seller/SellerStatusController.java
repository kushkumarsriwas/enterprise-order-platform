package com.enterprise.inventory.merchant;

import com.enterprise.inventory.catalog.ProductCache;
import com.enterprise.inventory.catalog.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by the account service when an administrator suspends or reactivates a merchant: all of the store's
 * products leave (or return to) the marketplace at once. Not routed by the gateway.
 */
@RestController
@Tag(name = "Internal - store status")
public class MerchantStatusController {

    private static final Logger log = LoggerFactory.getLogger(MerchantStatusController.class);

    private final ProductRepository productRepository;
    private final ProductCache productCache;

    public MerchantStatusController(ProductRepository productRepository, ProductCache productCache) {
        this.productRepository = productRepository;
        this.productCache = productCache;
    }

    @PutMapping("/internal/merchants/{merchantId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Hide or show every product of a store (administrator suspension)")
    public StatusResult setStatus(@PathVariable Long merchantId, @Valid @RequestBody StatusChange request) {
        int updated = productRepository.setMerchantActive(merchantId, request.active());
        // the cache manager is transaction-aware: these evictions run after the commit
        List<Long> ids = productRepository.findIdsByMerchantId(merchantId);
        ids.forEach(productCache::evictProduct);
        productCache.evictCategories();
        log.info("Store {} {}: {} products {} the marketplace", merchantId, request.active() ? "reactivated" : "suspended",
                updated, request.active() ? "back on" : "removed from");
        return new StatusResult(merchantId, request.active(), updated);
    }

    public record StatusChange(@NotNull Boolean active) {
    }

    public record StatusResult(Long merchantId, boolean active, int products) {
    }
}
