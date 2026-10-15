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
 * One payment attempt (stored in Oracle locally, PostgreSQL on Render). Card numbers and CVVs are never stored
 * (PCI-DSS): only a masked instrument. The platform keeps a commission ({@code platformFee}); the rest
 * ({@code sellerNet}) belongs to the seller.
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_order", columnList = "order_id"),
        @Index(name = "idx_payments_customer_created", columnList = "customer_id, created_at"),
        @Index(name = "idx_payments_seller_created", columnList = "seller_id, created_at"),
        @Index(name = "idx_payments_status_created", columnList = "payment_status, created_at")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 40)
    private String transactionReference;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_number", nullable = false, length = 40)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_username", length = 50)
    private String customerUsername;

    /** The seller who receives the money. */
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "seller_name", length = 80)
    private String sellerName;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "platform_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "seller_net", nullable = false, precision = 14, scale = 2)
    private BigDecimal sellerNet;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "provider", length = 20)
    private String provider;

    @Column(name = "provider_reference", length = 60)
    private String providerReference;

    @Column(name = "masked_instrument", length = 60)
    private String maskedInstrument;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "refund_reason", length = 255)
    private String refundReason;

    /** The order service has not acknowledged this payment yet; {@link OrderUpdateRetryJob} retries. */
    @Column(name = "order_update_pending", nullable = false)
    private boolean orderUpdatePending;

    @Column(name = "order_update_attempts", nullable = false)
    private int orderUpdateAttempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version")
    private Long version;

    protected Payment() {
    }

    public Payment(String transactionReference, Long orderId, String orderNumber, Long customerId,
                   String customerUsername, Long sellerId, String sellerName, BigDecimal amount, String currency,
                   PaymentMethod method, PaymentStatus status, String provider, String providerReference,
                   String maskedInstrument, String failureReason, BigDecimal platformFee) {
        this.transactionReference = transactionReference;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.customerUsername = customerUsername;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.amount = amount;
        this.platformFee = status == PaymentStatus.FAILED ? BigDecimal.ZERO : platformFee;
        this.sellerNet = status == PaymentStatus.FAILED ? BigDecimal.ZERO : amount.subtract(platformFee);
        this.currency = currency;
        this.method = method;
        this.status = status;
        this.provider = provider;
        this.providerReference = providerReference;
        this.maskedInstrument = maskedInstrument;
        this.failureReason = failureReason;
        this.orderUpdatePending = true;
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

    /** Money goes back: a captured payment becomes REFUNDED, cash on delivery not yet collected is CANCELLED. */
    public void refund(String reason) {
        if (status == PaymentStatus.SUCCESS) {
            status = PaymentStatus.REFUNDED;
        } else if (status == PaymentStatus.PENDING) {
            status = PaymentStatus.CANCELLED;
        }
        this.refundReason = reason;
        this.platformFee = BigDecimal.ZERO;
        this.sellerNet = BigDecimal.ZERO;
    }

    public void capture() {
        this.status = PaymentStatus.SUCCESS;
    }

    public void orderUpdated() {
        this.orderUpdatePending = false;
    }

    public void orderUpdateFailed() {
        this.orderUpdateAttempts++;
    }

    public boolean isSettled() {
        return status == PaymentStatus.SUCCESS || status == PaymentStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getCustomerUsername() {
        return customerUsername;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPlatformFee() {
        return platformFee;
    }

    public BigDecimal getSellerNet() {
        return sellerNet;
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

    public String getProvider() {
        return provider;
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

    public boolean isOrderUpdatePending() {
        return orderUpdatePending;
    }

    public int getOrderUpdateAttempts() {
        return orderUpdateAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
