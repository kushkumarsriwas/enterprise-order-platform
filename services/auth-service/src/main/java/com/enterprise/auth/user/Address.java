package com.enterprise.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;

/** Default delivery address of a customer (embedded in the users table, used to pre-fill the checkout). */
@Embeddable
public class Address {

    @Column(name = "address_recipient", length = 120)
    private String recipient;

    @Column(name = "address_phone", length = 30)
    private String phone;

    @Column(name = "address_line1", length = 150)
    private String line1;

    @Column(name = "address_line2", length = 150)
    private String line2;

    @Column(name = "address_city", length = 60)
    private String city;

    /** Emirate / state / province. */
    @Column(name = "address_region", length = 60)
    private String region;

    @Column(name = "address_country", length = 60)
    private String country;

    protected Address() {
    }

    public Address(String recipient, String phone, String line1, String line2, String city, String region,
                   String country) {
        this.recipient = recipient;
        this.phone = phone;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.region = region;
        this.country = country;
    }

    /** One line, the way it is printed on the shipping label. */
    public String formatted() {
        List<String> parts = new ArrayList<>();
        for (String part : new String[]{recipient, line1, line2, city, region, country}) {
            if (part != null && !part.isBlank()) {
                parts.add(part.trim());
            }
        }
        return String.join(", ", parts) + (phone == null || phone.isBlank() ? "" : " (" + phone.trim() + ")");
    }

    public String getRecipient() {
        return recipient;
    }

    public String getPhone() {
        return phone;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getCity() {
        return city;
    }

    public String getRegion() {
        return region;
    }

    public String getCountry() {
        return country;
    }
}
