package com.enterprise.inventory.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Append-only audit trail of every stock change (who, what, why, when). */
@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_movements_product_time", columnList = "product_id, created_at"),
        @Index(name = "idx_movements_merchant_time", columnList = "merchant_id, created_at")
})
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    /** Change of the available quantity (negative when stock leaves the shelf). */
    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(length = 40)
    private String reference;

    @Column(length = 255)
    private String note;

    @Column(name = "performed_by", length = 60)
    private String performedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(Long merchantId, Long productId, MovementType type, int quantityChange, String reference,
                         String note, String performedBy) {
        this.merchantId = merchantId;
        this.productId = productId;
        this.type = type;
        this.quantityChange = quantityChange;
        this.reference = reference;
        this.note = note;
        this.performedBy = performedBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public Long getProductId() {
        return productId;
    }

    public MovementType getType() {
        return type;
    }

    public int getQuantityChange() {
        return quantityChange;
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
