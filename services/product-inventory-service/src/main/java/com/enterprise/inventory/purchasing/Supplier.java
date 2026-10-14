package com.enterprise.inventory.purchasing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** A vendor the seller buys stock from. */
@Entity
@Table(name = "suppliers",
        uniqueConstraints = @UniqueConstraint(name = "uk_suppliers_seller_name", columnNames = {"seller_id", "name"}),
        indexes = @Index(name = "idx_suppliers_seller", columnList = "seller_id, active"))
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(length = 120)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(length = 60)
    private String city;

    @Column(length = 60)
    private String country;

    /** Days from ordering to delivery - used by replenishment planning. */
    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays = 7;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays = 30;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Supplier() {
    }

    public Supplier(Long sellerId, String name) {
        this.sellerId = sellerId;
        this.name = name;
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

    public void update(String name, String contactName, String email, String phone, String address, String city,
                       String country, int leadTimeDays, int paymentTermsDays, String notes) {
        this.name = name;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.city = city;
        this.country = country;
        this.leadTimeDays = leadTimeDays;
        this.paymentTermsDays = paymentTermsDays;
        this.notes = notes;
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

    public String getName() {
        return name;
    }

    public String getContactName() {
        return contactName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public int getLeadTimeDays() {
        return leadTimeDays;
    }

    public int getPaymentTermsDays() {
        return paymentTermsDays;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
