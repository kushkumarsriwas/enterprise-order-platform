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
import java.util.ArrayList;
import java.util.List;

public final class OrderDtos {

    static final String PHONE = "^[+0-9 ()-]{7,30}$";

    private OrderDtos() {
    }

    public record OrderLineRequest(@NotNull Long productId, @NotNull @Min(1) @Max(100) Integer quantity) {
    }

    /** Delivery address entered (or pre-filled) at checkout. */
    public record AddressRequest(@NotBlank @Size(max = 120) String recipient,
                                 @NotBlank @Pattern(regexp = PHONE, message = "is not a valid phone number") String phone,
                                 @NotBlank @Size(max = 150) String line1,
                                 @Size(max = 150) String line2,
                                 @NotBlank @Size(max = 60) String city,
                                 @Size(max = 60) String region,
                                 @Size(max = 60) String country) {

        /** "Recipient, line 1, line 2, city, emirate, country (phone)"; the emirate is left out when it is the city. */
        public String formatted() {
            List<String> parts = new ArrayList<>();
            for (String part : new String[]{recipient, line1, line2, city, region,
                    country == null || country.isBlank() ? "United Arab Emirates" : country}) {
                if (part != null && !part.isBlank()
                        && (parts.isEmpty() || !parts.getLast().equalsIgnoreCase(part.trim()))) {
                    parts.add(part.trim());
                }
            }
            return String.join(", ", parts) + " (" + phone.trim() + ")";
        }
    }

    public record PlaceOrderRequest(@NotEmpty @Size(max = 30) List<@Valid OrderLineRequest> items,
                                    @NotNull @Valid AddressRequest address) {
    }

    /** One checkout can contain products of several sellers: it becomes one order per seller. */
    public record CheckoutResponse(List<OrderResponse> orders, BigDecimal grandTotal, String currency) {
    }

    public record ShipRequest(@NotBlank @Size(max = 60) String carrier, @NotBlank @Size(max = 60) String trackingNumber,
                              @Size(max = 255) String note) {
    }

    public record CancelRequest(@Size(max = 255) String reason) {
    }

    /** Generic "update order status" for sellers: SHIPPED (with carrier + tracking), DELIVERED or CANCELLED. */
    public record StatusUpdateRequest(@NotNull OrderStatus status, @Size(max = 60) String carrier,
                                      @Size(max = 60) String trackingNumber, @Size(max = 255) String note) {
    }

    public record OrderItemResponse(Long productId, String sku, String productName, String categoryName,
                                    BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    public record SellerOrderItemResponse(Long productId, String sku, String productName, String categoryName,
                                          BigDecimal unitPrice, BigDecimal unitCost, int quantity, BigDecimal lineTotal,
                                          BigDecimal lineProfit) {
    }

    public record StatusHistoryResponse(String fromStatus, String toStatus, String changedBy, String note,
                                        Instant changedAt) {
    }

    public record OrderResponse(Long id, String orderNumber, Long userId, String customerUsername, Long sellerId,
                                String sellerName, String status, String paymentState, String paymentMethod,
                                Long paymentId, String currency, BigDecimal totalAmount, BigDecimal vatAmount,
                                String shippingAddress, String recipientName, String carrier, String trackingNumber,
                                Instant shippedAt, Instant deliveredAt, String cancelReason, Instant createdAt,
                                Instant updatedAt, Instant paymentDeadline, boolean invoiceAvailable,
                                List<OrderItemResponse> items, List<StatusHistoryResponse> history) {
    }

    /** The seller's (and administrator's) view adds the customer's contact details, costs and profit. */
    public record SellerOrderResponse(Long id, String orderNumber, Long customerId, String customerUsername,
                                      String customerEmail, String customerPhone, Long sellerId, String sellerName,
                                      String status, String paymentState, String paymentMethod, String currency,
                                      BigDecimal totalAmount, BigDecimal vatAmount, BigDecimal totalCost,
                                      BigDecimal grossProfit, String shippingAddress, String recipientName,
                                      String shippingCity, String shippingRegion, String carrier,
                                      String trackingNumber, Instant confirmedAt, Instant shippedAt,
                                      Instant deliveredAt, String cancelReason, Instant createdAt, int units,
                                      List<SellerOrderItemResponse> items, List<StatusHistoryResponse> history) {
    }

    // ------------------------------------------------------------------ payment service <-> order service

    /** What the payment service needs to charge an order: the amount always comes from the order, never the client. */
    public record PaymentIntent(Long orderId, String orderNumber, Long customerId, String customerUsername,
                                String customerEmail, Long sellerId, String sellerName, BigDecimal amount,
                                String currency) {
    }

    /** Result of a payment attempt, reported by the payment service (idempotent: may be reported again). */
    public record PaymentOutcomeRequest(Long paymentId, @Size(max = 40) String transactionRef,
                                        @NotBlank @Pattern(regexp = "CARD|UPI|COD") String method,
                                        @NotBlank @Pattern(regexp = "SUCCESS|PENDING|FAILED") String status,
                                        @Size(max = 60) String maskedInstrument,
                                        @Size(max = 255) String failureReason) {

        public boolean successful() {
            return "SUCCESS".equals(status) || "PENDING".equals(status);
        }
    }

    /**
     * {@code confirmed}: the order is confirmed and the stock is sold. {@code refundRequired}: the money was taken
     * but the order could not be confirmed (expired, cancelled or stock gone), so the payment must be refunded.
     */
    public record PaymentOutcomeResponse(boolean confirmed, boolean refundRequired, String message,
                                         OrderResponse order) {
    }
}
