package com.enterprise.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    /** BCrypt hash - the plain password is never stored or logged. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    /** False while the account is suspended by the platform administrator. */
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "suspension_reason", length = 255)
    private String suspensionReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Embedded
    private Address address;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    protected User() {
    }

    public User(String username, String email, String passwordHash, String fullName, String phone, Role role) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void updateContact(String fullName, String phone) {
        this.fullName = fullName;
        this.phone = phone;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = Instant.now();
    }

    public void changeAddress(Address address) {
        this.address = address;
    }

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    public void suspend(String reason) {
        this.enabled = false;
        this.suspensionReason = reason;
        this.statusChangedAt = Instant.now();
    }

    public void reactivate() {
        this.enabled = true;
        this.suspensionReason = null;
        this.statusChangedAt = Instant.now();
    }

    public boolean isSeller() {
        return role == Role.SELLER;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Address getAddress() {
        return address;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }
}
