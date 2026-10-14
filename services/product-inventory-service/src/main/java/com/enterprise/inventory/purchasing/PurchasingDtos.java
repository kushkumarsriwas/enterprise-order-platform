package com.enterprise.inventory.purchasing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PurchasingDtos {

    private PurchasingDtos() {
    }

    // ------------------------------------------------------------------ suppliers

    public record SupplierRequest(@NotBlank @Size(max = 120) String name,
                                  @Size(max = 120) String contactName,
                                  @Email @Size(max = 120) String email,
                                  @Size(max = 30) @Pattern(regexp = "^$|^[+0-9 ()-]{7,30}$", message = "is not a valid phone number")
                                  String phone,
                                  @Size(max = 255) String address,
                                  @Size(max = 60) String city,
                                  @Size(max = 60) String country,
                                  @Min(0) @Max(365) Integer leadTimeDays,
                                  @Min(0) @Max(365) Integer paymentTermsDays,
                                  @Size(max = 500) String notes) {
    }

    public record SupplierResponse(Long id, String name, String contactName, String email, String phone, String address,
                                   String city, String country, int leadTimeDays, int paymentTermsDays, String notes,
                                   boolean active, long purchaseOrders, long openPurchaseOrders,
                                   BigDecimal orderedAmount, Integer onTimeDeliveryPercent, long productsSupplied,
                                   Instant createdAt) {
    }

    // ------------------------------------------------------------------ purchase orders

    public record PurchaseOrderLineRequest(@NotNull Long productId,
                                           @NotNull @Min(1) @Max(1000000) Integer quantity,
                                           @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2)
                                           BigDecimal unitCost) {
    }

    public record PurchaseOrderRequest(@NotNull Long supplierId,
                                       Long warehouseId,
                                       LocalDate expectedDate,
                                       @Size(max = 500) String notes,
                                       @NotEmpty @Size(max = 200) List<@Valid PurchaseOrderLineRequest> lines) {
    }

    public record PurchaseOrderLineResponse(Long id, Long productId, String sku, String productName,
                                            int quantityOrdered, int quantityReceived, int quantityOutstanding,
                                            BigDecimal unitCost, BigDecimal lineTotal) {
    }

    public record ReceiptSummary(Long id, String number, int totalUnits, BigDecimal totalAmount, String receivedBy,
                                 Instant receivedAt) {
    }

    public record PurchaseOrderResponse(Long id, String number, Long supplierId, String supplierName,
                                        String supplierEmail, Long warehouseId, String warehouseCode,
                                        String warehouseName, String status, LocalDate orderDate,
                                        LocalDate expectedDate, Instant receivedAt, boolean overdue, String notes,
                                        BigDecimal totalAmount, int totalUnits, int outstandingUnits, String createdBy,
                                        Instant createdAt, Instant updatedAt, List<PurchaseOrderLineResponse> lines,
                                        List<ReceiptSummary> receipts) {
    }

    public record ReceiveLineRequest(@NotNull Long lineId, @NotNull @Min(0) @Max(1000000) Integer quantity) {
    }

    public record ReceivePurchaseOrderRequest(@NotEmpty @Size(max = 200) List<@Valid ReceiveLineRequest> lines,
                                              @Size(max = 60) String supplierReference,
                                              @Size(max = 255) String note) {
    }

    // ------------------------------------------------------------------ goods receipts

    public record GoodsReceiptLineResponse(Long productId, String sku, String productName, int quantity,
                                           BigDecimal unitCost, BigDecimal lineTotal) {
    }

    public record GoodsReceiptResponse(Long id, String number, Long purchaseOrderId, String purchaseOrderNumber,
                                       String supplierName, Long warehouseId, String warehouseCode,
                                       String supplierReference, String note, BigDecimal totalAmount, int totalUnits,
                                       String receivedBy, Instant receivedAt, List<GoodsReceiptLineResponse> lines) {
    }

    // ------------------------------------------------------------------ replenishment

    public record ReplenishmentSuggestion(Long productId, String sku, String productName, String categoryName,
                                          Long supplierId, String supplierName, int availableQuantity,
                                          int incomingQuantity, int reorderPoint, int reorderQuantity,
                                          BigDecimal averageDailySales, Integer daysOfCover, int leadTimeDays,
                                          int suggestedQuantity, BigDecimal unitCost, BigDecimal estimatedCost,
                                          String reason) {
    }

    public record ReplenishmentItem(@NotNull Long productId, @NotNull @Min(1) @Max(1000000) Integer quantity,
                                    Long supplierId) {
    }

    public record ReplenishmentOrderRequest(@NotEmpty @Size(max = 200) List<@Valid ReplenishmentItem> items,
                                            Long warehouseId,
                                            @Size(max = 500) String notes) {
    }
}
