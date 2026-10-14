package com.enterprise.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Business details of a MERCHANT account (one-to-one with the user, sharing its primary key). */
@Entity
@Table(name = "merchant_profiles")
public class MerchantProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "store_name", nullable = false, unique = true, length = 80)
    private String storeName;

    @Column(length = 500)
    private String description;

    @Column(name = "business_phone", nullable = false, length = 30)
    private String phone;

    @Column(nullable = false, length = 60)
    private String city;

    @Column(nullable = false, length = 60)
    private String country;

    @Column(name = "trade_license_number", length = 40)
    private String tradeLicenseNumber;

    protected MerchantProfile() {
    }

    public MerchantProfile(User user, String storeName, String description, String phone, String city, String country,
                           String tradeLicenseNumber) {
        this.user = user;
        this.storeName = storeName;
        this.description = description;
        this.phone = phone;
        this.city = city;
        this.country = country;
        this.tradeLicenseNumber = tradeLicenseNumber;
    }

    public void update(String description, String phone, String city) {
        this.description = description;
        this.phone = phone;
        this.city = city;
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

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public String getTradeLicenseNumber() {
        return tradeLicenseNumber;
    }
}
