package com.enterprise.order.order;

import jakarta.validation.Valid;
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

public final class OrderDtos {

    private OrderDtos() {
    }

    public record OrderLineRequest(@NotNull Long productId, @NotNull @Min(1) @Max(100) Integer quantity) {
    }

    public record PlaceOrderRequest(@NotEmpty @Size(max = 30) List<@Valid OrderLineRequest> items,
                                    @NotBlank @Size(min = 10, max = 500) String shippingAddress,
                                    @Size(max = 30) @Pattern(regexp = "^$|^[+0-9 ()-]{7,30}$", message = "is not a valid phone number")
                                    String phone) {
    }

    /** One checkout can contain products of several merchants: it becomes one order per merchant. */
    public record CheckoutResponse(List<OrderResponse> orders, BigDecimal grandTotal, String currency) {
    }

    public record PayOrderRequest(
            @NotBlank @Pattern(regexp = "CARD|UPI|COD", message = "must be CARD, UPI or COD") String method,
            @Size(max = 23) String cardNumber,
            @Size(max = 100) String cardHolder,
            @Size(max = 7) String expiry,
            @Size(max = 4) String cvv,
            @Size(max = 100) String upiId) {
    }

    public record PayManyRequest(@NotEmpty @Size(max = 20) List<@NotNull Long> orderIds,
                                 @NotNull @Valid PayOrderRequest payment) {
    }

    public record ShipRequest(@NotBlank @Size(max = 60) String carrier, @NotBlank @Size(max = 60) String trackingNumber,
                              @Size(max = 255) String note) {
    }

    public record CancelRequest(@Size(max = 255) String reason) {
    }

    public record OrderItemResponse(Long productId, String sku, String productName, BigDecimal unitPrice,
                                    int quantity, BigDecimal lineTotal) {
    }

    public record MerchantOrderItemResponse(Long productId, String sku, String productName, BigDecimal unitPrice,
                                            BigDecimal unitCost, int quantity, BigDecimal lineTotal,
                                            BigDecimal lineProfit) {
    }

    public record StatusHistoryResponse(String fromStatus, String toStatus, String changedBy, String note,
                                        Instant changedAt) {
    }

    public record OrderResponse(Long id, String orderNumber, Long userId, String customerUsername, Long merchantId,
                                String merchantName, String status, String paymentState, String paymentMethod,
                                Long paymentId, String currency, BigDecimal totalAmount, String shippingAddress,
                                String carrier, String trackingNumber, Instant shippedAt, Instant deliveredAt,
                                String cancelReason, Instant createdAt, Instant updatedAt, Instant paymentDeadline,
                                List<OrderItemResponse> items, List<StatusHistoryResponse> history) {
    }

    /** The merchant's view adds the customer's contact details, costs and profit. */
    public record MerchantOrderResponse(Long id, String orderNumber, Long customerId, String customerUsername,
                                        String customerEmail, String customerPhone, String status, String paymentState,
                                        String paymentMethod, String currency, BigDecimal totalAmount,
                                        BigDecimal totalCost, BigDecimal grossProfit, String shippingAddress,
                                        String carrier, String trackingNumber, Instant shippedAt, Instant deliveredAt,
                                        String cancelReason, Instant createdAt, List<MerchantOrderItemResponse> items,
                                        List<StatusHistoryResponse> history) {
    }

    public record PaymentSummary(Long paymentId, String transactionRef, String method, String status,
                                 String maskedInstrument, String failureReason) {
    }

    public record PayOrderResponse(OrderResponse order, PaymentSummary payment) {
    }

    public record PayManyResult(Long orderId, boolean success, String message, PayOrderResponse result) {
    }
}
