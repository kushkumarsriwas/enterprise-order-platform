package com.enterprise.inventory.counting;

import com.enterprise.inventory.catalog.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "stock_count_lines")
public class StockCountLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "count_id", nullable = false)
    private StockCount count;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "bin_location", length = 30)
    private String binLocation;

    /** Units the system expected in the warehouse when the count started. */
    @Column(name = "expected_quantity", nullable = false)
    private int expectedQuantity;

    /** Units actually counted (null = not counted yet). */
    @Column(name = "counted_quantity")
    private Integer countedQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    protected StockCountLine() {
    }

    public StockCountLine(Product product, String binLocation, int expectedQuantity) {
        this.product = product;
        this.sku = product.getSku();
        this.productName = product.getName();
        this.binLocation = binLocation;
        this.expectedQuantity = expectedQuantity;
        this.unitCost = product.getCostPrice();
    }

    void attachTo(StockCount count) {
        this.count = count;
    }

    public void record(Integer counted) {
        this.countedQuantity = counted;
    }

    /** Counted minus expected (0 while not counted). */
    public int variance() {
        return countedQuantity == null ? 0 : countedQuantity - expectedQuantity;
    }

    public Long getId() {
        return id;
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

    public String getBinLocation() {
        return binLocation;
    }

    public int getExpectedQuantity() {
        return expectedQuantity;
    }

    public Integer getCountedQuantity() {
        return countedQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }
}
