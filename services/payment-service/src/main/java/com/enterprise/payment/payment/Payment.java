package com.enterprise.payment.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One payment attempt. Card numbers and CVVs are never stored (PCI-DSS): only a masked instrument.
 * The platform keeps a commission ({@code platformFee}); the rest ({@code merchantNet}) belongs to the seller.
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_order", columnList = "order_id"),
        @Index(name = "idx_payments_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_payments_merchant_created", columnList = "merchant_id, created_at")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_ref", nullable = false, unique = true, length = 40)
    private String transactionRef;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_number", nullable = false, length = 40)
    private String orderNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The seller (merchant user id) who receives the money. */
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "platform_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "merchant_net", nullable = false, precision = 14, scale = 2)
    private BigDecimal merchantNet;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "provider_reference", length = 60)
    private String providerReference;

    @Column(name = "masked_instrument", length = 60)
    private String maskedInstrument;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "refund_reason", length = 255)
    private String refundReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version")
    private Long version;

    protected Payment() {
    }

    public Payment(String transactionRef, Long orderId, String orderNumber, Long userId, Long merchantId,
                   BigDecimal amount, String currency, PaymentMethod method, PaymentStatus status,
                   String providerReference, String maskedInstrument, String failureReason, BigDecimal platformFee) {
        this.transactionRef = transactionRef;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.platformFee = status == PaymentStatus.FAILED ? BigDecimal.ZERO : platformFee;
        this.merchantNet = status == PaymentStatus.FAILED ? BigDecimal.ZERO : amount.subtract(platformFee);
        this.currency = currency;
        this.method = method;
        this.status = status;
        this.providerReference = providerReference;
        this.maskedInstrument = maskedInstrument;
        this.failureReason = failureReason;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void refund(String reason) {
        this.status = status == PaymentStatus.PENDING ? PaymentStatus.CANCELLED : PaymentStatus.REFUNDED;
        this.refundReason = reason;
    }

    public void capture() {
        this.status = PaymentStatus.SUCCESS;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPlatformFee() {
        return platformFee;
    }

    public BigDecimal getMerchantNet() {
        return merchantNet;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getMaskedInstrument() {
        return maskedInstrument;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
