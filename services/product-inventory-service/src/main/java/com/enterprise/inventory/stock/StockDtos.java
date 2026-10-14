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

    public record ReserveItem(@NotNull Long productId, @NotNull @Min(1) @Max(1000) Integer quantity) {
    }

    public record ReserveRequest(@NotBlank @Size(max = 40) String orderRef,
                                 @NotEmpty @Size(max = 50) List<@Valid ReserveItem> items) {
    }

    public record ReservedLine(Long productId, Long merchantId, String merchantName, String sku, String name,
                               BigDecimal unitPrice, BigDecimal unitCost, int quantity, String status) {
    }

    public record ReservationResponse(String orderRef, String currency, Instant expiresAt, List<ReservedLine> lines) {
    }

    public record LookupRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> productIds) {
    }

    public record ProductLookup(Long productId, Long merchantId, String merchantName, String name, BigDecimal price,
                                boolean purchasable, int availableQuantity) {
    }

    public record AdjustStockRequest(@NotNull @Min(0) @Max(1000000) Integer availableQuantity,
                                     @Min(0) @Max(100000) Integer reorderLevel,
                                     @Size(max = 255) String note) {
    }

    public record RestockRequest(@NotNull @Min(1) @Max(1000000) Integer quantity,
                                 @NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2)
                                 BigDecimal unitCost,
                                 @Size(max = 100) String supplier,
                                 @Size(max = 60) String reference) {
    }

    public record InventoryResponse(Long productId, String sku, String productName, boolean listed,
                                    int availableQuantity, int reservedQuantity, int reorderLevel, boolean lowStock,
                                    BigDecimal unitCost, BigDecimal price, BigDecimal stockValueAtCost,
                                    Instant updatedAt) {
    }

    public record MovementResponse(Long id, Long productId, String type, int quantityChange, String reference,
                                   String note, String performedBy, Instant createdAt) {

        static MovementResponse from(StockMovement movement) {
            return new MovementResponse(movement.getId(), movement.getProductId(), movement.getType().name(),
                    movement.getQuantityChange(), movement.getReference(), movement.getNote(),
                    movement.getPerformedBy(), movement.getCreatedAt());
        }
    }

    public record PurchaseResponse(Long id, Long productId, String sku, String productName, int quantity,
                                   BigDecimal unitCost, BigDecimal totalCost, String supplier, String reference,
                                   Instant purchasedAt) {

        static PurchaseResponse from(StockPurchase purchase) {
            return new PurchaseResponse(purchase.getId(), purchase.getProductId(), purchase.getSku(),
                    purchase.getProductName(), purchase.getQuantity(), purchase.getUnitCost(), purchase.getTotalCost(),
                    purchase.getSupplier(), purchase.getReference(), purchase.getPurchasedAt());
        }
    }

    /** Headline inventory figures for the merchant dashboard. */
    public record InventorySummary(int products, int listedProducts, long unitsOnHand, long unitsReserved,
                                   int lowStockProducts, int outOfStockProducts, BigDecimal stockValueAtCost,
                                   BigDecimal stockValueAtPrice, BigDecimal potentialProfit, String currency) {
    }
}
