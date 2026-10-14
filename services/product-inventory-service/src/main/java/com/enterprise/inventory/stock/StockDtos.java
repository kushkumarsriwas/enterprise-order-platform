package com.enterprise.inventory.stock;

import jakarta.validation.Valid;
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

    public record ReservedLine(Long productId, String sku, String name, BigDecimal unitPrice, int quantity,
                               String status) {
    }

    public record ReservationResponse(String orderRef, String currency, Instant expiresAt, List<ReservedLine> lines) {
    }

    public record AdjustStockRequest(@NotNull @Min(0) @Max(1000000) Integer availableQuantity,
                                     @Min(0) @Max(100000) Integer reorderLevel,
                                     @Size(max = 255) String note) {
    }

    public record RestockRequest(@NotNull @Min(1) @Max(1000000) Integer quantity, @Size(max = 255) String note) {
    }

    public record InventoryResponse(Long productId, String sku, String productName, int availableQuantity,
                                    int reservedQuantity, int reorderLevel, boolean lowStock, Instant updatedAt) {
    }

    public record MovementResponse(Long id, String type, int quantityChange, String reference, String note,
                                   String performedBy, Instant createdAt) {

        static MovementResponse from(StockMovement movement) {
            return new MovementResponse(movement.getId(), movement.getType().name(), movement.getQuantityChange(),
                    movement.getReference(), movement.getNote(), movement.getPerformedBy(), movement.getCreatedAt());
        }
    }
}
