package com.enterprise.notification.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Transactional outbox for e-mails: the e-mail is stored in the same transaction as the notification and sent
 * after commit, so an e-mail is never lost (it is retried) and never sent for a rolled-back change.
 */
@Entity
@Table(name = "email_outbox", indexes = {
        @Index(name = "idx_email_outbox_status_created", columnList = "status, created_at"),
        @Index(name = "idx_email_outbox_user", columnList = "user_id")
})
public class EmailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_address", nullable = false, length = 120)
    private String toAddress;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "notification_type", length = 40)
    private String notificationType;

    @Column(nullable = false, length = 150)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EmailStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected EmailMessage() {
    }

    public EmailMessage(String toAddress, Long userId, String notificationType, String subject, String body) {
        this.toAddress = toAddress;
        this.userId = userId;
        this.notificationType = notificationType;
        this.subject = subject;
        this.body = body;
        this.status = EmailStatus.QUEUED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void sent() {
        this.status = EmailStatus.SENT;
        this.attempts++;
        this.sentAt = Instant.now();
        this.lastError = null;
    }

    public void failed(String error) {
        this.attempts++;
        this.status = EmailStatus.FAILED;
        this.lastError = error == null ? "Unknown error" : error.length() > 255 ? error.substring(0, 255) : error;
    }

    public Long getId() {
        return id;
    }

    public String getToAddress() {
        return toAddress;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public EmailStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
