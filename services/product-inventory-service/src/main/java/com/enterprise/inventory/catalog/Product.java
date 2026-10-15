package com.enterprise.inventory.catalog;

import com.enterprise.inventory.purchasing.Supplier;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A product owned by one seller. Every product is part of the seller's inventory; it is only visible to
 * customers when {@code listed} is true (some items are raw materials, spare parts or not ready for sale).
 * <p>
 * {@code @DynamicUpdate}: an UPDATE only writes the changed columns, so counters that are incremented with
 * bulk statements (units sold, ratings) are never overwritten by a seller saving the product form.
 */
@Entity
@DynamicUpdate
@Table(name = "products",
        uniqueConstraints = @UniqueConstraint(name = "uk_products_seller_sku", columnNames = {"seller_id", "sku"}),
        indexes = {
                @Index(name = "idx_products_seller", columnList = "seller_id, active"),
                @Index(name = "idx_products_marketplace", columnList = "listed, active, seller_active, store_suspended, category_id"),
                @Index(name = "idx_products_barcode", columnList = "seller_id, barcode")
        })
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "seller_name", nullable = false, length = 80)
    private String sellerName;

    @Column(nullable = false, length = 40)
    private String sku;

    /** EAN / UPC printed on the package (optional). */
    @Column(length = 40)
    private String barcode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 60)
    private String brand;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Unit unit = Unit.PIECE;

    /** Selling price shown to customers (UAE prices include 5% VAT). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Optional "was" price: when higher than the price the storefront shows the saving. */
    @Column(name = "compare_at_price", precision = 12, scale = 2)
    private BigDecimal compareAtPrice;

    /** Weighted average purchase cost per unit (cost of goods sold, stock valuation). */
    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_supplier_id")
    private Supplier preferredSupplier;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Reorder when available stock falls to this level. */
    @Column(name = "reorder_point", nullable = false)
    private int reorderPoint;

    /** Usual quantity to order from the supplier. */
    @Column(name = "reorder_quantity", nullable = false)
    private int reorderQuantity;

    @Column(nullable = false)
    private boolean listed;

    /** False once archived (kept for order history and the stock ledger). */
    @Column(nullable = false)
    private boolean active = true;

    /** False while the seller's store is in vacation mode: listings are hidden from customers. */
    @Column(name = "seller_active", nullable = false)
    private boolean sellerActive = true;

    /** True while the platform administrator has suspended the store: listings are hidden from customers. */
    @Column(name = "store_suspended", nullable = false)
    private boolean storeSuspended;

    @Column(name = "rating_average", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAverage = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "units_sold", nullable = false)
    private long unitsSold;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Product() {
    }

    public Product(Long sellerId, String sellerName, String sku, String name, BigDecimal price, BigDecimal costPrice) {
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.costPrice = costPrice;
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

    /** Catalogue details (everything a seller edits on the product form). */
    public void describe(String name, String brand, String barcode, String description, Unit unit, Category category,
                         String imageUrl) {
        this.name = name;
        this.brand = brand;
        this.barcode = barcode;
        this.description = description;
        this.unit = unit == null ? Unit.PIECE : unit;
        this.category = category;
        this.imageUrl = imageUrl;
    }

    public void price(BigDecimal price, BigDecimal compareAtPrice, BigDecimal costPrice) {
        this.price = price;
        this.compareAtPrice = compareAtPrice;
        this.costPrice = costPrice;
    }

    public void planning(int reorderPoint, int reorderQuantity, Supplier preferredSupplier) {
        this.reorderPoint = reorderPoint;
        this.reorderQuantity = reorderQuantity;
        this.preferredSupplier = preferredSupplier;
    }

    public void setListed(boolean listed) {
        this.listed = listed;
    }

    /** Store-level visibility, copied onto new products (vacation mode / suspension by the administrator). */
    public void setStoreVisibility(boolean sellerActive, boolean storeSuspended) {
        this.sellerActive = sellerActive;
        this.storeSuspended = storeSuspended;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public void setPriceOnly(BigDecimal price) {
        this.price = price;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    /** Weighted average cost: blends the cost of the units on hand with the cost of the units received. */
    public void receiveStockAtCost(int unitsOnHand, int unitsReceived, BigDecimal unitCost) {
        int onHand = Math.max(unitsOnHand, 0);
        int totalUnits = onHand + unitsReceived;
        if (totalUnits <= 0 || unitCost == null) {
            return;
        }
        BigDecimal existingValue = costPrice.multiply(BigDecimal.valueOf(onHand));
        BigDecimal receivedValue = unitCost.multiply(BigDecimal.valueOf(unitsReceived));
        this.costPrice = existingValue.add(receivedValue).divide(BigDecimal.valueOf(totalUnits), 2, RoundingMode.HALF_UP);
    }

    public void archive() {
        this.active = false;
        this.listed = false;
    }

    public boolean isOwnedBy(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }

    /** Can a customer buy it right now? */
    public boolean isPurchasable() {
        return active && listed && sellerActive && !storeSuspended;
    }

    /** Percentage saved against the "was" price, or 0. */
    public int discountPercent() {
        if (compareAtPrice == null || compareAtPrice.compareTo(price) <= 0) {
            return 0;
        }
        return compareAtPrice.subtract(price).multiply(BigDecimal.valueOf(100))
                .divide(compareAtPrice, 0, RoundingMode.HALF_UP).intValue();
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public String getSku() {
        return sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getDescription() {
        return description;
    }

    public Unit getUnit() {
        return unit;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getCompareAtPrice() {
        return compareAtPrice;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public Category getCategory() {
        return category;
    }

    public Supplier getPreferredSupplier() {
        return preferredSupplier;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getReorderPoint() {
        return reorderPoint;
    }

    public int getReorderQuantity() {
        return reorderQuantity;
    }

    public boolean isListed() {
        return listed;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSellerActive() {
        return sellerActive;
    }

    public boolean isStoreSuspended() {
        return storeSuspended;
    }

    public BigDecimal getRatingAverage() {
        return ratingAverage;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public long getUnitsSold() {
        return unitsSold;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
