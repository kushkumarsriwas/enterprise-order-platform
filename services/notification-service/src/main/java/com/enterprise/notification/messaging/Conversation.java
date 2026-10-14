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
        uniqueConstraints = @UniqueConstraint(name = "uk_conversations_customer_seller",
                columnNames = {"customer_id", "seller_id"}),
        indexes = {
                @Index(name = "idx_conversations_customer_last", columnList = "customer_id, last_message_at"),
                @Index(name = "idx_conversations_seller_last", columnList = "seller_id, last_message_at")
        })
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "seller_name", nullable = false, length = 100)
    private String sellerName;

    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @Column(name = "customer_unread", nullable = false)
    private int customerUnread;

    @Column(name = "seller_unread", nullable = false)
    private int sellerUnread;

    /** A participant reported the conversation (spam, abuse, scam); the administrator reviews it. */
    @Column(nullable = false)
    private boolean reported;

    @Column(name = "report_reason", length = 255)
    private String reportReason;

    @Column(name = "reported_by", length = 100)
    private String reportedBy;

    @Column(name = "reported_at")
    private Instant reportedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Conversation() {
    }

    public Conversation(Long customerId, String customerName, Long sellerId, String sellerName) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (lastMessageAt == null) {
            lastMessageAt = createdAt;
        }
    }

    public boolean hasParticipant(Long userId) {
        return customerId.equals(userId) || sellerId.equals(userId);
    }

    public boolean isCustomer(Long userId) {
        return customerId.equals(userId);
    }

    /** The sender has obviously read everything; the other side gets one more unread message. */
    public void recordMessage(String preview, boolean fromCustomer) {
        this.lastMessagePreview = preview;
        this.lastMessageAt = Instant.now();
        if (fromCustomer) {
            sellerUnread++;
            customerUnread = 0;
        } else {
            customerUnread++;
            sellerUnread = 0;
        }
    }

    public void markReadBy(Long userId) {
        if (isCustomer(userId)) {
            customerUnread = 0;
        } else {
            sellerUnread = 0;
        }
    }

    public void report(String by, String reason) {
        this.reported = true;
        this.reportedBy = by;
        this.reportReason = reason;
        this.reportedAt = Instant.now();
    }

    /** The administrator reviewed the report. */
    public void resolveReport() {
        this.reported = false;
    }

    public int unreadFor(Long userId) {
        return isCustomer(userId) ? customerUnread : sellerUnread;
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

    public Long getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
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

    public int getSellerUnread() {
        return sellerUnread;
    }

    public boolean isReported() {
        return reported;
    }

    public String getReportReason() {
        return reportReason;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
