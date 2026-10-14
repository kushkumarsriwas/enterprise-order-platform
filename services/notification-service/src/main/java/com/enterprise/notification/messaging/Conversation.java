package com.enterprise.notification.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * The message thread between one customer and one seller (like a chat with a business).
 * Each side has its own unread counter, so both inboxes can show a badge.
 */
@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(name = "uk_conversations_customer_merchant",
                columnNames = {"customer_id", "merchant_id"}),
        indexes = {
                @Index(name = "idx_conversations_customer_last", columnList = "customer_id, last_message_at"),
                @Index(name = "idx_conversations_merchant_last", columnList = "merchant_id, last_message_at")
        })
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "merchant_name", nullable = false, length = 100)
    private String merchantName;

    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @Column(name = "customer_unread", nullable = false)
    private int customerUnread;

    @Column(name = "merchant_unread", nullable = false)
    private int merchantUnread;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Conversation() {
    }

    public Conversation(Long customerId, String customerName, Long merchantId, String merchantName) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.merchantId = merchantId;
        this.merchantName = merchantName;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (lastMessageAt == null) {
            lastMessageAt = createdAt;
        }
    }

    public boolean hasParticipant(Long userId) {
        return customerId.equals(userId) || merchantId.equals(userId);
    }

    public boolean isCustomer(Long userId) {
        return customerId.equals(userId);
    }

    /** The sender has obviously read everything; the other side gets one more unread message. */
    public void recordMessage(String preview, boolean fromCustomer) {
        this.lastMessagePreview = preview;
        this.lastMessageAt = Instant.now();
        if (fromCustomer) {
            merchantUnread++;
            customerUnread = 0;
        } else {
            customerUnread++;
            merchantUnread = 0;
        }
    }

    public void markReadBy(Long userId) {
        if (isCustomer(userId)) {
            customerUnread = 0;
        } else {
            merchantUnread = 0;
        }
    }

    public int unreadFor(Long userId) {
        return isCustomer(userId) ? customerUnread : merchantUnread;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getLastMessagePreview() {
        return lastMessagePreview;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public int getCustomerUnread() {
        return customerUnread;
    }

    public int getMerchantUnread() {
        return merchantUnread;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
