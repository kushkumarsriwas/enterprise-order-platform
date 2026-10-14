package com.enterprise.notification.announcement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** A banner published by the platform administrator (sale events, maintenance windows, policy changes). */
@Entity
@Table(name = "announcements")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AnnouncementAudience audience;

    /** INFO, SUCCESS or WARNING - the colour of the banner. */
    @Column(nullable = false, length = 10)
    private String level;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Announcement() {
    }

    public Announcement(String title, String message, AnnouncementAudience audience, String level, Instant startsAt,
                        Instant endsAt, String createdBy) {
        update(title, message, audience, level, startsAt, endsAt, true);
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void update(String title, String message, AnnouncementAudience audience, String level, Instant startsAt,
                       Instant endsAt, boolean active) {
        this.title = title;
        this.message = message;
        this.audience = audience;
        this.level = level;
        this.startsAt = startsAt == null ? Instant.now() : startsAt;
        this.endsAt = endsAt;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public AnnouncementAudience getAudience() {
        return audience;
    }

    public String getLevel() {
        return level;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public boolean isActive() {
        return active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
