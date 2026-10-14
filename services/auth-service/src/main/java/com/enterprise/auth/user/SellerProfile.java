package com.enterprise.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Business details of a SELLER account (one-to-one with the user, sharing its primary key). */
@Entity
@Table(name = "seller_profiles")
public class SellerProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Shown on every product and order; unique on the marketplace and not editable (orders keep it). */
    @Column(name = "store_name", nullable = false, unique = true, length = 80)
    private String storeName;

    @Column(length = 500)
    private String description;

    @Column(name = "business_phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "support_email", length = 120)
    private String supportEmail;

    @Column(nullable = false, length = 60)
    private String city;

    @Column(nullable = false, length = 60)
    private String country;

    @Column(name = "trade_license_number", length = 40)
    private String tradeLicenseNumber;

    /** UAE VAT Tax Registration Number (TRN), printed on tax invoices. */
    @Column(name = "vat_number", length = 20)
    private String vatNumber;

    protected SellerProfile() {
    }

    public SellerProfile(User user, String storeName, String description, String phone, String city, String country,
                         String tradeLicenseNumber, String vatNumber) {
        this.user = user;
        this.storeName = storeName;
        this.description = description;
        this.phone = phone;
        this.city = city;
        this.country = country;
        this.tradeLicenseNumber = tradeLicenseNumber;
        this.vatNumber = vatNumber;
        this.supportEmail = user.getEmail();
    }

    public void update(String description, String phone, String city, String supportEmail, String vatNumber) {
        this.description = description;
        this.phone = phone;
        this.city = city;
        this.supportEmail = supportEmail;
        this.vatNumber = vatNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getDescription() {
        return description;
    }

    public String getPhone() {
        return phone;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public String getTradeLicenseNumber() {
        return tradeLicenseNumber;
    }

    public String getVatNumber() {
        return vatNumber;
    }
}
