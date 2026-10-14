package com.enterprise.inventory.counting;

import com.enterprise.inventory.catalog.Category;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.warehouse.Warehouse;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A physical stock count (cycle count) of one warehouse, optionally one category (document CNT-00001).
 * The expected quantities are frozen when the count starts; on completion every difference between counted
 * and expected becomes a COUNT_ADJUSTMENT in the stock ledger.
 */
@Entity
@Table(name = "stock_counts",
        uniqueConstraints = @UniqueConstraint(name = "uk_counts_seller_number", columnNames = {"seller_id", "number"}),
        indexes = @Index(name = "idx_counts_seller_status", columnList = "seller_id, status"))
public class StockCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 20)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockCountStatus status = StockCountStatus.IN_PROGRESS;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_by", length = 60)
    private String completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "count", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productName")
    private List<StockCountLine> lines = new ArrayList<>();

    protected StockCount() {
    }

    public StockCount(Long sellerId, String number, Warehouse warehouse, Category category, String note,
                      String createdBy) {
        this.sellerId = sellerId;
        this.number = number;
        this.warehouse = warehouse;
        this.category = category;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void addLine(StockCountLine line) {
        line.attachTo(this);
        lines.add(line);
    }

    public void requireInProgress() {
        if (status != StockCountStatus.IN_PROGRESS) {
            throw ApiException.conflict("Stock count " + number + " is " + status);
        }
    }

    public void complete(String actor) {
        requireInProgress();
        status = StockCountStatus.COMPLETED;
        completedBy = actor;
        completedAt = Instant.now();
    }

    public void cancel(String actor) {
        requireInProgress();
        status = StockCountStatus.CANCELLED;
        completedBy = actor;
        completedAt = Instant.now();
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

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public Category getCategory() {
        return category;
    }

    public StockCountStatus getStatus() {
        return status;
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

    public String getCompletedBy() {
        return completedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<StockCountLine> getLines() {
        return lines;
    }
}
