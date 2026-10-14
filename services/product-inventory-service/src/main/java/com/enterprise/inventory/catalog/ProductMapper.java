package com.enterprise.inventory.catalog;

import com.enterprise.inventory.catalog.CatalogDtos.MerchantProductResponse;
import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import com.enterprise.inventory.stock.Inventory;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ProductMapper {

    public static final String CURRENCY = "AED";

    private ProductMapper() {
    }

    public static ProductResponse toPublic(Product product, Inventory inventory) {
        Category category = product.getCategory();
        int available = inventory == null ? 0 : inventory.getAvailableQuantity();
        return new ProductResponse(product.getId(), product.getMerchantId(), product.getMerchantName(), product.getSku(),
                product.getName(), product.getDescription(), product.getPrice(), CURRENCY,
                category == null ? null : category.getId(), category == null ? null : category.getName(),
                product.getImageUrl(), available, available > 0, inventory != null && inventory.isLowStock(),
                product.getCreatedAt());
    }

    public static MerchantProductResponse toMerchant(Product product, Inventory inventory) {
        Category category = product.getCategory();
        int available = inventory == null ? 0 : inventory.getAvailableQuantity();
        int reserved = inventory == null ? 0 : inventory.getReservedQuantity();
        BigDecimal onHand = BigDecimal.valueOf(available + reserved);
        return new MerchantProductResponse(product.getId(), product.getSku(), product.getName(),
                product.getDescription(), product.getPrice(), product.getCostPrice(),
                marginPercent(product.getPrice(), product.getCostPrice()), CURRENCY,
                category == null ? null : category.getId(), category == null ? null : category.getName(),
                product.getImageUrl(), product.isListed(), product.isActive(), available, reserved,
                inventory == null ? 0 : inventory.getReorderLevel(), inventory != null && inventory.isLowStock(),
                product.getCostPrice().multiply(onHand).setScale(2, RoundingMode.HALF_UP),
                product.getPrice().multiply(onHand).setScale(2, RoundingMode.HALF_UP),
                product.getCreatedAt(), product.getUpdatedAt());
    }

    /** (price - cost) / price, as a percentage. */
    public static BigDecimal marginPercent(BigDecimal price, BigDecimal cost) {
        if (price == null || price.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return price.subtract(cost).multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP);
    }
}
