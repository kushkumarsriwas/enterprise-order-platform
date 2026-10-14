package com.enterprise.inventory.purchasing;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.common.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_lines", indexes = @Index(name = "idx_po_lines_product", columnList = "product_id"))
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "quantity_ordered", nullable = false)
    private int quantityOrdered;

    @Column(name = "quantity_received", nullable = false)
    private int quantityReceived;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    protected PurchaseOrderLine() {
    }

    public PurchaseOrderLine(Product product, int quantityOrdered, BigDecimal unitCost) {
        this.product = product;
        this.sku = product.getSku();
        this.productName = product.getName();
        this.quantityOrdered = quantityOrdered;
        this.unitCost = unitCost;
        this.lineTotal = unitCost.multiply(BigDecimal.valueOf(quantityOrdered));
    }

    void attachTo(PurchaseOrder order) {
        this.purchaseOrder = order;
    }

    public void receive(int quantity) {
        if (quantity > getQuantityOutstanding()) {
            throw ApiException.badRequest("Only " + getQuantityOutstanding() + " unit(s) of " + sku
                    + " are still outstanding on this purchase order");
        }
        quantityReceived += quantity;
    }

    public int getQuantityOutstanding() {
        return quantityOrdered - quantityReceived;
    }

    public Long getId() {
        return id;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public Product getProduct() {
        return product;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantityOrdered() {
        return quantityOrdered;
    }

    public int getQuantityReceived() {
        return quantityReceived;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
