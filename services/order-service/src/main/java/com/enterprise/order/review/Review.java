package com.enterprise.order.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Verified-purchase product review: only customers who received the product can write one, once per product. */
@Entity
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_reviews_customer_product", columnNames = {"customer_id", "product_id"}),
        indexes = {
                @Index(name = "idx_reviews_product", columnList = "product_id, created_at"),
                @Index(name = "idx_reviews_seller", columnList = "seller_id, created_at")
        })
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "seller_name", length = 80)
    private String sellerName;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false, length = 50)
    private String customerName;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    /** Public answer of the seller, shown under the review. */
    @Column(name = "seller_reply", length = 1000)
    private String sellerReply;

    @Column(name = "seller_replied_at")
    private Instant sellerRepliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
    }

    public Review(Long productId, String productName, Long sellerId, String sellerName, Long orderId, Long customerId,
                  String customerName, int rating, String comment) {
        this.productId = productId;
        this.productName = productName;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.rating = rating;
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void reply(String text) {
        this.sellerReply = text;
        this.sellerRepliedAt = text == null ? null : Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public String getSellerReply() {
        return sellerReply;
    }

    public Instant getSellerRepliedAt() {
        return sellerRepliedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
