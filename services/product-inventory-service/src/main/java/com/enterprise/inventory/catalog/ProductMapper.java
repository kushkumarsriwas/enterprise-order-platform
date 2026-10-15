package com.enterprise.inventory.catalog;

import com.enterprise.inventory.catalog.CatalogDtos.ProductResponse;
import com.enterprise.inventory.catalog.CatalogDtos.SellerProductResponse;
import com.enterprise.inventory.purchasing.Supplier;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.StockStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** DTO pattern: entities never leave the service; customers and sellers get different views of a product. */
public final class ProductMapper {

    public static final String CURRENCY = "AED";
    /** "Only N left" is shown to customers at or below this quantity. */
    public static final int FEW_LEFT = 5;

    private ProductMapper() {
    }

    public static ProductResponse toPublic(Product product, Inventory inventory) {
        Category category = product.getCategory();
        int available = inventory == null ? 0 : inventory.getAvailableQuantity();
        return new ProductResponse(product.getId(), product.getSellerId(), product.getSellerName(), product.getSku(),
                product.getName(), product.getBrand(), product.getDescription(), product.getUnit().symbol(),
                product.getPrice(), visibleCompareAt(product), product.discountPercent(), CURRENCY,
                category == null ? null : category.getId(), category == null ? null : category.getName(),
                product.getImageUrl(), available, available > 0, available > 0 && available <= FEW_LEFT,
                product.getRatingAverage(), product.getRatingCount(), product.getUnitsSold(), product.getCreatedAt());
    }

    public static SellerProductResponse toSeller(Product product, Inventory inventory, int incoming) {
        Category category = product.getCategory();
        Supplier supplier = product.getPreferredSupplier();
        int available = inventory == null ? 0 : inventory.getAvailableQuantity();
        int reserved = inventory == null ? 0 : inventory.getReservedQuantity();
        BigDecimal onHand = BigDecimal.valueOf(available + reserved);
        return new SellerProductResponse(product.getId(), product.getSellerId(), product.getSellerName(),
                product.getSku(), product.getBarcode(), product.getName(), product.getBrand(), product.getDescription(),
                product.getUnit().name(), category == null ? null : category.getId(),
                category == null ? null : category.getName(), product.getPrice(), product.getCompareAtPrice(),
                product.getCostPrice(), marginPercent(product.getPrice(), product.getCostPrice()),
                product.discountPercent(), CURRENCY, product.getImageUrl(), product.isListed(), product.isActive(),
                product.isPurchasable(), product.isStoreSuspended(), available, reserved, available + reserved,
                incoming, product.getReorderPoint(), product.getReorderQuantity(),
                StockStatus.of(available, product.getReorderPoint()).name(),
                money(product.getCostPrice().multiply(onHand)), money(product.getPrice().multiply(onHand)),
                supplier == null ? null : supplier.getId(), supplier == null ? null : supplier.getName(),
                product.getRatingAverage(), product.getRatingCount(), product.getUnitsSold(), product.getCreatedAt(),
                product.getUpdatedAt());
    }

    /** (price - cost) / price, as a percentage. */
    public static BigDecimal marginPercent(BigDecimal price, BigDecimal cost) {
        if (price == null || price.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return price.subtract(cost).multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal visibleCompareAt(Product product) {
        return product.discountPercent() > 0 ? product.getCompareAtPrice() : null;
    }
}
