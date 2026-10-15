package com.enterprise.inventory.catalog;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CatalogDtos {

    public static final String SKU_PATTERN = "^[A-Za-z0-9][A-Za-z0-9-]{0,39}$";
    public static final String BARCODE_PATTERN = "^$|^\\d{8,14}$";

    private CatalogDtos() {
    }

    // ------------------------------------------------------------------ requests (seller)

    public record CreateProductRequest(
            @NotBlank @Pattern(regexp = SKU_PATTERN, message = "letters, digits and '-' only (max 40)") String sku,
            @Pattern(regexp = BARCODE_PATTERN, message = "must be 8 to 14 digits (EAN / UPC)") String barcode,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 60) String brand,
            @Size(max = 2000) String description,
            Unit unit,
            Long categoryId,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal compareAtPrice,
            @NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
            @Size(max = 500) String imageUrl,
            @Min(0) @Max(1000000) Integer reorderPoint,
            @Min(0) @Max(1000000) Integer reorderQuantity,
            Long preferredSupplierId,
            Boolean listed,
            @Min(0) @Max(1000000) Integer initialStock,
            Long warehouseId) {
    }

    public record UpdateProductRequest(
            @Pattern(regexp = BARCODE_PATTERN, message = "must be 8 to 14 digits (EAN / UPC)") String barcode,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 60) String brand,
            @Size(max = 2000) String description,
            Unit unit,
            Long categoryId,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal compareAtPrice,
            @NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
            @Size(max = 500) String imageUrl,
            @NotNull @Min(0) @Max(1000000) Integer reorderPoint,
            @NotNull @Min(0) @Max(1000000) Integer reorderQuantity,
            Long preferredSupplierId,
            @NotNull Boolean listed) {
    }

    public record ListingRequest(@NotNull Boolean listed) {
    }

    public enum BulkAction {
        LIST,
        UNLIST,
        ARCHIVE,
        SET_CATEGORY,
        ADJUST_PRICE_PERCENT,
        SET_REORDER_POINT
    }

    public record BulkRequest(@NotEmpty @Size(max = 200) List<@NotNull Long> productIds,
                              @NotNull BulkAction action,
                              Long categoryId,
                              @DecimalMin("-90") @DecimalMax("500") BigDecimal percent,
                              @Min(0) @Max(1000000) Integer reorderPoint) {
    }

    public record BulkResult(int updated, List<String> skipped) {
    }

    public record CreateCategoryRequest(@NotBlank @Size(max = 80) String name, @Size(max = 255) String description,
                                        @Size(max = 40) String icon) {
    }

    // ------------------------------------------------------------------ responses

    public record CategoryResponse(Long id, String name, String description, String icon, Long productCount) {
    }

    /** Wrapper so the category list can be cached in Redis with a concrete JSON type. */
    public record CategoryList(List<CategoryResponse> items) {
    }

    /** What customers see. Never contains the seller's cost price or stock details beyond availability. */
    public record ProductResponse(
            Long id,
            Long sellerId,
            String sellerName,
            String sku,
            String name,
            String brand,
            String description,
            String unit,
            BigDecimal price,
            BigDecimal compareAtPrice,
            int discountPercent,
            String currency,
            Long categoryId,
            String categoryName,
            String imageUrl,
            int availableQuantity,
            boolean inStock,
            boolean fewLeft,
            BigDecimal ratingAverage,
            int ratingCount,
            long unitsSold,
            Instant createdAt) {
    }

    /** What the owning seller (and the platform administrator) sees: costs, margins, stock and planning data. */
    public record SellerProductResponse(
            Long id,
            Long sellerId,
            String sellerName,
            String sku,
            String barcode,
            String name,
            String brand,
            String description,
            String unit,
            Long categoryId,
            String categoryName,
            BigDecimal price,
            BigDecimal compareAtPrice,
            BigDecimal costPrice,
            BigDecimal marginPercent,
            int discountPercent,
            String currency,
            String imageUrl,
            boolean listed,
            boolean active,
            boolean visibleToCustomers,
            boolean storeSuspended,
            int availableQuantity,
            int reservedQuantity,
            int onHandQuantity,
            int incomingQuantity,
            int reorderPoint,
            int reorderQuantity,
            String stockStatus,
            BigDecimal stockValueAtCost,
            BigDecimal stockValueAtPrice,
            Long preferredSupplierId,
            String preferredSupplierName,
            BigDecimal ratingAverage,
            int ratingCount,
            long unitsSold,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record WarehouseQuantity(Long warehouseId, String code, String name, boolean defaultWarehouse, int onHand,
                                    String binLocation) {
    }

    public record IncomingPurchase(Long purchaseOrderId, String number, String supplierName, String status,
                                   String expectedDate, int quantityOutstanding) {
    }

    /** Product page of Seller Central: stock per warehouse, recent ledger rows, demand and incoming stock. */
    public record SellerProductDetail(SellerProductResponse product, List<WarehouseQuantity> warehouses,
                                      List<com.enterprise.inventory.stock.StockDtos.MovementResponse> recentMovements,
                                      long unitsSoldLast30Days, BigDecimal revenueLast30Days,
                                      BigDecimal averageDailySales, Integer daysOfCover,
                                      List<IncomingPurchase> openPurchaseOrders) {
    }
}
