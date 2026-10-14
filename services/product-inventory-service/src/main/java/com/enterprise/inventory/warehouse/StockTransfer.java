package com.enterprise.inventory.warehouse;

import com.enterprise.inventory.catalog.Product;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Units moved from one warehouse to another (document TRF-00001). */
@Entity
@Table(name = "stock_transfers",
        uniqueConstraints = @UniqueConstraint(name = "uk_transfers_seller_number", columnNames = {"seller_id", "number"}),
        indexes = @Index(name = "idx_transfers_seller_time", columnList = "seller_id, created_at"))
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 20)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_warehouse_id", nullable = false)
    private Warehouse fromWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_warehouse_id", nullable = false)
    private Warehouse toWarehouse;

    @Column(name = "total_units", nullable = false)
    private int totalUnits;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<StockTransferLine> lines = new ArrayList<>();

    protected StockTransfer() {
    }

    public StockTransfer(Long sellerId, String number, Warehouse fromWarehouse, Warehouse toWarehouse, String note,
                         String createdBy) {
        this.sellerId = sellerId;
        this.number = number;
        this.fromWarehouse = fromWarehouse;
        this.toWarehouse = toWarehouse;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void addLine(Product product, int quantity) {
        lines.add(new StockTransferLine(this, product, quantity));
        totalUnits += quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getNumber() {
        return number;
    }

    public Warehouse getFromWarehouse() {
        return fromWarehouse;
    }

    public Warehouse getToWarehouse() {
        return toWarehouse;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public String getNote() {
        return note;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<StockTransferLine> getLines() {
        return lines;
    }
}
