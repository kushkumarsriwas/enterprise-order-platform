package com.enterprise.inventory.catalog;

import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import com.enterprise.inventory.stock.Inventory;

public final class ProductMapper {

    public static final String CURRENCY = "AED";

    private ProductMapper() {
    }

    public static ProductResponse toResponse(Product product, Inventory inventory) {
        Category category = product.getCategory();
        int available = inventory == null ? 0 : inventory.getAvailableQuantity();
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                CURRENCY,
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                product.getImageUrl(),
                product.isActive(),
                available,
                inventory == null ? 0 : inventory.getReservedQuantity(),
                inventory == null ? 0 : inventory.getReorderLevel(),
                available > 0,
                inventory != null && inventory.isLowStock(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
