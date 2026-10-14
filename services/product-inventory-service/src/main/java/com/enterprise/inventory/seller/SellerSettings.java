package com.enterprise.inventory.seller;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/** Store-level switches that apply to every listing of a seller. */
@Entity
@Table(name = "seller_settings")
public class SellerSettings {

    @Id
    @Column(name = "seller_id")
    private Long sellerId;

    /** Seller is away: listings are hidden, inventory work continues as usual. */
    @Column(name = "vacation_mode", nullable = false)
    private boolean vacationMode;

    @Column(name = "vacation_message", length = 255)
    private String vacationMessage;

    /** Set by the platform administrator through the account service. */
    @Column(nullable = false)
    private boolean suspended;

    @Column(name = "low_stock_alerts", nullable = false)
    private boolean lowStockAlerts = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SellerSettings() {
    }

    public SellerSettings(Long sellerId) {
        this.sellerId = sellerId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void vacation(boolean on, String message) {
        this.vacationMode = on;
        this.vacationMessage = on ? message : null;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public void setLowStockAlerts(boolean lowStockAlerts) {
        this.lowStockAlerts = lowStockAlerts;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public boolean isVacationMode() {
        return vacationMode;
    }

    public String getVacationMessage() {
        return vacationMessage;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public boolean isLowStockAlerts() {
        return lowStockAlerts;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
