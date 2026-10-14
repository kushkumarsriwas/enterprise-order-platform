package com.enterprise.payment.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record ChargeRequest(
            @NotNull Long orderId,
            @NotBlank @Size(max = 40) String orderNumber,
            @NotNull Long userId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull PaymentMethod method,
            @Size(max = 23) String cardNumber,
            @Size(max = 100) String cardHolder,
            @Size(max = 7) String expiry,
            @Size(max = 4) String cvv,
            @Size(max = 100) String upiId) {
    }

    public record RefundRequest(@Size(max = 255) String reason) {
    }

    public record PaymentResponse(Long id, String transactionRef, Long orderId, String orderNumber, Long userId,
                                  BigDecimal amount, String currency, String method, String status,
                                  String maskedInstrument, String failureReason, String refundReason,
                                  Instant createdAt, Instant updatedAt) {

        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(payment.getId(), payment.getTransactionRef(), payment.getOrderId(),
                    payment.getOrderNumber(), payment.getUserId(), payment.getAmount(), payment.getCurrency(),
                    payment.getMethod().name(), payment.getStatus().name(), payment.getMaskedInstrument(),
                    payment.getFailureReason(), payment.getRefundReason(), payment.getCreatedAt(),
                    payment.getUpdatedAt());
        }
    }
}
