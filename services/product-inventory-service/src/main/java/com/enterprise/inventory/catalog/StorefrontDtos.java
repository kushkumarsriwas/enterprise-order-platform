package com.enterprise.inventory.catalog;

import com.enterprise.inventory.catalog.CatalogDtos.CategoryResponse;
import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import java.math.BigDecimal;
import java.util.List;

/** Storefront responses: search filters (facets), home page sections and search suggestions. */
public final class StorefrontDtos {

    private StorefrontDtos() {
    }

    public record SearchCriteria(String query, Long categoryId, List<Long> sellerIds, List<String> brands,
                                 BigDecimal minPrice, BigDecimal maxPrice, Integer minRating, boolean inStockOnly,
                                 boolean dealsOnly, String sort) {
    }

    public record FacetValue(String value, String label, long count) {
    }

    /** What the filter panel of the search results offers for the current search (with result counts). */
    public record SearchFacets(long total, List<FacetValue> categories, List<FacetValue> brands,
                               List<FacetValue> sellers, BigDecimal minPrice, BigDecimal maxPrice,
                               List<FacetValue> ratings, long deals, long inStock) {
    }

    public record HomeSections(List<ProductResponse> deals, List<ProductResponse> bestSellers,
                               List<ProductResponse> newArrivals, List<ProductResponse> topRated,
                               List<CategoryResponse> categories) {
    }

    public record Suggestions(List<String> products, List<CategoryResponse> categories) {
    }

    public record RelatedProducts(List<ProductResponse> similar, List<ProductResponse> fromSameSeller) {
    }
}
