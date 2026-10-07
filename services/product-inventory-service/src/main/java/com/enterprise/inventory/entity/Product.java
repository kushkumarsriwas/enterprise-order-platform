package com.enterprise.inventory.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name="products")
public class Product {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false,unique=true)
    private String sku;

    @Column(nullable=false)
    private String name;

    private String description;

    @Column(nullable=false,precision=12,scale=2)
    private BigDecimal price;

    @Column(nullable=false)
    private Integer stockQuantity;

    @Version
    private Long version;

    public Product() {
    }

    public Product(String sku,String name,String description,BigDecimal price,Integer stockQuantity) {
        this.sku=sku;
        this.name=name;
        this.description=description;
        this.price=price;
        this.stockQuantity=stockQuantity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id=id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku=sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name=name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description=description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price=price; }
    public Integer getStockQuantity() { return stockQuantity; }
    public Long getVersion() { return version; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity=stockQuantity; }
    public void setVersion(Long version) { this.version=version; }
}
