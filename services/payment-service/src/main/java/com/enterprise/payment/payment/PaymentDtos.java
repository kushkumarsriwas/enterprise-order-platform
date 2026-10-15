package com.enterprise.payment.payment;

import com.enterprise.payment.strategy.PaymentDetails;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** POST /api/payments: pay one order. The amount is never sent by the client - it comes from the order. */
    public record PayOrderRequest(@NotNull Long orderId, @NotNull PaymentMethod method,
                                  @Size(max = 23) String cardNumber, @Size(max = 100) String cardHolder,
                                  @Size(max = 7) String expiry, @Size(max = 4) String cvv,
                                  @Size(max = 100) String upiId) {

        public PaymentDetails details() {
            return new PaymentDetails(cardNumber, cardHolder, expiry, cvv, upiId);
        }

        @Override
        public String toString() {
            return "PayOrderRequest[order=" + orderId + ", method=" + method + "]";
        }
    }

    /** POST /api/payments/checkout: one checkout = one order per seller, all paid with the same method. */
    public record CheckoutPaymentRequest(@NotEmpty @Size(max = 20) List<@NotNull Long> orderIds,
                                         @NotNull PaymentMethod method, @Size(max = 23) String cardNumber,
                                         @Size(max = 100) String cardHolder, @Size(max = 7) String expiry,
                                         @Size(max = 4) String cvv, @Size(max = 100) String upiId) {

        public PayOrderRequest forOrder(Long orderId) {
            return new PayOrderRequest(orderId, method, cardNumber, cardHolder, expiry, cvv, upiId);
        }

        @Override
        public String toString() {
            return "CheckoutPaymentRequest[orders=" + orderIds + ", method=" + method + "]";
        }
    }

    public record RefundRequest(@Size(max = 255) String reason) {
    }

    /**
     * What the customer sees. {@code orderStatus}/{@code orderConfirmed}/{@code message} describe what happened to
     * the order after the payment. The platform commission is not exposed to customers.
     */
    public record PaymentResponse(Long id, String transactionReference, Long orderId, String orderNumber,
                                  Long customerId, Long sellerId, String sellerName, BigDecimal amount,
                                  String currency, String method, String status, String maskedInstrument,
                                  String failureReason, String refundReason, String orderStatus,
                                  boolean orderConfirmed, String message, Instant createdAt, Instant updatedAt) {

        public static PaymentResponse from(Payment payment) {
            return from(payment, null, false, null);
        }

        public static PaymentResponse from(Payment payment, String orderStatus, boolean orderConfirmed,
                                           String message) {
            return new PaymentResponse(payment.getId(), payment.getTransactionReference(), payment.getOrderId(),
                    payment.getOrderNumber(), payment.getCustomerId(), payment.getSellerId(), payment.getSellerName(),
                    payment.getAmount(), payment.getCurrency(), payment.getMethod().name(), payment.getStatus().name(),
                    payment.getMaskedInstrument(), payment.getFailureReason(), payment.getRefundReason(), orderStatus,
                    orderConfirmed, message, payment.getCreatedAt(), payment.getUpdatedAt());
        }
    }

    public record CheckoutPaymentResult(Long orderId, boolean success, String message, PaymentResponse payment) {
    }

    /** What the seller sees: the amount, the platform fee and what they actually earn. */
    public record SellerPaymentResponse(Long id, String transactionReference, Long orderId, String orderNumber,
                                        Long customerId, String customerUsername, BigDecimal amount,
                                        BigDecimal platformFee, BigDecimal sellerNet, String currency, String method,
                                        String status, String maskedInstrument, String failureReason,
                                        String refundReason, Instant createdAt, Instant updatedAt) {

        public static SellerPaymentResponse from(Payment payment) {
            return new SellerPaymentResponse(payment.getId(), payment.getTransactionReference(), payment.getOrderId(),
                    payment.getOrderNumber(), payment.getCustomerId(), payment.getCustomerUsername(),
                    payment.getAmount(), payment.getPlatformFee(), payment.getSellerNet(), payment.getCurrency(),
                    payment.getMethod().name(), payment.getStatus().name(), payment.getMaskedInstrument(),
                    payment.getFailureReason(), payment.getRefundReason(), payment.getCreatedAt(),
                    payment.getUpdatedAt());
        }
    }

    /** The administrator sees every detail of a transaction, including the provider and its reference. */
    public record AdminPaymentResponse(Long id, String transactionReference, Long orderId, String orderNumber,
                                       Long customerId, String customerUsername, Long sellerId, String sellerName,
                                       BigDecimal amount, BigDecimal platformFee, BigDecimal sellerNet,
                                       String currency, String method, String status, String provider,
                                       String providerReference, String maskedInstrument, String failureReason,
                                       String refundReason, boolean orderUpdatePending, Instant createdAt,
                                       Instant updatedAt) {

        public static AdminPaymentResponse from(Payment payment) {
            return new AdminPaymentResponse(payment.getId(), payment.getTransactionReference(), payment.getOrderId(),
                    payment.getOrderNumber(), payment.getCustomerId(), payment.getCustomerUsername(),
                    payment.getSellerId(), payment.getSellerName(), payment.getAmount(), payment.getPlatformFee(),
                    payment.getSellerNet(), payment.getCurrency(), payment.getMethod().name(),
                    payment.getStatus().name(), payment.getProvider(), payment.getProviderReference(),
                    payment.getMaskedInstrument(), payment.getFailureReason(), payment.getRefundReason(),
                    payment.isOrderUpdatePending(), payment.getCreatedAt(), payment.getUpdatedAt());
        }
    }

    public record MethodTotal(String method, long payments, BigDecimal amount) {
    }

    public record DailyVolume(String date, long payments, BigDecimal amount, BigDecimal platformFees) {
    }

    public record SellerVolume(Long sellerId, String sellerName, long payments, BigDecimal amount,
                               BigDecimal platformFees, BigDecimal sellerNet) {
    }

    /** Seller earnings for a period: what was sold, collected, still to collect (cash on delivery) and the fees. */
    public record PaymentSummary(String from, String to, String currency, long payments, BigDecimal grossSales,
                                 BigDecimal collected, BigDecimal awaitingCollection, BigDecimal platformFees,
                                 BigDecimal netEarnings, BigDecimal refunded, long refunds, long failedAttempts,
                                 BigDecimal successRatePercent, BigDecimal feeRatePercent, List<MethodTotal> byMethod,
                                 List<DailyVolume> daily) {
    }

    /** The administrator's view of all money on the marketplace: volume, platform revenue, refunds, failures. */
    public record PlatformSummary(String from, String to, String currency, long payments, BigDecimal totalVolume,
                                  BigDecimal collected, BigDecimal awaitingCollection, BigDecimal platformRevenue,
                                  BigDecimal paidOutToSellers, BigDecimal refunded, long refunds,
                                  long failedAttempts, BigDecimal successRatePercent, BigDecimal feeRatePercent,
                                  List<MethodTotal> byMethod, List<DailyVolume> daily, List<SellerVolume> topSellers) {
    }
}
