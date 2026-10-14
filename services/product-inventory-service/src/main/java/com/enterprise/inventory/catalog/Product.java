package com.enterprise.inventory.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * A product owned by one merchant. It is part of the merchant's inventory and is only visible on the
 * marketplace when {@code listed} is true (some stock is for internal use or not ready for sale).
 */
@Entity
@Table(name = "products",
        uniqueConstraints = @UniqueConstraint(name = "uk_products_merchant_sku", columnNames = {"merchant_id", "sku"}),
        indexes = {
                @Index(name = "idx_products_merchant", columnList = "merchant_id, active"),
                @Index(name = "idx_products_listing", columnList = "listed, active, name"),
                @Index(name = "idx_products_category", columnList = "category_id")
        })
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "merchant_name", nullable = false, length = 80)
    private String merchantName;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Selling price shown to customers. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Weighted average purchase cost per unit (used for cost of goods sold and stock valuation). */
    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean listed;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Product() {
    }

    public Product(Long merchantId, String merchantName, String sku, String name, String description, BigDecimal price,
                   BigDecimal costPrice, Category category, String imageUrl, boolean listed) {
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.costPrice = costPrice;
        this.category = category;
        this.imageUrl = imageUrl;
        this.listed = listed;
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

    public void update(String name, String description, BigDecimal price, BigDecimal costPrice, Category category,
                       String imageUrl, boolean listed) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.costPrice = costPrice;
        this.category = category;
        this.imageUrl = imageUrl;
        this.listed = listed;
    }

    public void setListed(boolean listed) {
        this.listed = listed;
    }

    /** Weighted average cost: blends the cost of units on hand with the cost of the units just received. */
    public void receiveStockAtCost(int unitsOnHand, int unitsReceived, BigDecimal unitCost) {
        int totalUnits = Math.max(unitsOnHand, 0) + unitsReceived;
        if (totalUnits <= 0) {
            return;
        }
        BigDecimal existingValue = costPrice.multiply(BigDecimal.valueOf(Math.max(unitsOnHand, 0)));
        BigDecimal receivedValue = unitCost.multiply(BigDecimal.valueOf(unitsReceived));
        this.costPrice = existingValue.add(receivedValue).divide(BigDecimal.valueOf(totalUnits), 2, RoundingMode.HALF_UP);
    }

    public void archive() {
        this.active = false;
        this.listed = false;
    }

    public boolean isOwnedBy(Long merchantId) {
        return this.merchantId.equals(merchantId);
    }

    public boolean isPurchasable() {
        return active && listed;
    }

    public Long getId() {
        return id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public Category getCategory() {
        return category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isListed() {
        return listed;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
