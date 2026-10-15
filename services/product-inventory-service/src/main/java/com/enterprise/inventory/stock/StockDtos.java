package com.enterprise.inventory.stock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class StockDtos {

    private StockDtos() {
    }

    // ------------------------------------------------------------------ order saga (internal API)

    public record ReserveItem(@NotNull Long productId, @NotNull @Min(1) @Max(1000) Integer quantity) {
    }

    public record ReserveRequest(@NotBlank @Size(max = 40) String orderRef,
                                 @NotEmpty @Size(max = 50) List<@Valid ReserveItem> items) {
    }

    public record ReservedLine(Long productId, Long sellerId, String sellerName, String sku, String name,
                               String categoryName, BigDecimal unitPrice, BigDecimal unitCost, int quantity,
                               String status) {
    }

    public record ReservationResponse(String orderRef, String currency, Instant expiresAt, List<ReservedLine> lines) {
    }

    public record LookupRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> productIds) {
    }

    public record ProductLookup(Long productId, Long sellerId, String sellerName, String name, BigDecimal price,
                                boolean purchasable, int availableQuantity) {
    }

    public record RatingUpdate(@NotNull @DecimalMin("0.0") @DecimalMax("5.0") BigDecimal average,
                               @NotNull @Min(0) Integer count) {
    }

    public record SuspensionRequest(@NotNull Boolean suspended) {
    }

    // ------------------------------------------------------------------ seller stock operations

    /** Manual correction in one warehouse: positive = units found / added, negative = damaged, lost... */
    public record AdjustmentRequest(@NotNull Long productId,
                                    Long warehouseId,
                                    @NotNull @Min(-1000000) @Max(1000000) Integer quantityChange,
                                    @NotNull AdjustmentReason reason,
                                    @Size(max = 255) String note) {
    }

    /** PUT /api/inventory/{productId}: set the counted on-hand quantity of a warehouse (stock take). */
    public record SetStockRequest(Long warehouseId,
                                  @NotNull @Min(0) @Max(1000000) Integer onHand,
                                  AdjustmentReason reason,
                                  @Size(max = 255) String note,
                                  @Size(max = 30) String binLocation) {
    }

    /** Goods received without a purchase order (records a goods receipt note). */
    public record ReceiveRequest(@NotNull Long productId,
                                 Long warehouseId,
                                 @NotNull @Min(1) @Max(1000000) Integer quantity,
                                 @NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2)
                                 BigDecimal unitCost,
                                 Long supplierId,
                                 @Size(max = 60) String reference,
                                 @Size(max = 255) String note) {
    }

    public record BinRequest(@NotNull Long warehouseId, @Size(max = 30) String binLocation) {
    }

    // ------------------------------------------------------------------ responses

    public record WarehouseLevel(Long warehouseId, String code, String name, int onHand, String binLocation) {
    }

    /** Stock position of one product (GET /api/inventory/{productId}). */
    public record InventoryResponse(Long productId, String sku, String productName, boolean listed,
                                    int availableQuantity, int reservedQuantity, int onHandQuantity,
                                    int reorderPoint, String stockStatus, BigDecimal unitCost, BigDecimal price,
                                    BigDecimal stockValueAtCost, List<WarehouseLevel> warehouses, Instant updatedAt) {
    }

    /** One row of the stock levels screen (optionally scoped to one warehouse). */
    public record StockLevelRow(Long productId, String sku, String productName, String categoryName, String unit,
                                boolean listed, int availableQuantity, int reservedQuantity, int onHandQuantity,
                                Integer warehouseOnHand, String binLocation, int incomingQuantity, int reorderPoint,
                                int reorderQuantity, String stockStatus, BigDecimal unitCost,
                                BigDecimal stockValueAtCost, Instant updatedAt) {
    }

    public record MovementResponse(Long id, Long productId, String sku, String productName, Long warehouseId,
                                   String warehouseCode, String type, int quantity, Integer balanceAfter,
                                   BigDecimal unitCost, BigDecimal unitPrice, BigDecimal value, String reason,
                                   String reference, String note, String performedBy, Instant createdAt) {

        public static MovementResponse from(StockMovement m) {
            BigDecimal cost = m.getUnitCost();
            return new MovementResponse(m.getId(), m.getProduct().getId(), m.getProduct().getSku(),
                    m.getProduct().getName(), m.getWarehouse() == null ? null : m.getWarehouse().getId(),
                    m.getWarehouse() == null ? null : m.getWarehouse().getCode(), m.getType().name(), m.getQuantity(),
                    m.getBalanceAfter(), cost, m.getUnitPrice(),
                    cost == null || !m.getType().isPhysical() ? null
                            : cost.multiply(BigDecimal.valueOf(m.getQuantity())).setScale(2, java.math.RoundingMode.HALF_UP),
                    m.getReason() == null ? null : m.getReason().label(), m.getReference(), m.getNote(),
                    m.getPerformedBy(), m.getCreatedAt());
        }
    }

    /** Headline inventory figures (Seller Central dashboard; platform totals for the admin). */
    public record InventorySummary(int products, int listedProducts, int inventoryOnlyProducts, long unitsAvailable,
                                   long unitsReserved, long unitsOnHand, long unitsIncoming, int lowStockProducts,
                                   int outOfStockProducts, int warehouses, int openPurchaseOrders,
                                   BigDecimal stockValueAtCost, BigDecimal stockValueAtPrice,
                                   BigDecimal potentialGrossProfit, String currency) {
    }

    /** Result of the reconciliation check: products whose warehouse rows don't add up to the stock position. */
    public record IntegrityIssue(Long productId, String sku, int availablePlusReserved, int sumOfWarehouses) {
    }
}
