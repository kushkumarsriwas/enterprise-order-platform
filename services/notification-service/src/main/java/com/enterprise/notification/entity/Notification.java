package com.enterprise.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private Long userId;

    @Column(nullable=false)
    private String type;

    @Column(nullable=false, length=1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private NotificationStatus status;

    @Column(nullable=false, updatable=false)
    private Instant createdAt;

    public Notification() {}

    public Notification(Long userId, String type, String message) {
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.status = NotificationStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public NotificationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(NotificationStatus status) { this.status = status; }
}
