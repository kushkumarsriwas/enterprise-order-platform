package com.enterprise.inventory.stock;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.warehouse.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only stock ledger: who changed which product's stock, where, by how much, why and when.
 * Rows are never updated or deleted, so the ledger is an audit trail and the source of stock analytics.
 */
@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_movements_seller_time", columnList = "seller_id, created_at"),
        @Index(name = "idx_movements_product_time", columnList = "product_id, created_at"),
        @Index(name = "idx_movements_reference", columnList = "reference")
})
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Null for reservation events (no physical move). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    /** Signed change: + units in, - units out (for reservations: change of the available quantity). */
    @Column(nullable = false)
    private int quantity;

    /** Units on hand in the warehouse after this movement (the "stock card" running balance). */
    @Column(name = "balance_after")
    private Integer balanceAfter;

    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    /** Selling price for SOLD / RETURNED rows (revenue analytics). */
    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AdjustmentReason reason;

    /** Order number, PO number, GRN, transfer or count number. */
    @Column(length = 40)
    private String reference;

    @Column(length = 255)
    private String note;

    @Column(name = "performed_by", length = 60)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(Product product, Warehouse warehouse, MovementType type, int quantity, Integer balanceAfter,
                         BigDecimal unitCost, BigDecimal unitPrice, AdjustmentReason reason, String reference,
                         String note, String performedBy) {
        this.sellerId = product.getSellerId();
        this.product = product;
        this.warehouse = warehouse;
        this.type = type;
        this.quantity = quantity;
        this.balanceAfter = balanceAfter;
        this.unitCost = unitCost;
        this.unitPrice = unitPrice;
        this.reason = reason;
        this.reference = reference;
        this.note = note;
        this.performedBy = performedBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public Product getProduct() {
        return product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public MovementType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public Integer getBalanceAfter() {
        return balanceAfter;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public AdjustmentReason getReason() {
        return reason;
    }

    public String getReference() {
        return reference;
    }

    public String getNote() {
        return note;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
