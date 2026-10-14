package com.enterprise.inventory.purchasing;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** An order placed with a supplier (document PO-00001). Stock arrives through goods receipts. */
@Entity
@Table(name = "purchase_orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_po_seller_number", columnNames = {"seller_id", "number"}),
        indexes = {
                @Index(name = "idx_po_seller_status", columnList = "seller_id, status"),
                @Index(name = "idx_po_seller_created", columnList = "seller_id, created_at")
        })
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 20)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** Where the goods will be delivered. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(length = 500)
    private String notes;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(Long sellerId, String number, Supplier supplier, Warehouse warehouse, LocalDate expectedDate,
                         String notes, String createdBy) {
        this.sellerId = sellerId;
        this.number = number;
        this.supplier = supplier;
        this.warehouse = warehouse;
        this.expectedDate = expectedDate;
        this.notes = notes;
        this.createdBy = createdBy;
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

    /** Only drafts can be edited: replaces supplier, delivery details and every line. */
    public void revise(Supplier supplier, Warehouse warehouse, LocalDate expectedDate, String notes) {
        requireStatus(PurchaseOrderStatus.DRAFT, "edited");
        this.supplier = supplier;
        this.warehouse = warehouse;
        this.expectedDate = expectedDate;
        this.notes = notes;
        this.lines.clear();
        this.totalAmount = BigDecimal.ZERO;
    }

    public void addLine(PurchaseOrderLine line) {
        line.attachTo(this);
        lines.add(line);
        totalAmount = totalAmount.add(line.getLineTotal());
    }

    public void submit() {
        if (lines.isEmpty()) {
            throw ApiException.badRequest("Add at least one product before sending the purchase order");
        }
        moveTo(PurchaseOrderStatus.ORDERED);
        this.orderDate = LocalDate.now();
    }

    public void cancel() {
        if (lines.stream().anyMatch(line -> line.getQuantityReceived() > 0)) {
            throw ApiException.conflict("Purchase order " + number + " already has received goods - close it instead");
        }
        moveTo(PurchaseOrderStatus.CANCELLED);
    }

    /** Short delivery accepted: the remaining quantities will not arrive. */
    public void close() {
        moveTo(PurchaseOrderStatus.CLOSED);
    }

    /** Called after quantities were received on the lines. */
    public void refreshReceiptStatus() {
        boolean complete = lines.stream().allMatch(line -> line.getQuantityOutstanding() == 0);
        moveTo(complete ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        if (complete) {
            receivedAt = Instant.now();
        }
    }

    public boolean isOpen() {
        return PurchaseOrderStatus.OPEN.contains(status);
    }

    /** Expected date passed and goods are still outstanding. */
    public boolean isOverdue() {
        return isOpen() && expectedDate != null && expectedDate.isBefore(LocalDate.now());
    }

    private void moveTo(PurchaseOrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw ApiException.conflict("Purchase order " + number + " is " + status + " and cannot become " + next);
        }
        status = next;
    }

    private void requireStatus(PurchaseOrderStatus expected, String action) {
        if (status != expected) {
            throw ApiException.conflict("Purchase order " + number + " is " + status + " and can no longer be " + action);
        }
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

    public Supplier getSupplier() {
        return supplier;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PurchaseOrderLine> getLines() {
        return lines;
    }
}
