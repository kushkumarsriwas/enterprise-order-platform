package com.enterprise.inventory.purchasing;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.warehouse.Warehouse;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Goods receipt note (GRN-00001): what arrived, where, at which cost - with or without a purchase order. */
@Entity
@Table(name = "goods_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_grn_seller_number", columnNames = {"seller_id", "number"}),
        indexes = @Index(name = "idx_grn_seller_time", columnList = "seller_id, received_at"))
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 20)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "supplier_name", length = 120)
    private String supplierName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Supplier's delivery note or invoice number. */
    @Column(name = "supplier_reference", length = 60)
    private String supplierReference;

    @Column(length = 255)
    private String note;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "total_units", nullable = false)
    private int totalUnits;

    @Column(name = "received_by", nullable = false, length = 60)
    private String receivedBy;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<GoodsReceiptLine> lines = new ArrayList<>();

    protected GoodsReceipt() {
    }

    public GoodsReceipt(Long sellerId, String number, PurchaseOrder purchaseOrder, Supplier supplier, Warehouse warehouse,
                        String supplierReference, String note, String receivedBy) {
        this.sellerId = sellerId;
        this.number = number;
        this.purchaseOrder = purchaseOrder;
        this.supplier = supplier;
        this.supplierName = supplier == null ? null : supplier.getName();
        this.warehouse = warehouse;
        this.supplierReference = supplierReference;
        this.note = note;
        this.receivedBy = receivedBy;
        this.receivedAt = Instant.now();
    }

    public void addLine(Product product, int quantity, BigDecimal unitCost) {
        GoodsReceiptLine line = new GoodsReceiptLine(this, product, quantity, unitCost);
        lines.add(line);
        totalAmount = totalAmount.add(line.getLineTotal());
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

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public String getSupplierReference() {
        return supplierReference;
    }

    public String getNote() {
        return note;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public List<GoodsReceiptLine> getLines() {
        return lines;
    }
}
