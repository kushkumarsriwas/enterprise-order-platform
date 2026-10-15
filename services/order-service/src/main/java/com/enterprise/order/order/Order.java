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
        @Index(name = "idx_orders_seller_status", columnList = "seller_id, status, created_at"),
        @Index(name = "idx_orders_number", columnList = "order_number"),
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

    /** Who receives the parcel (from the delivery address). */
    @Column(name = "recipient_name", length = 120)
    private String recipientName;

    /** City and emirate of the delivery address (sales by region). */
    @Column(name = "shipping_city", length = 60)
    private String shippingCity;

    @Column(name = "shipping_region", length = 60)
    private String shippingRegion;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "seller_name", nullable = false, length = 80)
    private String sellerName;

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

    /** When the current payment attempt started; an attempt that never reported back goes stale. */
    @Column(name = "payment_started_at")
    private Instant paymentStartedAt;

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
                 Long sellerId, String sellerName, String currency, String shippingAddress) {
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.customerUsername = customerUsername;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
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

    public void deliverTo(String recipientName, String city, String region) {
        this.recipientName = recipientName;
        this.shippingCity = city;
        this.shippingRegion = region;
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

    /** The payment service is about to charge this order: blocks a second, concurrent payment attempt. */
    public void startPaymentAttempt() {
        this.paymentInProgress = true;
        this.paymentStartedAt = Instant.now();
    }

    public void endPaymentAttempt() {
        this.paymentInProgress = false;
        this.paymentStartedAt = null;
    }

    /** True while a payment attempt is running and has not gone stale (the payment service never reported back). */
    public boolean hasActivePaymentAttempt(java.time.Duration staleAfter) {
        return paymentInProgress && paymentStartedAt != null && paymentStartedAt.plus(staleAfter).isAfter(Instant.now());
    }

    public void confirmPayment(Long paymentId, String method, PaymentState state, String actor, String note) {
        this.paymentId = paymentId;
        this.paymentMethod = method;
        this.paymentState = state;
        endPaymentAttempt();
        changeStatus(OrderStatus.CONFIRMED, actor, note);
    }

    public void cancel(String actor, String reason, PaymentState newPaymentState) {
        changeStatus(OrderStatus.CANCELLED, actor, reason);
        this.cancelReason = reason;
        this.paymentState = newPaymentState;
        endPaymentAttempt();
    }

    /** Remembers which payment was refunded or voided when a paid order could not be confirmed. */
    public void recordPayment(Long paymentId, String method) {
        this.paymentId = paymentId;
        this.paymentMethod = method;
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

    public boolean isSoldBy(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }

    public BigDecimal getTotalCost() {
        return items.stream().map(OrderItem::getLineCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getUnits() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
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

    public Long getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getShippingCity() {
        return shippingCity;
    }

    public String getShippingRegion() {
        return shippingRegion;
    }

    /** When the order was confirmed (paid or cash on delivery accepted), from the status history. */
    public Instant getConfirmedAt() {
        return history.stream().filter(h -> h.getToStatus() == OrderStatus.CONFIRMED)
                .map(OrderStatusHistory::getChangedAt).findFirst().orElse(null);
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

    public Instant getPaymentStartedAt() {
        return paymentStartedAt;
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
