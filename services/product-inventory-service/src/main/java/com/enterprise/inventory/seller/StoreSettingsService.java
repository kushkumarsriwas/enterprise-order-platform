package com.enterprise.inventory.seller;

import com.enterprise.inventory.catalog.ProductCache;
import com.enterprise.inventory.catalog.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vacation mode (seller) and suspension (platform administrator) hide every listing of a store at once. */
@Service
public class StoreSettingsService {

    private static final Logger log = LoggerFactory.getLogger(StoreSettingsService.class);

    private final SellerSettingsRepository repository;
    private final ProductRepository productRepository;
    private final ProductCache productCache;

    public StoreSettingsService(SellerSettingsRepository repository, ProductRepository productRepository,
                                ProductCache productCache) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.productCache = productCache;
    }

    public record StoreSettingsResponse(boolean vacationMode, String vacationMessage, boolean suspended,
                                        boolean lowStockAlerts) {
    }

    @Transactional
    public SellerSettings settingsOf(Long sellerId) {
        return repository.findById(sellerId).orElseGet(() -> repository.save(new SellerSettings(sellerId)));
    }

    @Transactional
    public StoreSettingsResponse get(Long sellerId) {
        return toResponse(settingsOf(sellerId));
    }

    @Transactional
    public StoreSettingsResponse update(Long sellerId, boolean vacationMode, String vacationMessage,
                                        boolean lowStockAlerts) {
        SellerSettings settings = settingsOf(sellerId);
        boolean changed = settings.isVacationMode() != vacationMode;
        settings.vacation(vacationMode, vacationMessage == null || vacationMessage.isBlank() ? null : vacationMessage.trim());
        settings.setLowStockAlerts(lowStockAlerts);
        if (changed) {
            int products = productRepository.setSellerActive(sellerId, !vacationMode);
            evict(sellerId);
            log.info("Seller {} {} vacation mode ({} listing(s) {})", sellerId, vacationMode ? "started" : "ended",
                    products, vacationMode ? "hidden" : "visible again");
        }
        return toResponse(settings);
    }

    @Transactional
    public int suspend(Long sellerId, boolean suspended) {
        settingsOf(sellerId).setSuspended(suspended);
        int products = productRepository.setStoreSuspended(sellerId, suspended);
        evict(sellerId);
        return products;
    }

    private void evict(Long sellerId) {
        productRepository.findIdsBySeller(sellerId).forEach(productCache::evictProduct);
        productCache.evictListings();
    }

    private static StoreSettingsResponse toResponse(SellerSettings s) {
        return new StoreSettingsResponse(s.isVacationMode(), s.getVacationMessage(), s.isSuspended(), s.isLowStockAlerts());
    }
}
