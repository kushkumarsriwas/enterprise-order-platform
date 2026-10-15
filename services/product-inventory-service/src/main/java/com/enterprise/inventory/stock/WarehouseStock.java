package com.enterprise.inventory.stock;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.warehouse.Warehouse;
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
import java.time.Instant;

/** Units of one product physically in one warehouse. Changed only by {@link StockLedger}. */
@Entity
@Table(name = "warehouse_stock",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_product_warehouse", columnNames = {"product_id", "warehouse_id"}),
        indexes = @Index(name = "idx_stock_warehouse", columnList = "warehouse_id"))
public class WarehouseStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    /** Shelf / bin where the units are kept, e.g. "A-03-2". */
    @Column(name = "bin_location", length = 30)
    private String binLocation;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected WarehouseStock() {
    }

    public WarehouseStock(Product product, Warehouse warehouse) {
        this.product = product;
        this.warehouse = warehouse;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    void add(int delta) {
        if (onHand + delta < 0) {
            throw new IllegalStateException("Negative stock for product " + product.getId() + " in " + warehouse.getCode());
        }
        onHand += delta;
    }

    public void setBinLocation(String binLocation) {
        this.binLocation = binLocation;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public int getOnHand() {
        return onHand;
    }

    public String getBinLocation() {
        return binLocation;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
