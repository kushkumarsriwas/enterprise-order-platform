package com.enterprise.order.order;

import com.enterprise.order.common.ApiException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_orders_merchant_status", columnList = "merchant_id, status, created_at"),
        @Index(name = "idx_orders_status_created", columnList = "status, created_at")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "customer_username", nullable = false, length = 50)
    private String customerUsername;

    @Column(name = "customer_email", length = 120)
    private String customerEmail;

    @Column(name = "customer_phone", length = 30)
    private String customerPhone;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "merchant_name", nullable = false, length = 80)
    private String merchantName;

    @Column(length = 60)
    private String carrier;

    @Column(name = "tracking_number", length = 60)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_state", nullable = false, length = 20)
    private PaymentState paymentState;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "payment_in_progress", nullable = false)
    private boolean paymentInProgress;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "shipping_address", nullable = false, length = 500)
    private String shippingAddress;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt, id")
    private List<OrderStatusHistory> history = new ArrayList<>();

    protected Order() {
    }

    public Order(String orderNumber, Long userId, String customerUsername, String customerEmail, String customerPhone,
                 Long merchantId, String merchantName, String currency, String shippingAddress) {
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.customerUsername = customerUsername;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.currency = currency;
        this.shippingAddress = shippingAddress;
        this.status = OrderStatus.PENDING;
        this.paymentState = PaymentState.UNPAID;
        this.history.add(new OrderStatusHistory(this, null, OrderStatus.PENDING, customerUsername, "Order placed"));
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

    public void addItem(OrderItem item) {
        item.attachTo(this);
        items.add(item);
        totalAmount = totalAmount.add(item.getLineTotal());
    }

    /** All status changes go through here, so the state machine rules can't be bypassed. */
    public void changeStatus(OrderStatus next, String actor, String note) {
        if (!status.canTransitionTo(next)) {
            throw ApiException.conflict("Order " + orderNumber + " cannot change from " + status + " to " + next);
        }
        history.add(new OrderStatusHistory(this, status, next, actor, note));
        this.status = next;
    }

    public void confirmPayment(Long paymentId, String method, PaymentState state, String actor, String note) {
        this.paymentId = paymentId;
        this.paymentMethod = method;
        this.paymentState = state;
        this.paymentInProgress = false;
        changeStatus(OrderStatus.CONFIRMED, actor, note);
    }

    public void cancel(String actor, String reason, PaymentState newPaymentState) {
        changeStatus(OrderStatus.CANCELLED, actor, reason);
        this.cancelReason = reason;
        this.paymentState = newPaymentState;
        this.paymentInProgress = false;
    }

    public void ship(String carrier, String trackingNumber, String actor, String note) {
        changeStatus(OrderStatus.SHIPPED, actor, note == null ? "Shipped with " + carrier + " (" + trackingNumber + ")" : note);
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.shippedAt = Instant.now();
    }

    public void deliver(String actor) {
        changeStatus(OrderStatus.DELIVERED, actor, "Delivered to the customer");
        this.deliveredAt = Instant.now();
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isSoldBy(Long merchantId) {
        return this.merchantId.equals(merchantId);
    }

    public java.math.BigDecimal getTotalCost() {
        return items.stream().map(OrderItem::getLineCost).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    public void setPaymentInProgress(boolean paymentInProgress) {
        this.paymentInProgress = paymentInProgress;
    }

    public void setPaymentState(PaymentState paymentState) {
        this.paymentState = paymentState;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCustomerUsername() {
        return customerUsername;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public PaymentState getPaymentState() {
        return paymentState;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public boolean isPaymentInProgress() {
        return paymentInProgress;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public List<OrderStatusHistory> getHistory() {
        return history;
    }
}
