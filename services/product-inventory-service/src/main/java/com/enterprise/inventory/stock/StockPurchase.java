package com.enterprise.inventory.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Goods received from a supplier (stock-in at a unit cost). Drives the weighted average cost. */
@Entity
@Table(name = "stock_purchases", indexes = @Index(name = "idx_purchases_merchant_time", columnList = "merchant_id, purchased_at"))
public class StockPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCost;

    @Column(length = 100)
    private String supplier;

    @Column(length = 60)
    private String reference;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    protected StockPurchase() {
    }

    public StockPurchase(Long merchantId, Long productId, String sku, String productName, int quantity,
                         BigDecimal unitCost, String supplier, String reference) {
        this.merchantId = merchantId;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.totalCost = unitCost.multiply(BigDecimal.valueOf(quantity));
        this.supplier = supplier;
        this.reference = reference;
        this.purchasedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public String getSupplier() {
        return supplier;
    }

    public String getReference() {
        return reference;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }
}
