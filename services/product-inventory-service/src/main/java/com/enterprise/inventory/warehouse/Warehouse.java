package com.enterprise.inventory.warehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** A stock location of one seller (warehouse, shop floor, returns area...). */
@Entity
@Table(name = "warehouses",
        uniqueConstraints = @UniqueConstraint(name = "uk_warehouses_seller_code", columnNames = {"seller_id", "code"}))
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 60)
    private String city;

    @Column(length = 200)
    private String address;

    /** Receives stock by default and ships orders first. Exactly one per seller. */
    @Column(name = "is_default", nullable = false)
    private boolean defaultWarehouse;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Warehouse() {
    }

    public Warehouse(Long sellerId, String code, String name, String city, String address, boolean defaultWarehouse) {
        this.sellerId = sellerId;
        this.code = code;
        this.name = name;
        this.city = city;
        this.address = address;
        this.defaultWarehouse = defaultWarehouse;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void update(String name, String city, String address) {
        this.name = name;
        this.city = city;
        this.address = address;
    }

    public void setDefaultWarehouse(boolean defaultWarehouse) {
        this.defaultWarehouse = defaultWarehouse;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isOwnedBy(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public String getAddress() {
        return address;
    }

    public boolean isDefaultWarehouse() {
        return defaultWarehouse;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** "DXB-1 · Dubai main" */
    public String label() {
        return code + " - " + name;
    }
}
