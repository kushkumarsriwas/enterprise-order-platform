package com.enterprise.auth.audit;

import com.enterprise.auth.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Security audit trail: every sign-in attempt, successful or not (append-only). */
@Entity
@Table(name = "login_events", indexes = {
        @Index(name = "idx_login_events_time", columnList = "created_at"),
        @Index(name = "idx_login_events_user", columnList = "user_id, created_at")
})
public class LoginEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null when the username does not exist. */
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 120)
    private String username;

    /** Sign-in page that was used: CUSTOMER, SELLER (marketplace) or ADMIN (admin console). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role portal;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "failure_reason", length = 120)
    private String failureReason;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LoginEvent() {
    }

    public LoginEvent(Long userId, String username, Role portal, boolean success, String failureReason,
                      ClientInfo client) {
        this.userId = userId;
        this.username = username;
        this.portal = portal;
        this.success = success;
        this.failureReason = failureReason;
        this.ipAddress = client.ipAddress();
        this.userAgent = client.userAgent();
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Role getPortal() {
        return portal;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
