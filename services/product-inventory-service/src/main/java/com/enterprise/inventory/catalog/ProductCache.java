package com.enterprise.inventory.catalog;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Programmatic cache eviction. The Redis cache manager is transaction-aware, so evictions happen only after
 * the database transaction commits (no stale data re-cached in the middle of a transaction).
 */
@Component
public class ProductCache {

    public static final String PRODUCT = "product";
    public static final String CATEGORIES = "categories";
    public static final String HOME = "home";

    private final ObjectProvider<CacheManager> cacheManager;

    public ProductCache(ObjectProvider<CacheManager> cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictProduct(Long productId) {
        Cache cache = cache(PRODUCT);
        if (cache != null) {
            cache.evict(productId);
        }
    }

    /** Category counts and the storefront home page change when listings change. */
    public void evictListings() {
        clear(CATEGORIES);
        clear(HOME);
    }

    private void clear(String name) {
        Cache cache = cache(name);
        if (cache != null) {
            cache.clear();
        }
    }

    private Cache cache(String name) {
        CacheManager manager = cacheManager.getIfAvailable();
        return manager == null ? null : manager.getCache(name);
    }
}
