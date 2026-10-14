package com.enterprise.inventory.stock;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.common.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Stock position of one product across all of the seller's warehouses (one row per product, sharing its id).
 * <ul>
 *   <li>available = can be sold now</li>
 *   <li>reserved = held for orders that are not paid yet (still physically on the shelf)</li>
 * </ul>
 * Invariant kept by {@link StockLedger}: available + reserved == sum of on-hand over all warehouses.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Inventory() {
    }

    public Inventory(Product product) {
        this.product = product;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** Units physically on the shelves (available + reserved). */
    public int onHand() {
        return availableQuantity + reservedQuantity;
    }

    void addAvailable(int delta) {
        if (availableQuantity + delta < 0) {
            throw ApiException.conflict("Only " + availableQuantity + " unit(s) of " + product.getSku()
                    + " are available" + (reservedQuantity > 0 ? " (" + reservedQuantity
                    + " more are reserved for orders awaiting payment)" : ""));
        }
        availableQuantity += delta;
    }

    void consumeReserved(int quantity) {
        if (reservedQuantity < quantity) {
            throw ApiException.conflict("Reserved quantity mismatch for " + product.getSku());
        }
        reservedQuantity -= quantity;
    }

    public StockStatus status(int reorderPoint) {
        return StockStatus.of(availableQuantity, reorderPoint);
    }

    public Long getProductId() {
        return productId;
    }

    public Product getProduct() {
        return product;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
