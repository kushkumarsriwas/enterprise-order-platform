package com.enterprise.inventory.stock;

import com.enterprise.inventory.catalog.Product;
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
 * Stock level of one product (one-to-one, shares the product's primary key).
 * available = can be sold now, reserved = held for orders that are not paid yet.
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

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Inventory() {
    }

    public Inventory(Product product, int availableQuantity, int reorderLevel) {
        this.product = product;
        this.availableQuantity = availableQuantity;
        this.reorderLevel = reorderLevel;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public boolean isLowStock() {
        return availableQuantity <= reorderLevel;
    }

    public void setLevels(int availableQuantity, int reorderLevel) {
        this.availableQuantity = availableQuantity;
        this.reorderLevel = reorderLevel;
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

    public int getReorderLevel() {
        return reorderLevel;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
