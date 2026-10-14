package com.enterprise.inventory.catalog;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Programmatic cache eviction. The Redis cache manager is transaction-aware, so evictions
 * happen only after the database transaction commits (no stale data re-cached mid-transaction).
 */
@Component
public class ProductCache {

    public static final String PRODUCT = "product";
    public static final String CATEGORIES = "categories";

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

    public void evictCategories() {
        Cache cache = cache(CATEGORIES);
        if (cache != null) {
            cache.clear();
        }
    }

    private Cache cache(String name) {
        CacheManager manager = cacheManager.getIfAvailable();
        return manager == null ? null : manager.getCache(name);
    }
}
