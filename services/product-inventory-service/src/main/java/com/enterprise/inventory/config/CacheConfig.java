package com.enterprise.inventory.config;

import com.enterprise.inventory.catalog.CatalogDtos.CategoryList;
import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import com.enterprise.inventory.catalog.ProductCache;
import com.enterprise.inventory.catalog.StorefrontDtos.HomeSections;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Redis cache for product details, the department list and the storefront home page. If Redis is down the
 * error is logged and the request falls back to PostgreSQL (the cache must never break the shop).
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true", matchIfMissing = true)
public class CacheConfig implements CachingConfigurer {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     @Value("${app.cache.ttl:10m}") Duration ttl,
                                     @Value("${app.cache.home-ttl:60s}") Duration homeTtl) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("inventory:v3:");
        Map<String, RedisCacheConfiguration> caches = Map.of(
                ProductCache.PRODUCT, defaults.serializeValuesWith(
                        SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(ProductResponse.class))),
                ProductCache.CATEGORIES, defaults.serializeValuesWith(
                        SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(CategoryList.class))),
                ProductCache.HOME, defaults.entryTtl(homeTtl).serializeValuesWith(
                        SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(HomeSections.class))));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(caches)
                .transactionAware()
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
