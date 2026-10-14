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

    public record PlaceOrderRequest(@NotEmpty @Size(max = 20) List<@Valid OrderLineRequest> items,
                                    @NotBlank @Size(min = 10, max = 500) String shippingAddress) {
    }

    public record PayOrderRequest(
            @NotBlank @Pattern(regexp = "CARD|UPI|COD", message = "must be CARD, UPI or COD") String method,
            @Size(max = 23) String cardNumber,
            @Size(max = 100) String cardHolder,
            @Size(max = 7) String expiry,
            @Size(max = 4) String cvv,
            @Size(max = 100) String upiId) {
    }

    public record StatusChangeRequest(@NotNull OrderStatus status, @Size(max = 255) String note) {
    }

    public record CancelRequest(@Size(max = 255) String reason) {
    }

    public record OrderItemResponse(Long productId, String sku, String productName, BigDecimal unitPrice,
                                    int quantity, BigDecimal lineTotal) {
    }

    public record StatusHistoryResponse(String fromStatus, String toStatus, String changedBy, String note,
                                        Instant changedAt) {
    }

    public record OrderResponse(Long id, String orderNumber, Long userId, String customerUsername, String status,
                                String paymentState, String paymentMethod, Long paymentId, String currency,
                                BigDecimal totalAmount, String shippingAddress, String cancelReason,
                                Instant createdAt, Instant updatedAt, Instant paymentDeadline,
                                List<OrderItemResponse> items, List<StatusHistoryResponse> history) {
    }

    public record PaymentSummary(Long paymentId, String transactionRef, String method, String status,
                                 String maskedInstrument, String failureReason) {
    }

    public record PayOrderResponse(OrderResponse order, PaymentSummary payment) {
    }
}
